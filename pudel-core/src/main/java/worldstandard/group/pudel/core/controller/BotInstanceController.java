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
package worldstandard.group.pudel.core.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * REST controller for bot instance management.
 * Uses GuildSettings for all configuration operations.
 */
@RestController
@RequestMapping("/api/instances")
public class BotInstanceController {

    private final GuildInitializationService guildInitializationService;

    public BotInstanceController(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    /**
     * Get bot configuration for a guild.
     * @param guildId the guild ID
     * @return guild settings or 404 if not found
     */
    @GetMapping("/{guildId}")
    public ResponseEntity<GuildSettings> getGuildSettings(@PathVariable("guildId") String guildId) {
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Update bot configuration for a guild.
     * @param guildId the guild ID
     * @param settings the updated settings
     * @return updated settings
     */
    @PutMapping("/{guildId}")
    public ResponseEntity<GuildSettings> updateGuildSettings(
            @PathVariable("guildId") String guildId,
            @RequestBody GuildSettings settings) {

        GuildSettings existing = guildInitializationService.getOrCreateGuildSettings(guildId);

        // Update fields if provided
        if (settings.getPrefix() != null) {
            existing.setPrefix(settings.getPrefix());
        }
        if (settings.getVerbosity() != null) {
            existing.setVerbosity(settings.getVerbosity());
        }
        if (settings.getCooldown() != null) {
            existing.setCooldown(settings.getCooldown());
        }
        if (settings.getLogChannel() != null) {
            existing.setLogChannel(settings.getLogChannel());
        }
        if (settings.getBotChannel() != null) {
            existing.setBotChannel(settings.getBotChannel());
        }
        if (settings.getBiography() != null) {
            existing.setBiography(settings.getBiography());
        }
        if (settings.getPersonality() != null) {
            existing.setPersonality(settings.getPersonality());
        }
        if (settings.getPreferences() != null) {
            existing.setPreferences(settings.getPreferences());
        }
        if (settings.getDialogueStyle() != null) {
            existing.setDialogueStyle(settings.getDialogueStyle());
        }
        if (settings.getNickname() != null) {
            existing.setNickname(settings.getNickname());
        }
        if (settings.getLanguage() != null) {
            existing.setLanguage(settings.getLanguage());
        }
        if (settings.getResponseLength() != null) {
            existing.setResponseLength(settings.getResponseLength());
        }
        if (settings.getFormality() != null) {
            existing.setFormality(settings.getFormality());
        }
        if (settings.getEmoteUsage() != null) {
            existing.setEmoteUsage(settings.getEmoteUsage());
        }
        if (settings.getQuirks() != null) {
            existing.setQuirks(settings.getQuirks());
        }
        if (settings.getTopicsInterest() != null) {
            existing.setTopicsInterest(settings.getTopicsInterest());
        }
        if (settings.getTopicsAvoid() != null) {
            existing.setTopicsAvoid(settings.getTopicsAvoid());
        }
        if (settings.getSystemPromptPrefix() != null) {
            existing.setSystemPromptPrefix(settings.getSystemPromptPrefix());
        }
        if (settings.getAiEnabled() != null) {
            existing.setAiEnabled(settings.getAiEnabled());
        }

        guildInitializationService.updateGuildSettings(guildId, existing);
        return ResponseEntity.ok(existing);
    }

    /**
     * Response for health check.
     */
    static class HealthResponse {
        public String status;
        public long timestamp;

        public HealthResponse(String status, long timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
    }

    /**
     * Response for status.
     */
    static class StatusResponse {
        public String status;
        public int activeGuilds;
        public long timestamp;

        public StatusResponse(String status, int activeGuilds, long timestamp) {
            this.status = status;
            this.activeGuilds = activeGuilds;
            this.timestamp = timestamp;
        }
    }
}
