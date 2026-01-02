package bruh.regionrestore.timer

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.nms.ChunkByChunkRestore
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import com.github.shynixn.mccoroutine.folia.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.bukkit.Chunk
import org.bukkit.World
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class RestoreJob(
    val id: UUID,
    val world: World,
    val targetChunkX: Int,
    val targetChunkZ: Int,
    val sizeXChunks: Int = 1,
    val sizeZChunks: Int = 1,
    val template: RegionTemplate,
    val updateLight: Boolean = false
) {
    var isRunning = false
        @Synchronized get
        @Synchronized set

    /**
     * AABB block bounds of the region.
     * Calculated from chunk coordinates and size.
     */
    val minBlockX: Int = targetChunkX * 16
    val maxBlockX: Int = (targetChunkX + sizeXChunks) * 16 - 1
    val minBlockZ: Int = targetChunkZ * 16
    val maxBlockZ: Int = (targetChunkZ + sizeZChunks) * 16 - 1
}

@OptIn(ExperimentalAtomicApi::class)
class SchedulerService(
    private val plugin: SuspendingJavaPlugin,
    private val notificationService: NotificationService,
    private val restoreConfig: RestoreConfig,
    private val notificationsConfig: NotificationsConfig,
    private val nmsAdapter: PaperNmsAdapter
) {
    private val restoreJobs = ConcurrentHashMap<UUID, RestoreJob>()
    private val countdownJobs = ConcurrentHashMap<UUID, Job>()
    private val repeatingJobs = ConcurrentHashMap<UUID, Job>()
    private val activeRestores = AtomicInteger(0)

    @OptIn(ExperimentalAtomicApi::class)
    private val chunkLoads = AtomicLong(0)

    private data class ChunkKey(
        val worldId: UUID,
        val x: Int,
        val z: Int
    )

    private data class ChunkTicketHandle(
        val key: ChunkKey,
        val chunk: Chunk?,
        val hadTicket: Boolean,
        val wasLoaded: Boolean
    )

    private val chunkTicketRefs = ConcurrentHashMap<ChunkKey, Int>()

    private fun incrementTicketRef(key: ChunkKey): Int =
        chunkTicketRefs.merge(key, 1) { a, b -> a + b }!!

    private fun decrementTicketRef(key: ChunkKey): Int {
        val newCount = chunkTicketRefs.compute(key) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        return newCount ?: 0
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

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun preloadChunks(job: RestoreJob): List<ChunkTicketHandle> {
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
                                } catch (t: Throwable) {
                                    // Roll back ref count and continue without a ticket
                                    decrementTicketRef(key)
                                    ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
                                }
                            }

                            ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)
                        }
                    } catch (t: Throwable) {
                        ChunkTicketHandle(key, null, hadTicket = false, wasLoaded = wasLoaded)
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

    @OptIn(ExperimentalAtomicApi::class)
    private fun releaseChunkTickets(handles: List<ChunkTicketHandle>) {

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

    suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int? = null,
        announcePoints: List<Int> = restoreConfig.defaultAnnounceTimes,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope
    ) {
        if (countdownSeconds != null && countdownSeconds > 0) {
            // Launch countdown as a separate job for cancellation support
            val countdownJob = plugin.launch {
                startCountdown(job, countdownSeconds, announcePoints, audienceScope)
            }
            countdownJobs[job.id] = countdownJob
        } else {
            executeRestore(job, audienceScope)
        }
    }

    private suspend fun startCountdown(
        job: RestoreJob,
        seconds: Int,
        announcePoints: List<Int>,
        audienceScope: AudienceScope
    ) {
        try {
            for (remaining in seconds downTo 1) {
                if (remaining in announcePoints) {
                    val config = NotificationConfig.fromEventConfig(
                        notificationsConfig.countdownTick,
                        mapOf(
                            "seconds" to "$remaining",
                            "seconds,s" to if (remaining == 1) "" else "s"
                        )
                    )

                    if (config != null) {
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

                delay(1.seconds)
            }

            executeRestore(job, audienceScope)
        } finally {
            // Clean up countdown job from map
            countdownJobs.remove(job.id)
        }
    }

    fun scheduleRepeatingRestore(
        job: RestoreJob,
        intervalSeconds: Int,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope
    ) {
        val repeatingJob = plugin.launch(plugin.asyncDispatcher) {
            while (isActive) {
                if (!job.isRunning) {
                    executeRestore(job, audienceScope)
                }
                delay(intervalSeconds.toLong().seconds)
            }
        }

        repeatingJobs[job.id] = repeatingJob
    }

    private suspend fun executeRestore(
        job: RestoreJob,
        audienceScope: AudienceScope
    ) {
        if (job.isRunning) {
            return
        }

        if (activeRestores.get() >= restoreConfig.maxConcurrentRestores) {
            val config = NotificationConfig.fromEventConfig(
                notificationsConfig.restoreSkipped,
                mapOf("reason" to "maximum concurrent restores (${restoreConfig.maxConcurrentRestores}) reached")
            )

            if (config != null) {
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
            return
        }

        job.isRunning = true
        activeRestores.incrementAndGet()

        try {
            val startedConfig = NotificationConfig.fromEventConfig(notificationsConfig.restoreStarted)
            if (startedConfig != null) {
                notificationService.sendNotification(
                    audienceScope,
                    startedConfig,
                    world = job.world,
                    minBlockX = job.minBlockX,
                    maxBlockX = job.maxBlockX,
                    minBlockZ = job.minBlockZ,
                    maxBlockZ = job.maxBlockZ
                )
            }

            val start = System.currentTimeMillis()

            // Check if adapter supports chunk-by-chunk streaming
            if (nmsAdapter is ChunkByChunkRestore &&
                restoreConfig.streamingRestore &&
                job.sizeXChunks * job.sizeZChunks >
                (restoreConfig.taskChunkLoadThrottle * 0.9)
                    .roundToLong()
                    .coerceAtLeast(100)
                    .coerceAtMost(1000)
            ) {
                // Streaming mode: restore chunks as they load
                val chunkAdapter = nmsAdapter as ChunkByChunkRestore
                val restoreFutures = mutableListOf<CompletableFuture<Unit>>()
                val totalTime = AtomicLong(0)
                val async = restoreConfig.asyncRestore ?: true

                for ((templateChunkX, templateChunkZ) in job.template.chunkData.keys) {
                    val targetChunkX = templateChunkX - job.template.minChunkX + job.targetChunkX
                    val targetChunkZ = templateChunkZ - job.template.minChunkZ + job.targetChunkZ

                    // Start async chunk load
                    val chunkFuture = job.world.getChunkAtAsync(targetChunkX, targetChunkZ)
                    val key = ChunkKey(job.world.uid, targetChunkX, targetChunkZ)
                    val wasLoaded = job.world.isChunkLoaded(targetChunkX, targetChunkZ)
                    val dispatcher =
                        if (async)
                            plugin.asyncDispatcher
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
                                val newCount = incrementTicketRef(key)
                                if (newCount == 1) {
                                    try {
                                        chunk.addPluginChunkTicket(plugin)
                                        ChunkTicketHandle(key, chunk, hadTicket = true, wasLoaded = wasLoaded)
                                    } catch (t: Exception) {
                                        // Roll back ref count and continue without a ticket
                                        decrementTicketRef(key)
                                        ChunkTicketHandle(key, chunk, hadTicket = false, wasLoaded = wasLoaded)

                                        plugin.slF4JLogger.error(
                                            "Failed to ticket chunk at $targetChunkX, $targetChunkZ",
                                            t
                                        )
                                        future.complete(Unit)
                                    }
                                }

                                val handle = ChunkTicketHandle(
                                    key = key,
                                    chunk = chunk,
                                    hadTicket = newCount == 1,
                                    wasLoaded = wasLoaded
                                )

                                try {
                                    // Restore this chunk immediately
                                    val begin = System.currentTimeMillis()
                                    chunkAdapter.restoreSingleChunk(
                                        job.world,
                                        job.template,
                                        templateChunkX,
                                        templateChunkZ,
                                        job.targetChunkX,
                                        job.targetChunkZ,
                                        plugin,
                                        job.updateLight
                                    )
                                    val completeTime = System.currentTimeMillis()
                                    totalTime.addAndFetch(completeTime - begin)

                                    // Release ticket immediately after restore
                                    releaseChunkTickets(listOf(handle))
                                    future.complete(Unit)
                                } catch (e: Exception) {
                                    plugin.slF4JLogger.error(
                                        "Failed to restore chunk at $targetChunkX, $targetChunkZ",
                                        e
                                    )
                                    releaseChunkTickets(listOf(handle))
                                    future.completeExceptionally(e)
                                }
                            }

                            return@handle future
                        }.thenCompose { it }
                    )

                    // Throttle if this wasn't already loaded
                    if (!wasLoaded) {
                        @OptIn(ExperimentalAtomicApi::class)
                        if (chunkLoads.incrementAndFetch() % restoreConfig.taskChunkLoadThrottle == 0L) {
                            delay(48)
                        }
                    }
                }

                // Wait for all chunks to complete
                CompletableFuture.allOf(*restoreFutures.toTypedArray()).await()
                val end = System.currentTimeMillis()
                val allTime = end - start
                val totalActiveTime = totalTime.load()
                val activeTimePer = totalActiveTime / restoreFutures.size
                plugin.slF4JLogger.info("Restore took ${totalActiveTime}ms active, ${allTime - activeTimePer}ms total. \nNote that the `streamingRestore` is on in your config, and causes a higher active time, but reduces memory usage and chunk load.")
            } else {
                // Legacy mode: preload all chunks, then restore all, then release all
                var chunkHandles: List<ChunkTicketHandle> = emptyList()
                try {
                    chunkHandles = preloadChunks(job)

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
                    releaseChunkTickets(chunkHandles)
                }
                val end = System.currentTimeMillis()
                plugin.slF4JLogger.info("Restore took ${end - start}ms")
            }

            val completedConfig = NotificationConfig.fromEventConfig(notificationsConfig.restoreCompleted)
            if (completedConfig != null) {
                notificationService.sendNotification(
                    audienceScope,
                    completedConfig,
                    world = job.world,
                    minBlockX = job.minBlockX,
                    maxBlockX = job.maxBlockX,
                    minBlockZ = job.minBlockZ,
                    maxBlockZ = job.maxBlockZ
                )
            }
        } catch (e: Exception) {
            val failedConfig = NotificationConfig.fromEventConfig(
                notificationsConfig.restoreFailed,
                mapOf("error" to (e.message ?: "unknown error"))
            )

            plugin.slF4JLogger.warn("Failed to restore region ${job.id}", e)

            if (failedConfig != null) {
                notificationService.sendNotification(
                    audienceScope,
                    failedConfig,
                    world = job.world,
                    minBlockX = job.minBlockX,
                    maxBlockX = job.maxBlockX,
                    minBlockZ = job.minBlockZ,
                    maxBlockZ = job.maxBlockZ
                )
            }
        } finally {
            job.isRunning = false
            activeRestores.decrementAndGet()
        }
    }

    fun cancelRepeatingRestore(jobId: UUID) {
        this@SchedulerService.repeatingJobs[jobId]?.cancel()
        this@SchedulerService.repeatingJobs.remove(jobId)
    }

    fun cancelCountdown(jobId: UUID) {
        this@SchedulerService.countdownJobs[jobId]?.cancel()
        this@SchedulerService.countdownJobs.remove(jobId)
    }

    fun cancelAll() {
        this@SchedulerService.countdownJobs.values.forEach { it.cancel() }
        this@SchedulerService.countdownJobs.clear()
        this@SchedulerService.repeatingJobs.values.forEach { it.cancel() }
        this@SchedulerService.repeatingJobs.clear()
    }
}
