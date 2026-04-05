package bruh.regionrestore.timer

import bruh.regionrestore.config.RestoreConfig
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.bukkit.Chunk
import org.bukkit.World
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages chunk ticket references and chunk loading lifecycle.
 * Thread-safe via ConcurrentHashMap.
 */
@OptIn(ExperimentalAtomicApi::class)
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
     * Increment the ticket reference count for a chunk.
     * Returns the new count.
     */
    fun incrementTicketRef(key: ChunkKey): Int =
        chunkTicketRefs.merge(key, 1) { a, b -> a + b }!!

    /**
     * Decrement the ticket reference count for a chunk.
     * Returns the new count (0 if removed).
     */
    fun decrementTicketRef(key: ChunkKey): Int {
        val newCount = chunkTicketRefs.compute(key) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        return newCount ?: 0
    }

    /**
     * Get the current chunk load count.
     */
    fun getLoadCount(): Long = chunkLoads.load()

    /**
     * Increment the chunk load count and return the new value.
     */
    fun incrementAndGetLoadCount(): Long = chunkLoads.incrementAndFetch()

    /**
     * Preload chunks for a restore job and add chunk tickets.
     * Applies throttling based on config.
     */
    suspend fun preloadChunks(job: RestoreJob): List<ChunkTicketHandle> {
        val futures = mutableListOf<CompletableFuture<ChunkTicketHandle>>()
        val worldId = job.world.uid

        for (dx in 0 until job.sizeXChunks) {
            for (dz in 0 until job.sizeZChunks) {
                val x = job.targetChunkX + dx
                val z = job.targetChunkZ + dz
                val key = ChunkKey(worldId, x, z)
                val wasLoaded = job.world.isChunkLoaded(x, z)

                val future = job.world.getChunkAtAsync(x, z)
                val handleFuture = future.handle { chunk, throwable ->
                    try {
                        if (throwable != null || chunk == null) {
                            ChunkTicketHandle(key, null, hadTicket = false, wasLoaded = wasLoaded)
                        } else {
                            val newCount = incrementTicketRef(key)
                            if (newCount == 1) {
                                return@handle try {
                                    chunk.addPluginChunkTicket(plugin)
                                    ChunkTicketHandle(key, chunk, hadTicket = true, wasLoaded = wasLoaded)
                                } catch (_: Throwable) {
                                    // Roll back ref count and continue without a ticket
                                    decrementTicketRef(key)
                                    ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
                                }
                            }

                            ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
                        }
                    } catch (_: Throwable) {
                        ChunkTicketHandle(key, null, hadTicket = false, wasLoaded = wasLoaded)
                    }
                }

                futures += handleFuture

                if (!wasLoaded) {
                    if (chunkLoads.incrementAndFetch() % restoreConfig.taskLoadThrottle == 0L) {
                        delay(48)
                    }
                }
            }
        }

        return awaitAll(futures)
    }

    /**
     * Release chunk tickets and optionally unload chunks.
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
                    val needsDelay = currentLoad % restoreConfig.taskLoadThrottle == 0L
                    val delay = ceil(currentLoad / restoreConfig.taskLoadThrottle.toDouble()).toLong() * 48

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
     * Create a ticket handle for a single chunk without preloading.
     */
    fun createTicketHandle(chunk: Chunk, wasLoaded: Boolean): ChunkTicketHandle {
        val key = ChunkKey(chunk.world.uid, chunk.x, chunk.z)
        val newCount = incrementTicketRef(key)
        val hadTicket = if (newCount == 1) {
            try {
                chunk.addPluginChunkTicket(plugin)
                true
            } catch (_: Throwable) {
                decrementTicketRef(key)
                false
            }
        } else false

        return ChunkTicketHandle(key, chunk, hadTicket, wasLoaded)
    }

    /**
     * Release a single ticket handle.
     */
    fun releaseTicketHandle(handle: ChunkTicketHandle) {
        releaseChunkTickets(listOf(handle))
    }

    private suspend fun <T> awaitAll(futures: List<CompletableFuture<T>>): List<T> =
        suspendCancellableCoroutine { cont ->
            if (futures.isEmpty()) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val all = CompletableFuture.allOf(*futures.toTypedArray())

            cont.invokeOnCancellation {
                all.cancel(false)
            }

            all.whenComplete { _, throwable ->
                if (throwable != null) {
                    cont.resumeWithException(throwable)
                } else {
                    try {
                        cont.resume(futures.map { it.join() })
                    } catch (e: Throwable) {
                        cont.resumeWithException(e)
                    }
                }
            }
        }
}
