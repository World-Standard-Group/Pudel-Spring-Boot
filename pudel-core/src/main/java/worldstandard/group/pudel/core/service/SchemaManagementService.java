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

/**
 * Service for managing per-guild and per-user database schemas.
 * Each guild and user gets their own PostgreSQL schema for data isolation.
 * This enables Pudel's personalized behavior per guild/user.
 */
@Service
@Transactional
public class SchemaManagementService {

    private static final Logger logger = LoggerFactory.getLogger(SchemaManagementService.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===========================================
    // Guild Schema Management
    // ===========================================

    /**
     * Create a schema for a guild if it doesn't exist.
     * @param guildId the Discord guild ID
     */
    public void createGuildSchema(long guildId) {
        String schemaName = getGuildSchemaName(guildId);

        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
            );

            if (exists != null && exists > 0) {
                logger.debug("Schema already exists for guild {}: {}", guildId, schemaName);
                return;
            }

            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            logger.info("Created schema for guild {}: {}", guildId, schemaName);

            createGuildTables(schemaName);

        } catch (Exception e) {
            logger.error("Error creating schema for guild {}: {}", guildId, e.getMessage(), e);
        }
    }

    /**
     * Create tables for a guild schema.
     * @param schemaName the schema name
     */
    private void createGuildTables(String schemaName) {
        try {
            // Dialogue history - stores conversation history for chatbot functionality
            String dialogueHistoryTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".dialogue_history (\n" +
                    "    id BIGSERIAL PRIMARY KEY,\n" +
                    "    user_id BIGINT NOT NULL,\n" +
                    "    channel_id BIGINT NOT NULL,\n" +
                    "    user_message TEXT NOT NULL,\n" +
                    "    bot_response TEXT,\n" +
                    "    intent VARCHAR(100),\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(dialogueHistoryTable);

            // User preferences within guild - stores per-user customizations in this guild
            String userPreferencesTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".user_preferences (\n" +
                    "    user_id BIGINT PRIMARY KEY,\n" +
                    "    preferred_name VARCHAR(255),\n" +
                    "    custom_settings JSONB,\n" +
                    "    notes TEXT,\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(userPreferencesTable);

            // Memory table - stores guild-specific memories (facts, reminders, etc.)
            String memoryTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".memory (\n" +
                    "    id BIGSERIAL PRIMARY KEY,\n" +
                    "    key VARCHAR(255) UNIQUE NOT NULL,\n" +
                    "    value TEXT NOT NULL,\n" +
                    "    category VARCHAR(50) DEFAULT 'general',\n" +
                    "    created_by BIGINT,\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(memoryTable);


            // Indexes
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dialogue_user ON " + schemaName + ".dialogue_history(user_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dialogue_created ON " + schemaName + ".dialogue_history(created_at)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_key ON " + schemaName + ".memory(key)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_category ON " + schemaName + ".memory(category)");

            logger.info("Created tables for guild schema: {}", schemaName);

        } catch (Exception e) {
            logger.error("Error creating tables for schema {}: {}", schemaName, e.getMessage(), e);
        }
    }

    /**
     * Drop a schema for a guild.
     * @param guildId the Discord guild ID
     */
    public void dropGuildSchema(long guildId) {
        String schemaName = getGuildSchemaName(guildId);

        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            logger.info("Dropped schema for guild {}: {}", guildId, schemaName);
        } catch (Exception e) {
            logger.error("Error dropping schema for guild {}: {}", guildId, e.getMessage(), e);
        }
    }

    /**
     * Get the schema name for a guild.
     * @param guildId the Discord guild ID
     * @return the schema name
     */
    public String getGuildSchemaName(long guildId) {
        return "guild_" + guildId;
    }

    /**
     * Legacy method for compatibility.
     */
    public String getSchemaName(long guildId) {
        return getGuildSchemaName(guildId);
    }

    /**
     * Check if a schema exists for a guild.
     * @param guildId the Discord guild ID
     * @return true if schema exists
     */
    public boolean schemaExists(long guildId) {
        String schemaName = getGuildSchemaName(guildId);

        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
            );
            return exists != null && exists > 0;
        } catch (Exception e) {
            logger.error("Error checking schema existence for guild {}: {}", guildId, e.getMessage());
            return false;
        }
    }

    // ===========================================
    // User Schema Management (for DM/PM)
    // ===========================================

    /**
     * Create a schema for a user if it doesn't exist.
     * Used for DM conversations with personalized settings.
     * @param userId the Discord user ID
     */
    public void createUserSchema(long userId) {
        String schemaName = getUserSchemaName(userId);

        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
            );

            if (exists != null && exists > 0) {
                logger.debug("Schema already exists for user {}: {}", userId, schemaName);
                return;
            }

            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            logger.info("Created schema for user {}: {}", userId, schemaName);

            createUserTables(schemaName);

        } catch (Exception e) {
            logger.error("Error creating schema for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Create tables for a user schema.
     * @param schemaName the schema name
     */
    private void createUserTables(String schemaName) {
        try {
            // User's personal Pudel settings (biography, personality, etc. for DMs)
            String settingsTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".pudel_settings (\n" +
                    "    id SERIAL PRIMARY KEY,\n" +
                    "    biography TEXT,\n" +
                    "    personality TEXT,\n" +
                    "    preferences TEXT,\n" +
                    "    dialogue_style TEXT,\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(settingsTable);

            // Insert default row
            jdbcTemplate.execute("INSERT INTO " + schemaName + ".pudel_settings (biography, personality, preferences, dialogue_style) " +
                    "VALUES ('', '', '', '') ON CONFLICT DO NOTHING");

            // Dialogue history for DM conversations
            String dialogueHistoryTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".dialogue_history (\n" +
                    "    id BIGSERIAL PRIMARY KEY,\n" +
                    "    user_message TEXT NOT NULL,\n" +
                    "    bot_response TEXT,\n" +
                    "    intent VARCHAR(100),\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(dialogueHistoryTable);

            // Memory table - stores user-specific memories
            String memoryTable = "CREATE TABLE IF NOT EXISTS " + schemaName + ".memory (\n" +
                    "    id BIGSERIAL PRIMARY KEY,\n" +
                    "    key VARCHAR(255) UNIQUE NOT NULL,\n" +
                    "    value TEXT NOT NULL,\n" +
                    "    category VARCHAR(50) DEFAULT 'general',\n" +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(memoryTable);

            // Indexes
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dialogue_created ON " + schemaName + ".dialogue_history(created_at)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_key ON " + schemaName + ".memory(key)");

            logger.info("Created tables for user schema: {}", schemaName);

        } catch (Exception e) {
            logger.error("Error creating tables for user schema {}: {}", schemaName, e.getMessage(), e);
        }
    }

    /**
     * Drop a schema for a user.
     * @param userId the Discord user ID
     */
    public void dropUserSchema(long userId) {
        String schemaName = getUserSchemaName(userId);

        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            logger.info("Dropped schema for user {}: {}", userId, schemaName);
        } catch (Exception e) {
            logger.error("Error dropping schema for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Get the schema name for a user.
     * @param userId the Discord user ID
     * @return the schema name
     */
    public String getUserSchemaName(long userId) {
        return "user_" + userId;
    }

    /**
     * Check if a schema exists for a user.
     * @param userId the Discord user ID
     * @return true if schema exists
     */
    public boolean userSchemaExists(long userId) {
        String schemaName = getUserSchemaName(userId);

        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
            );
            return exists != null && exists > 0;
        } catch (Exception e) {
            logger.error("Error checking schema existence for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    // ===========================================
    // Schema Statistics (for subscription limits)
    // ===========================================

    /**
     * Get the row count for a table in a guild schema.
     * Used for subscription capacity checking.
     */
    public long getGuildTableRowCount(long guildId, String tableName) {
        String schemaName = getGuildSchemaName(guildId);
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + "." + tableName,
                Long.class
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.debug("Error getting row count for {}.{}: {}", schemaName, tableName, e.getMessage());
            return 0;
        }
    }

    /**
     * Get the row count for a table in a user schema.
     * Used for subscription capacity checking.
     */
    public long getUserTableRowCount(long userId, String tableName) {
        String schemaName = getUserSchemaName(userId);
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + "." + tableName,
                Long.class
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.debug("Error getting row count for {}.{}: {}", schemaName, tableName, e.getMessage());
            return 0;
        }
    }
}

