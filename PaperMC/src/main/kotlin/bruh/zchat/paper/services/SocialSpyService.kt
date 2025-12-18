package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import net.kyori.adventure.text.minimessage.MiniMessage
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
    private val messageFormattingService: MessageFormattingService
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
            player.sendMessage(messageFormattingService.getConfigMessage("social_spy.system_disabled", player))
            return false
        }
        
        if (!player.hasPermission("zchat.socialspy")) {
            player.sendMessage(messageFormattingService.getConfigMessage("social_spy.no_permission", player))
            return false
        }
        
        val wasEnabled = socialSpyEnabled.contains(player.uniqueId)
        
        if (wasEnabled) {
            socialSpyEnabled.remove(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("social_spy.disabled", player))
            logger.info("${player.name} disabled social spy")
        } else {
            socialSpyEnabled.add(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("social_spy.enabled", player))
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
            (sender.hasPermission("zchat.socialspy") || recipient.hasPermission("zchat.socialspy"))) {
            return
        }
        
        val spyMessage = messageFormattingService.formatMessage(
            format = configManager.messages.socialSpy.socialSpyFormat,
            player = sender,
            additionalPlaceholders = mapOf(
                "sender" to sender.name,
                "recipient" to recipient.name,
                "message" to message
            ),
            processUrls = false,
            processMentions = false,
            allowColors = true,
            allowFormatting = true
        )
        
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
     * Broadcast a remote private message to all social spy users
     */
    fun broadcastRemotePrivateMessage(senderName: String, recipient: Player, message: String) {
        val config = configManager.config.socialSpy
        
        if (!config.enableSocialSpy || socialSpyEnabled.isEmpty()) {
            return
        }
        
        // Don't spy on staff messages if configured (check recipient only since sender is remote/string)
        if (config.ignoreModerators && recipient.hasPermission("zchat.socialspy")) {
            return
        }
        
        val spyMessage = messageFormattingService.formatMessage(
            format = configManager.messages.socialSpy.socialSpyFormat,
            player = recipient, // Use recipient for placeholder parsing context
            additionalPlaceholders = mapOf(
                "sender" to senderName,
                "recipient" to recipient.name,
                "message" to message
            ),
            processUrls = false,
            processMentions = false,
            allowColors = true,
            allowFormatting = true
        )
        
        // Send to all social spy users
        for (spyPlayerUUID in socialSpyEnabled) {
            val spyPlayer = Bukkit.getPlayer(spyPlayerUUID)
            if (spyPlayer != null && spyPlayer.isOnline) {
                // Don't send spy message to the recipient
                if (spyPlayer.uniqueId != recipient.uniqueId) {
                    spyPlayer.sendMessage(spyMessage)
                }
            }
        }
        
        // Log to console if enabled
        if (config.logToConsole) {
            logger.info("[SOCIALSPY] $senderName -> ${recipient.name}: $message")
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
        if (!player.hasPermission("zchat.socialspy")) {
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
        if (config.ignoreModerators && player.hasPermission("zchat.socialspy")) {
            return
        }
        
        val spyMessage = messageFormattingService.formatMessage(
            format = configManager.messages.socialSpy.commandSpyFormat,
            player = player,
            additionalPlaceholders = mapOf(
                "player" to player.name,
                "command" to command
            ),
            processUrls = false,
            processMentions = false,
            allowColors = true,
            allowFormatting = true
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
    
    /**
     * Broadcast a block/unblock action to social spy users
     */
    fun broadcastBlockAction(player: Player, target: Player, action: String) {
        val config = configManager.config.socialSpy
        
        if (!config.enableSocialSpy || socialSpyEnabled.isEmpty()) {
            return
        }
        
        // Format the block action message
        val actionMessage = when (action) {
            "blocked" -> "<dark_gray>[<red>BLOCK</red>]</dark_gray> <gray>${player.name} blocked ${target.name}</gray>"
            "unblocked" -> "<dark_gray>[<green>UNBLOCK</green>]</dark_gray> <gray>${player.name} unblocked ${target.name}</gray>"
            else -> return
        }
        
        val spyMessage = messageFormattingService.formatMessage(
            format = actionMessage,
            player = player,
            processUrls = true,
            processMentions = false,
            allowColors = true,
            allowFormatting = true
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
            logger.info("[BLOCKSPY] ${player.name} $action ${target.name}")
        }
    }
}