-- ============================================
-- Pudel Discord Bot - Complete Database Schema
-- PostgreSQL 12+ with pgvector extension
-- Version: 1.0.0 (Consolidated)
--
-- This file contains the complete schema including all migrations.
-- Use this for fresh installations.
-- ============================================

-- Create database (run as superuser)
-- CREATE DATABASE pudel;

-- Connect to pudel database
-- \c pudel;

-- Enable pgvector extension (requires superuser)
CREATE EXTENSION IF NOT EXISTS vector;

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS public;

-- ============================================
-- Core Tables
-- ============================================

-- Users Table (Discord users who interact with Pudel)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    discriminator VARCHAR(10),
    avatar VARCHAR(255),
    email VARCHAR(255),
    verified BOOLEAN,
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Guilds Table (Discord servers with Pudel)
CREATE TABLE IF NOT EXISTS guilds (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(255) NOT NULL UNIQUE,
    guild_name VARCHAR(255) NOT NULL,
    icon VARCHAR(255),
    owner_id VARCHAR(255),
    bot_joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User-Guild Relationship (which users are in which guilds)
CREATE TABLE IF NOT EXISTS user_guilds (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    guild_id VARCHAR(255) NOT NULL,
    permissions BIGINT,
    owner BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, guild_id)
);

-- Guild Settings Table (per-guild bot configuration)
-- Column names match the JPA entity GuildSettings.java
CREATE TABLE IF NOT EXISTS guild_settings (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(255) NOT NULL UNIQUE,

    -- Basic settings
    prefix VARCHAR(10) DEFAULT '!',
    verbosity INT DEFAULT 3,
    cooldown FLOAT DEFAULT 0,
    log_channel VARCHAR(255),
    bot_channel VARCHAR(255),

    -- AI personality settings
    biography TEXT,
    personality TEXT,
    preferences TEXT,
    dialogue_style TEXT,
    nickname VARCHAR(255) DEFAULT 'Pudel',
    language VARCHAR(10) DEFAULT 'en',

    -- Response style settings
    response_length VARCHAR(50) DEFAULT 'medium',
    formality VARCHAR(50) DEFAULT 'balanced',
    emote_usage VARCHAR(20) DEFAULT 'moderate',
    quirks TEXT,
    topics_interest TEXT,
    topics_avoid TEXT,

    -- AI and system settings
    ai_enabled BOOLEAN DEFAULT TRUE,
    system_prompt_prefix TEXT,
    ignored_channels TEXT,
    disabled_commands TEXT,

    -- Schema management
    schema_created BOOLEAN DEFAULT FALSE,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add comments for guild_settings columns
COMMENT ON COLUMN guild_settings.biography IS 'Bot biography/backstory for AI personality';
COMMENT ON COLUMN guild_settings.prefix IS 'Command prefix for the guild';
COMMENT ON COLUMN guild_settings.verbosity IS 'Response verbosity level (1-5)';
COMMENT ON COLUMN guild_settings.cooldown IS 'Command cooldown in seconds';
COMMENT ON COLUMN guild_settings.log_channel IS 'Channel ID for logging';
COMMENT ON COLUMN guild_settings.bot_channel IS 'Primary bot channel ID';
COMMENT ON COLUMN guild_settings.response_length IS 'Preferred response length: short, medium, long';
COMMENT ON COLUMN guild_settings.formality IS 'Response formality: casual, balanced, formal';
COMMENT ON COLUMN guild_settings.emote_usage IS 'Emoji usage level: none, minimal, moderate, frequent';
COMMENT ON COLUMN guild_settings.quirks IS 'Custom speech patterns/catchphrases for natural responses';
COMMENT ON COLUMN guild_settings.topics_interest IS 'Topics Pudel should be more engaged about';
COMMENT ON COLUMN guild_settings.topics_avoid IS 'Topics Pudel should politely redirect away from';
COMMENT ON COLUMN guild_settings.ai_enabled IS 'Whether AI/chatbot features are enabled for this guild';
COMMENT ON COLUMN guild_settings.system_prompt_prefix IS 'Custom system prompt prefix for LLM customization per guild';
COMMENT ON COLUMN guild_settings.ignored_channels IS 'Comma-separated list of channel IDs to completely ignore';

-- ============================================
-- Plugin Tables
-- ============================================

-- Plugin Metadata Table (loaded plugins)
CREATE TABLE IF NOT EXISTS plugin_metadata (
    id BIGSERIAL PRIMARY KEY,
    plugin_name VARCHAR(255) NOT NULL UNIQUE,
    plugin_version VARCHAR(50) NOT NULL,
    plugin_author VARCHAR(255),
    plugin_description TEXT,
    jar_file_name VARCHAR(255) NOT NULL,
    main_class VARCHAR(255) NOT NULL,
    jar_hash VARCHAR(64),
    enabled BOOLEAN DEFAULT FALSE,
    loaded BOOLEAN DEFAULT FALSE,
    load_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Market Plugins Table (plugin marketplace - community-driven)
CREATE TABLE IF NOT EXISTS market_plugins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    category VARCHAR(50) DEFAULT 'other',
    author_id VARCHAR(255) NOT NULL,
    author_name VARCHAR(255),
    version VARCHAR(20) NOT NULL,
    downloads BIGINT DEFAULT 0,
    source_url VARCHAR(255) NOT NULL,

    -- Licensing fields
    license_type VARCHAR(20) DEFAULT 'MIT',
    is_commercial BOOLEAN DEFAULT FALSE,
    price_cents INTEGER DEFAULT 0,
    contact_email VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- Subscription Tables
-- ============================================

-- Subscriptions Table (capacity limits per user/guild)
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGSERIAL PRIMARY KEY,
    target_id VARCHAR(255) NOT NULL,
    subscription_type VARCHAR(20) NOT NULL,  -- USER or GUILD
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    tier_name VARCHAR(50) NOT NULL DEFAULT 'FREE',
    dialogue_limit BIGINT DEFAULT 5000,
    memory_limit BIGINT DEFAULT 500,
    plugin_limit INTEGER DEFAULT -1,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(target_id, subscription_type)
);

-- ============================================
-- Indexes
-- ============================================

-- Core table indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_guilds_guild_id ON guilds(guild_id);
CREATE INDEX IF NOT EXISTS idx_user_guilds_user ON user_guilds(user_id);
CREATE INDEX IF NOT EXISTS idx_user_guilds_guild ON user_guilds(guild_id);

-- Guild settings indexes
CREATE INDEX IF NOT EXISTS idx_guild_settings_guild_id ON guild_settings(guild_id);
CREATE INDEX IF NOT EXISTS idx_guild_settings_ai_enabled ON guild_settings(ai_enabled);

-- Plugin indexes
CREATE INDEX IF NOT EXISTS idx_plugin_metadata_name ON plugin_metadata(plugin_name);
CREATE INDEX IF NOT EXISTS idx_plugin_metadata_enabled ON plugin_metadata(enabled);
CREATE INDEX IF NOT EXISTS idx_plugin_metadata_hash ON plugin_metadata(jar_hash);

-- Market plugins indexes
CREATE INDEX IF NOT EXISTS idx_market_plugins_author ON market_plugins(author_id);
CREATE INDEX IF NOT EXISTS idx_market_plugins_category ON market_plugins(category);
CREATE INDEX IF NOT EXISTS idx_market_plugins_downloads ON market_plugins(downloads DESC);
CREATE INDEX IF NOT EXISTS idx_market_plugins_license_type ON market_plugins(license_type);
CREATE INDEX IF NOT EXISTS idx_market_plugins_commercial ON market_plugins(is_commercial);

-- Subscription indexes
CREATE INDEX IF NOT EXISTS idx_subscriptions_target ON subscriptions(target_id, subscription_type);

-- ============================================
-- Per-Guild Schema Template
-- These are created dynamically by SchemaManagementService
-- ============================================

-- Example: CREATE SCHEMA guild_123456789;
--
-- Tables created per guild schema:
--
-- dialogue_history: Stores conversation history
--   - id, user_id, channel_id, user_message, bot_response, intent, created_at
--
-- user_preferences: Stores per-user preferences within guild
--   - user_id, preferred_name, custom_settings (JSONB), notes, created_at, updated_at
--
-- memory: Key-value memory storage
--   - id, key, value, category, created_by, created_at, updated_at
--
-- memory_embeddings: Vector embeddings for semantic search (requires pgvector)
--   - id, memory_id, embedding (vector), created_at

-- ============================================
-- Grant permissions (adjust as needed for your setup)
-- ============================================

-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO pudel_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO pudel_user;

COMMIT;
