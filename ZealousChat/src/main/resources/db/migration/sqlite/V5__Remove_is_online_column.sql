-- Remove is_online column and its index from players table
DROP INDEX IF EXISTS idx_online;
ALTER TABLE players DROP COLUMN is_online;
