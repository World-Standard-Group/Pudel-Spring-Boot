# Pudel Legal Overview

Pudel is open-source software with a dual-license structure.

## Licensing Structure

| Component | License | Commercial Use |
|-----------|---------|----------------|
| `pudel-core` | AGPLv3 + Plugin Exception | Must open-source modifications |
| `pudel-api` | MIT | ✅ Proprietary plugins allowed |

## Plugin Development

Plugins built **only against** the official `pudel-api` (`group.worldstandard:pudel-api`) 
can be proprietary/commercial. See [PLUGIN_EXCEPTION](PLUGIN_EXCEPTION) for details.

### Important Restrictions

The Plugin Exception:
- **Only applies** to the official `pudel-api` from Upstream Pudel
- **Does NOT apply** to forks of Pudel
- **Does NOT transfer** to replacement APIs created by forks
- **Requires** plugins to run on unmodified Pudel

If you fork Pudel, any plugin API you create remains AGPLv3.

## Official Hosted Instance
- Governed by:
    - Terms of Service
    - Privacy Policy
    - Security Policy

## Self-Hosted Instances
- You are the data controller
- You must comply with AGPLv3
- Official Terms/Privacy/Security do not apply to your instance

## Legal Documents
- License: [LICENSE](LICENSE)
- Plugin Exception: [PLUGIN_EXCEPTION](PLUGIN_EXCEPTION)
- Terms of Service: [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md)
- Privacy Policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md)
- Security Policy: [SECURITY.md](SECURITY.md)
