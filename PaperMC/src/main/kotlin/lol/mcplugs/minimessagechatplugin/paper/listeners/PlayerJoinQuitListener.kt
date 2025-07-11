package lol.mcplugs.minimessagechatplugin.paper.listeners

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import lol.mcplugs.minimessagechatplugin.paper.utils.MessageEnhancer
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.slf4j.LoggerFactory

class PlayerJoinQuitListener(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val messageFormattingService: MessageFormattingService
) : Listener {
    
    private val messageEnhancer = MessageEnhancer(configManager, messageFormattingService)
    
    private val logger = LoggerFactory.getLogger(PlayerJoinQuitListener::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!configManager.config.features.enableJoinMessages) {
            event.joinMessage(null)
            return
        }
        
        val player = event.player
        val originalMessage = event.joinMessage()
        val joinMessage = configManager.config.features.joinMessage
        
        if (joinMessage.isBlank()) {
            event.joinMessage(null)
            return
        }
        
        try {
            val baseMessage = messageFormattingService.formatMessage(
                format = joinMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "original_message" to (originalMessage?.let { plainTextSerializer.serialize(it) } ?: "${player.name} joined the game"),
                    "online_players_after_join" to player.server.onlinePlayers.size.toString(),
                    "ping" to player.ping.toString()
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            // Enhance the message with hover and click actions
            val enhancedMessage = messageEnhancer.enhanceMessage(
                message = baseMessage,
                player = player,
                messageType = MessageEnhancer.MessageType.JOIN
            )
            
            event.joinMessage(enhancedMessage)
            
            if (configManager.config.features.enableChatLogging) {
                logger.info("[JOIN] ${player.name} joined the server")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse join message format: $joinMessage", e)
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
        
        if (leaveMessage.isBlank()) {
            event.quitMessage(null)
            return
        }
        
        try {
            val baseMessage = messageFormattingService.formatMessage(
                format = leaveMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "original_message" to (originalMessage?.let { plainTextSerializer.serialize(it) } ?: "${player.name} left the game"),
                    "online_players_after_leave" to (player.server.onlinePlayers.size - 1).toString(),
                    "ping" to player.ping.toString()
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            // Enhance the message with hover and click actions
            val enhancedMessage = messageEnhancer.enhanceMessage(
                message = baseMessage,
                player = player,
                messageType = MessageEnhancer.MessageType.LEAVE
            )
            
            event.quitMessage(enhancedMessage)
            
            if (configManager.config.features.enableChatLogging) {
                logger.info("[QUIT] ${player.name} left the server")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse quit message format: $leaveMessage", e)
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
}
