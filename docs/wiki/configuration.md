# Configuration Guide

Complete reference for Pudel configuration files.

## Configuration Files Overview

| File | Location | Purpose |
|------|----------|---------|
| `application.yml` | pudel-core/src/main/resources/ | Main bot configuration |
| `application-local.yml` | Same directory | Local development overrides |
| `application-production.yml` | Same directory | Production settings |
| `subscription-tiers.yml` | Same directory | Subscription tier definitions |

---

## application.yml

### Spring Boot Settings

```yaml
spring:
  application:
    name: pudel-core

  datasource:
    url: jdbc:postgresql://localhost:5432/pudel
    username: postgres
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      # update = Hibernate auto-creates/evolves the global public tables.
      # Per-guild/per-user schemas are managed separately by SchemaManagementService
      # (schema-as-code); see SCHEMA_MANAGEMENT.md.
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

server:
  port: 8080
```

### Discord Settings

```yaml
pudel:
  discord:
    # Bot token (required)
    token: ${DISCORD_BOT_TOKEN}
    
    # Default command prefix
    prefix: "!"
    
    # OAuth2 settings for web dashboard
    oauth:
      client-id: ${DISCORD_CLIENT_ID}
      client-secret: ${DISCORD_CLIENT_SECRET}
      redirect-uri: http://localhost:5173/auth/callback
```

### Ollama / LLM Settings

```yaml
pudel:
  ollama:
    # Enable/disable Ollama integration
    enabled: true
    
    # Ollama server URL
    base-url: http://localhost:11434
    
    # Model to use (qwen3:8b default; phi3:mini, gemma:2b, llama3.2:1b also work)
    model: qwen3:8b
    
    # Request timeout in seconds
    timeout-seconds: 60
    
    # Fallback when Ollama unavailable
    fallback-enabled: true
```

### Embedding Settings

```yaml
pudel:
  chatbot:
    embedding:
      # Enable vector embeddings for semantic search
      enabled: true
      
      # Vector dimension — MUST match the embedding model (qwen3-embedding:8b = 1024)
      dimension: 1024
      
      # IVFFlat index settings for pgvector
      ivfProbes: 10    # Higher = more accurate, slower
      ivfLists: 100    # Number of IVF lists
```

### Chatbot Triggers

```yaml
pudel:
  chatbot:
    triggers:
      # Respond when @mentioned
      onMention: true
      
      # Always respond in DMs
      onDirectMessage: true
      
      # Respond to replies to Pudel's messages
      onReplyToBot: true
      
      # Keywords that trigger response
      keywords:
        - "pudel"
        - "hey pudel"
      
      # Channels where always active (no mention needed)
      alwaysActiveChannels: []
    
    # Number of past messages for context
    contextSize: 10
```

### Memory Management

```yaml
pudel:
  memory:
    autoCleanup:
      # Enable automatic cleanup when capacity reached
      enabled: true
      
      # Keep newest X% of entries
      keepPercentage: 80
      
      # Only delete entries older than X days
      minAgeDays: 30
```

### Per-Guild AI Settings (via `/settings` Panel)

These settings are stored in the `guild_settings` table and configured through the `/settings` > AI view in Discord:

| Setting | Column | Type | Default | Description |
|---------|--------|------|---------|-------------|
| AI Enabled | `ai_enabled` | boolean | `false` | Enable AI chatbot for the guild |
| AI Nickname | `ai_nickname` | string | `null` | Custom nickname for the bot |
| Language | `language` | string | `en` | Response language (ISO 639-1 code) |
| Personality | `personality` | text | `null` | Personality traits description |
| Biography | `biography` | text | `null` | Bot backstory for the guild |
| Response Length | `response_length` | string | `medium` | `short` / `medium` / `long` |
| Formality | `formality` | string | `balanced` | `casual` / `balanced` / `formal` |
| Emote Usage | `emote_usage` | string | `moderate` | `none` / `minimal` / `moderate` / `frequent` |

### Audio Settings

```yaml
pudel:
  audio:
    # Enable/disable audio features
    enabled: true
    
    # DAVE is required after March 1, 2026
    # Handled automatically by plugins
```

### Plugin Settings

```yaml
pudel:
  plugins:
    # Directory for plugin JARs
    directory: ./plugins
    
    # Auto-load plugins on startup
    autoLoad: true
    
    # Enable hot-reload detection
    hotReload: true
    
    # Watch interval in seconds
    watchInterval: 30
```

### Security Settings

```yaml
pudel:
  security:
    jwt:
      # Ed25519 signing key file (PKCS#8 PEM)
      private-key-path: keys/jwt_pv.key
      public-key-path: keys/jwt_pb.key
      
      # Token expiration
      expiration-hours: 24
```

---

## subscription-tiers.yml

Define subscription tiers with capacity limits:

```yaml
subscription:
  # Default tier for new users/guilds
  defaultTier: FREE
  
  # Enable subscription expiration
  enableExpiration: true
  
  tiers:
    FREE:
      name: "Free"
      description: "Basic free tier"
      user:
        dialogueLimit: 1000
        memoryLimit: 100
      guild:
        dialogueLimit: 5000
        memoryLimit: 500
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1    # -1 = unlimited
        voiceEnabled: false
        prioritySupport: false
        
    TIER_1:
      name: "Supporter"
      description: "Basic supporter tier"
      user:
        dialogueLimit: 1500
        memoryLimit: 150
      guild:
        dialogueLimit: 7500
        memoryLimit: 750
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: true
        prioritySupport: false
        
    TIER_2:
      name: "Premium"
      description: "Premium supporter tier"
      user:
        dialogueLimit: 2000
        memoryLimit: 200
      guild:
        dialogueLimit: 10000
        memoryLimit: 1000
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: true
        prioritySupport: true
        
    UNLIMITED:
      name: "Unlimited"
      description: "No limits"
      user:
        dialogueLimit: -1   # -1 = unlimited
        memoryLimit: -1
      guild:
        dialogueLimit: -1
        memoryLimit: -1
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: true
        prioritySupport: true
```

---

## Environment Variables

For sensitive configuration, use environment variables:

| Variable | Description |
|----------|-------------|
| `DISCORD_BOT_TOKEN` | Discord bot token |
| `DISCORD_CLIENT_ID` | OAuth2 client ID |
| `DISCORD_CLIENT_SECRET` | OAuth2 client secret |
| `DB_PASSWORD` | Database password |
| `DB_URL` | Full JDBC URL |
| `OLLAMA_URL` | Ollama server URL |

### Example .env File

```bash
DISCORD_BOT_TOKEN=Bruh...
DISCORD_CLIENT_ID=123456789012345678
DISCORD_CLIENT_SECRET=Bro...
DB_PASSWORD=your_secure_password
OLLAMA_URL=http://localhost:11434
```

---

## Docker Configuration

### docker-compose.yml

```yaml
services:
  pudel:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DISCORD_BOT_TOKEN=${DISCORD_BOT_TOKEN}
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/pudel
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - PUDEL_OLLAMA_BASE_URL=http://ollama:11434
    volumes:
      - ./plugins:/app/plugins
      - ./keys:/app/keys:ro
    depends_on:
      - db
      - ollama

  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: pudel
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    # No init.sql mount: Pudel reconciles its schema from Java on startup.

  ollama:
    image: ollama/ollama
    volumes:
      - ollama_data:/root/.ollama

volumes:
  postgres_data:
  ollama_data:
```

---

## Profile-Specific Configuration

### Development (application-local.yml)

```yaml
spring:
  jpa:
    show-sql: true
    
pudel:
  discord:
    token: ${DISCORD_BOT_TOKEN_DEV}
    
logging:
  level:
    worldstandard.group.pudel: DEBUG
```

### Production (application-production.yml)

```yaml
spring:
  jpa:
    show-sql: false
    
server:
  port: 8080
  
logging:
  level:
    root: WARN
    worldstandard.group.pudel: INFO
```

Run with profile:
```bash
java -jar pudel-core.jar --spring.profiles.active=production
```

---

## Configuration Priority

1. Command line arguments
2. Environment variables
3. `application-{profile}.yml`
4. `application.yml`

---

*For more details, see the inline comments in the configuration files.*
