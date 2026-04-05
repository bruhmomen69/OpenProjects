package bruh.regionrestore.nms.v26_1

import bruh.regionrestore.nms.ChunkByChunkRestore
import bruh.regionrestore.nms.NmsAdapterCommon
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ThreadedLevelLightEngine
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
class PaperNmsAdapter26_1 : PaperNmsAdapter, ChunkByChunkRestore {
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

        private val STATE_VISIBLE_FIELD: Field = SWMRNibbleArray::class.java.getDeclaredField("stateVisible")

        init {
            STATE_VISIBLE_FIELD.isAccessible = true
        }
    }

    override val minecraftVersion = "1.21.11"
    override val supportsAsync = true

    override suspend fun restoreSingleChunk(
        world: World,
        template: RegionTemplate,
        templateChunkX: Int,
        templateChunkZ: Int,
        targetChunkX: Int,
        targetChunkZ: Int,
        plugin: JavaPlugin,
        updateLight: Boolean
    ): Job {
        val level = (world as CraftWorld).handle
        val chunkDataKey = Pair(templateChunkX, templateChunkZ)
        val chunkData =
            template.chunkData[chunkDataKey] ?: throw IllegalStateException("Chunk data not found for $chunkDataKey")
        val fBuffer = FriendlyByteBuf(chunkData)

        val movedChunkPos = ChunkPos(
            templateChunkX - template.minChunkX + targetChunkX,
            templateChunkZ - template.minChunkZ + targetChunkZ
        )

        val chonkHandle = if (NmsAdapterCommon.IS_FOLIA) {
            level.getChunkIfLoaded(movedChunkPos.x, movedChunkPos.z)?.let { return@let it }
                ?: world.getChunkAtAsync(movedChunkPos.x, movedChunkPos.z)
                    .thenApply { (it as CraftChunk).getHandle(ChunkStatus.FULL) as LevelChunk }.join()
        } else {
            level.getChunk(movedChunkPos.x, movedChunkPos.z, ChunkStatus.FULL, true)!! as LevelChunk
        }

        val chunk = CraftChunk(chonkHandle)

        val relightFuture = CompletableFuture<Unit>()

        val restore = restoreChunk(
            template,
            targetChunkX,
            targetChunkZ,
            level,
            chunkData,
            chunk,
            fBuffer,
            chonkHandle,
            updateLight
        )

        if (!updateLight) relightFuture.complete(Unit)
        else if (level.lightEngine is ThreadedLevelLightEngine) level.lightEngine.`starlight$serverRelightChunks`(
            mutableListOf(movedChunkPos),
            {},
            { relightFuture.complete(Unit) }
        )
        else {
            level.lightEngine.propagateLightSources(movedChunkPos)
            relightFuture.complete(Unit)
        }

        val beMap = chonkHandle.blockEntities
        val absBlockXOffset = (targetChunkX - template.minChunkX).shl(4)
        val absBlockZOffset = (targetChunkZ - template.minChunkZ).shl(4)

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

        val `j*b` = Job()

        relightFuture.await()
        val players = level.getChunkSource().chunkMap.getPlayers(chonkHandle.pos, false)
        if (players.isNotEmpty()) {
            val bitset = BitSet()
            for (layer in 0..level.lightEngine.lightSectionCount) {
                bitset.set(layer, true)
            }
            val packet = ClientboundLevelChunkWithLightPacket(
                chonkHandle, level.lightEngine, bitset, bitset
            )

            val remaining = AtomicInt(players.size)
            for (player in players) {
                player.connection.send(packet) {
                    if (remaining.decrementAndFetch() == 0) {
                        `j*b`.complete()
                    }
                }
            }
        } else {
            `j*b`.complete()
        }

        return `j*b`
    }

    override fun getRestoreExecutor(): ExecutorService = NmsAdapterCommon.RESTORE_POOL

    override fun serializeChunkDataToByteBuf(chunkData: Map<Pair<Int, Int>, ByteBuf>): ByteBuf =
        NmsAdapterCommon.serializeChunkDataToByteBuf(chunkData)

    override fun deserializeChunkDataFromByteBuf(buffer: ByteBuf, offheap: Boolean): Map<Pair<Int, Int>, ByteBuf> =
        NmsAdapterCommon.deserializeChunkDataFromByteBuf(buffer, offheap)

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

            if (NmsAdapterCommon.IS_FOLIA) {
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
                val restore =
                    restoreChunk(
                        template,
                        originChunkX,
                        originChunkZ,
                        level,
                        chunkData,
                        chunk,
                        fBuffer,
                        chonkHandle,
                        updateLight
                    )
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
                return@supplyAsync restore
            }, NmsAdapterCommon.RESTORE_POOL))
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

            val bitset = BitSet()
            for (layer in 0..level.lightEngine.lightSectionCount) {
                bitset.set(layer, true)
            }

            plugin.launch(plugin.regionDispatcher(world, chunk.x, chunk.z)) {
                val players = level.getChunkSource().chunkMap.getPlayers(chonkHandle.pos, false)

                if (players.isNotEmpty()) {
                    relightCompletions[chonkHandle.pos]?.whenComplete { _, _ ->
                        val packet = ClientboundLevelChunkWithLightPacket(
                            chonkHandle, level.lightEngine, bitset, bitset
                        )
                        for (player in players) {
                            player.connection.send(packet)
                        }
                    } ?: run {
                        val packet = ClientboundLevelChunkWithLightPacket(
                            chonkHandle, level.lightEngine, bitset, bitset
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
        chonkHandle: LevelChunk,
        doFullRelight: Boolean
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


        if (!doFullRelight) {
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
        } else {
            val skyNibbleSize = fBuffer.readShort()
            for (i in 0 until skyNibbleSize) {
                val old = fBuffer.readShort().toInt()
                if (old != 0 && old != 1) {
                    val bytes = fBuffer.readInt()
                    fBuffer.readerIndex(fBuffer.readerIndex() + bytes)
                }
            }
            val blockNibbleSize = fBuffer.readShort()
            for (i in 0 until blockNibbleSize) {
                val old = fBuffer.readShort().toInt()
                if (old != 0 && old != 1) {
                    val bytes = fBuffer.readInt()
                    fBuffer.readerIndex(fBuffer.readerIndex() + bytes)
                }
            }
        }

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

        val heightmaps = fBuffer.readShort().toInt()
        for (i in 0 until heightmaps) {
            val heightmapName = fBuffer.readUtf(8192)
            val heightmap = Heightmap.Types.valueOf(heightmapName)
            if (heightmap.sendToClient()) {
                val heightmapData = fBuffer.readLongArray()
                chonkHandle.setHeightmap(heightmap, heightmapData)
            } else {
                fBuffer.readerIndex(fBuffer.readerIndex() + 8)
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

                val heightmaps = nmsChunk.getHeightmaps()
                    .filter { entry1 -> entry1.key.sendToClient() }
                    .associate { entry -> entry.key to entry.value.rawData.clone() }

                fBuffer.writeShort(heightmaps.size)
                for (heightmap in heightmaps) {
                    fBuffer.writeUtf(heightmap.key.name)
                    fBuffer.writeLongArray(heightmap.value)
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
        val chonkHandle: LevelChunk
    )
}
