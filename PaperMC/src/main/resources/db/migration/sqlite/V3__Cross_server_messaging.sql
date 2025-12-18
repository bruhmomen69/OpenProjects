ALTER TABLE players ADD COLUMN online_server_id TEXT NULL;
ALTER TABLE players ADD COLUMN online_last_heartbeat TIMESTAMP NULL;

CREATE TABLE message_bus (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_server_id TEXT NOT NULL,
    type TEXT NOT NULL,
    sender_uuid TEXT NOT NULL,
    sender_username TEXT NOT NULL,
    recipient_uuid TEXT NOT NULL,
    recipient_username TEXT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'PENDING',
    claimed_by TEXT NULL,
    claimed_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    error TEXT NULL
);

CREATE INDEX idx_target_status ON message_bus (target_server_id, status, id);
CREATE INDEX idx_status_claimed ON message_bus (status, claimed_at);
CREATE INDEX idx_recipient_status ON message_bus (recipient_uuid, status);
