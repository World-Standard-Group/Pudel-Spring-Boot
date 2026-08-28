# Pudel Schema Management (schema-as-code)

Pudel's database layout is defined in **Java**, not in a SQL file. There are **two separate schema management systems**:

## 1. Core Bot Schemas (Per-Guild / Per-User)

Managed by `SchemaManagementService` — owns the per-guild / per-user schemas and reconciles them against a declarative in-code model on every boot.

## 2. Plugin Schemas (Plugin Database API)

Managed by `PluginDatabaseManager` — provides a **schema-as-code, self-reconciling** layer for plugin developers. Plugins define their tables via entity classes, and the manager auto-creates/updates schemas on demand.

- Companion diagram: [SchemaManagement.mermaid](./flowchart/architecture/SchemaManagement.mermaid)
- ER diagram: [DatabaseSchema.mermaid](./flowchart/architecture/DatabaseSchema.mermaid)
- Architecture overview: [ARCHITECTURE.md](./flowchart/architecture/ARCHITECTURE.md#schema-management-schema-as-code)

---

## Two layers of schema ownership

| Layer | Scope | Owner | Mechanism |
|-------|-------|-------|-----------|
| Global `public` schema | Shared tables (users, guild_settings, subscriptions, plugin_metadata, …) | Hibernate | `spring.jpa.hibernate.ddl-auto: update` (JPA `@Entity`) |
| Per-guild `guild_{id}` / per-user `user_{id}` schemas | Isolated per-server / per-user tables | `SchemaManagementService` | Declarative reconcile (schema-as-code) |

The two layers are intentionally separate: Hibernate cannot manage
dynamically-named schemas (`guild_123456…`), so those are handled by the
reconciler. **No SQL file is required at runtime** — `database/init.sql` was
deleted. The `database/migrations/*.sql` files are kept only as a historical
change-log.

---

## Why schema-as-code

Before this design, the schema was fragmented across many places:

- `database/init.sql` (a large hand-written SQL file),
- `PassiveContextProcessor.ensurePassiveContextTable()` (created `passive_context`
  with `message_id … UNIQUE`),
- `MemoryEmbeddingService.createGuildEmbeddingTables()` / `createUserEmbeddingTables()`
  (created `memory_embeddings` / `dialogue_embeddings`).

This fragmentation caused real bugs:

- A table created by one code path (`forwarded_messages`) was never actually
  created — only its indexes were — producing
  `ERROR: relation "guild_xxx.forwarded_messages" does not exist`.
- `passive_context.message_id` was declared `UNIQUE` in
  `PassiveContextProcessor` but, because `CREATE TABLE IF NOT EXISTS` is a no-op
  once the table exists, the unique constraint was never added to pre-existing
  schemas. That broke the `INSERT … ON CONFLICT (message_id)` upsert with
  `bad SQL grammar` / `there is no unique or exclusion constraint matching the
  ON CONFLICT specification`.

Consolidating the whole layout into one declarative model in
`SchemaManagementService` removed the divergence: there is now a single source
of truth, and existing schemas are repaired automatically on boot.

---

## The declarative model

Tables are described by an in-code `TableDefinition` record:

```java
record TableDefinition(
        String name,
        List<Col> columns,            // name + SQL type, e.g. "BIGINT", "TEXT[]", "JSONB"
        List<String> foreignKeys,     // inline FK clauses (schema placeholder %SCHEMA%)
        List<String> indexes,         // CREATE INDEX IF NOT EXISTS statements
        List<String> uniqueConstraints // columns that must carry a UNIQUE index
) {}
```

The full layout is returned by:

- `buildGuildTables()` — `dialogue_history`, `passive_context`, `user_preferences`,
  `memory`, `forwarded_messages`
- `buildUserTables()` — `pudel_settings`, `dialogue_history`, `memory`
- `buildEmbeddingTables()` — `memory_embeddings`, `dialogue_embeddings` (vector
  dimension injected from config)

`%SCHEMA%` in FK / index strings is substituted with the real schema name at
runtime (e.g. `guild_644371111819214859`).

---

## Reconcile flow (idempotent, never destructive)

`reconcileSchema(schemaName, tables, embeddingsOnly)` iterates the definitions
and applies, for each table:

```
CREATE TABLE IF NOT EXISTS <schema>.<table> ( … )     # no-op if present
for each column:
    ALTER TABLE <schema>.<table> ADD COLUMN IF NOT EXISTS <col> <type>
        # guarded by a pg_indexes/information_schema column-existence check
CREATE INDEX IF NOT EXISTS <idx> ON <schema>.<table>(…)   # no-op if present
for each unique-constraint column:
    CREATE UNIQUE INDEX IF NOT EXISTS uq_<table>_<col> ON <schema>.<table>(<col>)
```

Key properties:

- **Safe migrations only.** The reconciler never runs `DROP`, `RENAME`, or
  `ALTER TYPE`. It only adds. There is no path that destroys data.
- **Idempotent.** Every statement is `IF NOT EXISTS` / existence-guarded, so
  re-running (e.g. on every boot) is a no-op when the schema already matches.
- **Auto-repair.** Adding a new table or column is just an edit to the
  declaration; on the next boot every existing guild/user schema is brought up
  to date automatically — no manual migration step.
- **Unique indexes handled separately.** A `UNIQUE` constraint cannot be added
  via `ADD COLUMN`, so unique columns are listed in `uniqueConstraints` and
  reconciled with `CREATE UNIQUE INDEX IF NOT EXISTS`. This is what makes the
  `passive_context` `ON CONFLICT (message_id)` upsert work.

> ⚠️ A `CREATE UNIQUE INDEX` will fail if the column already contains duplicate
> rows. The reconciler catches and logs this rather than aborting startup; clean
> the data before adding a unique constraint to a populated table.

---

## pgvector embedding tables

`memory_embeddings` and `dialogue_embeddings` are created **only when pgvector is
- **pgvector embedding tables** (`memory_embeddings`, `dialogue_embeddings`) are
  created only when pgvector is available and embeddings are enabled:
  - `isPgvectorAvailable()` detects the `vector` extension (cached).
  - `MemoryEmbeddingService` pushes the embedding dimension and IVFFlat lists into
    `SchemaManagementService` once at construction, then delegates table
    provisioning to `ensureGuildEmbeddingTables(long)` /
    `ensureUserEmbeddingTables(long)` — it no longer owns any DDL.
  - When pgvector is absent, the embedding tables are simply skipped; the rest of
    the schema is still fully reconciled.
- **Dimension must match the model.** The `embedding.dimension` config value and
  the `embeddingDimension` field must equal the real vector dimension of the
  configured embedding model (the default `qwen3-embedding:8b` is **1024**). A
  mismatch means every insert fails with a pgvector dimension error. To prevent
  this, `ensureGuildEmbeddingTables` / `ensureUserEmbeddingTables` call
  `ensureEmbeddingDimension()`, which `ALTER`s the `embedding` column to
  `vector(<dimension>)` when an existing table was created with a stale dimension
  (e.g. an old `vector(384)`). Safe when the table is empty; failures are logged,
  not thrown.

---

## When reconciliation runs

| Trigger | What happens |
|---------|--------------|
| `SchemaBootstrapRunner` (startup, after JDA ready) | Reconciles every guild the bot is in, then ensures embedding tables per guild |
| Guild join / user DM (`GuildInitializationService`) | Reconciles the new `guild_{id}` / `user_{id}` schema on demand |
| `MemoryEmbeddingService` read/write paths | Ensures embedding tables exist before use |

---

## Making a schema change

1. Edit the relevant `TableDefinition` in `buildGuildTables()` /
   `buildUserTables()` / `buildEmbeddingTables()`.
   - New column → add a `Col`.
   - New table → add a `TableDefinition`.
   - New unique constraint → add the column name to `uniqueConstraints` (and make
     sure existing data is unique, or the unique index creation will be logged and
     skipped until the data is cleaned).
2. Rebuild (`mvn clean package` / `docker compose build pudel`).
3. Restart. The reconciler repairs all existing schemas automatically.

No SQL migration file, no `init.sql` edit, no manual `psql` step is required.

---

## Migrating from the old `init.sql` world

If you are upgrading an instance that was previously bootstrapped with
`init.sql`:

- **Nothing to do.** On first boot after the upgrade, `SchemaManagementService`
  reconciles the live schema. Tables/columns that already exist are left alone;
  anything missing (e.g. `forwarded_messages`, or the `passive_context` unique
  index) is created automatically.
- The `docker-compose.yml` `db` service no longer mounts `init.sql`
  (`/docker-entrypoint-initdb.d/…`). The `Dockerfile` no longer `COPY`s it.
- Global `public` tables continue to be managed by Hibernate as before.

---

## Plugin Database Migration API (NEW)

The `PluginDatabaseManager` provides a **schema-as-code, self-reconciling** layer for plugin developers. Instead of writing manual migrations, plugins define their data model via `@Entity` annotated classes with `@Column` annotations, and the manager handles schema creation and evolution automatically.

### Quick Start

```java
@Entity
public class UserSetting {
    private Long id;
    
    @Column(name = "discord_user_id", nullable = false)
    private Long userId;
    
    private String settingName;
    private String settingValue;
    
    @Column(defaultValue = "true")
    private Boolean enabled;
    
    @Column(unique = true)
    private String email;
    
    @Column(index = true)
    private String username;
    
    @Column(ignore = true)
    private transient String cache;  // Not persisted
    
    // getters and setters...
}

// In your plugin's @OnEnable:
@OnEnable
public void onEnable(PluginContext context) {
    PluginDatabaseManager db = context.getDatabaseManager();
    
    // Option 1: One-liner auto-migration (recommended)
    db.autoMigrate(UserSetting.class, GuildConfig.class, UserProfile.class);
    
    // Option 2: Individual table control
    db.createOrUpdateTable(UserSetting.class);
    
    // Get repository for CRUD
    PluginRepository<UserSetting> repo = db.getRepository("user_setting", UserSetting.class);
}
```

### API Methods

| Method | Purpose |
|--------|---------|
| `autoMigrate(Class<?>... entityClasses)` | **One-liner**: Auto-create or update all tables from entity classes |
| `createOrUpdateTable(Class<T> entityClass)` | Create table if missing, or add missing columns/indexes if exists |
| `getTableSchema(String tableName)` | Introspect current database schema (for debugging/comparison) |

### How It Works

1. **Entity → Schema**: Uses `TableSchema.builder().fromEntity()` to derive desired schema from `@Entity` + `@Column` annotations
2. **Introspection**: Reads current database schema from `information_schema.columns` and `pg_indexes`
3. **Diff & Apply**: Only adds missing columns/indexes (never drops - safe by default)
4. **Naming**: Auto-converts `UserSettingEntity` → `user_setting` table name

### Features from `@Column` Annotations

All `@Column` attributes are respected during migration:

- `nullable` — Override nullability (default: inferred from primitive/wrapper)
- `defaultValue` — SQL default value (e.g., `"true"`, `"0"`, `"'default'"`, `"CURRENT_TIMESTAMP"`)
- `unique` — Creates unique index
- `index` — Creates regular index
- `ignore` — Skip field
- `name` — Custom column name

### Safety

- **Never destructive** — Only `ADD COLUMN IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS`
- **Idempotent** — Safe to call on every plugin startup
- **Auto-repair** — Existing schemas are brought up to date automatically
