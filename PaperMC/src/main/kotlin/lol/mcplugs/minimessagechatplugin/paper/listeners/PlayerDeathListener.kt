package lol.mcplugs.minimessagechatplugin.paper.listeners

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import lol.mcplugs.minimessagechatplugin.paper.utils.MessageEnhancer
import net.kyori.adventure.text.Component
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
    
    private val messageEnhancer = MessageEnhancer(configManager, messageFormattingService)
    
    private val logger = LoggerFactory.getLogger(PlayerDeathListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // Check if death messages should be completely disabled
        if (configManager.config.death.disabled) {
            event.deathMessage(null)
            return
        }
        
        // Check if custom death messages are enabled
        if (!configManager.config.death.enabled) {
            return // Let vanilla handle death messages
        }
        
        val player = event.player
        val originalMessage = event.deathMessage() ?: return
        
        // Try to get death cause from the player's last damage cause
        val deathCause = player.lastDamageCause?.cause?.name ?: "UNKNOWN"
        
        // First try to find a custom message by death cause
        var customMessage = configManager.config.death.messages[deathCause]
        
        // If not found by death cause, try to find by the original vanilla message text
        if (customMessage == null) {
            val originalText = plainTextSerializer.serialize(originalMessage)
            customMessage = configManager.config.death.messages[originalText]
        }
        
        // If still no custom message found, use the backup death message
        if (customMessage == null) {
            customMessage = configManager.config.death.defaultMessage
        }
        
        // If custom message is empty, disable this specific death message
        if (customMessage.isBlank()) {
            event.deathMessage(null)
            return
        }
        
        // Use MessageFormattingService for consistent placeholder processing
        try {
            val location = player.location
            val worldName = location.world?.name ?: "unknown"
            
            val baseMessage = messageFormattingService.formatMessage(
                format = customMessage,
                player = player,
                additionalPlaceholders = mapOf(
                    "death_cause" to deathCause,
                    "death_message" to plainTextSerializer.serialize(originalMessage),
                    "original_message" to plainTextSerializer.serialize(originalMessage),
                    "world" to worldName,
                    "x" to location.blockX.toString(),
                    "y" to location.blockY.toString(),
                    "z" to location.blockZ.toString()
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
                messageType = MessageEnhancer.MessageType.DEATH,
                additionalPlaceholders = mapOf(
                    "death_cause" to deathCause,
                    "death_message" to plainTextSerializer.serialize(originalMessage),
                    "world" to worldName,
                    "x" to location.blockX.toString(),
                    "y" to location.blockY.toString(),
                    "z" to location.blockZ.toString()
                )
            )
            
            event.deathMessage(enhancedMessage)
            
            // Log the death if chat logging is enabled
            if (configManager.config.chat.enableLogging) {
                logger.info("[DEATH] ${player.name} died: $deathCause at $worldName ${location.blockX}, ${location.blockY}, ${location.blockZ}")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse death message format: $customMessage", e)
            // Fall back to a simple formatted message
            try {
                val fallbackBase = messageFormattingService.formatMessage(
                    format = "<gray>💀</gray> <yellow><player_name></yellow> <gray>died</gray>",
                    player = player,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
                
                val fallbackEnhanced = messageEnhancer.enhanceMessage(
                    message = fallbackBase,
                    player = player,
                    messageType = MessageEnhancer.MessageType.DEATH,
                    additionalPlaceholders = mapOf(
                        "death_cause" to deathCause,
                        "death_message" to plainTextSerializer.serialize(originalMessage),
                        "world" to (player.world.name ?: "unknown"),
                        "x" to player.location.blockX.toString(),
                        "y" to player.location.blockY.toString(),
                        "z" to player.location.blockZ.toString()
                    )
                )
                event.deathMessage(fallbackEnhanced)
            } catch (fallbackException: Exception) {
                logger.error("Failed to parse fallback death message", fallbackException)
                // Keep original vanilla message as last resort
            }
        }
    }
}
