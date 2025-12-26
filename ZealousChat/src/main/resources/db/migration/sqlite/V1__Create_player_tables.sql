CREATE TABLE players (
    uuid TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_online INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_username ON players (username);
CREATE INDEX idx_last_seen ON players (last_seen);
CREATE INDEX idx_online ON players (is_online);

CREATE TABLE player_infractions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    group_name TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_player_group ON player_infractions (player_uuid, group_name);
CREATE INDEX idx_player_uuid ON player_infractions (player_uuid);
CREATE INDEX idx_group_name ON player_infractions (group_name);

CREATE TABLE player_blocks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    blocker_uuid TEXT NOT NULL,
    blocked_uuid TEXT NOT NULL,
    blocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    blocked_by_username TEXT,
    FOREIGN KEY (blocker_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (blocked_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_blocker_blocked ON player_blocks (blocker_uuid, blocked_uuid);
CREATE INDEX idx_blocker_uuid ON player_blocks (blocker_uuid);
CREATE INDEX idx_blocked_uuid ON player_blocks (blocked_uuid);