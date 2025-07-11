package lol.mcplugs.minimessagechatplugin.paper.listeners

import io.papermc.paper.advancement.AdvancementDisplay
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.slf4j.LoggerFactory

class PlayerAdvancementListener(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) : Listener {
    private val logger = LoggerFactory.getLogger(PlayerAdvancementListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerAdvancement(event: PlayerAdvancementDoneEvent) {
        // Check if advancement messages are enabled
        if (!configManager.config.features.enableAdvancementMessages) {
            return
        }
        
        val player = event.player
        val advancement = event.advancement
        
        // Skip if no advancement or player is null
        if (!player.isOnline) {
            return
        }
        
        // Get the advancement display
        val display = advancement.display ?: return // No display, probably internal advancement
        if (!display.doesAnnounceToChat()) {
            return // Don't announce if the advancement isn't set to announce
        }
        
        // Get the advancement key without the namespace
        val advancementKey = advancement.key.key
        
        try {
            // Get the appropriate message format
            val messageFormat = configManager.config.features.customAdvancementMessages[advancementKey]
                ?: configManager.config.features.backupAdvancementMessage
            
            if (messageFormat.isBlank()) {
                return // Skip if message format is empty
            }
            
            // Get advancement details
            val advancementName = plainTextSerializer.serialize(display.title())
            val advancementDescription = plainTextSerializer.serialize(display.description())
            val advancementType = when (display.frame()) {
                AdvancementDisplay.Frame.TASK -> "task"
                AdvancementDisplay.Frame.GOAL -> "goal"
                AdvancementDisplay.Frame.CHALLENGE -> "challenge"
                else -> "advancement"
            }
            
            // Format the message
            val formattedMessage = messageFormattingService.formatMessage(
                format = messageFormat,
                player = player,
                additionalPlaceholders = mapOf(
                    "advancement_name" to advancementName,
                    "advancement_description" to advancementDescription,
                    "advancement_type" to advancementType
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            // Cancel the default advancement message
            event.message(formattedMessage)
            
            // Broadcast the custom message TODO: only do this if needed.
            // player.server.broadcast(formattedMessage)
            
            // Log the advancement if chat logging is enabled
            if (configManager.config.features.enableChatLogging) {
                logger.info("[ADVANCEMENT] ${player.name} completed advancement: $advancementKey ($advancementName)")
            }
        } catch (e: Exception) {
            logger.warn("Failed to format advancement message for $advancementKey", e)
            
            // Fall back to a simple message if formatting fails
            try {
                val fallbackMessage = messageFormattingService.formatMessage(
                    format = "<gray>🎯</gray> <yellow><player_name></yellow> <gray>completed an advancement: <green>$advancementKey</green></gray>",
                    player = player,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                event.message(null)
                player.server.broadcast(fallbackMessage)
            } catch (fallbackException: Exception) {
                logger.error("Failed to format fallback advancement message", fallbackException)
                // Let the default message through if we can't format our fallback
            }
        }
    }
}
