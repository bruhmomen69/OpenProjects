package bruh.regionrestore.nms.v1_21_11

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray
import com.github.luben.zstd.Zstd
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
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
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class PaperNmsAdapter1_21_11 : PaperNmsAdapter {
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

        private fun isFolia(): Boolean {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                return true
            } catch (e: ClassNotFoundException) {
                return false
            }
        }

        private val STATE_VISIBLE_FIELD: Field = SWMRNibbleArray::class.java.getDeclaredField("stateVisible")
        private val IS_FOLIA: Boolean by lazy { isFolia() }

        init {
            STATE_VISIBLE_FIELD.isAccessible = true
        }

        private val RESTORE_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        private val logger = LoggerFactory.getLogger("RegionRestore NMS")
    }

    override val minecraftVersion = "1.21.11"
    override val supportsAsync = true

    override fun serializeChunkDataToByteBuf(chunkData: Map<Pair<Int, Int>, ByteBuf>): ByteBuf {
        val chunks = chunkData.entries.chunked(600)
        val bigBuffer = Unpooled.directBuffer(chunkData.values.size * 2000)

        data class SubData(
            val originalByteSize: Int,
            val smallBytes: ByteArray
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is SubData) return false

                if (originalByteSize != other.originalByteSize) return false
                if (!smallBytes.contentEquals(other.smallBytes)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = originalByteSize
                result = 31 * result + smallBytes.contentHashCode()
                return result
            }
        }

        bigBuffer.writeInt(chunks.size)
        logger.info("Serializing ${chunks.size} chunksets (${chunkData.size} chunks)")

        val queue = ArrayDeque<CompletableFuture<SubData>>()
        for (chunk in chunks) {
            queue.add(CompletableFuture.supplyAsync({
                val buffer = Unpooled.buffer(chunk.sumOf { it.value.readableBytes() + 4 + 4 + 4 + 4 } + 64)
                buffer.writeInt(chunk.size)
                for ((pos, byteBuf) in chunk) {
                    buffer.writeInt(pos.first)
                    buffer.writeInt(pos.second)
                    val xLen = byteBuf.readableBytes()
                    val dataBytes = Unpooled.directBuffer(xLen)
                    byteBuf.readBytes(dataBytes, xLen)
                    buffer.writeInt(xLen)
                    buffer.writeBytes(dataBytes)
                    dataBytes.release()
                }
                val originalByteSize = buffer.writerIndex()
                val originalBytes = ByteArray(originalByteSize)
                buffer.readBytes(originalBytes)

                val smallBytes = Zstd.compress(originalBytes, 18)
                buffer.release()
                SubData(originalByteSize, smallBytes)
            }, RESTORE_POOL))
        }

        for (chunk in queue) {
            val data = chunk.join()

            bigBuffer.writeInt(data.originalByteSize)
            bigBuffer.writeInt(data.smallBytes.size)
            logger.info("Compressed to ${data.smallBytes.size} bytes from ${data.originalByteSize} bytes.")
            bigBuffer.writeBytes(data.smallBytes)
        }

        return bigBuffer
    }

    override fun deserializeChunkDataFromByteBuf(buffer: ByteBuf): Map<Pair<Int, Int>, ByteBuf> {
        val chunks = buffer.readInt()
        val result = ConcurrentHashMap<Pair<Int, Int>, ByteBuf>()
        logger.info("Deserializing $chunks chunksets")

        val tasks = ArrayDeque<CompletableFuture<*>>()

        repeat(chunks) {
            val originalByteSize = buffer.readInt()
            val chunkSize = buffer.readInt()
            val chunkBytes = Unpooled.directBuffer(chunkSize)
            buffer.readBytes(chunkBytes, chunkSize)

            tasks.add(CompletableFuture.runAsync({
                val buffer = Unpooled.directBuffer(originalByteSize)
                Zstd.decompressUnsafe(
                    buffer.memoryAddress(),
                    originalByteSize.toLong(),
                    chunkBytes.memoryAddress(),
                    chunkSize.toLong()
                )
                buffer.writerIndex(originalByteSize)

                val size = buffer.readInt()

                repeat(size) {
                    val x = buffer.readInt()
                    val z = buffer.readInt()
                    val dataLength = buffer.readInt()
                    val dataBytes = Unpooled.directBuffer(dataLength)
                    buffer.readBytes(dataBytes, dataLength)
                    result[Pair(x, z)] = dataBytes
                }
                buffer.release()
                chunkBytes.release()

                logger.info("Loaded a $size-long chunkset.")
            }, RESTORE_POOL))
        }

        CompletableFuture.allOf(*tasks.toTypedArray()).join()

        logger.info("Loaded chunk data for ${result.size} chunks.")

        val returnSyncMap = mutableMapOf<Pair<Int, Int>, ByteBuf>()
        result.forEach { (key, value) ->
            returnSyncMap[key] = value
        }
        return returnSyncMap
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

        val completions = ArrayList<CompletableFuture<ChunkRestoreData>>(chunkData.size)
        val relightCompletions = HashMap<ChunkPos, CompletableFuture<Unit>>(chunkData.size)
        val chunkHandleMap = HashMap<ChunkPos, CompletableFuture<LevelChunk>>(chunkData.size)

        for (chunkDatum in chunkData) {
            val chunkPos = chunkDatum.key

            val movedChunkPos = ChunkPos(
                chunkPos.first - template.minChunkX + originChunkX,
                chunkPos.second - template.minChunkZ + originChunkZ,
            )

            if (IS_FOLIA) {
                chunkHandleMap.put(
                    movedChunkPos,
                    level.getChunkIfLoaded(
                        movedChunkPos.x,
                        movedChunkPos.z
                    )?.let { CompletableFuture.completedFuture(it) } ?: world.getChunkAtAsync(
                        movedChunkPos.x,
                        movedChunkPos.z
                    ).thenApply { it ->
                        (it as CraftChunk).getHandle(ChunkStatus.FULL) as LevelChunk
                    }
                )
            } else {
                chunkHandleMap.put(
                    movedChunkPos, CompletableFuture.completedFuture(
                        level.getChunk(
                            movedChunkPos.x,
                            movedChunkPos.z,
                            ChunkStatus.FULL,
                            true
                        )!! as LevelChunk
                    )
                )
            }
        }

        for (chunkDatum in chunkData) {
            val chunkPos = chunkDatum.key
            val chunkData = chunkDatum.value
            val fBuffer = FriendlyByteBuf(chunkData)

            val movedChunkPos = ChunkPos(
                chunkPos.first - template.minChunkX + originChunkX,
                chunkPos.second - template.minChunkZ + originChunkZ,
            )

            val chonkHandle = chunkHandleMap[movedChunkPos]?.join() ?: (level.getChunk(
                movedChunkPos.x,
                movedChunkPos.z,
                ChunkStatus.FULL,
                true
            )!! as LevelChunk)

            val chunk = CraftChunk(chonkHandle)

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

            plugin.launch(plugin.regionDispatcher(world, chunk.x, chunk.z)) {
                val levelChunk = level.getChunk(chunk.x, chunk.z)
                val players = level.getChunkSource().chunkMap.getPlayers(levelChunk.pos, false)
                if (players.isNotEmpty()) {
                    // Ensure light has updated before sending
                    relightCompletions[levelChunk.pos]?.whenComplete(
                        { _, _ ->
                            val packet = ClientboundLevelChunkWithLightPacket(
                                levelChunk, level.lightEngine, BitSet(), BitSet(), true
                            )
                            for (player in players) {
                                player.connection.send(packet)
                            }
                        }
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
    }

    fun restoreChunk(
        template: RegionTemplate,
        originChunkX: Int,
        originChunkZ: Int,
        level: ServerLevel,
        chunkData: ByteBuf,
        chunk: Chunk,
        fBuffer: FriendlyByteBuf,
        chonkHandle: ChunkAccess,
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

        val skyNibbleSize = fBuffer.readShort()
        val skyNibbles = arrayOfNulls<SWMRNibbleArray>(skyNibbleSize.toInt())
        for (i in 0 until skyNibbleSize) {
            val old = fBuffer.readShort().toInt()
            if (old != 0 && old != 1) {
                val bytes = fBuffer.readInt()
                val array = ByteArray(bytes)
                fBuffer.readBytes(array)
                skyNibbles[i] = SWMRNibbleArray(array)
            } else {
                skyNibbles[i] = SWMRNibbleArray(null)
            }
        }
        val blockNibbleSize = fBuffer.readShort()
        val blockNibbles = arrayOfNulls<SWMRNibbleArray>(blockNibbleSize.toInt())
        for (i in 0 until blockNibbleSize) {
            val old = fBuffer.readShort().toInt()
            if (old != 0 && old != 1) {
                val bytes = fBuffer.readInt()
                val array = ByteArray(bytes)
                fBuffer.readBytes(array)
                blockNibbles[i] = SWMRNibbleArray(array)
            } else {
                blockNibbles[i] = SWMRNibbleArray(null)
            }
        }
        chonkHandle.`starlight$setSkyNibbles`(skyNibbles as Array<out SWMRNibbleArray>)
        chonkHandle.`starlight$setBlockNibbles`(blockNibbles as Array<out SWMRNibbleArray>)

        val skyEmptinessMap = BooleanArray(fBuffer.readInt())
        for (i in 0 until skyEmptinessMap.size) {
            skyEmptinessMap[i] = fBuffer.readBoolean()
        }
        chonkHandle.`starlight$setSkyEmptinessMap`(skyEmptinessMap)

        val blockEmptinessMap = BooleanArray(fBuffer.readInt())
        for (i in 0 until blockEmptinessMap.size) {
            blockEmptinessMap[i] = fBuffer.readBoolean()
        }
        chonkHandle.`starlight$setBlockEmptinessMap`(blockEmptinessMap)


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

                val nmsChunk = (chunk as CraftChunk).getHandle(ChunkStatus.FULL)

                val sections = nmsChunk.sections.map { it.copy() }
                fBuffer.writeShort(sections.size)
                for (section in sections) {
                    section.write(fBuffer)
                }

                val skyNibbles = nmsChunk.`starlight$getSkyNibbles`()
                val blockNibbles = nmsChunk.`starlight$getBlockNibbles`()

                fBuffer.writeShort(skyNibbles.size)
                for (array in skyNibbles) {
                    val old = STATE_VISIBLE_FIELD.get(array) as Int
                    fBuffer.writeShort(old)
                    if (old != 0 && old != 1) {
                        STATE_VISIBLE_FIELD.set(array, 2)
                        var bytes = array.saveState.data
                        if (bytes == null) bytes = ByteArray(SWMRNibbleArray.ARRAY_SIZE)
                        fBuffer.writeInt(bytes.size)
                        fBuffer.writeBytes(bytes)
                        if (old != 2) STATE_VISIBLE_FIELD.set(array, old)
                    }
                }

                fBuffer.writeShort(blockNibbles.size)
                for (array in blockNibbles) {
                    val old = STATE_VISIBLE_FIELD.get(array) as Int
                    fBuffer.writeShort(old)
                    if (old != 0 && old != 1) {
                        STATE_VISIBLE_FIELD.set(array, 2)
                        var bytes = array.saveState.data
                        if (bytes == null) bytes = ByteArray(SWMRNibbleArray.ARRAY_SIZE)
                        fBuffer.writeInt(bytes.size)
                        fBuffer.writeBytes(bytes)
                        if (old != 2) STATE_VISIBLE_FIELD.set(array, old)
                    }
                }

                val skyEmptinessMap = nmsChunk.`starlight$getSkyEmptinessMap`()
                val blockEmptinessMap = nmsChunk.`starlight$getBlockEmptinessMap`()

                fBuffer.writeInt(skyEmptinessMap.size)
                for (bool in skyEmptinessMap) {
                    fBuffer.writeBoolean(bool)
                }

                fBuffer.writeInt(blockEmptinessMap.size)
                for (bool in blockEmptinessMap) {
                    fBuffer.writeBoolean(bool)
                }

                val invTiles =
                    nmsChunk.blockEntities.filter { it.value is BaseContainerBlockEntity }
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
