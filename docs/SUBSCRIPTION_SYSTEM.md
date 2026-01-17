# Subscription Tier System

This document explains how to configure subscription tiers for the Pudel Discord Bot.

## Overview

The subscription system allows hosters to define capacity limits for users and guilds based on their subscription tier. Each tier can have different limits for:

- **Dialogue History** - Number of conversation messages stored
- **Memory Entries** - Key-value memory storage capacity
- **Plugin Limit** - Maximum number of plugins enabled

## Configuration

All subscription tiers are configured in `subscription-tiers.yml` under `subscription.tiers`.

### Default Tiers

```yaml
subscription:
  tiers:
    FREE:
      name: "Free"
      description: "Basic free tier with limited capacity"
      user:
        dialogueLimit: 1000
        memoryLimit: 100
      guild:
        dialogueLimit: 5000
        memoryLimit: 500
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: false
        prioritySupport: false
        
    TIER_1:
      name: "Tier 1"
      description: "Basic paid subscription"
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
      name: "Tier 2"
      description: "Premium subscription"
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
      description: "Unlimited tier for special cases"
      user:
        dialogueLimit: -1  # -1 means unlimited
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
        
  defaultTier: FREE
  enableExpiration: true
```

### Adding Custom Tiers

Hosters can add their own tiers by adding entries to the `tiers` map:

```yaml
subscription:
  tiers:
    # ... existing tiers ...
    
    ENTERPRISE:
      name: "Enterprise"
      description: "Enterprise subscription with maximum limits"
      user:
        dialogueLimit: 10000
        memoryLimit: 2000
      guild:
        dialogueLimit: 50000
        memoryLimit: 10000
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: true
        prioritySupport: true
```

## API Endpoints

### Get All Tiers

```http
GET /api/subscription/tiers
```

Returns all configured subscription tiers with their limits and features.

### Get Guild Usage

```http
GET /api/subscription/guild/{guildId}/usage
```

Returns current usage statistics for a guild including:
- Current count vs limit for dialogue, memory
- Active tier name
- Subscription status (active/expired)

### Get User Usage

```http
GET /api/subscription/user/{userId}/usage
```

Returns current usage statistics for a user.

## Memory Management

### IVFFlat Index

For efficient semantic search, the system uses PostgreSQL's pgvector extension with IVFFlat indexing:

```yaml
pudel:
  chatbot:
    embedding:
      enabled: true
      dimension: 384      # Vector dimension
      ivfProbes: 10       # Higher = more accurate, slower
      ivfLists: 100       # Number of IVF lists
```

**Note**: Requires pgvector extension: `CREATE EXTENSION vector;`

### Auto-Cleanup

Old dialogue entries are automatically cleaned when reaching capacity:

```yaml
pudel:
  memory:
    autoCleanup:
      enabled: true
      keepPercentage: 80    # Keep newest 80% of entries
      minAgeDays: 30        # Only delete entries older than 30 days
```

## Chatbot Triggers

Configure when Pudel responds as a chatbot:

```yaml
pudel:
  chatbot:
    triggers:
      onMention: true       # Respond when @mentioned
      onDirectMessage: true # Always respond in DMs
      onReplyToBot: true    # Respond to replies
      keywords:             # Trigger on keywords
        - "pudel"
        - "hey pudel"
      alwaysActiveChannels: []  # Channel IDs where always active
    contextSize: 10         # Number of past messages for context
```

## Schema Isolation

Each guild and user has their own PostgreSQL schema:
- Guild schema: `guild_{guildId}`
- User schema: `user_{userId}`

This provides complete data isolation between guilds/users.

## Upgrading Subscriptions

Subscription upgrades are managed by the hoster through the database:

```sql
UPDATE subscriptions 
SET tier_name = 'TIER_2', updated_at = NOW()
WHERE target_id = '123456789' AND subscription_type = 'GUILD';
```

The limits are automatically applied from the YAML configuration on the next capacity check.