package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing per-player chat toggle functionality
 */
class ChatToggleService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val logger = LoggerFactory.getLogger(ChatToggleService::class.java)

    // Track players who have chat disabled
    private val chatDisabledPlayers = ConcurrentHashMap.newKeySet<UUID>()
    
    // Track players who have private messages disabled
    private val messagesDisabledPlayers = ConcurrentHashMap.newKeySet<UUID>()
    
    /**
     * Toggle chat for a player
     */
    fun toggleChat(player: Player): Boolean {
        val config = configManager.config.chatToggle
        
        if (!config.enableChatToggle) {
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.system_disabled", player))
            return false
        }
        
        val wasDisabled = chatDisabledPlayers.contains(player.uniqueId)
        
        if (wasDisabled) {
            chatDisabledPlayers.remove(player.uniqueId)
            // Also toggle messages if linked
            if (config.linkChatAndMessages) {
                messagesDisabledPlayers.remove(player.uniqueId)
            }
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.chat_enabled", player))
            logger.info("${player.name} enabled their chat")
        } else {
            chatDisabledPlayers.add(player.uniqueId)
            // Also toggle messages if linked
            if (config.linkChatAndMessages) {
                messagesDisabledPlayers.add(player.uniqueId)
            }
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.chat_disabled", player))
            logger.info("${player.name} disabled their chat")
        }
        
        return !wasDisabled // Return new state (true = enabled, false = disabled)
    }
    
    /**
     * Toggle private messages for a player
     */
    fun togglePrivateMessages(player: Player): Boolean {
        val config = configManager.config.chatToggle
        
        if (!config.enableMessageToggle) {
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.message_toggle_disabled", player))
            return false
        }
        
        val wasDisabled = messagesDisabledPlayers.contains(player.uniqueId)
        
        if (wasDisabled) {
            messagesDisabledPlayers.remove(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.messages_enabled", player))
            logger.info("${player.name} enabled their private messages")
        } else {
            messagesDisabledPlayers.add(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("chat_toggle.messages_disabled", player))
            logger.info("${player.name} disabled their private messages")
        }
        
        return !wasDisabled // Return new state (true = enabled, false = disabled)
    }
    
    /**
     * Check if a player can send chat messages
     */
    fun canSendChat(player: Player): Boolean {
        // Staff can always bypass chat toggle
        if (player.hasPermission("chatplugin.bypass.chattoggle")) {
            return true
        }
        
        return !chatDisabledPlayers.contains(player.uniqueId)
    }
    
    /**
     * Check if a player can receive private messages
     */
    fun canReceiveMessages(player: Player): Boolean {
        return !messagesDisabledPlayers.contains(player.uniqueId)
    }
    
    /**
     * Check if a player can send private messages
     */
    fun canSendMessages(player: Player): Boolean {
        // Staff can always bypass message toggle
        if (player.hasPermission("chatplugin.bypass.messagetoggle")) {
            return true
        }
        
        return !messagesDisabledPlayers.contains(player.uniqueId)
    }
    
    /**
     * Get chat status for a player
     */
    fun getChatStatus(player: Player): String {
        val chatEnabled = canSendChat(player)
        val messagesEnabled = canReceiveMessages(player)
        
        return when {
            chatEnabled && messagesEnabled -> "enabled"
            !chatEnabled && !messagesEnabled -> "disabled"
            chatEnabled && !messagesEnabled -> "chat only"
            else -> "messages only"
        }
    }
    
    /**
     * Force enable chat for a player (admin command)
     */
    fun forceEnableChat(player: Player) {
        chatDisabledPlayers.remove(player.uniqueId)
        logger.info("Chat force-enabled for ${player.name}")
    }
    
    /**
     * Force disable chat for a player (admin command)
     */
    fun forceDisableChat(player: Player) {
        chatDisabledPlayers.add(player.uniqueId)
        logger.info("Chat force-disabled for ${player.name}")
    }
    
    /**
     * Force enable messages for a player (admin command)
     */
    fun forceEnableMessages(player: Player) {
        messagesDisabledPlayers.remove(player.uniqueId)
        logger.info("Messages force-enabled for ${player.name}")
    }
    
    /**
     * Force disable messages for a player (admin command)
     */
    fun forceDisableMessages(player: Player) {
        messagesDisabledPlayers.add(player.uniqueId)
        logger.info("Messages force-disabled for ${player.name}")
    }
    
    /**
     * Force enable both chat and messages for a player (admin command)
     */
    fun forceEnableAll(player: Player) {
        chatDisabledPlayers.remove(player.uniqueId)
        messagesDisabledPlayers.remove(player.uniqueId)
        logger.info("Chat and messages force-enabled for ${player.name}")
    }
    
    /**
     * Force disable both chat and messages for a player (admin command)
     */
    fun forceDisableAll(player: Player) {
        chatDisabledPlayers.add(player.uniqueId)
        messagesDisabledPlayers.add(player.uniqueId)
        logger.info("Chat and messages force-disabled for ${player.name}")
    }
    
    /**
     * Handle player quit - clean up tracking
     */
    fun handlePlayerQuit(player: Player) {
        // Note: We don't remove players from disabled sets on quit
        // This preserves their toggle state across reconnections
        // Only remove if persistence is disabled in config
        
        val config = configManager.config.chatToggle
        if (!config.persistToggleState) {
            chatDisabledPlayers.remove(player.uniqueId)
            messagesDisabledPlayers.remove(player.uniqueId)
        }
    }
    
    /**
     * Get statistics about chat toggles
     */
    fun getToggleStats(): Map<String, Int> {
        return mapOf(
            "chat_disabled" to chatDisabledPlayers.size,
            "messages_disabled" to messagesDisabledPlayers.size,
            "total_online" to org.bukkit.Bukkit.getOnlinePlayers().size
        )
    }
    
    /**
     * Clear all toggle states (admin command)
     */
    fun clearAllToggles() {
        val chatCount = chatDisabledPlayers.size
        val messageCount = messagesDisabledPlayers.size
        
        chatDisabledPlayers.clear()
        messagesDisabledPlayers.clear()
        
        logger.info("Cleared $chatCount chat toggles and $messageCount message toggles")
    }
}