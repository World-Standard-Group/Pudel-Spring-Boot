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

package worldstandard.group.pudel.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import worldstandard.group.pudel.core.config.brain.ChatbotConfig;
import worldstandard.group.pudel.core.config.brain.MemoryConfig;
import worldstandard.group.pudel.core.config.database.SubscriptionTierConfig;
import worldstandard.group.pudel.model.config.OllamaConfig;

/**
 * Main entry point for the Pudel Discord Bot.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "worldstandard.group.pudel.core",
        "worldstandard.group.pudel.model"
})
@EnableConfigurationProperties({
        SubscriptionTierConfig.class,
        ChatbotConfig.class,
        MemoryConfig.class,
        OllamaConfig.class
})
public class Pudel {
    private static final Logger log = LoggerFactory.getLogger(Pudel.class);

    public static void main(String[] args) {
        log.info("==================================");
        log.info("Pudel Discord Bot is starting");
        log.info("==================================");

        SpringApplication.run(Pudel.class, args);

        log.info("==================================");
        log.info("Pudel Discord Bot started");
        log.info("==================================");
    }
}
