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
package group.worldstandard.pudel.core.brain;

import group.worldstandard.pudel.core.brain.context.EntityExtractor;
import group.worldstandard.pudel.core.brain.context.PassiveContextEntry;
import group.worldstandard.pudel.core.brain.context.PassiveContextProcessor;
import group.worldstandard.pudel.core.brain.memory.DialogueHistoryManager;
import group.worldstandard.pudel.core.brain.memory.MemoryManager;
import group.worldstandard.pudel.core.brain.ollama.OllamaClient;
import group.worldstandard.pudel.core.brain.ollama.OllamaClient.ConversationTurn;
import group.worldstandard.pudel.core.brain.personality.PudelPersonality;
import group.worldstandard.pudel.core.brain.personality.SystemPromptBuilder;
import group.worldstandard.pudel.core.config.brain.PudelBrainConfig;
import group.worldstandard.pudel.core.config.brain.PudelBrainConfig.Discord;
import group.worldstandard.pudel.core.brain.analyzer.TextAnalysis;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.messages.MessageSnapshot;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PudelBrain v2 - The reworked central intelligence component.
 * <p>
 * Key changes from the legacy brain:
 * <ul>
 *   <li>Ollama completion-focused (local / BYO API key)</li>
 *   <li>Async Discord handling: sends typing indicator immediately, then sends
 *       the completed response via simple sendMessage().queue() — no blocking
 *       RestAction waiting that could hang Discord</li>
 *   <li>Roleplay support with custom prompts from database</li>
 *   <li>Passive context collection with message_id tracking, entity extraction,
 *       reply/forward references, and attachment URL collection</li>
 *   <li>Dialogue history with respond_to tracking</li>
 *   <li>Agent tools via MCP (Model Context Protocol) — passive context and
 *       dialogue history are accessed through MCP tools</li>
 *   <li>Discord Markdown output (emoji, user mention, channel mention, etc.)</li>
 *   <li>Attachment reading (text files) and reply (image/video)</li>
 *   <li>No dependency on pudel-model module (deprecated)</li>
 * </ul>
 */
@Component
public class PudelBrain {

    private static final Logger logger = LoggerFactory.getLogger(PudelBrain.class);

    private final PudelBrainConfig brainConfig;
    private final OllamaClient ollamaClient;
    private final SystemPromptBuilder systemPromptBuilder;
    private final PassiveContextProcessor passiveContextProcessor;
    private final DialogueHistoryManager dialogueHistoryManager;
    private final MemoryManager memoryManager;
    private final EntityExtractor entityExtractor;

    public PudelBrain(PudelBrainConfig brainConfig,
                       OllamaClient ollamaClient,
                       SystemPromptBuilder systemPromptBuilder,
                       PassiveContextProcessor passiveContextProcessor,
                       DialogueHistoryManager dialogueHistoryManager,
                       MemoryManager memoryManager,
                       EntityExtractor entityExtractor) {
        this.brainConfig = brainConfig;
        this.ollamaClient = ollamaClient;
        this.systemPromptBuilder = systemPromptBuilder;
        this.passiveContextProcessor = passiveContextProcessor;
        this.dialogueHistoryManager = dialogueHistoryManager;
        this.memoryManager = memoryManager;
        this.entityExtractor = entityExtractor;

        logger.info("PudelBrain v2 initialized (Ollama: {}, model: {})",
                brainConfig.getOllama().getBaseUrl(),
                brainConfig.getOllama().getModel());
    }

    // ===============================
    // Main Message Processing
    // ===============================

    /**
     * Process a message and generate a response asynchronously.
     * <p>
     * This is the main entry point for the reworked brain. It:
     * <ol>
     *   <li>Sends a typing indicator to Discord immediately</li>
     *   <li>Builds the system prompt from personality settings</li>
     *   <li>Gathers conversation history via MCP tools</li>
     *   <li>Reads text attachments if present</li>
     *   <li>Calls Ollama for completion</li>
     *   <li>Sends the response via sendMessage().queue()</li>
     *   <li>Stores the dialogue exchange with respond_to tracking</li>
     * </ol>
     *
     * @param event        the Discord message event
     * @param personality  the personality configuration
     * @param isGuild      whether this is a guild message
     * @param targetId     guild ID or user ID
     */
    public void processMessageAsync(MessageReceivedEvent event,
                                     PudelPersonality personality,
                                     boolean isGuild,
                                     long targetId) {
        processMessageAsync(event, personality, isGuild, targetId, null);
    }

    /**
     * Process a message and generate a response asynchronously.
     * Includes optional recent context (e.g., forwarded messages sent just before the mention).
     */
    public void processMessageAsync(MessageReceivedEvent event,
                                     PudelPersonality personality,
                                     boolean isGuild,
                                     long targetId,
                                     String recentContext) {
        long userId = event.getAuthor().getIdLong();
        long channelId = event.getChannel().getIdLong();
        long messageId = event.getMessage().getIdLong();
        MessageChannelUnion channel = event.getChannel();

        // Step 1: Send typing indicator immediately (non-blocking)
        if (brainConfig.getDiscord().isSendTyping()) {
            channel.sendTyping().queue(
                    success -> logger.debug("Typing indicator sent"),
                    error -> logger.debug("Failed to send typing: {}", error.getMessage())
            );
        }

        // Step 2: Build the user message (including attachment content)
        String userMessage = buildUserMessage(event);
        logger.debug("Built user message: length={}, content='{}'", userMessage.length(),
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);

        // Step 3: Build system prompt from personality
        String systemPrompt = systemPromptBuilder.buildSystemPrompt(
                personality, isGuild, brainConfig.getCompletion().isEnableRoleplay());

        // Step 4: Gather conversation history
        List<ConversationTurn> history = gatherConversationHistory(
                userId, channelId, isGuild, targetId);

        // Step 5: Get passive context via MCP tool simulation
        String passiveContext = gatherPassiveContext(channelId, isGuild, targetId);

        // Step 6: Enrich the user message with context (passive context + recent forwarded content)
        String enrichedMessage = enrichMessageWithContext(userMessage, passiveContext, recentContext);

        // Step 7: Call Ollama asynchronously
        CompletableFuture<String> responseFuture = ollamaClient.generateStreaming(
                systemPrompt,
                enrichedMessage,
                history,
                token -> {
                    // We don't stream tokens to Discord (too many messages)
                    // Instead, we collect the full response and send it once
                }
        );

        // Step 8: Handle the completed response
        final String finalUserMessage = userMessage;
        responseFuture.thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                // Ensure Discord Markdown formatting
                if (brainConfig.getDiscord().isFormatMarkdown()) {
                    response = ensureDiscordMarkdown(response);
                }

                // Truncate if needed
                response = truncateForDiscord(response);

                // Send the response (non-blocking)
                final String finalResponse = response;
                channel.sendMessage(finalResponse)
                        .setMessageReference(messageId)
                        .queue(
                                sentMessage -> {
                                    // Store dialogue history with respond_to tracking
                                    storeDialogueExchange(
                                            finalUserMessage, finalResponse, userId, channelId,
                                            isGuild, targetId, messageId,
                                            event.getMessage());
                                    logger.debug("Response sent for message {}", messageId);
                                },
                                error -> logger.error("Failed to send response: {}", error.getMessage())
                        );
            } else {
                logger.warn("Ollama returned empty response for message {}", messageId);
                channel.sendMessage("I'm sorry, I couldn't generate a response. Please try again.")
                        .setMessageReference(messageId)
                        .queue();
            }
        }).exceptionally(throwable -> {
            logger.error("Error generating response for message {}: {}",
                    messageId, throwable.getMessage());
            channel.sendMessage("I encountered an error while thinking. Please try again later.")
                    .setMessageReference(messageId)
                    .queue();
            return null;
        });
    }

    // ===============================
    // Passive Context
    // ===============================

    /**
     * Passively track context from a message without generating a response.
     * <p>
     * Used for building context when Pudel isn't directly addressed.
     * The message is queued in the PassiveContextProcessor for later processing.
     *
     * @param event     the Discord message event
     * @param targetId  guild ID or user ID for schema routing
     * @param isGuild   whether this is a guild context
     */
    public void trackContext(MessageReceivedEvent event, long targetId, boolean isGuild) {
        passiveContextProcessor.submit(event, targetId, isGuild);
    }

    /**
     * Get passive context for the MCP tool interface.
     * <p>
     * This is called by MCP tools when the LLM requests context.
     *
     * @param channelId the channel ID
     * @param isGuild   whether this is a guild context
     * @param targetId  guild ID or user ID
     * @param limit     maximum entries to return
     * @return formatted context string for the LLM
     */
    public String getPassiveContext(long channelId, boolean isGuild, long targetId, int limit) {
        List<PassiveContextEntry> entries = passiveContextProcessor.getRecentContext(
                channelId, isGuild, targetId, limit);

        if (entries.isEmpty()) {
            return "No recent context available.";
        }

        StringBuilder sb = new StringBuilder("Recent conversation context:\n");
        for (PassiveContextEntry entry : entries) {
            sb.append("- [Message ").append(entry.messageId()).append("] ");
            sb.append("<@").append(entry.userId()).append(">: ");
            sb.append(entry.content());
            if (entry.replyToMessageId() != null) {
                sb.append(" (replying to message ").append(entry.replyToMessageId()).append(")");
            }
            if (!entry.attachmentUrls().isEmpty()) {
                sb.append(" [attachments: ").append(entry.attachmentUrls().size()).append("]");
            }
            // Include forwarded message content
            if (entry.forwardedMessages() != null && !entry.forwardedMessages().isEmpty()) {
                for (var forwarded : entry.forwardedMessages()) {
                    sb.append("\n  [Forwarded]: ");
                    // Truncate long forwarded content for context
                    String fwdContent = forwarded.content();
                    if (fwdContent != null && fwdContent.length() > 200) {
                        sb.append(fwdContent, 0, 200).append("...");
                    } else {
                        sb.append(fwdContent);
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Fetch a specific message from passive context by message ID.
     * <p>
     * Used by MCP tools when the LLM references a specific message.
     *
     * @param messageId the message ID to fetch
     * @param channelId the channel ID
     * @param isGuild   whether this is a guild context
     * @param targetId  guild ID or user ID
     * @return the context entry, or null if not found
     */
    public PassiveContextEntry fetchContextByMessageId(long messageId, long channelId,
                                                        boolean isGuild, long targetId) {
        return passiveContextProcessor.fetchByMessageId(messageId, channelId, isGuild, targetId);
    }

    // ===============================
    // Dialogue History
    // ===============================

    /**
     * Get dialogue history for the MCP tool interface.
     * <p>
     * This is called by MCP tools when the LLM requests conversation history.
     *
     * @param userId  the user ID
     * @param isGuild whether this is a guild context
     * @param targetId guild ID or user ID
     * @param limit   maximum turns to return
     * @return formatted history string for the LLM
     */
    public String getDialogueHistory(long userId, boolean isGuild, long targetId, int limit) {
        List<Map<String, Object>> history = dialogueHistoryManager.getRecentHistory(
                userId, isGuild, targetId, limit);

        if (history.isEmpty()) {
            return "No previous conversation history.";
        }

        StringBuilder sb = new StringBuilder("Previous conversation:\n");
        for (Map<String, Object> turn : history) {
            String userMsg = (String) turn.getOrDefault("user_message", "");
            String botMsg = (String) turn.getOrDefault("bot_response", "");
            Object respondTo = turn.get("respond_to");

            sb.append("User: ").append(userMsg).append("\n");
            sb.append("Assistant: ").append(botMsg);
            if (respondTo != null) {
                sb.append(" (in response to message ").append(respondTo).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Store a dialogue exchange.
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                  long userId, long channelId, boolean isGuild, long targetId) {
        return dialogueHistoryManager.storeDialogue(
                userMessage, botResponse, intent, userId, channelId, isGuild, targetId);
    }

    /**
     * Store a dialogue exchange with respond_to tracking.
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                  long userId, long channelId, boolean isGuild, long targetId,
                                  Long respondToMessageId,
                                  List<String> userAttachmentUrls,
                                  List<String> botAttachmentUrls) {
        return dialogueHistoryManager.storeDialogue(
                userMessage, botResponse, intent, userId, channelId, isGuild, targetId,
                respondToMessageId, userAttachmentUrls, botAttachmentUrls);
    }

    // ===============================
    // Utility Methods
    // ===============================

    /**
     * Build the user message from the event, including text attachment content,
     * embed content (forwarded messages), and stripping bot name/nickname if mentioned by name.
     */
    private String buildUserMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        StringBuilder sb = new StringBuilder();

        String content = message.getContentDisplay();
        logger.debug("buildUserMessage: contentDisplay='{}', embeds={}, attachments={}",
                content.length() > 50 ? content.substring(0, 50) + "..." : content,
                message.getEmbeds().size(),
                message.getAttachments().size());

        if (!content.isBlank()) {
            // Strip bot name/nickname from the beginning of the message
            String cleaned = stripBotName(content, event);
            sb.append(cleaned);
        }

        // Include referenced message content (replies)
        Message referencedMessage = message.getReferencedMessage();
        if (referencedMessage != null) {
            logger.debug("buildUserMessage: processing referenced message id={}", referencedMessage.getId());
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("[Referenced message from ");
            sb.append(referencedMessage.getAuthor().getName());
            sb.append("]");

            String refContent = referencedMessage.getContentDisplay();
            if (!refContent.isBlank()) {
                sb.append("\n").append(refContent);
            }

            // Include attachments from referenced message
            for (Attachment refAttachment : referencedMessage.getAttachments()) {
                if (refAttachment.isImage()) {
                    sb.append("\n[Image: ").append(refAttachment.getFileName()).append("]");
                } else if (refAttachment.isVideo()) {
                    sb.append("\n[Video: ").append(refAttachment.getFileName()).append("]");
                } else {
                    sb.append("\n[Attachment: ").append(refAttachment.getFileName()).append("]");
                }
            }
        }

        // Include forwarded message content using MessageSnapshot (Discord's forward feature)
        // This is the proper way to access forwarded message content in JDA 6.x
        List<MessageSnapshot> snapshots = message.getMessageSnapshots();
        if (!snapshots.isEmpty()) {
            logger.debug("buildUserMessage: processing {} forwarded message snapshots", snapshots.size());
            MessageSnapshot snapshot = snapshots.getFirst(); // Get the first (most recent) snapshot
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("[Forwarded message]");

            // Add the forwarded message content
            String snapshotContent = snapshot.getContentRaw();
            if (!snapshotContent.isBlank()) {
                sb.append("\n").append(snapshotContent);
            }

            // Include embeds from the snapshot
            if (!snapshot.getEmbeds().isEmpty()) {
                for (var embed : snapshot.getEmbeds()) {
                    sb.append("\n[Embed");
                    if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                        sb.append(" from ").append(embed.getAuthor().getName());
                    }
                    sb.append("]");
                    if (embed.getTitle() != null && !embed.getTitle().isBlank()) {
                        sb.append("\n**").append(embed.getTitle()).append("**");
                    }
                    if (embed.getDescription() != null && !embed.getDescription().isBlank()) {
                        sb.append("\n").append(embed.getDescription());
                    }
                    for (var field : embed.getFields()) {
                        sb.append("\n").append(field.getName()).append(": ").append(field.getValue());
                    }
                }
            }

            // Include attachments from the snapshot
            if (!snapshot.getAttachments().isEmpty()) {
                for (Attachment snapshotAttachment : snapshot.getAttachments()) {
                    if (snapshotAttachment.isImage()) {
                        sb.append("\n[Image: ").append(snapshotAttachment.getFileName()).append("]");
                    } else if (snapshotAttachment.isVideo()) {
                        sb.append("\n[Video: ").append(snapshotAttachment.getFileName()).append("]");
                    } else {
                        sb.append("\n[Attachment: ").append(snapshotAttachment.getFileName()).append("]");
                    }
                }
            }

            // Include stickers from the snapshot
            if (!snapshot.getStickers().isEmpty()) {
                sb.append("\n[Stickers: ").append(snapshot.getStickers().size()).append("]");
            }
        }

        // Include embed content (forwarded messages, link previews, etc.)
        if (!message.getEmbeds().isEmpty()) {
            logger.debug("buildUserMessage: processing {} embeds", message.getEmbeds().size());
            for (var embed : message.getEmbeds()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("[Forwarded message");

                // Add author info
                if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                    sb.append(" from ").append(embed.getAuthor().getName());
                }
                sb.append("]");

                // Add title
                if (embed.getTitle() != null && !embed.getTitle().isBlank()) {
                    sb.append("\n**").append(embed.getTitle()).append("**");
                }

                // Add description (main content)
                if (embed.getDescription() != null && !embed.getDescription().isBlank()) {
                    sb.append("\n").append(embed.getDescription());
                }

                // Add fields
                if (!embed.getFields().isEmpty()) {
                    for (var field : embed.getFields()) {
                        sb.append("\n").append(field.getName()).append(": ").append(field.getValue());
                    }
                }

                // Add footer (often contains source info)
                if (embed.getFooter() != null && embed.getFooter().getText() != null) {
                    sb.append("\n_").append(embed.getFooter().getText()).append("_");
                }
            }
        }

        // Include text attachments
        if (brainConfig.getDiscord().isReadTextAttachments()) {
            for (Attachment attachment : message.getAttachments()) {
                if (attachment.getContentType() != null &&
                        attachment.getContentType().startsWith("text/")) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append("[Attachment: ").append(attachment.getFileName()).append("]\n");
                    sb.append("(Text attachment: ").append(attachment.getFileName()).append(")");
                } else if (attachment.isImage()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append("[Image: ").append(attachment.getFileName()).append("]");
                } else if (attachment.isVideo()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append("[Video: ").append(attachment.getFileName()).append("]");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Gather conversation history for the LLM context.
     */
    private List<ConversationTurn> gatherConversationHistory(long userId, long channelId,
                                                              boolean isGuild, long targetId) {
        int limit = brainConfig.getCompletion().getMaxContextMessages();
        List<Map<String, Object>> rawHistory = dialogueHistoryManager.getRecentHistory(
                userId, isGuild, targetId, limit);

        List<ConversationTurn> turns = new ArrayList<>();
        for (Map<String, Object> entry : rawHistory) {
            String userMsg = (String) entry.getOrDefault("user_message", "");
            String botMsg = (String) entry.getOrDefault("bot_response", "");
            if (userMsg != null && !userMsg.isBlank()) {
                turns.add(new ConversationTurn(userMsg, botMsg != null ? botMsg : ""));
            }
        }
        return turns;
    }

    /**
     * Gather passive context for the LLM.
     */
    public String gatherPassiveContext(long channelId, boolean isGuild, long targetId) {
        return getPassiveContext(channelId, isGuild, targetId, 10);
    }

    /**
     * Enrich the user message with passive context and optional recent forwarded content.
     */
    private String enrichMessageWithContext(String userMessage, String passiveContext, String recentContext) {
        StringBuilder sb = new StringBuilder();

        // Add passive context (from database)
        if (passiveContext != null && !passiveContext.isBlank()
                && !passiveContext.equals("No recent context available.")) {
            sb.append(passiveContext);
        }

        // Add recent forwarded content (from same channel, just before the mention)
        if (recentContext != null && !recentContext.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("Recent messages in channel:\n").append(recentContext);
        }

        // Add the current user message
        if (!sb.isEmpty()) {
            sb.append("\n\n---\n\nCurrent message: ");
        }
        sb.append(userMessage);

        return sb.toString();
    }

    /**
     * Ensure the response uses proper Discord Markdown formatting.
     */
    private String ensureDiscordMarkdown(String response) {
        int codeBlockCount = response.split("```", -1).length - 1;
        if (codeBlockCount % 2 != 0) {
            response += "\n```";
        }
        return response;
    }

    /**
     * Truncate the response to fit Discord's message length limit.
     */
    private String truncateForDiscord(String response) {
        Discord discordConfig = brainConfig.getDiscord();
        int maxLength = discordConfig.getMaxMessageLength();

        if (response.length() <= maxLength) {
            return response;
        }

        int lastPeriod = response.lastIndexOf('.', maxLength - 100);
        int lastNewline = response.lastIndexOf('\n', maxLength - 100);

        int truncateAt = Math.max(lastPeriod, lastNewline);
        if (truncateAt < maxLength / 2) {
            truncateAt = maxLength - 3;
        }

        return response.substring(0, truncateAt) + "...";
    }

    /**
     * Strip bot name/nickname from the beginning of a message.
     * This is used when the bot is mentioned by name (not @mention).
     */
    private String stripBotName(String content, MessageReceivedEvent event) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String selfName = event.getJDA().getSelfUser().getName();
        String lowerContent = content.toLowerCase();
        String lowerName = selfName.toLowerCase();

        // Check for nickname in guild
        String nickname = null;
        if (event.isFromGuild()) {
            nickname = event.getGuild().getSelfMember().getNickname();
        }

        String lowerNick = nickname != null ? nickname.toLowerCase() : null;

        // Try to strip nickname first (more specific), then bot name
        String result = tryStripPrefix(lowerContent, lowerNick);
        if (result == null) {
            result = tryStripPrefix(lowerContent, lowerName);
        }

        if (result != null) {
            // Preserve original case by stripping the same length from original content
            int stripLen = content.length() - result.length();
            return content.substring(stripLen).trim();
        }

        return content;
    }

    /**
     * Try to strip a prefix (name/nickname) from the content.
     * Returns the stripped content in lowercase, or null if no match.
     */
    private String tryStripPrefix(String lowerContent, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        // Match patterns like "pudel, ", "pudel: ", "pudel ", "pudel\n"
        if (lowerContent.startsWith(prefix + ", ")
                || lowerContent.startsWith(prefix + ": ")
                || lowerContent.startsWith(prefix + "\n")) {
            return lowerContent.substring(prefix.length() + 2).trim();
        }
        if (lowerContent.startsWith(prefix + " ")) {
            return lowerContent.substring(prefix.length() + 1).trim();
        }
        // Exact match (just the bot name)
        if (lowerContent.equals(prefix)) {
            return "";
        }

        return null;
    }

    /**
     * Store a dialogue exchange after sending a response.
     */
    private void storeDialogueExchange(String userMessage, String botResponse,
                                        long userId, long channelId,
                                        boolean isGuild, long targetId,
                                        long respondToMessageId,
                                        Message userMessageObj) {
        List<String> userAttachmentUrls = entityExtractor.extractAttachmentUrls(userMessageObj);

        storeDialogue(userMessage, botResponse, "chat", userId, channelId, isGuild, targetId,
                respondToMessageId, userAttachmentUrls, List.of());
    }

    // ===============================
    // Attachment Reply Capability
    // ===============================

    /**
     * Send a response with file attachments to a Discord channel.
     * <p>
     * Supports sending images, videos, and other file types as replies.
     * Files can be provided as URLs (downloaded and sent) or as raw byte arrays.
     *
     * @param channel      the Discord channel to send to
     * @param message      the text message to send (can be null if only sending files)
     * @param attachments  list of file attachments to include
     * @param replyToId    the message ID to reply to (0 for no reply)
     */
    public void sendResponseWithAttachments(MessageChannelUnion channel, String message,
                                              List<FileAttachment> attachments, long replyToId) {
        if ((message == null || message.isBlank()) && (attachments == null || attachments.isEmpty())) {
            logger.warn("sendResponseWithAttachments called with no content");
            return;
        }

        try {
            // Build the message action
            var messageAction = channel.sendMessage(message != null ? message : "");

            // Add file uploads
            if (attachments != null && !attachments.isEmpty()) {
                for (FileAttachment file : attachments) {
                    try {
                        InputStream data = file.data();
                        if (data != null) {
                            messageAction = messageAction.addFiles(
                                    FileUpload.fromData(data, file.fileName()));
                        }
                    } catch (Exception e) {
                        logger.error("Error adding file {}: {}", file.fileName(), e.getMessage());
                    }
                }
            }

            // Set reply reference
            if (replyToId > 0) {
                messageAction = messageAction.setMessageReference(replyToId);
            }

            // Send asynchronously
            messageAction.queue(
                    sent -> logger.debug("Response with {} attachments sent", attachments != null ? attachments.size() : 0),
                    error -> logger.error("Failed to send response with attachments: {}", error.getMessage())
            );

        } catch (Exception e) {
            logger.error("Error sending response with attachments: {}", e.getMessage());
        }
    }

    /**
     * Send a response with image attachments.
     * <p>
     * Convenience method for sending images generated or processed by the bot.
     *
     * @param channel   the Discord channel
     * @param message   the text message
     * @param images    list of image data (bytes + filename)
     * @param replyToId the message ID to reply to
     */
    public void sendImageReply(MessageChannelUnion channel, String message,
                                List<FileAttachment> images, long replyToId) {
        sendResponseWithAttachments(channel, message, images, replyToId);
    }

    /**
     * Send a response with a video attachment.
     *
     * @param channel   the Discord channel
     * @param message   the text message
     * @param videoData the video file data
     * @param fileName  the video file name
     * @param replyToId the message ID to reply to
     */
    public void sendVideoReply(MessageChannelUnion channel, String message,
                                byte[] videoData, String fileName, long replyToId) {
        List<FileAttachment> attachments = List.of(
                new FileAttachment(fileName, new ByteArrayInputStream(videoData)));
        sendResponseWithAttachments(channel, message, attachments, replyToId);
    }

    /**
     * Download a file from a URL and return it as a FileAttachment.
     * <p>
     * Useful for downloading images/videos from URLs to re-send them.
     *
     * @param url      the URL to download from
     * @param fileName the file name to use
     * @return the file attachment, or null on error
     */
    public FileAttachment downloadFile(String url, String fileName) {
        try {
            URL fileUrl = URI.create(url).toURL();
            try (InputStream is = fileUrl.openStream()) {
                byte[] data = is.readAllBytes();
                return new FileAttachment(fileName, new ByteArrayInputStream(data));
            }
        } catch (Exception e) {
            logger.error("Error downloading file from {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ===============================
    // Status & Diagnostics
    // ===============================

    /**
     * Check if the Ollama server is available.
     */
    public boolean isOllamaAvailable() {
        return ollamaClient.isServerReachable();
    }

    /**
     * Check if the configured Ollama model is available.
     */
    public boolean isModelAvailable() {
        return ollamaClient.isModelAvailable();
    }

    /**
     * Get passive context queue statistics.
     */
    public PassiveContextProcessor.QueueStats getPassiveContextStats() {
        return passiveContextProcessor.getStats();
    }

    /**
     * Get the brain configuration.
     */
    public PudelBrainConfig getConfig() {
        return brainConfig;
    }

    /**
     * Get the Ollama client for external use.
     */
    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }

    /**
     * Get the entity extractor for external use.
     */
    public EntityExtractor getEntityExtractor() {
        return entityExtractor;
    }

    /**
     * Get the memory manager for external use.
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Get the dialogue history manager for external use.
     */
    public DialogueHistoryManager getDialogueHistoryManager() {
        return dialogueHistoryManager;
    }

    /**
     * Check if the LLM-based text analyzer is available.
     * In the new brain, this checks if Ollama is reachable.
     */
    public boolean isLLMAnalyzerAvailable() {
        return ollamaClient.isServerReachable();
    }

    /**
     * Analyze text using the brain's text analysis capabilities.
     * <p>
     * Provides basic pattern-based analysis. For full LLM-based analysis,
     * use the Ollama client directly.
     *
     * @param text the text to analyze
     * @return the text analysis result
     */
    public TextAnalysis analyzeText(String text) {
        if (text == null || text.isBlank()) {
            return new TextAnalysis("en", "unknown", 0.0, "neutral",
                    Map.of(), List.of(), false, false, false, false);
        }

        String lower = text.toLowerCase().trim();

        // Detect intent
        String intent = "chat";
        if (lower.matches("^(hi|hello|hey|howdy|greetings|sup|yo).*")) {
            intent = "greeting";
        } else if (lower.matches("^(bye|goodbye|see you|farewell|cya|gtg).*")) {
            intent = "farewell";
        } else if (lower.endsWith("?") || lower.startsWith("what") || lower.startsWith("how")
                || lower.startsWith("why") || lower.startsWith("when") || lower.startsWith("where")
                || lower.startsWith("who") || lower.startsWith("can you")
                || lower.startsWith("could you")) {
            intent = "question";
        } else if (lower.matches("^(please |help |could you |would you |can you ).*")) {
            intent = "help";
        }

        // Detect sentiment
        String sentiment = "neutral";
        if (lower.contains("thank") || lower.contains("great") || lower.contains("awesome")
                || lower.contains("love") || lower.contains("amazing") || lower.contains("wonderful")
                || lower.contains("happy") || lower.contains("good") || lower.contains("nice")) {
            sentiment = "positive";
        } else if (lower.contains("hate") || lower.contains("bad") || lower.contains("terrible")
                || lower.contains("awful") || lower.contains("sad") || lower.contains("angry")
                || lower.contains("stupid") || lower.contains("worst") || lower.contains("horrible")) {
            sentiment = "negative";
        }

        // Extract simple entities (mentions, channels, URLs)
        Map<String, List<String>> entities = new HashMap<>();
        Pattern mentionPattern = Pattern.compile("<@!?(\\d+)>");
        Matcher matcher = mentionPattern.matcher(text);
        List<String> mentions = new ArrayList<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        if (!mentions.isEmpty()) {
            entities.put("users", mentions);
        }

        // Extract keywords (simple word extraction)
        List<String> keywords = Arrays.stream(lower.split("\\s+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !Set.of("this", "that", "with", "from", "have", "been", "were", "they", "them", "their", "what", "when", "where", "which", "while", "about", "would", "could", "should").contains(w))
                .limit(5)
                .toList();

        boolean isQuestion = intent.equals("question") || lower.endsWith("?");
        boolean isCommand = intent.equals("help") || lower.startsWith("!");
        boolean isGreeting = intent.equals("greeting");
        boolean isFarewell = intent.equals("farewell");

        return new TextAnalysis(
                "en", intent, 0.7, sentiment, entities, keywords,
                isQuestion, isCommand, isGreeting, isFarewell
        );
    }
}
