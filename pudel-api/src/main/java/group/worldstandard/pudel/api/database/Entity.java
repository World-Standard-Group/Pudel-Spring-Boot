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
 * Annotation for mapping entity classes to database tables.
 * <p>
 * Entities are simple POJOs that map to database rows.
 * Field names are automatically converted to snake_case column names.
 * <p>
 * By default, the database table name is derived from the entity class name
 * (PascalCase → snake_case, with an optional {@code Entity} suffix stripped).
 * For example, {@code UserSettingEntity} → {@code user_setting}.
 * <p>
 * When automatic migration collides with a manually-managed table — for instance
 * because the manual SQL script uses a different naming convention — declare the
 * desired table name explicitly via {@link #tableName()} to guarantee the
 * auto-migrator targets the same table as the manual scripts:
 * <pre>
 * {@code @Entity(tableName = "user_settings")
 * public class UserSettingEntity {
 *     private Long id;
 *     private Long userId;
 *     // ...
 * }
 * }
 * </pre>
 * When {@code tableName} is left empty (the default) the auto-migrator falls
 * back to the PascalCase → snake_case derivation described above.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
    /**
     * Optional explicit database table name for this entity.
     * <p>
     * When empty (the default), the table name is derived from the entity
     * class name (PascalCase → snake_case, with a trailing {@code Entity}
     * suffix stripped).
     * <p>
     * When non-empty, this exact name is used by the auto-migrator so the
     * table it manages matches the one created or expected by manual
     * migration scripts. The value must follow the same naming rules as
     * {@code TableSchema.Builder} — lowercase letters, digits, and
     * underscores, starting with a lowercase letter, max 50 chars.
     *
     * @return the explicit table name, or {@code ""} to derive from the
     *     class name
     */
    String tableName() default "";
}