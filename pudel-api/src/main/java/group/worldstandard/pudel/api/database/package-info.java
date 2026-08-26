/**
 * Plugin data persistence and database management API.
 *
 * <p>This package provides a JPA-like repository pattern for plugin data persistence.
 * Each plugin gets its own isolated database schema, ensuring data separation between
 * plugins. Raw SQL is not allowed - all interactions go through the repository pattern.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.database.Column} - Annotation for field-to-column mapping</li>
 *   <li>{@link group.worldstandard.pudel.api.database.ColumnType} - Enum of supported column types</li>
 *   <li>{@link group.worldstandard.pudel.api.database.Entity} - Annotation for entity classes</li>
 *   <li>{@link group.worldstandard.pudel.api.database.PluginDatabaseManager} - Main database access interface</li>
 *   <li>{@link group.worldstandard.pudel.api.database.PluginKeyValueStore} - Simple key-value storage</li>
 *   <li>{@link group.worldstandard.pudel.api.database.PluginMigration} - Interface for schema migrations</li>
 *   <li>{@link group.worldstandard.pudel.api.database.PluginRepository} - Repository for CRUD operations</li>
 *   <li>{@link group.worldstandard.pudel.api.database.QueryBuilder} - Fluent query builder</li>
 *   <li>{@link group.worldstandard.pudel.api.database.TableSchema} - Table schema definition builder</li>
 * </ul>
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @Entity
 * public class UserSetting {
 *     private Long id;
 *     private Long userId;
 *     private String key;
 *     private String value;
 *     // getters and setters...
 * }
 *
 * // In your plugin:
 * PluginDatabaseManager db = context.getDatabaseManager();
 *
 * // Create table
 * TableSchema schema = TableSchema.builder("user_settings")
 *     .column("user_id", ColumnType.BIGINT, false)
 *     .column("key", ColumnType.STRING, 100, false)
 *     .column("value", ColumnType.TEXT, true)
 *     .build();
 * db.createTable(schema);
 *
 * // Get repository
 * PluginRepository<UserSetting> repo = db.getRepository("user_settings", UserSetting.class);
 *
 * // CRUD operations
 * UserSetting setting = new UserSetting();
 * setting.setUserId(12345L);
 * setting.setKey("theme");
 * setting.setValue("dark");
 * repo.save(setting);
 *
 * Optional<UserSetting> found = repo.findById(setting.getId());
 * }</pre>
 *
 * <h2>Key-Value Store:</h2>
 * <pre>{@code
 * PluginKeyValueStore kv = db.getKeyValueStore();
 * kv.set("config.enabled", true);
 * boolean enabled = kv.getBoolean("config.enabled", false);
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.database;