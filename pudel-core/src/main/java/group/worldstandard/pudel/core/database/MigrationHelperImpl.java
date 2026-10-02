/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
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
package group.worldstandard.pudel.core.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginMigration;
import group.worldstandard.pudel.api.database.PluginRepository;

/**
 * Implementation of {@link PluginMigration.MigrationHelper} that provides
 * database schema and data migration operations.
 * <p>
 * This class allows adding, dropping, and modifying columns and tables,
 * managing indexes, and performing data migrations using a repository-based approach.
 */
public class MigrationHelperImpl implements PluginMigration.MigrationHelper {
    private static final Logger logger = LoggerFactory.getLogger(MigrationHelperImpl.class);

    private final PluginDatabaseManagerImpl dbManager;
    private final JdbcTemplate jdbcTemplate;

    public MigrationHelperImpl(PluginDatabaseManagerImpl dbManager, JdbcTemplate jdbcTemplate) {
        this.dbManager = dbManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the database manager instance used for plugin data persistence and schema migrations.
     *
     * @return the plugin database manager
     */
    @Override
    public PluginDatabaseManager getDatabaseManager() {
        return dbManager;
    }

    /**
     * Adds a new column to an existing table in the database.
     * The column is added only if it does not already exist.
     *
     * @param tableName  the name of the table to which the column will be added (without schema prefix)
     * @param columnName the name of the new column
     * @param type       the data type of the new column
     * @param size       the size of the column, applicable for types like STRING and DECIMAL; can be null if not applicable
     * @param nullable   whether the column allows NULL values
     */
    @Override
    public void addColumn(String tableName, String columnName, ColumnType type, Integer size, boolean nullable) {
        addColumn(tableName, columnName, type, size, nullable, null);
    }

    /**
         * Adds a new column to an existing table in the database.
         * The column is added only if it does not already exist.
         *
         * @param tableName   the name of the table to which the column will be added (without schema prefix)
         * @param columnName  the name of the new column
         * @param type        the data type of the new column
         * @param size        the size of the column, applicable for types like STRING and DECIMAL; can be null if not applicable
         * @param nullable    whether the column allows NULL values
         * @param defaultValue the default value for the column, can be null if no default is specified
         */
        @Override
        public void addColumn(String tableName, String columnName, ColumnType type, Integer size,
                             boolean nullable, String defaultValue) {
            String fullTableName = dbManager.getFullTableName(tableName);
            StringBuilder sql = new StringBuilder();
            sql.append("ALTER TABLE ").append(fullTableName);
            sql.append(" ADD COLUMN IF NOT EXISTS ").append(columnName).append(" ");
            sql.append(type.getSqlType(size));

            if (!nullable) {
                sql.append(" NOT NULL");
            }
            if (defaultValue != null) {
                sql.append(" DEFAULT ").append(formatDefaultValue(defaultValue, type));
            }

            jdbcTemplate.execute(sql.toString());
            logger.debug("Added column {} to table {}", columnName, fullTableName);
        }

        /**
         * Formats a default value for SQL based on the column type.
         * String types get single-quoted, other types are used as-is.
         */
        private String formatDefaultValue(String defaultValue, ColumnType type) {
            // Keywords that should not be quoted
            String upper = defaultValue.trim().toUpperCase();
            if (upper.equals("CURRENT_TIMESTAMP") 
                || upper.equals("CURRENT_DATE") 
                || upper.equals("CURRENT_TIME")
                || upper.equals("NOW()")
                || upper.equals("DEFAULT")
                || upper.equals("NULL")
                || upper.equals("TRUE")
                || upper.equals("FALSE")
                || upper.startsWith("NEXTVAL(")
                || upper.startsWith("GEN_RANDOM_UUID()")
                || upper.startsWith("UUID_GENERATE_V4()")) {
                return defaultValue;
            }
        
            // For string/text types, wrap in single quotes and escape existing single quotes
            if (type == ColumnType.STRING || type == ColumnType.TEXT 
                || type == ColumnType.UUID || type == ColumnType.JSON) {
                String escaped = defaultValue.replace("'", "''");
                return "'" + escaped + "'";
            }
        
            // For other types (numeric, boolean, etc.), use as-is
            return defaultValue;
        }

    /**
     * Drops a column from an existing table in the database.
     * The column is dropped only if it exists.
     *
     * @param tableName  the name of the table from which the column will be dropped
     * @param columnName the name of the column to be dropped
     */
    @Override
    public void dropColumn(String tableName, String columnName) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " DROP COLUMN IF EXISTS " + columnName;
        jdbcTemplate.execute(sql);
        logger.debug("Dropped column {} from table {}", columnName, fullTableName);
    }

    /**
     * Renames a column in the specified table.
     *
     * @param tableName the name of the table containing the column to be renamed
     * @param oldName   the current name of the column
     * @param newName   the new name for the column
     */
    @Override
    public void renameColumn(String tableName, String oldName, String newName) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " RENAME COLUMN " + oldName + " TO " + newName;
        jdbcTemplate.execute(sql);
        logger.debug("Renamed column {} to {} in table {}", oldName, newName, fullTableName);
    }

    /**
     * Alters the data type of existing column in the specified table.
     *
     * @param tableName  the name of the table containing the column to modify
     * @param columnName the name of the column whose type is to be changed
     * @param newType    the new data type for the column
     * @param newSize    the size of the new type, applicable for types like STRING and DECIMAL; can be null if not applicable
     */
    @Override
    public void alterColumnType(String tableName, String columnName, ColumnType newType, Integer newSize) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " ALTER COLUMN " + columnName +
                     " TYPE " + newType.getSqlType(newSize);
        jdbcTemplate.execute(sql);
        logger.debug("Changed type of column {} in table {} to {}", columnName, fullTableName, newType);
    }

    /**
     * Creates an index on the specified table for the given columns.
     * If the index already exists, this method does nothing.
     *
     * @param tableName the name of the table on which to create the index (without schema prefix)
     * @param unique    true if the index should enforce uniqueness, false otherwise
     * @param columns   the names of the columns to include in the index
     */
    @Override
    public void createIndex(String tableName, boolean unique, String... columns) {
        String fullTableName = dbManager.getFullTableName(tableName);
        dbManager.createIndexInternal(fullTableName, unique, columns);
    }

    /**
     * Drops an index from the specified table if it exists.
     *
     * @param tableName the name of the table from which the index will be dropped
     * @param columns   the names of the columns that were included in the index
     */
    @Override
    public void dropIndex(String tableName, String... columns) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String indexName = "idx_" + fullTableName.replace(".", "_") + "_" + String.join("_", columns);
        String sql = "DROP INDEX IF EXISTS " + indexName;
        jdbcTemplate.execute(sql);
        logger.debug("Dropped index {} from table {}", indexName, fullTableName);
    }

    /**
     * Renames a database table from its current name to a new name.
     *
     * @param oldName the current name of the table to be renamed (without schema prefix)
     * @param newName the new name for the table (without schema prefix)
     */
    @Override
    public void renameTable(String oldName, String newName) {
        String fullOldName = dbManager.getFullTableName(oldName);
        String fullNewName = dbManager.getFullTableName(newName);
        String sql = "ALTER TABLE " + fullOldName + " RENAME TO " + newName;
        jdbcTemplate.execute(sql);
        logger.debug("Renamed table {} to {}", fullOldName, fullNewName);
    }

    /**
     * Migrates data in the specified table by applying a transformation function to each entity.
     * Entities returned as null by the migrator are deleted from the table,
     * while non-null results are saved back to the table.
     *
     * @param tableName    the name of the database table to migrate
     * @param entityClass  the class type of the entities stored in the table
     * @param migrator     the function used to transform each entity during migration
     */
    @Override
    public <T> void migrateData(String tableName, Class<T> entityClass, PluginMigration.DataMigrator<T> migrator) {
        PluginRepository<T> repo = dbManager.getRepository(tableName, entityClass);

        for (T entity : repo.findAll()) {
            T migrated = migrator.migrate(entity);
            if (migrated == null) {
                repo.delete(entity);
            } else {
                repo.save(migrated);
            }
        }

        logger.debug("Migrated data in table {}", tableName);
    }
}