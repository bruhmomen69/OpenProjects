package bruh.zchat.paper.services

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.DatabaseService
import bruh.zchat.paper.database.DatabaseType
import bruh.zchat.paper.database.PlayerDataManager
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class CrossServerMessageBusService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val databaseService: DatabaseService,
    private val playerDataManager: PlayerDataManager,
    private val privateMessageService: PrivateMessageService,
    private val socialSpyService: SocialSpyService,
    private val messageFormattingService: MessageFormattingService,
    private val serverInstanceId: String
) {
    private val logger = LoggerFactory.getLogger(CrossServerMessageBusService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private data class ClaimedMessage(
        val id: Long,
        val type: String,
        val senderUuid: UUID,
        val senderName: String,
        val recipientUuid: UUID,
        val recipientName: String?,
        val payloadJson: String
    )

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

    suspend fun pollMessages() = withContext(Dispatchers.IO) {
        val config = configManager.config.crossServerMessaging
        if (!config.enabled || databaseService.databaseType != DatabaseType.MYSQL) return@withContext

        try {
            // 1. Claim messages
            val claimedCount = databaseService.executeUpdate(
                """UPDATE message_bus 
                   SET status = 'CLAIMED', claimed_by = ?, claimed_at = CURRENT_TIMESTAMP 
                   WHERE target_server_id = ? AND status = 'PENDING' 
                   ORDER BY id ASC LIMIT ?""",
                serverInstanceId, serverInstanceId, config.pollBatchSize
            )

            if (claimedCount == 0) return@withContext

            // 2. Fetch claimed messages
            val claimed = databaseService.executeQuery(
                """SELECT id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload
                   FROM message_bus
                   WHERE claimed_by = ? AND status = 'CLAIMED'""",
                serverInstanceId
            ) { rs ->
                ClaimedMessage(
                    id = rs.getLong("id"),
                    type = rs.getString("type"),
                    senderUuid = UUID.fromString(rs.getString("sender_uuid")),
                    senderName = rs.getString("sender_username"),
                    recipientUuid = UUID.fromString(rs.getString("recipient_uuid")),
                    recipientName = rs.getString("recipient_username"),
                    payloadJson = rs.getString("payload")
                )
            }

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
                            format = configManager.config.privateMessages.recipientFormat,
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
                            "RECIPIENT_MESSAGES_DISABLED" -> "private_messages.target_messages_disabled"
                            else -> "private_messages.delivery_failed"
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
        val config = configManager.config.crossServerMessaging
        val cutoff = Instant.now().minusSeconds(config.heartbeatTimeoutSeconds.toLong())

        // Find where sender is now (heartbeat-aware)
        val senderPresence = databaseService.executeQuerySingle(
            """SELECT online_server_id FROM players
               WHERE uuid = ?
               AND is_online = TRUE
               AND online_server_id IS NOT NULL
               AND online_last_heartbeat IS NOT NULL
               AND online_last_heartbeat >= ?""",
            originalSenderUuid,
            cutoff
        ) { rs -> rs.getString("online_server_id") }

        if (senderPresence != null) {
            val failurePayload = MessagePayload(
                processedMessage = originalPayload.processedMessage,
                originalMessage = originalPayload.originalMessage,
                reason = reason
            )

            val recipientLabel = originalRecipientName
                ?: databaseService.executeQuerySingle(
                    "SELECT username FROM players WHERE uuid = ?",
                    originalRecipientUuid
                ) { rs -> rs.getString("username") }

            val recipientLabelFinal = recipientLabel ?: originalRecipientUuid.toString()

            databaseService.executeUpdate(
                """INSERT INTO message_bus 
                   (target_server_id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload, status) 
                   VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')""",
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

    private suspend fun updateMessageStatus(id: Long, status: String, error: String? = null) {
        try {
            if (error == null) {
                databaseService.executeUpdate(
                    "UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = NULL WHERE id = ?",
                    status, id
                )
            } else {
                databaseService.executeUpdate(
                    "UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = ? WHERE id = ?",
                    status, error, id
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to update status for message $id", e)
        }
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
        try {
            val payload = MessagePayload(
                processedMessage = processedMessage,
                originalMessage = originalMessage
            )

            databaseService.executeUpdate(
                """INSERT INTO message_bus 
                   (target_server_id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload, status) 
                   VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')""",
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
        val config = configManager.config.crossServerMessaging
        if (!config.enabled) return@withContext

        try {
            val cutoff = Instant.now().minusSeconds(config.claimTimeoutSeconds.toLong())
            databaseService.executeUpdate(
                """UPDATE message_bus 
                   SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL 
                   WHERE status = 'CLAIMED' AND claimed_at < ?""",
                cutoff
            )
        } catch (e: Exception) {
            logger.error("Failed to reclaim stale messages", e)
        }
    }
}
