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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.JDA;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.repository.GuildSettingsRepository;

import java.util.Optional;

/**
 * Service for guild settings management.
 */
@Service
public class GuildSettingsService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(GuildSettingsService.class);

    private final GuildSettingsRepository guildSettingsRepository;

    public GuildSettingsService(JDA jda, GuildSettingsRepository guildSettingsRepository) {
        super(jda);
        this.guildSettingsRepository = guildSettingsRepository;
    }

    /**
     * Get guild settings by guild ID.
     */
    public Optional<GuildSettings> getGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId);
    }

    /**
     * Get or create default guild settings.
     */
    public GuildSettings getOrCreateGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId)
                .orElseGet(() -> {
                    GuildSettings settings = new GuildSettings(guildId);
                    return guildSettingsRepository.save(settings);
                });
    }

    /**
     * Update guild settings.
     */
    public GuildSettings updateGuildSettings(String guildId, GuildSettings updatedSettings) {
        try {
            GuildSettings settings = getOrCreateGuildSettings(guildId);

            if (updatedSettings.getPrefix() != null) {
                settings.setPrefix(updatedSettings.getPrefix());
            }
            if (updatedSettings.getVerbosity() != null) {
                settings.setVerbosity(updatedSettings.getVerbosity());
            }
            if (updatedSettings.getCooldown() != null) {
                settings.setCooldown(updatedSettings.getCooldown());
            }
            if (updatedSettings.getLogChannel() != null) {
                settings.setLogChannel(updatedSettings.getLogChannel());
            }
            if (updatedSettings.getBotChannel() != null) {
                settings.setBotChannel(updatedSettings.getBotChannel());
            }
            if (updatedSettings.getBiography() != null) {
                settings.setBiography(updatedSettings.getBiography());
            }
            if (updatedSettings.getPersonality() != null) {
                settings.setPersonality(updatedSettings.getPersonality());
            }
            if (updatedSettings.getPreferences() != null) {
                settings.setPreferences(updatedSettings.getPreferences());
            }
            if (updatedSettings.getDialogueStyle() != null) {
                settings.setDialogueStyle(updatedSettings.getDialogueStyle());
            }

            GuildSettings saved = guildSettingsRepository.save(settings);
            log.info("Updated settings for guild: {}", guildId);
            return saved;
        } catch (Exception e) {
            log.error("Error updating guild settings", e);
            throw new RuntimeException("Failed to update guild settings", e);
        }
    }

    /**
     * Validate guild settings.
     */
    public boolean validateSettings(GuildSettings settings) {
        // Validate prefix length
        if (settings.getPrefix() != null && (settings.getPrefix().isEmpty() || settings.getPrefix().length() > 5)) {
            return false;
        }

        // Validate verbosity level
        if (settings.getVerbosity() != null && (settings.getVerbosity() < 1 || settings.getVerbosity() > 3)) {
            return false;
        }

        // Validate cooldown
        if (settings.getCooldown() != null && settings.getCooldown() < 0) {
            return false;
        }

        return true;
    }

    /**
     * Delete guild settings.
     */
    public void deleteGuildSettings(String guildId) {
        try {
            guildSettingsRepository.findByGuildId(guildId)
                    .ifPresent(guildSettingsRepository::delete);
            log.info("Deleted settings for guild: {}", guildId);
        } catch (Exception e) {
            log.error("Error deleting guild settings", e);
            throw new RuntimeException("Failed to delete guild settings", e);
        }
    }
}

