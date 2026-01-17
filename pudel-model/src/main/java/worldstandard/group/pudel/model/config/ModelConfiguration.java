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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring configuration for the pudel-model module.
 * Sets up WebClient for Ollama HTTP calls.
 */
@Configuration
@EnableConfigurationProperties({OllamaConfig.class})
public class ModelConfiguration {

    /**
     * WebClient configured for Ollama API calls.
     */
    @Bean("ollamaWebClient")
    public WebClient ollamaWebClient(OllamaConfig config) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));

        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB
                .build();
    }
}

