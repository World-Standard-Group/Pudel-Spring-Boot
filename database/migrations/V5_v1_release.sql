-- Pudel Discord Bot - Migration V5
-- Version: 1.0.0 Release Migration
-- Description: Remove deprecated NLP features, add hot-reload support, update schema

-- ============================================
-- Remove deprecated NLP fields
-- ============================================

-- Remove nlp_training_limit from subscriptions if exists
ALTER TABLE subscriptions DROP COLUMN IF EXISTS nlp_training_limit;

-- Add tier_name if not exists (for configurable tiers)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS tier_name VARCHAR(50) DEFAULT 'FREE';

-- Add jar_hash to plugin_metadata for hot-reload
ALTER TABLE plugin_metadata ADD COLUMN IF NOT EXISTS jar_hash VARCHAR(64);

-- ============================================
-- Add new guild_settings fields
-- ============================================

ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS bot_nickname VARCHAR(255) DEFAULT 'Pudel';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS language VARCHAR(10) DEFAULT 'en';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS system_prompt_prefix TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS ignore_channels TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS disabled_commands TEXT;

-- ============================================
-- Update defaults for v1.0.0
-- ============================================

-- Set unlimited plugin limit by default
UPDATE subscriptions SET plugin_limit = -1 WHERE plugin_limit IS NULL OR plugin_limit < 0;

-- ============================================
-- Add indexes for performance
-- ============================================

CREATE INDEX IF NOT EXISTS idx_plugin_metadata_hash ON plugin_metadata(jar_hash);
CREATE INDEX IF NOT EXISTS idx_guild_settings_ai_enabled ON guild_settings(ai_enabled);

-- ============================================
-- Cleanup deprecated tables (optional - uncomment if needed)
-- ============================================

-- DROP TABLE IF EXISTS guild_config;
-- DROP TABLE IF EXISTS bot_user;

COMMIT;

