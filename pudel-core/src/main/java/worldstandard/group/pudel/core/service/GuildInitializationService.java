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
package worldstandard.group.pudel.core.service;

import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.repository.GuildSettingsRepository;

/**
 * Service for guild initialization and lifecycle management.
 * Note: This service intentionally does NOT extend BaseService to avoid
 * circular dependency with JDA (JDA -> DiscordEventListener -> GuildInitializationService -> JDA)
 */
@Service
@Transactional
public class GuildInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(GuildInitializationService.class);

    private final GuildSettingsRepository guildSettingsRepository;
    private final SchemaManagementService schemaManagementService;

    public GuildInitializationService(GuildSettingsRepository guildSettingsRepository,
                                      @Lazy SchemaManagementService schemaManagementService) {
        this.guildSettingsRepository = guildSettingsRepository;
        this.schemaManagementService = schemaManagementService;
    }

    /**
     * Initialize a guild when the bot joins or on startup.
     * Creates guild settings and a separate database schema for the guild.
     * @param guild the Discord guild
     */
    public void initializeGuild(Guild guild) {
        String guildId = guild.getId();
        String guildName = guild.getName();
        long guildIdLong = guild.getIdLong();

        logger.info("Initializing guild: {} ({})", guildName, guildId);

        // Check if guild settings already exist
        boolean settingsExist = guildSettingsRepository.findByGuildId(guildId).isPresent();

        if (!settingsExist) {
            // Create guild settings
            GuildSettings settings = new GuildSettings(guildId);
            settings.setSchemaCreated(false);
            guildSettingsRepository.save(settings);
            logger.info("Created guild settings for: {} ({})", guildName, guildId);
        }

        // Create per-guild schema for isolated data storage
        try {
            if (!schemaManagementService.schemaExists(guildIdLong)) {
                schemaManagementService.createGuildSchema(guildIdLong);

                // Update settings to mark schema as created
                guildSettingsRepository.findByGuildId(guildId).ifPresent(settings -> {
                    settings.setSchemaCreated(true);
                    guildSettingsRepository.save(settings);
                });

                logger.info("Created database schema for guild: {} ({})", guildName, guildId);
            } else {
                logger.debug("Schema already exists for guild: {} ({})", guildName, guildId);
            }
        } catch (Exception e) {
            logger.error("Failed to create schema for guild {} ({}): {}", guildName, guildId, e.getMessage(), e);
        }

        logger.info("Guild initialized successfully: {} ({})", guildName, guildId);
    }

    /**
     * Clean up when the bot leaves a guild.
     * Optionally drops the guild schema (currently disabled to preserve data).
     * @param guildId the Discord guild ID
     */
    public void cleanupGuild(String guildId) {
        logger.info("Cleaning up guild: {}", guildId);

        guildSettingsRepository.findByGuildId(guildId)
                .ifPresent(guildSettingsRepository::delete);

        // Note: We intentionally do NOT drop the guild schema on leave
        // to preserve data in case the bot is re-added later.
        // If you want to drop schema on leave, uncomment below:
        // try {
        //     schemaManagementService.dropGuildSchema(Long.parseLong(guildId));
        // } catch (Exception e) {
        //     logger.warn("Failed to drop schema for guild {}: {}", guildId, e.getMessage());
        // }

        logger.info("Guild cleaned up: {}", guildId);
    }

    /**
     * Get or create guild settings.
     * @param guildId the Discord guild ID
     * @return guild settings
     */
    public GuildSettings getOrCreateGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId)
                .orElseGet(() -> {
                    GuildSettings settings = new GuildSettings(guildId);
                    return guildSettingsRepository.save(settings);
                });
    }

    /**
     * Get guild settings.
     * @param guildId the Discord guild ID
     * @return guild settings or null
     */
    public GuildSettings getGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId).orElse(null);
    }

    /**
     * Update guild settings.
     * @param guildId the Discord guild ID
     * @param settings the updated settings
     * @return the saved settings
     */
    public GuildSettings updateGuildSettings(String guildId, GuildSettings settings) {
        settings.setGuildId(guildId);
        return guildSettingsRepository.save(settings);
    }
}

