package bruh.regionrestore.restore

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
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
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

    private val taskCount = AtomicLong(0)

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
        val useChunkLocking = context.shouldUseChunkLocking()

        val maxMem =
            if (Runtime.getRuntime().maxMemory() == Long.MAX_VALUE) 8000000000L else Runtime.getRuntime().maxMemory()
        val speedMult = maxMem / 1000 / 1000 / 1000
        // Add 25% to account for pre-chunk-load locking (and similar) delay. The other delays are still correct and use the normal values.
        val maxSpeed = context.restoreConfig.taskLoadThrottle * speedMult * 1.25

        val completedTasks = AtomicLong(0)
        val totalTasks = job.template.chunkData.size.toLong()
        var lastNotification = Clock.System.now()

        for ((templateChunkX, templateChunkZ) in job.template.chunkData.keys) {
            val (targetChunkX, targetChunkZ) = context.calculateTargetChunk(job, templateChunkX, templateChunkZ)

            taskCount.incrementAndFetch()

            val worldId = job.world.uid
            val key = ChunkTicketManager.ChunkKey(worldId, targetChunkX, targetChunkZ)
            val neighborKeys = context.buildNeighborChunkKeys(worldId, targetChunkX, targetChunkZ)
            val wasLoaded = job.world.isChunkLoaded(targetChunkX, targetChunkZ)

            val dispatcher = if (async) {
                Dispatchers.Default
            } else {
                context.plugin.regionDispatcher(job.world, targetChunkX, targetChunkZ)
            }

            val future = CompletableFuture<Unit>()
            context.plugin.launch(dispatcher) {
                val (localLock, neighborLocks, locked) = if (useChunkLocking) {
                    val localLock = context.chunkLockManager.accessChunkLock(targetChunkX, targetChunkZ)
                    val neighborLocks = neighborKeys.map { (_, x, z) ->
                        context.chunkLockManager.accessChunkLock(x, z)
                    }
                    val locked = acquireLocksWithLogging(localLock, neighborLocks, targetChunkX, targetChunkZ)
                    Triple(localLock, neighborLocks, locked)
                } else {
                    Triple(null, emptyList(), false)
                }

                val chunk = try {
                    job.world.getChunkAtAsync(targetChunkX, targetChunkZ).await()
                } catch (e: Exception) {
                    context.slF4JLogger.error(
                        "Failed to load chunk at $targetChunkX, $targetChunkZ",
                        e
                    )
                    future.completeExceptionally(e)
                    return@launch
                }

                val handle = createAndAddTicket(chunk, key, wasLoaded, targetChunkX, targetChunkZ)
                    ?: run {
                        future.complete(Unit)
                        return@launch
                    }

                executeChunkRestore(
                    job, templateChunkX, templateChunkZ, targetChunkX, targetChunkZ,
                    handle, localLock, neighborLocks, locked, totalTime, future, useChunkLocking
                )
            }

            restoreFutures.add(
                future.whenComplete { _, _ ->
                    taskCount.decrementAndFetch()
                    completedTasks.incrementAndFetch()
                }
            )

            if (taskCount.load() > maxSpeed) {
                val now = Clock.System.now()
                if (now - lastNotification > 5.seconds) {
                    val speed = taskCount.load().toDouble() / maxSpeed
                    val percent = (speed * 100).roundToInt()
                    val completedPercent = (completedTasks.load().toDouble() / totalTasks * 100).roundToInt()
                    context.slF4JLogger.warn(
                        "Throttling chunk load tasks: $percent% of max speed (${taskCount.load()} tasks queued), " +
                                "$completedPercent% completed"
                    )
                    lastNotification = now
                }
                delay(200.milliseconds)
            }
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
                context.slF4JLogger.error(
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
            context.slF4JLogger.debug(message)
        }
        context.slF4JLogger.debug("Locked chunk $targetChunkX, $targetChunkZ")
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
        localLock: ChunkLockManager.ChunkLock?,
        neighborLocks: List<ChunkLockManager.ChunkLock>,
        initiallyLocked: Boolean,
        totalTime: AtomicLong,
        future: CompletableFuture<Unit>,
        useChunkLocking: Boolean
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

            if (useChunkLocking && locked) {
                localLock!!.lock.unlock()
                locked = false
                context.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ")
            }

            val completeTime = System.currentTimeMillis()
            totalTime.addAndFetch(completeTime - begin)

            nextTickFuture.asCompletableFuture().await()

            context.chunkTicketManager.releaseChunkTickets(listOf(handle))
            future.complete(Unit)
        } catch (e: Exception) {
            context.slF4JLogger.error(
                "Failed to restore chunk at $targetChunkX, $targetChunkZ",
                e
            )
            context.chunkTicketManager.releaseChunkTickets(listOf(handle))
            future.completeExceptionally(e)
        } finally {
            if (useChunkLocking && locked) {
                localLock!!.lock.unlock()
                locked = false
                context.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ (e-case)")
            }

            if (useChunkLocking) {
                neighborLocks.forEach { context.chunkLockManager.releaseChunkLock(it) }
                context.chunkLockManager.releaseChunkLock(localLock!!)
            }
        }
    }
}

// Extension functions are no longer needed as methods are now public in ChunkTicketManager
