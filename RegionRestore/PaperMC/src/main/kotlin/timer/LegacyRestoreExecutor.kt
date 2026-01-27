package bruh.regionrestore.timer

import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Handles legacy (non-streaming) restore mode where all chunks are preloaded,
 * the restore is executed, and then tickets are released.
 */
@OptIn(ExperimentalAtomicApi::class)
class LegacyRestoreExecutor(
    private val context: RestoreExecutionContext
) {

    /**
     * Execute a restore using the legacy mode.
     * Preloads all chunks, performs the restore, then releases tickets.
     *
     * @param job The restore job to execute
     * @return Time taken in milliseconds
     */
    suspend fun execute(job: RestoreJob, audienceScope: AudienceScope): Long {
        val start = System.currentTimeMillis()
        var chunkHandles: List<ChunkTicketManager.ChunkTicketHandle> = emptyList()

        try {
            chunkHandles = context.chunkTicketManager.preloadChunks(job)

            withContext(context.getAsyncDispatcher(context.restoreConfig.asyncRestore ?: false)) {
                context.nmsAdapter.restoreTemplate(
                    job.world,
                    job.template,
                    job.targetChunkX,
                    job.targetChunkZ,
                    context.plugin,
                    job.updateLight
                )
            }
        } finally {
            context.chunkTicketManager.releaseChunkTickets(chunkHandles)
        }

        return System.currentTimeMillis() - start
    }
}
