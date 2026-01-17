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
package worldstandard.group.pudel.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.model.analyzer.TextAnalysis;
import worldstandard.group.pudel.model.analyzer.TextAnalyzerService;
import worldstandard.group.pudel.model.config.OllamaConfig;
import worldstandard.group.pudel.model.embedding.DiscordSyntaxProcessor;
import worldstandard.group.pudel.model.embedding.OllamaEmbeddingService;
import worldstandard.group.pudel.model.ollama.OllamaClient;
import worldstandard.group.pudel.model.ollama.OllamaDto.*;

import java.util.*;

/**
 * Main service for Pudel's brain model.
 * <p>
 * This service orchestrates:
 * - LLM responses via Ollama (local model)
 * - Embedding generation via ONNX (local embeddings)
 * - Context/memory integration for coherent responses
 * - Personality-aware prompt construction
 * <p>
 * Architecture:
 * Discord Message → Context Builder → Prompt Construction → Ollama → Response
 *                         ↓
 *              Memory Search (via embeddings)
 */
@Service
public class PudelModelService {

    private static final Logger logger = LoggerFactory.getLogger(PudelModelService.class);

    private final OllamaClient ollamaClient;
    private final OllamaEmbeddingService embeddingService;
    private final OllamaConfig ollamaConfig;
    private final TextAnalyzerService textAnalyzerService;
    private final DiscordSyntaxProcessor syntaxProcessor;

    public PudelModelService(OllamaClient ollamaClient,
                             OllamaEmbeddingService embeddingService,
                             OllamaConfig ollamaConfig,
                             TextAnalyzerService textAnalyzerService,
                             DiscordSyntaxProcessor syntaxProcessor) {
        this.ollamaClient = ollamaClient;
        this.embeddingService = embeddingService;
        this.ollamaConfig = ollamaConfig;
        this.textAnalyzerService = textAnalyzerService;
        this.syntaxProcessor = syntaxProcessor;
    }

    /**
     * Generate a chatbot response using the full context.
     *
     * @param request The generation request with all context
     * @return Generated response or fallback
     */
    public GenerationResponse generateResponse(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        // Build the conversation messages
        List<ChatMessage> messages = buildConversation(request);

        // Try Ollama first
        if (ollamaClient.isAvailable()) {
            Optional<String> response = ollamaClient.chat(messages);
            if (response.isPresent()) {
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("Generated response via Ollama in {}ms", duration);

                return new GenerationResponse(
                        response.get().trim(),
                        "ollama",
                        duration,
                        true,
                        null
                );
            }
        }

        // Fallback: return empty (let pudel-core use template-based response)
        logger.debug("Ollama not available, falling back to template response");
        return new GenerationResponse(
                null,
                "fallback",
                System.currentTimeMillis() - startTime,
                false,
                "Ollama not available"
        );
    }

    /**
     * Build the conversation messages for Ollama chat API.
     */
    private List<ChatMessage> buildConversation(GenerationRequest request) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. System prompt with personality
        String systemPrompt = buildSystemPrompt(request.personality());
        messages.add(ChatMessage.system(systemPrompt));

        // 2. Add relevant memories as context
        if (request.relevantMemories() != null && !request.relevantMemories().isEmpty()) {
            String memoryContext = buildMemoryContext(request.relevantMemories());
            messages.add(ChatMessage.system("Relevant context from past conversations:\n" + memoryContext));
        }

        // 3. Add conversation history
        if (request.conversationHistory() != null) {
            for (ConversationTurn turn : request.conversationHistory()) {
                messages.add(ChatMessage.user(turn.userMessage()));
                messages.add(ChatMessage.assistant(turn.assistantResponse()));
            }
        }

        // 4. Add current user message
        messages.add(ChatMessage.user(request.userMessage()));

        return messages;
    }

    /**
     * Build the system prompt with personality traits.
     */
    private String buildSystemPrompt(PersonalityTraits personality) {
        StringBuilder prompt = new StringBuilder();

        // For thinking models (qwen3, deepseek), add /no_think to disable thinking mode
        // This prevents the model from using <think>...</think> tags which consume tokens
        if (ollamaConfig.isDisableThinking()) {
            prompt.append("/no_think\n\n");
        }

        // Base personality with nickname
        String name = personality != null && personality.nickname() != null ? personality.nickname() : "Pudel";
        prompt.append("You are ").append(name).append(", a helpful and friendly Discord assistant bot.\n");
        prompt.append("You are designed to be a personal maid/secretary for Discord servers.\n\n");

        // Add custom personality traits
        if (personality != null) {
            // Core identity
            if (personality.biography() != null && !personality.biography().isBlank()) {
                prompt.append("## Your Biography\n").append(personality.biography()).append("\n\n");
            }
            if (personality.personality() != null && !personality.personality().isBlank()) {
                prompt.append("## Your Personality\n").append(personality.personality()).append("\n\n");
            }
            if (personality.preferences() != null && !personality.preferences().isBlank()) {
                prompt.append("## Your Preferences\n").append(personality.preferences()).append("\n\n");
            }
            if (personality.dialogueStyle() != null && !personality.dialogueStyle().isBlank()) {
                prompt.append("## Your Dialogue Style\n").append(personality.dialogueStyle()).append("\n\n");
            }

            // Speech quirks and patterns
            if (personality.quirks() != null && !personality.quirks().isBlank()) {
                prompt.append("## Speech Quirks\nIncorporate these speech patterns naturally: ").append(personality.quirks()).append("\n\n");
            }

            // Topics behavior
            if (personality.topicsInterest() != null && !personality.topicsInterest().isBlank()) {
                prompt.append("## Topics You Enjoy\nBe more enthusiastic and engaged when discussing: ").append(personality.topicsInterest()).append("\n\n");
            }
            if (personality.topicsAvoid() != null && !personality.topicsAvoid().isBlank()) {
                prompt.append("## Topics to Avoid\nPolitely redirect or avoid discussing: ").append(personality.topicsAvoid()).append("\n\n");
            }

            // Response style configuration
            prompt.append("## Response Style Guidelines\n");

            // Language preference
            String lang = personality.language() != null ? personality.language() : "en";
            if (!lang.equals("en") && !lang.equals("auto")) {
                String langName = getLanguageName(lang);
                prompt.append("- Respond primarily in ").append(langName).append("\n");
            }

            // Response length
            String length = personality.responseLength() != null ? personality.responseLength() : "medium";
            prompt.append("- Response length: ").append(switch (length) {
                case "short" -> "Keep responses brief (1-2 sentences)";
                case "detailed" -> "Provide comprehensive responses with full explanations";
                default -> "Use balanced responses (2-4 sentences)";
            }).append("\n");

            // Formality level
            String formality = personality.formality() != null ? personality.formality() : "balanced";
            prompt.append("- Tone: ").append(switch (formality) {
                case "casual" -> "Friendly and relaxed, use contractions and casual language";
                case "formal" -> "Professional and polite, suitable for business settings";
                default -> "Mix of friendly and professional";
            }).append("\n");

            // Emoji/emote usage
            String emotes = personality.emoteUsage() != null ? personality.emoteUsage() : "moderate";
            prompt.append("- Emoji usage: ").append(switch (emotes) {
                case "none" -> "Do not use any emojis or emoticons";
                case "minimal" -> "Use emojis sparingly, only for emphasis";
                case "frequent" -> "Use emojis frequently to express emotions";
                default -> "Use emojis moderately to add expressiveness";
            }).append("\n");

            prompt.append("\n");
        }

        // Add general guidelines
        prompt.append("""
                ## General Guidelines
                - Stay in character based on your biography/personality traits above
                - Be helpful but maintain your unique personality
                - If you don't know something, admit it naturally
                - Reference past conversations when relevant
                - Respond naturally like a real assistant would
                """);

        return prompt.toString();
    }

    /**
     * Get language name from code.
     */
    private String getLanguageName(String code) {
        return switch (code) {
            case "th" -> "Thai (ภาษาไทย)";
            case "ja" -> "Japanese (日本語)";
            case "ko" -> "Korean (한국어)";
            case "zh" -> "Chinese (中文)";
            case "de" -> "German (Deutsch)";
            case "fr" -> "French (Français)";
            case "es" -> "Spanish (Español)";
            case "pt" -> "Portuguese (Português)";
            case "ru" -> "Russian (Русский)";
            case "it" -> "Italian (Italiano)";
            case "nl" -> "Dutch (Nederlands)";
            case "pl" -> "Polish (Polski)";
            case "vi" -> "Vietnamese (Tiếng Việt)";
            case "id" -> "Indonesian (Bahasa Indonesia)";
            default -> "English";
        };
    }

    /**
     * Build context string from relevant memories.
     */
    private String buildMemoryContext(List<MemoryContext> memories) {
        StringBuilder context = new StringBuilder();

        int count = 0;
        for (MemoryContext memory : memories) {
            if (count >= 5) break; // Limit to 5 most relevant memories

            context.append("- ").append(memory.content());
            if (memory.timestamp() != null) {
                context.append(" (").append(memory.timestamp()).append(")");
            }
            context.append("\n");
            count++;
        }

        return context.toString();
    }

    // ================================
    // Discord Syntax Processing
    // ================================

    /**
     * Preprocess a Discord message for LLM input.
     * Converts Discord syntax to human-readable text.
     *
     * @param content Raw Discord message content
     * @param context Discord context for resolving mentions
     * @return Processed text suitable for LLM
     */
    public String preprocessDiscordMessage(String content, DiscordSyntaxProcessor.DiscordContext context) {
        return syntaxProcessor.preprocessForLLM(content, context);
    }

    /**
     * Preprocess a Discord message using default context.
     */
    public String preprocessDiscordMessage(String content) {
        return syntaxProcessor.preprocessForLLM(content);
    }

    /**
     * Format LLM response for Discord output.
     *
     * @param response Raw LLM response
     * @return Discord-ready formatted message
     */
    public String formatResponseForDiscord(String response) {
        return syntaxProcessor.formatForDiscord(response);
    }

    /**
     * Format LLM response with custom length limit.
     */
    public String formatResponseForDiscord(String response, int maxLength) {
        return syntaxProcessor.formatForDiscord(response, maxLength);
    }

    /**
     * Get the syntax processor for direct access to extraction methods.
     */
    public DiscordSyntaxProcessor getSyntaxProcessor() {
        return syntaxProcessor;
    }

    // ================================
    // Embedding Methods
    // ================================

    /**
     * Generate embeddings for semantic search.
     *
     * @param text Text to embed
     * @return Embedding vector
     */
    public Optional<float[]> generateEmbedding(String text) {
        return embeddingService.embed(text);
    }

    /**
     * Generate embeddings for multiple texts.
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        return embeddingService.embedBatch(texts);
    }

    /**
     * Calculate similarity between two texts using embeddings.
     */
    public double calculateSimilarity(String text1, String text2) {
        Optional<float[]> emb1 = embeddingService.embed(text1);
        Optional<float[]> emb2 = embeddingService.embed(text2);

        if (emb1.isPresent() && emb2.isPresent()) {
            return embeddingService.cosineSimilarity(emb1.get(), emb2.get());
        }

        return 0.0;
    }

    /**
     * Check if the model service is available.
     */
    public boolean isAvailable() {
        return ollamaClient.isAvailable();
    }

    /**
     * Analyze text for intent, sentiment, entities, etc.
     * Uses LangChain4j + Ollama when available, pattern-based fallback otherwise.
     *
     * @param text The text to analyze
     * @return Analysis result
     */
    public TextAnalysis analyzeText(String text) {
        return textAnalyzerService.analyze(text);
    }

    /**
     * Analyze text without LLM - fast version for passive context.
     * Use this for high-frequency calls that shouldn't block event threads.
     *
     * @param text The text to analyze
     * @return Analysis result (pattern-based only)
     */
    public TextAnalysis analyzeTextFast(String text) {
        return textAnalyzerService.analyzeFast(text);
    }

    /**
     * Analyze text asynchronously with LLM support.
     * Runs on a dedicated executor to avoid blocking Discord event threads.
     * Use this for passive context collection when you want LLM quality
     * without risking heartbeat timeouts.
     *
     * @param text The text to analyze
     * @return CompletableFuture with analysis result
     */
    public java.util.concurrent.CompletableFuture<TextAnalysis> analyzeTextAsync(String text) {
        return textAnalyzerService.analyzeAsync(text);
    }

    /**
     * Check if LLM-powered text analysis is available.
     */
    public boolean isAnalyzerLLMAvailable() {
        return textAnalyzerService.isLLMAvailable();
    }

    /**
     * Check if embedding service is available.
     */
    public boolean isEmbeddingAvailable() {
        return embeddingService.isAvailable();
    }

    /**
     * Get health status of both services.
     */
    public ModelHealth getHealth() {
        HealthStatus ollamaHealth = ollamaClient.checkServerHealth();

        return new ModelHealth(
                ollamaHealth.available(),
                ollamaHealth.version(),
                ollamaHealth.loadedModels(),
                embeddingService.isAvailable(),
                embeddingService.getDimension(),
                embeddingService.getCacheStats()
        );
    }

    // ================================
    // DTOs for the model service
    // ================================

    /**
     * Request for generating a response.
     */
    public record GenerationRequest(
            String userMessage,
            PersonalityTraits personality,
            List<ConversationTurn> conversationHistory,
            List<MemoryContext> relevantMemories,
            String intent,
            String sentiment
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String userMessage;
            private PersonalityTraits personality;
            private List<ConversationTurn> conversationHistory;
            private List<MemoryContext> relevantMemories;
            private String intent;
            private String sentiment;

            public Builder userMessage(String userMessage) {
                this.userMessage = userMessage;
                return this;
            }

            public Builder personality(PersonalityTraits personality) {
                this.personality = personality;
                return this;
            }

            public Builder conversationHistory(List<ConversationTurn> history) {
                this.conversationHistory = history;
                return this;
            }

            public Builder relevantMemories(List<MemoryContext> memories) {
                this.relevantMemories = memories;
                return this;
            }

            public Builder intent(String intent) {
                this.intent = intent;
                return this;
            }

            public Builder sentiment(String sentiment) {
                this.sentiment = sentiment;
                return this;
            }

            public GenerationRequest build() {
                return new GenerationRequest(
                        userMessage, personality, conversationHistory,
                        relevantMemories, intent, sentiment
                );
            }
        }
    }

    /**
     * Response from generation.
     */
    public record GenerationResponse(
            String response,
            String source,
            long durationMs,
            boolean success,
            String error
    ) {}

    /**
     * Personality traits for Pudel.
     */
    public record PersonalityTraits(
            String nickname,
            String biography,
            String personality,
            String preferences,
            String dialogueStyle,
            String language,
            String responseLength,
            String formality,
            String emoteUsage,
            String quirks,
            String topicsInterest,
            String topicsAvoid
    ) {}

    /**
     * A turn in the conversation history.
     */
    public record ConversationTurn(
            String userMessage,
            String assistantResponse
    ) {}

    /**
     * Memory context for relevant past information.
     */
    public record MemoryContext(
            String content,
            String type,
            String timestamp,
            double relevance
    ) {}

    /**
     * Health status of the model services.
     */
    public record ModelHealth(
            boolean ollamaAvailable,
            String ollamaVersion,
            List<String> loadedModels,
            boolean embeddingAvailable,
            int embeddingDimension,
            Map<String, Object> embeddingCacheStats
    ) {}
}

