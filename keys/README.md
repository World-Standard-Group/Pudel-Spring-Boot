# JWT RSA Keys

This directory should contain RSA key files for JWT token signing and verification.

## Generate RSA Key Pair

Run the following commands to generate the keys:

### Generate Private Key (PKCS#8 format)
```bash
openssl genpkey -algorithm RSA -out private.key -pkeyopt rsa_keygen_bits:2048
```

### Extract Public Key
```bash
openssl rsa -pubout -in private.key -out public.key
```

## File Structure

After generating, this directory should contain:
- `private.key` - RSA private key (PKCS#8 PEM format) - **KEEP SECRET!**
- `public.key` - RSA public key (X.509 PEM format)

## Important Security Notes

1. **Never commit private keys to version control!**
2. Add `private.key` and `public.key` to your `.gitignore`
3. Use environment variables `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` to specify custom paths in production
4. Ensure proper file permissions (private key should be readable only by the application user)

## Configuration

The application expects:
- Default private key path: `keys/private.key`
- Default public key path: `keys/public.key`

Override with environment variables:
```bash
export JWT_PRIVATE_KEY_PATH=/secure/path/private.key
export JWT_PUBLIC_KEY_PATH=/secure/path/public.key
```

Or in Docker:
```yaml
environment:
  JWT_PRIVATE_KEY_PATH: /app/secrets/private.key
  JWT_PUBLIC_KEY_PATH: /app/secrets/public.key
```
