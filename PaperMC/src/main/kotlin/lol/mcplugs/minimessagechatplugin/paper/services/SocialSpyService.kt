package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for social spy functionality - allows moderators to monitor private messages
 */
class SocialSpyService(
    private val configManager: ConfigManager,
    private val placeholderAPIService: PlaceholderAPIService
) {
    private val logger = LoggerFactory.getLogger(SocialSpyService::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    
    // Track players who have social spy enabled
    private val socialSpyEnabled = ConcurrentHashMap.newKeySet<UUID>()
    
    /**
     * Toggle social spy for a moderator
     */
    fun toggleSocialSpy(player: Player): Boolean {
        val config = configManager.config.socialSpy
        
        if (!config.enableSocialSpy) {
            player.sendMessage(miniMessage.deserialize("<red>Social spy is currently disabled.</red>"))
            return false
        }
        
        if (!player.hasPermission("chatplugin.socialspy")) {
            player.sendMessage(miniMessage.deserialize("<red>You don't have permission to use social spy!</red>"))
            return false
        }
        
        val wasEnabled = socialSpyEnabled.contains(player.uniqueId)
        
        if (wasEnabled) {
            socialSpyEnabled.remove(player.uniqueId)
            player.sendMessage(miniMessage.deserialize(config.socialSpyDisabledMessage))
            logger.info("${player.name} disabled social spy")
        } else {
            socialSpyEnabled.add(player.uniqueId)
            player.sendMessage(miniMessage.deserialize(config.socialSpyEnabledMessage))
            logger.info("${player.name} enabled social spy")
        }
        
        return !wasEnabled // Return new state (true = enabled, false = disabled)
    }
    
    /**
     * Broadcast a private message to all social spy users
     */
    fun broadcastPrivateMessage(sender: Player, recipient: Player, message: String) {
        val config = configManager.config.socialSpy
        
        if (!config.enableSocialSpy || socialSpyEnabled.isEmpty()) {
            return
        }
        
        // Don't spy on staff messages if configured
        if (config.ignoreModerators && 
            (sender.hasPermission("chatplugin.socialspy") || recipient.hasPermission("chatplugin.socialspy"))) {
            return
        }
        
        val spyMessage = createSpyMessage(config.socialSpyFormat, sender, recipient, message)
        
        // Send to all social spy users
        for (spyPlayerUUID in socialSpyEnabled) {
            val spyPlayer = Bukkit.getPlayer(spyPlayerUUID)
            if (spyPlayer != null && spyPlayer.isOnline) {
                // Don't send spy message to the sender or recipient
                if (spyPlayer.uniqueId != sender.uniqueId && spyPlayer.uniqueId != recipient.uniqueId) {
                    spyPlayer.sendMessage(spyMessage)
                }
            }
        }
        
        // Log to console if enabled
        if (config.logToConsole) {
            logger.info("[SOCIALSPY] ${sender.name} -> ${recipient.name}: $message")
        }
    }
    
    /**
     * Create a formatted social spy message
     */
    private fun createSpyMessage(format: String, sender: Player, recipient: Player, message: String): Component {
        val resolvers = mutableListOf<TagResolver>()
        
        // Add basic placeholders
        resolvers.add(Placeholder.unparsed("sender", sender.name))
        resolvers.add(Placeholder.unparsed("recipient", recipient.name))
        resolvers.add(Placeholder.unparsed("message", message))
        resolvers.add(Placeholder.component("sender_displayname", sender.displayName()))
        resolvers.add(Placeholder.component("recipient_displayname", recipient.displayName()))
        
        // Add PlaceholderAPI support if enabled
        if (placeholderAPIService.isEnabled()) {
            val placeholderAPIResolver = placeholderAPIService.createPlaceholderAPIResolver(sender, format)
            resolvers.add(placeholderAPIResolver)
        }
        
        val combinedResolver = TagResolver.resolver(resolvers)
        
        return try {
            miniMessage.deserialize(format, combinedResolver)
        } catch (e: Exception) {
            logger.warn("Failed to parse social spy format: $format", e)
            miniMessage.deserialize("<dark_gray>[SPY]</dark_gray> <gray>${sender.name} -> ${recipient.name}:</gray> <white>$message</white>")
        }
    }
    
    /**
     * Check if a player has social spy enabled
     */
    fun hasSocialSpyEnabled(player: Player): Boolean {
        return socialSpyEnabled.contains(player.uniqueId)
    }
    
    /**
     * Get list of players with social spy enabled
     */
    fun getSocialSpyUsers(): List<Player> {
        return socialSpyEnabled.mapNotNull { uuid ->
            Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }
        }
    }
    
    /**
     * Force enable social spy for a player (admin command)
     */
    fun forceEnableSocialSpy(player: Player): Boolean {
        if (!player.hasPermission("chatplugin.socialspy")) {
            return false
        }
        
        socialSpyEnabled.add(player.uniqueId)
        logger.info("Social spy force-enabled for ${player.name}")
        return true
    }
    
    /**
     * Force disable social spy for a player (admin command)
     */
    fun forceDisableSocialSpy(player: Player) {
        socialSpyEnabled.remove(player.uniqueId)
        logger.info("Social spy force-disabled for ${player.name}")
    }
    
    /**
     * Handle player quit - clean up tracking
     */
    fun handlePlayerQuit(player: Player) {
        // Note: We don't remove players from social spy on quit
        // This preserves their spy state across reconnections
        // Only remove if persistence is disabled in config
        
        val config = configManager.config.socialSpy
        if (!config.persistSocialSpyState) {
            socialSpyEnabled.remove(player.uniqueId)
        }
    }
    
    /**
     * Get social spy statistics
     */
    fun getSocialSpyStats(): Map<String, Any> {
        val onlineSpyUsers = getSocialSpyUsers()
        return mapOf(
            "total_spy_users" to socialSpyEnabled.size,
            "online_spy_users" to onlineSpyUsers.size,
            "spy_user_names" to onlineSpyUsers.map { it.name }
        )
    }
    
    /**
     * Clear all social spy states (admin command)
     */
    fun clearAllSocialSpy() {
        val count = socialSpyEnabled.size
        socialSpyEnabled.clear()
        logger.info("Cleared $count social spy states")
    }
    
    /**
     * Broadcast a command to social spy users (for command monitoring)
     */
    fun broadcastCommand(player: Player, command: String) {
        val config = configManager.config.socialSpy
        
        if (!config.enableCommandSpy || socialSpyEnabled.isEmpty()) {
            return
        }
        
        // Don't spy on staff commands if configured
        if (config.ignoreModerators && player.hasPermission("chatplugin.socialspy")) {
            return
        }
        
        val spyMessage = miniMessage.deserialize(
            config.commandSpyFormat
                .replace("{player}", player.name)
                .replace("{command}", command)
        )
        
        // Send to all social spy users
        for (spyPlayerUUID in socialSpyEnabled) {
            val spyPlayer = Bukkit.getPlayer(spyPlayerUUID)
            if (spyPlayer != null && spyPlayer.isOnline && spyPlayer.uniqueId != player.uniqueId) {
                spyPlayer.sendMessage(spyMessage)
            }
        }
        
        // Log to console if enabled
        if (config.logToConsole) {
            logger.info("[COMMANDSPY] ${player.name}: $command")
        }
    }
}