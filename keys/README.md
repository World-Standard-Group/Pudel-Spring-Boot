# Signing and Credential Keys

`keys/` holds the key material Pudel needs at runtime. All of it is gitignored
(only this README is tracked).

## JWT Signing Keys (Ed25519)

Every server-issued token — session, admin, swagger — is signed with **Ed25519**
(`Jwts.SIG.EdDSA`, `KeyFactory("EdDSA")`). RSA keys will make startup fail with
`Failed to initialize JWT keys`.

### Generate the Key Pair

```bash
openssl genpkey -algorithm ED25519 -out pv.key
openssl pkey -in pv.key -pubout -out pb.key
```

### File Structure

- `pv.key` — Ed25519 private key (PKCS#8 PEM) — **KEEP SECRET!**
- `pb.key` — Ed25519 public key (X.509 PEM)

### Important Security Notes

1. **Never commit private keys to version control!**
2. Add `pv.key` and `pb.key` to your `.gitignore`
3. Use environment variables to specify custom paths in production
4. Restrict file permissions (`chmod 600 pv.key`, `chmod 644 pb.key`)
5. Rotating the pair invalidates every issued token and every browser session
   key bound to it

## Configuration

The application expects:
- Default private key path: `keys/pv.key` (local) or `/app/keys/pv.key` (Docker)
- Default public key path: `keys/pb.key` (local) or `/app/keys/pb.key` (Docker)

Override with environment variables in `.env`:
```bash
JWT_PRIVATE_KEY_FILE=pv.key
JWT_PUBLIC_KEY_FILE=pb.key
JWT_PRIVATE_KEY_PATH=keys/pv.key
JWT_PUBLIC_KEY_PATH=keys/pb.key
```

## Docker Usage

When running with Docker, the keys directory is mounted as a read-only volume:

```yaml
services:
  pudel:
    volumes:
      - ./keys:/app/keys:ro  # Mount local keys directory (read-only for security)
    environment:
      JWT_PRIVATE_KEY_PATH: /app/keys/${JWT_PRIVATE_KEY_FILE:-pv.key}
      JWT_PUBLIC_KEY_PATH: /app/keys/${JWT_PUBLIC_KEY_FILE:-pb.key}
```

### Steps for Docker Deployment:

1. Generate keys locally in the `keys/` directory:
   ```bash
   cd keys
   openssl genpkey -algorithm RSA -out pv.key -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in pv.key -out pb.key
   ```

2. Ensure proper file permissions:
   ```bash
   chmod 600 keys/pv.key
   chmod 644 keys/pb.key
   ```

3. Start Docker containers:
   ```bash
   docker-compose up -d
   ```

The keys will be automatically mounted into the container at `/app/keys/`.

---

# Other Key Material in `keys/`

| File | Purpose |
|------|---------|
| `pv.key` / `pb.key` | Ed25519 JWT signing pair (required) |
| `owner_pb.key` | Owner's **RSA** public key for the initial admin whitelist entry (`PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH`). Each admin's own keypair is RSA and their public key is stored in the `admin_whitelist` table |
| `cookie.key` | AES-256 key for the encrypted session cookie, generated on first start unless `SESSION_KEY` is set |
| `ca.crt`, `client.crt`, `client.pk8` | PostgreSQL mTLS material (external DB only, see below) |

Note the asymmetry: **Pudel** signs with Ed25519, while **admins** sign the login
challenge with `SHA256withRSA`. Generate the two key types accordingly.

---

# PostgreSQL: Local vs External (verify-full mTLS)

`scripts/start.sh` picks the database deployment from `POSTGRES_SSL`:

- **`POSTGRES_SSL=false` → LOCAL.** The bundled `postgres` container starts
  (compose `local` profile) with no TLS. No certificates needed. This is the
  default, so a fresh clone works out of the box.
- **`POSTGRES_SSL=true` → EXTERNAL.** No local container is started; the app
  connects to a remote Postgres (`POSTGRES_HOST`) using `sslmode=verify-full`
  with a client certificate (mTLS). You provide the client material below.

## Required files in `keys/` (EXTERNAL mode only)

- `ca.crt` — CA that signed the **remote server's** certificate (JDBC `sslrootcert`)
- `client.crt` — your client certificate (JDBC `sslcert`)
- `client.pk8` — your client private key in **PKCS#8** format (JDBC `sslkey`)

There are **no `server.crt` / `server.key`** here — the TLS server certificate
lives on your external database, not in this project.

## Permissions

```bash
chmod 600 keys/client.pk8
chmod 644 keys/ca.crt keys/client.crt
```

## Enable via `.env`

```bash
POSTGRES_SSL=true
POSTGRES_SSL_MODE=verify-full          # start.sh requires exactly this for external
POSTGRES_HOST=db.example.com           # your external host (not localhost/postgres)
POSTGRES_PORT=5432
# Paths as seen inside the container (./keys is mounted at /app/keys):
POSTGRES_SSL_CA_CERT=/app/keys/ca.crt
POSTGRES_SSL_CLIENT_CERT=/app/keys/client.crt
POSTGRES_SSL_CLIENT_KEY=/app/keys/client.pk8
```

For a local (non-Docker) run, use relative paths instead (e.g. `keys/ca.crt`).
Set `POSTGRES_SSL=false` for the bundled local database (no certs required).

