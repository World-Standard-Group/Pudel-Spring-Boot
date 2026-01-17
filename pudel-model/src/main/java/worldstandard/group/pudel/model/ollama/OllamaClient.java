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
package worldstandard.group.pudel.model.ollama;

import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.scheduler.Schedulers;
import worldstandard.group.pudel.model.config.OllamaConfig;
import worldstandard.group.pudel.model.ollama.OllamaDto.*;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP client for communicating with Ollama local LLM server.
 * <p>
 * Ollama runs as an external process (typically on localhost:11434),
 * keeping model memory separate from JVM heap for better resource management.
 * <p>
 * Key features:
 * - Chat completion with conversation history
 * - Generate endpoint for simple prompts
 * - Embedding generation (optional, prefer local ONNX for performance)
 * - Health monitoring and model management
 * - Automatic retry with backoff
 */
@Service
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;
    private final OllamaConfig config;
    private volatile boolean serverAvailable = false;
    private volatile String serverVersion = null;

    public OllamaClient(@Qualifier("ollamaWebClient") WebClient webClient, OllamaConfig config) {
        this.webClient = webClient;
        this.config = config;

        // Check server availability on startup
        checkServerHealth();
    }

    /**
     * Check if Ollama server is available and reachable.
     */
    public boolean isAvailable() {
        return serverAvailable && config.isEnabled();
    }

    /**
     * Check server health and update availability status.
     */
    public HealthStatus checkServerHealth() {
        if (!config.isEnabled()) {
            return new HealthStatus(false, null, Collections.emptyList());
        }

        try {
            // Ping the server
            String response = webClient.get()
                    .uri("/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.contains("Ollama")) {
                serverAvailable = true;
                serverVersion = response.trim();

                // Get loaded models
                List<String> models = listModels().stream()
                        .map(ModelInfo::name)
                        .toList();

                logger.info("Ollama server available at {}, models: {}",
                        config.getBaseUrl(), models);

                return new HealthStatus(true, serverVersion, models);
            }
        } catch (Exception e) {
            logger.warn("Ollama server not available at {}: {}",
                    config.getBaseUrl(), e.getMessage());
            serverAvailable = false;
        }

        return new HealthStatus(false, null, Collections.emptyList());
    }

    /**
     * Generate a chat response using conversation history.
     * This is the primary method for Pudel's chatbot functionality.
     * <p>
     * Uses a separate thread pool to avoid JDA heartbeat interruptions.
     *
     * @param messages Conversation history (system, user, assistant messages)
     * @return Generated response text
     */
    public Optional<String> chat(List<ChatMessage> messages) {
        if (!isAvailable()) {
            logger.debug("Ollama not available, skipping chat");
            return Optional.empty();
        }

        int maxRetries = config.getRetryCount() + 1;
        int timeoutCount = 0;  // Track consecutive timeouts
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> options = buildOptions();

                ChatRequest request = ChatRequest.builder()
                        .model(config.getModel())
                        .messages(messages)
                        .stream(false)
                        .options(options)
                        .keepAlive(config.getKeepAliveDuration())
                        .build();

                // Use CompletableFuture to run on a separate thread pool
                // This prevents JDA heartbeat reconnections from interrupting the request
                CompletableFuture<ChatResponse> future = webClient.post()
                        .uri("/api/chat")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(ChatResponse.class)
                        .subscribeOn(Schedulers.boundedElastic())  // Run on separate thread pool
                        .toFuture();

                // Wait with timeout using a loop to handle interruptions gracefully
                ChatResponse response = waitForResponse(future, config.getTimeoutSeconds());

                if (response != null && response.message() != null) {
                    String content = response.message().content();
                    logger.debug("Ollama chat response: {} tokens in {}ms",
                            response.evalCount(),
                            response.totalDuration() != null ? response.totalDuration() / 1_000_000 : "?");

                    // Post-process response: remove thinking tags from thinking models (e.g., qwen3)
                    content = stripThinkingTags(content);

                    // Validate content is not empty after processing
                    if (content != null && !content.isBlank()) {
                        return Optional.of(content);
                    } else {
                        logger.warn("Ollama returned empty response after stripping thinking tags");
                        return Optional.empty();
                    }
                }

                // Response was null, retry
                logger.debug("Ollama returned null response, attempt {}/{}", attempt, maxRetries);

            } catch (TimeoutException e) {
                lastException = e;
                timeoutCount++;
                logger.warn("Ollama request timed out on attempt {}/{} (timeout #{} consecutive)",
                        attempt, maxRetries, timeoutCount);

                // If we've had 2+ consecutive timeouts, stop retrying - server is too slow
                if (timeoutCount >= 2) {
                    logger.warn("Multiple consecutive timeouts, Ollama server appears overloaded. Giving up.");
                    return Optional.empty();
                }
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                lastException = e;

                // Check for read timeout (netty timeout)
                if (cause instanceof ReadTimeoutException) {
                    timeoutCount++;
                    logger.warn("Ollama read timeout on attempt {}/{} (timeout #{} consecutive)",
                            attempt, maxRetries, timeoutCount);
                    if (timeoutCount >= 2) {
                        logger.warn("Multiple consecutive read timeouts. Giving up.");
                        return Optional.empty();
                    }
                } else if (isRetryable(cause)) {
                    timeoutCount = 0; // Reset timeout count on non-timeout retryable error
                    logger.debug("Retryable error on attempt {}/{}: {}",
                            attempt, maxRetries, cause != null ? cause.getMessage() : e.getMessage());
                } else {
                    timeoutCount = 0; // Reset timeout count on non-timeout error
                    logger.error("Non-retryable error calling Ollama chat: {}",
                            cause != null ? cause.getMessage() : e.getMessage());
                    handleError(cause instanceof Exception ? (Exception) cause : e);
                    return Optional.empty();
                }
            } catch (Exception e) {
                lastException = e;
                timeoutCount = 0; // Reset timeout count on non-timeout error
                logger.error("Error calling Ollama chat on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
                handleError(e);
            }

            // Backoff before retry (shorter for timeouts since we already waited)
            if (attempt < maxRetries) {
                try {
                    long backoffMs = timeoutCount > 0 ? 500 : (long) Math.pow(2, attempt) * 1000;
                    logger.debug("Backing off {}ms before retry", backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // Continue with retry anyway
                }
            }
        }

        logger.warn("All Ollama retries exhausted: {}",
                lastException != null ? lastException.getMessage() : "unknown error");
        return Optional.empty();
    }

    /**
     * Wait for a future response, handling thread interruptions gracefully.
     * JDA heartbeat reconnections can interrupt threads, but we want to
     * continue waiting for Ollama since it runs on a separate thread pool.
     */
    private <T> T waitForResponse(CompletableFuture<T> future, long timeoutSeconds)
            throws TimeoutException, java.util.concurrent.ExecutionException {

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000;

        while (true) {
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = timeoutMs - elapsed;

                if (remaining <= 0) {
                    throw new TimeoutException("Ollama request exceeded " + timeoutSeconds + " seconds");
                }

                return future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // JDA heartbeat reconnection interrupted us, but the future is still running
                logger.debug("Thread interrupted while waiting for Ollama, continuing to wait ({}ms elapsed)...",
                        System.currentTimeMillis() - startTime);
                // Don't clear interrupt flag immediately - check if future is done first
                if (future.isDone()) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag
                    try {
                        return future.get(); // Get the completed result
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new TimeoutException("Future completed but interrupted during retrieval");
                    }
                }
                // Future still running, continue waiting
            }
        }
    }

    /**
     * Generate response with a simple prompt (no conversation history).
     * Uses a separate thread pool to avoid JDA heartbeat interruptions.
     *
     * @param prompt The user's prompt
     * @param systemPrompt Optional system prompt for context
     * @return Generated response text
     */
    public Optional<String> generate(String prompt, String systemPrompt) {
        if (!isAvailable()) {
            logger.debug("Ollama not available, skipping generate");
            return Optional.empty();
        }

        int maxRetries = config.getRetryCount() + 1;
        int timeoutCount = 0;  // Track consecutive timeouts
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> options = buildOptions();

                GenerateRequest request = GenerateRequest.builder()
                        .model(config.getModel())
                        .prompt(prompt)
                        .system(systemPrompt)
                        .stream(false)
                        .options(options)
                        .keepAlive(config.getKeepAliveDuration())
                        .build();

                CompletableFuture<GenerateResponse> future = webClient.post()
                        .uri("/api/generate")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(GenerateResponse.class)
                        .subscribeOn(Schedulers.boundedElastic())
                        .toFuture();

                GenerateResponse response = waitForResponse(future, config.getTimeoutSeconds());

                if (response != null) {
                    logger.debug("Ollama generate response: {} tokens in {}ms",
                            response.evalCount(),
                            response.totalDuration() != null ? response.totalDuration() / 1_000_000 : "?");
                    String content = response.response();
                    // Strip thinking tags for generate as well
                    content = stripThinkingTags(content);
                    if (content != null && !content.isBlank()) {
                        return Optional.of(content);
                    }
                }

                logger.debug("Ollama generate returned empty, attempt {}/{}", attempt, maxRetries);

            } catch (TimeoutException e) {
                lastException = e;
                timeoutCount++;
                logger.warn("Ollama generate timed out on attempt {}/{} (timeout #{} consecutive)",
                        attempt, maxRetries, timeoutCount);

                // If we've had 2+ consecutive timeouts, stop retrying
                if (timeoutCount >= 2) {
                    logger.warn("Multiple consecutive generate timeouts. Giving up.");
                    return Optional.empty();
                }
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                lastException = e;

                // Check for read timeout (netty timeout)
                if (cause instanceof ReadTimeoutException) {
                    timeoutCount++;
                    logger.warn("Ollama generate read timeout on attempt {}/{} (timeout #{} consecutive)",
                            attempt, maxRetries, timeoutCount);
                    if (timeoutCount >= 2) {
                        logger.warn("Multiple consecutive generate read timeouts. Giving up.");
                        return Optional.empty();
                    }
                } else if (isRetryable(cause)) {
                    timeoutCount = 0; // Reset on non-timeout retryable error
                    logger.debug("Retryable error on attempt {}/{}: {}",
                            attempt, maxRetries, cause != null ? cause.getMessage() : e.getMessage());
                } else {
                    timeoutCount = 0; // Reset on non-timeout error
                    logger.error("Non-retryable error calling Ollama generate: {}",
                            cause != null ? cause.getMessage() : e.getMessage());
                    handleError(cause instanceof Exception ? (Exception) cause : e);
                    return Optional.empty();
                }
            } catch (Exception e) {
                lastException = e;
                timeoutCount = 0; // Reset on non-timeout error
                logger.error("Error calling Ollama generate on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
                handleError(e);
            }

            // Backoff before retry (shorter for timeouts)
            if (attempt < maxRetries) {
                try {
                    long backoffMs = timeoutCount > 0 ? 500 : (long) Math.pow(2, attempt) * 1000;
                    logger.debug("Backing off {}ms before retry", backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // Continue with retry anyway
                }
            }
        }

        logger.warn("All Ollama generate retries exhausted: {}",
                lastException != null ? lastException.getMessage() : "unknown error");
        return Optional.empty();
    }

    /**
     * List available models on the Ollama server.
     */
    public List<ModelInfo> listModels() {
        if (!config.isEnabled()) {
            return Collections.emptyList();
        }

        try {
            ListModelsResponse response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(ListModelsResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.models() != null) {
                return response.models();
            }
        } catch (Exception e) {
            logger.debug("Error listing Ollama models: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Build options map for generation requests.
     */
    private Map<String, Object> buildOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", config.getTemperature());
        options.put("num_predict", config.getMaxTokens());
        options.put("num_ctx", config.getContextWindow());
        return options;
    }

    /**
     * Check if an exception is retryable.
     */
    private boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            // Retry on 429 (rate limit), 5xx (server error)
            return status == 429 || status >= 500;
        }

        // Retry on connection issues and timeouts
        if (throwable instanceof ReadTimeoutException ||
                throwable instanceof IOException) {
            return true;
        }

        // Check cause as well
        Throwable cause = throwable.getCause();
        if (cause != null) {
            return cause instanceof ReadTimeoutException ||
                    cause instanceof IOException;
        }

        return false;
    }

    /**
     * Handle errors and potentially mark server as unavailable.
     */
    private void handleError(Exception e) {
        if (e instanceof WebClientResponseException wcre) {
            if (wcre.getStatusCode().value() == 404) {
                logger.warn("Ollama model '{}' not found. Run: ollama pull {}",
                        config.getModel(), config.getModel());
            }
        } else if (e.getCause() instanceof ConnectException) {
            serverAvailable = false;
            logger.warn("Ollama server disconnected. Start with: ollama serve");
        } else if (e instanceof ConnectException) {
            serverAvailable = false;
            logger.warn("Ollama server disconnected. Start with: ollama serve");
        }
    }

    /**
     * Strip thinking tags from model responses.
     * Some models (e.g., qwen3, deepseek) use <think>...</think> tags
     * to wrap their reasoning process. We need to remove these to get
     * the actual response content.
     *
     * @param content Raw response content from the model
     * @return Cleaned content without thinking tags
     */
    private String stripThinkingTags(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // Remove <think>...</think> blocks (including multiline)
        String cleaned = content.replaceAll("(?s)<think>.*?</think>", "").trim();

        // Also handle unclosed thinking tags (model might get cut off)
        if (cleaned.contains("<think>")) {
            cleaned = cleaned.replaceAll("(?s)<think>.*", "").trim();
        }

        // Handle other common thinking patterns
        // Some models use <reasoning>...</reasoning> or similar
        cleaned = cleaned.replaceAll("(?s)<reasoning>.*?</reasoning>", "").trim();
        cleaned = cleaned.replaceAll("(?s)<thought>.*?</thought>", "").trim();

        // If after stripping we have nothing, return original content
        // (in case model didn't use thinking tags and content was just whitespace)
        if (cleaned.isEmpty() && !content.isBlank()) {
            logger.debug("Response became empty after stripping tags, returning original: {}",
                    content.substring(0, Math.min(100, content.length())));
            return content;
        }

        return cleaned;
    }
}
