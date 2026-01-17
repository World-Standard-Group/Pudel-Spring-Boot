# Pudel Privacy Policy

**Effective Date**: January 1, 2026
**Last Updated**: January 17, 2026

---

## 1. Introduction

This Privacy Policy describes how World Standard Group ("we," "us," or "our") collects, uses, and protects information when you use the Pudel Discord bot and related services (the "Service").

We are committed to protecting your privacy and handling your data responsibly. Please read this policy carefully to understand our practices.

---

## 2. Information We Collect

### 2.1 Information from Discord

When you use Pudel, we receive information from Discord, including:

| Data Type | Purpose | Retention |
|-----------|---------|-----------|
| **User ID** | Identify users across sessions | While using service |
| **Username** | Display in dashboard and logs | While using service |
| **Avatar** | Display in web dashboard | Session only |
| **Guild ID** | Identify servers using Pudel | While bot is in guild |
| **Guild Name** | Display in dashboard | While bot is in guild |
| **Message Content** | Process commands and AI responses | Per subscription tier |
| **Role Information** | Permission checking | Session only |

### 2.2 Information You Provide

We collect information you directly provide:

- **Bot Configuration**: Prefix, enabled features, channel settings
- **Personality Settings**: Biography, personality, preferences, dialogue style
- **Subscription Information**: Payment details (processed by third-party providers)
- **Plugin Marketplace**: Plugin descriptions, source links, author information

### 2.3 Automatically Collected Information

We automatically collect:

- **Usage Data**: Commands used, features accessed, interaction patterns
- **Conversation Data**: Messages in channels where Pudel is active (for AI context)
- **Technical Data**: Error logs, performance metrics, API request logs
- **Memory Embeddings**: Vector representations of conversations for semantic search

### 2.4 Information from Third Parties

We may receive information from:

- **Discord OAuth**: When you log into our dashboard
- **Payment Processors**: Subscription status (no payment details stored)
- **Ollama/AI Services**: AI model responses (processed locally when self-hosted)

---

## 3. How We Use Your Information

We use collected information to:

### 3.1 Provide the Service

- Process commands and generate AI responses
- Store conversation context for personalized interactions
- Manage guild settings and configurations
- Enable plugin functionality

### 3.2 Improve the Service

- Analyze usage patterns to improve features
- Debug errors and fix technical issues
- Develop new features based on user needs
- Optimize performance and response quality

### 3.3 Security and Compliance

- Detect and prevent abuse or fraud
- Enforce our Terms of Service
- Comply with legal obligations
- Protect users and our systems

### 3.4 Communication

- Respond to support requests
- Send important service announcements
- Notify about policy changes
- Provide subscription updates

---

## 4. Data Storage and Isolation

### 4.1 Per-Guild Schemas

We implement strong data isolation:

```
PostgreSQL Database
├── public (shared metadata)
├── guild_<id_1> (Guild 1 data)
│   ├── dialogue_history
│   ├── memory_embeddings
│   └── user_preferences
├── guild_<id_2> (Guild 2 data)
│   └── ...
└── user_<id> (DM data)
    └── ...
```

- Each guild has its own database schema
- Guild data is never shared with other guilds
- User DM data is stored in separate schemas
- Database credentials are isolated per schema

### 4.2 Memory and Conversation Data

| Subscription Tier | User Memory Limit | Guild Memory Limit |
|------------------|-------------------|-------------------|
| Free | 1,000 rows | 5,000 rows |
| Tier 1 | 1,500 rows | 7,500 rows |
| Tier 2 | 2,000 rows | 10,000 rows |
| Unlimited | No limit | No limit |

- Older data is automatically pruned when limits are reached
- Memory embeddings are stored using pgvector with IVFFlat indexing
- Conversation context is used for AI responses within the same guild

### 4.3 Data Location

- **Official Instance**: Data is stored on servers operated by the official instance operator
- **Self-Hosted**: Data is stored wherever you choose to host

---

## 5. Data Sharing

### 5.1 We Do NOT Sell Your Data

We never sell, rent, or trade your personal information to third parties for marketing purposes.

### 5.2 Limited Sharing

We may share information only in these cases:

| Recipient | Data Shared | Purpose |
|-----------|-------------|---------|
| **Discord** | Commands, responses | Bot operation |
| **Ollama (local)** | Message content | AI generation |
| **Payment Processor** | Subscription info | Billing |
| **Plugins** | As needed by plugin | Plugin functionality |
| **Legal Authorities** | As required | Legal compliance |

### 5.3 Plugin Data Access

Third-party plugins may access:

- Message content in channels where they operate
- User IDs and guild IDs
- Configuration data relevant to the plugin

Plugin developers are bound by their own privacy policies. Review plugin privacy practices before installation.

---

## 6. Data Retention

### 6.1 Active Data

| Data Type | Retention Period |
|-----------|-----------------|
| Guild Settings | While bot is in guild |
| User Preferences | While user is active |
| Conversation Memory | Per subscription tier limits |
| Command Logs | 30 days |
| Error Logs | 7 days |

### 6.2 After Removal

When Pudel is removed from a guild:

1. **Immediate**: Bot stops processing messages
2. **24 hours**: Settings marked for deletion
3. **7 days**: Guild schema and all data deleted
4. **Backup**: Removed from backups within 30 days

### 6.3 Account Deletion

You can request complete data deletion by:

1. Contacting us via Discord or email
2. Removing Pudel from all your servers
3. We will delete all associated data within 30 days

---

## 7. Your Rights

### 7.1 Access

You have the right to:

- View your data via the web dashboard
- Request a copy of your data
- See what information we have about you

### 7.2 Correction

You can:

- Update your guild settings anytime
- Modify Pudel's personality configuration
- Correct inaccurate information

### 7.3 Deletion

You can:

- Delete conversation history
- Clear memory embeddings
- Request complete account deletion
- Remove Pudel from your server (triggers data deletion)

### 7.4 Portability

Upon request, we can provide your data in a machine-readable format (JSON).

### 7.5 Objection

You can:

- Disable AI features (no conversation processing)
- Set channels to ignore (no data collection)
- Opt out of specific features

---

## 8. Security

### 8.1 Technical Measures

We implement:

- **Encryption**: TLS for data in transit, encryption at rest
- **Access Control**: Role-based access, principle of least privilege
- **Schema Isolation**: PostgreSQL schema-level data separation
- **Authentication**: JWT tokens with expiration, Discord OAuth
- **Rate Limiting**: Protection against abuse and DDoS

### 8.2 Operational Measures

- Regular security audits
- Dependency vulnerability scanning
- Secure development practices
- Incident response procedures

### 8.3 Breach Notification

In the event of a data breach:

1. We will investigate immediately
2. Affected users will be notified within 72 hours
3. We will report to authorities as required by law
4. We will provide guidance on protective measures

---

## 9. Children's Privacy

### 9.1 Age Requirement

Pudel is not intended for users under 13 years of age. We do not knowingly collect data from children under 13.

### 9.2 Discord's Policies

Users must comply with Discord's age requirements. Discord requires users to be at least 13 years old (or older in some jurisdictions).

### 9.3 Discovery of Underage Users

If we discover we have collected data from a child under 13, we will:

1. Delete the data immediately
2. Take steps to prevent future collection
3. Notify parents/guardians if possible

---

## 10. International Users

### 10.1 Data Transfer

Your data may be transferred to and processed in countries outside your residence. We ensure appropriate safeguards are in place.

### 10.2 GDPR (European Users)

If you are in the European Economic Area:

- **Legal Basis**: We process data based on consent, contract, and legitimate interests
- **Data Controller**: World Standard Group
- **Rights**: Access, rectification, erasure, portability, objection
- **Complaints**: You may lodge complaints with your local supervisory authority

### 10.3 CCPA (California Users)

If you are a California resident:

- **Right to Know**: What personal information we collect
- **Right to Delete**: Request deletion of personal information
- **Right to Opt-Out**: We do not sell personal information
- **Non-Discrimination**: We will not discriminate for exercising rights

---

## 11. Self-Hosted Instances

### 11.1 Your Responsibility

If you self-host Pudel:

- You are the data controller
- You are responsible for compliance with privacy laws
- You must create your own privacy policy
- This policy does not apply to self-hosted instances

### 11.2 Our Role

For self-hosted instances:

- We provide the software only
- We do not have access to your data
- We are not responsible for your data handling
- We do not provide privacy compliance support

---

## 12. Cookies and Tracking

### 12.1 Web Dashboard

Our web dashboard uses:

| Cookie/Storage | Purpose | Duration |
|---------------|---------|----------|
| `auth_token` | Authentication | 24 hours |
| `user_preferences` | UI settings | 30 days |

### 12.2 No Third-Party Tracking

- We do not use advertising trackers
- We do not use social media trackers
- We do not sell data to advertisers
- We use minimal analytics for improvement only

---

## 13. Changes to This Policy

### 13.1 Notification

We will notify you of material changes by:

- Posting the new policy on our website
- Announcing in our Discord server
- Sending email if we have your address

### 13.2 Continued Use

Continued use of Pudel after changes constitutes acceptance of the updated policy.

### 13.3 Review Frequency

We review this policy at least annually and update as needed.

---

## 14. Contact Us

For privacy-related questions or requests:

**World Standard Group**  
Email: -
Discord: [Pudel Support Server (TBA)](https://discord.gg/pudel)

### Data Protection Inquiries

For data access, deletion, or other rights requests:

1. Join our Discord support server
2. Open a ticket in #privacy-requests
3. Or email 

We will respond within 30 days.

---

## 15. Summary

| What We Collect | Why | How Long |
|-----------------|-----|----------|
| Discord User ID | Identify you | While active |
| Messages | AI responses | Per tier limit |
| Guild Settings | Configuration | While in guild |
| Memory | Context | Per tier limit |

| What We Do | What We Don't |
|------------|---------------|
| ✅ Store data in isolated schemas | ❌ Sell your data |
| ✅ Use data for AI features | ❌ Share with advertisers |
| ✅ Delete on request | ❌ Track across sites |
| ✅ Protect with encryption | ❌ Store unnecessary data |

---

*Last updated: January 17, 2026*

