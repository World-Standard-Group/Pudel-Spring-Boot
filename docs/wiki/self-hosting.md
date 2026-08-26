# Self-Hosting Guide

Complete guide for hosting your own Pudel instance.

## Prerequisites

| Component | Version | Required |
|-----------|---------|----------|
| Java | 25+     | Yes |
| Maven | 3.9+    | Yes |
| PostgreSQL | 18+     | Yes |
| pgvector | 0.5+    | Yes |
| Ollama | Latest  | Optional |
| Docker | 20+     | Optional |

---

## Option 1: Manual Installation

### 1. Install Java 25+

```bash
# Ubuntu/Debian
sudo apt install openjdk-25-jdk

# macOS (Homebrew)
brew install openjdk@25

# Windows - Download from Adoptium
```

Verify:
```bash
java --version
# openjdk 25 2025-09-16
```

### 2. Install PostgreSQL with pgvector

```bash
# Ubuntu/Debian
sudo apt install postgresql postgresql-contrib
sudo apt install postgresql-16-pgvector

# macOS
brew install postgresql@16
brew install pgvector

# Start PostgreSQL
sudo systemctl start postgresql
```

### 3. Create Database

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE pudel;
\c pudel
CREATE EXTENSION IF NOT EXISTS vector;
\q
```

### 4. Database Schema (no manual step)

Pudel defines its entire schema in Java. On startup `SchemaManagementService`
reconciles the live database — creating any missing tables/columns/indexes and
repairing existing schemas automatically. **You do not run `database/init.sql`
(removed) or any manual migration.** Just ensure the `vector` extension exists
(see Step 3) for pgvector-backed embeddings. Global `public` tables are managed
by Hibernate; per-guild/per-user schemas are managed by the reconciler.

### 5. Clone and Build

```bash
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd Pudel-Spring-Boot
mvn clean package -DskipTests
```

### 6. Configure

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
    oauth:
      client-id: YOUR_CLIENT_ID
      client-secret: YOUR_CLIENT_SECRET
      redirect-uri: http://localhost:5173/auth/callback
  
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: qwen3:8b
```

### 7. Generate JWT Keys

```bash
# Generate RSA key pair
openssl genrsa -out keys/jwt_private.key 2048
openssl rsa -in keys/jwt_private.key -pubout -out keys/jwt_public.key
```

### 8. Install Ollama (Optional)

```bash
# Linux/macOS
curl -fsSL https://ollama.ai/install.sh | sh

# Pull model
ollama pull qwen3:8b

# Start Ollama
ollama serve
```

### 9. Run Pudel

```bash
java -jar pudel-core/target/pudel-core-2.4.0.jar \
  --spring.profiles.active=local
```

---

## Option 2: Docker Installation

### 1. Create Environment File

Create `.env`:
```bash
DISCORD_BOT_TOKEN=your_bot_token
DISCORD_CLIENT_ID=your_client_id
DISCORD_CLIENT_SECRET=your_client_secret
DB_PASSWORD=secure_password
```

### 2. Generate Keys

```bash
mkdir -p keys
openssl genrsa -out keys/jwt_private.key 2048
openssl rsa -in keys/jwt_private.key -pubout -out keys/jwt_public.key
```

### 3. Start with Docker Compose

```bash
docker compose up -d
```

### 4. View Logs

```bash
docker compose logs -f pudel
```

---

## Discord Bot Setup

### 1. Create Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application"
3. Name it (any choice)

### 2. Create Bot

1. Go to "Bot" section
2. Click "Add Bot"
3. Copy the token

### 3. Enable Intents

Enable these Privileged Gateway Intents:
- ✅ PRESENCE INTENT
- ✅ SERVER MEMBERS INTENT
- ✅ MESSAGE CONTENT INTENT

### 4. OAuth2 Setup (for Dashboard)

1. Go to "OAuth2" section
2. Add redirect URI: `http://localhost:5173/auth/callback`
3. Copy Client ID and Client Secret

### 5. Generate Invite Link

OAuth2 URL Generator:
- Scopes: `bot`, `applications.commands`
- Permissions: Administrator (or selective)

---

## Production Deployment

### Systemd Service

Create `/etc/systemd/system/pudel.service`:

```ini
[Unit]
Description=Pudel Discord Bot
After=network.target postgresql.service

[Service]
Type=simple
User=pudel
WorkingDirectory=/opt/pudel
ExecStart=/usr/bin/java -Xmx2g -jar pudel-core-2.4.0.jar --spring.profiles.active=production
Restart=always
RestartSec=10

Environment=DISCORD_BOT_TOKEN=your_token
Environment=DB_PASSWORD=your_password

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable pudel
sudo systemctl start pudel
```

### Nginx Reverse Proxy

```nginx
server {
    listen 80;
    server_name pudel.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

### SSL with Certbot

```bash
sudo certbot --nginx -d pudel.yourdomain.com
```

---

## Updating

### Manual Update

```bash
cd Pudel-Spring-Boot
git pull
mvn clean package -DskipTests
sudo systemctl restart pudel
```

### Docker Update

```bash
docker compose pull
docker compose up -d
```

---

## Backup Strategy

### Database Backup

```bash
# Full backup
pg_dump pudel > pudel_backup_$(date +%Y%m%d).sql

# Automated daily backup (cron)
0 2 * * * pg_dump pudel | gzip > /backups/pudel_$(date +\%Y\%m\%d).sql.gz
```

### Plugin Backup

```bash
tar -czf plugins_backup.tar.gz plugins/
```

### Restore

```bash
psql pudel < pudel_backup.sql
tar -xzf plugins_backup.tar.gz
```

---

## Admin Portal (Self-Hosted Management)

The Admin Portal provides a web-based interface for managing your self-hosted Pudel instance. It uses **Mutual RSA Authentication** for secure admin access - each admin has their own RSA keypair.

### How Mutual RSA Authentication Works

```
┌────────────────────────────────────────────────────────────────────────┐
│                    MUTUAL RSA AUTHENTICATION FLOW                      │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌─────────┐                                          ┌─────────┐      │
│  │  Admin  │                                          │  Pudel  │      │
│  └────┬────┘                                          └────┬────┘      │
│       │                                                    │           │
│       │  1. Login with Discord OAuth                       │           │
│       │ ─────────────────────────────────────────────────► │           │
│       │                                                    │           │
│       │  2. Receive User JWT (contains discordUserId)      │           │
│       │ ◄───────────────────────────────────────────────── │           │
│       │                                                    │           │
│       │  3. Request Challenge                              │           │
│       │     GET /api/admin/challenge                       │           │
│       │ ─────────────────────────────────────────────────► │           │
│       │                                                    │           │
│       │  4. Challenge + Pudel's Signature                  │           │
│       │     (signed with Pudel's PRIVATE key)              │           │
│       │ ◄───────────────────────────────────────────────── │           │
│       │                                                    │           │
│       │  5. (Optional) Verify Pudel's signature            │           │
│       │     using Pudel's PUBLIC key                       │           │
│       │     (confirms server identity)                     │           │
│       │                                                    │           │
│       │  6. Sign challenge nonce with                      │           │
│       │     Admin's PRIVATE key                            │           │
│       │                                                    │           │
│       │  7. Submit signed challenge                        │           │
│       │     POST /api/admin/auth/mutual                    │           │
│       │     { challengeId, signature }                     │           │
│       │ ─────────────────────────────────────────────────► │           │
│       │                                                    │           │
│       │              8. Extract discordUserId from JWT     │           │
│       │              9. Lookup admin's PUBLIC key from DB  │           │
│       │             10. Verify signature with admin's key  │           │
│       │                                                    │           │
│       │ 11. AdminJWT (24-hour session token)               │           │
│       │ ◄───────────────────────────────────────────────── │           │
│       │                                                    │           │
│  ┌────┴────┐                                          ┌────┴────┐      │
│  │  Admin  │                                          │  Pudel  │      │
│  └─────────┘                                          └─────────┘      │
└────────────────────────────────────────────────────────────────────────┘
```

**Key Security Features:**
- **Mutual Authentication**: Both parties prove their identity cryptographically
- **Per-Admin Keys**: Each admin has their own RSA keypair
- **Private Keys Never Leave**: Admin signs in browser, private key never transmitted
- **Challenge Expiry**: 5-minute window prevents replay attacks

### Setting Up Initial Owner

The owner is the first admin who can add other admins. You need to:

1. **Generate Owner's RSA Keypair**
```bash
# Generate private key (keep this secure!)
openssl genrsa -out keys/owner_pv.key 2048

# Extract public key
openssl rsa -in keys/owner_pv.key -pubout -out keys/owner_pb.key
```

2. **Configure Environment**
```bash
# In your .env file
PUDEL_ADMIN_INITIAL_OWNER=123456789012345678  # Your Discord user ID
PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH=keys/owner_pb.key
```

3. **Secure Your Private Key**
```bash
chmod 600 keys/owner_pv.key
# Store owner_pv.key securely - you'll need it to login
# NEVER upload or share your private key
```

### Adding New Admins

New admins must generate their own RSA keypair and provide their **public key** to the owner.

**For the new admin:**
```bash
# Generate your keypair
openssl genrsa -out my_admin_pv.key 2048
openssl rsa -in my_admin_pv.key -pubout -out my_admin_pb.key

# Send my_admin_pb.key (PUBLIC key only!) to the owner
# Keep my_admin_pv.key secure on your machine
```

**For the owner (via Admin Portal or API):**
```bash
curl -X POST http://localhost:8080/api/admin/whitelist \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "discordUserId": "987654321098765432",
    "discordUsername": "NewAdmin",
    "adminRole": "ADMIN",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkq...\n-----END PUBLIC KEY-----",
    "note": "Added for plugin management"
  }'
```

### Admin Roles

| Role | Permissions |
|------|-------------|
| **OWNER** | Full access + can manage other admins |
| **ADMIN** | Full access to plugins and settings |
| **MODERATOR** | View-only access |

### Accessing the Admin Portal

1. Navigate to your Pudel dashboard
2. Click "Admin Portal" (requires Discord login first)
3. System checks if your Discord ID is in the whitelist
4. If whitelisted, request a challenge from Pudel
5. (Optional) Verify Pudel's signature to confirm server identity
6. Upload or paste your **private key** to sign the challenge
7. Click "Sign Challenge" - signing happens in your browser
8. Submit the signature - if valid, receive 24-hour AdminJWT

> ⚠️ **Your private key never leaves your browser.** The signing is done client-side using the Web Crypto API.

### Finding Your Discord User ID

1. Open Discord → User Settings
2. Go to Advanced → Enable **Developer Mode**
3. Right-click your profile picture
4. Click **Copy User ID**

### Admin Portal Features

| Feature | Description |
|---------|-------------|
| **Dashboard** | Overview of system status, memory, and plugin statistics |
| **Plugin Manager** | Upload, enable, disable, reload, and remove plugins |
| **Admin Whitelist** | Manage admin users and their public keys (OWNER only) |
| **System Status** | Monitor JVM memory, bot statistics, and version info |

### Managing Admin Whitelist

As an OWNER, you can manage other admins through the Admin Portal:

**Update admin's public key:**
```bash
curl -X PUT http://localhost:8080/api/admin/whitelist/987654321098765432 \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nNEW_KEY_HERE...\n-----END PUBLIC KEY-----"
  }'
```

**Remove an admin:**
```bash
curl -X DELETE http://localhost:8080/api/admin/whitelist/987654321098765432 \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### API Endpoints

| Method | Endpoint | Description | Auth | Role |
|--------|----------|-------------|------|------|
| GET | `/api/admin/public-key` | Get Pudel's public key | No | - |
| GET | `/api/admin/challenge` | Request authentication challenge | No | - |
| GET | `/api/admin/check` | Check if user is admin (requires Discord JWT) | Discord JWT | - |
| POST | `/api/admin/auth/mutual` | Authenticate with RSA signature | Discord JWT | - |
| POST | `/api/admin/logout` | Invalidate session | AdminJWT | Any |
| GET | `/api/admin/status` | Get system status | AdminJWT | Any |
| GET | `/api/admin/plugins` | List all plugins | AdminJWT | Any |
| GET | `/api/admin/plugins/files` | List plugin JAR files | AdminJWT | Any |
| POST | `/api/admin/plugins/upload` | Upload plugin JAR | AdminJWT | ADMIN+ |
| POST | `/api/admin/plugins/{name}/enable` | Enable plugin | AdminJWT | ADMIN+ |
| POST | `/api/admin/plugins/{name}/disable` | Disable plugin | AdminJWT | ADMIN+ |
| POST | `/api/admin/plugins/{name}/reload` | Reload plugin | AdminJWT | ADMIN+ |
| DELETE | `/api/admin/plugins/{name}` | Remove plugin | AdminJWT | ADMIN+ |
| GET | `/api/admin/whitelist` | List admin whitelist | AdminJWT | OWNER |
| POST | `/api/admin/whitelist` | Add admin with public key | AdminJWT | OWNER |
| PUT | `/api/admin/whitelist/{id}` | Update admin entry | AdminJWT | OWNER |
| DELETE | `/api/admin/whitelist/{id}` | Remove admin | AdminJWT | OWNER |

### Mutual Auth Request Body

```json
{
  "challengeId": "uuid-from-challenge-response",
  "signature": "base64-encoded-rsa-signature-of-nonce"
}
```

### Authentication Response

```json
{
  "success": true,
  "message": "Mutual authentication successful",
  "adminToken": "eyJhbGciOiJSUzI1NiJ9...",
  "discordUserId": "123456789012345678",
  "discordUsername": "YourUsername",
  "adminRole": "OWNER",
  "canModify": true,
  "canManageAdmins": true,
  "expiresAt": 1738627200000,
  "expiresIn": 86400
}
```

### Generating RSA Keys

**Using OpenSSL (Recommended):**
```bash
# Generate 2048-bit RSA private key (PKCS#8 format)
openssl genrsa -out private.key 2048

# Convert to PKCS#8 if needed (for browser compatibility)
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.key -out private_pkcs8.key

# Extract public key
openssl rsa -in private.key -pubout -out public.key
```

**Using Java keytool:**
```bash
keytool -genkeypair -alias admin -keyalg RSA -keysize 2048 -keystore admin.jks
keytool -exportcert -alias admin -keystore admin.jks -rfc -file public.pem
```

### Security Best Practices

- ✅ Generate unique RSA keypair for each admin
- ✅ Use 2048-bit or higher RSA keys
- ✅ Store private keys securely (chmod 600)
- ✅ Never share or upload private keys
- ✅ Use HTTPS in production to protect tokens
- ✅ Regularly rotate keys if compromised
- ✅ Use MODERATOR role for view-only users
- ✅ Audit admin whitelist regularly
- ✅ AdminJWT tokens expire after 24 hours
- ❌ Never commit private keys to version control
- ❌ Never transmit private keys over network
- ❌ Don't reuse keys across different systems

---

## Monitoring

### Health Check

```bash
curl http://localhost:8080/api/bot/status
```

### Log Monitoring

```bash
# Systemd
journalctl -u pudel -f

# Docker
docker compose logs -f pudel

# File logs
tail -f logs/pudel.log
```

### Memory Usage

```bash
# Check JVM memory
jcmd $(pgrep -f pudel) VM.native_memory summary
```

---

## Scaling

### Multiple Shards

For large bot (2500+ guilds):

```yaml
pudel:
  discord:
    sharding:
      enabled: true
      totalShards: 4
      shardIds: [0, 1, 2, 3]
```

### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

---

## Security Checklist

- [ ] Use strong database password
- [ ] Keep bot token secret
- [ ] Generate unique JWT RSA keys (2048-bit minimum)
- [ ] Secure private key with restricted permissions (chmod 600)
- [ ] Enable firewall (only expose 80/443)
- [ ] Regular security updates
- [ ] Use HTTPS for dashboard and API
- [ ] Restrict database access
- [ ] Never commit secrets to version control
- [ ] Use separate keys for production and development

**Admin Portal Security:**
- [ ] Generate owner RSA keypair before first run
- [ ] Set `PUDEL_ADMIN_INITIAL_OWNER` with your Discord ID
- [ ] Set `PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH` to owner's public key
- [ ] Store owner's private key securely (never on server)
- [ ] Each admin should have their own RSA keypair
- [ ] Never share or transmit private keys
- [ ] Use MODERATOR role for view-only access
- [ ] Regularly audit the admin whitelist
- [ ] Rotate keys if compromised

---

## Troubleshooting

### Bot won't connect

1. Check token is correct
2. Verify intents are enabled
3. Check firewall allows Discord

### Database errors

1. PostgreSQL running?
2. pgvector installed?
3. Credentials correct?

### Out of memory

1. Increase heap: `-Xmx4g`
2. Use smaller Ollama model
3. Enable auto-cleanup

---

*Happy hosting!* 🏠
