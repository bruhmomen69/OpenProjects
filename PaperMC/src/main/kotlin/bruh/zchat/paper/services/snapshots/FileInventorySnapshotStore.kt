package bruh.zchat.paper.services.snapshots

import bruh.zchat.paper.config.ConfigManager
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Filesystem-backed snapshot store that matches the existing behavior:
 * - Same directory layout (inventory_snapshots/<snapshotId>.dat)
 * - Same serialization format (provided by caller)
 * - Cleanup logic identical to previous implementation (age-based, triggered after view)
 */
class FileInventorySnapshotStore(
    private val dataDir: Path,
    private val configManager: ConfigManager
) : InventorySnapshotStore {
    private val logger = LoggerFactory.getLogger(FileInventorySnapshotStore::class.java)

    init {
        try {
            Files.createDirectories(dataDir)
        } catch (e: Exception) {
            logger.error("Failed to create inventory snapshots directory", e)
        }
    }

    override suspend fun save(
        snapshotId: String,
        serverInstanceId: String,
        createdAtEpochMs: Long,
        expiresAtEpochMs: Long,
        data: ByteArray
    ): Boolean {
        return try {
            val target = dataDir.resolve("$snapshotId.dat")
            target.outputStream().use { it.write(data) }
            true
        } catch (e: Exception) {
            logger.error("Failed to save inventory snapshot $snapshotId", e)
            false
        }
    }

    override suspend fun load(snapshotId: String, serverInstanceId: String): InventorySnapshotStore.StoredSnapshot? {
        val file = dataDir.resolve("$snapshotId.dat")
        if (!file.exists()) return null
        return try {
            val bytes = file.inputStream().use { it.readBytes() }
            // For FS, we don't store created/expiry explicitly; derive expiry from retention config.
            val retentionMillis = configManager.config.inventoryPlaceholders.snapshotRetentionMinutes * 60 * 1000L
            val lastModified = file.getLastModifiedTime().toMillis()
            InventorySnapshotStore.StoredSnapshot(
                createdAtEpochMs = lastModified,
                expiresAtEpochMs = lastModified + retentionMillis,
                data = bytes
            )
        } catch (e: Exception) {
            logger.error("Failed to load inventory snapshot $snapshotId", e)
            null
        }
    }

    override suspend fun delete(snapshotId: String, serverInstanceId: String): Boolean {
        return runCatching {
            dataDir.resolve("$snapshotId.dat").deleteIfExists()
        }.getOrElse {
            logger.debug("Failed to delete inventory snapshot $snapshotId", it)
            false
        }
    }

    override suspend fun cleanupExpired(nowEpochMs: Long): Int {
        var removed = 0
        val retentionMillis = configManager.config.inventoryPlaceholders.snapshotRetentionMinutes * 60 * 1000L
        val cutoff = nowEpochMs - retentionMillis
        return try {
            Files.list(dataDir).use { stream ->
                stream.filter { it.toString().endsWith(".dat") }
                    .filter { it.getLastModifiedTime().toMillis() < cutoff }
                    .forEach {
                        if (runCatching { Files.deleteIfExists(it) }.isSuccess) {
                            removed++
                        }
                    }
            }
            removed
        } catch (e: Exception) {
            logger.debug("Failed to cleanup old snapshots", e)
            removed
        }
    }

    override fun close() {
        // no-op
    }
}
