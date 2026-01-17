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
package worldstandard.group.pudel.core.bootstrap;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.repository.GuildSettingsRepository;
import worldstandard.group.pudel.core.service.SchemaManagementService;

import java.util.List;

/**
 * Bootstrap runner that ensures all guilds the bot is in have proper database schemas.
 * Runs on startup after JDA is ready.
 */
@Component
@Order(10) // Run after other bootstrap tasks
public class SchemaBootstrapRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchemaBootstrapRunner.class);

    private final JDA jda;
    private final GuildSettingsRepository guildSettingsRepository;
    private final SchemaManagementService schemaManagementService;

    public SchemaBootstrapRunner(@Lazy JDA jda,
                                 GuildSettingsRepository guildSettingsRepository,
                                 SchemaManagementService schemaManagementService) {
        this.jda = jda;
        this.guildSettingsRepository = guildSettingsRepository;
        this.schemaManagementService = schemaManagementService;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting schema bootstrap process...");

        // Wait for JDA to be ready
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for JDA to be ready", e);
            Thread.currentThread().interrupt();
            return;
        }

        List<Guild> guilds = jda.getGuilds();
        int schemaCreated = 0;
        int settingsCreated = 0;

        logger.info("Checking {} guilds for schema initialization...", guilds.size());

        for (Guild guild : guilds) {
            String guildId = guild.getId();
            long guildIdLong = guild.getIdLong();
            String guildName = guild.getName();

            // Ensure guild settings exist
            GuildSettings settings = guildSettingsRepository.findByGuildId(guildId)
                    .orElseGet(() -> {
                        GuildSettings newSettings = new GuildSettings(guildId);
                        newSettings.setSchemaCreated(false);
                        return guildSettingsRepository.save(newSettings);
                    });

            if (settings.getSchemaCreated() == null || !settings.getSchemaCreated()) {
                settingsCreated++;
            }

            // Ensure schema exists
            if (!schemaManagementService.schemaExists(guildIdLong)) {
                try {
                    schemaManagementService.createGuildSchema(guildIdLong);

                    // Update settings to mark schema as created
                    settings.setSchemaCreated(true);
                    guildSettingsRepository.save(settings);

                    schemaCreated++;
                    logger.info("Created schema for guild: {} ({})", guildName, guildId);
                } catch (Exception e) {
                    logger.error("Failed to create schema for guild {} ({}): {}",
                            guildName, guildId, e.getMessage());
                }
            } else if (settings.getSchemaCreated() == null || !settings.getSchemaCreated()) {
                // Schema exists but not marked in settings - update the flag
                settings.setSchemaCreated(true);
                guildSettingsRepository.save(settings);
            }
        }

        logger.info("Schema bootstrap complete. Created {} new schemas, {} guilds processed.",
                schemaCreated, guilds.size());
    }
}

