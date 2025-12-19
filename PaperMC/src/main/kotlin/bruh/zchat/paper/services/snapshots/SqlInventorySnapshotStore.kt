package bruh.zchat.paper.services.snapshots

import bruh.zchat.paper.database.DatabaseService
import bruh.zchat.paper.database.DatabaseType
import org.slf4j.LoggerFactory

/**
 * SQL-backed snapshot store. Keeps rows indefinitely; expiry is enforced at view time.
 */
class SqlInventorySnapshotStore(
    private val databaseService: DatabaseService
) : InventorySnapshotStore {
    private val logger = LoggerFactory.getLogger(SqlInventorySnapshotStore::class.java)

    override suspend fun save(
        snapshotId: String,
        serverInstanceId: String,
        createdAtEpochMs: Long,
        expiresAtEpochMs: Long,
        data: ByteArray
    ): Boolean {
        val (sql, params) = when (databaseService.databaseType) {
            DatabaseType.MYSQL -> """
                INSERT INTO inventory_snapshots (snapshot_id, server_instance_id, created_at_epoch_ms, expires_at_epoch_ms, data)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    server_instance_id = VALUES(server_instance_id),
                    created_at_epoch_ms = VALUES(created_at_epoch_ms),
                    expires_at_epoch_ms = VALUES(expires_at_epoch_ms),
                    data = VALUES(data)
            """.trimIndent() to arrayOf(
                snapshotId,
                serverInstanceId,
                createdAtEpochMs,
                expiresAtEpochMs,
                data
            )
            DatabaseType.SQLITE -> """
                INSERT OR REPLACE INTO inventory_snapshots
                (snapshot_id, server_instance_id, created_at_epoch_ms, expires_at_epoch_ms, data)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent() to arrayOf(
                snapshotId,
                serverInstanceId,
                createdAtEpochMs,
                expiresAtEpochMs,
                data
            )
        }

        return runCatching {
            databaseService.executeUpdate(sql, *params)
            true
        }.onFailure { e ->
            logger.error("Failed to save inventory snapshot $snapshotId", e)
        }.getOrDefault(false)
    }

    override suspend fun load(snapshotId: String, serverInstanceId: String): InventorySnapshotStore.StoredSnapshot? {
        val sql = """
            SELECT created_at_epoch_ms, expires_at_epoch_ms, data
            FROM inventory_snapshots
            WHERE snapshot_id = ? AND server_instance_id = ?
        """.trimIndent()

        return try {
            databaseService.executeQuerySingle(
                sql,
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
        val sql = "DELETE FROM inventory_snapshots WHERE snapshot_id = ? AND server_instance_id = ?"
        return runCatching {
            databaseService.executeUpdate(sql, snapshotId, serverInstanceId)
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
