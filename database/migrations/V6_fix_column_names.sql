-- Pudel Discord Bot - Migration V6
-- Version: Fix column name mismatches between init.sql and JPA entity
-- Description: Add missing columns that JPA entity expects

-- ============================================
-- Fix guild_settings column names
-- The init.sql used different column names than the JPA entity
-- JPA entity expects: biography, prefix, verbosity, cooldown, log_channel, bot_channel
-- init.sql created: bot_biography, command_prefix, verbosity_level, command_cooldown, log_channel_id, bot_channel_id
-- ============================================

-- Add columns that JPA entity expects (if they don't exist)
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS biography TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS prefix VARCHAR(10) DEFAULT '!';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS verbosity INT DEFAULT 3;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS cooldown FLOAT DEFAULT 0;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS log_channel VARCHAR(255);
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS bot_channel VARCHAR(255);
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS personality TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS preferences TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS nickname VARCHAR(255);
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS response_length VARCHAR(50) DEFAULT 'medium';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS formality VARCHAR(50) DEFAULT 'balanced';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS emote_usage VARCHAR(20) DEFAULT 'moderate';
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS quirks TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS topics_interest TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS topics_avoid TEXT;
ALTER TABLE guild_settings ADD COLUMN IF NOT EXISTS ignored_channels TEXT;

-- Copy data from old columns to new columns (only if old columns exist and new columns are empty)
DO $$
BEGIN
    -- Copy bot_biography to biography
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'bot_biography') THEN
        UPDATE guild_settings SET biography = bot_biography WHERE biography IS NULL AND bot_biography IS NOT NULL;
    END IF;

    -- Copy command_prefix to prefix
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'command_prefix') THEN
        UPDATE guild_settings SET prefix = command_prefix WHERE prefix IS NULL AND command_prefix IS NOT NULL;
    END IF;

    -- Copy verbosity_level to verbosity
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'verbosity_level') THEN
        UPDATE guild_settings SET verbosity = verbosity_level WHERE verbosity IS NULL AND verbosity_level IS NOT NULL;
    END IF;

    -- Copy command_cooldown to cooldown
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'command_cooldown') THEN
        UPDATE guild_settings SET cooldown = command_cooldown WHERE cooldown IS NULL AND command_cooldown IS NOT NULL;
    END IF;

    -- Copy log_channel_id to log_channel
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'log_channel_id') THEN
        UPDATE guild_settings SET log_channel = log_channel_id WHERE log_channel IS NULL AND log_channel_id IS NOT NULL;
    END IF;

    -- Copy bot_channel_id to bot_channel
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'bot_channel_id') THEN
        UPDATE guild_settings SET bot_channel = bot_channel_id WHERE bot_channel IS NULL AND bot_channel_id IS NOT NULL;
    END IF;

    -- Copy bot_personality to personality
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'bot_personality') THEN
        UPDATE guild_settings SET personality = bot_personality WHERE personality IS NULL AND bot_personality IS NOT NULL;
    END IF;

    -- Copy bot_preferences to preferences
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'bot_preferences') THEN
        UPDATE guild_settings SET preferences = bot_preferences WHERE preferences IS NULL AND bot_preferences IS NOT NULL;
    END IF;

    -- Copy bot_nickname to nickname
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'bot_nickname') THEN
        UPDATE guild_settings SET nickname = bot_nickname WHERE nickname IS NULL AND bot_nickname IS NOT NULL;
    END IF;

    -- Copy ignore_channels to ignored_channels
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'guild_settings' AND column_name = 'ignore_channels') THEN
        UPDATE guild_settings SET ignored_channels = ignore_channels WHERE ignored_channels IS NULL AND ignore_channels IS NOT NULL;
    END IF;
END $$;

-- Add comments for documentation
COMMENT ON COLUMN guild_settings.biography IS 'Bot biography/backstory for AI personality';
COMMENT ON COLUMN guild_settings.prefix IS 'Command prefix for the guild';
COMMENT ON COLUMN guild_settings.verbosity IS 'Response verbosity level (1-5)';
COMMENT ON COLUMN guild_settings.cooldown IS 'Command cooldown in seconds';
COMMENT ON COLUMN guild_settings.log_channel IS 'Channel ID for logging';
COMMENT ON COLUMN guild_settings.bot_channel IS 'Primary bot channel ID';
COMMENT ON COLUMN guild_settings.response_length IS 'Preferred response length: short, medium, long';
COMMENT ON COLUMN guild_settings.formality IS 'Response formality: casual, balanced, formal';
COMMENT ON COLUMN guild_settings.emote_usage IS 'Emoji usage level: none, minimal, moderate, frequent';

