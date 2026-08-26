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
package group.worldstandard.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing per-guild and per-user database schemas.
 *
 * <p>Each guild and user gets its own PostgreSQL schema for data isolation.
 * This enables Pudel's personalized behavior per guild/user.
 *
 * <p><b>Schema-as-code / self-reconciling:</b> the full per-schema table layout
 * is declared once in Java (see {@link #buildGuildTables()} / {@link #buildUserTables()}).
 * On startup (and whenever a guild/user is created) the service reconciles the
 * live database against that declaration:
 * <ul>
 *   <li>creates any table that is missing ({@code CREATE TABLE IF NOT EXISTS}),</li>
 *   <li>adds any column that is missing ({@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS}),</li>
 *   <li>creates any missing index ({@code CREATE INDEX IF NOT EXISTS}).</li>
 * </ul>
 * This mirrors what {@code spring.jpa.hibernate.ddl-auto: update} does for the
 * global JPA entities, but for the dynamically-named per-guild/per-user schemas
 * that Hibernate cannot manage. It is fully idempotent and never drops or renames
 * data, so existing schemas are automatically repaired when a new table/column is
 * introduced in code (no need to run init.sql or manual migrations first).
 */
@Service
@Transactional
public class SchemaManagementService {
    private static final Logger logger = LoggerFactory.getLogger(SchemaManagementService.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===========================================
    // Declarative table model
    // ===========================================

    /** A single column: name + SQL type (e.g. "BIGINT", "TEXT[]", "JSONB"). */
    private record Col(String name, String type) {}

    /** A complete table definition: name, ordered columns, FK clauses, indexes,
     *  and columns that must carry a UNIQUE index (for ON CONFLICT upserts). */
    private record TableDefinition(
            String name,
            List<Col> columns,
            List<String> foreignKeys,
            List<String> indexes,
            List<String> uniqueConstraints
    ) {}

    // ===========================================
    // Guild Schema Management
    // ===========================================

    /**
     * Create (or repair) a schema for a guild if it doesn't exist.
     * @param guildId the Discord guild ID
     */
    public void createGuildSchema(long guildId) {
        String schemaName = getGuildSchemaName(guildId);
        try {
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            reconcileSchema(schemaName, buildGuildTables(), false);
            logger.debug("Reconciled guild schema: {}", schemaName);
        } catch (Exception e) {
            logger.error("Error creating schema for guild {}: {}", guildId, e.getMessage(), e);
        }
    }

    /**
     * Create (or repair) a schema for a user if it doesn't exist.
     * @param userId the Discord user ID
     */
    public void createUserSchema(long userId) {
        String schemaName = getUserSchemaName(userId);
        try {
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            reconcileSchema(schemaName, buildUserTables(), false);
            logger.debug("Reconciled user schema: {}", schemaName);
        } catch (Exception e) {
            logger.error("Error creating schema for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Reconcile a schema against a set of table definitions.
     * Creates missing tables, adds missing columns, creates missing indexes.
     * Idempotent and safe: never drops or renames anything.
     *
     * @param schemaName   the schema to reconcile
     * @param tables       declared table definitions
     * @param withEmbeddings whether to also (re)create the pgvector embedding tables
     */
    private void reconcileSchema(String schemaName, List<TableDefinition> tables, boolean withEmbeddings) {
        try {
            List<TableDefinition> all = new ArrayList<>(tables);
            if (withEmbeddings) {
                all.addAll(buildEmbeddingTables());
            }
            for (TableDefinition table : all) {
                ensureTable(schemaName, table);
            }
        } catch (Exception e) {
            logger.error("Error reconciling schema {}: {}", schemaName, e.getMessage(), e);
        }
    }

    /** Create the table if absent, then add any missing columns and indexes. */
    private void ensureTable(String schemaName, TableDefinition table) {
        String full = schemaName + "." + table.name();

        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(full).append(" (\n");
        List<String> lines = new ArrayList<>();
        for (Col c : table.columns()) {
            lines.add("    " + c.name() + " " + c.type());
        }
        for (String fk : table.foreignKeys()) {
            // FKs are inline in the CREATE TABLE; substitute the schema placeholder.
            lines.add("    " + fk.replace("%SCHEMA%", schemaName));
        }
        ddl.append(String.join(",\n", lines)).append("\n)");
        jdbcTemplate.execute(ddl.toString());

        for (Col c : table.columns()) {
            addColumnIfMissing(schemaName, table.name(), c);
        }
        for (String idx : table.indexes()) {
            jdbcTemplate.execute(idx.replace("%SCHEMA%", schemaName));
        }
        // Unique constraints can't be added via ADD COLUMN; reconcile them explicitly.
        for (String uc : table.uniqueConstraints()) {
            ensureUniqueIndex(schemaName, table.name(), uc);
        }
    }

    /**
     * Create a UNIQUE index if absent. UNIQUE indexes are idempotent and safe;
     * they back ON CONFLICT (column) upserts and dedupe rows. Creating one on a
     * column that already has duplicates will fail — callers must ensure the
     * data is clean first. Safe to re-run: no-op when the index exists.
     */
    private void ensureUniqueIndex(String schemaName, String tableName, String column) {
        String indexName = "uq_" + tableName + "_" + column;
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND tablename = ? AND indexname = ?",
                Integer.class, schemaName, tableName, indexName);
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName +
                    " ON " + schemaName + "." + tableName + " (" + column + ")");
        } catch (Exception e) {
            logger.warn("Could not create unique index {}.{} ({}): {}", schemaName, tableName, column, e.getMessage());
        }
    }

    /** ALTER TABLE ... ADD COLUMN IF NOT EXISTS, unless the column already exists. */
    private void addColumnIfMissing(String schemaName, String tableName, Col col) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schemaName, tableName, col.name());
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + schemaName + "." + tableName +
                    " ADD COLUMN IF NOT EXISTS " + col.name() + " " + col.type());
        } catch (Exception e) {
            logger.warn("Could not add column {}.{} ({}): {}", schemaName, tableName, col.name(), e.getMessage());
        }
    }

    /** Declarative definition of all core per-guild tables. */
    private List<TableDefinition> buildGuildTables() {
        return List.of(
            new TableDefinition("dialogue_history",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("user_id", "BIGINT NOT NULL"),
                    new Col("channel_id", "BIGINT NOT NULL"),
                    new Col("user_message", "TEXT NOT NULL"),
                    new Col("bot_response", "TEXT"),
                    new Col("intent", "VARCHAR(100)"),
                    new Col("respond_to", "BIGINT"),
                    new Col("attachment_urls", "TEXT[]"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_dialogue_user ON %SCHEMA%.dialogue_history(user_id)",
                    "CREATE INDEX IF NOT EXISTS idx_dialogue_created ON %SCHEMA%.dialogue_history(created_at)",
                    "CREATE INDEX IF NOT EXISTS idx_dialogue_respond_to ON %SCHEMA%.dialogue_history(respond_to)"
                ),
                List.of()
            ),
            new TableDefinition("passive_context",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("message_id", "BIGINT NOT NULL UNIQUE"),
                    new Col("user_id", "BIGINT NOT NULL"),
                    new Col("channel_id", "BIGINT NOT NULL"),
                    new Col("content", "TEXT NOT NULL"),
                    new Col("entities", "JSONB"),
                    new Col("attachment_urls", "TEXT[]"),
                    new Col("forwarded_message_id", "BIGINT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_message_id ON %SCHEMA%.passive_context(message_id)",
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_user ON %SCHEMA%.passive_context(user_id)",
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_channel ON %SCHEMA%.passive_context(channel_id)"
                ),
                List.of("message_id")
            ),
            new TableDefinition("user_preferences",
                List.of(
                    new Col("user_id", "BIGINT PRIMARY KEY"),
                    new Col("preferred_name", "VARCHAR(255)"),
                    new Col("custom_settings", "JSONB"),
                    new Col("notes", "TEXT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"),
                    new Col("updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(),
                List.of()
            ),
            new TableDefinition("memory",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("key", "VARCHAR(255) UNIQUE NOT NULL"),
                    new Col("value", "TEXT NOT NULL"),
                    new Col("category", "VARCHAR(50) DEFAULT 'general'"),
                    new Col("created_by", "BIGINT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"),
                    new Col("updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_memory_key ON %SCHEMA%.memory(key)",
                    "CREATE INDEX IF NOT EXISTS idx_memory_category ON %SCHEMA%.memory(category)"
                ),
                List.of()
            ),
            new TableDefinition("forwarded_messages",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("passive_context_id", "BIGINT"),
                    new Col("message_id", "BIGINT NOT NULL"),
                    new Col("author_id", "BIGINT"),
                    new Col("author_name", "VARCHAR(255)"),
                    new Col("content", "TEXT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(
                    "FOREIGN KEY (passive_context_id) REFERENCES %SCHEMA%.passive_context(id) ON DELETE CASCADE"
                ),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_fwd_msgs_passive_ctx ON %SCHEMA%.forwarded_messages(passive_context_id)",
                    "CREATE INDEX IF NOT EXISTS idx_fwd_msgs_message_id ON %SCHEMA%.forwarded_messages(message_id)"
                ),
                List.of()
            )
        );
    }

    /** Declarative definition of all core per-user tables. */
    private List<TableDefinition> buildUserTables() {
        return List.of(
            new TableDefinition("passive_context",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("message_id", "BIGINT NOT NULL UNIQUE"),
                    new Col("user_id", "BIGINT NOT NULL"),
                    new Col("channel_id", "BIGINT NOT NULL"),
                    new Col("content", "TEXT NOT NULL"),
                    new Col("entities", "JSONB"),
                    new Col("attachment_urls", "TEXT[]"),
                    new Col("forwarded_message_id", "BIGINT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_message_id ON %SCHEMA%.passive_context(message_id)",
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_user ON %SCHEMA%.passive_context(user_id)",
                    "CREATE INDEX IF NOT EXISTS idx_passive_ctx_channel ON %SCHEMA%.passive_context(channel_id)"
                ),
                List.of("message_id")
            ),
            new TableDefinition("forwarded_messages",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("passive_context_id", "BIGINT"),
                    new Col("message_id", "BIGINT NOT NULL"),
                    new Col("author_id", "BIGINT"),
                    new Col("author_name", "VARCHAR(255)"),
                    new Col("content", "TEXT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(
                    "FOREIGN KEY (passive_context_id) REFERENCES %SCHEMA%.passive_context(id) ON DELETE CASCADE"
                ),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_fwd_msgs_passive_ctx ON %SCHEMA%.forwarded_messages(passive_context_id)",
                    "CREATE INDEX IF NOT EXISTS idx_fwd_msgs_message_id ON %SCHEMA%.forwarded_messages(message_id)"
                ),
                List.of()
            ),
            new TableDefinition("pudel_settings",
                List.of(
                    new Col("id", "SERIAL PRIMARY KEY"),
                    new Col("biography", "TEXT"),
                    new Col("personality", "TEXT"),
                    new Col("preferences", "TEXT"),
                    new Col("dialogue_style", "TEXT"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"),
                    new Col("updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(),
                List.of()
            ),
            new TableDefinition("dialogue_history",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("user_message", "TEXT NOT NULL"),
                    new Col("bot_response", "TEXT"),
                    new Col("intent", "VARCHAR(100)"),
                    new Col("respond_to", "BIGINT"),
                    new Col("attachment_urls", "TEXT[]"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_dialogue_created ON %SCHEMA%.dialogue_history(created_at)"
                ),
                List.of()
            ),
            new TableDefinition("memory",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("key", "VARCHAR(255) UNIQUE NOT NULL"),
                    new Col("value", "TEXT NOT NULL"),
                    new Col("category", "VARCHAR(50) DEFAULT 'general'"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"),
                    new Col("updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(),
                List.of(
                    "CREATE INDEX IF NOT EXISTS idx_memory_key ON %SCHEMA%.memory(key)"
                ),
                List.of()
            )
        );
    }

    // ===========================================
    // Embedding tables (pgvector, optional)
    // ===========================================

    /**
     * Ensure the embedding tables exist for a guild schema (if pgvector is
     * available and embeddings are enabled). Safe to call repeatedly.
     * @param guildId the Discord guild ID
     */
    public void ensureGuildEmbeddingTables(long guildId) {
        if (!isPgvectorAvailable()) {
            return;
        }
        try {
            String schemaName = getGuildSchemaName(guildId);
            reconcileSchema(schemaName, buildGuildTables(), true);
            int dim = embeddingDimension();
            ensureEmbeddingDimension(schemaName, "memory_embeddings", dim);
            ensureEmbeddingDimension(schemaName, "dialogue_embeddings", dim);
            ensureEmbeddingDimension(schemaName, "passive_context_embeddings", dim);
            createEmbeddingIndexes(schemaName, "memory_embeddings", "embedding");
            createEmbeddingIndexes(schemaName, "dialogue_embeddings", "embedding");
            createEmbeddingIndexes(schemaName, "passive_context_embeddings", "embedding");
        } catch (Exception e) {
            logger.warn("Could not ensure guild embedding tables for {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Ensure the embedding tables exist for a user schema (if pgvector is
     * available and embeddings are enabled). Safe to call repeatedly.
     * @param userId the Discord user ID
     */
    public void ensureUserEmbeddingTables(long userId) {
        if (!isPgvectorAvailable()) {
            return;
        }
        try {
            String schemaName = getUserSchemaName(userId);
            reconcileSchema(schemaName, buildUserTables(), true);
            int dim = embeddingDimension();
            ensureEmbeddingDimension(schemaName, "memory_embeddings", dim);
            ensureEmbeddingDimension(schemaName, "dialogue_embeddings", dim);
            createEmbeddingIndexes(schemaName, "memory_embeddings", "embedding");
            createEmbeddingIndexes(schemaName, "dialogue_embeddings", "embedding");
        } catch (Exception e) {
            logger.warn("Could not ensure user embedding tables for {}: {}", userId, e.getMessage());
        }
    }

    /** Declarative definition of the pgvector embedding tables (dimension injected). */
    private List<TableDefinition> buildEmbeddingTables() {
        String dim = Integer.toString(embeddingDimension());
        return List.of(
            new TableDefinition("memory_embeddings",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("memory_id", "BIGINT"),
                    new Col("embedding", "vector(" + dim + ") NOT NULL"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(
                    "FOREIGN KEY (memory_id) REFERENCES %SCHEMA%.memory(id) ON DELETE CASCADE"
                ),
                List.of(),
                List.of()
            ),
            new TableDefinition("dialogue_embeddings",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("dialogue_id", "BIGINT"),
                    new Col("embedding", "vector(" + dim + ") NOT NULL"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(
                    "FOREIGN KEY (dialogue_id) REFERENCES %SCHEMA%.dialogue_history(id) ON DELETE CASCADE"
                ),
                List.of(),
                List.of()
            ),
            new TableDefinition("passive_context_embeddings",
                List.of(
                    new Col("id", "BIGSERIAL PRIMARY KEY"),
                    new Col("passive_context_id", "BIGINT"),
                    new Col("embedding", "vector(" + dim + ") NOT NULL"),
                    new Col("created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                ),
                List.of(
                    "FOREIGN KEY (passive_context_id) REFERENCES %SCHEMA%.passive_context(id) ON DELETE CASCADE"
                ),
                List.of(),
                List.of()
            )
        );
    }

    /**
     * Repair the embedding column dimension when it differs from the configured
     * model. pgvector stores the dimension in the column type; an existing table
     * created with a stale dimension (e.g. vector(384) before the default model
     * became qwen3-embedding:8b = 1024) must be altered to match the model that
     * actually produces the vectors, or every insert fails with a dimension
     * mismatch. Safe when the table is empty (no rescaling needed); failures are
     * logged, not thrown, so bootstrap is never blocked.
     */
    private void ensureEmbeddingDimension(String schemaName, String tableName, int dimension) {
        try {
            Integer current = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN a.atttypmod <= 0 THEN -1 ELSE a.atttypmod - 4 END " +
                "FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid " +
                "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relname = ? AND a.attname = 'embedding'",
                Integer.class, schemaName, tableName);
            if (current == null) {
                return; // column not present yet; CREATE TABLE IF NOT EXISTS handled it
            }
            if (current != dimension) {
                jdbcTemplate.execute(String.format(
                    "ALTER TABLE %s.%s ALTER COLUMN embedding TYPE vector(%d)",
                    schemaName, tableName, dimension));
                logger.info("Resized embedding column {}.{} from vector({}) to vector({})",
                        schemaName, tableName, current, dimension);
            }
        } catch (Exception e) {
            logger.warn("Could not resize embedding column {}.{} to vector({}): {}",
                    schemaName, tableName, dimension, e.getMessage());
        }
    }

    private void createEmbeddingIndexes(String schemaName, String tableName, String columnName) {
        String indexName = "idx_" + tableName + "_" + columnName + "_ivfflat";
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                Integer.class, schemaName, indexName);
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute(String.format(
                "CREATE INDEX %s ON %s.%s USING ivfflat (%s vector_cosine_ops) WITH (lists = %d)",
                indexName, schemaName, tableName, columnName, embeddingIvfLists()));
        } catch (Exception e) {
            logger.debug("IVFFlat index {} not created yet (needs more data): {}", indexName, e.getMessage());
        }
    }

    /** Detect availability of the pgvector extension (cached). */
    private Boolean pgvectorAvailable = null;
    boolean isPgvectorAvailable() {
        if (pgvectorAvailable == null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
                pgvectorAvailable = count != null && count > 0;
                if (!pgvectorAvailable) {
                    logger.warn("pgvector extension not found; semantic search disabled. Run CREATE EXTENSION vector;");
                }
            } catch (Exception e) {
                pgvectorAvailable = false;
                logger.warn("Could not check pgvector availability: {}", e.getMessage());
            }
        }
        return pgvectorAvailable;
    }

    /** Embedding vector dimension (default 1024, matching qwen3-embedding:8b). */
    private int embeddingDimension() {
        // Value is injected by Spring via setEmbeddingConfig; default matches ChatbotConfig.
        return embeddingDimension;
    }
    private int embeddingIvfLists() {
        return embeddingIvfLists;
    }
    // Set once by MemoryEmbeddingService (or Spring) so dimension/lists stay in one place.
    // Must match the configured embedding model's real dimension (qwen3-embedding:8b = 1024).
    private int embeddingDimension = 1024;
    private int embeddingIvfLists = 100;

    public void setEmbeddingConfig(int dimension, int ivfLists) {
        this.embeddingDimension = dimension;
        this.embeddingIvfLists = ivfLists;
    }

    // ===========================================
    // Schema introspection helpers
    // ===========================================

    /**
     * Drop a schema for a guild.
     * @param guildId the Discord guild ID
     */
    public void dropGuildSchema(long guildId) {
        String schemaName = getGuildSchemaName(guildId);
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            logger.info("Dropped schema for guild {}: {}", guildId, schemaName);
        } catch (Exception e) {
            logger.error("Error dropping schema for guild {}: {}", guildId, e.getMessage(), e);
        }
    }

    /**
     * Drop a schema for a user.
     * @param userId the Discord user ID
     */
    public void dropUserSchema(long userId) {
        String schemaName = getUserSchemaName(userId);
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            logger.info("Dropped schema for user {}: {}", userId, schemaName);
        } catch (Exception e) {
            logger.error("Error dropping schema for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Get the schema name for a guild.
     * @param guildId the Discord guild ID
     * @return the schema name
     */
    public String getGuildSchemaName(long guildId) {
        return "guild_" + guildId;
    }

    /**
     * Legacy method for compatibility.
     */
    public String getSchemaName(long guildId) {
        return getGuildSchemaName(guildId);
    }

    /**
     * Check if a schema exists for a guild.
     * @param guildId the Discord guild ID
     * @return true if schema exists
     */
    public boolean schemaExists(long guildId) {
        String schemaName = getGuildSchemaName(guildId);
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
            return exists != null && exists > 0;
        } catch (Exception e) {
            logger.error("Error checking schema existence for guild {}: {}", guildId, e.getMessage());
            return false;
        }
    }

    /**
     * Get the schema name for a user.
     * @param userId the Discord user ID
     * @return the schema name
     */
    public String getUserSchemaName(long userId) {
        return "user_" + userId;
    }

    /**
     * Check if a schema exists for a user.
     * @param userId the Discord user ID
     * @return true if schema exists
     */
    public boolean userSchemaExists(long userId) {
        String schemaName = getUserSchemaName(userId);
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
            return exists != null && exists > 0;
        } catch (Exception e) {
            logger.error("Error checking schema existence for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    // ===========================================
    // Schema Statistics (for subscription limits)
    // ===========================================

    /**
     * Get the row count for a table in a guild schema.
     * Used for subscription capacity checking.
     */
    public long getGuildTableRowCount(long guildId, String tableName) {
        String schemaName = getGuildSchemaName(guildId);
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + "." + tableName, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.debug("Error getting row count for {}.{}: {}", schemaName, tableName, e.getMessage());
            return 0;
        }
    }

    /**
     * Get the row count for a table in a user schema.
     * Used for subscription capacity checking.
     */
    public long getUserTableRowCount(long userId, String tableName) {
        String schemaName = getUserSchemaName(userId);
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + "." + tableName, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.debug("Error getting row count for {}.{}: {}", schemaName, tableName, e.getMessage());
            return 0;
        }
    }
}
