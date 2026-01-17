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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for accessing guild-specific schema data.
 * Provides methods to store/retrieve dialogue history, memory, and user preferences
 * within each guild's isolated schema.
 */
@Service
@Transactional
public class GuildDataService {

    private static final Logger logger = LoggerFactory.getLogger(GuildDataService.class);

    private final JdbcTemplate jdbcTemplate;
    private final SchemaManagementService schemaManagementService;

    public GuildDataService(JdbcTemplate jdbcTemplate, SchemaManagementService schemaManagementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaManagementService = schemaManagementService;
    }

    // ===========================================
    // Dialogue History Operations
    // ===========================================

    /**
     * Store a dialogue entry (user message + bot response) in guild schema.
     */
    public void storeDialogue(long guildId, long userId, long channelId,
                              String userMessage, String botResponse, String intent) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "INSERT INTO " + schemaName + ".dialogue_history " +
                    "(user_id, channel_id, user_message, bot_response, intent, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, userId, channelId, userMessage, botResponse, intent,
                    Timestamp.valueOf(LocalDateTime.now()));
            logger.debug("Stored dialogue for guild {} user {}", guildId, userId);
        } catch (Exception e) {
            logger.error("Error storing dialogue for guild {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Get recent dialogue history for a user in a guild.
     * @param limit maximum number of entries to return
     */
    public List<Map<String, Object>> getRecentDialogue(long guildId, long userId, int limit) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".dialogue_history " +
                    "WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, userId, limit);
        } catch (Exception e) {
            logger.error("Error getting dialogue for guild {} user {}: {}", guildId, userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all dialogue history for a channel.
     */
    public List<Map<String, Object>> getChannelDialogue(long guildId, long channelId, int limit) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".dialogue_history " +
                    "WHERE channel_id = ? ORDER BY created_at DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, channelId, limit);
        } catch (Exception e) {
            logger.error("Error getting channel dialogue for guild {}: {}", guildId, e.getMessage());
            return List.of();
        }
    }

    // ===========================================
    // Memory Operations
    // ===========================================

    /**
     * Store a memory entry (key-value) in guild schema.
     */
    public void storeMemory(long guildId, String key, String value, String category, Long createdBy) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "INSERT INTO " + schemaName + ".memory (key, value, category, created_by, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (key) DO UPDATE SET value = ?, category = ?, updated_at = ?";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(sql, key, value, category, createdBy, now, now, value, category, now);
            logger.debug("Stored memory '{}' for guild {}", key, guildId);
        } catch (Exception e) {
            logger.error("Error storing memory for guild {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Get a memory entry by key.
     */
    public Optional<String> getMemory(long guildId, String key) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "SELECT value FROM " + schemaName + ".memory WHERE key = ?";
            List<String> results = jdbcTemplate.queryForList(sql, String.class, key);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("Memory '{}' not found for guild {}: {}", key, guildId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all memories by category.
     */
    public List<Map<String, Object>> getMemoriesByCategory(long guildId, String category) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".memory WHERE category = ? ORDER BY created_at DESC";
            return jdbcTemplate.queryForList(sql, category);
        } catch (Exception e) {
            logger.error("Error getting memories for guild {}: {}", guildId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Delete a memory entry.
     */
    public boolean deleteMemory(long guildId, String key) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "DELETE FROM " + schemaName + ".memory WHERE key = ?";
            int deleted = jdbcTemplate.update(sql, key);
            return deleted > 0;
        } catch (Exception e) {
            logger.error("Error deleting memory for guild {}: {}", guildId, e.getMessage());
            return false;
        }
    }

    // ===========================================
    // User Preferences Operations (per guild)
    // ===========================================

    /**
     * Store/update user preferences within a guild.
     */
    public void storeUserPreference(long guildId, long userId, String preferredName, String customSettings, String notes) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "INSERT INTO " + schemaName + ".user_preferences " +
                    "(user_id, preferred_name, custom_settings, notes, created_at, updated_at) " +
                    "VALUES (?, ?, ?::jsonb, ?, ?, ?) " +
                    "ON CONFLICT (user_id) DO UPDATE SET preferred_name = ?, custom_settings = ?::jsonb, notes = ?, updated_at = ?";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(sql, userId, preferredName, customSettings, notes, now, now,
                    preferredName, customSettings, notes, now);
            logger.debug("Stored user preferences for user {} in guild {}", userId, guildId);
        } catch (Exception e) {
            logger.error("Error storing user preferences for guild {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Get user preferences within a guild.
     */
    public Optional<Map<String, Object>> getUserPreference(long guildId, long userId) {
        String schemaName = schemaManagementService.getGuildSchemaName(guildId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".user_preferences WHERE user_id = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("User preferences not found for user {} in guild {}: {}", userId, guildId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get preferred name for a user in a guild.
     */
    public Optional<String> getPreferredName(long guildId, long userId) {
        return getUserPreference(guildId, userId)
                .map(prefs -> (String) prefs.get("preferred_name"))
                .filter(name -> name != null && !name.isEmpty());
    }

}