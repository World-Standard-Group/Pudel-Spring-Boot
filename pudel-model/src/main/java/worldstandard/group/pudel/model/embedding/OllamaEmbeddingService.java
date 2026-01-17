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
package worldstandard.group.pudel.model.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;
import worldstandard.group.pudel.model.config.OllamaConfig;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Embedding service using Ollama's embedding API.
 * <p>
 * Uses Ollama's /api/embed endpoint for generating text embeddings.
 * This replaces the ONNX-based LocalEmbeddingService for a unified
 * Ollama-based brain architecture.
 * <p>
 * Key features:
 * - Uses same Ollama server as LLM (no separate model loading)
 * - Supports various embedding models (nomic-embed-text, mxbai-embed-large, etc.)
 * - LRU cache for frequently embedded texts
 * - Batch processing support
 * - Discord syntax preprocessing
 */
@Service
public class OllamaEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final WebClient webClient;
    private final OllamaConfig config;
    private final DiscordSyntaxProcessor syntaxProcessor;
    private volatile boolean initialized = false;
    private volatile int embeddingDimension = 0;

    // LRU cache for embeddings
    private final Map<String, float[]> embeddingCache;
    private static final int DEFAULT_CACHE_SIZE = 1000;

    public OllamaEmbeddingService(@Qualifier("ollamaWebClient") WebClient webClient,
                                   OllamaConfig config,
                                   DiscordSyntaxProcessor syntaxProcessor) {
        this.webClient = webClient;
        this.config = config;
        this.syntaxProcessor = syntaxProcessor;

        // Initialize LRU cache
        this.embeddingCache = Collections.synchronizedMap(
                new LinkedHashMap<String, float[]>(DEFAULT_CACHE_SIZE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                        return size() > DEFAULT_CACHE_SIZE;
                    }
                }
        );
    }

    /**
     * Initialize the embedding service and verify connectivity.
     */
    @PostConstruct
    public void initialize() {
        if (!config.isEnabled() || !config.isEmbeddingEnabled()) {
            logger.info("Ollama embedding service is disabled");
            return;
        }

        try {
            // Test embedding generation to verify model is available
            Optional<float[]> testEmbed = embedDirect("test");
            if (testEmbed.isPresent()) {
                embeddingDimension = testEmbed.get().length;
                initialized = true;
                logger.info("Ollama embedding service initialized with model: {} (dimension={})",
                        config.getEmbeddingModel(), embeddingDimension);
            } else {
                logger.warn("Ollama embedding model '{}' not available. Run: ollama pull {}",
                        config.getEmbeddingModel(), config.getEmbeddingModel());
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize Ollama embedding service: {}", e.getMessage());
            initialized = false;
        }
    }

    /**
     * Check if the service is ready to generate embeddings.
     */
    public boolean isAvailable() {
        return initialized && config.isEnabled() && config.isEmbeddingEnabled();
    }

    /**
     * Generate embedding for a single text.
     * Preprocesses Discord syntax before embedding.
     *
     * @param text The text to embed
     * @return Embedding vector
     */
    public Optional<float[]> embed(String text) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return Optional.empty();
        }

        // Preprocess Discord syntax for cleaner embeddings
        String processed = syntaxProcessor.preprocessForEmbedding(text);

        // Check cache first
        String cacheKey = processed.toLowerCase().trim();
        if (embeddingCache.containsKey(cacheKey)) {
            return Optional.of(embeddingCache.get(cacheKey));
        }

        Optional<float[]> result = embedDirect(processed);

        // Cache the result
        result.ifPresent(embedding -> embeddingCache.put(cacheKey, embedding));

        return result;
    }

    /**
     * Generate embedding directly without preprocessing.
     */
    private Optional<float[]> embedDirect(String text) {
        try {
            EmbedRequest request = new EmbedRequest(config.getEmbeddingModel(), text);

            CompletableFuture<EmbedResponse> future = webClient.post()
                    .uri("/api/embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbedResponse.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .toFuture();

            EmbedResponse response = future.get(30, TimeUnit.SECONDS);

            if (response != null && response.embeddings() != null && !response.embeddings().isEmpty()) {
                List<Double> embeddingList = response.embeddings().getFirst();
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return Optional.of(embedding);
            }
        } catch (Exception e) {
            logger.debug("Error generating embedding: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Generate embeddings for multiple texts (batch processing).
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (!isAvailable() || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            embed(text).ifPresent(results::add);
        }
        return results;
    }

    /**
     * Compute cosine similarity between two embeddings.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Find the most similar texts to a query.
     */
    public List<Map.Entry<String, Double>> findSimilar(float[] queryEmbedding,
                                                        Map<String, float[]> candidateEmbeddings,
                                                        int topK) {
        if (queryEmbedding == null || candidateEmbeddings == null || candidateEmbeddings.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, Double>> similarities = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : candidateEmbeddings.entrySet()) {
            double similarity = cosineSimilarity(queryEmbedding, entry.getValue());
            similarities.add(Map.entry(entry.getKey(), similarity));
        }

        similarities.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return similarities.subList(0, Math.min(topK, similarities.size()));
    }

    /**
     * Get embedding dimension.
     */
    public int getDimension() {
        return embeddingDimension;
    }

    /**
     * Clear the embedding cache.
     */
    public void clearCache() {
        embeddingCache.clear();
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", embeddingCache.size());
        stats.put("cacheMaxSize", DEFAULT_CACHE_SIZE);
        stats.put("embeddingModel", config.getEmbeddingModel());
        stats.put("embeddingDimension", embeddingDimension);
        return stats;
    }

    // ================================
    // DTOs for Ollama Embed API
    // ================================

    /**
     * Request body for /api/embed endpoint.
     */
    public record EmbedRequest(
            String model,
            String input
    ) {}

    /**
     * Response from /api/embed endpoint.
     */
    public record EmbedResponse(
            String model,
            List<List<Double>> embeddings,
            Long total_duration,
            Long load_duration,
            Integer prompt_eval_count
    ) {}
}

