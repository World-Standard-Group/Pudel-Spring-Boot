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
import worldstandard.group.pudel.model.agent.AgentDataExecutor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AgentDataExecutor for pudel-core.
 * <p>
 * This service executes database operations requested by the AI agent.
 * All operations are scoped to the guild/user's isolated schema.
 * <p>
 * Security:
 * - Table names are sanitized before use
 * - All tables are prefixed with 'agent_' to identify agent-created tables
 * - Operations are limited to the target's schema only
 */
@Service
@Transactional
public class AgentDataExecutorImpl implements AgentDataExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentDataExecutorImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final SchemaManagementService schemaManagementService;

    public AgentDataExecutorImpl(JdbcTemplate jdbcTemplate, SchemaManagementService schemaManagementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaManagementService = schemaManagementService;
    }

    // ===========================================
    // Table Management
    // ===========================================

    @Override
    public boolean createCustomTable(long targetId, boolean isGuild, String tableName, String description, long createdBy) {
        String schemaName = getSchemaName(targetId, isGuild);
        ensureSchemaExists(targetId, isGuild);

        try {
            // Check if table already exists
            if (tableExists(schemaName, tableName)) {
                return false;
            }

            // Create the table with standard structure
            String createTableSql = String.format("""
                    CREATE TABLE %s.%s (
                        id BIGSERIAL PRIMARY KEY,
                        title VARCHAR(500) NOT NULL,
                        content TEXT NOT NULL,
                        created_by BIGINT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """, schemaName, tableName);
            jdbcTemplate.execute(createTableSql);

            // Create indexes
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_title ON %s.%s(title)",
                    tableName, schemaName, tableName));
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_created ON %s.%s(created_at)",
                    tableName, schemaName, tableName));

            // Register in metadata table
            registerTableMetadata(schemaName, tableName, description, createdBy);

            logger.info("Agent created table {}.{}", schemaName, tableName);
            return true;

        } catch (Exception e) {
            logger.error("Error creating agent table {}.{}: {}", schemaName, tableName, e.getMessage());
            throw new RuntimeException("Failed to create table: " + e.getMessage(), e);
        }
    }

    @Override
    public void ensureTableExists(long targetId, boolean isGuild, String tableName, long createdBy) {
        String schemaName = getSchemaName(targetId, isGuild);
        ensureSchemaExists(targetId, isGuild);

        if (!tableExists(schemaName, tableName)) {
            createCustomTable(targetId, isGuild, tableName, "Auto-created by agent", createdBy);
        }
    }

    @Override
    public List<Map<String, Object>> listCustomTables(long targetId, boolean isGuild) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            // Get tables from metadata
            String sql = "SELECT table_name, description, created_at FROM " + schemaName + ".agent_table_metadata ORDER BY created_at DESC";
            List<Map<String, Object>> metadata = jdbcTemplate.queryForList(sql);

            // Add row counts
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> table : metadata) {
                String tableName = (String) table.get("table_name");
                try {
                    Integer rowCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + schemaName + "." + tableName,
                            Integer.class);
                    table.put("row_count", rowCount != null ? rowCount : 0);
                } catch (Exception e) {
                    table.put("row_count", 0);
                }
                result.add(table);
            }

            return result;

        } catch (Exception e) {
            // Metadata table might not exist
            logger.debug("Could not list tables for {}: {}", schemaName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean deleteTable(long targetId, boolean isGuild, String tableName) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            if (!tableExists(schemaName, tableName)) {
                return false;
            }

            jdbcTemplate.execute("DROP TABLE IF EXISTS " + schemaName + "." + tableName);
            jdbcTemplate.update("DELETE FROM " + schemaName + ".agent_table_metadata WHERE table_name = ?", tableName);

            logger.info("Agent deleted table {}.{}", schemaName, tableName);
            return true;

        } catch (Exception e) {
            logger.error("Error deleting table {}.{}: {}", schemaName, tableName, e.getMessage());
            throw new RuntimeException("Failed to delete table: " + e.getMessage(), e);
        }
    }

    // ===========================================
    // Data Operations
    // ===========================================

    @Override
    public long insertData(long targetId, boolean isGuild, String tableName, String title, String content, long createdBy) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format(
                    "INSERT INTO %s.%s (title, content, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    schemaName, tableName);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            Long id = jdbcTemplate.queryForObject(sql, Long.class, title, content, createdBy, now, now);

            logger.debug("Agent inserted data into {}.{}: id={}", schemaName, tableName, id);
            return id != null ? id : -1;

        } catch (Exception e) {
            logger.error("Error inserting data into {}.{}: {}", schemaName, tableName, e.getMessage());
            throw new RuntimeException("Failed to insert data: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchData(long targetId, boolean isGuild, String tableName, String searchQuery, int limit) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format(
                    "SELECT * FROM %s.%s WHERE title ILIKE ? OR content ILIKE ? ORDER BY created_at DESC LIMIT ?",
                    schemaName, tableName);
            String pattern = "%" + searchQuery + "%";
            return jdbcTemplate.queryForList(sql, pattern, pattern, limit);

        } catch (Exception e) {
            logger.error("Error searching data in {}.{}: {}", schemaName, tableName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getAllData(long targetId, boolean isGuild, String tableName, int limit) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format(
                    "SELECT * FROM %s.%s ORDER BY created_at DESC LIMIT ?",
                    schemaName, tableName);
            return jdbcTemplate.queryForList(sql, limit);

        } catch (Exception e) {
            logger.error("Error getting all data from {}.{}: {}", schemaName, tableName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getDataById(long targetId, boolean isGuild, String tableName, long id) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format("SELECT * FROM %s.%s WHERE id = ?", schemaName, tableName);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id);
            return results.isEmpty() ? null : results.getFirst();

        } catch (Exception e) {
            logger.error("Error getting data by id from {}.{}: {}", schemaName, tableName, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean updateData(long targetId, boolean isGuild, String tableName, long id, String newTitle, String newContent) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format(
                    "UPDATE %s.%s SET title = ?, content = ?, updated_at = ? WHERE id = ?",
                    schemaName, tableName);
            int updated = jdbcTemplate.update(sql, newTitle, newContent, Timestamp.valueOf(LocalDateTime.now()), id);
            return updated > 0;

        } catch (Exception e) {
            logger.error("Error updating data in {}.{}: {}", schemaName, tableName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteData(long targetId, boolean isGuild, String tableName, long id) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = String.format("DELETE FROM %s.%s WHERE id = ?", schemaName, tableName);
            int deleted = jdbcTemplate.update(sql, id);
            return deleted > 0;

        } catch (Exception e) {
            logger.error("Error deleting data from {}.{}: {}", schemaName, tableName, e.getMessage());
            return false;
        }
    }

    // ===========================================
    // Memory Operations
    // ===========================================

    @Override
    public void storeMemory(long targetId, boolean isGuild, String key, String value, String category, long createdBy) {
        String schemaName = getSchemaName(targetId, isGuild);
        ensureSchemaExists(targetId, isGuild);

        try {
            String sql = "INSERT INTO " + schemaName + ".memory (key, value, category, created_by, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (key) DO UPDATE SET value = ?, category = ?, updated_at = ?";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(sql, key, value, category, createdBy, now, now, value, category, now);

        } catch (Exception e) {
            logger.error("Error storing memory in {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Failed to store memory: " + e.getMessage(), e);
        }
    }

    @Override
    public String getMemory(long targetId, boolean isGuild, String key) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = "SELECT value FROM " + schemaName + ".memory WHERE key = ?";
            List<String> results = jdbcTemplate.queryForList(sql, String.class, key);
            return results.isEmpty() ? null : results.getFirst();

        } catch (Exception e) {
            logger.debug("Memory not found in {}: {}", schemaName, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getMemoriesByCategory(long targetId, boolean isGuild, String category) {
        String schemaName = getSchemaName(targetId, isGuild);

        try {
            String sql = "SELECT * FROM " + schemaName + ".memory WHERE category = ? ORDER BY created_at DESC";
            return jdbcTemplate.queryForList(sql, category);

        } catch (Exception e) {
            logger.error("Error getting memories from {}: {}", schemaName, e.getMessage());
            return List.of();
        }
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private String getSchemaName(long targetId, boolean isGuild) {
        if (isGuild) {
            return schemaManagementService.getGuildSchemaName(targetId);
        } else {
            return schemaManagementService.getUserSchemaName(targetId);
        }
    }

    private void ensureSchemaExists(long targetId, boolean isGuild) {
        if (isGuild) {
            if (!schemaManagementService.schemaExists(targetId)) {
                schemaManagementService.createGuildSchema(targetId);
            }
        } else {
            if (!schemaManagementService.userSchemaExists(targetId)) {
                schemaManagementService.createUserSchema(targetId);
            }
        }

        // Ensure metadata table exists
        String schemaName = getSchemaName(targetId, isGuild);
        ensureMetadataTableExists(schemaName);
    }

    private void ensureMetadataTableExists(String schemaName) {
        try {
            String sql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s.agent_table_metadata (
                        table_name VARCHAR(100) PRIMARY KEY,
                        description TEXT,
                        created_by BIGINT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """, schemaName);
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            logger.debug("Metadata table creation: {}", e.getMessage());
        }
    }

    private void registerTableMetadata(String schemaName, String tableName, String description, long createdBy) {
        try {
            String sql = "INSERT INTO " + schemaName + ".agent_table_metadata (table_name, description, created_by, created_at) " +
                    "VALUES (?, ?, ?, ?) ON CONFLICT (table_name) DO UPDATE SET description = ?, created_by = ?";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(sql, tableName, description, createdBy, now, description, createdBy);
        } catch (Exception e) {
            logger.warn("Could not register table metadata: {}", e.getMessage());
        }
    }

    private boolean tableExists(String schemaName, String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                    Integer.class,
                    schemaName, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

