-- Remove is_online column and its index from players table
ALTER TABLE players DROP INDEX idx_online;
ALTER TABLE players DROP COLUMN is_online;
