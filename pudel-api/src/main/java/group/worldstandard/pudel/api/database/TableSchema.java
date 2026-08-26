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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Schema definition for a plugin database table.
 * <p>
 * All tables automatically include an 'id' column (BIGSERIAL PRIMARY KEY)
 * and 'created_at', 'updated_at' timestamp columns.
 * <p>
 * Example:
 * <pre>
 * {@code
 * TableSchema schema = TableSchema.builder("user_settings")
 *     .column("user_id", ColumnType.BIGINT, false)
 *     .column("setting_name", ColumnType.STRING, 100, false)
 *     .column("setting_value", ColumnType.TEXT, true)
 *     .column("enabled", ColumnType.BOOLEAN, false, "true")
 *     .index("user_id")
 *     .uniqueIndex("user_id", "setting_name")
 *     .build();
 * }
 * </pre>
 */
public final class TableSchema {
    private final String tableName;
    private final List<ColumnDefinition> columns;
    private final List<IndexDefinition> indexes;

    private TableSchema(Builder builder) {
        this.tableName = builder.tableName;
        this.columns = Collections.unmodifiableList(new ArrayList<>(builder.columns));
        this.indexes = Collections.unmodifiableList(new ArrayList<>(builder.indexes));
    }

    /**
     * Returns the name of the database table associated with this schema.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the list of column definitions associated with this table schema.
     *
     * @return a list of {@link ColumnDefinition} objects representing the columns of the table
     */
    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    /**
     * Returns the list of index definitions associated with this table schema.
     *
     * @return a list of {@link IndexDefinition} objects representing the indexes of the table
     */
    public List<IndexDefinition> getIndexes() {
        return indexes;
    }

    /**
     * Create a new schema builder.
     *
     * @param tableName the table name (will be prefixed automatically)
     * @return a new builder
     */
    public static Builder builder(String tableName) {
        return new Builder(tableName);
    }

    /**
     * Builder class for constructing {@link TableSchema} instances.
     * Provides a fluent API for defining table structure including columns and indexes.
     * Validates table and column names according to naming conventions.
     * Table names must start with a lowercase letter and contain only lowercase letters, numbers, and underscores.
     * Column names follow the same rules but cannot be reserved words like 'id', 'created_at', or 'updated_at'.
     */
    public static class Builder {
        private final String tableName;
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final List<IndexDefinition> indexes = new ArrayList<>();

        private Builder(String tableName) {
            this.tableName = validateTableName(tableName);
        }

        /**
         * Add a column to the schema.
         *
         * @param name column name
         * @param type column type
         * @param nullable whether the column can be null
         * @return this builder
         */
        public Builder column(String name, ColumnType type, boolean nullable) {
            return column(name, type, null, nullable, null);
        }

        /**
         * Add a column with a size constraint.
         *
         * @param name column name
         * @param type column type
         * @param size size/length for VARCHAR, etc.
         * @param nullable whether the column can be null
         * @return this builder
         */
        public Builder column(String name, ColumnType type, Integer size, boolean nullable) {
            return column(name, type, size, nullable, null);
        }

        /**
         * Add a column with a default value.
         *
         * @param name column name
         * @param type column type
         * @param nullable whether the column can be null
         * @param defaultValue SQL default value expression
         * @return this builder
         */
        public Builder column(String name, ColumnType type, boolean nullable, String defaultValue) {
            return column(name, type, null, nullable, defaultValue);
        }

        /**
         * Add a column with all options.
         *
         * @param name column name
         * @param type column type
         * @param size size/length (nullable)
         * @param nullable whether the column can be null
         * @param defaultValue SQL default value expression (nullable)
         * @return this builder
         */
        public Builder column(String name, ColumnType type, Integer size, boolean nullable, String defaultValue) {
            columns.add(new ColumnDefinition(
                    validateColumnName(name),
                    type,
                    size,
                    nullable,
                    defaultValue
            ));
            return this;
        }

        /**
         * Add an index on one or more columns.
         *
         * @param columnNames the columns to index
         * @return this builder
         */
        public Builder index(String... columnNames) {
            indexes.add(new IndexDefinition(false, List.of(columnNames)));
            return this;
        }

        /**
         * Add a unique index on one or more columns.
         *
         * @param columnNames the columns for the unique constraint
         * @return this builder
         */
        public Builder uniqueIndex(String... columnNames) {
            indexes.add(new IndexDefinition(true, List.of(columnNames)));
            return this;
        }

        public TableSchema build() {
            return new TableSchema(this);
        }

        private static String validateTableName(String name) {
            Objects.requireNonNull(name, "Table name cannot be null");
            if (!name.matches("^[a-z][a-z0-9_]*$")) {
                throw new IllegalArgumentException(
                        "Table name must start with lowercase letter and contain only lowercase letters, numbers, and underscores: " + name);
            }
            if (name.length() > 50) {
                throw new IllegalArgumentException("Table name too long (max 50 chars): " + name);
            }
            return name;
        }

        private static String validateColumnName(String name) {
            Objects.requireNonNull(name, "Column name cannot be null");
            if (!name.matches("^[a-z][a-z0-9_]*$")) {
                throw new IllegalArgumentException(
                        "Column name must start with lowercase letter and contain only lowercase letters, numbers, and underscores: " + name);
            }
            // Reserved column names
            if (name.equals("id") || name.equals("created_at") || name.equals("updated_at")) {
                throw new IllegalArgumentException("Column name '" + name + "' is reserved");
            }
            return name;
        }
    }

    /**
     * Represents the definition of a database column within a table schema.
     * Contains metadata about the column including its name, data type, size,
     * nullability constraint, and default value.
     * <p>
     * This record is used internally by the schema builder to construct
     * database table definitions. Column definitions are immutable once created.
     * <p>
     * The column type determines how the column is represented in SQL,
     * with optional size parameters for variable-length types like VARCHAR
     * or NUMERIC. Nullability controls whether the column can contain NULL values.
     * Default values are specified as strings and should be compatible
     * with the column's data type.
     *
     * @param name         the column name
     * @param type         the column's data type
     * @param size         the optional maximum size for variable-length types (e.g. VARCHAR/NUMERIC)
     * @param nullable     whether the column allows NULL values
     * @param defaultValue the default value as a string, compatible with the column type (may be null)
     */
    public record ColumnDefinition(
            String name,
            ColumnType type,
            Integer size,
            boolean nullable,
            String defaultValue
    ) {}

    /**
     * Represents the definition of an index in a database table schema.
     * <p>
     * An index definition specifies whether the index enforces uniqueness
     * and which columns are included in the index
     *
     * @param unique  whether the index enforces uniqueness (no duplicate values)
     * @param columns the ordered list of column names included in the index
     */
    public record IndexDefinition(
            boolean unique,
            List<String> columns
    ) {}
}
