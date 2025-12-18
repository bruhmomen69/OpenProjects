CREATE TABLE players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_username (username),
    INDEX idx_last_seen (last_seen),
    INDEX idx_online (is_online)
);

CREATE TABLE player_infractions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    group_name VARCHAR(50) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    UNIQUE KEY uk_player_group (player_uuid, group_name),
    INDEX idx_player_uuid (player_uuid),
    INDEX idx_group_name (group_name)
);

CREATE TABLE player_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_uuid VARCHAR(36) NOT NULL,
    blocked_uuid VARCHAR(36) NOT NULL,
    blocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    blocked_by_username VARCHAR(16),
    FOREIGN KEY (blocker_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (blocked_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    UNIQUE KEY uk_blocker_blocked (blocker_uuid, blocked_uuid),
    INDEX idx_blocker_uuid (blocker_uuid),
    INDEX idx_blocked_uuid (blocked_uuid)
);