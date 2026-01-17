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
package worldstandard.group.pudel.core.brain.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.brain.PudelBrain.EnrichedContext;
import worldstandard.group.pudel.core.brain.memory.MemoryManager.MemoryEntry;
import worldstandard.group.pudel.core.brain.personality.PersonalityEngine;
import worldstandard.group.pudel.core.brain.personality.PersonalityEngine.PersonalityProfile;
import worldstandard.group.pudel.core.service.ChatbotService.PudelPersonality;
import worldstandard.group.pudel.model.PudelModelService;
import worldstandard.group.pudel.model.PudelModelService.*;
import worldstandard.group.pudel.model.analyzer.TextAnalysis;

import java.util.*;

/**
 * Response Generator for Pudel's Brain.
 * <p>
 * Generates contextual responses based on:
 * - Text analysis (intent, sentiment, entities via LangChain4j)
 * - Retrieved memories
 * - Personality profile
 * - Conversation history
 * <p>
 * Primary mode: Ollama LLM for intelligent responses
 * Fallback mode: Template-based responses (no external API)
 */
@Component
public class ResponseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGenerator.class);

    private final PersonalityEngine personalityEngine;
    private final PudelModelService modelService;

    // Response templates by intent (fallback mode)
    private final Map<String, List<String>> intentTemplates = new HashMap<>();

    // Contextual connectors
    private final List<String> memoryConnectors = Arrays.asList(
            "I remember that ",
            "Previously, ",
            "From what I recall, ",
            "As I mentioned before, ",
            "I believe we discussed that "
    );

    public ResponseGenerator(PersonalityEngine personalityEngine,
                             @Lazy PudelModelService modelService) {
        this.personalityEngine = personalityEngine;
        this.modelService = modelService;
        initializeTemplates();
    }

    private void initializeTemplates() {
        // Greeting templates
        intentTemplates.put("greeting", Arrays.asList(
                "Hello! How can I help you today?",
                "Hi there! What can I do for you?",
                "Hey! Nice to see you!",
                "Greetings! How may I assist you?"
        ));

        // Farewell templates
        intentTemplates.put("farewell", Arrays.asList(
                "Goodbye! Take care!",
                "See you later!",
                "Bye! Feel free to come back anytime!",
                "Farewell! Have a great day!"
        ));

        // Thanks templates
        intentTemplates.put("thanks", Arrays.asList(
                "You're welcome!",
                "Happy to help!",
                "Anytime!",
                "Glad I could assist!"
        ));

        // Help templates
        intentTemplates.put("help", Arrays.asList(
                "I'm here to help! What do you need assistance with?",
                "Sure, I can help with that. What specifically would you like to know?",
                "I'd be happy to help! Could you tell me more about what you need?",
                "Of course! Let me know what you're looking for."
        ));

        // Question templates (generic)
        intentTemplates.put("question", Arrays.asList(
                "That's an interesting question! ",
                "Let me think about that... ",
                "Good question! ",
                "I'll do my best to answer that. "
        ));

        // Information request templates
        intentTemplates.put("information", Arrays.asList(
                "Let me tell you about that...",
                "Here's what I know: ",
                "I can share some information on that. ",
                "Based on what I know, "
        ));

        // Affirmation templates
        intentTemplates.put("affirmation", Arrays.asList(
                "Great! ",
                "Understood! ",
                "Perfect! ",
                "Alright! "
        ));

        // Negation templates
        intentTemplates.put("negation", Arrays.asList(
                "I see, no problem. ",
                "Alright, understood. ",
                "Okay, let me know if you change your mind. ",
                "No worries! "
        ));

        // Command templates
        intentTemplates.put("command", Arrays.asList(
                "I'll help you with that setting. ",
                "Let me process that for you. ",
                "Working on that now. ",
                "I can help you configure that. "
        ));

        // Default/chat templates
        intentTemplates.put("chat", Arrays.asList(
                "I understand. ",
                "I see what you mean. ",
                "That's interesting! ",
                "Tell me more about that. "
        ));
    }

    /**
     * Generate a response based on context and personality.
     * Tries Ollama LLM first, falls back to template-based responses.
     */
    public String generate(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        try {
            // Try Ollama LLM first for intelligent responses
            if (modelService != null && modelService.isAvailable()) {
                String ollamaResponse = tryOllamaGeneration(userMessage, context, profile);
                if (ollamaResponse != null) {
                    // Format response for Discord output
                    String formattedResponse = modelService.formatResponseForDiscord(ollamaResponse);
                    logger.debug("Generated response via Ollama LLM");
                    return formattedResponse;
                }
            }

            // Fallback to template-based response generation
            logger.debug("Using template-based response generation");
            return generateTemplateResponse(userMessage, context, profile);

        } catch (Exception e) {
            logger.error("Error generating response: {}", e.getMessage(), e);
            return personalityEngine.getErrorResponse(context.getPersonality());
        }
    }

    /**
     * Try generating response via Ollama LLM.
     */
    private String tryOllamaGeneration(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        try {
            PudelPersonality personality = context.getPersonality();

            // Build personality traits with all natural behavior fields
            PersonalityTraits traits = new PersonalityTraits(
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

            // Build conversation history from context
            // Note: History comes in DESC order (most recent first), we need chronological order
            List<Map<String, Object>> historyList = new ArrayList<>(context.getHistory());
            Collections.reverse(historyList); // Reverse to chronological order

            List<ConversationTurn> conversationHistory = historyList.stream()
                    .filter(h -> h.containsKey("user_message") && h.containsKey("bot_response"))
                    .map(h -> new ConversationTurn(
                            (String) h.get("user_message"),
                            (String) h.get("bot_response")
                    ))
                    .limit(5) // Limit context to last 5 turns
                    .toList();

            // Build memory context from relevant memories
            List<MemoryContext> memoryContext = context.relevantMemories().stream()
                    .map(m -> new MemoryContext(
                            m.content(),
                            m.type(),
                            m.timestamp() != null ? m.timestamp().toString() : null,
                            m.relevance()
                    ))
                    .limit(5)
                    .toList();

            // Build generation request
            GenerationRequest request = GenerationRequest.builder()
                    .userMessage(userMessage)
                    .personality(traits)
                    .conversationHistory(conversationHistory)
                    .relevantMemories(memoryContext)
                    .intent(context.textAnalysis().intent())
                    .sentiment(context.textAnalysis().sentiment())
                    .build();

            // Call model service
            GenerationResponse response = modelService.generateResponse(request);

            if (response.success() && response.response() != null) {
                return response.response();
            }

        } catch (Exception e) {
            logger.debug("Ollama generation failed, falling back to templates: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Generate template-based response (fallback mode).
     */
    private String generateTemplateResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        TextAnalysis analysis = context.textAnalysis();
        PudelPersonality personality = context.getPersonality();

        // Handle specific intents with personality
        String response = switch (analysis.intent()) {
            case "greeting" -> personalityEngine.getGreeting(personality, null);
            case "farewell" -> personalityEngine.getFarewell(personality, null);
            case "thanks" -> personalityEngine.getThanksResponse(personality);
            case "help" -> generateHelpResponse(userMessage, context, profile);
            case "question" -> generateQuestionResponse(userMessage, context, profile);
            case "information" -> generateInformationResponse(userMessage, context, profile);
            case "command" -> generateCommandResponse(userMessage, context, profile);
            default -> generateChatResponse(userMessage, context, profile);
        };

        // Apply personality styling
        response = personalityEngine.applyPersonalityStyle(response, profile);

        // Apply quirks
        response = applyQuirks(response, profile);

        return response;
    }

    /**
     * Generate a help response.
     */
    private String generateHelpResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        StringBuilder response = new StringBuilder();
        response.append(getRandomTemplate("help"));

        // Check if there are relevant memories about help topics
        List<MemoryEntry> memories = context.relevantMemories();
        if (!memories.isEmpty()) {
            response.append("\n\n");
            response.append(getRandomMemoryConnector());
            response.append(summarizeMemories(memories, 2));
        }

        // Add context-aware suggestion
        if (userMessage.toLowerCase().contains("command")) {
            response.append("\n\nYou can use commands by typing the prefix followed by the command name. Try `help` for a list of available commands!");
        } else if (userMessage.toLowerCase().contains("setting") || userMessage.toLowerCase().contains("config")) {
            response.append("\n\nFor settings, you can use the `settings` command to configure various options.");
        }

        return response.toString();
    }

    /**
     * Generate a response to a question.
     */
    private String generateQuestionResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        StringBuilder response = new StringBuilder();
        response.append(getRandomTemplate("question"));

        // Check for relevant memories
        List<MemoryEntry> memories = context.relevantMemories();
        if (!memories.isEmpty()) {
            response.append(getRandomMemoryConnector());
            response.append(summarizeMemories(memories, 3));
        } else {
            // No relevant memories - give a generic but thoughtful response
            response.append(generateThoughtfulResponse(userMessage, context, profile));
        }

        return response.toString();
    }

    /**
     * Generate an information response.
     */
    private String generateInformationResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        StringBuilder response = new StringBuilder();
        response.append(getRandomTemplate("information"));

        // Extract what information is being requested
        String topic = extractTopic(userMessage);

        // Check memories for information about the topic
        List<MemoryEntry> memories = context.relevantMemories();
        if (!memories.isEmpty()) {
            response.append("\n");
            for (MemoryEntry memory : memories) {
                if (memory.content().length() < 200) {
                    response.append("• ").append(memory.content()).append("\n");
                }
            }
        } else {
            response.append("I don't have specific information about that stored, but I'm happy to learn more if you share!");
        }

        return response.toString();
    }

    /**
     * Generate a response for command-like messages.
     */
    private String generateCommandResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        StringBuilder response = new StringBuilder();
        response.append(getRandomTemplate("command"));

        // Suggest using the command prefix
        response.append("\n\nIf you're trying to use a command, make sure to use the command prefix. ");
        response.append("For example: `!help` or `!settings`");

        return response.toString();
    }

    /**
     * Generate a general chat response.
     */
    private String generateChatResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        TextAnalysis analysis = context.textAnalysis();
        List<MemoryEntry> memories = context.relevantMemories();

        StringBuilder response = new StringBuilder();

        // Check sentiment and respond appropriately
        if ("positive".equals(analysis.sentiment())) {
            response.append(getPositiveAcknowledgement(profile));
        } else if ("negative".equals(analysis.sentiment())) {
            response.append(getNegativeAcknowledgement(profile));
        }

        // If we have relevant context/memories, use them
        if (!memories.isEmpty()) {
            if (!response.isEmpty()) response.append(" ");
            response.append(getRandomMemoryConnector());
            response.append(summarizeMemories(memories, 2));
        } else {
            // No memories - generate a conversational response
            if (response.isEmpty()) {
                response.append(getRandomTemplate("chat"));
            }
            response.append(generateThoughtfulResponse(userMessage, context, profile));
        }

        // Add a follow-up question occasionally to encourage conversation
        if (Math.random() < 0.3 && !analysis.isQuestion()) {
            response.append("\n\n").append(getFollowUpQuestion(userMessage, profile));
        }

        return response.toString();
    }

    /**
     * Generate a thoughtful response based on the message content.
     */
    private String generateThoughtfulResponse(String userMessage, EnrichedContext context, PersonalityProfile profile) {
        String lower = userMessage.toLowerCase();
        StringBuilder response = new StringBuilder();

        // Check for common topics and provide contextual responses
        if (lower.contains("bot") || lower.contains("pudel")) {
            response.append("I'm Pudel, your friendly Discord assistant! ");
            if (profile.hasTrait("maid")) {
                response.append("I'm here to serve and assist with anything you need. ");
            }
        } else if (lower.contains("discord") || lower.contains("server") || lower.contains("guild")) {
            response.append("I can help with various Discord-related tasks and commands. ");
        } else if (lower.contains("game") || lower.contains("play")) {
            response.append("That sounds fun! While I can't play games directly, I can help manage your gaming community! ");
        } else if (lower.contains("music") || lower.contains("song")) {
            response.append("Music is wonderful! Unfortunately, I don't have music playback features right now. ");
        } else if (lower.contains("weather") || lower.contains("time")) {
            response.append("I don't have access to external APIs for real-time data like weather or time, but I can help with other things! ");
        } else if (lower.contains("joke") || lower.contains("funny")) {
            response.append("I'm still working on my comedy skills! Maybe try asking me to help with something else? ");
        } else {
            // Generic response based on message length
            if (userMessage.length() > 100) {
                response.append("That's quite detailed! I appreciate you sharing that with me. ");
            } else if (userMessage.length() > 50) {
                response.append("I see what you're saying. ");
            } else {
                response.append("Alright! ");
            }
        }

        return response.toString();
    }

    /**
     * Extract the main topic from a message.
     */
    private String extractTopic(String message) {
        // Remove question words and common words to find the topic
        String topic = message.replaceAll("(?i)\\b(what|who|where|when|why|how|tell|me|about|is|are|the|a|an)\\b", "")
                .trim();
        return topic.isEmpty() ? message : topic;
    }

    /**
     * Summarize memory entries into a response.
     */
    private String summarizeMemories(List<MemoryEntry> memories, int maxEntries) {
        StringBuilder summary = new StringBuilder();
        int count = 0;

        for (MemoryEntry memory : memories) {
            if (count >= maxEntries) break;

            String content = memory.content();
            // Truncate long content
            if (content.length() > 150) {
                content = content.substring(0, 147) + "...";
            }

            if (count > 0) {
                summary.append(" Also, ");
            }
            summary.append(content);
            count++;
        }

        return summary.toString();
    }

    /**
     * Get a positive sentiment acknowledgement.
     */
    private String getPositiveAcknowledgement(PersonalityProfile profile) {
        List<String> responses;
        if (profile.hasTrait("maid")) {
            responses = Arrays.asList(
                    "How delightful! ",
                    "That brings me joy to hear! ",
                    "Wonderful! "
            );
        } else if (profile.hasTrait("tsundere")) {
            responses = Arrays.asList(
                    "W-well, that's good I suppose... ",
                    "Hmph, I'm glad... not that I care or anything! ",
                    "That's... nice. "
            );
        } else {
            responses = Arrays.asList(
                    "That's great! ",
                    "Awesome! ",
                    "Nice! "
            );
        }
        return responses.get(new Random().nextInt(responses.size()));
    }

    /**
     * Get a negative sentiment acknowledgement.
     */
    private String getNegativeAcknowledgement(PersonalityProfile profile) {
        List<String> responses;
        if (profile.hasTrait("maid")) {
            responses = Arrays.asList(
                    "I'm sorry to hear that. How may I help make things better? ",
                    "That's unfortunate. Please let me know if there's anything I can do. ",
                    "I understand your concern. "
            );
        } else if (profile.hasTrait("tsundere")) {
            responses = Arrays.asList(
                    "That's... I'm sorry, okay? ",
                    "I-I didn't mean for things to be difficult... ",
                    "Don't worry, I'll help... not because I want to or anything! "
            );
        } else {
            responses = Arrays.asList(
                    "I'm sorry to hear that. ",
                    "That doesn't sound great. ",
                    "I understand. "
            );
        }
        return responses.get(new Random().nextInt(responses.size()));
    }

    /**
     * Get a follow-up question to encourage conversation.
     */
    private String getFollowUpQuestion(String message, PersonalityProfile profile) {
        List<String> questions;
        if (profile.hasTrait("maid")) {
            questions = Arrays.asList(
                    "Is there anything else I may assist you with?",
                    "Would you like me to help with anything else?",
                    "May I be of further service?"
            );
        } else if (profile.hasTrait("curious")) {
            questions = Arrays.asList(
                    "That's interesting! Can you tell me more?",
                    "I'm curious - what made you think of that?",
                    "Oh? I'd love to hear more about that!"
            );
        } else {
            questions = Arrays.asList(
                    "Is there anything else you'd like to know?",
                    "What else would you like to talk about?",
                    "Anything else on your mind?"
            );
        }
        return questions.get(new Random().nextInt(questions.size()));
    }

    /**
     * Apply personality quirks to the response.
     */
    private String applyQuirks(String response, PersonalityProfile profile) {
        if (profile.hasQuirk("nya") && Math.random() < 0.3) {
            response = response.replace("n", "ny").replace("N", "Ny");
            if (!response.endsWith("~")) {
                response += " nya~";
            }
        }

        if (profile.hasQuirk("tilde") && !response.endsWith("~") && Math.random() < 0.4) {
            response = response.replaceAll("!$", "~!").replaceAll("\\.$", "~");
        }

        if (profile.hasQuirk("stutter") && Math.random() < 0.2) {
            // Add stutter to the first word
            String[] words = response.split(" ", 2);
            if (words.length > 0 && words[0].length() > 1) {
                words[0] = words[0].charAt(0) + "-" + words[0];
                response = String.join(" ", words);
            }
        }

        return response;
    }

    /**
     * Get a random template for an intent.
     */
    private String getRandomTemplate(String intent) {
        List<String> templates = intentTemplates.getOrDefault(intent, intentTemplates.get("chat"));
        return templates.get(new Random().nextInt(templates.size()));
    }

    /**
     * Get a random memory connector phrase.
     */
    private String getRandomMemoryConnector() {
        return memoryConnectors.get(new Random().nextInt(memoryConnectors.size()));
    }
}

