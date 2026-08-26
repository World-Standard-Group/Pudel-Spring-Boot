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
package group.worldstandard.pudel.core.config.brain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the reworked PudelBrain.
 * <p>
 * Controls Ollama completion, passive context collection, dialogue history,
 * Discord interaction behavior, and MCP tool integration.
 * <p>
 * Set {@code pudel.brain.use-legacy-model-module=true} to continue using the
 * deprecated pudel-model module instead of the new built-in brain.
 */
@Component
@ConfigurationProperties(prefix = "pudel.brain")
public class PudelBrainConfig {

    /**
     * Whether to use the deprecated pudel-model module instead of the new brain.
     * Set to true for backward compatibility during migration.
     * @deprecated The pudel-model module is deprecated and will be removed.
     */
    @Deprecated
    private boolean useLegacyModelModule = false;

    private Ollama ollama = new Ollama();
    private Completion completion = new Completion();
    private PassiveContext passiveContext = new PassiveContext();
    private DialogueHistory dialogueHistory = new DialogueHistory();
    private Discord discord = new Discord();
    private Mcp mcp = new Mcp();

    // ===============================
    // Getters / Setters
    // ===============================

    @Deprecated
    public boolean isUseLegacyModelModule() {
        return useLegacyModelModule;
    }

    @Deprecated
    public void setUseLegacyModelModule(boolean useLegacyModelModule) {
        this.useLegacyModelModule = useLegacyModelModule;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public Completion getCompletion() {
        return completion;
    }

    public void setCompletion(Completion completion) {
        this.completion = completion;
    }

    public PassiveContext getPassiveContext() {
        return passiveContext;
    }

    public void setPassiveContext(PassiveContext passiveContext) {
        this.passiveContext = passiveContext;
    }

    public DialogueHistory getDialogueHistory() {
        return dialogueHistory;
    }

    public void setDialogueHistory(DialogueHistory dialogueHistory) {
        this.dialogueHistory = dialogueHistory;
    }

    public Discord getDiscord() {
        return discord;
    }

    public void setDiscord(Discord discord) {
        this.discord = discord;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp;
    }

    // ===============================
    // Inner Config Classes
    // ===============================

    /**
     * Ollama connection settings.
     */
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.2";
        private String embeddingModel; // null => use `model` for embeddings
        private int timeoutSeconds = 120;
        private double temperature = 0.7;
        private int maxTokens = 2048;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbeddingModel() { return embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel : model; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    /**
     * Completion behavior settings.
     */
    public static class Completion {
        private int maxContextMessages = 20;
        private int systemPromptMaxLength = 4000;
        private boolean enableRoleplay = true;

        public int getMaxContextMessages() { return maxContextMessages; }
        public void setMaxContextMessages(int maxContextMessages) { this.maxContextMessages = maxContextMessages; }
        public int getSystemPromptMaxLength() { return systemPromptMaxLength; }
        public void setSystemPromptMaxLength(int systemPromptMaxLength) { this.systemPromptMaxLength = systemPromptMaxLength; }
        public boolean isEnableRoleplay() { return enableRoleplay; }
        public void setEnableRoleplay(boolean enableRoleplay) { this.enableRoleplay = enableRoleplay; }
    }

    /**
     * Passive context collection settings.
     */
    public static class PassiveContext {
        private boolean enabled = true;
        private int maxQueueSize = 200;
        private long maxAgeMs = 120_000;
        private long processingIntervalMs = 1000;
        private int batchSize = 5;
        private boolean extractEntities = true;
        private boolean trackAttachments = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxQueueSize() { return maxQueueSize; }
        public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
        public long getMaxAgeMs() { return maxAgeMs; }
        public void setMaxAgeMs(long maxAgeMs) { this.maxAgeMs = maxAgeMs; }
        public long getProcessingIntervalMs() { return processingIntervalMs; }
        public void setProcessingIntervalMs(long processingIntervalMs) { this.processingIntervalMs = processingIntervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public boolean isExtractEntities() { return extractEntities; }
        public void setExtractEntities(boolean extractEntities) { this.extractEntities = extractEntities; }
        public boolean isTrackAttachments() { return trackAttachments; }
        public void setTrackAttachments(boolean trackAttachments) { this.trackAttachments = trackAttachments; }
    }

    /**
     * Dialogue history storage settings.
     */
    public static class DialogueHistory {
        private boolean enabled = true;
        private int maxHistoryPerUser = 50;
        private boolean storeRespondTo = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxHistoryPerUser() { return maxHistoryPerUser; }
        public void setMaxHistoryPerUser(int maxHistoryPerUser) { this.maxHistoryPerUser = maxHistoryPerUser; }
        public boolean isStoreRespondTo() { return storeRespondTo; }
        public void setStoreRespondTo(boolean storeRespondTo) { this.storeRespondTo = storeRespondTo; }
    }

    /**
     * Discord interaction settings.
     */
    public static class Discord {
        private boolean sendTyping = true;
        private int maxMessageLength = 2000;
        private boolean formatMarkdown = true;
        private boolean readTextAttachments = true;

        public boolean isSendTyping() { return sendTyping; }
        public void setSendTyping(boolean sendTyping) { this.sendTyping = sendTyping; }
        public int getMaxMessageLength() { return maxMessageLength; }
        public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }
        public boolean isFormatMarkdown() { return formatMarkdown; }
        public void setFormatMarkdown(boolean formatMarkdown) { this.formatMarkdown = formatMarkdown; }
        public boolean isReadTextAttachments() { return readTextAttachments; }
        public void setReadTextAttachments(boolean readTextAttachments) { this.readTextAttachments = readTextAttachments; }
    }

    /**
     * MCP (Model Context Protocol) tool settings.
     */
    public static class Mcp {
        private boolean enabled = true;
        private int toolTimeoutSeconds = 30;
        private int maxToolIterations = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getToolTimeoutSeconds() { return toolTimeoutSeconds; }
        public void setToolTimeoutSeconds(int toolTimeoutSeconds) { this.toolTimeoutSeconds = toolTimeoutSeconds; }
        public int getMaxToolIterations() { return maxToolIterations; }
        public void setMaxToolIterations(int maxToolIterations) { this.maxToolIterations = maxToolIterations; }
    }
}

