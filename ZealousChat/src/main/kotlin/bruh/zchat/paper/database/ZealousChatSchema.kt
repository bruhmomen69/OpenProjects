package bruh.zchat.paper.database

import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.sql

/**
 * Database schema for ZealousChat plugin.
 * 
 * This schema uses a single defensive migration that creates the final database state.
 * It is designed to work safely with both new databases and existing Flyway-migrated databases.
 * All statements use IF NOT EXISTS / IF EXISTS to avoid errors on existing databases.
 */
object ZealousChatSchema : DatabaseSchema("zealouschat") {
    
    override val migrations = listOf(
        // V1: Complete schema - defensive migration that creates final state
        // Works with both new databases and existing Flyway-migrated databases
        migration(1, "Complete schema setup") {
            
            // ==================== Players Table ====================
            // Final state: no is_online column (was removed in Flyway V5)
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        online_server_id VARCHAR(64) NULL,
                        online_last_heartbeat TIMESTAMP NULL,
                        chat_disabled BOOLEAN NOT NULL DEFAULT FALSE,
                        messages_disabled BOOLEAN NOT NULL DEFAULT FALSE
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        online_server_id VARCHAR(64) NULL,
                        online_last_heartbeat TIMESTAMP NULL,
                        chat_disabled BOOLEAN NOT NULL DEFAULT FALSE,
                        messages_disabled BOOLEAN NOT NULL DEFAULT FALSE
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        username TEXT NOT NULL,
                        first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        online_server_id TEXT NULL,
                        online_last_heartbeat TIMESTAMP NULL,
                        chat_disabled INTEGER NOT NULL DEFAULT 0,
                        messages_disabled INTEGER NOT NULL DEFAULT 0
                    )
                """)
            })
            
            // Players indexes (defensive - MySQL inline indexes may already exist)
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_username ON players (username)")
                postgres("CREATE INDEX IF NOT EXISTS idx_username ON players (username)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_username ON players (username)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_last_seen ON players (last_seen)")
                postgres("CREATE INDEX IF NOT EXISTS idx_last_seen ON players (last_seen)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_last_seen ON players (last_seen)")
            })
            
            // ==================== Player Infractions Table ====================
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS player_infractions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        group_name VARCHAR(50) NOT NULL,
                        count INT NOT NULL DEFAULT 1,
                        last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_player_group (player_uuid, group_name)
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS player_infractions (
                        id BIGSERIAL PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        group_name VARCHAR(50) NOT NULL,
                        count INT NOT NULL DEFAULT 1,
                        last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (player_uuid, group_name)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS player_infractions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        group_name TEXT NOT NULL,
                        count INTEGER NOT NULL DEFAULT 1,
                        last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
            })
            
            // Player infractions indexes
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_infractions (player_uuid)")
                postgres("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_infractions (player_uuid)")
                sqlite("CREATE UNIQUE INDEX IF NOT EXISTS uk_player_group ON player_infractions (player_uuid, group_name)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_group_name ON player_infractions (group_name)")
                postgres("CREATE INDEX IF NOT EXISTS idx_group_name ON player_infractions (group_name)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_infractions (player_uuid)")
            })
            execute(sql {
                mysql("")
                postgres("")
                sqlite("CREATE INDEX IF NOT EXISTS idx_group_name ON player_infractions (group_name)")
            })
            
            // ==================== Player Blocks Table ====================
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS player_blocks (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        blocker_uuid VARCHAR(36) NOT NULL,
                        blocked_uuid VARCHAR(36) NOT NULL,
                        blocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        blocked_by_username VARCHAR(16),
                        UNIQUE KEY uk_blocker_blocked (blocker_uuid, blocked_uuid)
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS player_blocks (
                        id BIGSERIAL PRIMARY KEY,
                        blocker_uuid VARCHAR(36) NOT NULL,
                        blocked_uuid VARCHAR(36) NOT NULL,
                        blocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        blocked_by_username VARCHAR(16),
                        UNIQUE (blocker_uuid, blocked_uuid)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS player_blocks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        blocker_uuid TEXT NOT NULL,
                        blocked_uuid TEXT NOT NULL,
                        blocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        blocked_by_username TEXT
                    )
                """)
            })
            
            // Player blocks indexes
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_blocker_uuid ON player_blocks (blocker_uuid)")
                postgres("CREATE INDEX IF NOT EXISTS idx_blocker_uuid ON player_blocks (blocker_uuid)")
                sqlite("CREATE UNIQUE INDEX IF NOT EXISTS uk_blocker_blocked ON player_blocks (blocker_uuid, blocked_uuid)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_blocked_uuid ON player_blocks (blocked_uuid)")
                postgres("CREATE INDEX IF NOT EXISTS idx_blocked_uuid ON player_blocks (blocked_uuid)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_blocker_uuid ON player_blocks (blocker_uuid)")
            })
            execute(sql {
                mysql("")
                postgres("")
                sqlite("CREATE INDEX IF NOT EXISTS idx_blocked_uuid ON player_blocks (blocked_uuid)")
            })
            
            // ==================== Infractions Archive Table ====================
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS player_infractions_archive (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        group_name VARCHAR(50) NOT NULL,
                        count INT NOT NULL,
                        last_updated TIMESTAMP NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS player_infractions_archive (
                        id BIGSERIAL PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        group_name VARCHAR(50) NOT NULL,
                        count INT NOT NULL,
                        last_updated TIMESTAMP NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS player_infractions_archive (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        group_name TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        last_updated TIMESTAMP NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
            })
            
            // Infractions archive indexes
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_player_uuid_archive ON player_infractions_archive (player_uuid)")
                postgres("CREATE INDEX IF NOT EXISTS idx_player_uuid_archive ON player_infractions_archive (player_uuid)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_player_uuid_archive ON player_infractions_archive (player_uuid)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_group_name_archive ON player_infractions_archive (group_name)")
                postgres("CREATE INDEX IF NOT EXISTS idx_group_name_archive ON player_infractions_archive (group_name)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_group_name_archive ON player_infractions_archive (group_name)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_archived_at ON player_infractions_archive (archived_at)")
                postgres("CREATE INDEX IF NOT EXISTS idx_archived_at ON player_infractions_archive (archived_at)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_archived_at ON player_infractions_archive (archived_at)")
            })
            
            // ==================== Message Bus Table ====================
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS message_bus (
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
                        error TEXT NULL
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS message_bus (
                        id BIGSERIAL PRIMARY KEY,
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
                        error TEXT NULL
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS message_bus (
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
                    )
                """)
            })
            
            // Message bus indexes
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_target_status ON message_bus (target_server_id, status, id)")
                postgres("CREATE INDEX IF NOT EXISTS idx_target_status ON message_bus (target_server_id, status, id)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_target_status ON message_bus (target_server_id, status, id)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_status_claimed ON message_bus (status, claimed_at)")
                postgres("CREATE INDEX IF NOT EXISTS idx_status_claimed ON message_bus (status, claimed_at)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_status_claimed ON message_bus (status, claimed_at)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_recipient_status ON message_bus (recipient_uuid, status)")
                postgres("CREATE INDEX IF NOT EXISTS idx_recipient_status ON message_bus (recipient_uuid, status)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_recipient_status ON message_bus (recipient_uuid, status)")
            })
            
            // ==================== Inventory Snapshots Table ====================
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS inventory_snapshots (
                        snapshot_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        server_instance_id VARCHAR(64) NOT NULL,
                        created_at_epoch_ms BIGINT NOT NULL,
                        expires_at_epoch_ms BIGINT NOT NULL,
                        data LONGBLOB NOT NULL,
                        player_uuid VARCHAR(36),
                        player_name VARCHAR(16),
                        snapshot_type VARCHAR(32)
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS inventory_snapshots (
                        snapshot_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        server_instance_id VARCHAR(64) NOT NULL,
                        created_at_epoch_ms BIGINT NOT NULL,
                        expires_at_epoch_ms BIGINT NOT NULL,
                        data BYTEA NOT NULL,
                        player_uuid VARCHAR(36),
                        player_name VARCHAR(16),
                        snapshot_type VARCHAR(32)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS inventory_snapshots (
                        snapshot_id TEXT NOT NULL PRIMARY KEY,
                        server_instance_id TEXT NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        expires_at_epoch_ms INTEGER NOT NULL,
                        data BLOB NOT NULL,
                        player_uuid TEXT,
                        player_name TEXT,
                        snapshot_type TEXT
                    )
                """)
            })
            
            // Inventory snapshots indexes
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_expires ON inventory_snapshots (expires_at_epoch_ms)")
                postgres("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_expires ON inventory_snapshots (expires_at_epoch_ms)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_expires ON inventory_snapshots (expires_at_epoch_ms)")
            })
            execute(sql {
                mysql("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_server ON inventory_snapshots (server_instance_id, snapshot_id)")
                postgres("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_server ON inventory_snapshots (server_instance_id, snapshot_id)")
                sqlite("CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_server ON inventory_snapshots (server_instance_id, snapshot_id)")
            })
            
            // ==================== Cleanup Legacy Tables ====================
            // Drop player_blocks_archive if it exists (was removed in Flyway V6)
            execute(sql {
                default("DROP TABLE IF EXISTS player_blocks_archive")
            })
            
            // Drop is_online index if it exists (column was removed in Flyway V5)
            execute(sql {
                mysql("DROP INDEX IF EXISTS idx_online ON players")
                postgres("DROP INDEX IF EXISTS idx_online")
                sqlite("DROP INDEX IF EXISTS idx_online")
            })
        }
    )
}
