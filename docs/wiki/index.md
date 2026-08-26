# Pudel Documentation

Welcome to the official Pudel documentation! Pudel is an AI-powered Discord bot framework with an annotation-based plugin system.

**Current Version: 2.4.0**

---

## What's New in v2.4.0

### ContextMenu command improvement
- `ContextMenu` command became more likely to `SlashCommand` and ready to be integrated
- Fix bug for embedding api of Ollama

## What's New in v2.3.2

### 🗄️ Schema defined in Java (schema-as-code)
- **No `init.sql` / manual migration step.** The entire per-guild / per-user schema is declared in `SchemaManagementService` and reconciled against the live database on every boot (creates missing tables/columns/indexes, repairs existing schemas). See [Schema Management](./../SCHEMA_MANAGEMENT).
- **Single source of truth.** Removed the scattered `CREATE TABLE IF NOT EXISTS` calls (`PassiveContextProcessor.ensurePassiveContextTable`, `MemoryEmbeddingService.createGuildEmbeddingTables`) which previously caused divergent/missing schemas.
- **`passive_context.message_id` is now `UNIQUE`** (auto-reconciled), which fixes the `INSERT … ON CONFLICT (message_id)` upsert that was failing with `bad SQL grammar`.
- **`forwarded_messages` table** is now created by the reconciler (previously only its indexes existed, causing `relation does not exist`).
- **pgvector embedding tables** (`memory_embeddings`, `dialogue_embeddings`) are auto-created per guild/user when pgvector is available.

---

## What's New in v2.3.1

### 🧠 PudelBrain v2 — Reworked Intelligence
- **Ollama completion-focused** — Local LLM with streaming support
- **Async Discord handling** — `sendTyping()` + `sendMessage().queue()` (no blocking)
- **Passive context collection** — Observes messages with entity extraction, message_id tracking
- **Dialogue history** — Per-user history with `respond_to` message ID tracking
- **Dual tool system** — MCP tools (context access) + Agent tools (data management)
- **Discord Markdown output** — Proper formatting for mentions, channels, emojis, code blocks
- **Attachment support** — Read text files, reply with images/videos

### 🔧 Key Architecture Changes
- `pudel-model` module deprecated (marked for removal)
- New `PassiveContextProcessor` with `ConcurrentLinkedQueue` + batch processing
- New `DialogueHistoryManager` with `respond_to` and `attachment_urls` columns
- New `EntityExtractor` for users, channels, roles, emojis, URLs, attachments
- New `BuiltinMcpToolRegistrar` — 5 MCP tools for brain context access
- New `PudelAgentService.processWithTools()` — unified tool-calling iteration loop
- Database migrations V10 (`respond_to`, `attachment_urls`) and V10_pudel_brain_v2 (`passive_context`, `forwarded_messages`)

### ⚠️ Breaking Changes from v1.x
- `PudelPlugin` interface is **deprecated**
- `SimplePlugin` class is **deprecated**
- Manual `syncCommands()` no longer required
- `pudel-model` module deprecated — use built-in brain instead

---

## Quick Links

| Guide | Description |
|-------|-------------|
| [🚀 Getting Started](./getting-started) | Quick setup guide |
| [🔌 Plugin Development](./plugin-development) | **NEW!** Annotation-based plugins |
| [📖 Commands](./commands) | Full command reference |
| [⚙️ Configuration](./configuration) | Configuration options |
| [🤖 Agent System](./agent) | AI data management |
| [🌐 REST API](./api) | API reference |
| [🎙️ Voice & DAVE](./voice-dave) | Voice features |
| [💳 Subscriptions](./subscription) | Tier system |
| [⚖️ Licensing](./licensing) | License guide |
| [🏠 Self-Hosting](./self-hosting) | Host your own |
| [📐 Schema Management](../SCHEMA_MANAGEMENT.md) | Schema-as-code / self-reconciling |
| [📋 Cheat Sheet](./cheatsheet) | Quick reference |
| [❓ FAQ](./faq) | Common questions |

---

## Features

### 🤖 AI-Powered Conversations
- Local LLM via Ollama (no cloud required)
- Per-guild personality customization
- Semantic memory with vector search (pgvector)
- Agent tools API for custom AI capabilities

### 🔌 Annotation-Based Plugin System
```java
@Plugin(name = "MyPlugin", version = "1.0.0")
public class MyPlugin {

    @SlashCommand(name = "hello", description = "Say hello")
    public void hello(SlashCommandInteractionEvent event) {
        event.reply("Hello! 👋").queue();
    }

    @OnShutdown
    public boolean shutdown(PluginContext ctx) {
        return true; // Graceful cleanup
    }
}
```

### 🛡️ Enterprise Ready
- Per-guild PostgreSQL schemas
- RSA JWT authentication
- REST API for integrations
- Docker deployment with hot-reload

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Discord                              │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      JDA 6                                  │
│            (Discord API + DAVE Protocol)                    │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                    Pudel Core                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Annotation-Based Plugin System             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │ ClassLoader │  │ Annotation  │  │ Plugin      │   │   │
│  │  │ (JAR + hot) │  │ Processor   │  │ Service     │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │  Built-in Cmds   │  │   AI Brain       │                 │
│  │  @SlashCommand   │  │ (LLM + Memory)   │                 │
│  └──────────────────┘  └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│  PostgreSQL + pgvector          Ollama (Local LLM)          │
└─────────────────────────────────────────────────────────────┘
```

---

## Module Structure

| Module | License | Purpose |
|--------|---------|---------|
| `pudel-api` | MIT | Plugin Development Kit |
| `pudel-core` | AGPLv3 + Exception | Bot core |

The **Plugin Exception** allows proprietary plugins using only `pudel-api` to interact with `pudel-core`.

---

## Quick Start

```bash
# Clone
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd Pudel-Spring-Boot

# Build
mvn clean package -DskipTests

# Configure
cp .env.example .env
# Edit .env with your Discord token

# Run
java -jar pudel-core/target/pudel-core-2.4.0.jar
```

See [Getting Started](./getting-started) for detailed setup.

---

*Last updated: 2026-07-24 for Pudel v2.4.0*
