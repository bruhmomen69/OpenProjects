-- Inventory snapshots table for SQL backend (MySQL)
CREATE TABLE IF NOT EXISTS inventory_snapshots (
    snapshot_id VARCHAR(255) NOT NULL,
    server_instance_id VARCHAR(64) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    expires_at_epoch_ms BIGINT NOT NULL,
    data LONGBLOB NOT NULL,
    player_uuid VARCHAR(36),
    player_name VARCHAR(16),
    snapshot_type VARCHAR(32),
    PRIMARY KEY (snapshot_id),
    INDEX idx_inventory_snapshots_expires (expires_at_epoch_ms),
    INDEX idx_inventory_snapshots_server (server_instance_id, snapshot_id)
);
