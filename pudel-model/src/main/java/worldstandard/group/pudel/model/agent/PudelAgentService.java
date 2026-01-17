/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.model.agent;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.model.PudelModelService.PersonalityTraits;
import worldstandard.group.pudel.model.config.OllamaConfig;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pudel AI Agent Service - The brain that can act autonomously.
 * <p>
 * This service uses LangChain4j's AI Services to create an agent that can:
 * - Understand user intent through natural conversation
 * - Execute database operations (create tables, store/retrieve data)
 * - Manage guild-specific data like a personal secretary
 * <p>
 * The agent uses Tool annotations to define actions it can take,
 * allowing the LLM to decide when and how to use them based on context.
 */
@Service
public class PudelAgentService {

    private static final Logger logger = LoggerFactory.getLogger(PudelAgentService.class);

    private final OllamaConfig ollamaConfig;

    private OllamaChatModel chatModel;
    private boolean agentAvailable = false;

    // Cache for per-session chat memories (key: "guildId:userId" or "user:userId")
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

    public PudelAgentService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
    }

    @PostConstruct
    public void initialize() {
        if (!ollamaConfig.isEnabled()) {
            logger.info("Ollama disabled, agent functionality will not be available");
            return;
        }

        try {
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(ollamaConfig.getBaseUrl())
                    .modelName(ollamaConfig.getModel())
                    .timeout(Duration.ofSeconds(ollamaConfig.getTimeoutSeconds()))
                    .temperature(0.7)
                    .build();

            // Test connection - OllamaChatModel doesn't have simple generate, it needs a list of messages
            // Just mark as available if build succeeds
            agentAvailable = true;
            logger.info("Pudel Agent Service initialized with model: {}", ollamaConfig.getModel());
        } catch (Exception e) {
            logger.error("Failed to initialize agent service: {}", e.getMessage());
            agentAvailable = false;
        }
    }

    /**
     * Process a message as an AI agent with tool capabilities.
     *
     * @param userMessage The user's message
     * @param personality The bot's personality configuration
     * @param dataExecutor The executor for database operations
     * @param targetId Guild or User ID
     * @param isGuild Whether this is a guild context
     * @param requestingUserId The user making the request
     * @param conversationHistory Recent conversation for context
     * @return The agent's response (may include tool executions)
     */
    public AgentResponse processAsAgent(
            String userMessage,
            PersonalityTraits personality,
            AgentDataExecutor dataExecutor,
            long targetId,
            boolean isGuild,
            long requestingUserId,
            List<Map<String, Object>> conversationHistory) {

        if (!agentAvailable) {
            return new AgentResponse(null, false, "Agent not available", List.of());
        }

        try {
            // Create tools for this context
            PudelAgentTools tools = new PudelAgentTools(dataExecutor, targetId, isGuild, requestingUserId);

            // Get or create session memory
            String sessionKey = (isGuild ? "guild:" : "user:") + targetId + ":" + requestingUserId;
            ChatMemory memory = sessionMemories.computeIfAbsent(sessionKey,
                    k -> MessageWindowChatMemory.withMaxMessages(20));

            // Create the AI service with tools
            PudelAssistant assistant = AiServices.builder(PudelAssistant.class)
                    .chatModel(chatModel)
                    .chatMemory(memory)
                    .tools(tools)
                    .build();

            // Build system message with personality
            String systemPrompt = buildAgentSystemPrompt(personality, isGuild);

            // Add conversation history context if available
            StringBuilder contextBuilder = new StringBuilder();
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                contextBuilder.append("\n\nRecent conversation context:\n");
                for (Map<String, Object> turn : conversationHistory) {
                    if (turn.containsKey("user_message")) {
                        contextBuilder.append("User: ").append(turn.get("user_message")).append("\n");
                    }
                    if (turn.containsKey("bot_response")) {
                        contextBuilder.append("You: ").append(turn.get("bot_response")).append("\n");
                    }
                }
            }

            // Execute agent
            String response = assistant.chat(systemPrompt + contextBuilder, userMessage);

            return new AgentResponse(response, true, null, List.of());

        } catch (Exception e) {
            logger.error("Error in agent processing: {}", e.getMessage(), e);
            return new AgentResponse(null, false, e.getMessage(), List.of());
        }
    }

    /**
     * Check if a message likely needs agent capabilities.
     * This helps decide whether to route to simple chat or full agent processing.
     */
    public boolean shouldUseAgent(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();

        // Keywords that suggest data management intent
        return lower.contains("remember") ||
               lower.contains("store") ||
               lower.contains("save") ||
               lower.contains("create") && (lower.contains("table") || lower.contains("list") || lower.contains("document")) ||
               lower.contains("add") && (lower.contains("note") || lower.contains("document") || lower.contains("entry")) ||
               lower.contains("search") ||
               lower.contains("find") && (lower.contains("my") || lower.contains("the") || lower.contains("all")) ||
               lower.contains("show") && (lower.contains("my") || lower.contains("all") || lower.contains("list")) ||
               lower.contains("delete") ||
               lower.contains("remove") ||
               lower.contains("update") ||
               lower.contains("edit") ||
               lower.contains("archive") ||
               lower.contains("record") ||
               lower.contains("keep track") ||
               lower.contains("what did i") ||
               lower.contains("recall") ||
               lower.contains("do you remember");
    }

    /**
     * Build the system prompt for agent mode.
     */
    private String buildAgentSystemPrompt(PersonalityTraits personality, boolean isGuild) {
        StringBuilder prompt = new StringBuilder();

        // Disable thinking for compatible models
        if (ollamaConfig.isDisableThinking()) {
            prompt.append("/no_think\n\n");
        }

        String name = personality != null && personality.nickname() != null ? personality.nickname() : "Pudel";

        prompt.append("You are ").append(name).append(", an AI maid/secretary assistant for ");
        prompt.append(isGuild ? "this Discord server" : "your master").append(".\n\n");

        prompt.append("""
                ## Your Role
                You are a personal assistant who can manage data and information for your master.
                You have access to tools that let you:
                - Create tables to organize different types of information
                - Store documents, notes, news, and any data your master wants to keep
                - Search and retrieve stored information
                - Remember important facts and recall them later
                
                ## How to Act
                - When your master wants to store something, use the appropriate tool
                - When asked to find or show something, search the relevant table
                - Be proactive in organizing information (e.g., suggest creating a table if it doesn't exist)
                - Always confirm what you've done after using a tool
                - If unsure which table to use, ask or list available tables
                
                ## Important Rules
                - Never expose raw SQL or technical details to users
                - Speak naturally as a helpful maid/secretary
                - Use tools when the intent is to manage data; just chat normally for casual conversation
                
                """);

        // Add personality traits if available
        if (personality != null) {
            if (personality.biography() != null && !personality.biography().isBlank()) {
                prompt.append("## Your Background\n").append(personality.biography()).append("\n\n");
            }
            if (personality.personality() != null && !personality.personality().isBlank()) {
                prompt.append("## Your Personality\n").append(personality.personality()).append("\n\n");
            }
            if (personality.dialogueStyle() != null && !personality.dialogueStyle().isBlank()) {
                prompt.append("## Your Speaking Style\n").append(personality.dialogueStyle()).append("\n\n");
            }
        }

        return prompt.toString();
    }

    /**
     * Clear session memory for a context.
     */
    public void clearSessionMemory(long targetId, boolean isGuild, long userId) {
        String sessionKey = (isGuild ? "guild:" : "user:") + targetId + ":" + userId;
        sessionMemories.remove(sessionKey);
    }

    /**
     * Check if agent is available.
     */
    public boolean isAvailable() {
        return agentAvailable;
    }

    // ===========================================
    // Response DTO
    // ===========================================

    public record AgentResponse(
            String response,
            boolean success,
            String error,
            List<String> toolsUsed
    ) {}

    // ===========================================
    // AI Service Interface (for LangChain4j)
    // ===========================================

    public interface PudelAssistant {
        @dev.langchain4j.service.SystemMessage("{{systemMessage}}")
        @dev.langchain4j.service.UserMessage("{{userMessage}}")
        String chat(@dev.langchain4j.service.V("systemMessage") String systemMessage,
                    @dev.langchain4j.service.V("userMessage") String userMessage);
    }
}

