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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTOs for Ollama API communication.
 */
public final class OllamaDto {

    private OllamaDto() {
        // Utility class
    }

    // ================================
    // Chat API DTOs
    // ================================

    /**
     * Chat message for Ollama conversation.
     */
    public record ChatMessage(
            String role,
            String content
    ) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /**
     * Request body for /api/chat endpoint.
     */
    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            Boolean stream,
            Map<String, Object> options,
            @JsonProperty("keep_alive") String keepAlive
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private List<ChatMessage> messages;
            private Boolean stream = false;
            private Map<String, Object> options;
            private String keepAlive = "5m";

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder messages(List<ChatMessage> messages) {
                this.messages = messages;
                return this;
            }

            public Builder stream(Boolean stream) {
                this.stream = stream;
                return this;
            }

            public Builder options(Map<String, Object> options) {
                this.options = options;
                return this;
            }

            public Builder keepAlive(String keepAlive) {
                this.keepAlive = keepAlive;
                return this;
            }

            public ChatRequest build() {
                return new ChatRequest(model, messages, stream, options, keepAlive);
            }
        }
    }

    /**
     * Response from /api/chat endpoint.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(
            String model,
            @JsonProperty("created_at") String createdAt,
            ChatMessage message,
            Boolean done,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("load_duration") Long loadDuration,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("eval_duration") Long evalDuration
    ) {}

    // ================================
    // Generate API DTOs (alternative)
    // ================================

    /**
     * Request body for /api/generate endpoint.
     */
    public record GenerateRequest(
            String model,
            String prompt,
            String system,
            Boolean stream,
            Map<String, Object> options,
            @JsonProperty("keep_alive") String keepAlive
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private String prompt;
            private String system;
            private Boolean stream = false;
            private Map<String, Object> options;
            private String keepAlive = "5m";

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder prompt(String prompt) {
                this.prompt = prompt;
                return this;
            }

            public Builder system(String system) {
                this.system = system;
                return this;
            }

            public Builder stream(Boolean stream) {
                this.stream = stream;
                return this;
            }

            public Builder options(Map<String, Object> options) {
                this.options = options;
                return this;
            }

            public Builder keepAlive(String keepAlive) {
                this.keepAlive = keepAlive;
                return this;
            }

            public GenerateRequest build() {
                return new GenerateRequest(model, prompt, system, stream, options, keepAlive);
            }
        }
    }

    /**
     * Response from /api/generate endpoint.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateResponse(
            String model,
            @JsonProperty("created_at") String createdAt,
            String response,
            Boolean done,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("load_duration") Long loadDuration,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("eval_duration") Long evalDuration
    ) {}

    // ================================
    // Embedding API DTOs
    // ================================

    /**
     * Request body for /api/embeddings endpoint.
     */
    public record EmbeddingRequest(
            String model,
            String prompt,
            @JsonProperty("keep_alive") String keepAlive
    ) {}

    /**
     * Response from /api/embeddings endpoint.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingResponse(
            List<Double> embedding
    ) {}

    // ================================
    // Model Management DTOs
    // ================================

    /**
     * Response from /api/tags endpoint (list models).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListModelsResponse(
            List<ModelInfo> models
    ) {}

    /**
     * Model info from list response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
            String name,
            @JsonProperty("modified_at") String modifiedAt,
            Long size,
            String digest,
            Details details
    ) {}

    /**
     * Model details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(
            String format,
            String family,
            @JsonProperty("parameter_size") String parameterSize,
            @JsonProperty("quantization_level") String quantizationLevel
    ) {}

    /**
     * Health check - ping Ollama server.
     */
    public record HealthStatus(
            boolean available,
            String version,
            List<String> loadedModels
    ) {}
}

