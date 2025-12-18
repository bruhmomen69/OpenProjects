CREATE TABLE player_infractions_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    group_name VARCHAR(50) NOT NULL,
    count INT NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player_uuid (player_uuid),
    INDEX idx_group_name (group_name),
    INDEX idx_archived_at (archived_at)
);