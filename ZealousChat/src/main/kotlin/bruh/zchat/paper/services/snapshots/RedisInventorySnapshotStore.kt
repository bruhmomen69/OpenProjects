package bruh.zchat.paper.services.snapshots

import bruh.zchat.paper.config.RedisConnectionConfig
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Redis-backed snapshot store. Uses TTL for expiry; no cleanup.
 */
class RedisInventorySnapshotStore(
    private val redisConfig: RedisConnectionConfig,
    private val keyPrefix: String
) : InventorySnapshotStore {
    private val logger = LoggerFactory.getLogger(RedisInventorySnapshotStore::class.java)
    private val client: RedisClient
    private val connection: StatefulRedisConnection<String, ByteArray>

    init {
        client = RedisClient.create(redisConfig.uri).apply {
            options = ClientOptions.builder()
                .autoReconnect(true)
                .timeoutOptions(
                    TimeoutOptions.builder()
                        .fixedTimeout(redisConfig.connectTimeoutMillis.milliseconds.toJavaDuration())
                        .build()
                )
                .build()
        }
        connection = client.connect(ByteArrayCodec)
        redisConfig.clientName?.let { name ->
            runCatching { connection.sync().clientSetname(name) }
        }
    }

    private fun key(serverInstanceId: String, snapshotId: String): String =
        "$keyPrefix:$serverInstanceId:$snapshotId"

    override suspend fun save(
        snapshotId: String,
        serverInstanceId: String,
        createdAtEpochMs: Long,
        expiresAtEpochMs: Long,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val ttlMillis = expiresAtEpochMs - System.currentTimeMillis()
        if (ttlMillis <= 0) return@withContext false
        runCatching {
            connection.sync().psetex(key(serverInstanceId, snapshotId), ttlMillis, data)
        }.onFailure { e ->
            logger.error("Failed to save inventory snapshot $snapshotId to Redis", e)
        }.isSuccess
    }

    override suspend fun load(snapshotId: String, serverInstanceId: String): InventorySnapshotStore.StoredSnapshot? =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { connection.sync().get(key(serverInstanceId, snapshotId)) }.getOrNull()
                ?: return@withContext null
            // Expiry is enforced by Redis TTL; we approximate expiresAt using TTL.
            val pttl = runCatching { connection.sync().pttl(key(serverInstanceId, snapshotId)) }.getOrNull() ?: -1
            val now = System.currentTimeMillis()
            val expiresAt = if (pttl > 0) now + pttl else now
            InventorySnapshotStore.StoredSnapshot(createdAtEpochMs = now, expiresAtEpochMs = expiresAt, data = bytes)
        }

    override suspend fun delete(snapshotId: String, serverInstanceId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { connection.sync().del(key(serverInstanceId, snapshotId)) }
            .onFailure { e -> logger.debug("Failed to delete Redis snapshot $snapshotId", e) }
            .map { it > 0 }
            .getOrDefault(false)
    }

    override suspend fun cleanupExpired(nowEpochMs: Long): Int {
        // Redis handles expiration; nothing to do.
        return 0
    }

    override fun close() {
        runCatching { connection.close() }
        runCatching { client.shutdown() }
    }
}

/**
 * Simple ByteArray codec for Lettuce String keys.
 */
private object ByteArrayCodec : io.lettuce.core.codec.RedisCodec<String, ByteArray> {
    private val stringCodec = io.lettuce.core.codec.StringCodec.UTF8

    override fun decodeKey(bytes: ByteBuffer): String = stringCodec.decodeKey(bytes)
    override fun encodeKey(key: String): ByteBuffer = stringCodec.encodeKey(key)

    override fun decodeValue(bytes: ByteBuffer): ByteArray {
        val dup = bytes.duplicate()
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    override fun encodeValue(value: ByteArray): ByteBuffer = ByteBuffer.wrap(value)
}
