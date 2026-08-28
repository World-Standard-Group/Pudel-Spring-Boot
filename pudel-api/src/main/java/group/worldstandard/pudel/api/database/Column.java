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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for customizing field-to-column mapping.
 * <p>
 * By default, field names are converted to snake_case.
 * Use this annotation to specify a custom column name, mark a field as transient,
 * control nullability, default values, and indexing.
 * <p>
 * Example:
 * <pre>
 * {@code @Entity
 * public class UserData {
 *     private Long id;
 *
 *     @Column(name = "discord_user_id", nullable = false)
 *     private Long userId;
 *
 *     @Column(defaultValue = "true")
 *     private Boolean enabled;
 *
 *     @Column(unique = true)
 *     private String email;
 *
 *     @Column(index = true)
 *     private String username;
 *
 *     @Column(ignore = true)
 *     private transient String cachedValue;  // Not persisted
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    /**
     * Custom column name.
     * <p>
     * If empty, the field name is converted to snake_case.
     *
     * @return the column name
     */
    String name() default "";

    /**
     * Whether this field should be ignored during persistence.
     *
     * @return true to ignore this field
     */
    boolean ignore() default false;

    /**
     * Whether the column allows NULL values.
     * <p>
     * If not specified, nullability is inferred from the field type:
     * primitive types are non-nullable, wrapper types are nullable.
     *
     * @return true if nullable, false if not nullable, default is inferred
     */
    boolean nullable() default true;

    /**
     * Default value for the column (SQL expression).
     * <p>
     * Example: "true", "0", "'default_text'", "CURRENT_TIMESTAMP"
     *
     * @return the default value expression, or empty string for none
     */
    String defaultValue() default "";

    /**
     * Whether to create a unique index on this column.
     *
     * @return true to create a unique index
     */
    boolean unique() default false;

    /**
     * Whether to create a regular index on this column.
     *
     * @return true to create an index
     */
    boolean index() default false;
}