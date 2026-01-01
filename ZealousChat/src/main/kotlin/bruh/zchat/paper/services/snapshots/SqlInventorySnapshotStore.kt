package bruh.zchat.paper.services.snapshots

import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.sql
import org.slf4j.LoggerFactory

/**
 * SQL-backed snapshot store. Keeps rows indefinitely; expiry is enforced at view time.
 */
class SqlInventorySnapshotStore(
    private val database: Database
) : InventorySnapshotStore {
    private val logger = LoggerFactory.getLogger(SqlInventorySnapshotStore::class.java)

    override suspend fun save(
        snapshotId: String,
        serverInstanceId: String,
        createdAtEpochMs: Long,
        expiresAtEpochMs: Long,
        data: ByteArray
    ): Boolean {
        return runCatching {
            database.execute(
                sql {
                    mysql("""
                        INSERT INTO inventory_snapshots (snapshot_id, server_instance_id, created_at_epoch_ms, expires_at_epoch_ms, data)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            server_instance_id = VALUES(server_instance_id),
                            created_at_epoch_ms = VALUES(created_at_epoch_ms),
                            expires_at_epoch_ms = VALUES(expires_at_epoch_ms),
                            data = VALUES(data)
                    """)
                    postgres("""
                        INSERT INTO inventory_snapshots (snapshot_id, server_instance_id, created_at_epoch_ms, expires_at_epoch_ms, data)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (snapshot_id) DO UPDATE SET
                            server_instance_id = EXCLUDED.server_instance_id,
                            created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
                            expires_at_epoch_ms = EXCLUDED.expires_at_epoch_ms,
                            data = EXCLUDED.data
                    """)
                    sqlite("""
                        INSERT OR REPLACE INTO inventory_snapshots
                        (snapshot_id, server_instance_id, created_at_epoch_ms, expires_at_epoch_ms, data)
                        VALUES (?, ?, ?, ?, ?)
                    """)
                },
                snapshotId,
                serverInstanceId,
                createdAtEpochMs,
                expiresAtEpochMs,
                data
            )
            true
        }.onFailure { e ->
            logger.error("Failed to save inventory snapshot $snapshotId", e)
        }.getOrDefault(false)
    }

    override suspend fun load(snapshotId: String, serverInstanceId: String): InventorySnapshotStore.StoredSnapshot? {
        return try {
            database.querySingle(
                sql("""
                    SELECT created_at_epoch_ms, expires_at_epoch_ms, data
                    FROM inventory_snapshots
                    WHERE snapshot_id = ? AND server_instance_id = ?
                """),
                snapshotId,
                serverInstanceId
            ) { rs ->
                val created = rs.getLong("created_at_epoch_ms")
                val expires = rs.getLong("expires_at_epoch_ms")
                val bytes = rs.getBytes("data")
                InventorySnapshotStore.StoredSnapshot(created, expires, bytes)
            }?.takeIf { it.expiresAtEpochMs > System.currentTimeMillis() }
        } catch (e: Exception) {
            logger.error("Failed to load inventory snapshot $snapshotId", e)
            null
        }
    }

    override suspend fun delete(snapshotId: String, serverInstanceId: String): Boolean {
        return runCatching {
            database.execute(
                sql("DELETE FROM inventory_snapshots WHERE snapshot_id = ? AND server_instance_id = ?"),
                snapshotId, serverInstanceId
            )
            true
        }.onFailure { e ->
            logger.debug("Failed to delete inventory snapshot $snapshotId", e)
        }.getOrDefault(false)
    }

    override suspend fun cleanupExpired(nowEpochMs: Long): Int {
        // Intentionally keep rows indefinitely; no cleanup.
        return 0
    }

    override fun close() {
        // no-op
    }
}
