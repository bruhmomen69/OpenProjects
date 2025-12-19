-- Inventory snapshots table for SQL backend (SQLite)
CREATE TABLE IF NOT EXISTS inventory_snapshots (
    snapshot_id TEXT NOT NULL PRIMARY KEY,
    server_instance_id TEXT NOT NULL,
    created_at_epoch_ms INTEGER NOT NULL,
    expires_at_epoch_ms INTEGER NOT NULL,
    data BLOB NOT NULL,
    player_uuid TEXT,
    player_name TEXT,
    snapshot_type TEXT
);
CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_expires ON inventory_snapshots (expires_at_epoch_ms);
CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_server ON inventory_snapshots (server_instance_id, snapshot_id);
