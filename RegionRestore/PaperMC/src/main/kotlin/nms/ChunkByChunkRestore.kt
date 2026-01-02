package bruh.regionrestore.nms

import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ExecutorService

/**
 * Interface for NMS adapters that support chunk-by-chunk restoration.
 * This allows for streaming restores where chunks are restored immediately
 * after loading, rather than waiting for all chunks to load first.
 */
interface ChunkByChunkRestore {
    /**
     * Restore a single chunk from a template.
     *
     * @param world The world to restore into
     * @param template The region template containing chunk data
     * @param templateChunkX The chunk X coordinate in the template
     * @param templateChunkZ The chunk Z coordinate in the template
     * @param targetChunkX The target chunk X coordinate in the world
     * @param targetChunkZ The target chunk Z coordinate in the world
     * @param plugin The plugin instance
     * @param updateLight Whether to update lighting after restoration
     */
    suspend fun restoreSingleChunk(
        world: World,
        template: RegionTemplate,
        templateChunkX: Int,
        templateChunkZ: Int,
        targetChunkX: Int,
        targetChunkZ: Int,
        plugin: JavaPlugin,
        updateLight: Boolean
    )

    /**
     * Get the executor service used for chunk restoration operations.
     * This allows the scheduler to properly dispatch async restoration work.
     *
     * @return The executor service for restoration operations
     */
    fun getRestoreExecutor(): ExecutorService
}
