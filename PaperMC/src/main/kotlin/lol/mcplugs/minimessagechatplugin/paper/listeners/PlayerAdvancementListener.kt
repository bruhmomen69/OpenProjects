package lol.mcplugs.minimessagechatplugin.paper.listeners

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.slf4j.LoggerFactory

class PlayerAdvancementListener(
    private val configManager: ConfigManager
) : Listener {
    
    private val logger = LoggerFactory.getLogger(PlayerAdvancementListener::class.java)

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
