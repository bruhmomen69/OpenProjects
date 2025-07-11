package lol.mcplugs.minimessagechatplugin.paper.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.ChatCooldownException
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import lol.mcplugs.minimessagechatplugin.paper.services.ChatToggleService
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
    private val chatToggleService: ChatToggleService
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
            event.player.sendMessage(miniMessage.deserialize("<red>You have chat disabled! Use /chatplugin toggle chat to enable it.</red>"))
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
            event.player.sendMessage(miniMessage.deserialize("<red>${e.message}</red>"))
            event.isCancelled = true
        } catch (e: Exception) {
            logger.error("Error formatting chat message for player ${event.player.name}", e)
            event.player.sendMessage(miniMessage.deserialize("<red>An error occurred while formatting your message.</red>"))
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!configManager.config.features.enableJoinMessages) {
            event.joinMessage(null)
            return
        }
        
        val joinMessage = configManager.config.features.joinMessage
        
        // If join message is empty, disable join messages
        if (joinMessage.isBlank()) {
            event.joinMessage(null)
            return
        }
        
        val processedMessage = joinMessage
            .replace("{player_name}", event.player.name)
            .replace("{player_displayname}", plainTextSerializer.serialize(event.player.displayName()))
            .replace("{online_players}", event.player.server.onlinePlayers.size.toString())
            .replace("{max_players}", event.player.server.maxPlayers.toString())
        
        try {
            event.joinMessage(miniMessage.deserialize(processedMessage))
        } catch (e: Exception) {
            logger.warn("Failed to parse join message format: $processedMessage", e)
            event.joinMessage(miniMessage.deserialize("<yellow>${event.player.name} joined the game</yellow>"))
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
        
        val leaveMessage = configManager.config.features.leaveMessage
        
        // If leave message is empty, disable leave messages
        if (leaveMessage.isBlank()) {
            event.quitMessage(null)
            return
        }
        
        val processedMessage = leaveMessage
            .replace("{player_name}", event.player.name)
            .replace("{player_displayname}", plainTextSerializer.serialize(event.player.displayName()))
            .replace("{online_players}", (event.player.server.onlinePlayers.size - 1).toString())
            .replace("{max_players}", event.player.server.maxPlayers.toString())
        
        try {
            event.quitMessage(miniMessage.deserialize(processedMessage))
        } catch (e: Exception) {
            logger.warn("Failed to parse quit message format: $processedMessage", e)
            event.quitMessage(miniMessage.deserialize("<yellow>${event.player.name} left the game</yellow>"))
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
        
        val originalMessage = event.deathMessage() ?: return
        val deathCause = plainTextSerializer.serialize(originalMessage)
        val customMessage = configManager.config.features.customDeathMessages[deathCause]
        
        if (customMessage != null) {
            // If custom message is empty, disable this specific death message
            if (customMessage.isBlank()) {
                event.deathMessage(null)
                return
            }
            
            val processedMessage = customMessage
                .replace("{player_name}", event.player.name)
                .replace("{player_displayname}", plainTextSerializer.serialize(event.player.displayName()))
            
            try {
                event.deathMessage(miniMessage.deserialize(processedMessage))
            } catch (e: Exception) {
                logger.warn("Failed to parse custom death message format: $processedMessage", e)
                // Keep original message
            }
        }
        // If no custom message is defined, keep the original vanilla message
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
