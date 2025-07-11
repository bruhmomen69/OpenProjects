package lol.mcplugs.minimessagechatplugin.paper.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.ChatCooldownException
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import lol.mcplugs.minimessagechatplugin.paper.services.ChatToggleService
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

class ChatMessageListener(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val chatToggleService: ChatToggleService,
    private val messageFormattingService: MessageFormattingService
) : Listener {
    
    private val logger = LoggerFactory.getLogger(ChatMessageListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncChat(event: AsyncChatEvent) {
        if (event.isCancelled) return
        
        // Check if chat formatting is enabled
        if (!configManager.config.chat.enableFormatting) {
            return // Let vanilla handle the chat
        }
        
        // Check if player can send chat messages
        if (!chatToggleService.canSendChat(event.player)) {
            event.isCancelled = true
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.disabled_self", event.player))
            return
        }

        event.isCancelled = true

        try {
            val player = event.player
            val message = plainTextSerializer.serialize(event.message())
            
            // Log the chat message if enabled
            if (configManager.config.chat.enableLogging) {
                logger.info("[CHAT] ${player.name}: $message")
            }
            
            val formattedMessage = chatFormattingService.formatMessage(player, message)
            event.message(formattedMessage)
            for (viewer in event.viewers()) {
                viewer.sendMessage(formattedMessage)
            }
        } catch (e: ChatCooldownException) {
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.cooldown", event.player, 
                mapOf("time" to e.message!!.substringAfter("wait ").substringBefore(" seconds"))))
        } catch (e: Exception) {
            logger.error("Error formatting chat message for player ${event.player.name}", e)
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.formatting_error", event.player))
        }
    }
}
