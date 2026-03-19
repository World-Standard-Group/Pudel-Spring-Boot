# Pudel
![GitHub Release](https://img.shields.io/github/v/release/World-Standard-Group/Pudel-Spring-Boot?label=Release)
![Maven Central](https://img.shields.io/maven-central/v/group.worldstandard/pudel-api)
![Java](https://img.shields.io/badge/JDK-25-green)
![License](https://img.shields.io/github/license/World-Standard-Group/Pudel-Spring-Boot)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue.svg)](https://world-standard-group.github.io/Pudel-Spring-Boot/)

**Pudel** is a modular, AI-powered Discord bot framework built on **Java 25**, **Spring Boot 4**, and **JDA 6**, designed to operate as a long-running service (SaaS-style) with strong separation between core functionality and third-party plugins.

Pudel acts as a **personal maid/secretary** for Discord guilds — capable of natural conversation, intelligent command handling, moderation, and extensibility through a robust **annotation-based plugin system**.

---

## What's New in 2.0.0

### 🎉 Annotation-Based Plugin System
- **Spring Boot-like development** — Use `@Plugin`, `@SlashCommand`, `@TextCommand` annotations
- **Zero boilerplate** — Core handles registration, sync, and cleanup automatically
- **Lifecycle annotations** — `@OnEnable`, `@OnDisable`, `@OnShutdown` (with boolean return for graceful shutdown)
- **Hot-reload support** — File watcher detects plugin updates automatically

### ⚠️ Breaking Changes
- `PudelPlugin` interface is **deprecated** — Migrate to `@Plugin` annotation
- `SimplePlugin` class is **deprecated** — Use annotation-based approach
- Manual `syncCommands()` no longer required — Core syncs automatically

---

## Features

### 🤖 AI-Powered Conversations
- **Local LLM Integration** via Ollama (phi-3, gemma-2b, or any compatible model)
- **LangChain4j** for text analysis, intent detection, and sentiment analysis
- **Per-guild personality customization** — biography, preferences, dialogue style
- **Memory system** with vector embeddings (pgvector + IVFFlat)
- **Passive context tracking** — Pudel listens and remembers without requiring mentions
- **Agent Tools API** — Extend AI capabilities with custom tools

### 🔧 Annotation-Based Plugin System
- **Spring Boot-like annotations** — `@Plugin`, `@SlashCommand`, `@TextCommand`
- **Automatic command registration** — No manual `registerCommand()` calls
- **Automatic Discord sync** — Commands appear instantly (guild) or within 1 hour (global)
- **Hot-reload support** — Update plugins without restarting the bot
- **Graceful shutdown** — `@OnShutdown` returns boolean for cleanup control
- **Interaction handlers** — `@ButtonHandler`, `@ModalHandler`, `@SelectMenuHandler`

### 🛡️ Enterprise Features
- **Per-guild PostgreSQL schemas** for data isolation
- **Plugin database access** — Isolated storage per plugin
- **RSA JWT authentication** for secure API access
- **REST API** for external integrations
- **Docker-ready** deployment with volume support for plugins

---

## Project Structure

```
pudel/
├── pudel-api/      # Plugin Development Kit (MIT License)
├── pudel-core/     # Bot core (AGPLv3 + Plugin Exception)
├── pudel-model/    # AI/ML components (AGPLv3)
├── plugins/        # Plugin JARs (hot-loadable)
├── keys/           # RSA keys for JWT
└── database/       # SQL migrations
```

See [ARCHITECTURE.md](docs/flowchart/architecture/ARCHITECTURE.md) for detailed system design.

---

## Quick Start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 25+ |
| Maven | 3.9+ |
| PostgreSQL | 18+ with pgvector |
| Ollama | Latest (for AI features) |

### 1. Clone & Build

```bash
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd Pudel-Spring-Boot
mvn clean package -DskipTests
```

### 2. Database Setup

```bash
psql -U postgres
CREATE DATABASE pudel;
\c pudel
CREATE EXTENSION IF NOT EXISTS vector;
\q

psql -U postgres -d pudel -f database/init.sql
```

### 3. Configure

Create `.env` file in project root:

```env
# Discord
DISCORD_BOT_TOKEN=your_discord_bot_token

# Database
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=pudel
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password

# AI (Optional)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=phi3:mini
OLLAMA_ENABLED=true

# JWT Keys
JWT_PRIVATE_KEY_PATH=./keys/pv.key
JWT_PUBLIC_KEY_PATH=./keys/pb.key
```

### 4. Run

```bash
# Start Ollama (for AI features)
ollama run phi3:mini

# Start Pudel
java -jar pudel-core/target/pudel-core-2.2.1.jar
```

---

## Built-in Commands

All built-in commands follow the same `@Plugin` annotation pattern as plugins.

### Slash Commands (`pudel-core`)

| Command | Description |
|---------|-------------|
| `/settings` | Components V2 interactive panel — General, AI, Channels, Commands, Plugins views |

> `/ai`, `/channel`, `/command` were merged into the `/settings` panel.

### Text Commands (`pudel-core-text`)

| Command | Description |
|---------|-------------|
| `!ping` | Bot latency with rich embed |
| `!help` | Paginated command listing (⏮ ◀ ▶ ⏭ navigation, 8 per page) |

### Agent Tools (`pudel-core-tools`)

14 AI agent tools registered via `AgentToolRegistry` — same standard as plugin tools. See [Agent System](docs/AGENT_SYSTEM.md).

---

## Plugin Development

### Add Dependency

**Maven Central:**
```xml
<dependency>
    <groupId>group.worldstandard</groupId>
    <artifactId>pudel-api</artifactId>
    <version>2.2.1</version>
    <scope>provided</scope>
</dependency>
```

**GitHub Packages:**
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/World-Standard-Group/Pudel-Spring-Boot</url>
    </repository>
</repositories>
```

### Simple Plugin Example (v2.0.0)

```java
@Plugin(
    name = "GreetPlugin",
    version = "1.0.0",
    author = "YourName",
    description = "A simple greeting plugin"
)
public class GreetPlugin {

    // Slash command - registered automatically!
    @SlashCommand(name = "greet", description = "Greet someone")
    public void greet(SlashCommandInteractionEvent event) {
        event.reply("Hello! 👋").queue();
    }

    // Text command with aliases
    @TextCommand(value = "hello", aliases = {"hi", "hey"})
    public void hello(CommandContext ctx) {
        ctx.reply("Hello, " + ctx.getUser().getName() + "!");
    }

    // Button handler
    @ButtonHandler("greet:wave")
    public void handleWave(ButtonInteractionEvent event) {
        event.reply("👋").setEphemeral(true).queue();
    }

    // Lifecycle hooks
    @OnEnable
    public void onEnable(PluginContext ctx) {
        ctx.log("info", "GreetPlugin enabled!");
    }

    @OnShutdown
    public boolean shutdown(PluginContext ctx) {
        // Cleanup resources
        return true; // Return false to force-kill
    }
}
```

### Key Annotations

| Annotation | Target | Description |
|------------|--------|-------------|
| `@Plugin` | Class | Marks class as a plugin (required) |
| `@SlashCommand` | Method | Slash command handler |
| `@TextCommand` | Method | Text command handler |
| `@ButtonHandler` | Method | Button click handler |
| `@ModalHandler` | Method | Modal submission handler |
| `@SelectMenuHandler` | Method | Select menu handler |
| `@OnEnable` | Method | Called when plugin enabled |
| `@OnDisable` | Method | Called when plugin disabled |
| `@OnShutdown` | Method | Called when plugin unloaded (returns boolean) |

### Migration from v1.x

**Before (Deprecated):**
```java
public class MyPlugin implements PudelPlugin {
    @Override
    public PluginInfo getPluginInfo() {
        return new PluginInfo("MyPlugin", "1.0.0", "Author", "Desc");
    }
    
    @Override
    public void onEnable(PluginContext ctx) {
        manager.registerSlashCommand(...);
        manager.syncCommands(); // Easy to forget!
    }
}
```

**After (v2.0.0):**
```java
@Plugin(name = "MyPlugin", version = "1.0.0", author = "Author", description = "Desc")
public class MyPlugin {

    @SlashCommand(name = "hello", description = "Say hello")
    public void hello(SlashCommandInteractionEvent event) {
        event.reply("Hello!").queue();
    }
}
```

See [Plugin Development Guide](https://worldstandard.group/wiki) for comprehensive documentation.

---

### Plugin Hot-Reload in Docker

Mount the plugins directory as a volume. When you copy a new JAR to `./plugins/`, Pudel automatically:
1. Detects the new/modified plugin
2. Loads or reloads it
3. Syncs slash commands to Discord

---

## Documentation

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.md](docs/flowchart/architecture/ARCHITECTURE.md) | System architecture |
| [Plugin Development](docs/vuedocs/plugin-development.md) | Complete plugin guide |
| [API Specification](docs/API_SPECIFICATION.md) | REST API reference |
| [Agent System](docs/AGENT_SYSTEM.md) | AI agent tools |

---

## Requirements

### Minimum
- **Java**: 25+
- **Memory**: 512MB RAM (without AI)
- **Storage**: 100MB (core only)
- **Database**: PostgreSQL 18+ with pgvector

### Recommended (with AI)
- **Memory**: 4GB+ RAM
- **GPU**: Optional, for faster Ollama inference
- **Ollama Model**: phi3:mini (2.3GB) or gemma:2b (1.4GB)

---

## Legal

### License

| Module | License | Purpose |
|--------|---------|---------|
| `pudel-core` | AGPLv3 + Plugin Exception | Bot core |
| `pudel-api` | MIT | Plugin Development Kit |
| `pudel-model` | AGPLv3 | AI/ML components |

### Plugin Exception

Plugins that:
- Using only `pudel-api` to interact with `pudel-core`
- Do not include or modify `pudel-core` code
- Are loaded dynamically at runtime

Are **NOT considered derivative works** of Pudel. This allows proprietary and commercial plugins while keeping the core open source.

---

## Support

- **Discord**: [Pudel Support Server](https://discord.gg/pudel)
- **Issues**: [GitHub Issues](https://github.com/World-Standard-Group/Pudel-Spring-Boot/issues)
- **Documentation**: [Wiki](https://worldstandard.group/wiki)

---

## Status

**Version**: 2.2.1 (Stable)

**Note**: Only changes or bug fixes originating from the API qualify for a semantic version update.

---

## Author

© 2026 Napapon Kamanee (World Standard Group)

