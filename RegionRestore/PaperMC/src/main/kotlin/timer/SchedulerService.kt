package bruh.regionrestore.timer

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.nms.ChunkByChunkRestore
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.utils.asLong
import com.github.shynixn.mccoroutine.folia.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import org.bukkit.Chunk
import org.bukkit.World
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.roundToInt
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
    val updateLight: Boolean = false,
    val future: CompletableFuture<RestoreJob> = CompletableFuture()
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
    private val countdownJobs = ConcurrentHashMap<UUID, Job>()
    private val repeatingJobs = ConcurrentHashMap<UUID, Job>()

    private val activeRestores = AtomicInt(0)
    private val chunkLoads = AtomicLong(0)

    private val chunkTicketRefs = ConcurrentHashMap<ChunkKey, Int>()
    private val cbcLocks = ConcurrentHashMap<Long, ChunkLock>()

    private data class ChunkKey(
        val worldId: UUID,
        val x: Int,
        val z: Int
    )

    private data class ChunkLock(
        val x: Int,
        val z: Int,
        val lock: Mutex = Mutex(),
        val lockAccessCnt: AtomicInt = AtomicInt(0),
        var objectAccessCnt: Int = 0
    )

    private data class ChunkTicketHandle(
        val key: ChunkKey,
        val chunk: Chunk?,
        val hadTicket: Boolean,
        val wasLoaded: Boolean
    )

    private fun incrementTicketRef(key: ChunkKey): Int =
        chunkTicketRefs.merge(key, 1) { a, b -> a + b }!!

    private fun decrementTicketRef(key: ChunkKey): Int {
        val newCount = chunkTicketRefs.compute(key) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        return newCount ?: 0
    }

    /**
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     */
    private fun accessChunkLock(x: Int, z: Int): ChunkLock {
        return cbcLocks.compute(asLong(x, z)) { key, value ->
            val finalValue = value ?: ChunkLock(x, z)
            finalValue.objectAccessCnt++
            finalValue
        }!!
    }

    /**
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     */
    private fun releaseChunkLock(lock: ChunkLock) {
        cbcLocks.compute(asLong(lock.x, lock.z)) { key, value ->
            if (--lock.objectAccessCnt == 0) null else value
        }
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
                    if (chunkLoads.incrementAndFetch() % restoreConfig.taskChunkLoadThrottle == 0L) {
                        delay(48)
                    }
                }
            }
        }

        return awaitAll(futures)
    }

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

        if (activeRestores.load() >= restoreConfig.maxConcurrentRestores) {
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

            job.future.completeExceptionally(Exception("Maximum concurrent restores reached"))
            return
        }

        job.isRunning = true
        activeRestores.incrementAndFetch()

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
                    val neighbourChunkKeys = arrayOf(
                        ChunkKey(job.world.uid, targetChunkX, targetChunkZ - 1),
                        ChunkKey(job.world.uid, targetChunkX, targetChunkZ + 1),
                        ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ),
                        ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ),
                        ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ - 1),
                        ChunkKey(job.world.uid, targetChunkX - 1, targetChunkZ + 1),
                        ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ - 1),
                        ChunkKey(job.world.uid, targetChunkX + 1, targetChunkZ + 1)
                    )

                    val wasLoaded = job.world.isChunkLoaded(targetChunkX, targetChunkZ)
                    val dispatcher =
                        if (async)
                            Dispatchers.Default
                        else
                            plugin.regionDispatcher(
                                job.world,
                                targetChunkX,
                                targetChunkZ
                            )

                    // Setup mutexes from one thread as the map is not thread safe.
                    val localLock = accessChunkLock(targetChunkX, targetChunkZ)

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

                                delay(42) // Try to be next tick after load to drastically reduce errors.

                                val handle = ChunkTicketHandle(
                                    key = key,
                                    chunk = chunk,
                                    hadTicket = newCount == 1,
                                    wasLoaded = wasLoaded
                                )

                                val neighbourMutexes = neighbourChunkKeys.map { (_, x, z) -> accessChunkLock(x, z) }

                                // Lock this and neighbours before doing shit
                                var locked = false
                                val spinId = (Math.random() * 1000).roundToInt()
                                while (!locked) {
                                    run {
                                        localLock.lock.lock()
                                        for ((x, z, mutex, refCnt) in neighbourMutexes) {
                                            refCnt.incrementAndFetch()
                                            if (mutex.isLocked) {
                                                // Unlock local lock
                                                localLock.lock.unlock()
                                                // Await remote lock availability.
                                                plugin.slF4JLogger.debug("Task $spinId: Spinning on remote lock for chunk $x, $z")
                                                mutex.lock()
                                                mutex.unlock()
                                                plugin.slF4JLogger.debug("Task $spinId: Spun remote lock for chunk $x, $z")
                                                // Delay for additional time if someone else is checking to avoid re-checking while they re-check, resulting in a loop.
                                                val extraDelay = refCnt.decrementAndFetch().let { cnt ->
                                                    if (cnt > 0) {
                                                        cnt
                                                    } else {
                                                        0
                                                    }
                                                }
                                                // Delay by random amount to avoid lock contention
                                                delay((Math.random() * 3).roundToLong() + extraDelay)
                                                // Delay for additional again, same reasons, this time is to just deal with randoms being random
                                                val newDelay = refCnt.load()
                                                if (newDelay > 0) {
                                                    delay(newDelay.toLong())
                                                }
                                                return@run
                                            }
                                            // Otherwise, lock is not locked, new lock cannot be locked as our local lock is locked, continue.
                                            refCnt.decrementAndFetch() // Unload reference count for this op
                                        }

                                        locked = true
                                    }
                                }
                                plugin.slF4JLogger.debug("Locked chunk $targetChunkX, $targetChunkZ")

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
                                    if (locked) {
                                        localLock.lock.unlock()
                                        locked = false
                                        plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ")
                                    }

                                    val completeTime = System.currentTimeMillis()
                                    totalTime.addAndFetch(completeTime - begin)

                                    nextTickFuture.asCompletableFuture().await()

                                    // Release ticket immediately after restore and tick tasks have completed.
                                    releaseChunkTickets(listOf(handle))
                                    future.complete(Unit)
                                } catch (e: Exception) {
                                    plugin.slF4JLogger.error(
                                        "Failed to restore chunk at $targetChunkX, $targetChunkZ",
                                        e
                                    )
                                    releaseChunkTickets(listOf(handle))
                                    future.completeExceptionally(e)
                                } finally {
                                    // Release local lock, if locked.
                                    if (locked) {
                                        localLock.lock.unlock()
                                        locked = false
                                        plugin.slF4JLogger.debug("Unlocked chunk $targetChunkX, $targetChunkZ (e-case)")
                                    }

                                    // Release lock references
                                    neighbourMutexes.forEach { releaseChunkLock(it) }
                                    releaseChunkLock(localLock)
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
                plugin.slF4JLogger.info("Restore Timer: ${totalActiveTime}ms active (restore time + packet writing cpu-time aggregated), ${allTime - activeTimePer}ms total (wall clock, includes active wall clock time, chunk loading, and more).")
                plugin.slF4JLogger.info("Note that `streamingRestore` is on in your config, and causes a higher active time, but reduces memory usage and chunk load.")
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

            job.future.completeExceptionally(e)
        } finally {
            job.isRunning = false
            activeRestores.decrementAndFetch()

            if (!job.future.isDone) {
                job.future.complete(job)
            }
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
