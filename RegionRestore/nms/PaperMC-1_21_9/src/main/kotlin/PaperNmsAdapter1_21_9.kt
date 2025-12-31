package bruh.regionrestore.nms.v1_21_9

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ThreadedLevelLightEngine
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import java.lang.reflect.Constructor
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.collections.iterator

class PaperNmsAdapter1_21_9 : PaperNmsAdapter {
    companion object {
        private val CONSTRUCTOR_CACHE = InMemoryKache<String, Constructor<BaseContainerBlockEntity>>(maxSize = 100) {
            strategy = KacheStrategy.LRU
        }

        private suspend fun getConstructor(className: String): Constructor<BaseContainerBlockEntity>? {
            return CONSTRUCTOR_CACHE.getOrPut(className) {
                for (constructor in Class.forName(className).constructors) {
                    for (parameter in constructor.parameters) {
                        if (parameter.type == BlockPos::class.java ||
                            parameter.type == BlockState::class.java
                        ) {
                            return@getOrPut constructor as Constructor<BaseContainerBlockEntity>
                        }
                    }
                }
                return@getOrPut null
            }
        }

        private val RESTORE_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    }

    override val minecraftVersion = "1.21.9"
    override val supportsAsync = false

    override fun serializeChunkDataToByteBuf(chunkData: Map<Pair<Int, Int>, ByteBuf>): ByteBuf {
        val buffer = Unpooled.buffer()

        buffer.writeInt(chunkData.size)

        for ((pos, byteBuf) in chunkData) {
            buffer.writeInt(pos.first)
            buffer.writeInt(pos.second)
            val xLen = byteBuf.readableBytes()
            val dataBytes = Unpooled.directBuffer(xLen)
            byteBuf.readBytes(dataBytes, xLen)
            buffer.writeInt(xLen)
            buffer.writeBytes(dataBytes)
            dataBytes.release()
        }

        return buffer
    }

    override fun deserializeChunkDataFromByteBuf(buffer: ByteBuf): Map<Pair<Int, Int>, ByteBuf> {
        val size = buffer.readInt()
        val result = mutableMapOf<Pair<Int, Int>, ByteBuf>()

        repeat(size) {
            val x = buffer.readInt()
            val z = buffer.readInt()
            val dataLength = buffer.readInt()
            val dataBytes = Unpooled.directBuffer(dataLength)
            buffer.readBytes(dataBytes, dataLength)
            result[Pair(x, z)] = dataBytes
        }

        return result
    }

    override suspend fun serializeArea(
        world: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int
    ): RegionTemplate {
        val chunks = serializeChunks(minChunkX, maxChunkX, minChunkZ, maxChunkZ, world)
        return RegionTemplate(
            name = "",
            description = "",
            createdAt = Instant.now(),
            sourceWorld = world.uid,
            minChunkX = minChunkX,
            minChunkZ = minChunkZ,
            sizeXChunks = maxChunkX - minChunkX + 1,
            sizeZChunks = maxChunkZ - minChunkZ + 1,
            chunkData = chunks
        )
    }

    override suspend fun restoreTemplate(
        world: World,
        template: RegionTemplate,
        originChunkX: Int,
        originChunkZ: Int,
        plugin: JavaPlugin,
        updateLight: Boolean
    ) {
        val level = (world as CraftWorld).handle
        val chunkData = template.chunkData

        val completions = mutableListOf<CompletableFuture<ChunkRestoreData>>()
        val relightCompletions = mutableMapOf<ChunkPos, CompletableFuture<Unit>>()

        for (chunkDatum in chunkData) {
            val chunkPos = chunkDatum.key
            val chunkData = chunkDatum.value
            val fBuffer = FriendlyByteBuf(chunkData)

            val chunk = world.getChunkAt(
                chunkPos.first - template.minChunkX + originChunkX,
                chunkPos.second - template.minChunkZ + originChunkZ
            )
            val chonkHandle = (chunk as CraftChunk).getHandle(ChunkStatus.SURFACE)
            val movedChunkPos = ChunkPos(chunk.x, chunk.z)

            val relightFuture = CompletableFuture<Unit>()
            relightCompletions[movedChunkPos] = relightFuture

            completions.add(CompletableFuture.supplyAsync({
                // Restore this chunk
                val restore =
                    restoreChunk(template, originChunkX, originChunkZ, level, chunkData, chunk, fBuffer, chonkHandle)
                // Re-light this chunk
                if (!updateLight) relightFuture.complete(Unit)
                else if (level.lightEngine is ThreadedLevelLightEngine) level.lightEngine.`starlight$serverRelightChunks`(
                    mutableListOf(movedChunkPos),
                    {},
                    {
                        relightFuture.complete(Unit)
                    })
                else {
                    level.lightEngine.propagateLightSources(movedChunkPos)
                    relightFuture.complete(Unit)
                }
                // Return
                return@supplyAsync restore
            }, RESTORE_POOL))
        }

        CompletableFuture.allOf(*(completions as List<CompletableFuture<*>>).toTypedArray()).join()

        for (future in completions) {
            val data = future.join()
            val fBuffer = data.fBuffer
            val chunk = data.chunk
            val chonkHandle = data.chonkHandle
            val beMap = chonkHandle.blockEntities

            val absBlockXOffset = (originChunkX - template.minChunkX).shl(4)
            val absBlockZOffset = (originChunkZ - template.minChunkZ).shl(4)

            val invTileCount = fBuffer.readShort()
            for (i in 0 until invTileCount) {
                val classNameByteSize = fBuffer.readShort().toInt()
                val classNameBytes = ByteArray(classNameByteSize)
                fBuffer.readBytes(classNameBytes)
                val className = String(classNameBytes, Charsets.UTF_8)

                val blockPos = BlockPos(
                    fBuffer.readInt() + absBlockXOffset,
                    fBuffer.readInt(),
                    fBuffer.readInt() + absBlockZOffset
                )

                val blockEnt =
                    (getConstructor(className) ?: throw IllegalStateException("Map has unknown item type $className"))
                        .newInstance(blockPos, chonkHandle.getBlockState(blockPos))
                blockEnt.setLevel(level)

                val itemCount = fBuffer.readShort()
                for (j in 0 until itemCount) {
                    val invPos = fBuffer.readShort()
                    val invByteLen = fBuffer.readShort()
                    val invBytes = ByteArray(invByteLen.toInt())
                    fBuffer.readBytes(invBytes)
                    val iStack = ItemStack.deserializeBytes(invBytes)
                    blockEnt.setItem(invPos.toInt(), (iStack as CraftItemStack).handle)
                }

                beMap[blockPos] = blockEnt
                level.setBlockEntity(blockEnt)
            }

            val levelChunk = level.getChunk(chunk.x, chunk.z)
            val players = level.getChunkSource().chunkMap.getPlayers(levelChunk.pos, false)
            if (players.isNotEmpty()) {
                // Ensure light has updated before sending
                relightCompletions[levelChunk.pos]?.whenCompleteAsync(
                    { _, _ ->
                        val packet = ClientboundLevelChunkWithLightPacket(
                            levelChunk, level.lightEngine, BitSet(), BitSet(), true
                        )
                        for (player in players) {
                            player.connection.send(packet)
                        }
                    }, RESTORE_POOL
                ) ?: run {
                    val packet = ClientboundLevelChunkWithLightPacket(
                        levelChunk, level.lightEngine, BitSet(), BitSet(), true
                    )
                    for (player in players) {
                        player.connection.send(packet)
                    }
                }
            }
        }
    }

    fun restoreChunk(
        template: RegionTemplate,
        originChunkX: Int,
        originChunkZ: Int,
        level: ServerLevel,
        chunkData: ByteBuf,
        chunk: Chunk,
        fBuffer: FriendlyByteBuf,
        chonkHandle: ChunkAccess
    ): ChunkRestoreData {
        chunkData.readerIndex(0)

        val sections = fBuffer.readShort()

        for (i in 0 until sections) {
            val section = chonkHandle.sections[i]
            section.read(fBuffer)
        }

        val beMap = chonkHandle.blockEntities
        for (mutableEntry in HashMap(beMap)) {
            val blockEntity = mutableEntry.value
            if (blockEntity is BaseContainerBlockEntity) {
                beMap.remove(mutableEntry.key)
            }
        }

        return ChunkRestoreData(template, originChunkX, originChunkZ, level, chunkData, chunk, fBuffer, chonkHandle)
    }

    private fun serializeChunks(
        minXChunk: Int,
        maxXChunk: Int,
        minZChunk: Int,
        maxZChunk: Int,
        world: World
    ): HashMap<Pair<Int, Int>, ByteBuf> {
        val chunks = HashMap<Pair<Int, Int>, ByteBuf>()

        for (x in minXChunk..maxXChunk) {
            for (z in minZChunk..maxZChunk) {
                val chunk = world.getChunkAt(x, z)
                chunk.load()

                val buffer = Unpooled.directBuffer(1024)
                val fBuffer = FriendlyByteBuf(buffer)

                val sections = (chunk as CraftChunk).getHandle(ChunkStatus.FULL).sections.map { it.copy() }
                fBuffer.writeShort(sections.size)
                for (section in sections) {
                    section.write(fBuffer)
                }

                val invTiles =
                    chunk.getHandle(ChunkStatus.FULL).blockEntities.filter { it.value is BaseContainerBlockEntity }
                fBuffer.writeShort(invTiles.size)
                for (invTile in invTiles) {
                    val tile = invTile.value as BaseContainerBlockEntity
                    val contents = tile.contents
                        .mapIndexed { idx, stack -> Pair(stack, idx) }
                        .filter { !it.first.isEmpty }
                        .map { Pair(it.first.asBukkitMirror().serializeAsBytes(), it.second) }
                    val size = contents.size

                    val classNameStr = tile.javaClass.name.encodeToByteArray()
                    fBuffer.writeShort(classNameStr.size)
                    fBuffer.writeBytes(classNameStr)

                    fBuffer.writeInt(invTile.key.x)
                    fBuffer.writeInt(invTile.key.y)
                    fBuffer.writeInt(invTile.key.z)

                    fBuffer.writeShort(size)
                    for (item in contents) {
                        fBuffer.writeShort(item.second)
                        fBuffer.writeShort(item.first.size)
                        fBuffer.writeBytes(item.first)
                    }
                }

                chunks[Pair(x, z)] = buffer
            }
        }
        return chunks
    }

    data class ChunkRestoreData(
        val template: RegionTemplate,
        val originChunkX: Int,
        val originChunkZ: Int,
        val level: ServerLevel,
        val chunkData: ByteBuf,
        val chunk: Chunk,
        val fBuffer: FriendlyByteBuf,
        val chonkHandle: ChunkAccess
    )
}
