# REST API Reference

Complete REST API documentation for Pudel.

## Base URL

```
http://localhost:8080/api
```

## Authentication

Pudel is a **cookie-only BFF**. The browser holds exactly one credential: an
`HttpOnly`, `Secure`, `SameSite=Strict` AES-GCM encrypted cookie named
`pudel_session` (configurable via `SESSION_NAME`). Its plaintext is an opaque
database key id — never a token, never the key itself.

There is **no `Authorization` header** in the normal flow:

```
JwtAuthenticationFilter
    Any Authorization header present → 401
      { "error": "invalid_token",
        "error_description": "Authorization headers are not accepted; use the encrypted session cookie" }
```

So requests are made with `credentials: include` (cookies), for example:

```bash
curl -c jar.txt https://host/api/session/bootstrap
curl -b jar.txt https://host/api/auth/me
```

On every authenticated request the BFF signs a fresh single-use Ed25519 DPoP
proof for the request and validates it in the same pass, then exposes only the
Discord user id to Spring Security with the `DPOP_VERIFIED` authority.

**HTTPS is required.** The cookie is `Secure` and will not be sent over plain
HTTP.

---

## Session Endpoints

### GET /api/session/bootstrap

Create or reuse the encrypted browser session. Public.

**Response:**
```json
{
  "ready": true,
  "expiresAt": 1788540000000,
  "maxAgeSeconds": 604800
}
```

No key id or token is returned.

### POST /api/session/rotate

Force a fresh Ed25519 browser key and cookie. Tokens bound to the previous key
id stop working.

**Response:** `{ "ready": true, "expiresAt": 1788540000000 }`

---

## Authentication Endpoints

### POST /api/auth/discord/callback

Exchange the Discord authorization code. Requires an existing browser session
cookie. Public.

**Request:**
```json
{
  "code": "discord_oauth_code",
  "redirectUri": "http://localhost:5173/auth/callback"
}
```

**Response:**
```json
{
  "accessToken": null,
  "user": {
    "id": "152140348980723712",
    "username": "Username",
    "avatar": "avatar_hash"
  },
  "tokenType": "COOKIE"
}
```

`accessToken` is always `null`: the DPoP-bound JWT is stored server-side in the
`dpop_keys` row and never sent to the SPA.

### POST /api/auth/refresh

Re-issue the server-held token using only the cookie; also refreshes the Discord
token when it expires within 300 s. Public.

**Response:** same shape as the callback, `{ "accessToken": null, "user": {...}, "tokenType": "COOKIE" }`

### GET /api/auth/me

Restore the user after a page refresh using only the cookie.

**Auth:** browser session required (the cookie)
**Response:** `UserDto`

### POST /api/auth/logout

Deactivate the session key row (`dpop_keys.is_active = false`) and clear the
cookie. Public.

**Response:** `{ "message": "Logged out successfully" }`

### GET /api/auth/user/guilds

Get all guilds for the authenticated user (admin-permission guilds only, split
into `managed` and `available`).

**Auth:** browser session required

**Response:**
```json
{
  "guilds": [
    {
      "id": "123456789",
      "name": "My Server",
      "icon": "icon_hash",
      "owner": true,
      "permissions": 8,
      "hasBot": true
    }
  ],
  "managed": [],
  "available": [],
  "managedCount": 0,
  "availableCount": 1,
  "total": 1
}
```

### GET /api/auth/user/guilds/{guildId}

Guild detail plus its settings for a guild the user belongs to.

---

## Bot Status

### GET /api/bot/status

Get bot online status.

**Auth:** Not required

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

### GET /api/bot/stats

Get detailed bot statistics.

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

## Guild Settings

### GET /api/guilds/{guildId}/settings

Get guild settings.

**Auth:** Required (must have permissions in guild)

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

### PATCH /api/guilds/{guildId}/settings

Update guild settings (partial update).

**Auth:** Required

**Request:**
```json
{
  "commandPrefix": "?",
  "botPersonality": "formal, professional"
}
```

---

## Guild Data

### GET /api/guilds/{guildId}/data/schema/status

Check if guild schema is initialized.

**Response:**
```json
{
  "guildId": "123456789",
  "schemaCreated": true,
  "tables": ["dialogue_history", "memory", "user_preferences"]
}
```

### POST /api/guilds/{guildId}/data/schema/initialize

Initialize guild schema (creates tables).

### GET /api/guilds/{guildId}/data/memory/{key}

Get a memory entry.

### POST /api/guilds/{guildId}/data/memory

Store a memory entry.

**Request:**
```json
{
  "key": "meeting_time",
  "value": "Every Tuesday at 3 PM",
  "category": "schedule"
}
```

---

## Plugins

### GET /api/plugins/installed

List all installed plugins on this instance.

**Response:**
```json
{
  "plugins": [
    {
      "pluginName": "DefaultPudelPlugins",
      "pluginVersion": "1.0.0",
      "pluginAuthor": "Pudel Team",
      "pluginDescription": "Default plugin bundle",
      "enabled": true,
      "loaded": true
    }
  ],
  "total": 1
}
```

### GET /api/plugins/installed/{name}

Get a specific installed plugin by name.

### GET /api/plugins/enabled

List all enabled plugins on this instance.

### POST /api/admin/plugins/{name}/enable

Enable a plugin globally.

**Auth:** browser session + admin session

### POST /api/admin/plugins/{name}/disable

Disable a plugin globally.

**Auth:** browser session + admin session

### Guild-Level Plugin Control

Guilds can disable specific globally-enabled plugins for their server
via the `disabled_plugins` field in guild settings (`PATCH /api/guilds/{guildId}/settings`).

---

## Plugin Marketplace

### GET /api/plugins/market

List marketplace plugins.

**Query Parameters:**
- `category` - Filter by category
- `search` - Search by name/description

**Response:**
```json
[
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
]
```

### GET /api/plugins/market/top

Get top downloaded plugins.

### POST /api/plugins/publish

Publish a new plugin.

**Auth:** Required

**Request:**
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

## Subscriptions

### GET /api/subscription/tiers

Get all subscription tiers.

**Response:**
```json
{
  "FREE": {
    "name": "Free",
    "description": "Basic free tier",
    "dialogueLimit": 5000,
    "memoryLimit": 500,
    "features": {
      "chatbot": true,
      "voiceEnabled": false
    }
  }
}
```

### GET /api/subscription/guild/{guildId}/usage

Get guild usage statistics.

**Auth:** Required

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

## Brain / AI

### GET /api/brain/status

Get AI system status.

**Response:**
```json
{
  "ollamaAvailable": true,
  "ollamaModel": "qwen3:8b",
  "embeddingEnabled": true,
  "embeddingDimension": 1024
}
```

### POST /api/brain/analyze

Analyze text for intent and sentiment.

**Auth:** Required

**Request:**
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

## Error Responses

All errors return a consistent format:

```json
{
  "error": "Error type",
  "message": "Human readable message",
  "timestamp": "2025-01-01T00:00:00Z",
  "path": "/api/auth/me"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 429 | Rate Limited |
| 500 | Server Error |

---

## Admin API (Self-Hosted)

The Admin API is layered on the same cookie session. Pudel proves its identity
with an **Ed25519** challenge signature; each admin proves theirs with their own
**RSA** keypair. The resulting AdminJWT is DPoP-bound to the browser session key
and stored in `dpop_keys.admin_token` — it is **not** returned to the browser.

### Authentication Flow (Mutual)

1. Log in with Discord first (a valid browser session cookie is required).
2. Request a challenge: `GET /api/admin/challenge`
   (server signs the nonce with EdDSA, valid for 60 seconds, single use)
3. *(Optional)* Verify Pudel's signature with `GET /api/admin/public-key`
   (returns `algorithm: "EdDSA"`).
4. Sign the challenge nonce with your RSA private key (in the browser):
   `Base64(SHA256withRSA(nonce, privateKey))`
5. Submit: `POST /api/admin/auth/mutual` with `{ challengeId, signature }`
6. On success an AdminJWT (1 hour) is written to `dpop_keys.admin_token` and a
   `pudel-swagger-session` cookie is set for Swagger UI access.

Every admin request authenticates with the ordinary session cookie;
`validateAdminSession` loads the admin token from the database for that browser
key, verifies the EdDSA signature, and requires `sub = pudel-admin-session` with
an unexpired `exp`. The `Authorization` header is ignored for admin endpoints.

### GET /api/admin/public-key

Pudel's public key for verifying challenge signatures.

**Auth:** Not required

**Response:**
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...",
  "algorithm": "EdDSA",
  "usage": "Use this key to verify challenge signatures from Pudel"
}
```

### GET /api/admin/challenge

Request an authentication challenge, signed by Pudel with its Ed25519 key.

**Auth:** Not required

**Response:**
```json
{
  "challengeId": "uuid",
  "nonce": "uuid",
  "timestamp": 1788540000000,
  "expiry": 1788540060000,
  "signature": "eyJhbGciOiJFZERTQSJ9...",
  "message": "Verify this signature with Pudel's public key, then submit your Discord user ID"
}
```

Challenges expire after **60 seconds** and are single use.

### GET /api/admin/check

Check if the current Discord user is a whitelisted admin.

**Auth:** browser session required

**Response:**
```json
{
  "isAdmin": true,
  "isAdminSession": false,
  "adminRole": "OWNER",
  "canModify": true,
  "canManageAdmins": true,
  "hasPublicKey": true,
  "message": "You can access the admin panel. Complete challenge verification to continue."
}
```

### POST /api/admin/auth/mutual

Authenticate with Mutual RSA — submit the signed challenge.

**Auth:** browser session required

**Request:**
```json
{
  "challengeId": "uuid-from-challenge",
  "signature": "base64-encoded-sha256withrsa-signature-of-nonce"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Mutual authentication successful",
  "discordUserId": "123456789012345678",
  "discordUsername": "YourUsername",
  "adminRole": "OWNER",
  "canModify": true,
  "canManageAdmins": true,
  "expiresAt": 1788543600000,
  "expiresIn": 3600
}
```

There is deliberately **no `adminToken` field** in the body.

### POST /api/admin/logout

Clear the admin token for the current browser key; the browser session itself
stays alive.

**Auth:** admin session required
**Response:** `{ "message": "Logged out successfully" }`

### POST /api/admin/swagger/authorize / DELETE /api/admin/swagger/authorize

Issue (or clear) the `pudel-swagger-session` HttpOnly cookie that unlocks
`/swagger-ui.html` and `/v3/api-docs`. Requires a valid admin session.

With `pudel.swagger.allow-query-token=true` the POST response additionally
carries a 30-second `queryToken` — off by default because URLs leak via logs,
history, proxies and Referer headers.

### POST /api/admin/auth ⚠️ REMOVED

The legacy OAuth admin login endpoints were **removed**. They are still
permit-listed in `SecurityConfiguration` for backward compatibility, but no
controller maps them, so they return **404**, not 410. Use
`/api/admin/auth/mutual`.

### GET /api/admin/status

Get system status.

**Auth:** browser session + admin session

**Response:**
```json
{
  "admin": {
    "discordUserId": "123456789012345678",
    "adminRole": "OWNER",
    "canModify": true,
    "canManageAdmins": true
  },
  "javaVersion": "25",
  "osName": "Linux",
  "memory": {
    "max": "2048 MB",
    "total": "512 MB",
    "free": "256 MB",
    "used": "256 MB"
  },
  "plugins": {
    "total": 5,
    "enabled": 3,
    "loaded": 3
  },
  "admins": {
    "total": 2,
    "enabled": 2
  }
}
```

### GET /api/admin/plugins

List all plugins.

**Auth:** browser session + admin session

**Response:**
```json
{
  "plugins": [
    {
      "id": 1,
      "name": "MyPlugin",
      "version": "1.0.0",
      "author": "Author",
      "description": "Description",
      "jarFile": "myplugin.jar",
      "enabled": true,
      "loaded": true,
      "loadError": null
    }
  ],
  "total": 1,
  "enabled": 1
}
```

### POST /api/admin/plugins/upload

Upload a plugin JAR file.

**Auth:** browser session + admin session (ADMIN+ role)

**Request:** `multipart/form-data` with `file` field

**Response:**
```json
{
  "success": true,
  "message": "Plugin uploaded successfully: myplugin.jar",
  "filename": "myplugin.jar",
  "size": 12345,
  "path": "/app/plugins/myplugin.jar"
}
```

### POST /api/admin/plugins/{name}/enable

Enable a plugin.

**Auth:** browser session + admin session (ADMIN+ role)

### POST /api/admin/plugins/{name}/disable

Disable a plugin.

**Auth:** browser session + admin session (ADMIN+ role)

### POST /api/admin/plugins/{name}/reload

Reload a plugin.

**Auth:** browser session + admin session (ADMIN+ role)

### DELETE /api/admin/plugins/{name}

Remove a plugin (unload and delete JAR).

**Auth:** browser session + admin session (ADMIN+ role)

### GET /api/admin/whitelist

List admin whitelist entries.

**Auth:** browser session + admin session (OWNER role only)

**Response:**
```json
{
  "admins": [
    {
      "id": 1,
      "discordUserId": "123456789012345678",
      "discordUsername": "OwnerUser",
      "adminRole": "OWNER",
      "enabled": true,
      "canModify": true,
      "canManageAdmins": true,
      "hasPublicKey": true,
      "lastLogin": "2026-02-03T10:00:00"
    }
  ],
  "total": 1,
  "enabled": 1
}
```

### POST /api/admin/whitelist

Add a Discord user to admin whitelist with their RSA public key.

**Auth:** browser session + admin session (OWNER role only)

**Request:**
```json
{
  "discordUserId": "987654321098765432",
  "discordUsername": "NewAdmin",
  "adminRole": "ADMIN",
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhki...\n-----END PUBLIC KEY-----",
  "note": "Added for plugin management"
}
```

> **Note:** `publicKeyPem` is **required** for Mutual RSA Authentication. The new admin must generate their own RSA keypair and provide their public key.

### PUT /api/admin/whitelist/{discordUserId}

Update an admin whitelist entry.

**Auth:** browser session + admin session (OWNER role only)

**Request:**
```json
{
  "adminRole": "MODERATOR",
  "enabled": false,
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nNEW_KEY...\n-----END PUBLIC KEY-----",
  "note": "Updated role and key"
}
```

### DELETE /api/admin/whitelist/{discordUserId}

Remove a Discord user from admin whitelist.

**Auth:** browser session + admin session (OWNER role only)

---

## Rate Limiting

There is no rate limiter in the API today — the documented limits below are
**not enforced** and no `X-RateLimit-*` headers are emitted. Put a reverse
proxy in front of the instance if you need them.

| Endpoint Type | Limit (unenforced) |
|---------------|--------------------|
| Authentication | 10/min |
| Read operations | 60/min |
| Write operations | 30/min |

---

## WebSocket

```
wss://<host>/ws/admin/logs
```

A single native WebSocket endpoint streams admin log entries. It is
authenticated during the handshake by `AdminLogHandshakeInterceptor`: the
session cookie is read, `dpop_keys.admin_token` is loaded and validated
(`sub = pudel-admin-session`, not expired), and the origin must be in
`pudel.cors.allowed-origins`. A failed handshake is rejected with 401.

There is no STOMP broker and no `/topic/...` destinations. Log history and
statistics are available over REST:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/logs?count=500&level=INFO` | Recent entries (max 5000) plus stats |
| GET | `/api/admin/logs/stats` | Aggregated log statistics |
| DELETE | `/api/admin/logs` | Clear the in-memory log buffer |

---

*API Version: 2.5.0*
