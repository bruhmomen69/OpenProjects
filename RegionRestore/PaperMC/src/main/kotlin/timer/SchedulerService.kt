package bruh.regionrestore.timer

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.config.EntityKillerConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.util.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds

/**
 * Coordinates restore scheduling and execution.
 * Delegates actual work to specialized managers and executors.
 */
@OptIn(ExperimentalAtomicApi::class)
class SchedulerService(
    private val plugin: SuspendingJavaPlugin,
    notificationService: bruh.regionrestore.notification.NotificationService,
    private val restoreConfig: RestoreConfig,
    private val notificationsConfig: NotificationsConfig,
    private val entityKillerConfig: EntityKillerConfig,
    nmsAdapter: PaperNmsAdapter
) {
    // Core managers
    private val chunkTicketManager = ChunkTicketManager(plugin, restoreConfig)
    private val chunkLockManager = ChunkLockManager()

    // Execution context shared across all restore operations
    private val context = RestoreExecutionContext(
        plugin,
        notificationService,
        restoreConfig,
        notificationsConfig,
        nmsAdapter,
        chunkTicketManager,
        chunkLockManager
    )

    // Executors for different restore modes
    private val streamingExecutor = StreamingRestoreExecutor(context)
    private val legacyExecutor = LegacyRestoreExecutor(context)

    // Entity killer service
    private val entityKiller = EntityKiller(entityKillerConfig, plugin)

    // Job management
    private val countdownJobs = context.countdownJobs
    private val repeatingJobs = context.repeatingJobs
    private val activeRestores = context.activeRestores

    /**
     * Schedule a restore with an optional countdown.
     *
     * @param job The restore job
     * @param countdownSeconds Optional countdown before restore starts
     * @param announcePoints Points during countdown to announce
     * @param audienceScope Audience to notify
     */
    suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int? = null,
        announcePoints: List<Int> = restoreConfig.defaultAnnounceTimes,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope
    ) {
        if (countdownSeconds != null && countdownSeconds > 0) {
            val countdownJob = plugin.launch {
                startCountdown(job, countdownSeconds, announcePoints, audienceScope)
            }
            countdownJobs[job.id] = countdownJob
        } else {
            executeRestore(job, audienceScope)
        }
    }

    /**
     * Schedule a repeating restore that executes at regular intervals.
     *
     * @param job The restore job
     * @param intervalSeconds Seconds between restores
     * @param audienceScope Audience to notify
     */
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

    /**
     * Cancel a repeating restore job.
     */
    fun cancelRepeatingRestore(jobId: UUID) {
        repeatingJobs[jobId]?.cancel()
        repeatingJobs.remove(jobId)
    }

    /**
     * Cancel a countdown job.
     */
    fun cancelCountdown(jobId: UUID) {
        countdownJobs[jobId]?.cancel()
        countdownJobs.remove(jobId)
    }

    /**
     * Cancel all jobs (both countdown and repeating).
     */
    fun cancelAll() {
        countdownJobs.values.forEach { it.cancel() }
        countdownJobs.clear()
        repeatingJobs.values.forEach { it.cancel() }
        repeatingJobs.clear()
    }

    /**
     * Execute the countdown sequence, announcing at specified points.
     */
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
                    context.sendNotification(config, audienceScope, job)
                }

                delay(1.seconds)
            }

            executeRestore(job, audienceScope)
        } finally {
            countdownJobs.remove(job.id)
        }
    }

    /**
     * Execute a restore job.
     * Handles concurrency limits and delegates to appropriate executor.
     */
    private suspend fun executeRestore(
        job: RestoreJob,
        audienceScope: AudienceScope
    ) {
        if (job.isRunning) {
            return
        }

        // Check concurrency limits
        if (activeRestores.load() >= restoreConfig.maxConcurrentRestores) {
            handleConcurrencyLimitReached(job, audienceScope)
            return
        }

        job.isRunning = true
        activeRestores.incrementAndFetch()

        try {
            // Send started notification
            val startedConfig = NotificationConfig.fromEventConfig(notificationsConfig.restoreStarted)
            context.sendNotification(startedConfig, audienceScope, job)

            // Kill entities in the region before restore
            entityKiller.killEntitiesInRegion(
                job.world,
                job.minBlockX,
                job.maxBlockX,
                job.minBlockZ,
                job.maxBlockZ
            )

            val start = System.currentTimeMillis()

            // Execute using appropriate mode
            if (context.shouldUseStreamingMode(job)) {
                executeStreamingRestore(job, audienceScope, start)
            } else {
                executeLegacyRestore(job, audienceScope, start)
            }

            // Send completed notification
            val completedConfig = NotificationConfig.fromEventConfig(notificationsConfig.restoreCompleted)
            context.sendNotification(completedConfig, audienceScope, job)

        } catch (e: Exception) {
            handleRestoreError(job, audienceScope, e)
        } finally {
            job.isRunning = false
            activeRestores.decrementAndFetch()

            if (!job.future.isDone) {
                job.future.complete(job)
            }
        }
    }

    /**
     * Execute restore using streaming mode.
     */
    private suspend fun executeStreamingRestore(
        job: RestoreJob,
        audienceScope: AudienceScope,
        start: Long
    ) {
        val (activeTime, totalTime) = streamingExecutor.execute(job, audienceScope)

        plugin.slF4JLogger.info(
            "Restore Timer: ${activeTime}ms active " +
                "(restore time + packet writing cpu-time aggregated), " +
                "${totalTime}ms total " +
                "(wall clock, includes active wall clock time, chunk loading, and more)."
        )
        plugin.slF4JLogger.info(
            "Note that `streamingRestore` is on in your config, " +
                "and causes a higher active time, but reduces memory usage and chunk load."
        )
    }

    /**
     * Execute restore using legacy mode.
     */
    private suspend fun executeLegacyRestore(
        job: RestoreJob,
        audienceScope: AudienceScope,
        start: Long
    ) {
        val duration = legacyExecutor.execute(job, audienceScope)
        plugin.slF4JLogger.info("Restore took ${duration}ms")
    }

    /**
     * Handle when max concurrent restores limit is reached.
     */
    private suspend fun handleConcurrencyLimitReached(
        job: RestoreJob,
        audienceScope: AudienceScope
    ) {
        val config = NotificationConfig.fromEventConfig(
            notificationsConfig.restoreSkipped,
            mapOf("reason" to "maximum concurrent restores (${restoreConfig.maxConcurrentRestores}) reached")
        )

        context.sendNotification(config, audienceScope, job)
        job.future.completeExceptionally(Exception("Maximum concurrent restores reached"))
    }

    /**
     * Handle restore execution errors.
     */
    private suspend fun handleRestoreError(
        job: RestoreJob,
        audienceScope: AudienceScope,
        error: Exception
    ) {
        val failedConfig = NotificationConfig.fromEventConfig(
            notificationsConfig.restoreFailed,
            mapOf("error" to (error.message ?: "unknown error"))
        )

        plugin.slF4JLogger.warn("Failed to restore region ${job.id}", error)
        context.sendNotification(failedConfig, audienceScope, job)
        job.future.completeExceptionally(error)
    }
}
