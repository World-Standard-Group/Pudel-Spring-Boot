# Pudel Architecture v2.5.0

This document describes the complete architecture of Pudel Discord Bot — reflecting the current implementation with Components V2 interactive panels, two-tier plugin control (admin global + guild local), per-guild command sync, and the annotation-based plugin system.

---

## Table of Contents

- [System Overview](#system-overview)
- [Module Structure](#module-structure)
- [Plugin System](#plugin-system)
- [Two-Tier Plugin Control](#two-tier-plugin-control)
- [Command System](#command-system)
- [Components V2 Settings Panel](#components-v2-settings-panel)
- [REST API (Vue Dashboard)](#rest-api-vue-dashboard)
  - [OpenAPI / Swagger UI](#openapi--swagger-ui)
- [Database Schema](#database-schema)
- [Schema Management (schema-as-code)](#schema-management-schema-as-code)
- [Configuration](#configuration)
- [Authentication Architecture](#authentication-architecture)
  - [Security Filter Chain](#security-filter-chain)
  - [Session Bootstrap](#session-bootstrap)
  - [User Authentication (Discord OAuth + DPoP)](#user-authentication-discord-oauth--dpop)
  - [DPoP (RFC 9449)](#dpop-demonstrating-proof-of-possession--rfc-9449)
  - [Admin Authentication (Mutual)](#admin-authentication-mutual)

---

## System Overview

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                             PUDEL DISCORD BOT v2.5.0                          │
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
│   │   ├── DPoPService.java           # RFC 9449 proof validation
│   │   ├── DPoPKeyManager.java        # Ed25519 keypair persistence + proof signing
│   │   ├── SubscriptionService.java
│   │   ├── MarketPluginService.java
│   │   └── MemoryEmbeddingService.java
│   ├── session/                      # BFF session layer
│   │   ├── SessionCookieService.java  # AES-GCM encrypted HttpOnly cookie
│   │   └── SessionAuthenticationService.java  # cookie → internal DPoP proof → validate
│   ├── websocket/                    # Admin log stream
│   │   ├── WebSocketConfiguration.java
│   │   ├── AdminLogHandshakeInterceptor.java
│   │   └── AdminLogWebSocketHandler.java
│   ├── config/springboot/
│   │   ├── SecurityConfiguration.java
│   │   ├── JwtAuthenticationFilter.java   # cookie-only, rejects Authorization
│   │   ├── SwaggerAccessFilter.java
│   │   ├── JwtUtil.java                  # EdDSA (Ed25519) sign/verify
│   │   ├── SpaWebConfig.java
│   │   └── OpenApiConfig.java
│   ├── interaction/
│   │   ├── InteractionManagerImpl.java  # Two-tier sync (global + per-guild)
│   │   ├── InteractionEventListener.java
│   │   └── builtin/
│   │       ├── BuiltinCommands.java          # Components V2 /settings panel
│   │       ├── BuiltinTextCommands.java      # !ping + !help (with paged navigation)
│   │       ├── BuiltinAgentTools.java        # 14 @AgentTool methods
│   │       └── BuiltinSlashCommandRegistrar.java  # Registers all 3 at startup
│   ├── controller/                     # REST API (Vue Dashboard)
│   │   ├── AdminController.java       # Admin-only: global plugin management
│   │   ├── GuildSettingsController.java # Guild plugin enable/disable + settings
│   │   ├── GuildDataController.java
│   │   ├── AuthController.java
│   │   ├── SessionController.java     # Encrypted browser session bootstrap/rotate
│   │   ├── DPoPController.java        # Legacy key management endpoints
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
│   │   ├── DPoPKey.java               # browser session keypair + server-held tokens
│   │   ├── User.java / BotUser.java
│   │   ├── Guild.java / UserGuild.java
│   │   ├── Subscription.java
│   │   └── MarketPlugin.java
│   └── repository/
│       ├── GuildSettingsRepository.java
│       ├── PluginMetadataRepository.java
│       ├── AdminWhitelistRepository.java
│       ├── DPoPKeyRepository.java
│       └── ...

├── plugins/            # Hot-reload directory for plugin JARs
└── keys/               # Ed25519 JWT keys (pv.key/pb.key), admin RSA public keys,
                        # session cookie key, mTLS client certs
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

**Text Commands** (`BuiltinTextCommands` — `pudel-core`):

| Command | Description | Features |
|---------|-------------|----------|
| `!ping` | Bot latency (rich embed) | Gateway + round-trip |
| `!help` | Full command listing | Paged (8/page), ⏮◀▶⏭ buttons, `!help <cmd>` detail |

**Agent Tools** (`BuiltinAgentTools` — `pudel-core`):

14 `@AgentTool` methods registered via `AgentToolRegistry.registerProvider()`.
See [AGENT_SYSTEM.md](../../AGENT_SYSTEM.md).

**Removed** (merged into `/settings` panel):
- ~~`/ai`~~ → Settings Panel > AI view
- ~~`/channel`~~ → Settings Panel > Channels view
- ~~`/command`~~ → Settings Panel > Commands view

### Registration (BuiltinSlashCommandRegistrar)

All built-in components are registered at `@PostConstruct`:

```
BuiltinSlashCommandRegistrar
├── processAndRegister(BuiltinCommands @Plugin name, …, dbPrefix "")      → slash commands
├── processAndRegister(BuiltinTextCommands @Plugin name, …, dbPrefix "") → text commands
├── agentToolRegistry.registerProvider(BuiltinAgentTools) → agent tools
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

Configured in `OpenApiConfig.java` with 3 security schemes. Note that these
describe the *API surface for non-browser clients*; the Vue SPA authenticates
with the encrypted session cookie and the `JwtAuthenticationFilter` rejects
`Authorization` headers outright.

| Scheme | Type | Description |
|--------|------|-------------|
| `Bearer` | HTTP Bearer | Session JWT from Discord OAuth callback (non-browser clients) |
| `DPoP` | API Key (Header) | DPoP proof token (RFC 9449) — use `DPoP <token>` in Authorization + proof in `DPoP` header |
| `AdminBearer` | HTTP Bearer | Admin JWT from the mutual authentication flow (non-browser clients) |

Swagger UI and `/v3/api-docs/**` are additionally gated by
`SwaggerAccessFilter`: set `SWAGGER_ACCESS_PROTECTED=false` to open them, or
have an admin call `POST /api/admin/swagger/authorize` to obtain the
`pudel-swagger-session` cookie. Query-parameter tokens
(`pudel.swagger.allow-query-token`) are off by default and expire after 30 s.

Toggle docs availability via environment: `SWAGGER_ENABLED=true/false` (default: `true`)

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
dpop_keys               # Browser session Ed25519 keypair + server-held
                        #   access_token / admin_token, thumbprint, expiry
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

## Plugin Database Migration API (NEW)

The `PluginDatabaseManager` (in `pudel-api`) provides a **schema-as-code, self-reconciling** layer for plugin developers. Instead of writing manual migrations, plugins define their data model via `@Entity` annotated classes with `@Column` annotations, and the manager handles schema creation and evolution automatically.

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

# JWT / DPoP (Ed25519 — used to sign session, admin and swagger tokens)
JWT_PRIVATE_KEY_PATH=./keys/pv.key
JWT_PUBLIC_KEY_PATH=./keys/pb.key
JWT_EXPIRATION=604800000

# Browser session cookie (AES-GCM; SESSION_KEY is hashed, SESSION_KEYFILE is generated)
SESSION_NAME=pudel_session
SESSION_KEY=
SESSION_KEYFILE=cookie.key

# Admin Authentication (admins sign challenges with their own RSA key)
PUDEL_ADMIN_INITIAL_OWNER=123456789012345678
PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH=./keys/owner_pb.key

# Browser origins allowed to send credentialed requests
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,http://localhost

# Swagger / OpenAPI
SWAGGER_ENABLED=true
SWAGGER_ACCESS_PROTECTED=true
```

---

## Authentication Architecture

Pudel is a **cookie-only Backend-for-Frontend**. The browser holds exactly one
credential — an AES-GCM encrypted, `HttpOnly`, `Secure`, `SameSite=Strict`
cookie whose plaintext is an opaque database key id. There is no bearer token
in `localStorage`, no `Authorization` header, and no client-side proof signing.

Three layers stack on top of that cookie:

1. **Encrypted browser session** — `SessionController` + `SessionCookieService`
   mint and rotate the cookie; `dpop_keys` holds the Ed25519 keypair and the
   server-held JWT.
2. **DPoP (RFC 9449), server-minted** — `SessionAuthenticationService` signs a
   fresh, single-use `EdDSA` proof for every request and `DPoPService`
   validates it in the same request. The proof never leaves the process.
3. **Admin mutual authentication** — Ed25519 server challenge, RSA admin
   signature; the resulting AdminJWT is DPoP-bound and persisted in
   `dpop_keys.admin_token`.

All JWTs (user, admin, swagger) are signed `EdDSA` (Ed25519) with
`keys/pv.key` and verified with `keys/pb.key`.

### Security Filter Chain

```
SwaggerAccessFilter (runs before JwtAuthenticationFilter, only /swagger-ui*, /v3/api-docs/*)
    │
JwtAuthenticationFilter  (OncePerRequestFilter, inside the Spring Security chain)
    │
    ├── Authorization header present (any scheme)?
    │      └── 401 { "error": "invalid_token",
    │                  "error_description": "Authorization headers are not accepted;
    │                                          use the encrypted session cookie" }
    │
    ├── SessionCookieService.readKeyId(request)
    │      ├── AES-GCM decrypt (v1 envelope: { keyId, exp })
    │      └── no cookie / bad ciphertext / expired envelope → unauthorized
    │
    ├── DPoPKeyManager.findActiveSession(keyId)
    │      └── row must have is_active = true AND expires_at > now()
    │
    ├── JwtUtil.validateToken(row.access_token)
    │      └── EdDSA signature + exp verified against keys/pb.key
    │
    ├── SessionAuthenticationService.signInternalProof(keyId, method, uri, token)
    │      └── payload { jti, htm, htu, iat, ath }, header { typ, alg: EdDSA, jwk }
    │
    ├── DPoPService.validateProofForResource(proof, method, uri, token, keyId)
    │      ├── reconstruct PublicKey from the stored OKP JWK
    │      ├── verify EdDSA signature; reject unless alg == "EdDSA"
    │      ├── htm == request method
    │      ├── htu == host + port + path (scheme ignored, reverse-proxy safe)
    │      ├── |now - iat| <= 60_000 ms
    │      ├── jti single-use (per-keyId ledger, expired entries purged)
    │      └── ath == base64url(SHA-256(accessToken))
    │
    ├── cnf.jkt of the access token == proof thumbprint
    ├── token subject == dpop_keys.user_id
    │
    └── set Authentication(discordUserId, [DPOP_VERIFIED])
        request.setAttribute("pudel.session.keyId", keyId)
        → 401 { "error": "invalid_session", "error_description": <reason> } on any failure
```

Paths excluded from the filter (`JwtAuthenticationFilter.shouldNotFilter`):
`/api/session/**`, `/api/auth/discord/**`, `/api/auth/refresh`,
`/api/auth/logout`, `/api/bot/**`, `/ws/admin/**`,
`/api/admin/logs/stream` (endpoint removed — see below), the public
`GET /api/plugins*` reads, and
`/api/dpop/**`.

`SecurityConfiguration` is stateless (`SessionCreationPolicy.STATELESS`), CSRF
is disabled (no ambient cookie-authenticated state-changing form surface),
CORS allows credentials from `pudel.cors.allowed-origins` and only the
`Content-Type` request header.

### Session Bootstrap

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/session/bootstrap` | GET | Reuse the active cookie session or create a new anonymous key; returns `{ ready, expiresAt, maxAgeSeconds }` and no key id or token |
| `/api/session/rotate` | POST | Force a fresh Ed25519 key + cookie; tokens bound to the old key id stop working |
| `/api/auth/me` | GET | Restore the user after a page refresh using only the cookie |

Cookie attributes: `HttpOnly`, `Secure`, `SameSite=Strict`, `path=/`,
`Max-Age = pudel.jwt.expiration / 1000`. The AES key comes from
`pudel.session.cookie-secret` (SHA-256 of the passphrase) or a generated
32-byte `pudel.session.cookie-key-path` file.

### User Authentication (Discord OAuth + DPoP)

```
1. GET  /api/session/bootstrap          → encrypted cookie
2. User authorises on Discord
3. POST /api/auth/discord/callback { code, redirectUri }
       → AuthService.handleOAuthCallback(code, browserKeyId)
       → JwtUtil.generateDPoPBoundToken(userId, { username }, thumbprint)
       → DPoPKeyManager.bindSessionKey(browserKeyId, userId, jwt)
       → 200 { accessToken: null, user: {...}, tokenType: "COOKIE" }
4. POST /api/auth/refresh               → re-mint the server-held token
                                         (refreshes the Discord token if it
                                         expires within 300s)
5. POST /api/auth/logout                → dpop_keys.is_active = false, cookie cleared
```

Every later request carries only the cookie; the filter signs and validates the
DPoP proof internally.

See: [AuthFlow.mermaid](./AuthFlow.mermaid)

### DPoP (Demonstrating Proof-of-Possession) — RFC 9449

Tokens are cryptographically bound to the browser's Ed25519 key, so an
intercepted token is useless without the matching private key — which never
leaves the server.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DPoP LIFECYCLE (server-minted proofs)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. Key material                                                            │
│  ──────────────                                                             │
│  DPoPKeyManager.generateAndStoreKeyPair()                                    │
│    KeyPairGenerator("Ed25519")                                              │
│    public JWK  -> dpop_keys.public_key_jwk                                   │
│    private JWK -> dpop_keys.private_key_jwk   (server only, never exposed)   │
│    thumbprint  -> dpop_keys.public_key_thumbprint  (RFC 7638)               │
│    token       -> dpop_keys.access_token / admin_token                      │
│                                                                             │
│  2. Request-bound proof (per HTTP request)                                  │
│  ────────────────────────────────────────                                    │
│  SessionAuthenticationService.signInternalProof(keyId, method, uri, token)  │
│    payload { jti: UUID, htm: METHOD, htu: requestURL,                        │
│              iat: epochSecond,                                              │
│              ath: base64url(SHA-256(token)) }                               │
│    header  { typ: "dpop+jwt", alg: "EdDSA", jwk: publicJwk }                │
│    signed with the stored Ed25519 private key (EdDSA)                       │
│                                                                             │
│  3. Validation — DPoPService.validateProofForResource                       │
│  ──────────────────────────────────────────────────────                      │
│    ✓ EdDSA signature over the stored public key                             │
│    ✓ header alg is exactly "EdDSA"                                          │
│    ✓ htm matches the request method                                         │
│    ✓ htu matches host + port + path (scheme dropped for proxies)            │
│    ✓ |iat - now| <= 60 s                                                    │
│    ✓ jti unused: usedJtis.putIfAbsent(keyId + ":" + jti, iat + 60s)         │
│    ✓ ath == base64url(SHA-256(accessToken))                                 │
│    → returns the stored thumbprint                                          │
│                                                                             │
│  4. Binding                                                                 │
│  ──────────                                                                 │
│    cnf.jkt of the access token == the proof thumbprint                      │
│    token sub == dpop_keys.user_id                                           │
│    → Authentication(discordUserId, [DPOP_VERIFIED])                         │
│                                                                             │
│  5. Revocation                                                              │
│  ──────────────                                                             │
│  Logout / rotation set is_active = false on the key row.                    │
│  There is no in-memory tokenBindings map.                                   │
│                                                                             │
│  Implementation                                                             │
│  ├── DPoPKeyManager.java       Ed25519 keygen, persistence, proof signing   │
│  ├── DPoPService.java          proof verification, single-use jti ledger     │
│  ├── SessionAuthenticationService.java  cookie → proof mint → validate       │
│  ├── DPoPController.java       legacy key management endpoints (see below)  │
│  └── JwtAuthenticationFilter.java       rejects Authorization headers        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

The `/api/dpop/*` endpoints (`/key`, `/public-key`, `/sign`, `/thumbprint`,
`/key` DELETE, `/keys` DELETE) remain for compatibility and are excluded from
the authentication filter. They are not part of the browser request path: the
Vue client never calls them (`createDPoPProof()` is a no-op stub) and the
private key never leaves the server.

See: [DPoPFlow.mermaid](./DPoPFlow.mermaid)

### Admin Authentication (Mutual)

The server proves its identity with Ed25519; each admin proves theirs with
their own RSA keypair. The admin's private key is used in the browser and never
transmitted.

```
1. Discord OAuth login (cookie session required)
2. GET  /api/admin/check             → whitelist / enabled / hasPublicKey
3. GET  /api/admin/challenge         → { challengeId, nonce, timestamp, expiry, signature }
                                      signature = EdDSA JWT over the nonce,
                                      sub = pudel-admin-challenge, TTL 60 s
4. (optional) GET /api/admin/public-key → verify Pudel's signature (algorithm EdDSA)
5. Admin signs the nonce: SHA256withRSA(nonce, adminPrivateKey), Base64
6. POST /api/admin/auth/mutual { challengeId, signature }
      → challenge must exist and not be expired (single use)
      → admin_whitelist lookup by dpop_keys.user_id; entry enabled + public key present
      → RSA verify
      → AdminJWT = generateDPoPBoundToken("pudel-admin-session", claims, thumbprint)
        claims: sessionId, discordUserId, discordUsername, adminRole,
                canModify, canManageAdmins   · TTL 1 hour
      → stored in dpop_keys.admin_token; NOT returned in the response body
      → Set-Cookie pudel-swagger-session (HttpOnly, SameSite=Lax, 1 h) for Swagger UI
7. Admin endpoints resolve the session from dpop_keys.admin_token for the
   current browser key (validateAdminSession); the Authorization header is ignored
8. Live logs: native WebSocket /ws/admin/logs, handshake authenticated by
   AdminLogHandshakeInterceptor (cookie → admin_token → sub/expiry), restricted
   to pudel.cors.allowed-origins
9. POST /api/admin/logout → dpop_keys.admin_token = NULL (browser session survives)
```

The legacy OAuth admin login endpoints were **removed**. They remain
permit-listed in `SecurityConfiguration` for backward compatibility but have no
controller mapping, so requests to them return 404 (not 410).

See: [AdminMutualAuth.mermaid](./AdminMutualAuth.mermaid)

### Token Types

All server-issued tokens are EdDSA-signed. None of them are handed to the SPA
as a usable credential.

| Token | Subject | Duration | Storage | Binding | Purpose |
|-------|---------|----------|---------|---------|---------|
| Session access JWT | `{discordUserId}` | 7 days (`JWT_EXPIRATION`) | `dpop_keys.access_token` | `cnf.jkt` = session thumbprint | Server-side user identity per cookie |
| Admin JWT | `pudel-admin-session` | 1 hour | `dpop_keys.admin_token` | `cnf.jkt` = session thumbprint | Admin panel authorisation |
| Swagger session JWT | `pudel-swagger-session` | 1 hour | `pudel-swagger-session` cookie | SameSite=Lax cookie | Swagger UI / OpenAPI docs |
| Swagger query token | `pudel-swagger-query` | 30 seconds | Response body (`?swaggerToken=`) | Opt-in only | Non-browser clients; off by default |
| Admin challenge JWT | `pudel-admin-challenge` | 60 seconds | In-memory `pendingChallenges` | — | Server identity proof |

### Admin Roles

| Role | Permissions |
|------|-------------|
| OWNER | Full access + manage admin whitelist |
| ADMIN | Plugin management, settings |
| MODERATOR | View-only access |

---

*Last updated: 2026-07-10 — schema is now defined in Java (schema-as-code / self-reconciling); `init.sql` removed.*


