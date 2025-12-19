package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import bruh.zchat.paper.database.PlayerDataManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for handling private messages between players with MiniMessage formatting
 */
class PrivateMessageService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val chatToggleService: ChatToggleService,
    private val socialSpyService: SocialSpyService,
    private val blockService: BlockService? = null,
    private val playerDataManager: PlayerDataManager
) {
    private val logger = LoggerFactory.getLogger(PrivateMessageService::class.java)
    
    // Late-init to avoid circular dependency
    var crossServerMessageBusService: CrossServerMessageBusService? = null
    
    // Track last message senders for reply functionality
    private val lastSenders = ConcurrentHashMap<UUID, UUID>()
    
    // Track message cooldowns
    private val messageCooldowns = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Set the last sender for a player (used by cross-server messaging)
     */
    fun setLastSender(recipientUuid: UUID, senderUuid: UUID) {
        lastSenders[recipientUuid] = senderUuid
    }
    
    /**
     * Send a private message from one player to another
     */
    suspend fun sendPrivateMessage(sender: Player, recipientName: String, message: String): Boolean {
        val config = configManager.config.privateMessages
        
        // Check if private messages are enabled
        if (!config.enablePrivateMessages) {
            sender.sendMessage(messageFormattingService.getConfigMessage("private_messages.system_disabled"))
            return false
        }
        
        // Check cooldown
        if (!sender.hasPermission("zchat.bypass.cooldown") && config.enableMessageCooldown) {
            val lastMessage = messageCooldowns[sender.uniqueId] ?: 0
            val cooldownTime = config.messageCooldownSeconds * 1000L
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastMessage < cooldownTime) {
                val remainingTime = (cooldownTime - (currentTime - lastMessage)) / 1000.0
                sender.sendMessage(messageFormattingService.getConfigMessage(
                    "private_messages.cooldown", 
                    sender, 
                    mapOf("time" to String.format("%.1f", remainingTime))
                ))
                return false
            }
            
            messageCooldowns[sender.uniqueId] = currentTime
        }
        
        // Try local recipient first
        val localRecipient = Bukkit.getPlayer(recipientName)
        
        if (localRecipient != null) {
            return sendLocalPrivateMessage(sender, localRecipient, message, config)
        }
        
        // Try cross-server recipient
        if (configManager.storage.crossServerMessaging.enabled && crossServerMessageBusService != null) {
            // Look up UUID from name using our DB (async and safe)
            val targetUuid = playerDataManager.getUuidByUsername(recipientName)
            
            if (targetUuid != null) {
                // Check presence
                val targetServerId = playerDataManager.getCrossServerPresence(
                    targetUuid,
                    configManager.storage.crossServerMessaging.heartbeatTimeoutSeconds
                )
                
                if (targetServerId != null) {
                    // Respect recipient message toggle (cross-server)
                    if (configManager.config.chatToggle.enableMessageToggle) {
                        val toggleState = playerDataManager.getToggleState(targetUuid)
                        if (toggleState?.messagesDisabled == true) {
                            sender.sendMessage(
                                messageFormattingService.getConfigMessage(
                                    "private_messages.target_messages_disabled",
                                    sender,
                                    mapOf("player" to recipientName)
                                )
                            )
                            return false
                        }
                    }

                    // Check blocks (basic DB check)
                    if (blockService?.isBlocked(targetUuid, sender.uniqueId) == true) {
                        sender.sendMessage(messageFormattingService.getConfigMessage(
                            "blocks.target_blocked_you",
                            sender,
                            mapOf("player" to recipientName)
                        ))
                        return false
                    }

                    // Process message content
                    val processedMessage = messageFormattingService.processMessageContent(sender, message)
                    
                    // Send cross-server
                    val success = crossServerMessageBusService!!.sendCrossServerMessage(
                        senderUuid = sender.uniqueId,
                        senderName = sender.name,
                        recipientUuid = targetUuid,
                        recipientName = recipientName,
                        targetServerId = targetServerId,
                        processedMessage = processedMessage,
                        originalMessage = message
                    )
                    
                    if (success) {
                        // Show sender formatted message
                        val senderMessage = messageFormattingService.formatMessage(
                            format = configManager.messages.privateMessages.senderFormat,
                            player = sender,
                            additionalPlaceholders = mapOf(
                                "sender" to sender.name,
                                "recipient" to recipientName,
                                "message" to processedMessage
                            ),
                            processUrls = false,
                            processMentions = false,
                            allowColors = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.colorPermission),
                            allowFormatting = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.formattingPermission)
                        )
                        sender.sendMessage(senderMessage)
                        
                        // Log
                        if (config.enableMessageLogging) {
                            logger.info("[MSG-CROSS] ${sender.name} -> $recipientName: $message")
                        }
                        return true
                    } else {
                        sender.sendMessage(messageFormattingService.getConfigMessage(
                            "private_messages.delivery_failed",
                            sender,
                            mapOf("player" to recipientName)
                        ))
                        logger.warn("Failed to send cross-server private message from ${sender.name} to $recipientName")
                        return false
                    }
                }
            }
        }
        
        // Player not found / not online
        sender.sendMessage(messageFormattingService.getConfigMessage(
            "private_messages.player_not_found",
            sender,
            mapOf("recipient" to recipientName)
        ))
        return false
    }

    private suspend fun sendLocalPrivateMessage(sender: Player, recipient: Player, message: String, config: bruh.zchat.paper.config.PrivateMessageConfig): Boolean {
        // Check if recipient is the same as sender
        if (recipient.uniqueId == sender.uniqueId) {
            sender.sendMessage(messageFormattingService.getConfigMessage("private_messages.self_message", sender))
            return false
        }
        
        // Check if recipient has messages disabled
        if (!chatToggleService.canReceiveMessages(recipient)) {
            sender.sendMessage(messageFormattingService.getConfigMessage(
                "private_messages.target_messages_disabled",
                sender,
                mapOf("player" to recipient.name)
            ))
            return false
        }
        
        // Check if sender is blocked by recipient
        if (blockService?.isBlocked(recipient.uniqueId, sender.uniqueId) == true) {
            sender.sendMessage(messageFormattingService.getConfigMessage(
                "blocks.target_blocked_you",
                sender,
                mapOf("player" to recipient.name)
            ))
            return false
        }
        
        // Process message content using MessageFormattingService
        val processedMessage = messageFormattingService.processMessageContent(sender, message)
        
        // Create formatted messages using MessageFormattingService
        val senderMessage = messageFormattingService.formatMessage(
            format = configManager.messages.privateMessages.senderFormat,
            player = sender,
            additionalPlaceholders = mapOf(
                "sender" to sender.name,
                "recipient" to recipient.name,
                "message" to processedMessage
            ),
            processUrls = false,
            processMentions = false,
            allowColors = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.colorPermission),
            allowFormatting = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.formattingPermission)
        )
        
        val recipientMessage = messageFormattingService.formatMessage(
            format = configManager.messages.privateMessages.recipientFormat,
            player = recipient,
            additionalPlaceholders = mapOf(
                "sender" to sender.name,
                "recipient" to recipient.name,
                "message" to processedMessage
            ),
            processUrls = false,
            processMentions = false,
            allowColors = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.colorPermission),
            allowFormatting = config.allowFormattingInMessages && sender.hasPermission(configManager.config.permissions.formattingPermission)
        )
        
        // Send messages
        sender.sendMessage(senderMessage)
        recipient.sendMessage(recipientMessage)
        
        // Update last sender for reply functionality
        lastSenders[recipient.uniqueId] = sender.uniqueId
        
        // Log message if enabled
        if (config.enableMessageLogging) {
            logger.info("[MSG] ${sender.name} -> ${recipient.name}: $message")
        }
        
        // Send to social spy if enabled
        socialSpyService.broadcastPrivateMessage(sender, recipient, message)
        
        return true
    }
    
    /**
     * Reply to the last person who sent a message
     */
    suspend fun replyToLastSender(sender: Player, message: String): Boolean {
        val lastSenderUUID = lastSenders[sender.uniqueId]
        if (lastSenderUUID == null) {
            sender.sendMessage(messageFormattingService.getConfigMessage("private_messages.no_reply_target", sender))
            return false
        }
        
        val lastSender = Bukkit.getPlayer(lastSenderUUID)
        if (lastSender != null && lastSender.isOnline) {
            return sendPrivateMessage(sender, lastSender.name, message)
        }

        if (configManager.storage.crossServerMessaging.enabled && crossServerMessageBusService != null) {
            val serverId = playerDataManager.getCrossServerPresence(
                lastSenderUUID,
                configManager.storage.crossServerMessaging.heartbeatTimeoutSeconds
            )

            if (serverId != null) {
                val username = playerDataManager.getUsernameByUuid(lastSenderUUID)
                if (username != null) {
                    return sendPrivateMessage(sender, username, message)
                }
            }
        }

        sender.sendMessage(messageFormattingService.getConfigMessage("private_messages.reply_target_offline", sender))
        lastSenders.remove(sender.uniqueId)
        return false
    }
    
    
    /**
     * Clear message cooldown for a player
     */
    fun clearCooldown(player: Player) {
        messageCooldowns.remove(player.uniqueId)
    }
    
    /**
     * Clear all message cooldowns
     */
    fun clearAllCooldowns() {
        messageCooldowns.clear()
    }
    
    /**
     * Remove last sender tracking when player leaves
     */
    fun handlePlayerQuit(player: Player) {
        lastSenders.remove(player.uniqueId)
        messageCooldowns.remove(player.uniqueId)
                
        // Remove this player as a last sender for others
        lastSenders.values.removeAll { it == player.uniqueId }
    }
    
    /**
     * Get the last sender for a player (for debugging/info)
     */
    fun getLastSender(player: Player): Player? {
        val lastSenderUUID = lastSenders[player.uniqueId] ?: return null
        return Bukkit.getPlayer(lastSenderUUID)
    }
}