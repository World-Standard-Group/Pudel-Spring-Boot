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
 * Service for accessing user-specific schema data.
 * Provides methods to store/retrieve dialogue history, memory, and Pudel settings
 * for DM conversations (personalized per user).
 */
@Service
@Transactional
public class UserDataService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);

    private final JdbcTemplate jdbcTemplate;
    private final SchemaManagementService schemaManagementService;

    public UserDataService(JdbcTemplate jdbcTemplate, SchemaManagementService schemaManagementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaManagementService = schemaManagementService;
    }

    /**
     * Ensure user schema exists before operations.
     */
    public void ensureUserSchema(long userId) {
        if (!schemaManagementService.userSchemaExists(userId)) {
            schemaManagementService.createUserSchema(userId);
        }
    }

    // ===========================================
    // Pudel Settings Operations (for DM)
    // ===========================================

    /**
     * Get user's personal Pudel settings for DM conversations.
     */
    public Optional<Map<String, Object>> getPudelSettings(long userId) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".pudel_settings LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
        } catch (Exception e) {
            logger.debug("Pudel settings not found for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update user's personal Pudel settings.
     */
    public void updatePudelSettings(long userId, String biography, String personality,
                                    String preferences, String dialogueStyle) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            // Check if row exists
            String countSql = "SELECT COUNT(*) FROM " + schemaName + ".pudel_settings";
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);

            if (count != null && count > 0) {
                // Update existing
                String sql = "UPDATE " + schemaName + ".pudel_settings SET " +
                        "biography = COALESCE(?, biography), " +
                        "personality = COALESCE(?, personality), " +
                        "preferences = COALESCE(?, preferences), " +
                        "dialogue_style = COALESCE(?, dialogue_style), " +
                        "updated_at = ?";
                jdbcTemplate.update(sql, biography, personality, preferences, dialogueStyle, now);
            } else {
                // Insert new
                String sql = "INSERT INTO " + schemaName + ".pudel_settings " +
                        "(biography, personality, preferences, dialogue_style, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(sql, biography, personality, preferences, dialogueStyle, now, now);
            }

            logger.debug("Updated Pudel settings for user {}", userId);
        } catch (Exception e) {
            logger.error("Error updating Pudel settings for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get a specific Pudel setting field.
     */
    public Optional<String> getPudelSetting(long userId, String field) {
        return getPudelSettings(userId)
                .map(settings -> (String) settings.get(field))
                .filter(value -> value != null && !value.isEmpty());
    }

    // ===========================================
    // Dialogue History Operations (for DM)
    // ===========================================

    /**
     * Store a dialogue entry for DM conversation.
     */
    public void storeDialogue(long userId, String userMessage, String botResponse, String intent) {
        ensureUserSchema(userId);
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "INSERT INTO " + schemaName + ".dialogue_history " +
                    "(user_message, bot_response, intent, created_at) " +
                    "VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, userMessage, botResponse, intent,
                    Timestamp.valueOf(LocalDateTime.now()));
            logger.debug("Stored DM dialogue for user {}", userId);
        } catch (Exception e) {
            logger.error("Error storing DM dialogue for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get recent DM dialogue history.
     */
    public List<Map<String, Object>> getRecentDialogue(long userId, int limit) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".dialogue_history " +
                    "ORDER BY created_at DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, limit);
        } catch (Exception e) {
            logger.debug("Error getting DM dialogue for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ===========================================
    // Memory Operations (for DM)
    // ===========================================

    /**
     * Store a memory entry for a user.
     */
    public void storeMemory(long userId, String key, String value, String category) {
        ensureUserSchema(userId);
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "INSERT INTO " + schemaName + ".memory (key, value, category, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT (key) DO UPDATE SET value = ?, category = ?, updated_at = ?";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(sql, key, value, category, now, now, value, category, now);
            logger.debug("Stored memory '{}' for user {}", key, userId);
        } catch (Exception e) {
            logger.error("Error storing memory for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get a memory entry by key.
     */
    public Optional<String> getMemory(long userId, String key) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "SELECT value FROM " + schemaName + ".memory WHERE key = ?";
            List<String> results = jdbcTemplate.queryForList(sql, String.class, key);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
        } catch (Exception e) {
            logger.debug("Memory '{}' not found for user {}: {}", key, userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all memories for a user.
     */
    public List<Map<String, Object>> getAllMemories(long userId) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "SELECT * FROM " + schemaName + ".memory ORDER BY created_at DESC";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            logger.debug("Error getting memories for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Delete a memory entry.
     */
    public boolean deleteMemory(long userId, String key) {
        String schemaName = schemaManagementService.getUserSchemaName(userId);

        try {
            String sql = "DELETE FROM " + schemaName + ".memory WHERE key = ?";
            int deleted = jdbcTemplate.update(sql, key);
            return deleted > 0;
        } catch (Exception e) {
            logger.error("Error deleting memory for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}

