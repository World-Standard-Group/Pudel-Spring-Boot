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
package group.worldstandard.pudel.core.config;

import group.worldstandard.pudel.core.brain.ollama.OllamaClient;
import group.worldstandard.pudel.core.config.brain.PudelBrainConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Ollama client bean.
 * <p>
 * Only created when pudel.brain.use-legacy-model-module is false (the default).
 * When the legacy model module is enabled, the old PudelModelService is used instead.
 */
@Configuration
@ConditionalOnProperty(name = "pudel.brain.use-legacy-model-module", havingValue = "false", matchIfMissing = true)
public class OllamaClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClientConfig.class);

    @Bean
    public OllamaClient ollamaClient(PudelBrainConfig brainConfig) {
        PudelBrainConfig.Ollama ollamaConfig = brainConfig.getOllama();

        logger.info("Creating OllamaClient: baseUrl={}, model={}, embeddingModel={}, timeout={}s",
                ollamaConfig.getBaseUrl(),
                ollamaConfig.getModel(),
                ollamaConfig.getEmbeddingModel(),
                ollamaConfig.getTimeoutSeconds());

        return new OllamaClient(
                ollamaConfig.getBaseUrl(),
                ollamaConfig.getModel(),
                ollamaConfig.getEmbeddingModel(),
                ollamaConfig.getTemperature(),
                ollamaConfig.getMaxTokens(),
                ollamaConfig.getTimeoutSeconds()
        );
    }
}

