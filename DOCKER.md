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

# Security (CHANGE IN PRODUCTION)
JWT_SECRET=your-secure-random-string-at-least-32-characters
```

### 3. Start the Services

**Option A: Production Mode (Pre-built)**
```bash
docker-compose up -d
```

**Option B: Auto-Update Mode (Pulls latest on restart)**
```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 4. Pull Ollama Models (if using AI features)

```bash
# Pull the main chat model
docker-compose exec ollama ollama pull qwen3:8b

# Pull the embedding model
docker-compose exec ollama ollama pull qwen3-embedding:8b
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
| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | - | **Change in production.** JWT signing key (min 32 chars) |
| `JWT_EXPIRATION` | `604800000` | JWT expiration in milliseconds (7 days) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated CORS origins |

### Ollama LLM Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_ENABLED` | `true` | Enable/disable Ollama AI |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama API URL |
| `OLLAMA_MODEL` | `qwen3:8b` | Chat model name |
| `EMBEDDING_ENABLED` | `true` | Enable semantic search |
| `EMBEDDING_MODEL` | `qwen3-embedding:8b` | Embedding model name |

### Server Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `JAVA_OPTS` | `-Xms512m -Xmx2g...` | JVM options |

### Auto-Update Configuration (docker-compose.dev.yml only)
| Variable | Default | Description |
|----------|---------|-------------|
| `GIT_REPO` | `https://github.com/World-Standard-Group/Pudel-Spring-Boot.git` | Git repository URL |
| `GIT_BRANCH` | `main` | Branch to track |
| `AUTO_UPDATE` | `true` | Pull latest on container restart |

## Docker Files Overview

| File | Purpose |
|------|---------|
| `Dockerfile` | Production multi-stage build |
| `Dockerfile.dev` | Development/auto-update mode |
| `docker-compose.yml` | Production deployment |
| `docker-compose.dev.yml` | Development with auto-updates |
| `.env.example` | Environment template |

## Updating the Bot

### Production Mode
```bash
# Pull latest changes
git pull origin main

# Rebuild and restart
docker-compose build --no-cache pudel
docker-compose up -d pudel
```

### Auto-Update Mode
```bash
# Simply restart the container - it will pull latest automatically
docker-compose -f docker-compose.dev.yml restart pudel
```

### Using the Update Script
```bash
chmod +x scripts/update.sh
./scripts/update.sh
```

## Common Commands

```bash
# View logs
docker-compose logs -f pudel

# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v

# Restart a specific service
docker-compose restart pudel

# Check service status
docker-compose ps

# Execute command in container
docker-compose exec pudel bash

# View Ollama models
docker-compose exec ollama ollama list
```

## GPU Support for Ollama

To enable GPU acceleration for Ollama, uncomment the GPU section in `docker-compose.yml`:

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
| `pudel_plugins` | `/app/plugins` | Bot plugins |

## Troubleshooting

### Bot not connecting to Discord
1. Verify `DISCORD_BOT_TOKEN` is correct
2. Check logs: `docker-compose logs pudel`
3. Ensure bot has proper intents enabled in Discord Developer Portal

### Database connection issues
1. Wait for PostgreSQL to be ready: `docker-compose logs postgres`
2. Verify credentials match between services

### Ollama not responding
1. Check if Ollama is running: `docker-compose ps ollama`
2. Verify models are downloaded: `docker-compose exec ollama ollama list`
3. Check Ollama logs: `docker-compose logs ollama`

### Out of memory
1. Adjust `JAVA_OPTS` in `.env` to reduce memory usage
2. Consider using a smaller Ollama model (e.g., `qwen3:4b`)

## Security Recommendations

1. **Change JWT_SECRET** to a random string (use `openssl rand -base64 32`)
2. **Use strong database password**
3. **Limit CORS_ALLOWED_ORIGINS** to your actual domains
4. **Use HTTPS** in production (configure reverse proxy)
5. **Keep images updated** regularly

## License

This project is licensed under AGPLv3 with the Pudel Plugin Exception. See [LICENSE](LICENSE) for details.

