# Pudel Discord Bot - Docker Deployment Guide

This guide covers deploying Pudel Discord Bot using Docker on Ubuntu Linux.

## Prerequisites

- Docker 20.10+ and Docker Compose v2+
- At least 4GB RAM (8GB recommended for Ollama)
- Discord Bot Token (from [Discord Developer Portal](https://discord.com/developers/applications))

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd Pudel-Spring-Boot
```

### 2. Configure Environment

```bash
# Copy the example environment file
cp .env.example .env

# Edit the .env file with your configuration
nano .env
```

**Required settings in `.env`:**
```env
# Discord Bot (REQUIRED)
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
```

### 2.5. Generate RSA Keys for JWT Authentication

Generate RSA key pair for JWT token signing:

```bash
# Navigate to the keys directory
cd keys

# Generate RSA private key (PKCS#8 format)
openssl genpkey -algorithm RSA -out private.key -pkeyopt rsa_keygen_bits:2048

# Extract public key
openssl rsa -pubout -in private.key -out public.key

# Set proper permissions
chmod 600 private.key
chmod 644 public.key

# Return to project root
cd ..
```

> **Important:** The keys directory is automatically mounted into the Docker container at `/app/keys/`. Never commit private keys to version control!

### 3. Start the Services

**Option A: Without AI (Ollama disabled)**
```bash
# Start only PostgreSQL and Pudel
docker compose up -d
```

**Option B: With AI Features (Ollama enabled)**
```bash
# Start with Ollama profile
docker compose --profile ollama up -d
```

Or use the quick start script:
```bash
chmod +x scripts/start.sh
./scripts/start.sh
```

> **Note:** Ollama requires additional RAM (8GB+ recommended). If you don't need AI features, skip the `--profile ollama` flag to save resources.

### 4. Pull Ollama Models (only if using AI features)

```bash
# Pull the main chat model
docker compose exec ollama ollama pull qwen3:8b

# Pull the embedding model
docker compose exec ollama ollama pull qwen3-embedding:8b
```

## Environment Variables Reference

### Database Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_USER` | `postgres` | Database username |
| `POSTGRES_PASS` | - | Database password |
| `POSTGRES_DB` | `pudel` | Database name |

### Discord Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `DISCORD_BOT_TOKEN` | - | **Required.** Bot token from Discord Developer Portal |
| `DISCORD_CLIENT_ID` | - | OAuth2 Client ID |
| `DISCORD_CLIENT_SECRET` | - | OAuth2 Client Secret |
| `DISCORD_REDIRECT_URI` | `http://localhost/auth/callback` | OAuth2 redirect URI |

### Security Configuration
| Variable | Default | Description                             |
|----------|---------|-----------------------------------------|
| `JWT_PRIVATE_KEY_PATH` | `/app/keys/private.key` | Path to RSA private key (mounted via volume) |
| `JWT_PUBLIC_KEY_PATH` | `/app/keys/public.key` | Path to RSA public key (mounted via volume) |
| `JWT_EXPIRATION` | `604800000` | JWT expiration in milliseconds (7 days) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated CORS origins            |

### Ollama LLM Configuration

Ollama is **optional** and only starts when using the `ollama` profile:

```bash
# With Ollama
docker compose --profile ollama up -d

# Without Ollama (default)
docker compose up -d
```

| Variable | Default               | Description                                                    |
|----------|-----------------------|----------------------------------------------------------------|
| `OLLAMA_ENABLED` | `true`                | Enable/disable Ollama AI in Pudel (app-level)                  |
| `OLLAMA_URL` | `http://ollama:11434` | Ollama API URL (use `ollama` for Docker, `localhost` for host) |
| `OLLAMA_MODEL` | `qwen3:8b`            | Chat model name                                                |
| `EMBEDDING_DIMENSION` | `1024`                | Embedding model's real vector dimension                        |
| `EMBEDDING_MODEL` | `qwen3-embedding:8b`  | Embedding model name                                           |

> **Note:** `OLLAMA_ENABLED=false` in `.env` disables AI features in Pudel but doesn't prevent the Ollama container from starting. Use profiles to control whether Ollama container runs.

### Server Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `JAVA_OPTS` | `-Xms512m -Xmx2g...` | JVM options |

## How to Update

Updating Pudel is simple — pull the latest code on your host, rebuild, and restart. Your `.env`, database, plugins, keys, and logs are **never touched** during this process.

```
┌──────────────────────────────────────────────────────────┐
│                    UPDATE WORKFLOW                        │
│                                                          │
│  On your server terminal:                                │
│                                                          │
│    1.  cd /path/to/Pudel-Spring-Boot                     │
│    2.  git pull origin main                              │
│    3.  docker compose build pudel                        │
│    4.  docker compose up -d pudel                        │
│                                                          │
│  That's it. Database and other services stay running.    │
│  Your .env file is untouched (it lives on the host).     │
│  Plugins, keys, and logs are bind-mounted / volumes.     │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### Quick Update (one-liner)
```bash
git pull origin main && docker compose build pudel && docker compose up -d pudel
```

### Using the Update Script
```bash
./scripts/update.sh          # defaults to 'main' branch
./scripts/update.sh develop  # update from a specific branch
```

### What's Safe During Updates

| Data | Storage | Safe? |
|------|---------|-------|
| `.env` configuration | Host filesystem | ✅ Never touched |
| PostgreSQL data | `postgres_data` Docker volume | ✅ Persists across rebuilds |
| Ollama models | `ollama_data` Docker volume | ✅ Persists across rebuilds |
| Plugins (JARs) | `./plugins` bind mount | ✅ Lives on host |
| RSA keys | `./keys` bind mount | ✅ Lives on host (read-only) |
| Application logs | `pudel_logs` Docker volume | ✅ Persists across rebuilds |

> **Tip:** `docker compose down` stops containers but preserves volumes. Only `docker compose down -v` deletes volumes — avoid it unless you want a full reset.

## Docker Files Overview

| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build (Maven build + lean JDK runtime) |
| `docker-compose.yml` | Full deployment (PostgreSQL, Ollama, Pudel) |
| `scripts/start.sh` | First-time setup script |
| `scripts/update.sh` | Pull + rebuild + restart script |
| `scripts/docker-entrypoint.sh` | Container entrypoint (permissions + startup) |
| `.env.example` | Environment template |

## Updating the Bot

```bash
# Pull latest code, rebuild, and restart (database stays running)
git pull origin main
docker compose build pudel
docker compose up -d pudel

# Or use the convenience script
./scripts/update.sh
```

## Common Commands

```bash
# View logs
docker compose logs -f pudel

# Stop all services
docker compose down

# Stop and remove volumes (WARNING: deletes data)
docker compose down -v

# Restart a specific service
docker compose restart pudel

# Check service status
docker compose ps

# Execute command in container
docker compose exec pudel bash

# View Ollama models
docker compose exec ollama ollama list
```

## GPU Support for Ollama

To enable GPU acceleration for Ollama, uncomment the GPU section in `docker compose.yml`:

```yaml
ollama:
  # ... other config ...
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: all
            capabilities: [gpu]
```

Make sure you have [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html) installed.

## Volumes and Data Persistence

| Volume | Path | Description |
|--------|------|-------------|
| `postgres_data` | `/var/lib/postgresql/data` | Database files |
| `ollama_data` | `/root/.ollama` | Downloaded AI models |
| `pudel_logs` | `/app/logs` | Application logs |
| `./plugins` (bind mount) | `/app/plugins` | Bot plugins (hot-reloadable) |
| `./keys` (bind mount) | `/app/keys` | RSA keys for JWT (read-only) |

> **Important:** All persistent data lives in Docker volumes or bind mounts on the host. Rebuilding the Pudel image (`docker compose build pudel`) only replaces the application code — your data is safe.

## Plugin Management in Docker

Pudel supports **live plugin installation** even while running in Docker. The plugins directory is mounted as a bind mount, allowing you to add, update, or remove plugins without rebuilding the container.

### How It Works

1. **Bind Mount**: The `./plugins` directory on your host is mounted to `/app/plugins` in the container
2. **NIO WatchService**: Pudel watches for file changes in real-time
3. **Polling Fallback**: If WatchService doesn't work (some Docker/NFS setups), polling every 60 seconds detects changes
4. **Stability Delay**: Waits 1 second after file changes to ensure uploads are complete

### Adding Plugins at Runtime

```bash
# Simply copy a JAR file to the plugins directory
cp my-awesome-plugin-1.0.0.jar ./plugins/

# The plugin will be auto-detected and loaded within seconds
docker compose logs -f pudel | grep "New plugin discovered"
```

### Updating Plugins

```bash
# Replace the JAR file
cp my-awesome-plugin-1.1.0.jar ./plugins/my-awesome-plugin-1.0.0.jar

# If the plugin is DISABLED: Update is applied immediately
# If the plugin is ENABLED: Update is queued (requires disable or restart)

# To force apply update, disable the plugin first:
# (via API or bot command)
```

### Removing Plugins

```bash
# Simply delete the JAR file
rm ./plugins/my-awesome-plugin-1.0.0.jar

# The plugin will be detected as removed
```

### Plugin Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PUDEL_PLUGIN_DIRECTORY` | `/app/plugins` | Path to plugins directory |
| `PUDEL_PLUGIN_ENABLE_AUTO_DISCOVERY` | `true` | Auto-detect new plugins |
| `PUDEL_PLUGIN_ENABLE_AUTO_LOAD` | `true` | Auto-load discovered plugins |
| `PUDEL_PLUGIN_RETRY_INTERVAL` | `30000` | Retry interval for failed plugins (ms) |
| `PUDEL_PLUGIN_MAX_RETRIES` | `3` | Max retry attempts for failed plugins |

### Monitoring Plugin Status

```bash
# Check plugin loading logs
docker compose logs pudel | grep -E "(plugin|Plugin)"

# Watch for plugin changes in real-time
docker compose logs -f pudel | grep -i plugin
```

### Failed Plugin Handling

If a plugin fails to load:
1. Pudel tracks the failure with the JAR's hash
2. Retries up to `PUDEL_PLUGIN_MAX_RETRIES` times at `PUDEL_PLUGIN_RETRY_INTERVAL` intervals
3. If the JAR file is updated (hash changes), retry count resets
4. After max retries, waits for JAR update before trying again

### Docker Volume vs Bind Mount

**Bind Mount (Recommended for plugins)**:
```yaml
volumes:
  - ./plugins:/app/plugins  # Live access from host
```

**Named Volume (NOT recommended for plugins)**:
```yaml
volumes:
  - pudel_plugins:/app/plugins  # Isolated from host
```

The production `docker compose.yml` uses a bind mount for plugins to allow runtime management.

## Troubleshooting

### Plugin Issues

#### Plugins not being detected
1. Check the plugins directory is properly mounted:
   ```bash
   docker compose exec pudel ls -la /app/plugins
   ```
2. Verify NIO WatchService is working (check logs for "NIO WatchService started")
3. If WatchService fails, the polling fallback runs every 60 seconds
4. Ensure JAR files have `.jar` extension

#### Plugin fails to load
1. Check logs for specific error:
   ```bash
   docker compose logs pudel | grep -A5 "Failed to load"
   ```
2. Verify plugin has valid `MANIFEST.MF` with `Plugin-Main` or a `plugin.yml`
3. Try updating the JAR to trigger a reload
4. Use the retry mechanism: replace the JAR file to reset retry counter

#### Plugin not updating when JAR is replaced
1. If plugin is ENABLED, updates are queued
2. Disable the plugin first, then the update applies immediately
3. Or restart the container to apply all pending updates

### Bot not connecting to Discord
1. Verify `DISCORD_BOT_TOKEN` is correct
2. Check logs: `docker compose logs pudel`
3. Ensure bot has proper intents enabled in Discord Developer Portal

### Database connection issues
1. Wait for PostgreSQL to be ready: `docker compose logs postgres`
2. Verify credentials match between services
3. **Windows `.env` line endings**: If you edited `.env` on Windows, it may have `\r` (carriage return) characters appended to your password. Fix with:
   ```bash
   # Convert .env to Unix line endings
   sed -i 's/\r$//' .env
   # OR in PowerShell:
   (Get-Content .env -Raw).Replace("`r`n","`n") | Set-Content .env -NoNewline
   ```
4. **Password already set**: `POSTGRES_PASSWORD` only takes effect on first database initialization. If the `postgres_data` volume already exists with a different password:
   ```bash
   # WARNING: This deletes all database data!
   docker compose down
   docker volume rm pudel-spring-boot_postgres_data
   docker compose up -d
   ```
   Or connect to the running database and change the password:
   ```bash
   docker compose exec postgres psql -U postgres -c "ALTER USER postgres PASSWORD 'your_new_password';"
   ```

### Ollama not responding
1. Check if Ollama is running: `docker compose ps ollama`
2. Verify models are downloaded: `docker compose exec ollama ollama list`
3. Check Ollama logs: `docker compose logs ollama`

### Out of memory
1. Adjust `JAVA_OPTS` in `.env` to reduce memory usage
2. Consider using a smaller Ollama model (e.g., `qwen3:4b`)

## Security Recommendations

1. **Generate strong RSA keys** - Use at least 2048-bit RSA keys for JWT signing
2. **Protect private key** - Ensure `private.key` has strict permissions (600) and never commit to version control
3. **Use strong database password**
4. **Limit CORS_ALLOWED_ORIGINS** to your actual domains
5. **Use HTTPS** in production (configure reverse proxy)
6. **Keep images updated** regularly
7. **Mount keys as read-only** - The keys volume is mounted with `:ro` flag for security

## License

This project is licensed under AGPLv3 with the Pudel Plugin Exception. See [LICENSE](LICENSE) for details.

