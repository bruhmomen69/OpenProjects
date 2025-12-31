package bruh.regionrestore.nms

import io.netty.buffer.ByteBuf
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.*

data class RegionTemplate(
    val name: String,
    val description: String,
    val createdAt: Instant,
    val sourceWorld: UUID,
    val minChunkX: Int,
    val minChunkZ: Int,
    val sizeXChunks: Int,
    val sizeZChunks: Int,
    val chunkData: Map<Pair<Int, Int>, ByteBuf>
) {
    /**
     * Release all ByteBufs in the chunkData map to prevent memory leaks.
     * Should be called when the template is no longer needed.
     */
    fun release() {
        for (byteBuf in chunkData.values) {
            if (byteBuf.refCnt() == 0)
                continue
            
            byteBuf.release()
        }
    }
}

data class RegionTemplateVersion(
    val templateName: String,
    val versionId: Int,
    val createdAt: Instant,
    val minecraftVersion: String,
    val data: RegionTemplate
) {
    /**
     * Release all ByteBufs in the underlying RegionTemplate to prevent memory leaks.
     * Should be called when the template version is no longer needed.
     */
    fun release() {
        data.release()
    }
}

interface PaperNmsAdapter {
    val minecraftVersion: String

    val supportsAsync: Boolean

    suspend fun serializeArea(
        world: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int
    ): RegionTemplate

    suspend fun restoreTemplate(
        world: World,
        template: RegionTemplate,
        originChunkX: Int,
        originChunkZ: Int,
        plugin: JavaPlugin,
        updateLight: Boolean = false
    )

    fun serializeChunkDataToByteBuf(chunkData: Map<Pair<Int, Int>, ByteBuf>): ByteBuf

    fun deserializeChunkDataFromByteBuf(buffer: ByteBuf): Map<Pair<Int, Int>, ByteBuf>
}
