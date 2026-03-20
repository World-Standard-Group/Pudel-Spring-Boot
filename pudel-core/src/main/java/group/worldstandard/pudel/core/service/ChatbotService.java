/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.service;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import static net.dv8tion.jda.api.entities.Message.MentionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import group.worldstandard.pudel.core.agent.AgentToolContextImpl;
import group.worldstandard.pudel.core.agent.AgentToolRegistryImpl;
import group.worldstandard.pudel.core.agent.PluginToolAdapter;
import group.worldstandard.pudel.core.brain.PudelBrain;
import group.worldstandard.pudel.core.config.brain.ChatbotConfig;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.util.DiscordMessageParser;
import group.worldstandard.pudel.core.util.DiscordMessageParser.ParseResult;
import group.worldstandard.pudel.model.PudelModelService.PersonalityTraits;
import group.worldstandard.pudel.model.agent.AgentDataExecutor;
import group.worldstandard.pudel.model.agent.PudelAgentService;
import group.worldstandard.pudel.model.agent.PudelAgentService.AgentResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for handling chatbot interactions.
 * Determines when Pudel should respond as a chatbot based on configuration.
 * <p>
 * Uses Discord-specific parsing for better message understanding including:
 * - Bot name detection (both ID mentions and nickname references)
 * - Reply context awareness
 * - Short message intent detection
 */
@Service
public class ChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotService.class);

    // Cache for tracking forwarded messages - key is "userId:channelId"
    // When a user forwards a message, we store the forward content here temporarily
    // If the user sends a follow-up message within FORWARD_CONTEXT_TIMEOUT_MS, we include this context
    private static final long FORWARD_CONTEXT_TIMEOUT_MS = 30_000; // 30 seconds
    private final Map<String, ForwardContext> forwardContextCache = new ConcurrentHashMap<>();

    // Message deduplication cache - prevents processing the same message twice
    // This can happen on Discord reconnection or if events are delivered multiple times
    private static final long MESSAGE_DEDUP_TIMEOUT_MS = 60_000; // 1 minute
    private final Map<String, Long> processedMessages = new ConcurrentHashMap<>();

    // Executor for async response generation - prevents blocking JDA event threads
    // This allows Discord heartbeats to continue while LLM generates response
    private final ExecutorService responseExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ChatbotResponse");
        t.setDaemon(true);
        return t;
    });

    private final ChatbotConfig chatbotConfig;
    private final GuildDataService guildDataService;
    private final UserDataService userDataService;
    private final SubscriptionService subscriptionService;
    private final GuildSettingsService guildSettingsService;
    private final PudelBrain pudelBrain;
    private final DiscordMessageParser messageParser;
    private final PudelAgentService agentService;
    private final AgentDataExecutor agentDataExecutor;
    private final AgentToolRegistryImpl agentToolRegistry;

    public ChatbotService(ChatbotConfig chatbotConfig,
                          GuildDataService guildDataService,
                          UserDataService userDataService,
                          @Lazy SubscriptionService subscriptionService,
                          @Lazy GuildSettingsService guildSettingsService,
                          @Lazy PudelBrain pudelBrain,
                          DiscordMessageParser messageParser,
                          @Lazy PudelAgentService agentService,
                          @Lazy AgentDataExecutor agentDataExecutor,
                          AgentToolRegistryImpl agentToolRegistry) {
        this.chatbotConfig = chatbotConfig;
        this.guildDataService = guildDataService;
        this.userDataService = userDataService;
        this.subscriptionService = subscriptionService;
        this.guildSettingsService = guildSettingsService;
        this.pudelBrain = pudelBrain;
        this.messageParser = messageParser;
        this.agentService = agentService;
        this.agentDataExecutor = agentDataExecutor;
        this.agentToolRegistry = agentToolRegistry;
    }

    /**
     * Check if Pudel should respond as a chatbot to this message.
     * Uses Discord-specific parsing for better detection of:
     * - Bot mentions (by ID and by name/nickname)
     * - Reply context (replying to bot's message)
     * - Trigger keywords
     */
    public boolean shouldRespondAsChatbot(MessageReceivedEvent event, String selfId) {
        Message message = event.getMessage();
        String content = message.getContentRaw().toLowerCase();

        // Direct message - always respond if enabled
        if (!event.isFromGuild()) {
            return chatbotConfig.getTriggers().isOnDirectMessage();
        }

        // Check if channel is in always-active list
        if (chatbotConfig.getTriggers().getAlwaysActiveChannels().contains(event.getChannel().getId())) {
            return true;
        }

        // Use message parser for mention detection
        ParseResult parseResult = messageParser.parseMessage(event);

        // Guard: Ignore @everyone and @here unless the bot is ALSO directly addressed.
        // Discord's mention state flags every server member as "mentioned" by @everyone/@here.
        // JDA's isMentioned() without MentionType filter returns true for these mass mentions,
        // which caused Pudel to respond to every @everyone/@here message.
        //
        // We check isMassMention via JDA's MentionType.EVERYONE/HERE (protocol-level state),
        // NOT text matching. If the bot is also directly mentioned by ID, name, or reply,
        // we allow the response (e.g., "@everyone @Pudel help" or "@everyone hey pudel").
        if (parseResult.isMassMention() && !parseResult.isDirectlyAddressed()) {
            logger.debug("Ignoring mass mention (@everyone/@here) — bot not directly addressed");
            return false;
        }

        // Check if bot was mentioned by ID
        if (chatbotConfig.getTriggers().isOnMention() && parseResult.mentionsBotById()) {
            logger.debug("Bot mentioned by ID in message from {}", event.getAuthor().getName());
            return true;
        }

        // Check if bot was mentioned by name/nickname
        if (chatbotConfig.getTriggers().isOnMention() && parseResult.mentionsBotByName()) {
            logger.debug("Bot mentioned by name in message from {}", event.getAuthor().getName());
            return true;
        }

        // Check if this is a reply to bot's message
        if (chatbotConfig.getTriggers().isOnReplyToBot() && parseResult.isReplyToBot()) {
            logger.debug("Reply to bot's message from {}", event.getAuthor().getName());
            return true;
        }

        // Fallback: JDA mention check (USER type only — excludes @everyone/@here)
        if (chatbotConfig.getTriggers().isOnMention() &&
                message.getMentions().isMentioned(event.getJDA().getSelfUser(), MentionType.USER)) {
            return true;
        }

        // Fallback: JDA reply check
        if (chatbotConfig.getTriggers().isOnReplyToBot() && message.getReferencedMessage() != null) {
            if (message.getReferencedMessage().getAuthor().getId().equals(selfId)) {
                return true;
            }
        }

        // Check for trigger keywords
        for (String keyword : chatbotConfig.getTriggers().getKeywords()) {
            if (content.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the personality/behavior settings for a guild.
     */
    public PudelPersonality getGuildPersonality(long guildId) {
        GuildSettings settings = guildSettingsService.getGuildSettings(String.valueOf(guildId))
                .orElse(null);
        if (settings == null) {
            return PudelPersonality.defaultPersonality();
        }

        return new PudelPersonality(
                settings.getBiography(),
                settings.getPersonality(),
                settings.getPreferences(),
                settings.getDialogueStyle(),
                settings.getNickname() != null ? settings.getNickname() : "Pudel",
                settings.getLanguage() != null ? settings.getLanguage() : "en",
                settings.getResponseLength() != null ? settings.getResponseLength() : "medium",
                settings.getFormality() != null ? settings.getFormality() : "balanced",
                settings.getEmoteUsage() != null ? settings.getEmoteUsage() : "moderate",
                settings.getQuirks(),
                settings.getTopicsInterest(),
                settings.getTopicsAvoid()
        );
    }

    /**
     * Get the personality/behavior settings for a user (DM context).
     */
    public PudelPersonality getUserPersonality(long userId) {
        return userDataService.getPudelSettings(userId)
                .map(settings -> new PudelPersonality(
                        (String) settings.get("biography"),
                        (String) settings.get("personality"),
                        (String) settings.get("preferences"),
                        (String) settings.get("dialogue_style"),
                        (String) settings.getOrDefault("nickname", "Pudel"),
                        (String) settings.getOrDefault("language", "en"),
                        (String) settings.getOrDefault("response_length", "medium"),
                        (String) settings.getOrDefault("formality", "balanced"),
                        (String) settings.getOrDefault("emote_usage", "moderate"),
                        (String) settings.get("quirks"),
                        (String) settings.get("topics_interest"),
                        (String) settings.get("topics_avoid")
                ))
                .orElse(PudelPersonality.defaultPersonality());
    }

    /**
     * Get conversation context for generating a response.
     * Retrieves recent dialogue history within capacity limits.
     */
    public ConversationContext getConversationContext(MessageReceivedEvent event) {
        int contextSize = chatbotConfig.getContextSize();

        if (event.isFromGuild()) {
            long guildId = event.getGuild().getIdLong();
            long userId = event.getAuthor().getIdLong();
            long channelId = event.getChannel().getIdLong();

            // Get recent dialogue from this channel (not just user) for better context
            // This allows Pudel to see the full conversation flow
            List<Map<String, Object>> channelHistory = guildDataService.getChannelDialogue(guildId, channelId, contextSize);

            // If no channel history, fall back to user's dialogue history
            List<Map<String, Object>> history = channelHistory.isEmpty()
                    ? guildDataService.getRecentDialogue(guildId, userId, contextSize)
                    : channelHistory;

            PudelPersonality personality = getGuildPersonality(guildId);

            logger.debug("Retrieved {} dialogue entries for guild {} channel {}",
                    history.size(), guildId, channelId);

            return new ConversationContext(
                    history,
                    personality,
                    true,
                    guildId,
                    userId,
                    event.getMember() // Include member for permission checks
            );
        } else {
            long userId = event.getAuthor().getIdLong();

            // Get recent dialogue for DM
            List<Map<String, Object>> history = userDataService.getRecentDialogue(userId, contextSize);
            PudelPersonality personality = getUserPersonality(userId);

            return new ConversationContext(
                    history,
                    personality,
                    false,
                    0L,
                    userId,
                    null // No member in DMs
            );
        }
    }

    /**
     * Store dialogue in the appropriate schema.
     * Checks capacity limits before storing.
     */
    public boolean storeDialogue(MessageReceivedEvent event, String userMessage, String botResponse, String intent) {
        if (event.isFromGuild()) {
            long guildId = event.getGuild().getIdLong();

            // Check capacity
            if (!subscriptionService.canStoreGuildDialogue(guildId)) {
                logger.warn("Guild {} has reached dialogue capacity, not storing", guildId);
                return false;
            }

            guildDataService.storeDialogue(
                    guildId,
                    event.getAuthor().getIdLong(),
                    event.getChannel().getIdLong(),
                    userMessage,
                    botResponse,
                    intent
            );
            return true;
        } else {
            long userId = event.getAuthor().getIdLong();

            // Check capacity
            if (!subscriptionService.canStoreUserDialogue(userId)) {
                logger.warn("User {} has reached dialogue capacity, not storing", userId);
                return false;
            }

            userDataService.storeDialogue(userId, userMessage, botResponse, intent);
            return true;
        }
    }

    /**
     * Generate a chatbot response based on context and personality.
     * Uses PudelBrain for intelligent response generation.
     * Routes to Agent mode when data management intent is detected.
     */
    public String generateResponse(String userMessage, ConversationContext context) {
        if (pudelBrain == null) {
            // Fallback if brain is not available
            return generateFallbackResponse(userMessage, context);
        }

        try {
            // Check if this message needs agent capabilities (data management)
            if (agentService != null && agentService.isAvailable() && agentService.shouldUseAgent(userMessage)) {
                logger.debug("Routing message to Agent mode for data management");
                return processAsAgent(userMessage, context);
            }

            // Convert to brain context
            PudelBrain.ConversationContext brainContext = new PudelBrain.ConversationContext(
                    context.history(),
                    context.personality(),
                    context.isGuild(),
                    context.isGuild() ? context.guildId() : context.userId(),
                    context.userId()
            );

            // Process with brain
            PudelBrain.BrainResponse brainResponse = pudelBrain.processMessage(
                    userMessage,
                    brainContext,
                    context.isGuild(),
                    context.isGuild() ? context.guildId() : context.userId()
            );

            logger.debug("Brain response: intent={}, sentiment={}, memoriesUsed={}, confidence={}",
                    brainResponse.intent(), brainResponse.sentiment(),
                    brainResponse.memoriesUsed(), brainResponse.confidence());

            return brainResponse.response();

        } catch (Exception e) {
            logger.error("Error generating response with brain: {}", e.getMessage());
            return generateFallbackResponse(userMessage, context);
        }
    }

    /**
     * Fallback response generation when brain is not available.
     */
    private String generateFallbackResponse(String userMessage, ConversationContext context) {
        PudelPersonality personality = context.personality();
        StringBuilder response = new StringBuilder();

        if (personality.personality() != null && !personality.personality().isEmpty()) {
            response.append("*adjusts maid headband* ");
        }

        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("hello") || lowerMessage.contains("hi")) {
            response.append("Hello! How may I assist you today?");
        } else if (lowerMessage.contains("help")) {
            response.append("I'm here to help! You can ask me questions or use commands with the prefix.");
        } else if (lowerMessage.contains("thanks") || lowerMessage.contains("thank you")) {
            response.append("You're welcome! Is there anything else I can help you with?");
        } else if (lowerMessage.contains("bye") || lowerMessage.contains("goodbye")) {
            response.append("Goodbye! Feel free to call on me anytime.");
        } else {
            response.append("I understand you said: \"").append(userMessage).append("\". ");
            response.append("I'm still learning to provide better responses!");
        }

        return response.toString();
    }

    /**
     * Process a message using the AI Agent with data management tools.
     * This allows Pudel to act as a true maid/secretary, managing data autonomously.
     * Also includes any plugin-registered tools.
     */
    private String processAsAgent(String userMessage, ConversationContext context) {
        try {
            // Convert personality to model format
            PersonalityTraits personality = convertToPersonalityTraits(context.personality());

            long targetId = context.isGuild() ? context.guildId() : context.userId();

            // Create tool context for plugin tools
            AgentToolContextImpl toolContext = AgentToolContextImpl.builder()
                    .targetId(targetId)
                    .isGuild(context.isGuild())
                    .requestingUserId(context.userId())
                    .requestingMember(context.member()) // Include member for permission checks
                    .build();

            // Build plugin tools list
            List<Object> pluginTools = new ArrayList<>();
            if (agentToolRegistry.getToolCount() > 0) {
                // Add plugin tool adapter that exposes all registered plugin tools
                PluginToolAdapter pluginAdapter = new PluginToolAdapter(agentToolRegistry, toolContext);
                pluginTools.add(pluginAdapter);
                logger.debug("Including {} plugin tools via adapter", agentToolRegistry.getToolCount());
            }

            // Process with agent, including plugin tools
            AgentResponse agentResponse = agentService.processAsAgent(
                    userMessage,
                    personality,
                    agentDataExecutor,
                    targetId,
                    context.isGuild(),
                    context.userId(),
                    context.history(),
                    pluginTools.isEmpty() ? null : pluginTools
            );

            if (agentResponse.success() && agentResponse.response() != null) {
                logger.debug("Agent processed message successfully");
                return agentResponse.response();
            } else {
                logger.debug("Agent processing failed: {}, falling back to brain", agentResponse.error());
                // Fall back to regular brain processing
                PudelBrain.ConversationContext brainContext = new PudelBrain.ConversationContext(
                        context.history(),
                        context.personality(),
                        context.isGuild(),
                        targetId,
                        context.userId()
                );
                PudelBrain.BrainResponse brainResponse = pudelBrain.processMessage(
                        userMessage, brainContext, context.isGuild(), targetId);
                return brainResponse.response();
            }
        } catch (Exception e) {
            logger.error("Error in agent processing: {}", e.getMessage());
            return generateFallbackResponse(userMessage, context);
        }
    }

    /**
     * Convert ChatbotService personality to PudelModelService format.
     */
    private PersonalityTraits convertToPersonalityTraits(PudelPersonality personality) {
        if (personality == null) {
            return null;
        }
        return new PersonalityTraits(
                personality.nickname(),
                personality.biography(),
                personality.personality(),
                personality.preferences(),
                personality.dialogueStyle(),
                personality.language(),
                personality.responseLength(),
                personality.formality(),
                personality.emoteUsage(),
                personality.quirks(),
                personality.topicsInterest(),
                personality.topicsAvoid()
        );
    }

    /**
     * Handle a chatbot interaction.
     * Runs asynchronously to prevent blocking JDA event thread during LLM generation.
     */
    public void handleChatbotMessage(MessageReceivedEvent event) {
        try {
            String messageId = event.getMessageId();

            // Deduplication check - prevent processing the same message twice
            // This can happen on Discord reconnection or if events are delivered multiple times
            long now = System.currentTimeMillis();
            Long previousProcessTime = processedMessages.putIfAbsent(messageId, now);
            if (previousProcessTime != null) {
                logger.debug("Message {} already being processed, skipping duplicate", messageId);
                return;
            }

            // Clean up old entries periodically (every 100 messages)
            if (processedMessages.size() > 100) {
                processedMessages.entrySet().removeIf(entry ->
                        now - entry.getValue() > MESSAGE_DEDUP_TIMEOUT_MS);
            }

            String userMessage = event.getMessage().getContentRaw();
            String userId = event.getAuthor().getId();
            String channelId = event.getChannel().getId();
            String cacheKey = userId + ":" + channelId;

            // Remove bot mention from message if present
            String processedMessage = userMessage
                    .replaceAll("<@!?" + event.getJDA().getSelfUser().getId() + ">", "")
                    .trim();

            // Include reply/referenced message content if present
            // This allows Pudel to understand what the user is replying to
            String replyContext = extractReplyContext(event);
            if (replyContext != null && !replyContext.isEmpty()) {
                processedMessage = "[Replying to: \"" + replyContext + "\"]\n\n" + processedMessage;
                logger.debug("Including reply context in message");
            }

            // Check for forwarded message snapshots in THIS message
            String inlineForwardContext = extractForwardContext(event);

            // Check if this is a forward-only message (has snapshots but no/minimal user text)
            // In this case, just cache the forward context silently - don't respond automatically
            // User needs to mention Pudel with their question to get a response
            boolean isForwardOnlyMessage = inlineForwardContext != null &&
                    !inlineForwardContext.isEmpty() &&
                    processedMessage.length() < 10;  // minimal or empty user text

            if (isForwardOnlyMessage) {
                // Store forward context for subsequent messages from this user in this channel
                forwardContextCache.put(cacheKey, new ForwardContext(inlineForwardContext, Instant.now()));
                logger.debug("Stored forward context silently for user {} in channel {} - waiting for follow-up mention",
                        userId, channelId);
                // Don't respond - just wait for user to mention Pudel with their actual question
                return;
            }

            // Check for cached forward context from a previous forward message
            String cachedForwardContext = getCachedForwardContext(cacheKey);

            // Include forward context if present (either inline or cached)
            String forwardContext = inlineForwardContext;
            if ((forwardContext == null || forwardContext.isEmpty()) && cachedForwardContext != null) {
                forwardContext = cachedForwardContext;
                logger.debug("Using cached forward context for message");
            }

            if (forwardContext != null && !forwardContext.isEmpty()) {
                processedMessage = "[Forwarded message context: \"" + forwardContext + "\"]\n\n" + processedMessage;
                logger.debug("Including forward context in message");
            }

            final String cleanMessage = processedMessage.isEmpty() ? "" : processedMessage;

            // Get conversation context (fast - just DB lookups)
            ConversationContext context = getConversationContext(event);

            // Capture event data before going async (event may become invalid)
            final boolean isGuild = event.isFromGuild();
            final long targetId = isGuild ? event.getGuild().getIdLong() : event.getAuthor().getIdLong();
            final long userIdLong = event.getAuthor().getIdLong();
            final long channelIdLong = event.getChannel().getIdLong();

            // Show typing indicator while generating response
            // This returns immediately and doesn't block
            event.getChannel().sendTyping().queue();

            // Run LLM generation asynchronously to prevent blocking JDA event thread
            // This allows Discord heartbeats to continue during long LLM generations
            responseExecutor.submit(() -> {
                try {
                    // Generate response (may take time with LLM)
                    String response = generateResponse(cleanMessage, context);

                    // Validate response is not empty before sending
                    if (response == null || response.isBlank()) {
                        logger.warn("Generated empty response, using fallback");
                        response = generateFallbackResponse(cleanMessage, context);
                    }

                    final String finalResponse = response;

                    // Send response
                    event.getChannel().sendMessage(finalResponse).queue(
                            sentMessage -> {
                                // Store the dialogue using brain if available
                                if (pudelBrain != null) {
                                    pudelBrain.storeDialogue(cleanMessage, finalResponse, "chat",
                                            userIdLong, channelIdLong, isGuild, targetId);
                                } else {
                                    // Fallback storage
                                    storeDialogue(event, cleanMessage, finalResponse, "chat");
                                }
                                logger.debug("Chatbot response sent and stored");
                            },
                            error -> logger.error("Failed to send chatbot response: {}", error.getMessage())
                    );

                } catch (Exception e) {
                    logger.error("Error generating chatbot response: {}", e.getMessage(), e);
                    // Send error message to user
                    event.getChannel().sendMessage("Sorry, I encountered an error while processing your message. Please try again.")
                            .queue(null, err -> logger.debug("Failed to send error message"));
                }
            });

        } catch (Exception e) {
            logger.error("Error handling chatbot message: {}", e.getMessage(), e);
        }
    }

    /**
     * Get cached forward context for a user+channel, if not expired.
     */
    private String getCachedForwardContext(String cacheKey) {
        ForwardContext cached = forwardContextCache.get(cacheKey);
        if (cached == null) {
            return null;
        }

        // Check if expired
        if (Instant.now().toEpochMilli() - cached.timestamp().toEpochMilli() > FORWARD_CONTEXT_TIMEOUT_MS) {
            forwardContextCache.remove(cacheKey);
            logger.debug("Forward context expired for {}", cacheKey);
            return null;
        }

        // Remove after use (one-time context)
        forwardContextCache.remove(cacheKey);
        logger.debug("Retrieved and consumed cached forward context for {}", cacheKey);
        return cached.content();
    }

    /**
     * Track passive context for messages that don't trigger a response.
     * Called by DiscordEventListener for context building.
     * Also tracks forwarded messages for potential follow-up questions.
     */
    public void trackPassiveContext(MessageReceivedEvent event) {
        // Check if this message has a forward that we should track
        String forwardContext = extractForwardContext(event);
        if (forwardContext != null && !forwardContext.isEmpty()) {
            String cacheKey = event.getAuthor().getId() + ":" + event.getChannel().getId();
            forwardContextCache.put(cacheKey, new ForwardContext(forwardContext, Instant.now()));
            logger.debug("Tracked forward context passively for user {} in channel {}",
                    event.getAuthor().getId(), event.getChannel().getId());
        }

        if (pudelBrain == null) {
            return;
        }

        try {
            String message = event.getMessage().getContentRaw();
            long userId = event.getAuthor().getIdLong();
            long channelId = event.getChannel().getIdLong();
            boolean isGuild = event.isFromGuild();
            long targetId = isGuild ? event.getGuild().getIdLong() : userId;

            pudelBrain.trackContext(message, userId, channelId, isGuild, targetId);
        } catch (Exception e) {
            logger.debug("Error tracking passive context: {}", e.getMessage());
        }
    }

    /**
     * Extract content from a reply/referenced message.
     * When a user replies to a message, this extracts the original message content
     * so Pudel can understand the context of what the user is referring to.
     *
     * @param event The message event
     * @return The referenced message content, or null if not a reply
     */
    private String extractReplyContext(MessageReceivedEvent event) {
        Message referencedMessage = event.getMessage().getReferencedMessage();
        if (referencedMessage == null) {
            return null;
        }

        String content = referencedMessage.getContentRaw();

        // Truncate very long messages to avoid token bloat
        if (content.length() > 500) {
            content = content.substring(0, 500) + "...";
        }

        // Include author info for context
        String authorName = referencedMessage.getAuthor().getName();
        boolean isFromBot = referencedMessage.getAuthor().getId()
                .equals(event.getJDA().getSelfUser().getId());

        if (isFromBot) {
            return "Pudel said: " + content;
        } else {
            return authorName + " said: " + content;
        }
    }

    /**
     * Extract content from forwarded messages.
     * Discord's message forwarding uses message snapshots (as of 2024).
     *
     * @param event The message event
     * @return The forwarded message content, or null if not a forward
     */
    private String extractForwardContext(MessageReceivedEvent event) {
        Message message = event.getMessage();
        StringBuilder forwardContent = new StringBuilder();

        // Check for message snapshots (Discord's native forward feature)
        // JDA 5.x uses getMessageSnapshots(), returns List<MessageSnapshot>
        try {
            var snapshots = event.getMessage().getMessageSnapshots();
            if (!snapshots.isEmpty()) {
                for (var snapshot : snapshots) {
                    // MessageSnapshot provides direct access to content
                    String content = snapshot.getContentRaw();
                    if (!content.isEmpty()) {
                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }
                        if (!forwardContent.isEmpty()) {
                            forwardContent.append("\n---\n");
                        }
                        forwardContent.append(content);
                    }
                }
                if (!forwardContent.isEmpty()) {
                    logger.debug("Extracted {} forwarded message snapshot(s)", snapshots.size());
                    return forwardContent.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting message snapshots: {}", e.getMessage());
        }

        // Fallback: Check embeds for forwarded content (older bot integrations)
        for (var embed : message.getEmbeds()) {
            String description = embed.getDescription();
            if (description != null && !description.isEmpty()) {
                String title = embed.getTitle();
                if (title != null && (
                        title.toLowerCase().contains("forward") ||
                        title.toLowerCase().contains("shared"))) {
                    if (description.length() > 500) {
                        description = description.substring(0, 500) + "...";
                    }
                    return description;
                }
            }
        }

        return null;
    }

    /**
     * Pudel personality/behavior settings.
     */
    public record PudelPersonality(
            String biography,
            String personality,
            String preferences,
            String dialogueStyle,
            String nickname,
            String language,
            String responseLength,
            String formality,
            String emoteUsage,
            String quirks,
            String topicsInterest,
            String topicsAvoid
    ) {
        public static PudelPersonality defaultPersonality() {
            return new PudelPersonality(
                    "Pudel is a helpful Discord bot assistant.",
                    "Friendly, helpful, and professional.",
                    "Prefers clear and concise communication.",
                    "Polite and informative.",
                    "Pudel",
                    "en",
                    "medium",
                    "balanced",
                    "moderate",
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Conversation context for generating responses.
     */
    public record ConversationContext(
            List<Map<String, Object>> history,
            PudelPersonality personality,
            boolean isGuild,
            long guildId,
            long userId,
            Member member // For permission checking in agent tools
    ) {}

    /**
     * Forward context cache entry.
     * Stores the content of a forwarded message along with its timestamp.
     * Used to associate forward content with follow-up questions from the same user.
     */
    private record ForwardContext(
            String content,
            Instant timestamp
    ) {}
}

