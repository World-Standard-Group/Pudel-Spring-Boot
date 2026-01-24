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
package worldstandard.group.pudel.model.analyzer;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.model.config.OllamaConfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text Analyzer Service using LangChain4j + Ollama.
 * <p>
 * Replaces the old NlpProcessor with a modern, LLM-powered approach.
 * Provides:
 * - Intent detection
 * - Sentiment analysis
 * - Entity extraction (Discord-aware: mentions, channels, roles)
 * - Language detection
 * - Keyword extraction
 * <p>
 * Uses Ollama for intelligent analysis when available,
 * falls back to pattern-based analysis otherwise.
 */
@Service
public class TextAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(TextAnalyzerService.class);

    private final OllamaConfig ollamaConfig;
    private OllamaChatModel analysisModel;
    private volatile boolean modelAvailable = false;

    // Dedicated executor for LLM calls - isolated from JDA event threads
    // This prevents Discord heartbeat interruptions from killing LLM requests
    private final ExecutorService llmExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "TextAnalyzer-LLM");
        t.setDaemon(true);
        return t;
    });

    // Discord entity patterns
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern EMOJI_CUSTOM = Pattern.compile("<a?:(\\w+):(\\d+)>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    // Intent patterns (fallback)
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(hi|hello|hey|yo|greetings|good\\s*(morning|afternoon|evening|night)|howdy|sup|heya)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FAREWELL_PATTERN = Pattern.compile(
            "^(bye|goodbye|see\\s*y(a|ou)|later|cya|farewell|good\\s*night|take\\s*care|peace)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "(^(what|when|where|who|why|how|which|whose|whom|can|could|will|would|should|do|does|did|is|are|was|were)\\b|\\?\\s*$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "^[!./]\\w+",
            Pattern.CASE_INSENSITIVE
    );

    // Sentiment words (fallback)
    private static final Set<String> POSITIVE_WORDS = Set.of(
            "good", "great", "excellent", "amazing", "wonderful", "fantastic", "love", "like",
            "happy", "thanks", "thank", "please", "nice", "awesome", "perfect", "best",
            "beautiful", "brilliant", "cool", "fun", "helpful", "kind", "friendly", "glad",
            "welcome", "yes", "agree", "lol", "lmao", "haha", "xd", ":)", "yay", "pog"
    );
    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "bad", "terrible", "awful", "horrible", "hate", "dislike", "angry", "sad",
            "upset", "annoyed", "frustrated", "stupid", "wrong", "broken", "error", "fail",
            "sucks", "worst", "ugly", "boring", "useless", "no", "never", "problem", "issue",
            "bug", "crash", "fix", "wtf", ":(", "bruh"
    );

    public TextAnalyzerService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
    }

    @PostConstruct
    public void initialize() {
        try {
            if (ollamaConfig.isEnabled()) {
                // Use a smaller, faster model for analysis if available
                String analysisModelName = ollamaConfig.getAnalysisModel() != null
                        ? ollamaConfig.getAnalysisModel()
                        : ollamaConfig.getModel();

                this.analysisModel = OllamaChatModel.builder()
                        .baseUrl(ollamaConfig.getBaseUrl())
                        .modelName(analysisModelName)
                        .timeout(Duration.ofSeconds(300)) // Quick timeout for analysis
                        .temperature(0.1) // Low temperature for consistent analysis
                        .build();

                // Test connection
                try {
                    String testResponse = analysisModel.chat("test");
                    modelAvailable = testResponse != null && !testResponse.isEmpty();
                    logger.info("LangChain4j text analyzer initialized with model: {}", analysisModelName);
                } catch (Exception e) {
                    logger.warn("LangChain4j analysis model not available, using pattern-based fallback: {}", e.getMessage());
                    modelAvailable = false;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize LangChain4j analyzer: {}", e.getMessage());
            modelAvailable = false;
        }
    }

    @PreDestroy
    public void shutdown() {
        llmExecutor.shutdown();
        try {
            if (!llmExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                llmExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            llmExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Analyze a text message for intent, sentiment, entities, etc.
     * Uses LLM analysis when available for better accuracy.
     */
    public TextAnalysis analyze(String text) {
        return analyze(text, false);
    }

    /**
     * Fast analysis without LLM - for passive context collection.
     * This avoids blocking the event thread with LLM calls.
     */
    public TextAnalysis analyzeFast(String text) {
        return analyze(text, true);
    }

    /**
     * Async analysis with LLM - runs entirely in background.
     * Use this for passive context collection when you want LLM quality
     * without blocking the Discord event thread.
     *
     * @param text The text to analyze
     * @return A CompletableFuture that completes with the analysis result
     */
    public CompletableFuture<TextAnalysis> analyzeAsync(String text) {
        return CompletableFuture.supplyAsync(() -> analyze(text, false), llmExecutor)
                .exceptionally(e -> {
                    logger.debug("Async analysis failed, returning pattern-based result: {}", e.getMessage());
                    return analyze(text, true);
                });
    }

    /**
     * Core analysis method with option to skip LLM.
     *
     * @param text The text to analyze
     * @param skipLLM If true, skip LLM analysis and use only fast pattern-based analysis
     */
    public TextAnalysis analyze(String text, boolean skipLLM) {
        if (text == null || text.isBlank()) {
            return TextAnalysis.empty();
        }

        // Always extract Discord entities (fast, local)
        Map<String, List<String>> entities = extractDiscordEntities(text);

        // Quick pattern checks
        boolean isQuestion = QUESTION_PATTERN.matcher(text).find();
        boolean isCommand = COMMAND_PATTERN.matcher(text).matches();
        boolean isGreeting = GREETING_PATTERN.matcher(text).find();
        boolean isFarewell = FAREWELL_PATTERN.matcher(text).find();

        // Try LLM analysis for intent/sentiment (if available, not skipped, and message is substantial)
        if (!skipLLM && modelAvailable && text.length() > 10 && !isCommand) {
            try {
                return analyzeWithLLM(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
            } catch (Exception e) {
                logger.debug("LLM analysis failed, using fallback: {}", e.getMessage());
            }
        }

        // Fallback to pattern-based analysis
        return analyzeWithPatterns(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
    }

    /**
     * Analyze using Ollama via LangChain4j.
     * Runs on a dedicated executor to avoid JDA heartbeat interruptions.
     */
    private TextAnalysis analyzeWithLLM(String text,
                                         Map<String, List<String>> entities,
                                         boolean isQuestion,
                                         boolean isCommand,
                                         boolean isGreeting,
                                         boolean isFarewell) {

        String prompt = buildAnalysisPrompt(text);

        try {
            // Run LLM call on dedicated executor to protect from JDA heartbeat interruptions
            Future<String> futureResult = CompletableFuture
                    .supplyAsync(() -> analysisModel.chat(prompt), llmExecutor)
                    .orTimeout(300, TimeUnit.SECONDS);

            // Wait for result with timeout - if JDA interrupts us, the LLM call continues
            String result;
            try {
                result = futureResult.get(300, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // JDA heartbeat interrupted us, but LLM call continues in background
                // Return pattern-based result for now, LLM result will be lost
                logger.debug("LLM analysis interrupted by heartbeat, using pattern fallback");
                Thread.currentThread().interrupt(); // Restore interrupt flag
                return analyzeWithPatterns(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
            } catch (TimeoutException e) {
                logger.debug("LLM analysis timed out");
                futureResult.cancel(false);
                return analyzeWithPatterns(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
            } catch (ExecutionException e) {
                logger.debug("LLM analysis execution error: {}", e.getCause().getMessage());
                return analyzeWithPatterns(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
            }

            return parseAnalysisResponse(result, entities, isQuestion, isCommand, isGreeting, isFarewell);
        } catch (Exception e) {
            logger.debug("LLM analysis error: {}", e.getMessage());
            return analyzeWithPatterns(text, entities, isQuestion, isCommand, isGreeting, isFarewell);
        }
    }

    /**
     * Build a concise prompt for text analysis.
     */
    private String buildAnalysisPrompt(String text) {
        return """
                Analyze this Discord message briefly. Reply in this exact format only:
                INTENT: greeting|farewell|question|help|thanks|chat|information|command
                SENTIMENT: positive|neutral|negative
                LANGUAGE: eng|tha|jpn|other
                KEYWORDS: word1,word2,word3
                
                Message: "%s"
                """.formatted(text.length() > 200 ? text.substring(0, 200) : text);
    }

    /**
     * Parse LLM analysis response.
     */
    private TextAnalysis parseAnalysisResponse(String response,
                                                Map<String, List<String>> entities,
                                                boolean isQuestion,
                                                boolean isCommand,
                                                boolean isGreeting,
                                                boolean isFarewell) {
        String intent = "chat";
        String sentiment = "neutral";
        String language = "eng";
        List<String> keywords = new ArrayList<>();
        double confidence = 0.8; // LLM responses have higher confidence

        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.startsWith("INTENT:")) {
                intent = line.substring(7).trim().toLowerCase();
            } else if (line.startsWith("SENTIMENT:")) {
                sentiment = line.substring(10).trim().toLowerCase();
            } else if (line.startsWith("LANGUAGE:")) {
                language = line.substring(9).trim().toLowerCase();
            } else if (line.startsWith("KEYWORDS:")) {
                String kw = line.substring(9).trim();
                keywords = Arrays.stream(kw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }

        // Normalize values
        intent = normalizeIntent(intent);
        sentiment = normalizeSentiment(sentiment);

        return TextAnalysis.builder()
                .language(language)
                .intent(intent)
                .confidence(confidence)
                .sentiment(sentiment)
                .entities(entities)
                .keywords(keywords)
                .isQuestion(isQuestion || "question".equals(intent))
                .isCommand(isCommand)
                .isGreeting(isGreeting || "greeting".equals(intent))
                .isFarewell(isFarewell || "farewell".equals(intent))
                .build();
    }

    /**
     * Fallback pattern-based analysis (fast, no LLM needed).
     */
    private TextAnalysis analyzeWithPatterns(String text,
                                              Map<String, List<String>> entities,
                                              boolean isQuestion,
                                              boolean isCommand,
                                              boolean isGreeting,
                                              boolean isFarewell) {

        // Detect intent from patterns
        String intent = detectIntentFromPatterns(text, isQuestion, isCommand, isGreeting, isFarewell);

        // Detect sentiment from word lists
        String sentiment = detectSentimentFromWords(text);

        // Basic language detection (just check for non-ASCII)
        String language = detectLanguage(text);

        // Extract keywords (simple: non-stopword tokens > 3 chars)
        List<String> keywords = extractKeywords(text);

        return TextAnalysis.builder()
                .language(language)
                .intent(intent)
                .confidence(0.5) // Pattern-based has lower confidence
                .sentiment(sentiment)
                .entities(entities)
                .keywords(keywords)
                .isQuestion(isQuestion)
                .isCommand(isCommand)
                .isGreeting(isGreeting)
                .isFarewell(isFarewell)
                .build();
    }

    /**
     * Extract Discord-specific entities from text.
     */
    private Map<String, List<String>> extractDiscordEntities(String text) {
        Map<String, List<String>> entities = new HashMap<>();

        // User mentions
        List<String> users = extractPattern(USER_MENTION, text);
        if (!users.isEmpty()) {
            entities.put("users", users);
        }

        // Channel mentions
        List<String> channels = extractPattern(CHANNEL_MENTION, text);
        if (!channels.isEmpty()) {
            entities.put("channels", channels);
        }

        // Role mentions
        List<String> roles = extractPattern(ROLE_MENTION, text);
        if (!roles.isEmpty()) {
            entities.put("roles", roles);
        }

        // Custom emojis
        List<String> emojis = extractPatternGroup(EMOJI_CUSTOM, text, 1);
        if (!emojis.isEmpty()) {
            entities.put("emojis", emojis);
        }

        // URLs
        List<String> urls = extractPattern(URL_PATTERN, text);
        if (!urls.isEmpty()) {
            entities.put("urls", urls);
        }

        return entities;
    }

    private List<String> extractPattern(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1) != null ? matcher.group(1) : matcher.group());
        }
        return matches;
    }

    private List<String> extractPatternGroup(Pattern pattern, String text, int group) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.groupCount() >= group) {
                matches.add(matcher.group(group));
            }
        }
        return matches;
    }

    private String detectIntentFromPatterns(String text, boolean isQuestion,
                                            boolean isCommand, boolean isGreeting, boolean isFarewell) {
        if (isCommand) return "command";
        if (isGreeting) return "greeting";
        if (isFarewell) return "farewell";
        if (isQuestion) return "question";

        String lower = text.toLowerCase();
        if (lower.contains("thank") || lower.contains("thanks") || lower.contains("thx")) {
            return "thanks";
        }
        if (lower.contains("help") || lower.contains("assist") || lower.contains("how do") || lower.contains("how to")) {
            return "help";
        }

        return "chat";
    }

    private String detectSentimentFromWords(String text) {
        String lower = text.toLowerCase();
        String[] words = lower.split("\\s+");

        int positive = 0;
        int negative = 0;

        for (String word : words) {
            // Clean punctuation
            word = word.replaceAll("[^a-z:)(]", "");
            if (POSITIVE_WORDS.contains(word)) positive++;
            if (NEGATIVE_WORDS.contains(word)) negative++;
        }

        if (positive > negative) return "positive";
        if (negative > positive) return "negative";
        return "neutral";
    }

    private String detectLanguage(String text) {
        // Simple heuristic: check for Thai or Japanese characters
        for (char c : text.toCharArray()) {
            if (c >= 0x0E00 && c <= 0x0E7F) return "tha"; // Thai
            if (c >= 0x3040 && c <= 0x30FF) return "jpn"; // Japanese hiragana/katakana
            if (c >= 0x4E00 && c <= 0x9FFF) return "cjk"; // CJK characters
        }
        return "eng";
    }

    private List<String> extractKeywords(String text) {
        // Simple keyword extraction: words > 4 chars, not stopwords
        Set<String> stopwords = Set.of(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                "of", "with", "by", "from", "this", "that", "these", "those", "is",
                "are", "was", "were", "be", "been", "being", "have", "has", "had",
                "do", "does", "did", "will", "would", "could", "should", "may", "might",
                "can", "just", "like", "know", "think", "want", "need", "make", "get"
        );

        String cleaned = text.toLowerCase().replaceAll("<[^>]+>", " "); // Remove Discord mentions
        String[] words = cleaned.split("\\s+");

        return Arrays.stream(words)
                .map(w -> w.replaceAll("[^a-z]", ""))
                .filter(w -> w.length() > 4)
                .filter(w -> !stopwords.contains(w))
                .distinct()
                .limit(10)
                .toList();
    }

    private String normalizeIntent(String intent) {
        return switch (intent) {
            case "greeting", "greet", "hello", "hi" -> "greeting";
            case "farewell", "bye", "goodbye" -> "farewell";
            case "question", "ask", "query" -> "question";
            case "help", "assist", "support" -> "help";
            case "thanks", "thank", "gratitude" -> "thanks";
            case "information", "info" -> "information";
            case "command", "cmd" -> "command";
            default -> "chat";
        };
    }

    private String normalizeSentiment(String sentiment) {
        return switch (sentiment) {
            case "positive", "happy", "good" -> "positive";
            case "negative", "sad", "bad", "angry" -> "negative";
            default -> "neutral";
        };
    }

    /**
     * Check if the analyzer is using LLM-powered analysis.
     */
    public boolean isLLMAvailable() {
        return modelAvailable;
    }
}

