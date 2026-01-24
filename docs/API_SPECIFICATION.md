# Pudel API Specification v1.0.0

Complete REST API reference for Pudel Discord Bot.

---

## Base URL

```
http://localhost:8080/api
```

---

## Authentication

All authenticated endpoints require a JWT token in the Authorization header:

```
Authorization: Bearer <jwt_token>
```

---

## API Endpoints

### Authentication (`/api/auth`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/discord/callback` | No | Exchange OAuth code for JWT |
| GET | `/user/guilds` | Yes | Get user's guilds with bot status |
| GET | `/user/guilds/{guildId}` | Yes | Get specific guild details |

#### POST `/api/auth/discord/callback`

Exchange Discord OAuth authorization code for Pudel JWT token.

**Request Body:**
```json
{
  "code": "discord_oauth_code",
  "redirectUri": "http://localhost:5173/auth/callback"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "152140348980723712",
    "username": "Username",
    "avatar": "avatar_hash"
  }
}
```

#### GET `/api/auth/user/guilds`

Get all guilds for authenticated user.

**Response:**
```json
[
  {
    "id": "123456789",
    "name": "My Server",
    "icon": "icon_hash",
    "owner": true,
    "permissions": 8,
    "hasBot": true
  }
]
```

---

### Bot Status (`/api/bot`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/status` | No | Get bot online status |
| GET | `/stats` | No | Get bot statistics |

#### GET `/api/bot/status`

**Response:**
```json
{
  "online": true,
  "guildCount": 5,
  "userCount": 1234,
  "shardCount": 1,
  "uptime": "2d 5h 30m",
  "version": "1.0.0"
}
```

#### GET `/api/bot/stats`

**Response:**
```json
{
  "guildCount": 5,
  "userCount": 1234,
  "channelCount": 50,
  "commandsExecuted": 10000,
  "messagesProcessed": 50000,
  "shards": [
    {
      "id": 0,
      "status": "CONNECTED",
      "guildCount": 5,
      "ping": 45
    }
  ]
}
```

---

### Guild Settings (`/api/guilds`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/{guildId}/settings` | Yes | Get guild settings |
| POST | `/{guildId}/settings` | Yes | Create guild settings |
| PATCH | `/{guildId}/settings` | Yes | Update guild settings |
| DELETE | `/{guildId}/settings` | Yes | Delete guild settings |

#### GET `/api/guilds/{guildId}/settings`

**Response:**
```json
{
  "guildId": "123456789",
  "commandPrefix": "!",
  "verbosityLevel": 3,
  "commandCooldown": 0,
  "logChannelId": null,
  "botChannelId": null,
  "botBiography": "A helpful assistant",
  "botPersonality": "friendly, helpful",
  "botPreferences": "casual conversation",
  "dialogueStyle": "natural",
  "botNickname": "Pudel",
  "language": "en",
  "aiEnabled": true,
  "systemPromptPrefix": null,
  "ignoreChannels": [],
  "disabledCommands": []
}
```

#### PATCH `/api/guilds/{guildId}/settings`

**Request Body (partial update):**
```json
{
  "commandPrefix": "?",
  "botPersonality": "formal, professional"
}
```

---

### Guild Data (`/api/guilds/{guildId}/data`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/schema/status` | Yes | Get schema status |
| POST | `/schema/initialize` | Yes | Initialize guild schema |
| GET | `/dialogue/user/{userId}` | Yes | Get user dialogue |
| GET | `/memory/{key}` | Yes | Get memory entry |
| POST | `/memory` | Yes | Store memory entry |
| DELETE | `/memory/{key}` | Yes | Delete memory entry |

---

### Plugins (`/api/plugins`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | No | List all loaded plugins |
| GET | `/{name}` | No | Get plugin details |
| GET | `/enabled` | No | List enabled plugins |
| POST | `/{name}/enable` | Yes | Enable a plugin |
| POST | `/{name}/disable` | Yes | Disable a plugin |

#### GET `/api/plugins`

**Response:**
```json
{
  "name": "DefaultPudelPlugins",
  "version": "1.0.0",
  "author": "Pudel Team",
  "description": "Default plugin bundle",
  "enabled": true,
  "loaded": true
}
```

---

### Plugin Market (`/api/plugins/market`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | No | List all marketplace plugins |
| GET | `/paged` | No | Paginated plugin list |
| GET | `/top` | No | Top downloaded plugins |
| GET | `/stats` | No | Marketplace statistics |
| GET | `/{id}` | No | Get plugin details |
| POST | `/{id}/download` | No | Increment download count |

#### GET `/api/plugins/market`

**Query Parameters:**
- `category` (optional): Filter by category
- `search` (optional): Search by name/description

**Response:**
```json
{
  "id": "uuid",
  "name": "My Plugin",
  "description": "A cool plugin",
  "category": "moderation",
  "authorId": "152140348980723712",
  "authorName": "Author",
  "version": "1.0.0",
  "downloads": 100,
  "sourceUrl": "https://github.com/...",
  "licenseType": "MIT",
  "isCommercial": false,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

---

### Plugin Publishing (`/api/plugins`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/publish` | Yes | Publish new plugin |
| PATCH | `/{id}` | Yes | Update plugin |
| DELETE | `/{id}` | Yes | Delete plugin |
| GET | `/user/plugins` | Yes | Get user's plugins |

#### POST `/api/plugins/publish`

**Request Body:**
```json
{
  "name": "My Plugin",
  "description": "A cool plugin for...",
  "category": "moderation",
  "version": "1.0.0",
  "sourceUrl": "https://github.com/user/plugin",
  "licenseType": "MIT"
}
```

---

### Subscription (`/api/subscription`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/guild/{guildId}/usage` | Yes | Get guild usage |
| GET | `/user/{userId}/usage` | Yes | Get user usage |
| GET | `/tiers` | No | Get available tiers |
| GET | `/tiers/{tierName}` | No | Get tier details |

#### GET `/api/subscription/guild/{guildId}/usage`

**Response:**
```json
{
  "guildId": "123456789",
  "tier": "FREE",
  "active": true,
  "dialogue": {
    "current": 1500,
    "limit": 5000,
    "percentage": 30.0
  },
  "memory": {
    "current": 50,
    "limit": 500,
    "percentage": 10.0
  }
}
```

---

### Brain (`/api/brain`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/status` | No | Get brain status |
| POST | `/analyze` | Yes | Analyze text |
| GET | `/memory/guild/{guildId}` | Yes | Get guild memories |
| GET | `/memory/user/{userId}` | Yes | Get user memories |

#### GET `/api/brain/status`

**Response:**
```json
{
  "ollamaAvailable": true,
  "ollamaModel": "phi3:mini",
  "embeddingEnabled": true,
  "embeddingDimension": 384
}
```

#### POST `/api/brain/analyze`

**Request Body:**
```json
{
  "text": "What time is the meeting tomorrow?"
}
```

**Response:**
```json
{
  "intent": "question",
  "sentiment": "neutral",
  "language": "en",
  "entities": [],
  "isQuestion": true,
  "isCommand": false,
  "keywords": ["time", "meeting", "tomorrow"]
}
```

---

### User Data (`/api/user/data`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/pudel-settings` | Yes | Get user's Pudel settings |
| PATCH | `/pudel-settings` | Yes | Update user's Pudel settings |
| GET | `/dialogue` | Yes | Get user's DM dialogue |
| GET | `/memory` | Yes | Get user's DM memory |

---

## Error Responses

All errors return a consistent format:

```json
{
  "error": "Error type",
  "message": "Human readable message",
  "timestamp": "2025-01-01T00:00:00Z",
  "path": "/api/endpoint"
}
```

### Common HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 500 | Internal Server Error |

---

## Rate Limiting

API endpoints are rate limited per IP:

| Endpoint Type | Limit |
|---------------|-------|
| Authentication | 10/min |
| Read operations | 60/min |
| Write operations | 30/min |

Rate limit headers:
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 55
X-RateLimit-Reset: 1609459200
```

---

## WebSocket (Future)

Reserved for real-time features:

```
ws://localhost:8080/ws
```

Topics:
- `/topic/bot/status` - Bot status updates
- `/topic/guild/{id}/chat` - Guild chat events
- `/topic/plugins` - Plugin status changes

---

*Version: 1.0.0*
*Last Updated: January 2026*
