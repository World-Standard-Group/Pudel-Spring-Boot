# Frequently Asked Questions

Common questions about Pudel.

---

## General

### What is Pudel?

Pudel is an AI-powered Discord bot designed to act as your personal maid/secretary. It features:
- Intelligent conversation via local LLM (Ollama)
- Data management (Agent System)
- Extensible plugin architecture
- Per-guild customization

### Why is it called Pudel?

"Pudel" is German for "poodle" - a breed known for being intelligent, loyal, and helpful. Just like a well-trained poodle, Pudel aims to be a helpful companion in your Discord server.

### Is Pudel free?

The software is open source (AGPL + MIT for plugins). Hosting costs depend on your setup. The official instance may have subscription tiers.

---

## Setup & Installation

### What are the requirements?

| Component | Minimum | Recommended       |
|-----------|---------|-------------------|
| Java | 25+ | 25+               |
| RAM | 512MB | 4GB (with AI)     |
| PostgreSQL | 14+ | 18+ with pgvector |
| Ollama | Optional | Latest            |

### How do I get a Discord bot token?

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to Bot section
4. Create bot and copy token
5. Enable required intents (MESSAGE_CONTENT, etc.)

### Pudel won't start - what do I check?

1. **Java version**: Must be 25+
2. **Database**: PostgreSQL running with pgvector
3. **Token**: Valid Discord bot token in config
4. **Permissions**: Bot has required Discord permissions

### How do I update Pudel?

```bash
git pull
mvn clean package
# Restart the service
```

---

## AI & Chatbot

### Why isn't Pudel responding to messages?

Check these:
1. AI enabled: `/settings` > AI view > Toggle
2. Mentioned or in active channel
3. Ollama running (if using AI)
4. Channel not ignored: `/settings` > Channels view

### How do I change Pudel's personality?

Use the `/settings` command and navigate to the **AI view**:

1. Type `/settings` in Discord
2. Click **🤖 AI** button
3. Use the available controls:
   - **Nickname** — Modal input for custom bot name
   - **Language** — Modal input for language code (`en`, `th`, `ja`, etc.)
   - **Personality** — Modal for personality traits (long text)
   - **Biography** — Modal for backstory (long text)
   - **Response Length** — Buttons: Short / Medium / Long
   - **Formality** — Buttons: Casual / Balanced / Formal
   - **Emote Usage** — Buttons: None / Minimal / Moderate / Frequent

### What models does Pudel support?

Any Ollama-compatible model:
- **qwen3:8b** - Default (recommended)
- **phi3:mini** - Balanced
- **gemma:2b** - Lightweight
- **llama3.2:1b** - Minimal
- **mistral** - Higher quality
- Custom models via Ollama

### Can Pudel work without Ollama?

Yes, but with limited functionality:
- Template-based responses
- No intelligent conversation
- Commands still work
- Agent features limited

### Why are responses slow?

1. **First response**: Model loading (cold start)
2. **Hardware**: CPU inference is slower than GPU
3. **Model size**: Larger models = slower
4. **Context**: More history = longer processing

---

## Plugins

### How do I install a plugin?

1. Download the `.jar` file
2. Place in `plugins/` directory
3. Restart Pudel (or wait for hot-reload)

### Why isn't my plugin loading?

Check:
1. JAR in correct `plugins/` folder
2. `Plugin-Main` manifest entry correct
3. Plugin compiled for Java 25+
4. No dependency conflicts

### Can I make commercial plugins?

Yes! The Plugin Exception allows:
- Proprietary source code
- Commercial sales
- Any license you choose

See [Licensing](./licensing) for details.

### How do I publish to the marketplace?

1. Create source repository (for open source)
2. Use API: `POST /api/plugins/publish`
3. Or use web dashboard

---

## Voice & Audio

### Why can't Pudel join voice?

After March 1, 2026, DAVE is required:
1. Check DAVE provider installed
2. Verify Java 25+ for JDAVE
3. Check voice permissions

### What is DAVE?

Discord Audio/Voice Encryption - required E2EE for all voice connections after March 2026.

### How do I add music features?

Install a music plugin (like pudel-music) that uses:
- LavaPlayer for playback
- DAVE for encryption
- YouTube/Spotify sources

---

## Database & Storage

### What database does Pudel use?

PostgreSQL with pgvector extension for:
- User/guild data
- Conversation history
- Vector embeddings (semantic search)
- Plugin data

### How is data isolated?

Each guild/user gets a separate PostgreSQL schema:
```
guild_123456789/
  ├── dialogue_history
  ├── memory
  └── user_preferences
```

### How do I backup data?

```bash
pg_dump pudel > pudel_backup.sql
```

For specific guild:
```bash
pg_dump -n guild_123456789 pudel > guild_backup.sql
```

### What happens when I hit storage limits?

- Auto-cleanup removes oldest entries
- Configurable retention policy
- Upgrade tier for more capacity

---

## Troubleshooting

### "Token invalid" error

1. Regenerate token in Discord Developer Portal
2. Update `application.yml`
3. Restart Pudel

### "Database connection failed"

1. PostgreSQL running?
2. Credentials correct?
3. Database `pudel` exists?
4. pgvector extension installed?

### "Out of memory" errors

1. Increase heap: `-Xmx2g`
2. Use smaller Ollama model
3. Reduce context size
4. Enable auto-cleanup

### Slash commands not appearing

1. Call `syncCommands()` in plugin
2. Wait up to 1 hour for global commands
3. Use guild commands for instant update
4. Check bot has `applications.commands` scope

### Plugin causing errors

1. Check logs for stack trace
2. Disable plugin: `POST /api/admin/plugins/{name}/disable` (requires admin auth)
3. Remove JAR and restart
4. Report issue to plugin developer

---

## Docker

### How do I run in Docker?

```bash
docker-compose up -d
```

See [DOCKER.md](https://github.com/World-Standard-Group/Pudel-Spring-Boot/blob/main/DOCKER.md) for full guide.

### How do I add plugins in Docker?

Mount plugins volume:
```yaml
volumes:
  - ./plugins:/app/plugins
```

### How do I update in Docker?

```bash
docker-compose pull
docker-compose up -d
```

---

## Security

### Is my data private?

- All data stored locally (your database)
- Ollama runs locally (no cloud AI)
- Schema isolation between guilds
- No telemetry by default

### How is authentication handled?

- Discord OAuth for web dashboard
- Ed25519-signed JWTs, held server-side and never exposed to the browser
- Per-guild permissions checked

### Can plugins access other guilds' data?

No. Plugins operate within schema boundaries. Core enforces isolation.

---

## Contributing

### How can I contribute?

1. Fork the repository
2. Create feature branch
3. Submit pull request
4. Follow code style guidelines

### Where do I report bugs?

GitHub Issues with:
- Pudel version
- Java version
- Steps to reproduce
- Error logs

---

*Didn't find your answer? Ask Pudel directly or open an issue!* 🐩
