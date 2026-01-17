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
package worldstandard.group.pudel.model.agent;

import java.util.List;
import java.util.Map;

/**
 * Interface for executing agent data operations.
 * <p>
 * This abstraction allows pudel-model to define tools without direct database access.
 * The actual implementation is in pudel-core where JdbcTemplate is available.
 */
public interface AgentDataExecutor {

    // ===========================================
    // Table Management
    // ===========================================

    /**
     * Create a custom table for storing agent-managed data.
     *
     * @param targetId Guild or User ID
     * @param isGuild Whether this is a guild (true) or user (false) schema
     * @param tableName Sanitized table name (e.g., "agent_news_documents")
     * @param description Human-readable description of what this table stores
     * @param createdBy User ID who requested the creation
     * @return true if created, false if already exists
     */
    boolean createCustomTable(long targetId, boolean isGuild, String tableName, String description, long createdBy);

    /**
     * Ensure a table exists, create it if not.
     */
    void ensureTableExists(long targetId, boolean isGuild, String tableName, long createdBy);

    /**
     * List all custom tables created by the agent in a schema.
     *
     * @return List of table info including name, description, row_count
     */
    List<Map<String, Object>> listCustomTables(long targetId, boolean isGuild);

    /**
     * Delete a custom table and all its data.
     */
    boolean deleteTable(long targetId, boolean isGuild, String tableName);

    // ===========================================
    // Data Operations
    // ===========================================

    /**
     * Insert data into a custom table.
     *
     * @param targetId Guild or User ID
     * @param isGuild Whether this is a guild schema
     * @param tableName The table to insert into
     * @param title Title/subject of the data
     * @param content The actual content/data
     * @param createdBy User ID who added this data
     * @return The generated ID of the new row
     */
    long insertData(long targetId, boolean isGuild, String tableName, String title, String content, long createdBy);

    /**
     * Search for data in a custom table.
     *
     * @param searchQuery Text to search for in title and content
     * @param limit Maximum results to return
     */
    List<Map<String, Object>> searchData(long targetId, boolean isGuild, String tableName, String searchQuery, int limit);

    /**
     * Get all data from a table.
     */
    List<Map<String, Object>> getAllData(long targetId, boolean isGuild, String tableName, int limit);

    /**
     * Get a specific entry by ID.
     */
    Map<String, Object> getDataById(long targetId, boolean isGuild, String tableName, long id);

    /**
     * Update an existing entry.
     */
    boolean updateData(long targetId, boolean isGuild, String tableName, long id, String newTitle, String newContent);

    /**
     * Delete an entry by ID.
     */
    boolean deleteData(long targetId, boolean isGuild, String tableName, long id);

    // ===========================================
    // Memory Operations (for quick key-value storage)
    // ===========================================

    /**
     * Store a memory (key-value pair).
     */
    void storeMemory(long targetId, boolean isGuild, String key, String value, String category, long createdBy);

    /**
     * Get a memory by key.
     */
    String getMemory(long targetId, boolean isGuild, String key);

    /**
     * Get all memories by category.
     */
    List<Map<String, Object>> getMemoriesByCategory(long targetId, boolean isGuild, String category);
}

