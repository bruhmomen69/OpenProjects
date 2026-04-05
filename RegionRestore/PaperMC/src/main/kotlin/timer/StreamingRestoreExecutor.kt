package bruh.regionrestore.timer

import bruh.regionrestore.nms.ChunkByChunkRestore
import bruh.regionrestore.notification.AudienceScope
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.bukkit.Chunk
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Handles streaming restore mode where chunks are restored as they load.
 * This reduces memory usage but may have higher active CPU time.
 */
@OptIn(ExperimentalAtomicApi::class)
class StreamingRestoreExecutor(
    private val context: RestoreExecutionContext
) {
    private val chunkAdapter: ChunkByChunkRestore
        get() = context.nmsAdapter as ChunkByChunkRestore

    /**
     * Execute a restore using streaming mode.
     * Chunks are loaded and restored asynchronously as they become available.
     *
     * @param job The restore job to execute
     * @param audienceScope The audience scope for notifications
     * @return Pair of (active time, wall clock time) in milliseconds
     */
    suspend fun execute(job: RestoreJob, audienceScope: AudienceScope): Pair<Long, Long> {
        val start = System.currentTimeMillis()
        val totalTime = AtomicLong(0)
        val async = context.restoreConfig.asyncRestore ?: true
        val restoreFutures = mutableListOf<CompletableFuture<Unit>>()

        for ((templateChunkX, templateChunkZ) in job.template.chunkData.keys) {
            val (targetChunkX, targetChunkZ) = context.calculateTargetChunk(job, templateChunkX, templateChunkZ)

            val chunkFuture = job.world.getChunkAtAsync(targetChunkX, targetChunkZ)
            val worldId = job.world.uid
            val key = ChunkTicketManager.ChunkKey(worldId, targetChunkX, targetChunkZ)
            val neighborKeys = context.buildNeighborChunkKeys(worldId, targetChunkX, targetChunkZ)
            val wasLoaded = job.world.isChunkLoaded(targetChunkX, targetChunkZ)

            val dispatcher = if (async) {
                Dispatchers.Default
            } else {
                context.plugin.regionDispatcher(job.world, targetChunkX, targetChunkZ)
            }

            val localLock = context.chunkLockManager.accessChunkLock(targetChunkX, targetChunkZ)

            restoreFutures.add(
                chunkFuture.handle { chunk, throwable ->
                    if (throwable != null || chunk == null) {
                        context.plugin.slF4JLogger.error(
                            "Failed to load chunk at $targetChunkX, $targetChunkZ",
                            throwable
                        )
                        return@handle CompletableFuture.completedFuture(Unit)
                    }

                    val future = CompletableFuture<Unit>()
                    context.plugin.launch(dispatcher) {
                        val handle = createAndAddTicket(chunk, key, wasLoaded, targetChunkX, targetChunkZ)
                            ?: run {
                                future.complete(Unit)
                                return@launch
                            }

                        delay(42) // Try to be next tick after load to drastically reduce errors.

                        val neighborLocks = neighborKeys.map { (_, x, z) ->
                            context.chunkLockManager.accessChunkLock(x, z)
                        }

                        val locked = acquireLocksWithLogging(localLock, neighborLocks, targetChunkX, targetChunkZ)

                        executeChunkRestore(
                            job, templateChunkX, templateChunkZ, targetChunkX, targetChunkZ,
                            handle, localLock, neighborLocks, locked, totalTime, future
                        )
                    }

                    return@handle future
                }.thenCompose { it }
            )

            applyChunkLoadThrottle(wasLoaded)
        }

        CompletableFuture.allOf(*restoreFutures.toTypedArray()).await()
        val end = System.currentTimeMillis()

        return totalTime.load() to (end - start)
    }

    /**
     * Create a ticket handle and add plugin chunk ticket.
     */
    private suspend fun createAndAddTicket(
        chunk: Chunk,
        key: ChunkTicketManager.ChunkKey,
        wasLoaded: Boolean,
        targetChunkX: Int,
        targetChunkZ: Int
    ): ChunkTicketManager.ChunkTicketHandle? {
        val newCount = context.chunkTicketManager.incrementTicketRef(key)

        return if (newCount == 1) {
            try {
                chunk.addPluginChunkTicket(context.plugin)
                ChunkTicketManager.ChunkTicketHandle(key, chunk, hadTicket = true, wasLoaded = wasLoaded)
            } catch (t: Exception) {
                context.chunkTicketManager.decrementTicketRef(key)
                context.plugin.slF4JLogger.error(
                    "Failed to ticket chunk at $targetChunkX, $targetChunkZ",
                    t
                )
                null
            }
        } else {
            ChunkTicketManager.ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
        }
    }

    /**
     * Acquire locks with debug logging.
     */
    private suspend fun acquireLocksWithLogging(
        localLock: ChunkLockManager.ChunkLock,
        neighborLocks: List<ChunkLockManager.ChunkLock>,
        targetChunkX: Int,
        targetChunkZ: Int
    ): Boolean {
        val locked = context.chunkLockManager.acquireLocksWithRetry(localLock, neighborLocks) { message ->
            context.plugin.slF4JLogger.debug(message)
        }
        context.plugin.slF4JLogger.debug("Locked chunk $targetChunkX, $targetChunkZ")
        return locked
    }

    /**
     * Execute the actual chunk restore with proper locking and error handling.
     */
    private suspend fun executeChunkRestore(
        job: RestoreJob,
        templateChunkX: Int,
        templateChunkZ: Int,
        targetChunkX: Int,
        targetChunkZ: Int,
        handle: ChunkTicketManager.ChunkTicketHandle,
        localLock: ChunkLockManager.ChunkLock,
        neighborLocks: List<ChunkLockManager.ChunkLock>,
        initiallyLocked: Boolean,
        totalTime: AtomicLong,
        future: CompletableFuture<Unit>
    ) {
        var locked = initiallyLocked

        try {
            val begin = System.currentTimeMillis()
            val nextTickFuture = chunkAdapter.restoreSingleChunk(
                job.world,
                job.template,
                templateChunkX,
                templateChunkZ,
                job.targetChunkX,
                job.targetChunkZ,
                context.plugin,
                job.updateLight
            )

            // Unlock pre-await
            if (locked) {
                localLock.lock.unlock()
                locked = false
                context.plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ")
            }

            val completeTime = System.currentTimeMillis()
            totalTime.addAndFetch(completeTime - begin)

            nextTickFuture.asCompletableFuture().await()

            // Release ticket immediately after restore and tick tasks have completed.
            context.chunkTicketManager.releaseChunkTickets(listOf(handle))
            future.complete(Unit)
        } catch (e: Exception) {
            context.plugin.slF4JLogger.error(
                "Failed to restore chunk at $targetChunkX, $targetChunkZ",
                e
            )
            context.chunkTicketManager.releaseChunkTickets(listOf(handle))
            future.completeExceptionally(e)
        } finally {
            // Release local lock, if locked.
            if (locked) {
                localLock.lock.unlock()
                locked = false
                context.plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ (e-case)")
            }

            // Release lock references
            neighborLocks.forEach { context.chunkLockManager.releaseChunkLock(it) }
            context.chunkLockManager.releaseChunkLock(localLock)
        }
    }

    /**
     * Apply chunk load throttling based on configuration.
     * Matches original behavior: increments counter and delays when threshold is hit.
     */
    private suspend fun applyChunkLoadThrottle(wasLoaded: Boolean) {
        if (!wasLoaded) {
            // Increment and check threshold (same as original implementation)
            if (context.chunkTicketManager.incrementAndGetLoadCount() % context.restoreConfig.taskChunkLoadThrottle == 0L) {
                delay(48)
            }
        }
    }
}

// Extension functions are no longer needed as methods are now public in ChunkTicketManager
