package bruh.regionrestore.timer

import bruh.regionrestore.nms.RegionTemplate
import org.bukkit.World
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

data class RestoreJob(
    val id: UUID,
    val world: World,
    val targetChunkX: Int,
    val targetChunkZ: Int,
    val sizeXChunks: Int = 1,
    val sizeZChunks: Int = 1,
    val template: RegionTemplate,
    val updateLight: Boolean = false,
    val future: CompletableFuture<RestoreJob> = CompletableFuture()
) {
    private val _isRunning = AtomicBoolean(false)
    val isRunning: Boolean get() = _isRunning.get()
    fun tryStart(): Boolean = _isRunning.compareAndSet(false, true)
    fun finish() {
        _isRunning.set(false)
    }

    /**
     * AABB block bounds of the region.
     * Calculated from chunk coordinates and size.
     */
    val minBlockX: Int = targetChunkX * 16
    val maxBlockX: Int = (targetChunkX + sizeXChunks) * 16 - 1
    val minBlockZ: Int = targetChunkZ * 16
    val maxBlockZ: Int = (targetChunkZ + sizeZChunks) * 16 - 1
}
