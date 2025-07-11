package lol.mcplugs.minimessagechatplugin.paper.listeners

import io.papermc.paper.advancement.AdvancementDisplay
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
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
            
            // Format the base message
            val baseMessage = messageFormattingService.formatMessage(
                format = messageFormat,
                player = player,
                additionalPlaceholders = mapOf(
                    "advancement_name" to advancementName,
                    "advancement_description" to advancementDescription,
                    "advancement_type" to advancementType,
                    "advancement_key" to advancementKey
                ),
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
            
            // Create the final message component
            val finalMessage = Component.text().append(baseMessage)
            
            // Add hover and click actions if enabled
            if (configManager.config.features.enableAdvancementHoverMessages) {
                // Format the hover message
                val hoverText = messageFormattingService.formatMessage(
                    format = configManager.config.features.advancementHoverMessage,
                    player = player,
                    additionalPlaceholders = mapOf(
                        "advancement_name" to advancementName,
                        "advancement_description" to advancementDescription,
                        "advancement_type" to advancementType,
                        "advancement_key" to advancementKey
                    ),
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                
                // Add hover event
                finalMessage.hoverEvent(HoverEvent.showText(hoverText))
                
                // Add click action if configured
                val clickAction = configManager.config.features.advancementClickAction
                if (clickAction.isNotBlank()) {
                    val (actionType, actionValue) = when {
                        clickAction.startsWith("suggest_command:") -> 
                            ClickEvent.Action.SUGGEST_COMMAND to clickAction.substringAfter("suggest_command:")
                        clickAction.startsWith("run_command:") -> 
                            ClickEvent.Action.RUN_COMMAND to clickAction.substringAfter("run_command:")
                        clickAction.startsWith("open_url:") -> 
                            ClickEvent.Action.OPEN_URL to clickAction.substringAfter("open_url:")
                        clickAction.startsWith("copy_to_clipboard:") -> 
                            ClickEvent.Action.COPY_TO_CLIPBOARD to clickAction.substringAfter("copy_to_clipboard:")
                        else -> null to null
                    }
                    
                    if (actionType != null && actionValue != null) {
                        val processedActionValue = actionValue
                            .replace("<player_name>", player.name)
                            .replace("<advancement_key>", advancementKey)
                            .replace("<advancement_name>", advancementName)
                            .replace("<advancement_type>", advancementType)
                        
                        finalMessage.clickEvent(ClickEvent.clickEvent(actionType, processedActionValue))
                    }
                }
            }
            
            // Set the final message
            event.message(finalMessage.build())
            
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
                    additionalPlaceholders = mapOf(
                        "advancement_name" to advancementKey,
                        "advancement_key" to advancementKey,
                        "advancement_type" to "advancement"
                    ),
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                event.message(fallbackMessage)
            } catch (fallbackException: Exception) {
                logger.error("Failed to format fallback advancement message", fallbackException)
                // Let the default message through if we can't format our fallback
            }
        }
    }
}
