-- V4: Add natural behavior settings for more natural responses
-- These fields allow guild owners to customize how Pudel communicates

-- Add emote usage setting (none, minimal, moderate, frequent)
ALTER TABLE guild_settings
ADD COLUMN IF NOT EXISTS emote_usage VARCHAR(20) DEFAULT 'moderate';

-- Add quirks/speech patterns (custom catchphrases, speech patterns)
ALTER TABLE guild_settings
ADD COLUMN IF NOT EXISTS quirks TEXT;

-- Add topics of interest (topics Pudel should be enthusiastic about)
ALTER TABLE guild_settings
ADD COLUMN IF NOT EXISTS topics_interest TEXT;

-- Add topics to avoid (topics Pudel should redirect away from)
ALTER TABLE guild_settings
ADD COLUMN IF NOT EXISTS topics_avoid TEXT;

-- Add system_prompt_prefix column to guild_settings
ALTER TABLE guild_settings
ADD COLUMN IF NOT EXISTS system_prompt_prefix TEXT;

-- Add comments for documentation
COMMENT ON COLUMN guild_settings.emote_usage IS 'Emoji usage level: none, minimal, moderate, frequent';
COMMENT ON COLUMN guild_settings.quirks IS 'Custom speech patterns/catchphrases for natural responses';
COMMENT ON COLUMN guild_settings.topics_interest IS 'Topics Pudel should be more engaged about';
COMMENT ON COLUMN guild_settings.topics_avoid IS 'Topics Pudel should politely redirect away from';
COMMENT ON COLUMN guild_settings.system_prompt_prefix IS 'Custom system prompt prefix for LLM customization per guild';
