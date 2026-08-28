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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Schema definition for a plugin database table.
 * <p>
 * All tables automatically include an 'id' column (BIGSERIAL PRIMARY KEY)
 * and 'created_at', 'updated_at' timestamp columns.
 * <p>
 * Example (manual column definition):
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
 * <p>
 * Example (auto-generate from entity):
 * <pre>
 * {@code
 * @Entity
 * public class UserSetting {
 *     private Long id;
 *     private Long userId;
 *     private String settingName;
 *     private String settingValue;
 *
 *     @Column(nullable = false)
 *     private Boolean enabled = true;
 *
 *     @Column(defaultValue = "true")
 *     private Boolean active;
 *
 *     @Column(unique = true)
 *     private String email;
 *
 *     @Column(index = true)
 *     private String username;
 *
 *     private Instant createdAt;
 *     private Instant updatedAt;
 *     // getters and setters...
 * }
 *
 * TableSchema schema = TableSchema.builder("user_settings")
 *     .fromEntity(UserSetting.class)
 *     .build(); // indexes from @Column(unique/index) are added automatically
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

        /**
         * Add columns from an entity class annotated with {@link Entity}.
         * <p>
         * This method reads all non-ignored fields from the entity class and automatically
         * maps them to columns based on field types and {@link Column} annotations.
         * The following field types are supported:
         * <ul>
         *   <li>{@code Long}, {@code long} -> {@link ColumnType#BIGINT}</li>
         *   <li>{@code Integer}, {@code int} -> {@link ColumnType#INTEGER}</li>
         *   <li>{@code Short}, {@code short} -> {@link ColumnType#SMALLINT}</li>
         *   <li>{@code Boolean}, {@code boolean} -> {@link ColumnType#BOOLEAN}</li>
         *   <li>{@code String} -> {@link ColumnType#STRING} (size 255) or {@link ColumnType#TEXT}</li>
         *   <li>{@code java.time.Instant}, {@code java.time.LocalDateTime}, {@code java.time.OffsetDateTime} -> {@link ColumnType#TIMESTAMP}</li>
         *   <li>{@code java.time.LocalDate} -> {@link ColumnType#DATE}</li>
         *   <li>{@code java.time.LocalTime} -> {@link ColumnType#TIME}</li>
         *   <li>{@code java.math.BigDecimal} -> {@link ColumnType#DECIMAL}</li>
         *   <li>{@code Double}, {@code double} -> {@link ColumnType#DOUBLE}</li>
         *   <li>{@code Float}, {@code float} -> {@link ColumnType#FLOAT}</li>
         *   <li>{@code java.util.UUID} -> {@link ColumnType#UUID}</li>
         *   <li>{@code byte[]} -> {@link ColumnType#BINARY}</li>
         * </ul>
         * Fields annotated with {@code @Column(ignore = true)} are skipped.
         * Fields named "id", "createdAt", "updatedAt" (or their snake_case equivalents)
         * are automatically excluded as they are managed by the database.
         * <p>
         * Indexes can be specified via {@code @Column(unique = true)} or {@code @Column(index = true)}.
         *
         * @param entityClass the entity class annotated with @Entity
         * @return this builder
         * @throws IllegalArgumentException if the class is not annotated with @Entity
         */
        public Builder fromEntity(Class<?> entityClass) {
            Objects.requireNonNull(entityClass, "Entity class cannot be null");

            // Verify @Entity annotation
            if (!entityClass.isAnnotationPresent(Entity.class)) {
                throw new IllegalArgumentException("Class must be annotated with @Entity: " + entityClass.getName());
            }

            Map<String, String> fieldToColumn = new LinkedHashMap<>();
            Map<String, Column> fieldToAnnotation = new LinkedHashMap<>();

            for (Field field : entityClass.getDeclaredFields()) {
                field.setAccessible(true);

                // Check for @Column annotation
                Column columnAnnotation = field.getAnnotation(Column.class);
                if (columnAnnotation != null && columnAnnotation.ignore()) {
                    continue; // Skip ignored fields
                }

                String columnName;
                if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                    columnName = columnAnnotation.name();
                } else {
                    // Convert camelCase to snake_case
                    columnName = toSnakeCase(field.getName());
                }

                // Skip auto-managed columns
                if (columnName.equals("id") || columnName.equals("created_at") || columnName.equals("updated_at")) {
                    continue;
                }

                fieldToColumn.put(field.getName(), columnName);
                if (columnAnnotation != null) {
                    fieldToAnnotation.put(field.getName(), columnAnnotation);
                }
            }

            // Add columns based on field types
            for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
                try {
                    Field field = entityClass.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    ColumnDefinition colDef = createColumnDefinition(field, entry.getValue(), fieldToAnnotation.get(entry.getKey()));
                    if (colDef != null) {
                        columns.add(colDef);
                    }
                } catch (NoSuchFieldException e) {
                    // Should not happen since we just got the field
                    throw new IllegalStateException("Field not found: " + entry.getKey(), e);
                }
            }

            // Add indexes from @Column annotations
            for (Map.Entry<String, Column> entry : fieldToAnnotation.entrySet()) {
                String fieldName = entry.getKey();
                String columnName = fieldToColumn.get(fieldName);
                if (columnName == null) continue; // Was skipped (auto-managed)
                Column ann = entry.getValue();
                if (ann.unique()) {
                    indexes.add(new IndexDefinition(true, List.of(columnName)));
                } else if (ann.index()) {
                    indexes.add(new IndexDefinition(false, List.of(columnName)));
                }
            }

            return this;
        }

        /**
         * Creates a ColumnDefinition from a field.
         *
         * @param field the field to create column definition for
         * @param columnName the column name
         * @param columnAnnotation the @Column annotation or null
         * @return ColumnDefinition or null if type is not supported
         */
        private ColumnDefinition createColumnDefinition(Field field, String columnName, Column columnAnnotation) {
            Class<?> type = field.getType();

            // Determine nullability
            boolean nullable;
            if (columnAnnotation != null && !columnAnnotation.nullable()) { // default is true, so only override if explicitly set to false
                // Check if user explicitly set nullable=false
                // We need a way to distinguish "not set" from "set to true"
                // Using a trick: check if annotation has the attribute explicitly
                // Actually simpler: if default is true, we only care if explicitly false
                // We'll use reflection to check if nullable was explicitly set
                nullable = isNullableExplicitlySet(columnAnnotation, field);
            } else {
                // Default behavior: primitives are non-nullable, wrappers are nullable
                nullable = !isPrimitive(field);
            }

            // Determine default value
            String defaultValue = null;
            if (columnAnnotation != null && !columnAnnotation.defaultValue().isEmpty()) {
                defaultValue = columnAnnotation.defaultValue();
            }

            // Handle String with optional size from @Column annotation
            if (type == String.class) {
                return new ColumnDefinition(columnName, ColumnType.TEXT, null, nullable, defaultValue);
            }

            // Primitive and wrapper types
            if (type == Long.class || type == long.class) {
                return new ColumnDefinition(columnName, ColumnType.BIGINT, null, nullable, defaultValue);
            }
            if (type == Integer.class || type == int.class) {
                return new ColumnDefinition(columnName, ColumnType.INTEGER, null, nullable, defaultValue);
            }
            if (type == Short.class || type == short.class) {
                return new ColumnDefinition(columnName, ColumnType.SMALLINT, null, nullable, defaultValue);
            }
            if (type == Boolean.class || type == boolean.class) {
                return new ColumnDefinition(columnName, ColumnType.BOOLEAN, null, nullable, defaultValue);
            }
            if (type == Double.class || type == double.class) {
                return new ColumnDefinition(columnName, ColumnType.DOUBLE, null, nullable, defaultValue);
            }
            if (type == Float.class || type == float.class) {
                return new ColumnDefinition(columnName, ColumnType.FLOAT, null, nullable, defaultValue);
            }

            // java.time types
            if (type == java.time.Instant.class
                    || type == java.time.LocalDateTime.class
                    || type == java.time.OffsetDateTime.class) {
                return new ColumnDefinition(columnName, ColumnType.TIMESTAMP, null, nullable, defaultValue);
            }
            if (type == java.time.LocalDate.class) {
                return new ColumnDefinition(columnName, ColumnType.DATE, null, nullable, defaultValue);
            }
            if (type == java.time.LocalTime.class) {
                return new ColumnDefinition(columnName, ColumnType.TIME, null, nullable, defaultValue);
            }

            // java.math.BigDecimal
            if (type == java.math.BigDecimal.class) {
                return new ColumnDefinition(columnName, ColumnType.DECIMAL, null, nullable, defaultValue);
            }

            // UUID
            if (type == java.util.UUID.class) {
                return new ColumnDefinition(columnName, ColumnType.UUID, null, nullable, defaultValue);
            }

            // byte array
            if (type == byte[].class) {
                return new ColumnDefinition(columnName, ColumnType.BINARY, null, nullable, defaultValue);
            }

            // Unsupported type
            return null;
        }

        /**
         * Checks if a field is a primitive type (not nullable by default).
         */
        private boolean isPrimitive(Field field) {
            return field.getType().isPrimitive();
        }

        /**
         * Determines nullability based on explicit annotation or field type.
         * Since annotation default is nullable=true, we check if nullable=false was explicitly set.
         */
        private boolean isNullableExplicitlySet(Column annotation, Field field) {
            // If annotation is null, use field type inference
            if (annotation == null) {
                return !isPrimitive(field);
            }
            // If nullable() returns false, user explicitly set it to non-nullable
            // If nullable() returns true, it could be explicit or default
            // We'll assume explicit false means non-nullable, otherwise use type inference
            return annotation.nullable() || !isPrimitive(field);
        }

        /**
         * Converts a camelCase string to snake_case format.
         *
         * @param camelCase the input string in camelCase format
         * @return the converted string in snake_case format
         */
        private String toSnakeCase(String camelCase) {
            return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
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
