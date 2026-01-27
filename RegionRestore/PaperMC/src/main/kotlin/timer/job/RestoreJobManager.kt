package bruh.regionrestore.timer.job

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.timer.RestoreJob
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.Duration.Companion.seconds

/**
 * Manages restore job scheduling, including countdowns and repeating restores.
 */
class RestoreJobManager(
    private val plugin: SuspendingJavaPlugin,
    private val notificationService: NotificationService,
    private val restoreConfig: RestoreConfig,
    private val notificationsConfig: NotificationsConfig
) {
    private val countdownJobs = ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()
    private val repeatingJobs = ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()
    private val activeRestores = AtomicInt(0)

    /**
     * Gets the current count of active restores.
     */
    fun getActiveRestoreCount(): Int = activeRestores.load()

    /**
     * Checks if a restore can be started based on concurrent restore limits.
     */
    fun canStartRestore(): Boolean = activeRestores.load() < restoreConfig.maxConcurrentRestores

    /**
     * Increments the active restore count.
     */
    fun incrementActiveRestores() = activeRestores.incrementAndFetch()

    /**
     * Decrements the active restore count.
     */
    fun decrementActiveRestores() = activeRestores.decrementAndFetch()

    /**
     * Schedules a restore with an optional countdown.
     *
     * @param job The restore job to schedule
     * @param countdownSeconds Optional countdown duration in seconds
     * @param announcePoints List of seconds to announce during countdown
     * @param audienceScope The audience scope for notifications
     * @param onRestoreReady Callback to execute when countdown completes (or immediately if no countdown)
     */
    suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int? = null,
        announcePoints: List<Int> = restoreConfig.defaultAnnounceTimes,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope,
        onRestoreReady: suspend () -> Unit
    ) {
        if (countdownSeconds != null && countdownSeconds > 0) {
            // Launch countdown as a separate job for cancellation support
            val countdownJob = plugin.launch {
                startCountdown(job, countdownSeconds, announcePoints, audienceScope, onRestoreReady)
            }
            countdownJobs[job.id] = countdownJob
        } else {
            onRestoreReady()
        }
    }

    /**
     * Starts a countdown before executing the restore.
     */
    private suspend fun startCountdown(
        job: RestoreJob,
        seconds: Int,
        announcePoints: List<Int>,
        audienceScope: AudienceScope,
        onRestoreReady: suspend () -> Unit
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

            onRestoreReady()
        } finally {
            // Clean up countdown job from map
            countdownJobs.remove(job.id)
        }
    }

    /**
     * Schedules a repeating restore.
     *
     * @param job The restore job to repeat
     * @param intervalSeconds The interval between restores in seconds
     * @param audienceScope The audience scope for notifications
     * @param onRestoreReady Callback to execute when it's time to restore
     */
    fun scheduleRepeatingRestore(
        job: RestoreJob,
        intervalSeconds: Int,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope,
        onRestoreReady: suspend () -> Unit
    ) {
        val repeatingJob = plugin.launch(plugin.asyncDispatcher) {
            while (isActive) {
                if (!job.isRunning) {
                    onRestoreReady()
                }
                delay(intervalSeconds.toLong().seconds)
            }
        }

        repeatingJobs[job.id] = repeatingJob
    }

    /**
     * Creates a notification config for the restore skipped event.
     */
    fun createRestoreSkippedNotification(reason: String): NotificationConfig? =
        NotificationConfig.fromEventConfig(
            notificationsConfig.restoreSkipped,
            mapOf("reason" to reason)
        )

    /**
     * Sends a notification for the restore skipped event.
     */
    suspend fun sendRestoreSkippedNotification(
        audienceScope: AudienceScope,
        job: RestoreJob,
        reason: String
    ) {
        val config = createRestoreSkippedNotification(reason)
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

    /**
     * Cancels a repeating restore job.
     *
     * @param jobId The ID of the job to cancel
     */
    fun cancelRepeatingRestore(jobId: UUID) {
        repeatingJobs[jobId]?.cancel()
        repeatingJobs.remove(jobId)
    }

    /**
     * Cancels a countdown job.
     *
     * @param jobId The ID of the job to cancel
     */
    fun cancelCountdown(jobId: UUID) {
        countdownJobs[jobId]?.cancel()
        countdownJobs.remove(jobId)
    }

    /**
     * Cancels all scheduled jobs (both countdowns and repeating restores).
     */
    fun cancelAll() {
        countdownJobs.values.forEach { it.cancel() }
        countdownJobs.clear()
        repeatingJobs.values.forEach { it.cancel() }
        repeatingJobs.clear()
    }
}
