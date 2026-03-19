# Pudel Architecture v2.2.1

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
│                             PUDEL DISCORD BOT v2.2.1                          │
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
├── pudel-model/        # AI/Brain Module (AGPL-3.0)
│   ├── PudelModelService.java
│   ├── ollama/
│   ├── embedding/
│   ├── analyzer/
│   └── agent/
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
├── database/           # SQL Scripts
│   ├── init.sql
│   └── migrations/
└── keys/               # JWT + Admin RSA keys
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

### Built-in Commands (v2.2.1)

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
│       │              Emote Usage (btns)                                       │
│       ├── 📢 Channels ──► Log/Bot channel (EntitySelectMenu modal),           │
│       │                    Ignore/Unignore (EntitySelectMenu modal)           │
│       ├── 📝 Commands ──► Paginated toggle buttons per text command           │
│       └── 🧩 Plugins ──► Paginated toggle buttons per plugin                  │
│                           └─► syncGuildCommands() on toggle                   │
│                                                                               │
│  Pattern:                                                                     │
│  ├── SettingsSession (per-user, ConcurrentHashMap<userId, Session>)           │
│  ├── SettingsView enum: MAIN, GENERAL, AI, CHANNELS, COMMANDS, PLUGINS        │
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

## Brain Architecture

```
Input: User Message
    │
    ▼
TextAnalyzerService (LangChain4j + Ollama)
    ├── Intent Detection
    ├── Sentiment Analysis
    ├── Entity Extraction
    └── Language Detection
    │
    ├── [Agent intent?] ──► PudelAgentService → PluginToolAdapter → AgentToolRegistry
    │                                         (BuiltinAgentTools + Plugin Tools → Data Executor)
    │
    ▼
MemoryManager → pgvector Semantic Search → Relevant Context
    │
    ▼
PersonalityEngine (biography, personality, preferences, quirks from GuildSettings)
    │
    ▼
ResponseGenerator → Ollama LLM → Bot Response
```

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

```
public schema (shared)
├── users                   # Discord user profiles
├── guild_settings          # Per-guild config + disabled_plugins CSV
├── user_guilds             # User-guild membership
├── subscriptions           # Subscription tiers
├── plugin_metadata         # Loaded plugin info (name, version, jar_path)
├── plugin_kv_store         # Plugin key-value storage
├── plugin_database_registry # Plugin table registry
├── admin_whitelist         # Admin RSA public keys
├── market_plugins          # Plugin marketplace
└── bot_users               # Bot user records

guild_{id} schema (per-guild isolation)
├── conversation_history    # Chat history per channel
├── memory_embeddings       # pgvector semantic memory
├── agent_*                 # Agent-created tables
└── plugin_*                # Plugin-created tables
```

See: [DatabaseSchema.mermaid](./DatabaseSchema.mermaid)

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
OLLAMA_MODEL=phi3:mini

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

*Last updated: 2026-03-18 for Pudel v2.2.1*
