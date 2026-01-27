package bruh.regionrestore.timer

import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.NotificationEventConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.nms.PaperNmsAdapter
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Context object that holds all dependencies and state needed for restore execution.
 * This allows clean separation between scheduling logic and execution logic.
 */
@OptIn(ExperimentalAtomicApi::class)
class RestoreExecutionContext(
    val plugin: SuspendingJavaPlugin,
    val notificationService: NotificationService,
    val restoreConfig: RestoreConfig,
    val notificationsConfig: NotificationsConfig,
    val nmsAdapter: PaperNmsAdapter,
    val chunkTicketManager: ChunkTicketManager,
    val chunkLockManager: ChunkLockManager
) {
    val activeRestores = AtomicInt(0)

    // Job tracking
    val countdownJobs = ConcurrentHashMap<UUID, Job>()
    val repeatingJobs = ConcurrentHashMap<UUID, Job>()

    /**
     * Send a restore notification with standard parameters.
     */
    suspend fun sendNotification(
        config: NotificationConfig?,
        audienceScope: AudienceScope,
        job: RestoreJob
    ) {
        if (config == null) return

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

    /**
     * Create notification config from event config with placeholders.
     */
    fun createNotificationConfig(
        eventConfig: NotificationEventConfig,
        placeholders: Map<String, String> = emptyMap()
    ): NotificationConfig? {
        return NotificationConfig.fromEventConfig(eventConfig, placeholders)
    }

    /**
     * Get the appropriate dispatcher for async operations.
     */
    fun getAsyncDispatcher(async: Boolean): CoroutineContext {
        return if (async) Dispatchers.Default else plugin.globalRegionDispatcher
    }

    /**
     * Check if streaming mode should be used for this job.
     */
    fun shouldUseStreamingMode(job: RestoreJob): Boolean {
        return nmsAdapter is bruh.regionrestore.nms.ChunkByChunkRestore &&
            restoreConfig.streamingRestore &&
            job.sizeXChunks * job.sizeZChunks >
            (restoreConfig.taskChunkLoadThrottle * 0.9)
                .toLong()
                .coerceAtLeast(100)
                .coerceAtMost(1000)
    }

    /**
     * Build a set of neighbor chunk keys for a given chunk position.
     */
    fun buildNeighborChunkKeys(worldId: UUID, chunkX: Int, chunkZ: Int): List<ChunkTicketManager.ChunkKey> {
        return listOf(
            ChunkTicketManager.ChunkKey(worldId, chunkX, chunkZ - 1),
            ChunkTicketManager.ChunkKey(worldId, chunkX, chunkZ + 1),
            ChunkTicketManager.ChunkKey(worldId, chunkX - 1, chunkZ),
            ChunkTicketManager.ChunkKey(worldId, chunkX + 1, chunkZ),
            ChunkTicketManager.ChunkKey(worldId, chunkX - 1, chunkZ - 1),
            ChunkTicketManager.ChunkKey(worldId, chunkX - 1, chunkZ + 1),
            ChunkTicketManager.ChunkKey(worldId, chunkX + 1, chunkZ - 1),
            ChunkTicketManager.ChunkKey(worldId, chunkX + 1, chunkZ + 1)
        )
    }

    /**
     * Calculate the target chunk position from template coordinates.
     */
    fun calculateTargetChunk(
        job: RestoreJob,
        templateChunkX: Int,
        templateChunkZ: Int
    ): Pair<Int, Int> {
        val targetChunkX = templateChunkX - job.template.minChunkX + job.targetChunkX
        val targetChunkZ = templateChunkZ - job.template.minChunkZ + job.targetChunkZ
        return targetChunkX to targetChunkZ
    }
}
