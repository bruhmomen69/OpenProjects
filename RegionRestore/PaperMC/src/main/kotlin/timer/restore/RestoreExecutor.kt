package bruh.regionrestore.timer.restore

import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.nms.ChunkByChunkRestore
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.chunk.ChunkLockManager
import bruh.regionrestore.timer.chunk.ChunkPreloader
import bruh.regionrestore.timer.chunk.ChunkTicketManager
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.atomics.AtomicLong
import kotlin.math.roundToLong

/**
 * Handles the actual execution of restore operations.
 * Supports both streaming (chunk-by-chunk) and legacy (bulk) restore modes.
 */
class RestoreExecutor(
    private val plugin: SuspendingJavaPlugin,
    private val notificationService: NotificationService,
    private val restoreConfig: RestoreConfig,
    private val notificationsConfig: bruh.regionrestore.config.NotificationsConfig,
    private val nmsAdapter: PaperNmsAdapter,
    private val chunkTicketManager: ChunkTicketManager,
    private val chunkLockManager: ChunkLockManager,
    private val chunkPreloader: ChunkPreloader
) {
    /**
     * Executes the restore operation for a given job.
     *
     * @param job The restore job to execute
     * @param audienceScope The audience scope for notifications
     * @param onRestoreStarted Callback when restore is about to start
     * @param onRestoreCompleted Callback when restore completes successfully
     * @param onRestoreFailed Callback when restore fails
     */
    suspend fun executeRestore(
        job: RestoreJob,
        audienceScope: AudienceScope,
        onRestoreStarted: suspend () -> Unit = {},
        onRestoreCompleted: suspend () -> Unit = {},
        onRestoreFailed: suspend (Exception) -> Unit = {}
    ) {
        try {
            onRestoreStarted()

            val start = System.currentTimeMillis()

            // Check if adapter supports chunk-by-chunk streaming
            if (shouldUseStreamingRestore(job)) {
                executeStreamingRestore(job, audienceScope, start)
            } else {
                executeLegacyRestore(job, start)
            }

            onRestoreCompleted()
        } catch (e: Exception) {
            onRestoreFailed(e)
        }
    }

    /**
     * Determines if streaming restore should be used for the given job.
     */
    private fun shouldUseStreamingRestore(job: RestoreJob): Boolean {
        return nmsAdapter is ChunkByChunkRestore &&
                restoreConfig.streamingRestore &&
                job.sizeXChunks * job.sizeZChunks >
                (restoreConfig.taskChunkLoadThrottle * 0.9)
                    .roundToLong()
                    .coerceAtLeast(100)
                    .coerceAtMost(1000)
    }

    /**
     * Executes a streaming restore, restoring chunks as they load.
     */
    private suspend fun executeStreamingRestore(
        job: RestoreJob,
        audienceScope: AudienceScope,
        start: Long
    ) {
        val chunkAdapter = nmsAdapter as ChunkByChunkRestore
        val restoreFutures = mutableListOf<CompletableFuture<Unit>>()
        val totalTime = AtomicLong(0)
        val async = restoreConfig.asyncRestore ?: true

        for ((templateChunkX, templateChunkZ) in job.template.chunkData.keys) {
            val targetChunkX = templateChunkX - job.template.minChunkX + job.targetChunkX
            val targetChunkZ = templateChunkZ - job.template.minChunkZ + job.targetChunkZ

            // Start async chunk load
            val chunkFuture = job.world.getChunkAtAsync(targetChunkX, targetChunkZ)
            val key = ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX, targetChunkZ)
            val neighbourChunkKeys = arrayOf(
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX, targetChunkZ - 1),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX, targetChunkZ + 1),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ - 1),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ + 1),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ - 1),
                ChunkTicketManager.ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ + 1)
            )

            val wasLoaded = job.world.isChunkLoaded(targetChunkX, targetChunkZ)
            val dispatcher =
                if (async)
                    kotlinx.coroutines.Dispatchers.Default
                else
                    plugin.regionDispatcher(
                        job.world,
                        targetChunkX,
                        targetChunkZ
                    )

            restoreFutures.add(
                chunkFuture.handle { chunk, throwable ->
                    if (throwable != null || chunk == null) {
                        plugin.slF4JLogger.error(
                            "Failed to load chunk at $targetChunkX, $targetChunkZ",
                            throwable
                        )
                        return@handle CompletableFuture.completedFuture(Unit)
                    }

                    val future = CompletableFuture<Unit>()
                    plugin.launch(dispatcher) {
                        // Create ticket handle for tracking
                        val newCount = chunkTicketManager.incrementTicketRef(key)
                        var handle: ChunkTicketManager.ChunkTicketHandle
                        
                        if (newCount == 1) {
                            try {
                                chunk.addPluginChunkTicket(plugin)
                                handle = ChunkTicketManager.ChunkTicketHandle(key, chunk, hadTicket = true, wasLoaded = wasLoaded)
                            } catch (t: Exception) {
                                // Roll back ref count and continue without a ticket
                                chunkTicketManager.decrementTicketRef(key)
                                handle = ChunkTicketManager.ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)

                                plugin.slF4JLogger.error(
                                    "Failed to ticket chunk at $targetChunkX, $targetChunkZ",
                                    t
                                )
                                future.complete(Unit)
                                return@launch
                            }
                        } else {
                            handle = ChunkTicketManager.ChunkTicketHandle(
                                key = key,
                                chunk = chunk,
                                hadTicket = false,
                                wasLoaded = wasLoaded
                            )
                        }

                        delay(42) // Try to be next tick after load to drastically reduce errors.

                        // Acquire locks for this chunk and neighbors
                        val lockedChunks = chunkLockManager.acquireChunkAndNeighborLocks(
                            targetChunkX,
                            targetChunkZ,
                            job.world.uid
                        )

                        // Actually run the restores
                        try {
                            // Restore this chunk immediately
                            val begin = System.currentTimeMillis()
                            val nextTickFuture = chunkAdapter.restoreSingleChunk(
                                job.world,
                                job.template,
                                templateChunkX,
                                templateChunkZ,
                                job.targetChunkX,
                                job.targetChunkZ,
                                plugin,
                                job.updateLight
                            )

                            // Unlock pre-await
                            lockedChunks.unlockLocal()
                            plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ")

                            val completeTime = System.currentTimeMillis()
                            totalTime.addAndFetch(completeTime - begin)

                            nextTickFuture.asCompletableFuture().await()

                            // Release ticket immediately after restore and tick tasks have completed.
                            chunkTicketManager.releaseChunkTickets(listOf(handle))
                            future.complete(Unit)
                        } catch (e: Exception) {
                            plugin.slF4JLogger.error(
                                "Failed to restore chunk at $targetChunkX, $targetChunkZ",
                                e
                            )
                            chunkTicketManager.releaseChunkTickets(listOf(handle))
                            future.completeExceptionally(e)
                        } finally {
                            // Release all locks
                            lockedChunks.unlock(chunkLockManager)
                            plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ (finally)")
                        }
                    }

                    return@handle future
                }.thenCompose { it }
            )

            // Throttle if this wasn't already loaded
            if (!wasLoaded) {
                if (chunkTicketManager.incrementTicketRef(key) % restoreConfig.taskChunkLoadThrottle == 0L) {
                    // Decrement since we just incremented for throttle check
                    chunkTicketManager.decrementTicketRef(key)
                    delay(48)
                } else {
                    // Decrement since we just incremented for throttle check
                    chunkTicketManager.decrementTicketRef(key)
                }
            }
        }

        // Wait for all chunks to complete
        CompletableFuture.allOf(*restoreFutures.toTypedArray()).await()
        val end = System.currentTimeMillis()
        val allTime = end - start
        val totalActiveTime = totalTime.load()
        val activeTimePer = totalActiveTime / restoreFutures.size
        plugin.slF4JLogger.info("Restore Timer: ${totalActiveTime}ms active (restore time + packet writing cpu-time aggregated), ${allTime - activeTimePer}ms total (wall clock, includes active wall clock time, chunk loading, and more).")
        plugin.slF4JLogger.info("Note that `streamingRestore` is on in your config, and causes a higher active time, but reduces memory usage and chunk load.")
    }

    /**
     * Executes a legacy restore, preloading all chunks first, then restoring all at once.
     */
    private suspend fun executeLegacyRestore(
        job: RestoreJob,
        start: Long
    ) {
        var chunkHandles: List<ChunkTicketManager.ChunkTicketHandle> = emptyList()
        try {
            chunkHandles = chunkPreloader.preloadChunks(
                job.world,
                job.targetChunkX,
                job.targetChunkZ,
                job.sizeXChunks,
                job.sizeZChunks
            )

            withContext(
                if (restoreConfig.asyncRestore
                        ?: false
                ) plugin.asyncDispatcher else plugin.globalRegionDispatcher
            ) {
                nmsAdapter.restoreTemplate(
                    job.world,
                    job.template,
                    job.targetChunkX,
                    job.targetChunkZ,
                    plugin,
                    job.updateLight
                )
            }
        } finally {
            chunkTicketManager.releaseChunkTickets(chunkHandles)
        }
        val end = System.currentTimeMillis()
        plugin.slF4JLogger.info("Restore took ${end - start}ms")
    }

    /**
     * Creates a notification config for the restore started event.
     */
    fun createRestoreStartedNotification(): NotificationConfig? =
        NotificationConfig.fromEventConfig(notificationsConfig.restoreStarted)

    /**
     * Creates a notification config for the restore completed event.
     */
    fun createRestoreCompletedNotification(): NotificationConfig? =
        NotificationConfig.fromEventConfig(notificationsConfig.restoreCompleted)

    /**
     * Creates a notification config for the restore failed event.
     */
    fun createRestoreFailedNotification(error: String): NotificationConfig? =
        NotificationConfig.fromEventConfig(
            notificationsConfig.restoreFailed,
            mapOf("error" to error)
        )

    /**
     * Creates a notification config for the restore skipped event.
     */
    fun createRestoreSkippedNotification(reason: String): NotificationConfig? =
        NotificationConfig.fromEventConfig(
            notificationsConfig.restoreSkipped,
            mapOf("reason" to reason)
        )

    /**
     * Sends a notification for the given config and job details.
     */
    suspend fun sendNotification(
        config: NotificationConfig,
        audienceScope: AudienceScope,
        job: RestoreJob
    ) {
        notificationService.sendNotification(
            audienceScope,
            config,
            world = job.world,
            minBlockX = job.minBlockX,
            maxBlockX = job.maxBlockX,
            minBlockZ = job.minBlockZ,
            maxBlockZ = job.maxBlockZ
        )
    }
}
