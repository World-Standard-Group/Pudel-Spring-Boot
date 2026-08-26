/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.database;

import java.util.List;

/**
 * Database manager for plugin data persistence.
 * <p>
 * Each plugin gets its own isolated database schema for data isolation.
 * Plugins interact with the database through a JPA-like repository pattern -
 * no raw SQL is allowed.
 * <p>
 * Example usage:
 * <pre>
 * {@code @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
 * public class MyPlugin {
 *     private PluginRepository<MyEntity> repository;
 *
 *     @OnEnable
 *     public void onEnable(PluginContext context) {
 *         PluginDatabaseManager db = context.getDatabaseManager();
 *
 *         // Define your table schema
 *         TableSchema schema = TableSchema.builder("my_data")
 *             .column("name", ColumnType.STRING, 255, false)
 *             .column("count", ColumnType.INTEGER, false)
 *             .column("active", ColumnType.BOOLEAN, false)
 *             .column("data", ColumnType.TEXT, true)  // nullable
 *             .build();
 *
 *         // Create the table (idempotent - safe to call every startup)
 *         db.createTable(schema);
 *
 *         // Get a repository for CRUD operations
 *         repository = db.getRepository("my_data", MyEntity.class);
 *     }
 * }
 * }
 * </pre>
 */
public interface PluginDatabaseManager {
    /**
     * Get the database schema name assigned to this plugin.
     * <p>
     * All tables created by this plugin will be created in this schema.
     * Format: "plugin_{pluginId}" (e.g., "plugin_abcd1234")
     *
     * @return the plugin's database schema name
     */
    String getSchemaName();

    /**
     * Get the plugin ID this manager belongs to.
     *
     * @return the plugin ID
     */
    String getPluginId();

    /**
     * Create a table for this plugin.
     * <p>
     * The table name will be automatically prefixed with the plugin's prefix.
     * This operation is idempotent - calling it multiple times is safe.
     *
     * @param schema the table schema definition
     * @return true if created, false if already exists
     */
    boolean createTable(TableSchema schema);

    /**
     * Check if a table exists.
     *
     * @param tableName the table name (without prefix)
     * @return true if exists
     */
    boolean tableExists(String tableName);

    /**
     * Drop a table.
     * <p>
     * <b>Warning:</b> This permanently deletes all data in the table.
     *
     * @param tableName the table name (without prefix)
     * @return true if dropped, false if didn't exist
     */
    boolean dropTable(String tableName);

    /**
     * Get a repository for CRUD operations on a table.
     *
     * @param tableName the table name (without prefix)
     * @param entityClass the entity class for mapping
     * @param <T> the entity type
     * @return a repository instance
     */
    <T> PluginRepository<T> getRepository(String tableName, Class<T> entityClass);

    /**
     * Get a simple key-value store for this plugin.
     * <p>
     * Useful for storing configuration or simple data without defining schemas.
     *
     * @return the key-value store
     */
    PluginKeyValueStore getKeyValueStore();

    /**
     * List all tables owned by this plugin.
     *
     * @return list of table names (without prefix)
     */
    List<String> listTables();

    /**
     * Get the current schema version for this plugin.
     * <p>
     * Used for migration management.
     *
     * @return current schema version, or 0 if not set
     */
    int getSchemaVersion();

    /**
     * Set the schema version for this plugin.
     * <p>
     * Call this after successfully applying migrations.
     *
     * @param version the new schema version
     */
    void setSchemaVersion(int version);

    /**
     * Execute a migration if needed.
     * <p>
     * The migration will only run if the current schema version is less than
     * the target version. After successful migration, the schema version is updated.
     *
     * @param targetVersion the version this migration upgrades to
     * @param migration the migration to execute
     * @return true if migration was executed, false if already at or past target version
     */
    boolean migrate(int targetVersion, PluginMigration migration);

    /**
     * Get database statistics for this plugin.
     *
     * @return database stats
     */
    DatabaseStats getStats();

    /**
     * Database statistics.
     *
     * @param pluginId      the identifier of the plugin the statistics belong to
     * @param schemaName    the name of the database schema
     * @param tableCount    the number of tables in the schema
     * @param totalRows     the total number of rows across all tables
     * @param schemaVersion the version of the schema definition
     */
    record DatabaseStats(
            String pluginId,
            String schemaName,
            int tableCount,
            long totalRows,
            int schemaVersion
    ) {}
}