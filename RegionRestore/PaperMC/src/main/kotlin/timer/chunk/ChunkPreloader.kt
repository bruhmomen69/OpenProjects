package bruh.regionrestore.timer.chunk

import bruh.regionrestore.config.RestoreConfig
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong

/**
 * Handles preloading chunks for restore operations.
 * Loads all chunks in a region and creates ticket handles for tracking.
 */
class ChunkPreloader(
    private val plugin: SuspendingJavaPlugin,
    private val chunkTicketManager: ChunkTicketManager,
    private val restoreConfig: RestoreConfig
) {
    private val chunkLoads = AtomicLong(0)

    /**
     * Preloads all chunks required for a restore operation.
     *
     * @param world The world to load chunks from
     * @param targetChunkX The starting chunk X coordinate
     * @param targetChunkZ The starting chunk Z coordinate
     * @param sizeXChunks The number of chunks in the X direction
     * @param sizeZChunks The number of chunks in the Z direction
     * @return List of ChunkTicketHandle for all loaded chunks
     */
    suspend fun preloadChunks(
        world: UUID,
        targetChunkX: Int,
        targetChunkZ: Int,
        sizeXChunks: Int,
        sizeZChunks: Int
    ): List<ChunkTicketManager.ChunkTicketHandle> {
        // We need the actual World object - this is passed from the RestoreJob
        // This will be called from within a context where the World is available
        throw UnsupportedOperationException("Use preloadChunks(World, ...) overload instead")
    }

    /**
     * Preloads all chunks required for a restore operation.
     *
     * @param jobWorld The world to load chunks from
     * @param targetChunkX The starting chunk X coordinate
     * @param targetChunkZ The starting chunk Z coordinate
     * @param sizeXChunks The number of chunks in the X direction
     * @param sizeZChunks The number of chunks in the Z direction
     * @return List of ChunkTicketHandle for all loaded chunks
     */
    suspend fun preloadChunks(
        jobWorld: org.bukkit.World,
        targetChunkX: Int,
        targetChunkZ: Int,
        sizeXChunks: Int,
        sizeZChunks: Int
    ): List<ChunkTicketManager.ChunkTicketHandle> {
        val futures = mutableListOf<CompletableFuture<ChunkTicketManager.ChunkTicketHandle>>()
        val worldId = jobWorld.uid

        for (dx in 0 until sizeXChunks) {
            for (dz in 0 until sizeZChunks) {
                val x = targetChunkX + dx
                val z = targetChunkZ + dz
                val key = ChunkTicketManager.ChunkKey(worldId, x, z)
                val wasLoaded = jobWorld.isChunkLoaded(x, z)

                val future = jobWorld.getChunkAtAsync(x, z)
                val handleFuture = future.handle { chunk, throwable ->
                    try {
                        if (throwable != null || chunk == null) {
                            ChunkTicketManager.ChunkTicketHandle(key, null, hadTicket = false, wasLoaded = wasLoaded)
                        } else {
                            chunkTicketManager.createTicketHandle(jobWorld, x, z, chunk, wasLoaded)
                        }
                    } catch (_: Throwable) {
                        ChunkTicketManager.ChunkTicketHandle(key, null, hadTicket = false, wasLoaded = wasLoaded)
                    }
                }

                futures += handleFuture

                if (!wasLoaded) {
                    if (chunkLoads.incrementAndFetch() % restoreConfig.taskChunkLoadThrottle == 0L) {
                        delay(48)
                    }
                }
            }
        }

        return awaitAll(futures)
    }

    /**
     * Waits for all futures to complete and returns their results.
     *
     * @param futures List of CompletableFuture to wait for
     * @return List of results from the completed futures
     */
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
