package bruh.regionrestore.nms.v1_21_4

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import java.lang.reflect.Constructor
import java.util.*

class PaperNmsAdapter1_21_4 : PaperNmsAdapter {
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
    }

    override val minecraftVersion = "1.21.4"
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

    override fun deserializeChunkDataFromByteBuf(buffer: ByteBuf, offheap: Boolean): Map<Pair<Int, Int>, ByteBuf> {
        val size = buffer.readInt()
        val result = mutableMapOf<Pair<Int, Int>, ByteBuf>()

        repeat(size) {
            val x = buffer.readInt()
            val z = buffer.readInt()
            val dataLength = buffer.readInt()
            val dataBytes = if (offheap) Unpooled.directBuffer(dataLength) else Unpooled.buffer(dataLength)
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
            createdAt = java.time.Instant.now(),
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

        for (chunkDatum in chunkData) {
            val chunkPos = chunkDatum.key
            val chunkData = chunkDatum.value
            val fBuffer = FriendlyByteBuf(chunkData)

            val chunk = world.getChunkAt(
                chunkPos.first - template.minChunkX + originChunkX,
                chunkPos.second - template.minChunkZ + originChunkZ
            )
            chunk.load()
            val chonkHandle = (chunk as CraftChunk).getHandle(ChunkStatus.FULL)

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
                blockEnt.level = world.handle

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

            for (player in chunk.playersSeeingChunk) {
                (player as CraftPlayer).handle.connection.send(
                    ClientboundLevelChunkWithLightPacket(
                        level.getChunk(chunk.x, chunk.z), world.handle.lightEngine, BitSet(), BitSet()
                    )
                )
            }
        }
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
                    section.write(fBuffer, null, 0)
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
}
