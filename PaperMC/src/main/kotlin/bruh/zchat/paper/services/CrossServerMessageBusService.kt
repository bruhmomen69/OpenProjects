package bruh.zchat.paper.services

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.StorageConfig
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.database.DBPlayerQueries
import bruh.zchat.paper.database.DatabaseService
import bruh.zchat.paper.database.DatabaseType
import bruh.zchat.paper.database.PlayerDataManager
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class CrossServerMessageBusService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val dbPlayerQueries: DBPlayerQueries,
    private val playerDataManager: PlayerDataManager,
    private val privateMessageService: PrivateMessageService,
    private val socialSpyService: SocialSpyService,
    private val messageFormattingService: MessageFormattingService,
    private val serverInstanceId: String
) {
    private val logger = LoggerFactory.getLogger(CrossServerMessageBusService::class.java)
    private val json = Json { ignoreUnknownKeys = true }


    @Serializable
    data class MessagePayload(
        val processedMessage: String,
        val originalMessage: String? = null,
        val reason: String? = null // For failures
    )

    enum class MessageType {
        PM_DELIVER,
        PM_DELIVERY_FAILED
    }

    @Serializable
    private data class RedisEnvelope(
        val id: Long,
        val type: MessageType,
        val senderUuid: String,
        val senderName: String,
        val recipientUuid: String,
        val recipientName: String?,
        val payload: MessagePayload
    )

    private val backendConfig: StorageConfig = configManager.storage
    private val isRedisBackend = backendConfig.crossServerMessaging.backend.equals("redis", ignoreCase = true)

    // Redis state (only used when backend=redis)
    private var redisClient: RedisClient? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
    private var publishConnection: StatefulRedisConnection<String, String>? = null
    private val redisChannel: String by lazy {
        val redisCfg = backendConfig.crossServerMessaging.redis
        "${redisCfg.channelPrefix}:server:$serverInstanceId"
    }

    /**
     * Initialize backend resources (Redis only).
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (!isRedisBackend || !backendConfig.crossServerMessaging.enabled) return@withContext
        if (pubSubConnection != null && publishConnection != null) return@withContext

        val redisCfg = backendConfig.database.redis
        val client = RedisClient.create(redisCfg.uri).apply {
            options = ClientOptions.builder()
                .autoReconnect(true)
                .timeoutOptions(
                    TimeoutOptions.builder()
                        .fixedTimeout(redisCfg.connectTimeoutMillis.milliseconds.toJavaDuration())
                        .build()
                )
                .build()
        }

        val pubSub = client.connectPubSub()
        val publisher = client.connect()

        redisClient = client
        pubSubConnection = pubSub
        publishConnection = publisher

        // Optional client name
        redisCfg.clientName?.let { name ->
            runCatching { publisher.sync().clientSetname(name) }
        }

        // Listener to handle messages off the event loop
        pubSub.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                // Offload to coroutine to avoid blocking Lettuce event loop
                plugin.launch(Dispatchers.IO) {
                    handleRedisMessage(message)
                }
            }

            override fun subscribed(channel: String, count: Long) {
                logger.info("Subscribed to Redis channel: $channel")
            }

            override fun unsubscribed(channel: String, count: Long) {
                logger.info("Unsubscribed from Redis channel: $channel")
            }
        })

        runCatching {
            pubSub.sync().subscribe(redisChannel)
            logger.info("Redis message bus subscribed to $redisChannel")
        }.onFailure { ex ->
            logger.error("Failed to subscribe to Redis channel $redisChannel", ex)
        }
    }

    /**
     * Close backend resources (Redis only).
     */
    fun close() {
        runCatching { pubSubConnection?.close() }
        runCatching { publishConnection?.close() }
        runCatching { redisClient?.shutdown() }
        pubSubConnection = null
        publishConnection = null
        redisClient = null
    }

    private suspend fun handleRedisMessage(raw: String) {
        try {
            val envelope = json.decodeFromString<RedisEnvelope>(raw)
            val id = if (envelope.id > 0L) envelope.id else System.currentTimeMillis()
            val type = envelope.type
            val senderUuid = UUID.fromString(envelope.senderUuid)
            val recipientUuid = UUID.fromString(envelope.recipientUuid)
            processMessage(
                id = id,
                type = type,
                senderUuid = senderUuid,
                senderName = envelope.senderName,
                recipientUuid = recipientUuid,
                recipientName = envelope.recipientName,
                payload = envelope.payload
            )
        } catch (ex: Exception) {
            logger.error("Failed to process Redis message bus payload", ex)
        }
    }

    suspend fun pollMessages() = withContext(Dispatchers.IO) {
        val config = configManager.storage.crossServerMessaging
        if (!config.enabled || dbPlayerQueries.databaseType != DatabaseType.MYSQL || isRedisBackend) return@withContext

        try {
            // 1. Claim messages
            val claimedCount = dbPlayerQueries.claimMessages(serverInstanceId, serverInstanceId, config.pollBatchSize)

            if (claimedCount == 0) return@withContext

            // 2. Fetch claimed messages
            val claimed = dbPlayerQueries.getClaimedMessages(serverInstanceId)

            for (msg in claimed) {
                try {
                    val type = MessageType.valueOf(msg.type)
                    val payload = json.decodeFromString<MessagePayload>(msg.payloadJson)
                    processMessage(msg.id, type, msg.senderUuid, msg.senderName, msg.recipientUuid, msg.recipientName, payload)
                } catch (e: Exception) {
                    logger.error("Failed to process message bus item ${msg.id}", e)
                    updateMessageStatus(msg.id, "FAILED", e.message)
                }
            }

        } catch (e: Exception) {
            logger.error("Error polling message bus", e)
        }
    }

    private suspend fun processMessage(
        id: Long,
        type: MessageType,
        senderUuid: UUID,
        senderName: String,
        recipientUuid: UUID,
        recipientName: String?,
        payload: MessagePayload
    ) {
        when (type) {
            MessageType.PM_DELIVER -> {
                val recipient = Bukkit.getPlayer(recipientUuid)
                if (recipient != null && recipient.isOnline) {
                    // Respect recipient message toggle on delivery server (prevents bypass)
                    if (configManager.config.chatToggle.enableMessageToggle) {
                        val toggleState = playerDataManager.getToggleState(recipientUuid)
                        if (toggleState?.messagesDisabled == true) {
                            updateMessageStatus(id, "FAILED", "Recipient has messages disabled")
                            sendReverseNotification(
                                senderUuid,
                                senderName,
                                recipientUuid,
                                recipientName,
                                payload,
                                "RECIPIENT_MESSAGES_DISABLED"
                            )
                            return
                        }
                    }

                    // Deliver on main thread
                    withContext(plugin.entityDispatcher(recipient)) {
                        // Use raw processed message from sender to ensure consistent formatting
                        val finalMessage = messageFormattingService.formatMessage(
                            format = configManager.messages.privateMessages.recipientFormat,
                            player = recipient,
                            additionalPlaceholders = mapOf(
                                "sender" to senderName,
                                "message" to payload.processedMessage
                            )
                        )

                        recipient.sendMessage(finalMessage)

                        // Update last sender for /reply
                        privateMessageService.setLastSender(recipient.uniqueId, senderUuid)

                        // Social spy
                        socialSpyService.broadcastRemotePrivateMessage(senderName, recipient, payload.originalMessage ?: "")
                    }
                    updateMessageStatus(id, "DELIVERED")
                } else {
                    // Recipient not found locally - fail and notify sender
                    updateMessageStatus(id, "FAILED", "Recipient offline on target server")
                    sendReverseNotification(
                        senderUuid,
                        senderName,
                        recipientUuid,
                        recipientName,
                        payload,
                        "RECIPIENT_OFFLINE"
                    )
                }
            }
            MessageType.PM_DELIVERY_FAILED -> {
                val sender = Bukkit.getPlayer(senderUuid) // In this case, sender is the original sender we are notifying
                if (sender != null && sender.isOnline) {
                    withContext(plugin.entityDispatcher(sender)) {
                        val targetLabel = recipientName ?: recipientUuid.toString()
                        val messageKey = when (payload.reason) {
                            "RECIPIENT_MESSAGES_DISABLED" -> MessageKey.PRIVATE_MESSAGES_TARGET_MESSAGES_DISABLED
                            else -> MessageKey.PRIVATE_MESSAGES_DELIVERY_FAILED
                        }
                        sender.sendMessage(messageFormattingService.getConfigMessage(messageKey, sender, mapOf("player" to targetLabel)))
                    }
                    updateMessageStatus(id, "DELIVERED")
                } else {
                    // Sender also offline, just mark done
                    updateMessageStatus(id, "FAILED", "Original sender offline")
                }
            }
        }
    }

    private suspend fun sendReverseNotification(
        originalSenderUuid: UUID,
        originalSenderName: String,
        originalRecipientUuid: UUID,
        originalRecipientName: String?,
        originalPayload: MessagePayload,
        reason: String
    ) {
        val config = configManager.storage.crossServerMessaging
        val cutoff = Instant.now().minusSeconds(config.heartbeatTimeoutSeconds.toLong())

        // Find where sender is now (heartbeat-aware)
        val senderPresence = dbPlayerQueries.getSenderPresence(originalSenderUuid, cutoff)

        if (senderPresence != null) {
            val failurePayload = MessagePayload(
                processedMessage = originalPayload.processedMessage,
                originalMessage = originalPayload.originalMessage,
                reason = reason
            )

            val recipientLabel = originalRecipientName
                ?: dbPlayerQueries.getUsername(originalRecipientUuid)

            val recipientLabelFinal = recipientLabel ?: originalRecipientUuid.toString()

            if (isRedisBackend) {
                publishRedisMessage(
                    targetServerId = senderPresence,
                    type = MessageType.PM_DELIVERY_FAILED,
                    senderUuid = originalSenderUuid,
                    senderName = originalSenderName,
                    recipientUuid = originalRecipientUuid,
                    recipientName = recipientLabelFinal,
                    payload = failurePayload
                )
            } else {
                dbPlayerQueries.insertMessageBus(
                    senderPresence,
                    MessageType.PM_DELIVERY_FAILED.name,
                    originalSenderUuid, // We send "to" the sender
                    originalSenderName,
                    originalRecipientUuid, // "From" the recipient (effectively)
                    recipientLabelFinal,
                    json.encodeToString(failurePayload)
                )
            }
        }
    }

    private suspend fun updateMessageStatus(id: Long, status: String, error: String? = null) {
        if (isRedisBackend) return
        dbPlayerQueries.updateMessageStatus(id, status, error)
    }

    /**
     * Allocates a unique id for Redis-published messages.
     */
    private fun allocateRedisMessageId(publisher: StatefulRedisConnection<String, String>): Long {
        val key = "${backendConfig.crossServerMessaging.redis.channelPrefix}:ids"
        return runCatching { publisher.sync().incr(key) }
            .getOrElse { System.currentTimeMillis() }
    }

    suspend fun sendCrossServerMessage(
        senderUuid: UUID,
        senderName: String,
        recipientUuid: UUID,
        recipientName: String,
        targetServerId: String,
        processedMessage: String,
        originalMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRedisBackend) {
            return@withContext publishRedisMessage(
                targetServerId = targetServerId,
                type = MessageType.PM_DELIVER,
                senderUuid = senderUuid,
                senderName = senderName,
                recipientUuid = recipientUuid,
                recipientName = recipientName,
                payload = MessagePayload(
                    processedMessage = processedMessage,
                    originalMessage = originalMessage
                )
            )
        }
        try {
            val payload = MessagePayload(
                processedMessage = processedMessage,
                originalMessage = originalMessage
            )

            dbPlayerQueries.insertMessageBus(
                targetServerId,
                MessageType.PM_DELIVER.name,
                senderUuid,
                senderName,
                recipientUuid,
                recipientName,
                json.encodeToString(payload)
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to send cross-server message", e)
            false
        }
    }

    suspend fun reclaimStaleMessages() = withContext(Dispatchers.IO) {
        val config = configManager.storage.crossServerMessaging
        if (!config.enabled || isRedisBackend) return@withContext

        try {
            val cutoff = Instant.now().minusSeconds(config.claimTimeoutSeconds.toLong())
            dbPlayerQueries.reclaimStaleMessages(cutoff)
        } catch (e: Exception) {
            logger.error("Failed to reclaim stale messages", e)
        }
    }

    private fun publishRedisMessage(
        targetServerId: String,
        type: MessageType,
        senderUuid: UUID,
        senderName: String,
        recipientUuid: UUID,
        recipientName: String,
        payload: MessagePayload
    ): Boolean {
        val publisher = publishConnection ?: return false
        val channel = "${backendConfig.crossServerMessaging.redis.channelPrefix}:server:$targetServerId"
        val id = allocateRedisMessageId(publisher)
        val envelope = RedisEnvelope(
            id = id,
            type = type,
            senderUuid = senderUuid.toString(),
            senderName = senderName,
            recipientUuid = recipientUuid.toString(),
            recipientName = recipientName,
            payload = payload
        )

        return runCatching {
            publisher.sync().publish(channel, json.encodeToString(envelope))
        }.onFailure { ex ->
            logger.error("Failed to publish cross-server message to Redis channel $channel", ex)
        }.isSuccess
    }
}
