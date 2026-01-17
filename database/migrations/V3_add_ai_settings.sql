-- Add AI enabled and ignored channels columns to guild_settings
-- Version: V3
-- Description: Adds support for per-guild AI toggle and channel ignore list

ALTER TABLE guild_settings
    ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE guild_settings
    ADD COLUMN IF NOT EXISTS ignored_channels TEXT;

COMMENT ON COLUMN guild_settings.ai_enabled IS 'Whether AI/chatbot features are enabled for this guild';
COMMENT ON COLUMN guild_settings.ignored_channels IS 'Comma-separated list of channel IDs to completely ignore';

