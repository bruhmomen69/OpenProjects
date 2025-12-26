CREATE TABLE player_infractions_archive (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    group_name TEXT NOT NULL,
    count INTEGER NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_player_uuid_archive ON player_infractions_archive (player_uuid);
CREATE INDEX idx_group_name_archive ON player_infractions_archive (group_name);
CREATE INDEX idx_archived_at ON player_infractions_archive (archived_at);

CREATE TABLE player_blocks_archive (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    blocker_uuid TEXT NOT NULL,
    blocked_uuid TEXT NOT NULL,
    blocked_at TIMESTAMP NOT NULL,
    blocked_by_username TEXT,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_blocker_uuid_archive ON player_blocks_archive (blocker_uuid);
CREATE INDEX idx_blocked_uuid_archive ON player_blocks_archive (blocked_uuid);
CREATE INDEX idx_archived_at_blocks ON player_blocks_archive (archived_at);