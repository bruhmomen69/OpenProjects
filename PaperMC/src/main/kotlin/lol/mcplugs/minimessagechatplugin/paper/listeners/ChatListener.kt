package lol.mcplugs.minimessagechatplugin.paper.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.ChatCooldownException
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import lol.mcplugs.minimessagechatplugin.paper.services.ChatToggleService
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.slf4j.LoggerFactory

class ChatListener(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val chatToggleService: ChatToggleService,
    private val messageFormattingService: MessageFormattingService
) : Listener {
    
    private val logger = LoggerFactory.getLogger(ChatListener::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGH)
    fun onAsyncChat(event: AsyncChatEvent) {
        if (event.isCancelled) return
        
        // Check if chat formatting is enabled
        if (!configManager.config.features.enableChatFormatting) {
            return // Let vanilla handle the chat
        }
        
        // Check if player can send chat messages
        if (!chatToggleService.canSendChat(event.player)) {
            event.isCancelled = true
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.disabled_self", event.player))
            return
        }

        try {
            val player = event.player
            val message = plainTextSerializer.serialize(event.message())
            
            // Log the chat message if enabled
            if (configManager.config.features.enableChatLogging) {
                logger.info("[CHAT] ${player.name}: $message")
            }
            
            val formattedMessage = chatFormattingService.formatMessage(player, message)

            event.message(formattedMessage)
            // If we need to do custom message sending, make the prio monitor, and cancel the event
        } catch (e: ChatCooldownException) {
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.cooldown", event.player, mapOf("time" to e.message!!.substringAfter("wait ").substringBefore(" seconds"))))
            event.isCancelled = true
        } catch (e: Exception) {
            logger.error("Error formatting chat message for player ${event.player.name}", e)
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.formatting_error", event.player))
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!configManager.config.features.enableJoinMessages) {
            event.joinMessage(null)
            return
        }
        
        val player = event.player
        val originalMessage = event.joinMessage()
        val joinMessage = configManager.config.features.joinMessage
        
        // If join message is empty, disable join messages
        if (joinMessage.isBlank()) {
            event.joinMessage(null)
            return
        }
        
        // Use MessageFormattingService for consistent placeholder processing
        try {
            val formattedMessage = messageFormattingService.formatMessage(
                format = joinMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "original_message" to (originalMessage?.let { plainTextSerializer.serialize(it) } ?: "${player.name} joined the game"),
                    "online_players_after_join" to player.server.onlinePlayers.size.toString()
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            event.joinMessage(formattedMessage)
            
            // Log the join if chat logging is enabled
            if (configManager.config.features.enableChatLogging) {
                logger.info("[JOIN] ${player.name} joined the server")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse join message format: $joinMessage", e)
            // Fall back to a simple formatted message
            try {
                val fallbackMessage = messageFormattingService.formatMessage(
                    format = "<yellow><player_name> joined the game</yellow>",
                    player = player,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                event.joinMessage(fallbackMessage)
            } catch (fallbackException: Exception) {
                logger.error("Failed to parse fallback join message", fallbackException)
                event.joinMessage(miniMessage.deserialize("<yellow>${player.name} joined the game</yellow>"))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Clear cooldown when player leaves
        chatFormattingService.clearCooldown(event.player)
        
        if (!configManager.config.features.enableLeaveMessages) {
            event.quitMessage(null)
            return
        }
        
        val player = event.player
        val originalMessage = event.quitMessage()
        val leaveMessage = configManager.config.features.leaveMessage
        
        // If leave message is empty, disable leave messages
        if (leaveMessage.isBlank()) {
            event.quitMessage(null)
            return
        }
        
        // Use MessageFormattingService for consistent placeholder processing
        try {
            val formattedMessage = messageFormattingService.formatMessage(
                format = leaveMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "original_message" to (originalMessage?.let { plainTextSerializer.serialize(it) } ?: "${player.name} left the game"),
                    "online_players_after_leave" to (player.server.onlinePlayers.size - 1).toString()
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            event.quitMessage(formattedMessage)
            
            // Log the quit if chat logging is enabled
            if (configManager.config.features.enableChatLogging) {
                logger.info("[QUIT] ${player.name} left the server")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse quit message format: $leaveMessage", e)
            // Fall back to a simple formatted message
            try {
                val fallbackMessage = messageFormattingService.formatMessage(
                    format = "<yellow><player_name> left the game</yellow>",
                    player = player,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                event.quitMessage(fallbackMessage)
            } catch (fallbackException: Exception) {
                logger.error("Failed to parse fallback quit message", fallbackException)
                event.quitMessage(miniMessage.deserialize("<yellow>${player.name} left the game</yellow>"))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // Check if death messages should be completely disabled
        if (configManager.config.features.disableDeathMessages) {
            event.deathMessage(null)
            return
        }
        
        // Check if custom death messages are enabled
        if (!configManager.config.features.enableDeathMessages) {
            return // Let vanilla handle death messages
        }
        
        val player = event.player
        val originalMessage = event.deathMessage() ?: return
        
        // Try to get death cause from the player's last damage cause
        val deathCause = player.lastDamageCause?.cause?.name ?: "UNKNOWN"
        
        // First try to find a custom message by death cause
        var customMessage = configManager.config.features.customDeathMessages[deathCause]
        
        // If not found by death cause, try to find by the original vanilla message text
        if (customMessage == null) {
            val originalText = plainTextSerializer.serialize(originalMessage)
            customMessage = configManager.config.features.customDeathMessages[originalText]
        }
        
        // If still no custom message found, use the backup death message
        if (customMessage == null) {
            customMessage = configManager.config.features.backupDeathMessage
        }
        
        // If custom message is empty, disable this specific death message
        if (customMessage.isBlank()) {
            event.deathMessage(null)
            return
        }
        
        // Use MessageFormattingService for consistent placeholder processing
        try {
            val formattedMessage = messageFormattingService.formatMessage(
                format = customMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "death_cause" to deathCause,
                    "original_message" to plainTextSerializer.serialize(originalMessage)
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            event.deathMessage(formattedMessage)
            
            // Log the death if chat logging is enabled
            if (configManager.config.features.enableChatLogging) {
                logger.info("[DEATH] ${player.name} died: $deathCause")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse death message format: $customMessage", e)
            // Fall back to a simple formatted message
            try {
                val fallbackMessage = messageFormattingService.formatMessage(
                    format = "<gray>💀</gray> <yellow><player_name></yellow> <gray>died</gray>",
                    player = player,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                event.deathMessage(fallbackMessage)
            } catch (fallbackException: Exception) {
                logger.error("Failed to parse fallback death message", fallbackException)
                // Keep original vanilla message as last resort
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!configManager.config.features.enableAdvancementMessages) {
            return
        }
        
        // Advancement messages are handled by the server by default
        // This event can be used to customize advancement messages if needed
        val advancement = event.advancement
        logger.debug("Player {} completed advancement: {}", event.player.name, advancement.key)
    }
}
