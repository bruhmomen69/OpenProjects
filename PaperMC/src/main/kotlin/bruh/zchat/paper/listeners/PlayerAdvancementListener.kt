package bruh.zchat.paper.listeners

import io.papermc.paper.advancement.AdvancementDisplay
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.utils.MessageEnhancer
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
    
    private val messageEnhancer = MessageEnhancer(configManager, messageFormattingService)
    private val logger = LoggerFactory.getLogger(PlayerAdvancementListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerAdvancement(event: PlayerAdvancementDoneEvent) {
        // Check if advancement messages are enabled
        if (!configManager.config.advancement.enabled) {
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
        
        // Get advancement details
        val advancementName = display.title()?.let { plainTextSerializer.serialize(it) } ?: advancementKey
        val advancementDescription = display.description()?.let { plainTextSerializer.serialize(it) } ?: ""
        
        // Get advancement type
        val advancementType = when (display.frame()) {
            AdvancementDisplay.Frame.TASK -> "task"
            AdvancementDisplay.Frame.GOAL -> "goal"
            AdvancementDisplay.Frame.CHALLENGE -> "challenge"
            else -> "advancement"
        }
        
        try {
            // Get the appropriate message format
            val messageFormat = configManager.messages.advancement.messages[advancementKey]
                ?: configManager.messages.advancement.defaultMessage
            
            if (messageFormat.isBlank()) {
                return // Skip if message format is empty
            }
            
            // Create placeholders for the message
            val placeholders = mapOf(
                "advancement_name" to advancementName,
                "advancement_description" to advancementDescription,
                "advancement_type" to advancementType,
                "advancement_key" to advancementKey
            )
            
            // Format the base message
            val baseMessage = messageFormattingService.formatMessage(
                format = messageFormat,
                player = player,
                additionalPlaceholders = placeholders,
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            // Enhance the message with hover and click actions
            val finalMessage = messageEnhancer.enhanceMessage(
                message = baseMessage,
                player = player,
                messageType = MessageEnhancer.MessageType.ADVANCEMENT,
                additionalPlaceholders = placeholders
            )
            
            // Set the final message
            event.message(finalMessage)
            
            // Log the advancement if chat logging is enabled
            if (configManager.config.chat.enableLogging) {
                logger.info("[ADVANCEMENT] ${player.name} completed advancement: $advancementKey ($advancementName)")
            }
        } catch (e: Exception) {
            logger.warn("Failed to format advancement message for $advancementKey", e)
            
            // Fall back to a simple message if formatting fails
            try {
                val fallbackBase = messageFormattingService.formatMessage(
                    format = "<gray>🎯</gray> <yellow><player_name></yellow> <gray>completed an advancement: <green>$advancementKey</green></gray>",
                    player = player,
                    additionalPlaceholders = mapOf(
                        "advancement_name" to advancementName,
                        "advancement_key" to advancementKey,
                        "advancement_type" to advancementType,
                        "advancement_description" to advancementDescription
                    ),
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                
                val fallbackEnhanced = messageEnhancer.enhanceMessage(
                    message = fallbackBase,
                    player = player,
                    messageType = MessageEnhancer.MessageType.ADVANCEMENT,
                    additionalPlaceholders = mapOf(
                        "advancement_name" to advancementName,
                        "advancement_key" to advancementKey,
                        "advancement_type" to advancementType,
                        "advancement_description" to advancementDescription
                    )
                )
                event.message(fallbackEnhanced)
            } catch (fallbackException: Exception) {
                logger.error("Failed to format fallback advancement message", fallbackException)
                // Let the default message through if we can't format our fallback
            }
        }
    }
}
