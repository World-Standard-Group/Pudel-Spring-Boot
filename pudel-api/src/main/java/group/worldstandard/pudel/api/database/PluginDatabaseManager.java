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
 *         // Option 1: Automatic migration from entity classes (recommended)
 *         db.autoMigrate(UserSetting.class, GuildConfig.class);
 *
 *         // Option 2: Create/update individual tables
 *         db.createOrUpdateTable(UserSetting.class);
 *
 *         // Option 3: Manual schema definition (legacy)
 *         TableSchema schema = TableSchema.builder("my_data")
 *             .column("name", ColumnType.STRING, 255, false)
 *             .column("count", ColumnType.INTEGER, false)
 *             .column("active", ColumnType.BOOLEAN, false)
 *             .column("data", ColumnType.TEXT, true)
 *             .build();
 *         db.createTable(schema);
 *
 *         // Get a repository for CRUD operations.
 *         // Prefer the single-arg form when MyEntity is annotated with @Entity
 *         // (with or without an explicit tableName) — the table name is then
 *         // derived from the annotation, so there is no risk of the table name
 *         // drifting between the entity definition and the getRepository call.
 *         repository = db.getRepository(MyEntity.class);
 *         // For legacy/ad-hoc tables without an @Entity class, fall back to the
 *         // explicit (String, Class) overload.
 *         // repository = db.getRepository("my_data", MyEntity.class);
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
     * <p>
     * Prefer {@link #getRepository(Class)} when the entity class is annotated
     * with {@code @Entity} — it derives the table name from
     * {@code @Entity(tableName = "...")} (or the class name) so the caller
     * does not have to keep two copies of the table name in sync. Use this
     * (String, Class) overload only when the entity is NOT annotated with
     * {@code @Entity} (e.g. legacy code, ad-hoc POJOs, or tables whose name
     * cannot be expressed via the annotation).
     *
     * @param tableName the table name (without prefix)
     * @param entityClass the entity class for mapping
     * @param <T> the entity type
     * @return a repository instance
     */
    <T> PluginRepository<T> getRepository(String tableName, Class<T> entityClass);

    /**
     * Get a repository for CRUD operations on a table, deriving the table name
     * from the entity's {@code @Entity} annotation.
     * <p>
     * The table name is resolved in this order:
     * <ol>
     *   <li>An explicit {@code @Entity(tableName = "...")} attribute on the class.</li>
     *   <li>Otherwise the class name converted from PascalCase to snake_case
     *       (with a trailing {@code Entity} suffix stripped).</li>
     * </ol>
     * This is the same resolution order used by {@link #autoMigrate(Class[])} and
     * {@link #createOrUpdateTable(Class)}, so an entity mapped via
     * {@code @Entity(tableName = "user_settings")} and a manual migration script
     * that creates {@code user_settings} all line up to the same physical table.
     * <p>
     * Example:
     * <pre>
     * {@code
     * @Entity(tableName = "user_settings")
     * public class UserSettingEntity { ... }
     *
     * // No need to repeat the table name here — it's read from the annotation.
     * PluginRepository<UserSettingEntity> repo = db.getRepository(UserSettingEntity.class);
     * }
     * </pre>
     *
     * @param entityClass the entity class annotated with {@code @Entity}
     * @param <T> the entity type
     * @return a repository instance for the resolved table
     * @throws IllegalArgumentException if {@code entityClass} is not annotated with
     *     {@code @Entity}, or if {@code @Entity(tableName = "...")} is set but
     *     violates the table-name naming rules
     */
    <T> PluginRepository<T> getRepository(Class<T> entityClass);

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
     * Automatically migrate tables to match the given entity classes.
     * <p>
     * This method compares the current database schema with the schema derived
     * from the entity classes and applies necessary changes (add columns, create indexes, etc.).
     * It does not drop columns or tables - only additive changes for safety.
     * <p>
     * The target table name for each entity is resolved in this order:
     * <ol>
     *   <li>An explicit {@code @Entity(tableName = "...")} attribute on the class.</li>
     *   <li>Otherwise the class name converted from PascalCase to snake_case
     *       (with a trailing {@code Entity} suffix stripped).</li>
     * </ol>
     * Use the explicit form whenever the table is also managed by a manual
     * migration script so the auto-migrator and the manual script target the
     * same physical table.
     * <p>
     * Example:
     * <pre>
     * {@code
     * dbManager.autoMigrate(UserSetting.class, GuildConfig.class);
     * }
     * </pre>
     *
     * @param entityClasses the entity classes to migrate to
     * @return true if any migrations were applied, false if already up to date
     */
    boolean autoMigrate(Class<?>... entityClasses);

    /**
     * Create or update a table from an entity class, using an explicit table name.
     * <p>
     * This is the legacy-smooth-migration overload: callers who used to do
     * {@code db.createTable(TableSchema.builder("user_settings")...)} can swap to
     * {@code db.createOrUpdateTable("user_settings", UserSettingEntity.class)} without
     * first having to annotate their POJO with
     * {@code @Entity(tableName = "user_settings")}. Once the migration is complete,
     * they can drop the explicit name and switch to the single-arg
     * {@link #createOrUpdateTable(Class)} form.
     * <p>
     * The explicit {@code tableName} overrides any
     * {@code @Entity(tableName = "...")} attribute on the class — the value here
     * is what the table will be named in the database, and the annotation is
     * ignored for this call. The name is validated against the same naming rules
     * as {@code TableSchema.Builder} (lowercase, starts with a letter, max 50 chars),
     * so a bad value fails fast with a clear message at the call site rather than
     * producing a confusing SQL syntax error later.
     * <p>
     * Example migration path:
     * <pre>
     * {@code
     * // Before — manual schema definition:
     * TableSchema schema = TableSchema.builder("user_settings")
     *     .column("user_id", ColumnType.BIGINT, false)
     *     .build();
     * db.createTable(schema);
     *
     * // After — entity-driven, but still naming the table explicitly so a manual
     * // migration script and the auto-migrator agree on the target table:
     * db.createOrUpdateTable("user_settings", UserSettingEntity.class);
     *
     * // Later — once UserSettingEntity carries @Entity(tableName = "user_settings"),
     * // drop the explicit name and use the single-arg form:
     * // db.createOrUpdateTable(UserSettingEntity.class);
     * }
     * </pre>
     *
     * @param tableName the explicit database table name to use (overrides
     *     {@code @Entity(tableName = "...")} for this call)
     * @param entityClass the entity class annotated with {@code @Entity}
     * @param <T> the entity type
     * @return true if the table was created or modified
     * @throws IllegalArgumentException if {@code entityClass} is not annotated with
     *     {@code @Entity}, or if {@code tableName} violates the table-name naming rules
     */
    <T> boolean createOrUpdateTable(String tableName, Class<T> entityClass);

    /**
     * Create or update a table from an entity class.
     * <p>
     * This is a convenience method that creates the table if it doesn't exist,
     * or adds missing columns/indexes if it does exist.
     * <p>
     * The table name is resolved from {@code @Entity(tableName = "...")} when
     * present, otherwise from the class name (PascalCase → snake_case).
     * <p>
     * If you have an entity class that is not yet annotated with
     * {@code @Entity(tableName = "...")} and you want to start using the
     * entity-driven migration path without changing the POJO first, use the
     * {@link #createOrUpdateTable(String, Class) (String, Class) overload} — it
     * takes the table name explicitly and overrides whatever the annotation says
     * for that call. Once the POJO carries the annotation, you can drop the
     * explicit name and switch to this single-arg form.
     *
     * @param entityClass the entity class annotated with @Entity
     * @return true if table was created or modified, false if already up to date
     */
    <T> boolean createOrUpdateTable(Class<T> entityClass);

/**
     * Get the current table schema for a table (for comparison/debugging).
     *
     * @param tableName the table name (without prefix)
     * @return current table schema, or null if table doesn't exist
     */
    TableSchema getTableSchema(String tableName);

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