ALTER TABLE players ADD COLUMN online_server_id VARCHAR(64) NULL;
ALTER TABLE players ADD COLUMN online_last_heartbeat TIMESTAMP NULL;

CREATE TABLE message_bus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_server_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    sender_uuid VARCHAR(36) NOT NULL,
    sender_username VARCHAR(16) NOT NULL,
    recipient_uuid VARCHAR(36) NOT NULL,
    recipient_username VARCHAR(16) NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    claimed_by VARCHAR(64) NULL,
    claimed_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    error TEXT NULL,
    
    INDEX idx_target_status (target_server_id, status, id),
    INDEX idx_status_claimed (status, claimed_at),
    INDEX idx_recipient_status (recipient_uuid, status)
);
