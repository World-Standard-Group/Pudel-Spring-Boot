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
package group.worldstandard.pudel.core.brain.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Ollama API client for the reworked PudelBrain.
 * <p>
 * Supports:
 * - Streaming completion (token-by-token) for responsive Discord interaction
 * - Non-streaming completion for simple requests
 * - System prompt + conversation history context
 * - Configurable model, temperature, max tokens
 * <p>
 * Uses WebClient for non-blocking HTTP calls. The streaming API sends
 * Server-Sent Events (SSE) which are consumed token-by-token.
 */
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String model;
    private final String embeddingModel;
    private final double temperature;
    private final int maxTokens;
    private final Duration timeout;

    // Simple in-memory cache for model availability
    private volatile Boolean modelAvailable = null;
    private volatile long lastAvailabilityCheck = 0;

    public OllamaClient(String baseUrl, String model, double temperature, int maxTokens, int timeoutSeconds) {
        this(baseUrl, model, model, temperature, maxTokens, timeoutSeconds);
    }

    public OllamaClient(String baseUrl, String model, String embeddingModel, double temperature, int maxTokens, int timeoutSeconds) {
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    // ===============================
    // Completion Methods
    // ===============================

    /**
     * Generate a completion with streaming.
     * <p>
     * Sends tokens to the consumer as they arrive from Ollama.
     * Returns a CompletableFuture that completes when the full response is received.
     *
     * @param systemPrompt    the system prompt (personality, instructions)
     * @param userMessage     the user's message
     * @param history         conversation history (chronological order)
     * @param tokenConsumer   called for each token received
     * @return CompletableFuture with the complete response text
     */
    public CompletableFuture<String> generateStreaming(
            String systemPrompt,
            String userMessage,
            List<ConversationTurn> history,
            Consumer<String> tokenConsumer) {

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullResponse = new StringBuilder();

        try {
            ObjectNode requestBody = buildRequestBody(systemPrompt, userMessage, history, true);

            String requestJson = requestBody.toString();
            logger.debug("Ollama request: model={}, prompt length={}, stream={}", model, userMessage != null ? userMessage.length() : 0, true);
            if (logger.isTraceEnabled()) {
                logger.trace("Ollama request body: {}", requestJson);
            }

            webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .accept(MediaType.APPLICATION_NDJSON)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(timeout)
                    .doOnNext(jsonLine -> {
                        try {
                            JsonNode node = MAPPER.readTree(jsonLine);
                            JsonNode responseNode = node.get("response");
                            if (responseNode != null && !responseNode.isNull()) {
                                String token = responseNode.asText();
                                fullResponse.append(token);
                                if (tokenConsumer != null) {
                                    tokenConsumer.accept(token);
                                }
                            }
                            // Check if done
                            JsonNode doneNode = node.get("done");
                            if (doneNode != null && doneNode.asBoolean(false)) {
                                // Stream will complete naturally via doOnComplete
                            }
                        } catch (Exception e) {
                            logger.debug("Error parsing streaming token: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        String result = fullResponse.toString().trim();
                        logger.debug("Streaming complete, total length: {}", result.length());
                        future.complete(result);
                    })
                    .doOnError(error -> {
                        logger.error("Ollama streaming error: {}", error.getMessage());
                        if (error instanceof WebClientResponseException wce) {
                            logger.error("Ollama error response body: {}", wce.getResponseBodyAsString());
                        }
                        // Return whatever we have so far
                        if (!fullResponse.isEmpty()) {
                            future.complete(fullResponse.toString().trim());
                        } else {
                            future.completeExceptionally(error);
                        }
                    })
                    .subscribe();

        } catch (Exception e) {
            logger.error("Error starting Ollama streaming: {}", e.getMessage());
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Generate a completion without streaming (blocking).
     * <p>
     * Simpler but waits for the full response before returning.
     *
     * @param systemPrompt the system prompt
     * @param userMessage  the user's message
     * @param history      conversation history
     * @return the complete response text, or null on error
     */
    public String generateBlocking(String systemPrompt, String userMessage, List<ConversationTurn> history) {
        try {
            ObjectNode requestBody = buildRequestBody(systemPrompt, userMessage, history, false);

            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (response != null) {
                JsonNode node = MAPPER.readTree(response);
                JsonNode responseNode = node.get("response");
                if (responseNode != null) {
                    return responseNode.asText().trim();
                }
            }

        } catch (Exception e) {
            logger.error("Ollama blocking generation error: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Generate a completion with streaming, returning a Flux of tokens.
     * <p>
     * Useful for reactive pipelines where you want to process tokens
     * as they arrive without managing a CompletableFuture.
     *
     * @param systemPrompt the system prompt
     * @param userMessage  the user's message
     * @param history      conversation history
     * @return Flux of response tokens
     */
    public Flux<String> generateFlux(String systemPrompt, String userMessage, List<ConversationTurn> history) {
        try {
            ObjectNode requestBody = buildRequestBody(systemPrompt, userMessage, history, true);

            return webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .accept(MediaType.APPLICATION_NDJSON)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(timeout)
                    .mapNotNull(jsonLine -> {
                        try {
                            JsonNode node = MAPPER.readTree(jsonLine);
                            JsonNode responseNode = node.get("response");
                            if (responseNode != null && !responseNode.isNull()) {
                                return responseNode.asText();
                            }
                        } catch (Exception e) {
                            logger.debug("Error parsing token: {}", e.getMessage());
                        }
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error creating Ollama flux: {}", e.getMessage());
            return Flux.empty();
        }
    }

    // ===============================
    // Embeddings
    // ===============================

    /**
     * Generate an embedding vector for the given text via Ollama's /api/embeddings.
     * <p>
     * Returns {@code null} if the server is unreachable or the response cannot be parsed,
     * so callers can gracefully skip embedding storage.
     *
     * @param text the text to embed
     * @return the embedding vector, or null on failure
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            ObjectNode requestBody = MAPPER.createObjectNode();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            String response = webClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (response == null) {
                return null;
            }

            JsonNode root = MAPPER.readTree(response);
            JsonNode embeddingsNode = root.get("embeddings");
            if (embeddingsNode != null && embeddingsNode.isArray()) {
                float[] result = new float[embeddingsNode.size()];
                for (int i = 0; i < embeddingsNode.size(); i++) {
                    result[i] = (float) embeddingsNode.get(i).asDouble();
                }
                return result;
            }
        } catch (Exception e) {
            logger.warn("Ollama embedding error: {}", e.getMessage());
        }
        return null;
    }

    // ===============================
    // Model Management
    // ===============================

    /**
     * Check if the configured model is available on the Ollama server.
     * Caches the result for 60 seconds to avoid repeated API calls.
     */
    public boolean isModelAvailable() {
        long now = System.currentTimeMillis();
        if (modelAvailable != null && (now - lastAvailabilityCheck) < 60_000) {
            return modelAvailable;
        }

        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode root = MAPPER.readTree(response);
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    for (JsonNode m : models) {
                        JsonNode name = m.get("name");
                        if (name != null && name.asText().startsWith(model)) {
                            modelAvailable = true;
                            lastAvailabilityCheck = now;
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Ollama model check failed: {}", e.getMessage());
        }

        modelAvailable = false;
        lastAvailabilityCheck = now;
        return false;
    }

    /**
     * Check if the Ollama server is reachable.
     */
    public boolean isServerReachable() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ===============================
    // Request Building
    // ===============================

    /**
     * Build the JSON request body for Ollama's /api/generate endpoint.
     */
    private ObjectNode buildRequestBody(String systemPrompt, String userMessage,
                                         List<ConversationTurn> history, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("stream", stream);
        root.put("system", systemPrompt != null ? systemPrompt : "");

        // Build the prompt with conversation history
        StringBuilder promptBuilder = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            for (ConversationTurn turn : history) {
                promptBuilder.append("User: ").append(turn.userMessage()).append("\n");
                promptBuilder.append("Assistant: ").append(turn.botResponse()).append("\n");
            }
        }

        promptBuilder.append("User: ").append(userMessage).append("\n");
        promptBuilder.append("Assistant:");

        root.put("prompt", promptBuilder.toString());

        // Options
        ObjectNode options = root.putObject("options");
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);

        return root;
    }

    // ===============================
    // Inner Types
    // ===============================

    /**
     * A single turn in a conversation.
     *
     * @param userMessage the user's message
     * @param botResponse the bot's response
     */
    public record ConversationTurn(String userMessage, String botResponse) {}
}
