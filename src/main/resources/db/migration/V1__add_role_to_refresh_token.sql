-- Add role column to refresh_token table
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS role VARCHAR(255) NOT NULL DEFAULT 'CONSUMER';

-- Update existing records to have a default role
UPDATE refresh_token SET role = 'CONSUMER' WHERE role IS NULL;
