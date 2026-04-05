package bruh.regionrestore.nms

import com.github.luben.zstd.Zstd
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class CompressedChunkData(
    val originalByteSize: Int,
    val smallBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressedChunkData) return false
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

object NmsAdapterCommon {
    val RESTORE_POOL: ExecutorService =
        Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors() - 3).coerceAtLeast(2))

    val NMS_LOGGER = LoggerFactory.getLogger("RegionRestore NMS")
    val SERIAL_LOGGER = LoggerFactory.getLogger("RegionRestore Serial")

    val IS_FOLIA: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun serializeChunkDataToByteBuf(
        chunkData: Map<Pair<Int, Int>, ByteBuf>,
        executor: ExecutorService = RESTORE_POOL
    ): ByteBuf {
        val chunks = chunkData.entries.chunked(600)
        val bigBuffer = Unpooled.directBuffer(chunkData.values.size * 2000)

        bigBuffer.writeInt(chunks.size)
        SERIAL_LOGGER.info("Serializing ${chunks.size} chunksets (${chunkData.size} chunks)")

        val queue = ArrayDeque<CompletableFuture<CompressedChunkData>>()
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
                CompressedChunkData(originalByteSize, smallBytes)
            }, executor))
        }

        for (chunk in queue) {
            val data = chunk.join()

            bigBuffer.writeInt(data.originalByteSize)
            bigBuffer.writeInt(data.smallBytes.size)
            SERIAL_LOGGER.info("Compressed to ${data.smallBytes.size} bytes from ${data.originalByteSize} bytes.")
            bigBuffer.writeBytes(data.smallBytes)
        }

        return bigBuffer
    }

    fun deserializeChunkDataFromByteBuf(
        buffer: ByteBuf,
        offheap: Boolean,
        executor: ExecutorService = RESTORE_POOL
    ): Map<Pair<Int, Int>, ByteBuf> {
        val chunks = buffer.readInt()
        val result = ConcurrentHashMap<Pair<Int, Int>, ByteBuf>()
        SERIAL_LOGGER.info("Deserializing $chunks chunksets")

        val tasks = ArrayDeque<CompletableFuture<*>>()

        repeat(chunks) {
            val originalByteSize = buffer.readInt()
            val chunkSize = buffer.readInt()
            val chunkBytes = Unpooled.directBuffer(chunkSize)
            buffer.readBytes(chunkBytes, chunkSize)

            tasks.add(CompletableFuture.runAsync({
                val count = if (offheap) {
                    val buf = Unpooled.directBuffer(originalByteSize)
                    Zstd.decompressUnsafe(
                        buf.memoryAddress(),
                        originalByteSize.toLong(),
                        chunkBytes.memoryAddress(),
                        chunkSize.toLong()
                    )
                    buf.writerIndex(originalByteSize)

                    val size = buf.readInt()

                    repeat(size) {
                        val x = buf.readInt()
                        val z = buf.readInt()
                        val dataLength = buf.readInt()
                        val dataBytes = Unpooled.directBuffer(dataLength)
                        buf.readBytes(dataBytes, dataLength)
                        result[Pair(x, z)] = dataBytes
                    }
                    buf.release()

                    size
                } else {
                    val decompressed = Zstd.decompress(chunkBytes.array(), originalByteSize)
                    val buf = Unpooled.wrappedBuffer(decompressed)

                    val size = buf.readInt()

                    repeat(size) {
                        val x = buf.readInt()
                        val z = buf.readInt()
                        val dataLength = buf.readInt()
                        val dataBytes = Unpooled.buffer(dataLength)
                        buf.readBytes(dataBytes, dataLength)
                        result[Pair(x, z)] = dataBytes
                    }

                    size
                }
                chunkBytes.release()

                SERIAL_LOGGER.info("Loaded a $count-long chunkset.")
            }, executor))
        }

        CompletableFuture.allOf(*tasks.toTypedArray()).join()

        SERIAL_LOGGER.info("Loaded chunk data for ${result.size} chunks.")

        val returnSyncMap = mutableMapOf<Pair<Int, Int>, ByteBuf>()
        result.forEach { (key, value) ->
            returnSyncMap[key] = value
        }
        return returnSyncMap
    }
}
