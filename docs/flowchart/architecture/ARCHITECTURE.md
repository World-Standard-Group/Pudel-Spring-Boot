# Pudel Architecture v2.3.2

This document describes the complete architecture of Pudel Discord Bot — reflecting the current implementation with Components V2 interactive panels, two-tier plugin control (admin global + guild local), per-guild command sync, and the annotation-based plugin system.

---

## Table of Contents

- [System Overview](#system-overview)
- [Module Structure](#module-structure)
- [Plugin System](#plugin-system)
- [Two-Tier Plugin Control](#two-tier-plugin-control)
- [Command System](#command-system)
- [Components V2 Settings Panel](#components-v2-settings-panel)
- [Brain Architecture](#brain-architecture)
- [REST API (Vue Dashboard)](#rest-api-vue-dashboard)
  - [OpenAPI / Swagger UI](#openapi--swagger-ui)
- [Database Schema](#database-schema)
- [Schema Management (schema-as-code)](#schema-management-schema-as-code)
- [Configuration](#configuration)
- [Authentication Architecture](#authentication-architecture)
  - [Security Filter Chain](#security-filter-chain)
  - [User Authentication (Discord OAuth + DPoP)](#user-authentication-discord-oauth--optional-dpop)
  - [DPoP (RFC 9449)](#dpop-demonstrating-proof-of-possession--rfc-9449)
  - [Admin Authentication (Mutual RSA)](#admin-authentication-mutual-rsa)

---

## System Overview

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                             PUDEL DISCORD BOT v2.3.1                          │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌────────────────────┐       ┌─────────────────────┐       ┌──────────────┐  │
│  │   Vue Frontend     │◄─────►│   Spring Boot API   │◄─────►│  PostgreSQL  │  │
│  │  (Dashboard/Wiki)  │       │    (pudel-core)     │       │  + pgvector  │  │
│  └────────────────────┘       └──────────┬──────────┘       └──────────────┘  │
│                                          │                                    │
│                               ┌──────────┴──────────┐                         │
│                               │                     │                         │
│                    ┌──────────▼─────────┐ ┌────────▼────────┐                 │
│                    │   Discord (JDA 6)  │ │   Ollama LLM    │                 │
│                    │   Gateway + REST   │ │  (Local Model)  │                 │
│                    └────────────────────┘ └─────────────────┘                 │
│                                                                               │
│  ┌────────────────────────────────────────────────────────────────────────┐   │
│  │                     TWO-TIER PLUGIN CONTROL                            │   │
│  │                                                                        │   │
│  │  Admin (Global)         Guild (Local)         Discord                  │   │
│  │  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐          │   │
│  │  │ AdminCtrl    │─────►│ GuildSettings│─────►│ syncGuild    │          │   │
│  │  │ enable/      │      │ disabled_    │      │ Commands()   │          │   │
│  │  │ disable JAR  │      │ plugins CSV  │      │ per-guild    │          │   │
│  │  └──────────────┘      └──────────────┘      └──────────────┘          │   │
│  │                                                                        │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────────┐    │   │
│  │  │  @Plugin    │ │  @Plugin    │ │  @Plugin    │ │ BuiltinCommands│    │   │
│  │  │  Music      │ │  Moderation │ │  Custom...  │ │ (pudel-core)   │    │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────────────┘    │   │
│  └────────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

See: [SystemOverview.mermaid](./SystemOverview.mermaid)

---

## Module Structure

```
pudel/
├── pudel-api/          # Plugin Development Kit (MIT License)
│   ├── annotation/              # Annotation-based API
│   │   ├── Plugin.java          # @Plugin - marks plugin class
│   │   ├── SlashCommand.java    # @SlashCommand - slash command handler
│   │   ├── TextCommand.java     # @TextCommand - text command handler
│   │   ├── ContextMenu.java     # @ContextMenu - context menu command handler
│   │   ├── ButtonHandler.java   # @ButtonHandler - button click handler
│   │   ├── ModalHandler.java    # @ModalHandler - modal submission
│   │   ├── SelectMenuHandler.java # @SelectMenuHandler - select menu
│   │   ├── OnEnable.java        # @OnEnable - lifecycle hook
│   │   ├── OnDisable.java       # @OnDisable - lifecycle hook
│   │   ├── OnShutdown.java      # @OnShutdown - returns boolean
│   │   ├── CommandOption.java   # @CommandOption - command options
│   │   ├── Subcommand.java      # @Subcommand - subcommands
│   │   └── Choice.java          # @Choice - option choices
│   │
│   ├── PluginContext.java       # Runtime context for plugins
│   ├── PluginInfo.java          # Plugin metadata
│   │
│   ├── command/                 # Text command API
│   ├── interaction/             # Discord interactions API
│   │   ├── InteractionManager   # Registers handlers + syncCommands()
│   │   ├── SlashCommandHandler  # Slash command contract
│   │   ├── ButtonHandler        # Button handler contract
│   │   ├── ModalHandler         # Modal handler contract
│   │   ├── SelectMenuHandler    # Select menu handler contract
│   │   ├── ContextMenuHandler   # Context menu contract
│   │   └── AutoCompleteHandler  # Autocomplete contract
│   ├── event/                   # Event listener API
│   ├── audio/                   # Voice/Audio API (DAVE support)
│   ├── agent/                   # Agent Tools API
│   └── database/                # Plugin database API
│
├── pudel-core/         # Main Bot Application (AGPL-3.0)
│   ├── Pudel.java               # Entry point
│   ├── plugin/
│   │   ├── PluginClassLoader.java     # JAR loading + hot-reload
│   │   ├── PluginAnnotationProcessor.java  # Annotation scanning
│   │   ├── PluginContextImpl.java
│   │   └── PluginContextFactory.java
│   ├── service/
│   │   ├── PluginService.java         # Global lifecycle (enable/disable/unload)
│   │   ├── PluginWatcherService.java  # File watcher for hot-reload
│   │   ├── GuildSettingsService.java  # Guild-level plugin toggle
│   │   ├── GuildInitializationService.java
│   │   ├── AuthService.java
│   │   ├── ChatbotService.java
│   │   ├── CommandExecutionService.java
│   │   ├── DiscordAPIService.java
│   │   ├── DPoPService.java
│   │   ├── SubscriptionService.java
│   │   ├── MarketPluginService.java
│   │   └── MemoryEmbeddingService.java
│   ├── interaction/
│   │   ├── InteractionManagerImpl.java  # Two-tier sync (global + per-guild)
│   │   ├── InteractionEventListener.java
│   │   └── builtin/
│   │       ├── BuiltinCommands.java          # Components V2 /settings panel
│   │       ├── BuiltinTextCommands.java      # !ping + !help (with paged navigation)
│   │       ├── BuiltinAgentTools.java        # AI agent data management tools
│   │       └── BuiltinSlashCommandRegistrar.java  # Registers all 3 at startup
│   ├── controller/                     # REST API (Vue Dashboard)
│   │   ├── AdminController.java       # Admin-only: global plugin management
│   │   ├── GuildSettingsController.java # Guild plugin enable/disable + settings
│   │   ├── GuildDataController.java
│   │   ├── AuthController.java
│   │   ├── BotInstanceController.java
│   │   ├── BotStatusController.java
│   │   ├── BrainController.java
│   │   ├── SubscriptionController.java
│   │   ├── MarketPluginController.java
│   │   └── UserDataController.java
│   ├── brain/
│   │   ├── PudelBrain.java
│   │   ├── context/
│   │   ├── memory/
│   │   ├── personality/
│   │   └── response/
│   ├── discord/
│   │   ├── DiscordEventListener.java  # Message routing
│   │   ├── GuildEventListener.java    # Guild join/leave
│   │   └── ReactionNavigationListener.java
│   ├── command/
│   │   ├── CommandRegistry.java
│   │   ├── CommandMetadataRegistry.java
│   │   └── builtin/
│   ├── agent/
│   │   ├── AgentToolRegistryImpl.java
│   │   ├── AgentToolContextImpl.java
│   │   └── PluginToolAdapter.java
│   ├── audio/
│   │   ├── VoiceManagerImpl.java
│   │   ├── JDAAudioSendHandler.java
│   │   └── JDAAudioReceiveHandler.java
│   ├── bootstrap/
│   │   ├── PluginBootstrapRunner.java
│   │   ├── CommandBootstrapRunner.java
│   │   ├── SchemaBootstrapRunner.java
│   │   └── SlashCommandSyncRunner.java
│   ├── database/
│   │   ├── PluginDatabaseService.java
│   │   ├── PluginDatabaseManagerImpl.java
│   │   ├── PluginRepositoryImpl.java
│   │   ├── PluginKeyValueStoreImpl.java
│   │   ├── QueryBuilderImpl.java
│   │   └── MigrationHelperImpl.java
│   ├── entity/
│   │   ├── GuildSettings.java         # disabled_plugins CSV field
│   │   ├── PluginMetadata.java
│   │   ├── AdminWhitelist.java
│   │   ├── User.java / BotUser.java
│   │   ├── Guild.java / UserGuild.java
│   │   ├── Subscription.java
│   │   └── MarketPlugin.java
│   └── repository/
│       ├── GuildSettingsRepository.java
│       ├── PluginMetadataRepository.java
│       ├── AdminWhitelistRepository.java
│       └── ...
│
├── plugins/            # Hot-reload directory for plugin JARs
└── keys/               # JWT + Admin RSA keys + mTLS client certs
```

See: [ModuleStructure.mermaid](./ModuleStructure.mermaid)

---

## Plugin System

### Annotation-Based Architecture

```
Developer writes:                     Core handles:
─────────────────                     ─────────────

@Plugin(name="MyPlugin")              PluginClassLoader
public class MyPlugin {               ├── JAR discovery + hot-reload
                                      ├── @Plugin detection
    @SlashCommand(...)       ────────►├── Instance creation
    public void cmd(event)            │
                                      PluginAnnotationProcessor
    @ButtonHandler(...)      ────────►├── Method scanning
    public void btn(event)            ├── Handler registration
                                      ├── Auto syncCommands()
    @ModalHandler(...)       ────────►│
    public void modal(event)          PluginService
                                      ├── Global lifecycle
    @OnEnable                ────────►├── Enable / Disable / Unload
    public void enable(ctx)           └── Force-kill on @OnShutdown false

    @OnShutdown              ────────►InteractionManagerImpl
    public boolean shutdown(ctx)      ├── Two-tier command sync
}                                     ├── Core → global commands
                                      └── Plugin → per-guild commands
```

### Plugin Lifecycle

See: [PluginsLifecycle.mermaid](./PluginsLifecycle.mermaid)

### Hot-Reload System

See: [PluginHotfix.mermaid](./PluginHotfix.mermaid)

---

## Two-Tier Plugin Control

This is the core architectural pattern that connects admin, guilds, and Discord slash commands.

### Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     TWO-TIER PLUGIN CONTROL                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Tier 1: ADMIN (Global)                                                     │
│  ───────────────────────                                                    │
│  AdminController (localhost:8080/api/admin/plugins)                         │
│  ├── POST /{id}/enable   → PluginService.enablePlugin()                     │
│  ├── POST /{id}/disable  → PluginService.disablePlugin()                    │
│  └── Effects: loads/unloads JAR, registers/unregisters all handlers         │
│                                                                             │
│  Tier 2: GUILD (Per-Server)                                                 │
│  ──────────────────────────                                                 │
│  GuildSettingsController (/api/guilds/{id}/plugins)                         │
│  ├── POST /{pluginName}/enable  → remove from disabled_plugins CSV          │
│  ├── POST /{pluginName}/disable → add to disabled_plugins CSV               │
│  └── Effects: syncGuildCommands() hides/shows slash commands                │
│                                                                             │
│  Also accessible via:                                                       │
│  ├── /settings slash command → Components V2 Plugin panel (in Discord)      │
│  └── Vue Dashboard → REST API (in browser)                                  │
│                                                                             │
│  Command Sync Strategy:                                                     │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │ Core commands (pudel-core): registered GLOBALLY                    │     │
│  │   /settings, /ping, /help → always visible everywhere              │     │
│  │                                                                    │     │
│  │ Plugin commands: registered PER-GUILD                              │     │
│  │   For each guild → filter out disabled_plugins → guild.update()    │     │
│  │   If Guild B disables "music" → /music disappears from Guild B     │     │
│  │   Guild A still sees /music if they haven't disabled it            │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Flow: Guild Admin Disables a Plugin

```
Guild Admin clicks "Disable Music" in /settings panel
    │
    ▼
BuiltinCommands.handlePluginToggle()
    ├── guildSettingsService.disablePluginForGuild(guildId, "pudel-music")
    │   └── GuildSettings.disabled_plugins = "pudel-music,..."  (CSV update)
    │
    ├── interactionManager.syncGuildCommands(guildId)
    │   ├── Get disabled plugins set for this guild
    │   ├── Filter slash commands: skip if pluginId in disabled set
    │   └── guild.updateCommands().addCommands(filteredList).submit()
    │       → Discord removes /music from Guild B's command list
    │
    └── Refresh Components V2 panel (show updated toggle state)
```

---

## Command System

### Built-in Commands (since v2.2.2)

**Slash Commands** (`BuiltinCommands` — `pudel-core`):

| Command | Description | Scope |
|---------|-------------|-------|
| `/settings` | Components V2 interactive Settings Panel | Global |

**Text Commands** (`BuiltinTextCommands` — `pudel-core-text`):

| Command | Description | Features |
|---------|-------------|----------|
| `!ping` | Bot latency (rich embed) | Gateway + round-trip |
| `!help` | Full command listing | Paged (8/page), ⏮◀▶⏭ buttons, `!help <cmd>` detail |

**Agent Tools** (`BuiltinAgentTools` — `pudel-core-tools`):

14 tools registered via `AgentToolRegistry.registerProvider()`. See [AGENT_SYSTEM.md](../../AGENT_SYSTEM.md).

**Removed** (merged into `/settings` panel):
- ~~`/ai`~~ → Settings Panel > AI view
- ~~`/channel`~~ → Settings Panel > Channels view
- ~~`/command`~~ → Settings Panel > Commands view

### Registration (BuiltinSlashCommandRegistrar)

All built-in components are registered at `@PostConstruct`:

```
BuiltinSlashCommandRegistrar
├── processAndRegister("pudel-core", builtinCommands)        → slash commands
├── processAndRegister("pudel-core-text", builtinTextCommands) → text commands
├── agentToolRegistry.registerProvider("pudel-core-tools", builtinAgentTools) → agent tools
└── syncCommands()                                            → push to Discord
```

### Annotation-Based Commands

See: [CommandSystem.mermaid](./CommandSystem.mermaid)

### Slash Command Flow

See: [SlashCommandFlow.mermaid](./SlashCommandFlow.mermaid)

---

## Components V2 Settings Panel

The `/settings` command opens a single ephemeral message with a rich interactive panel. Users navigate between views using buttons — all within one message, no subcommands needed. Inspired by community plugin patterns (PudelMusicPlugin's "Music Box").

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                    COMPONENTS V2 SETTINGS PANEL                               │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  User types /settings                                                         │
│       │                                                                       │
│       ▼                                                                       │
│  Accent Colors:                                                               │
│  ┌─────────────────────────────────────┐                                      │
│  │ ⚙️ Settings Panel (Main View)       │                                      │
│  │ Prefix: !  Verbosity: 3  AI: ✅     │                                      │
│  │ ─────────────────────────────────── │                                      │
│  │ [⚙ General] [🤖 AI] [📢 Channels]  │                                      │
│  │ [📝 Commands] [🧩 Plugins]         │                                      │
│  └─────────────────────────────────────┘                                      │
│       │                                                                       │
│       ├── ⚙ General ──► Prefix (modal), Cooldown (modal), Verbosity (btns)    │
│       ├── 🤖 AI ──► Toggle, Nickname/Language/Personality/Biography (modals)  │
│       │              Response Length (btns), Formality (btns),                │
│       │              Emote Usage (btns), 🔧 AI Advanced (prefs, quirks,       │
│       │              system-prompt prefix, topics)                            │
│       ├── 📢 Channels ──► Log/Bot channel (EntitySelectMenu modal),           |
│       │                    Ignore/Unignore (EntitySelectMenu modal)           │
│       ├── 📝 Commands ──► Paginated toggle buttons per text command           │
│       └── 🧩 Plugins ──► Paginated toggle buttons per plugin                  │
│                           └─► syncGuildCommands() on toggle                   │
│                                                                               │
│  Pattern:                                                                     │
│  ├── SettingsSession (per-user, ConcurrentHashMap<userId, Session>)           │
│  ├── SettingsView enum: MAIN, GENERAL, AI, AI_ADVANCED, CHANNELS, COMMANDS, PLUGINS │
│  ├── View builders return Container.of(children).withAccentColor(color)       │
│  ├── @ButtonHandler("settings:") routes all button clicks                     │
│  ├── @ModalHandler("settings:modal:") routes text/channel inputs              │
│  └── Channel selection uses EntitySelectMenu inside Modal (native picker)     │
│                                                                               │
│  Accent Colors:                                                               │
│  ├── Main: #5865F2 (Discord Blurple)                                          │
│  ├── General: #57F287 (Green)                                                 │
│  ├── AI: #EB459E (Pink)                                                       │
│  ├── Channels: #FEE75C (Yellow)                                               │
│  ├── Commands: #ED4245 (Red)                                                  │
│  └── Plugins: #00D4AA (Teal)                                                  │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## Brain Architecture (v2.3.1)

PudelBrain v2 is Ollama completion-focused with async Discord handling, passive context collection, dialogue history tracking, and a dual MCP/Agent tool system.

```
Input: User Message (@mention or trigger)
    │
    ▼
DiscordEventListener → PudelBrain.processMessageAsync()
    │
    ├── sendTyping().queue()  [non-blocking]
    │
    ▼
EntityExtractor → extractEntities()
    ├── Intent Detection
    ├── Sentiment Analysis
    ├── Entity Extraction (users, channels, roles, emojis, URLs, attachments)
    └── Language Detection
    │
    ├── [Agent intent?] ──► PudelAgentService.processWithTools()
    │                         ├── McpToolRegistry (5 built-in MCP tools)
    │                         │   ├── get_passive_context
    │                         │   ├── get_dialogue_history
    │                         │   ├── get_message_by_id
    │                         │   ├── get_forwarded_messages
    │                         │   └── get_brain_status
    │                         └── AgentToolRegistry (14+ agent tools)
    │                             ├── BuiltinAgentTools (create_table, store_data, etc.)
    │                             └── Plugin Tools (via @AgentTool)
    │
    ▼
Context Gathering
    ├── PassiveContextProcessor → getRecentContext()
    │   └── passive_context table (message_id, entities JSONB, attachment_urls)
    ├── DialogueHistoryManager → getRecentHistory()
    │   └── dialogue_history table (respond_to, attachment_urls)
    └── SystemPromptBuilder → buildSystemPrompt()
        └── biography, personality, preferences, dialogue_style, etc.
    │
    ▼
OllamaClient.generateStreaming() → ensureDiscordMarkdown() → truncateForDiscord()
    │
    ▼
sendMessage().queue() with setMessageReference()  [async, non-blocking]
    │
    ▼
Storage
    ├── DialogueHistoryManager.storeDialogue() [respond_to tracking]
    └── PassiveContextProcessor.submit() [async batch, message_id + entities]
```

### Key v2 Components

| Component | Purpose |
|-----------|---------|
| **PudelBrain** | Main entry point, async Discord handling (typing + sendMessage) |
| **PassiveContextProcessor** | Collects observed messages with entity extraction, message_id tracking |
| **DialogueHistoryManager** | Stores conversation turns with respond_to message ID tracking |
| **EntityExtractor** | Extracts users, channels, roles, emojis, URLs, attachments from messages |
| **SystemPromptBuilder** | Builds system prompt from personality settings |
| **OllamaClient** | Streaming completion with Discord Markdown formatting |
| **PudelAgentService** | Unified tool-calling iteration (MCP + Agent tools) |
| **BuiltinMcpTools** | 5 MCP tools for context/history/brain status access |
| **BuiltinMcpToolRegistrar** | Registers MCP tools at startup |
| **FileAttachment** | Image/video reply support with auto-detected MIME types |

See: [PudelBrain.mermaid](./PudelBrain.mermaid), [AgentSystem.mermaid](./AgentSystem.mermaid), [PassiveContext.mermaid](./PassiveContext.mermaid)

---

## REST API (Vue Dashboard)

### OpenAPI / Swagger UI

Pudel exposes interactive API documentation via SpringDoc OpenAPI (Swagger UI).

```
Swagger UI:   http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```

Configured in `OpenApiConfig.java` with 3 security schemes:

| Scheme | Type | Description |
|--------|------|-------------|
| `Bearer` | HTTP Bearer | Standard JWT from Discord OAuth callback |
| `DPoP` | API Key (Header) | DPoP proof token (RFC 9449) — use `DPoP <token>` in Authorization + proof in `DPoP` header |
| `AdminBearer` | HTTP Bearer | Admin JWT from mutual RSA authentication flow |

Toggle via environment: `SWAGGER_ENABLED=true/false` (default: `true`)

---

## Database Schema

Pudel uses **two layers** of schema management:

1. **Global `public` schema** — owned by Hibernate (`spring.jpa.hibernate.ddl-auto: update`). The JPA `@Entity` classes (users, guild_settings, subscriptions, plugin_metadata, …) auto-create/evolve here. No SQL file is needed.
2. **Per-guild / per-user schemas** (`guild_{id}`, `user_{id}`) — owned by `SchemaManagementService`, a **schema-as-code, self-reconciling** layer. The full table layout is declared once in Java; on startup (and whenever a guild/user is created) the bot reconciles the live DB against that declaration, creating missing tables, columns, and indexes, and repairing existing schemas. This makes `init.sql` obsolete (the file was deleted).

> See [SCHEMA_MANAGEMENT.md](../../SCHEMA_MANAGEMENT.md) and
> [SchemaManagement.mermaid](./SchemaManagement.mermaid) for the full design.

### Global `public` schema (shared)

```
users                   # Discord user profiles
guild_settings          # Per-guild config + disabled_plugins CSV
user_guilds             # User-guild membership
subscriptions           # Subscription tiers
plugin_metadata         # Loaded plugin info (name, version, jar_path)
plugin_kv_store         # Plugin key-value storage
plugin_database_registry # Plugin table registry
admin_whitelist         # Admin RSA public keys
market_plugins          # Plugin marketplace
bot_users               # Bot user records
```

### Per-guild schema `guild_{id}` (isolation)

```
dialogue_history    # Conversation turns (user_message, bot_response, respond_to, attachment_urls)
passive_context     # Observed messages w/ entity extraction (message_id UNIQUE, entities JSONB,
                    #   attachment_urls TEXT[], forwarded_content JSONB)
forwarded_messages  # Forwarded-message refs (passive_context_id FK → passive_context.id ON DELETE CASCADE)
user_preferences    # Per-guild user prefs (preferred_name, custom_settings JSONB)
memory             # Key/value memory store (key UNIQUE)
memory_embeddings  # pgvector semantic memory (created when pgvector available)
dialogue_embeddings # pgvector dialogue embeddings (created when pgvector available)
```

### Per-user schema `user_{id}` (isolation)

```
pudel_settings     # Biography / personality / preferences / dialogue_style
dialogue_history   # DM conversation turns
memory             # Key/value memory store (key UNIQUE)
memory_embeddings  # pgvector semantic memory (created when pgvector available)
dialogue_embeddings # pgvector dialogue embeddings (created when pgvector available)
```

See: [DatabaseSchema.mermaid](./DatabaseSchema.mermaid)

---

## Schema Management (schema-as-code)

`SchemaManagementService` is the single source of truth for per-guild/per-user
schemas. It replaces the old `init.sql` + scattered `CREATE TABLE IF NOT EXISTS`
calls (e.g. `PassiveContextProcessor.ensurePassiveContextTable`,
`MemoryEmbeddingService.createGuildEmbeddingTables`) with one declarative model.

**Declarative model.** Tables are described by an in-code `TableDefinition`
(name, columns, FK clauses, indexes, unique-constraint columns). `buildGuildTables()`,
`buildUserTables()`, and `buildEmbeddingTables()` return the full layout.

**Reconcile flow (idempotent, never destructive):**

```
reconcileSchema(schema, tables, embeddingsOnly)
  └─ for each TableDefinition:
       CREATE TABLE IF NOT EXISTS …          (no-op if present)
       for each column:
         ALTER TABLE … ADD COLUMN IF NOT EXISTS (guarded by column-existence check)
       CREATE INDEX IF NOT EXISTS …          (no-op if present)
       for each unique-constraint column:
         CREATE UNIQUE INDEX IF NOT EXISTS uq_<table>_<col> …  (no-op if present)
```

- **Safe migrations only.** It never runs `DROP`/`RENAME`/`ALTER TYPE`. Adding a
  table or column is just an edit to the declaration; on the next boot every
  existing schema is auto-repaired.
- **Unique indexes can't be added via `ADD COLUMN`**, so they are reconciled
  separately (e.g. `passive_context.message_id` → `uq_passive_context_message_id`),
  which is what makes the `INSERT … ON CONFLICT (message_id)` upsert work.
- **pgvector embedding tables** (`memory_embeddings`, `dialogue_embeddings`) are
  created only when pgvector is detected and embeddings are enabled; the vector
  dimension is injected from config. `MemoryEmbeddingService` now delegates table
  provisioning to this service instead of owning duplicate DDL.
- **Trigger points.** `SchemaBootstrapRunner` runs on startup (after JDA ready)
  and reconciles every guild the bot is in, plus embedding tables. New guilds
  joining later are handled by the guild-join path in `GuildInitializationService`.

See: [SchemaManagement.mermaid](./SchemaManagement.mermaid)

---

## Configuration

```env
# Discord
DISCORD_BOT_TOKEN=your_token

# Database
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=pudel
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# AI
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b

# JWT (for user authentication)
JWT_PRIVATE_KEY_PATH=./keys/pv.key
JWT_PUBLIC_KEY_PATH=./keys/pb.key

# Admin Authentication
PUDEL_ADMIN_INITIAL_OWNER=123456789012345678
PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH=./keys/owner_pb.key
```

---

## Authentication Architecture

Pudel implements a layered authentication system with three mechanisms:

1. **Discord OAuth2** — user login via Discord, returns JWT
2. **DPoP (RFC 9449)** — optional proof-of-possession binding for stolen token protection
3. **Admin Mutual RSA** — admin-level access via RSA keypair challenge-response

### Security Filter Chain

```
Every HTTP Request
    │
    ▼
JwtAuthenticationFilter
    ├── Extract Authorization header
    │
    ├── Authorization: Bearer <token>
    │   ├── Validate JWT signature (RSA public key)
    │   ├── Check: is this a DPoP-bound token?
    │   │   ├── YES → Reject! Must use "DPoP" scheme
    │   │   │         (WWW-Authenticate: DPoP error="use_dpop_nonce")
    │   │   └── NO  → Standard Bearer flow, set auth context
    │   └── Grant: [USER]
    │
    ├── Authorization: DPoP <token>
    │   ├── Validate JWT signature (RSA public key)
    │   ├── Require DPoP header present
    │   ├── DPoPService.validateProofForResource()
    │   │   ├── Verify proof signature (client's public key from JWK)
    │   │   ├── Validate jti (replay detection)
    │   │   ├── Validate htm (HTTP method match)
    │   │   ├── Validate htu (URI match, reverse-proxy aware)
    │   │   ├── Validate iat (not too old, not future)
    │   │   ├── Validate ath (SHA-256 hash of access token)
    │   │   └── Verify thumbprint matches token binding
    │   ├── Set auth context
    │   └── Grant: [USER, DPOP_VERIFIED]
    │
    └── No Authorization header → anonymous (public endpoints only)
```

### User Authentication (Discord OAuth + Optional DPoP)

```
Standard flow:
  User → Discord OAuth → POST /api/auth/discord/callback
       → Bearer JWT (RSA signed, 7 days)

DPoP-enhanced flow:
  User → Discord OAuth → POST /api/auth/discord/callback
       + DPoP header: signed proof JWT with client's public key
       → DPoP-bound JWT (contains cnf.jkt thumbprint claim)
       → Every subsequent request must include fresh DPoP proof
```

See: [AuthFlow.mermaid](./AuthFlow.mermaid)

### DPoP (Demonstrating Proof-of-Possession) — RFC 9449

DPoP prevents token theft by cryptographically binding tokens to the client's keypair. Even if a JWT is intercepted, it **cannot be used** without the client's private key.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DPoP TOKEN LIFECYCLE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. Token Request (OAuth callback)                                          │
│  ──────────────────────────────────                                         │
│  Client generates RSA/EC keypair (Web Crypto API, in browser)               │
│  Client creates DPoP proof JWT:                                             │
│    Header: { typ: "dpop+jwt", alg: "RS256", jwk: { client public key } }    │
│    Payload: { jti: unique, htm: "POST", htu: "/api/auth/..", iat: now }     │
│    Signed with: client's PRIVATE key                                        │
│                                                                             │
│  POST /api/auth/discord/callback                                            │
│    Authorization: (none — this is the login)                                │
│    DPoP: <proof JWT>                                                        │
│    Body: { code: "discord_oauth_code" }                                     │
│                                                                             │
│  Server validates proof → extracts JWK thumbprint (RFC 7638)                │
│  Server generates JWT with cnf.jkt = thumbprint                             │
│  Server binds: tokenBindings[jwt] = thumbprint                              │
│  Response: { token: "...", token_type: "DPoP" }                             │
│                                                                             │
│  2. Protected Resource Access                                               │
│  ────────────────────────────                                               │
│  For EVERY request, client creates fresh DPoP proof:                        │
│    Payload: { jti: new_unique, htm: "GET", htu: "/api/guilds/...",          │
│               iat: now, ath: SHA256(access_token) }                         │
│                                                                             │
│  GET /api/guilds/123/settings                                               │
│    Authorization: DPoP <access_token>                                       │
│    DPoP: <fresh proof JWT>                                                  │
│                                                                             │
│  Server validates:                                                          │
│    ✓ Proof signature valid (client's public key)                            │
│    ✓ jti not replayed (ConcurrentHashMap cache, 5min window)                │
│    ✓ htm matches request method                                             │
│    ✓ htu matches request URI (path-only fallback for reverse proxy)         │
│    ✓ iat within 5 minutes, not future (30s clock skew tolerance)            │
│    ✓ ath = SHA-256(access_token)                                            │
│    ✓ JWK thumbprint matches token binding                                   │
│                                                                             │
│  3. Logout                                                                  │
│  ─────────                                                                  │
│  POST /api/auth/logout → revokeTokenBinding(token)                          │
│                                                                             │
│  Implementation: DPoPService.java (544 lines)                               │
│  ├── validateProofForTokenRequest() — no ath required                       │
│  ├── validateProofForResource() — ath required                              │
│  ├── bindTokenToThumbprint() / revokeTokenBinding()                         │
│  ├── JTI replay cache with cleanup thread (5-min intervals)                 │
│  ├── JWK → PublicKey conversion (RSA + EC P-256/P-384/P-521)                │
│  └── JWK thumbprint calculation (RFC 7638, SHA-256)                         │
│                                                                             │
│  Enforced in: JwtAuthenticationFilter.java                                  │
│  ├── DPoP-bound token + Bearer scheme → 401 "use DPoP scheme"               │
│  ├── DPoP scheme + no proof header → 401 "missing proof"                    │
│  ├── DPoP proof invalid → 401 with specific error                           │
│  └── DPoP proof valid → grants DPOP_VERIFIED authority                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

See: [DPoPFlow.mermaid](./DPoPFlow.mermaid)

### Admin Authentication (Mutual RSA)

Each admin has their own RSA keypair. Private keys never leave the browser.

```
1. Login with Discord OAuth → User JWT
2. GET /api/admin/challenge → Pudel signs nonce with its private key
3. Admin signs nonce with their private key (Web Crypto API, in browser)
4. POST /api/admin/auth/mutual → Pudel verifies with admin's public key from DB
5. If valid → Admin JWT (1-hour session)
```

See: [AdminMutualAuth.mermaid](./AdminMutualAuth.mermaid)

### Token Types

| Token | Scheme | Subject | Duration | Binding | Purpose |
|-------|--------|---------|----------|---------|---------|
| User JWT | `Bearer` | `{discordUserId}` | 7 days | None | Dashboard access |
| User JWT (DPoP) | `DPoP` | `{discordUserId}` | 7 days | JWK thumbprint (`cnf.jkt`) | Theft-protected dashboard |
| Admin JWT | `Bearer` | `pudel-admin-session` | 1 hour | None | Admin panel access |

### Admin Roles

| Role | Permissions |
|------|-------------|
| OWNER | Full access + manage admin whitelist |
| ADMIN | Plugin management, settings |
| MODERATOR | View-only access |

---

*Last updated: 2026-07-10 — schema is now defined in Java (schema-as-code / self-reconciling); `init.sql` removed.*

