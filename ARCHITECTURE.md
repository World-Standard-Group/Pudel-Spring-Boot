# Pudel Architecture v1.0.0

This document describes the complete architecture of Pudel Discord Bot.

---

## Table of Contents

- [System Overview](#system-overview)
- [Module Structure](#module-structure)
- [Data Flow](#data-flow)
- [Plugin System](#plugin-system)
- [Brain Architecture](#brain-architecture)
- [Database Schema](#database-schema)
- [Configuration](#configuration)

---

## System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                             PUDEL DISCORD BOT v1.0.0                         │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐      ┌─────────────────────┐      ┌──────────────┐  │
│  │    Vue Frontend     │◄────►│   Spring Boot API   │◄────►│  PostgreSQL  │  │
│  │   (Dashboard/Wiki)  │      │    (pudel-core)     │      │  + pgvector  │  │
│  └─────────────────────┘      └──────────┬──────────┘      └──────────────┘  │
│                                          │                                   │
│                               ┌──────────┴──────────┐                        │
│                               │                     │                        │
│                    ┌──────────▼─────────┐ ┌────────▼────────┐                │
│                    │   Discord (JDA 6)  │ │   Ollama LLM    │                │
│                    │   Gateway + REST   │ │  (Local Model)  │                │
│                    └────────────────────┘ └─────────────────┘                │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │                         Plugin System                                 │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐  │   │
│  │  │ Plugin1 │ │ Plugin2 │ │ Plugin3 │ │  ...    │ │ Default Plugins │  │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Structure

```
pudel/
├── pudel-api/          # Plugin Development Kit (MIT License)
│   ├── PudelPlugin.java         # Main plugin interface
│   ├── PluginContext.java       # Runtime context for plugins
│   ├── PluginInfo.java          # Plugin metadata
│   ├── SimplePlugin.java        # Annotation-based plugin helper
│   ├── command/                 # Command handling API
│   ├── event/                   # Event listener API
│   ├── audio/                   # Voice/Audio API (DAVE support)
│
├── pudel-model/        # AI/Brain Module
│   ├── PudelModelService.java   # Main model orchestration
│   ├── OllamaClient.java        # Ollama LLM client
│   ├── OllamaEmbeddingService.java # Ollama embeddings
│   ├── DiscordSyntaxProcessor.java # Discord syntax handling
│   ├── analyzer/                # Text analysis
│   └── agent/                   # Agent data access
│
├── pudel-core/         # Main Bot Application (AGPL-3.0)
│   ├── Pudel.java               # Entry point
│   ├── bootstrap/               # Startup runners
│   ├── brain/                   # Pudel's brain logic
│   │   ├── PudelBrain.java      # Central intelligence
│   │   ├── memory/              # Memory management
│   │   ├── personality/         # Personality engine
│   │   └── response/            # Response generation
│   ├── command/                 # Built-in commands
│   ├── config/                  # Configuration classes
│   ├── controller/              # REST API endpoints
│   ├── discord/                 # JDA event handling
│   ├── dto/                     # Data transfer objects
│   ├── entity/                  # JPA entities
│   ├── event/                   # Event dispatching
│   ├── plugin/                  # Plugin loader/manager
│   ├── repository/              # Data access
│   ├── service/                 # Business logic
│   └── util/                    # Utilities
│
└── database/           # SQL Scripts
    ├── init.sql                 # Initial schema
    └── migrations/              # Version migrations
```

---

## Data Flow

### 1. Discord Message Flow

```
Discord User                    Pudel Bot
    │                              │
    │ ──── Message ──────────────► │
    │                              │
    │                    ┌─────────┴───────────┐
    │                    │ DiscordEventListener│
    │                    └─────────┬───────────┘
    │                              │
    │                    ┌─────────▼──────────┐
    │                    │ Is it a command?   │
    │                    └─────────┬──────────┘
    │                       yes/   │   \no
    │                       ┌──────┴──────┐
    │               ┌───────▼──────┐  ┌───▼────────────┐
    │               │ CommandExec  │  │ ChatbotService │
    │               │ Service      │  │ (AI Response)  │
    │               └──────┬───────┘  └───────┬────────┘
    │                      │                  │
    │               ┌──────▼──────┐   ┌───────▼────────┐
    │               │ Built-in or │   │ PudelBrain     │
    │               │ Plugin Cmd  │   │ (LangChain4j)  │
    │               └──────┬──────┘   └───────┬────────┘
    │                      │                  │
    │                      └────────┬─────────┘
    │                               │
    │ ◄──── Response ───────────────│
    │                               │
```

### 2. API Request Flow

```
Vue Frontend                Spring Boot                 Database
    │                           │                          │
    │ ── GET /api/guilds ─────► │                          │
    │    (JWT Token)            │                          │
    │                    ┌──────┴──────┐                   │
    │                    │ JWT Filter  │                   │
    │                    │ Validate    │                   │
    │                    └──────┬──────┘                   │
    │                           │                          │
    │                    ┌──────▼──────┐                   │
    │                    │ Controller  │                   │
    │                    └──────┬──────┘                   │
    │                           │                          │
    │                    ┌──────▼──────┐                   │
    │                    │ Service     │ ── Query ────────►│
    │                    └──────┬──────┘                   │
    │                           │ ◄─────── Result ─────────│
    │                           │                          │
    │ ◄─── JSON Response ───────│                          │
    │                           │                          │
```

---

## Plugin System

### Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                     Plugin Lifecycle                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [JAR File] ──load──► [initialize()] ──► [LOADED]          │
│                                              │              │
│                                     enable   │   disable    │
│                                       ▼      ▲              │
│                                   [onEnable()]              │
│                                       │                     │
│                                       ▼                     │
│                                  [ENABLED]                  │
│                                       │                     │
│                                       ▼                     │
│                                 [onDisable()]               │
│                                       │                     │
│                                       ▼                     │
│                                  [DISABLED]                 │
│                                       │                     │
│                                       ▼                     │
│   [shutdown()] ◄──unload──────  [shutdown()]                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Hot-Reload

```
┌─────────────────────────────────────────────────────────────┐
│                  Plugin Hot-Reload System                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PluginWatcherService                                       │
│       │                                                     │
│       │ (every 60 seconds)                                  │
│       ▼                                                     │
│  ┌───────────────┐                                          │
│  │ Scan /plugins │                                          │
│  │ Compute SHA256│                                          │
│  └──────┬────────┘                                          │
│         │                                                   │
│         ├─── New JAR ──► Copy to temp ──► Load plugin       │
│         │                                                   │
│         ├─── Hash changed (disabled) ──► Apply update now   │
│         │                                                   │
│         └─── Hash changed (enabled) ──► Queue update        │
│                                          │                  │
│                                          ▼                  │
│                                    Warn every 1 min         │
│                                    until disable/restart    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Brain Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PudelBrain                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Input: User Message                                                    │
│          │                                                              │
│          ▼                                                              │
│  ┌─────────────────────┐                                                │
│  │ TextAnalyzerService │ ◄──── LangChain4j + Ollama                     │
│  │ (Intent, Sentiment, │                                                │
│  │  Entities, Language)│                                                │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │   MemoryManager     │───►│   Memory Embeddings │ (pgvector)          │
│  │ (Retrieve context)  │    │   (Semantic Search) │                     │
│  └─────────┬───────────┘    └─────────────────────┘                     │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐                                                │
│  │  PersonalityEngine  │ ◄──── Biography, Personality, Preferences      │
│  │ (Apply guild traits)│                                                │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │ ResponseGenerator   │───►│   Ollama LLM        │                     │
│  │ (Build prompt)      │    │   (Generate reply)  │                     │
│  └─────────┬───────────┘    └─────────────────────┘                     │
│            │                                                            │
│            ▼                                                            │
│  Output: Bot Response                                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema

### Public Schema (Main Tables)

```sql
┌─────────────────────────────────────────────────────────────┐
│                     PUBLIC SCHEMA                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  users ──────────────── user_guilds ──────────────── guilds │
│  │                           │                          │   │
│  │                           │                          │   │
│  └──────── subscriptions ────┘                          │   │
│                                                         │   │
│                              guild_settings ────────────┘   │
│                                                             │
│  plugin_metadata                                            │
│                                                             │
│  market_plugins                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Per-Guild Schema

```sql
┌─────────────────────────────────────────────────────────────┐
│               SCHEMA: guild_{guild_id}                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  dialogue_history ─────── Stores conversation history       │
│  │                        (user_id, channel_id, messages)   │
│  │                                                          │
│  user_preferences ─────── Per-user settings in this guild   │
│  │                        (preferred_name, custom_settings) │
│  │                                                          │
│  memory ───────────────── Key-value memory storage          │
│  │                        (key, value, category)            │
│  │                                                          │
│  memory_embeddings ────── Vector embeddings (pgvector)      │
│                           (embedding vector, memory_id)     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Configuration

### Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `application.yml` | `pudel-core/src/main/resources/` | Main bot config |
| `subscription-tiers.yml` | `pudel-core/src/main/resources/` | Tier limits |
| `model-config.yml` | `pudel-model/src/main/resources/` | LLM settings |

### Key Configuration Sections

```yaml
pudel:
  discord:
    token: ${DISCORD_BOT_TOKEN}
    prefix: "!"
    
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: phi3:mini
    
  subscription:
    tiers:
      FREE: { dialogueLimit: 5000, memoryLimit: 500 }
      TIER_1: { dialogueLimit: 7500, memoryLimit: 750 }
      TIER_2: { dialogueLimit: 10000, memoryLimit: 1000 }
      
  chatbot:
    triggers:
      onMention: true
      onDirectMessage: true
      onReplyToBot: true
```

---

## Security

### Authentication Flow

```
┌────────────────────────────────────────────────────────────────┐
│                   Discord OAuth 2.0 Flow                       │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. User clicks "Login with Discord"                           │
│          │                                                     │
│          ▼                                                     │
│  2. Redirect to Discord OAuth                                  │
│          │                                                     │
│          ▼                                                     │
│  3. User authorizes                                            │
│          │                                                     │
│          ▼                                                     │
│  4. Redirect back with auth code                               │
│          │                                                     │
│          ▼                                                     │
│  5. Exchange code for Discord tokens                           │
│          │                                                     │
│          ▼                                                     │
│  6. Fetch user info from Discord                               │
│          │                                                     │
│          ▼                                                     │
│  7. Generate Pudel JWT token                                   │
│          │                                                     │
│          ▼                                                     │
│  8. Return JWT to frontend                                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Permission Checking

- **Guild Admin**: Required for guild settings management
- **JWT Token**: Required for all authenticated API endpoints
- **Bot Permissions**: Configured per-guild for Discord actions

---

## Version Info

| Component       | Version        | License    |
|-----------------|----------------|------------|
| Pudel Core      | 1.0.0          | AGPL-3.0   |
| Pudel API (PDK) | 1.0.0          | MIT        |
| Pudel Model     | 1.0.0          | AGPL-3.0   |
| JDA             | 6.3.0 / Latest | Apache-2.0 |
| Spring Boot     | 4.0.1 / Latest | Apache-2.0 |
| LangChain4j     | 1.10.0 / Latest | Apache-2.0 |

---

*Last Updated: January 2026*
*Version: 1.0.0*
