# Pudel
![Maven Central (TBA)](https://img.shields.io/maven-central/v/{}label=Maven%20Central)
![Java](https://img.shields.io/badge/JDK-25-green)
![License](https://img.shields.io/github/license/World-Standard-Group/Pudel-Spring-Boot)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue.svg)](https://world-standard-group.github.io/Pudel-Spring-Boot/latest/)

**Pudel** is a modular, AI-powered Discord bot framework built on **Java 25**, **Spring Boot 4**, and **JDA 6**, designed to operate as a long-running service (SaaS-style) with strong separation between core functionality and third-party plugins.

Pudel acts as a **personal maid/secretary** for Discord guilds — capable of natural conversation, intelligent command handling, moderation, and extensibility through a robust plugin system.

---

## Features

### 🤖 AI-Powered Conversations
- **Local LLM Integration** via Ollama (phi-3, gemma-2b, or any compatible model)
- **LangChain4j** for text analysis, intent detection, and sentiment analysis
- **Per-guild personality customization** — biography, preferences, dialogue style
- **Memory system** with vector embeddings (pgvector + IVFFlat)
- **Passive context tracking** — Pudel listens and remembers without requiring mentions

### 🔧 Extensible Plugin System
- **Hot-loadable plugins** without bot restart
- **MIT-licensed Plugin API** (pudel-api) for commercial/proprietary plugins
- **Full JDA event access** for plugin developers
- **DAVE protocol support** for voice features (Discord E2EE)
- **Community plugin marketplace** built-in

### 🛡️ Enterprise Features
- **Per-guild PostgreSQL schemas** for data isolation
- **Subscription tiers** with configurable capacity limits
- **Discord OAuth integration** for web dashboard
- **REST API** for external integrations
- **Docker-ready** deployment

### 🌐 Web Dashboard (Vue 4 + Tailwind)
- Bot statistics and health monitoring
- Guild settings management
- Plugin marketplace
- Wiki documentation
- Discord OAuth login

---

## Project Structure

```
pudel/
├── LICENSE                 # AGPLv3 (core)
├── PLUGIN_EXCEPTION        # Plugin exception for AGPL
├── NOTICE                  # Licensing overview
├── TERMS_OF_SERVICE.md     # Terms for official instance
├── PRIVACY_POLICY.md       # Privacy policy for users
│
├── pudel-core/             # AGPLv3 + Plugin Exception
│   └── src/main/java/worldstandard/group/pudel/core/
│       ├── brain/          # AI intelligence system
│       ├── command/        # Built-in commands
│       ├── config/         # Spring configurations
│       ├── controller/     # REST API endpoints
│       ├── discord/        # JDA event handlers
│       ├── entity/         # JPA entities
│       ├── plugin/         # Plugin loader & manager
│       └── service/        # Business logic
│
├── pudel-api/              # MIT License - Plugin Development Kit
│   └── src/main/java/worldstandard/group/pudel/api/
│       ├── command/        # Command interfaces
│       ├── event/          # Event system
│       └── audio/          # DAVE audio interfaces
│
├── pudel-model/            # AI model integration
│   └── src/main/java/worldstandard/group/pudel/model/
│       ├── embedding/      # Vector embeddings (ONNX)
│       ├── llm/            # Ollama client
│       └── analyzer/       # Text analysis (LangChain4j)
│
├── plugins/                # Runtime-loaded plugin JARs
├── database/               # SQL migrations
└── docs/                   # Additional documentation
```

---

## Quick Start

### Prerequisites

| Requirement | Version                     |
|------------|-----------------------------|
| Java | 25+                         |
| Maven | 3.8+                        |
| PostgreSQL | 18+ with pgvector extension |
| Ollama | Latest (for AI features)    |

### 1. Clone & Build

```bash
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd pudel
mvn clean package
```

### 2. Database Setup

```bash
# Create database with pgvector
psql -U postgres
CREATE DATABASE pudel;
\c pudel
CREATE EXTENSION IF NOT EXISTS vector;
\q

# Run migrations
psql -U postgres -d pudel -f database/init.sql
```

### 3. Configure

Create `pudel-core/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pudel
    username: postgres
    password: your_password

pudel:
  discord:
    token: YOUR_DISCORD_BOT_TOKEN
    prefix: "!"
  
  ollama:
    base-url: http://localhost:11434
    model: phi3:mini
    enabled: true
```

### 4. Run

```bash
# Start Ollama (for AI features)
ollama run phi3:mini

# Start Pudel
java -jar pudel-core/target/pudel-core-1.0.0.jar --spring.profiles.active=local
```

### 5. Dashboard (Optional)

```bash
cd vue
npm install
npm run dev
```

Visit `http://localhost:5173` for the web dashboard.

---

## Built-in Commands

| Command | Description |
|---------|-------------|
| `!help [command]` | Show all commands or info about specific command |
| `!settings` | Interactive guild configuration wizard |
| `!ai on/off` | Enable/disable AI chatbot features |
| `!ai biography <text>` | Set Pudel's biography for this guild |
| `!ai personality <text>` | Set Pudel's personality traits |
| `!ai preferences <text>` | Set Pudel's preferences |
| `!ai dialogue <text>` | Set Pudel's dialogue style |
| `!ai systemprompt <text>` | Set custom system prompt prefix |
| `!listen <#channel>` | Set channel where Pudel responds |
| `!ignore <#channel>` | Ignore a channel completely |
| `!enable <command>` | Enable a disabled command |
| `!disable <command>` | Disable a command |

---

## Plugin Development

Plugins are built against the **MIT-licensed `pudel-api`** module:

```xml
<dependency>
    <groupId>worldstandard.group</groupId>
    <artifactId>pudel-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Simple Plugin Example

```java
public class MyPlugin extends PudelPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MyPlugin enabled!");
        
        registerCommand("greet", (event, args) -> {
            event.getChannel().sendMessage("Hello! 👋").queue();
        });
    }
}
```

### Plugin Types

| License | Requirements |
|---------|--------------|
| **Open Source** | Publish on marketplace with source link |
| **Proprietary** | Distribute privately, no source required |
| **Commercial** | Sell directly, plugin exception applies |

See [pudel-api/README.md](pudel-api/README.md) for comprehensive documentation.

---

## Documentation

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture overview |
| [API_SPECIFICATION.md](API_SPECIFICATION.md) | REST API reference |
| [pudel-api/README.md](pudel-api/README.md) | Plugin Development Kit guide |
| [docs/DAVE_PROTOCOL.md](docs/DAVE_PROTOCOL.md) | Voice encryption (DAVE) guide |
| [docs/SUBSCRIPTION_SYSTEM.md](docs/SUBSCRIPTION_SYSTEM.md) | Subscription tiers |
| [vue/public/docs/](vue/public/docs/) | User documentation (Wiki) |

---

## Requirements

### Minimum

- **Java**: 25+
- **Memory**: 512MB RAM (without Ollama)
- **Storage**: 100MB (core only)
- **Database**: PostgreSQL 14+ with pgvector

### Recommended (with AI)

- **Memory**: 4GB+ RAM
- **GPU**: Optional, for faster Ollama inference
- **Ollama Model**: phi3:mini (2.3GB) or gemma:2b (1.4GB)

---

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY pudel-core/target/pudel-core-1.0.0.jar app.jar
COPY plugins/ plugins/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Systemd

```ini
[Unit]
Description=Pudel Discord Bot
After=network.target postgresql.service

[Service]
Type=simple
User=pudel
WorkingDirectory=/opt/pudel
ExecStart=/usr/bin/java -jar pudel-core-1.0.0.jar
Restart=always
Environment=DISCORD_BOT_TOKEN=your_token
Environment=SPRING_PROFILES_ACTIVE=production

[Install]
WantedBy=multi-user.target
```

---

## Legal

### License

Pudel uses a **dual-license model** with a **plugin exception**:

| Module | License | Purpose |
|--------|---------|---------|
| `pudel-core` | AGPLv3 + Plugin Exception | Bot core, network copyleft |
| `pudel-api` | MIT | Plugin Development Kit |
| `pudel-model` | AGPLv3 | AI/ML components |

### Plugin Exception

Plugins that:
- Only depend on `pudel-api` (MIT)
- Do not include or modify `pudel-core` code
- Are loaded dynamically at runtime

Are **NOT considered derivative works** of Pudel, even though they run in the same JVM.

This allows proprietary and commercial plugins while keeping the core fully open source.

### Official Instance

For users of the official Pudel instance:
- [Terms of Service](TERMS_OF_SERVICE.md)
- [Privacy Policy](PRIVACY_POLICY.md)

---

## Support

- **Discord**: [Pudel Support Server (TBA)](https://discord.gg/pudel)
- **Issues**: [GitHub Issues](https://github.com/worldstandard/pudel/issues)
- **Documentation**: [Wiki (TBA)]()

---

## Status

**Version**: 1.0.0 (Stable)

The plugin boundary and licensing model are considered **stable**. APIs are production-ready.

---

## Author

© 2025 Napapon Kamanee (World Standard Group)

---
