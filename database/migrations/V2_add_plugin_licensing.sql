-- Migration script to add license fields to market_plugins table
-- Run this if you have an existing database

-- Add license_type column
ALTER TABLE market_plugins
ADD COLUMN IF NOT EXISTS license_type VARCHAR(20) DEFAULT 'MIT';

-- Add commercial flag
ALTER TABLE market_plugins
ADD COLUMN IF NOT EXISTS is_commercial BOOLEAN DEFAULT FALSE;

-- Add price in cents
ALTER TABLE market_plugins
ADD COLUMN IF NOT EXISTS price_cents INTEGER DEFAULT 0;

-- Add contact email for commercial plugins
ALTER TABLE market_plugins
ADD COLUMN IF NOT EXISTS contact_email VARCHAR(100);

-- Update existing plugins to have MIT license by default
UPDATE market_plugins SET license_type = 'MIT' WHERE license_type IS NULL;
UPDATE market_plugins SET is_commercial = FALSE WHERE is_commercial IS NULL;
UPDATE market_plugins SET price_cents = 0 WHERE price_cents IS NULL;

-- Create index for license type filtering
CREATE INDEX IF NOT EXISTS idx_market_plugins_license_type ON market_plugins(license_type);
CREATE INDEX IF NOT EXISTS idx_market_plugins_commercial ON market_plugins(is_commercial);

COMMIT;

