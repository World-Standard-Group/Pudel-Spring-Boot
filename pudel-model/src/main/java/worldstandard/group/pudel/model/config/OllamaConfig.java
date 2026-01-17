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
package worldstandard.group.pudel.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Ollama local LLM.
 * <p>
 * Ollama runs as an external process on localhost, keeping model memory
 * separate from JVM heap for better resource management.
 */
@ConfigurationProperties(prefix = "pudel.ollama")
public class OllamaConfig {

    /**
     * Enable/disable Ollama integration.
     * When disabled, falls back to template-based responses.
     */
    private boolean enabled = true;

    /**
     * Ollama server base URL.
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Model to use for chat generation.
     * Recommended: phi-3-mini, gemma-2b, llama3.2 (2-4GB VRAM)
     */
    private String model = "phi3:mini";

    /**
     * Model to use for text analysis (intent, sentiment, etc.).
     * Can be a smaller, faster model. Defaults to main model if not set.
     */
    private String analysisModel = null;

    /**
     * Model temperature (0.0 - 1.0).
     * Lower = more deterministic, Higher = more creative.
     */
    private double temperature = 0.7;

    /**
     * Maximum tokens to generate in response.
     */
    private int maxTokens = 256;

    /**
     * Request timeout in seconds.
     */
    private int timeoutSeconds = 60;

    /**
     * Keep model loaded in memory between requests.
     */
    private boolean keepAlive = true;

    /**
     * Keep alive duration in minutes (0 = default, -1 = forever).
     */
    private String keepAliveDuration = "5m";

    /**
     * System prompt prefix for Pudel's base personality.
     */
    private String systemPromptPrefix = """
        You are Pudel (A given name might be given on biography), a helpful and friendly Discord assistant bot.
        You are designed to be a personal maid/secretary for Discord servers.
        Be concise, helpful, and maintain the personality traits given to you.
        Respond naturally like a real assistant would.
        """;

    /**
     * Context window size for the model.
     */
    private int contextWindow = 4096;

    /**
     * Number of retries on failure.
     */
    private int retryCount = 2;

    /**
     * Enable streaming responses (for typing indicator).
     */
    private boolean streaming = false;

    /**
     * Disable thinking mode for models that support it (e.g., qwen3, deepseek).
     * When enabled, adds /no_think instruction to prevent the model from
     * using &lt;think&gt;...&lt;/think&gt; tags which consume tokens.
     */
    private boolean disableThinking = true;

    // ================================
    // Embedding Configuration
    // ================================

    /**
     * Enable/disable Ollama-based embeddings.
     */
    private boolean embeddingEnabled = true;

    /**
     * Model to use for embeddings.
     * Recommended: nomic-embed-text, mxbai-embed-large, all-minilm
     */
    private String embeddingModel = "nomic-embed-text";

    /**
     * Preprocess Discord syntax before embedding for cleaner semantic matching.
     */
    private boolean embeddingPreprocessDiscord = true;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAnalysisModel() {
        return analysisModel;
    }

    public void setAnalysisModel(String analysisModel) {
        this.analysisModel = analysisModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public String getKeepAliveDuration() {
        return keepAliveDuration;
    }

    public void setKeepAliveDuration(String keepAliveDuration) {
        this.keepAliveDuration = keepAliveDuration;
    }

    public String getSystemPromptPrefix() {
        return systemPromptPrefix;
    }

    public void setSystemPromptPrefix(String systemPromptPrefix) {
        this.systemPromptPrefix = systemPromptPrefix;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isDisableThinking() {
        return disableThinking;
    }

    public void setDisableThinking(boolean disableThinking) {
        this.disableThinking = disableThinking;
    }

    public boolean isEmbeddingEnabled() {
        return embeddingEnabled;
    }

    public void setEmbeddingEnabled(boolean embeddingEnabled) {
        this.embeddingEnabled = embeddingEnabled;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public boolean isEmbeddingPreprocessDiscord() {
        return embeddingPreprocessDiscord;
    }

    public void setEmbeddingPreprocessDiscord(boolean embeddingPreprocessDiscord) {
        this.embeddingPreprocessDiscord = embeddingPreprocessDiscord;
    }
}

