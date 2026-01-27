package bruh.regionrestore.timer.chunk

import bruh.regionrestore.config.RestoreConfig
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Chunk
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong

/**
 * Manages chunk ticket references for preventing chunk unloading during restore operations.
 */
class ChunkTicketManager(
    private val plugin: SuspendingJavaPlugin,
    private val restoreConfig: RestoreConfig
) {
    private val chunkTicketRefs = ConcurrentHashMap<ChunkKey, Int>()
    private val chunkLoads = AtomicLong(0)

    data class ChunkKey(
        val worldId: UUID,
        val x: Int,
        val z: Int
    )

    data class ChunkTicketHandle(
        val key: ChunkKey,
        val chunk: Chunk?,
        val hadTicket: Boolean,
        val wasLoaded: Boolean
    )

    /**
     * Increments the ticket reference count for a chunk.
     * @return The new reference count
     */
    fun incrementTicketRef(key: ChunkKey): Int =
        chunkTicketRefs.merge(key, 1) { a, b -> a + b }!!

    /**
     * Decrements the ticket reference count for a chunk.
     * Removes the entry when count reaches 0.
     * @return The new reference count (0 if entry was removed)
     */
    fun decrementTicketRef(key: ChunkKey): Int {
        val newCount = chunkTicketRefs.compute(key) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        return newCount ?: 0
    }

    /**
     * Creates a chunk ticket handle for a loaded chunk.
     * Adds a plugin chunk ticket if this is the first reference.
     */
    fun createTicketHandle(
        world: World,
        x: Int,
        z: Int,
        chunk: Chunk,
        wasLoaded: Boolean
    ): ChunkTicketHandle {
        val key = ChunkKey(world.uid, x, z)
        val newCount = incrementTicketRef(key)
        
        return if (newCount == 1) {
            try {
                chunk.addPluginChunkTicket(plugin)
                ChunkTicketHandle(key, chunk, hadTicket = true, wasLoaded = wasLoaded)
            } catch (_: Throwable) {
                // Roll back ref count and continue without a ticket
                decrementTicketRef(key)
                ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
            }
        } else {
            ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
        }
    }

    /**
     * Releases chunk tickets after restore completion.
     * Handles delayed unloading based on configuration.
     */
    fun releaseChunkTickets(handles: List<ChunkTicketHandle>) {
        for (handle in handles) {
            if (!handle.hadTicket) {
                continue
            }

            val remaining = decrementTicketRef(handle.key)
            if (remaining == 0) {
                handle.chunk?.removePluginChunkTicket(plugin)

                if (restoreConfig.unload && handle.chunk != null && !handle.wasLoaded) {
                    val currentLoad = chunkLoads.incrementAndFetch()
                    val needsDelay = currentLoad % restoreConfig.taskChunkLoadThrottle == 0L
                    val delay = ceil(currentLoad / restoreConfig.taskChunkLoadThrottle.toDouble()).toLong() * 48

                    plugin.launch(
                        plugin.regionDispatcher(
                            handle.chunk.world,
                            handle.chunk.x,
                            handle.chunk.z
                        )
                    ) {
                        if (needsDelay) delay(delay.milliseconds)

                        if (restoreConfig.unloadInstant) {
                            handle.chunk.unload()
                        } else {
                            handle.chunk.world.unloadChunkRequest(handle.chunk.x, handle.chunk.z)
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if chunk loading should be throttled based on load count.
     * Call this before loading a chunk to respect throttling.
     */
    suspend fun checkLoadThrottle() {
        if (chunkLoads.incrementAndFetch() % restoreConfig.taskChunkLoadThrottle == 0L) {
            delay(48)
        }
    }
}
