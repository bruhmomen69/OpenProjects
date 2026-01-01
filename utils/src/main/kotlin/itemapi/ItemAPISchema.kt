package bruh.zchat.utils.itemapi

import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.sql

/**
 * Database schema for the ItemAPI tracked items system.
 * Defines migrations for the tracked_items and tracked_item_metadata tables.
 */
object ItemAPISchema : DatabaseSchema("itemapi") {
    override val migrations = listOf(
        migration(1, "Create tracked_items table") {
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS tracked_items (
                        instance_id VARCHAR(36) PRIMARY KEY,
                        item_id VARCHAR(64) NOT NULL,
                        owner_uuid VARCHAR(36),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_interacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_tracked_items_owner (owner_uuid),
                        INDEX idx_tracked_items_item_id (item_id)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS tracked_items (
                        instance_id TEXT PRIMARY KEY,
                        item_id TEXT NOT NULL,
                        owner_uuid TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_interacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS tracked_items (
                        instance_id VARCHAR(36) PRIMARY KEY,
                        item_id VARCHAR(64) NOT NULL,
                        owner_uuid VARCHAR(36),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_interacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
            })
            // SQLite indexes need separate statements
            execute(sql {
                mysql("SELECT 1") // no-op, indexes created inline
                sqlite("CREATE INDEX IF NOT EXISTS idx_tracked_items_owner ON tracked_items(owner_uuid)")
                postgres("CREATE INDEX IF NOT EXISTS idx_tracked_items_owner ON tracked_items(owner_uuid)")
            })
            execute(sql {
                mysql("SELECT 1") // no-op, indexes created inline
                sqlite("CREATE INDEX IF NOT EXISTS idx_tracked_items_item_id ON tracked_items(item_id)")
                postgres("CREATE INDEX IF NOT EXISTS idx_tracked_items_item_id ON tracked_items(item_id)")
            })
        },
        migration(2, "Create tracked_item_metadata table") {
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS tracked_item_metadata (
                        instance_id VARCHAR(36) NOT NULL,
                        meta_key VARCHAR(128) NOT NULL,
                        meta_value TEXT NOT NULL,
                        PRIMARY KEY (instance_id, meta_key),
                        FOREIGN KEY (instance_id) REFERENCES tracked_items(instance_id) ON DELETE CASCADE
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS tracked_item_metadata (
                        instance_id TEXT NOT NULL,
                        meta_key TEXT NOT NULL,
                        meta_value TEXT NOT NULL,
                        PRIMARY KEY (instance_id, meta_key),
                        FOREIGN KEY (instance_id) REFERENCES tracked_items(instance_id) ON DELETE CASCADE
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS tracked_item_metadata (
                        instance_id VARCHAR(36) NOT NULL,
                        meta_key VARCHAR(128) NOT NULL,
                        meta_value TEXT NOT NULL,
                        PRIMARY KEY (instance_id, meta_key),
                        FOREIGN KEY (instance_id) REFERENCES tracked_items(instance_id) ON DELETE CASCADE
                    )
                """)
            })
        }
    )
}
