package bruh.zchat.paper.services.snapshots

/**
 * Storage abstraction for inventory snapshots.
 *
 * Implementations may choose how to enforce expiry (e.g., Redis TTL, SQL check, filesystem cleanup).
 * The caller passes serverInstanceId to allow backends to enforce same-boot visibility.
 */
interface InventorySnapshotStore {
    data class StoredSnapshot(
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredSnapshot) return false

            if (createdAtEpochMs != other.createdAtEpochMs) return false
            if (expiresAtEpochMs != other.expiresAtEpochMs) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = createdAtEpochMs.hashCode()
            result = 31 * result + expiresAtEpochMs.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Persist a snapshot.
     */
    suspend fun save(
        snapshotId: String,
        serverInstanceId: String,
        createdAtEpochMs: Long,
        expiresAtEpochMs: Long,
        data: ByteArray
    ): Boolean

    /**
     * Load a snapshot. Returns null if missing or not viewable (e.g., wrong server/expired).
     */
    suspend fun load(snapshotId: String, serverInstanceId: String): StoredSnapshot?

    /**
     * Delete a snapshot for the given server instance.
     */
    suspend fun delete(snapshotId: String, serverInstanceId: String): Boolean

    /**
     * Cleanup expired snapshots where applicable. Return number of entries removed (if known).
     */
    suspend fun cleanupExpired(nowEpochMs: Long): Int

    /**
     * Close resources if any.
     */
    fun close()
}
