# Security Policy

**Project**: Pudel Discord Bot  
**Maintainer**: World Standard Group  
**Version**: Since v2.2.1
**Last Updated**: March 18, 2026

---

## Supported Versions

Security updates are provided for the following versions:

| Version | Supported      | Until         |
|---------|----------------|---------------|
| 2.2.x   | Active support | TBD           |
| <2.1.x  | End of Life    | Not supported    |

We strongly recommend running the latest release at all times.

---

## Introduction

At World Standard Group, we take the security of Pudel and our users' data seriously. This Security Policy outlines our security practices, how we protect the Official Instance, and how security researchers and users can report vulnerabilities to us. This policy applies to the Official Instance of the Pudel Discord bot. For Self-Hosted Instances, please refer to the specific guidelines in Section below.

---

## Self-Hosted Security Guidelines

If you choose to self-host Pudel under the AGPLv3 license, you are solely responsible for the security of your instance. We strongly recommend the following practices:

1. **Keep Software Updated**: Regularly pull the latest commits from the main repository to ensure you have the latest security patches.
2. **Secure Your Database**: Ensure your PostgreSQL instance is not exposed to the public internet and uses strong authentication.
3. **Protect Your Tokens**: Never commit your Discord Bot Token or internal API keys to public repositories. Use environment variables (`.env`).
4. **Isolate AI Models**: If using local AI models (e.g., Ollama), ensure the AI service API is restricted to localhost or protected by a firewall.

---

## Scope

In scope:

- pudel-core
- pudel-api
- pudel-model
- Official dashboard
- Official hosted instance (worldstandard.group)

Out of scope:

- Third-party plugins
- Self-hosted deployments not operated by World Standard Group
- Denial of Service attacks
- Social engineering attacks

---

## Reporting a Vulnerability

We deeply appreciate the community's efforts in discovering and reporting security vulnerabilities responsibly. If you believe you have found a security vulnerability in Pudel, please report it to us immediately.

### 📧 Private Disclosure (Preferred)

**Email**: [IT Department](mailto:it.department@worldstandard.group)  
**Subject**: `[SECURITY] Pudel — <brief description>`

### 🔐 Encrypted Reports (Recommended)

PGP Key: [pgp-key.txt](https://worldstandard.group/.well-known/pgp-key.txt)\
Fingerprint: EECC 98B3 6C89 F0DB 6EC9 47E3 477F A9A7 5F1A 8B1E

You may encrypt sensitive vulnerability details using our public key.

### 🔒 GitHub Security Advisories

You can also report vulnerabilities through [GitHub Security Advisories](https://github.com/World-Standard-Group/Pudel-Spring-Boot/security/advisories/new).

### What to Include

When reporting a vulnerability, please provide:

- **Description** — A clear explanation of the vulnerability
- **Affected component** — Module (`pudel-core`, `pudel-api`, `pudel-model`), class, or endpoint
- **Reproduction steps** — Detailed steps to reproduce the issue
- **Impact assessment** — What an attacker could achieve
- **Proof of Concept** — Code, request/response logs, or screenshots (if applicable)
- **Suggested fix** — If you have a remediation in mind (optional)

### Response Timeline

| Stage | Timeframe |
|-------|-----------|
| Acknowledgment | Within **48 hours** |
| Initial assessment | Within **5 business days** |
| Patch for critical issues | Within **14 days** |
| Public disclosure | After fix is released, coordinated with reporter |

### ⚠️ Please Do NOT

- Open a public GitHub issue for security vulnerabilities
- Disclose the vulnerability publicly before a fix is available
- Exploit the vulnerability against the official hosted instance or other users' instances
- Access, modify, or delete data belonging to other users during testing

---

## Security Architecture Overview

Pudel implements a layered security model with multiple authentication mechanisms and data isolation patterns. All official services enforce HTTPS with TLS 1.3 or higher. For full architectural details, see [ARCHITECTURE.md](docs/flowchart/architecture/ARCHITECTURE.md).

### Authentication Layers

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| **User Auth** | Discord OAuth2 → RSA-signed JWT (7-day expiry) | Dashboard and API access |
| **DPoP (RFC 9449)** | Proof-of-Possession token binding | Prevents stolen token reuse |
| **Admin Auth** | Mutual RSA challenge-response (1-hour session) | Admin panel access with per-admin keypairs |

### Token Security

- **RSA-signed JWTs** — Tokens are signed with the server's RSA-4096 private key and verified with the public key
- **DPoP binding** — Optional cryptographic binding of tokens to the client's keypair; even if a JWT is intercepted, it cannot be used without the client's private key
- **JTI replay protection** — Each DPoP proof includes a unique `jti` claim with a 5-minute deduplication window
- **Short-lived admin sessions** — Admin JWTs expire after 1 hour

### Data Isolation

- **Per-guild PostgreSQL schemas** — Each guild's data (conversation history, memory embeddings, plugin data) is stored in an isolated `guild_{id}` schema
- **Plugin database isolation** — Plugins receive isolated storage via `PluginDatabaseManager`; they cannot access other plugins' data or core tables
- **Plugin key-value store** — Scoped per-plugin to prevent cross-plugin data leakage

### Plugin Sandboxing

- **Custom class loader** — Plugins are loaded via `PluginClassLoader` with controlled access
- **Two-tier control** — Admin-level global enable/disable + guild-level per-server toggle
- **Hot-reload** — Plugins are fully unloaded (handlers deregistered, resources released) before reloading
- **Graceful shutdown** — `@OnShutdown` lifecycle hook allows plugins to clean up; forceful unload on failure

---

## Secure Configuration

### Secrets Management

The following secrets **must** be kept confidential and **never** committed to version control:

| Secret | Environment Variable | Description |
|--------|---------------------|-------------|
| Discord Bot Token | `DISCORD_BOT_TOKEN` | Full access to the bot's Discord account |
| Database Password | `POSTGRES_PASSWORD` | PostgreSQL database credentials |
| JWT Private Key | `JWT_PRIVATE_KEY_PATH` | RSA private key for signing JWTs |
| Admin Public Key | `PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH` | Owner's RSA public key for admin auth |

### Recommendations

1. **Rotate JWT keys** periodically — Generate new RSA keypairs and redeploy
2. **Use strong database passwords** — Minimum 32 characters, randomly generated
3. **Restrict database access** — Only allow connections from the application host
4. **Run behind a reverse proxy** — Use Nginx or Traefik with TLS termination
5. **Enable DPoP** — Clients should use DPoP token binding for theft-protected sessions
6. **Restrict Swagger UI in production** — Set `SWAGGER_ENABLED=false` or limit access via firewall
7. **Keep Ollama local** — Ollama API must be bound to localhost only (127.0.0.1)
8. **Secure the `keys/` directory** — Restrict file permissions (`chmod 600` on key files)
9. **Secure the `plugins/` directory** — Only load trusted plugin JARs; the plugin system executes arbitrary code

### Docker Deployment

When running with Docker:

- Do **not** expose port `5432` (PostgreSQL) to the public internet
- Do **not** expose port `11434` (Ollama) to the public internet
- Use Docker secrets or environment variable files (`.env`) instead of inline secrets in `docker-compose.yml`
- Mount `keys/` and `plugins/` as read-only volumes where possible

---

## Dependency Security

Pudel is built on the following core dependencies:

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 4.1.0-M2 | Application framework |
| JDA | 6.3.1 | Discord Gateway + REST |
| Jackson | 3.0.4 | JSON serialization |
| SLF4J | 2.0.17 | Logging |
| PostgreSQL + pgvector | — | Database + vector embeddings |

We actively monitor dependencies for known vulnerabilities (CVEs). If you discover a vulnerable transitive dependency, please report it using the process above.

### For Plugin Developers

- Only depend on the official `pudel-api` (`group.worldstandard:pudel-api`) that published on Maven Central to interact with `pudel-core`
- Keep your plugin dependencies up to date
- Do not bundle vulnerable or unnecessary transitive dependencies in your plugin JAR
- Follow the [Plugin Development Guide](https://worldstandard.group/wiki) for security best practices

---

## Known Security Considerations

### AI / LLM

- Pudel connects to a **local** Ollama instance — no data is sent to external AI providers by default
- Conversation data stored in `conversation_history` and `memory_embeddings` tables is per-guild isolated
- Prompt injection via user messages is mitigated through intent analysis and personality engine guardrails, but operators should be aware of inherent LLM limitations

### Plugin System

- Plugins execute within the bot's JVM process — a malicious plugin has access to the runtime
- Only load plugins from **trusted sources**
- The `pudel-api` module (MIT licensed) defines the boundary; plugins should not access `pudel-core` internals

### Discord OAuth

- OAuth tokens received from Discord are exchanged for Pudel-issued JWTs; Discord tokens are not stored long-term
- The bot requires only the scopes necessary for operation (`identify`, `guilds`)

---

## CVE Assignment

We may assign CVE identifiers for eligible vulnerabilities and publicly disclose them after a fix is released.

---

## Safe Harbor

We will not pursue legal action against researchers who:

- Act in good faith
- Do not access or modify other users' data
- Do not disrupt service availability
- Follow responsible disclosure practices

We consider such research authorized.

---

## Security Hall of Fame

We appreciate security researchers who help keep Pudel safe. With your permission, we will acknowledge your contribution here after a fix is released.

*No entries yet — be the first!*

---

## Related Documents

- [Architecture](docs/flowchart/architecture/ARCHITECTURE.md) — Full system architecture including auth flows
- [Privacy Policy](PRIVACY_POLICY.md) — Data collection and handling practices
- [Terms of Service](TERMS_OF_SERVICE.md) — Usage terms for the official hosted instance
- [License](LICENSE) — AGPLv3 with Plugin Exception
- [Legal Overview](LEGAL.md) — Licensing structure summary