package lol.mcplugs.minimessagechatplugin.paper.listeners

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.slf4j.LoggerFactory

class PlayerDeathListener(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) : Listener {
    
    private val logger = LoggerFactory.getLogger(PlayerDeathListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

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
}
