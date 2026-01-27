package bruh.regionrestore.timer

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.timer.chunk.ChunkLockManager
import bruh.regionrestore.timer.chunk.ChunkPreloader
import bruh.regionrestore.timer.chunk.ChunkTicketManager
import bruh.regionrestore.timer.job.RestoreJobManager
import bruh.regionrestore.timer.restore.RestoreExecutor
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import java.util.UUID

/**
 * Data class representing a restore job.
 *
 * @property id Unique identifier for this job
 * @property world The world to restore in
 * @property targetChunkX The target chunk X coordinate
 * @property targetChunkZ The target chunk Z coordinate
 * @property sizeXChunks The number of chunks in the X direction (default 1)
 * @property sizeZChunks The number of chunks in the Z direction (default 1)
 * @property template The region template to restore
 * @property updateLight Whether to update lighting after restore (default false)
 * @property future CompletableFuture that completes when the restore is done
 */
data class RestoreJob(
    val id: UUID,
    val world: org.bukkit.World,
    val targetChunkX: Int,
    val targetChunkZ: Int,
    val sizeXChunks: Int = 1,
    val sizeZChunks: Int = 1,
    val template: bruh.regionrestore.nms.RegionTemplate,
    val updateLight: Boolean = false,
    val future: java.util.concurrent.CompletableFuture<RestoreJob> = java.util.concurrent.CompletableFuture()
) {
    var isRunning: Boolean = false
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

/**
 * Service for scheduling and managing restore operations.
 *
 * This service coordinates between various managers to provide a complete restore scheduling solution:
 * - RestoreJobManager: Handles job scheduling, countdowns, and repeating restores
 * - RestoreExecutor: Handles the actual execution of restore operations
 * - ChunkTicketManager: Manages chunk tickets to prevent unloading
 * - ChunkLockManager: Manages chunk locking for concurrent operations
 * - ChunkPreloader: Handles preloading chunks for legacy restore mode
 */
class SchedulerService(
    private val plugin: SuspendingJavaPlugin,
    private val notificationService: NotificationService,
    private val restoreConfig: RestoreConfig,
    private val notificationsConfig: NotificationsConfig,
    private val nmsAdapter: PaperNmsAdapter
) {
    private val restoreJobManager = RestoreJobManager(
        plugin,
        notificationService,
        restoreConfig,
        notificationsConfig
    )

    private val chunkTicketManager = ChunkTicketManager(
        plugin,
        restoreConfig
    )

    private val chunkLockManager = ChunkLockManager(
        plugin
    )

    private val chunkPreloader = ChunkPreloader(
        plugin,
        chunkTicketManager,
        restoreConfig
    )

    private val restoreExecutor = RestoreExecutor(
        plugin,
        notificationService,
        restoreConfig,
        notificationsConfig,
        nmsAdapter,
        chunkTicketManager,
        chunkLockManager,
        chunkPreloader
    )

    /**
     * Schedules a restore operation.
     *
     * @param job The restore job to schedule
     * @param countdownSeconds Optional countdown duration in seconds
     * @param announcePoints List of seconds to announce during countdown
     * @param audienceScope The audience scope for notifications
     */
    suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int? = null,
        announcePoints: List<Int> = restoreConfig.defaultAnnounceTimes,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope
    ) {
        restoreJobManager.scheduleRestore(
            job,
            countdownSeconds,
            announcePoints,
            audienceScope
        ) {
            executeRestore(job, audienceScope)
        }
    }

    /**
     * Schedules a repeating restore operation.
     *
     * @param job The restore job to repeat
     * @param intervalSeconds The interval between restores in seconds
     * @param audienceScope The audience scope for notifications
     */
    fun scheduleRepeatingRestore(
        job: RestoreJob,
        intervalSeconds: Int,
        audienceScope: AudienceScope = notificationsConfig.defaultAudienceScope
    ) {
        restoreJobManager.scheduleRepeatingRestore(
            job,
            intervalSeconds,
            audienceScope
        ) {
            plugin.launch {
                executeRestore(job, audienceScope)
            }
        }
    }

    /**
     * Executes a restore operation.
     */
    private suspend fun executeRestore(
        job: RestoreJob,
        audienceScope: AudienceScope
    ) {
        // Check if job is already running
        if (job.isRunning) {
            return
        }

        // Check if we can start a new restore (concurrent restore limit)
        if (!restoreJobManager.canStartRestore()) {
            val reason = "maximum concurrent restores (${restoreConfig.maxConcurrentRestores}) reached"
            restoreJobManager.sendRestoreSkippedNotification(audienceScope, job, reason)
            job.future.completeExceptionally(Exception("Maximum concurrent restores reached"))
            return
        }

        // Mark job as running and increment active restore count
        job.isRunning = true
        restoreJobManager.incrementActiveRestores()

        try {
            // Execute the restore using the RestoreExecutor
            restoreExecutor.executeRestore(
                job,
                audienceScope,
                onRestoreStarted = {
                    // Send restore started notification
                    val config = restoreExecutor.createRestoreStartedNotification()
                    if (config != null) {
                        restoreExecutor.sendNotification(config, audienceScope, job)
                    }
                },
                onRestoreCompleted = {
                    // Send restore completed notification
                    val config = restoreExecutor.createRestoreCompletedNotification()
                    if (config != null) {
                        restoreExecutor.sendNotification(config, audienceScope, job)
                    }
                },
                onRestoreFailed = { e ->
                    // Log error and send restore failed notification
                    plugin.slF4JLogger.warn("Failed to restore region ${job.id}", e)
                    val config = restoreExecutor.createRestoreFailedNotification(
                        e.message ?: "unknown error"
                    )
                    if (config != null) {
                        restoreExecutor.sendNotification(config, audienceScope, job)
                    }
                    job.future.completeExceptionally(e)
                }
            )
        } finally {
            // Mark job as not running and decrement active restore count
            job.isRunning = false
            restoreJobManager.decrementActiveRestores()

            // Complete the future if not already done
            if (!job.future.isDone) {
                job.future.complete(job)
            }
        }
    }

    /**
     * Cancels a repeating restore job.
     *
     * @param jobId The ID of the job to cancel
     */
    fun cancelRepeatingRestore(jobId: UUID) {
        restoreJobManager.cancelRepeatingRestore(jobId)
    }

    /**
     * Cancels a countdown job.
     *
     * @param jobId The ID of the job to cancel
     */
    fun cancelCountdown(jobId: UUID) {
        restoreJobManager.cancelCountdown(jobId)
    }

    /**
     * Cancels all scheduled jobs (both countdowns and repeating restores).
     */
    fun cancelAll() {
        restoreJobManager.cancelAll()
    }
}
