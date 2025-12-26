package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.database.PlayerDataManager
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Service for managing per-player chat toggle functionality
 */
class ChatToggleService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val playerDataManager: PlayerDataManager
) {
    private val logger = LoggerFactory.getLogger(ChatToggleService::class.java)
    
    /**
     * Toggle chat for a player
     */
    fun toggleChat(player: Player): Boolean {
        val config = configManager.config.chatToggle
        
        if (!config.enableChatToggle) {
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_SYSTEM_DISABLED, player))
            return false
        }
        
        val wasDisabled = playerDataManager.isChatDisabledOnline(player.uniqueId)
        
        if (wasDisabled) {
            playerDataManager.setChatDisabledCached(player.uniqueId, false)
            // Also toggle messages if linked
            if (config.linkChatAndMessages) {
                playerDataManager.setMessagesDisabledCached(player.uniqueId, false)
            }
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_CHAT_ENABLED, player))
            logger.info("${player.name} enabled their chat")
        } else {
            playerDataManager.setChatDisabledCached(player.uniqueId, true)
            // Also toggle messages if linked
            if (config.linkChatAndMessages) {
                playerDataManager.setMessagesDisabledCached(player.uniqueId, true)
            }
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_CHAT_DISABLED, player))
            logger.info("${player.name} disabled their chat")
        }

        plugin.launch(Dispatchers.IO) {
            playerDataManager.setChatDisabled(player.uniqueId, !wasDisabled)
            if (config.linkChatAndMessages) {
                playerDataManager.setMessagesDisabled(player.uniqueId, !wasDisabled)
            }
        }
        
        return !wasDisabled // Return new state (true = enabled, false = disabled)
    }
    
    /**
     * Toggle private messages for a player
     */
    fun togglePrivateMessages(player: Player): Boolean {
        val config = configManager.config.chatToggle
        
        if (!config.enableMessageToggle) {
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_MESSAGE_TOGGLE_DISABLED, player))
            return false
        }
        
        val wasDisabled = playerDataManager.isMessagesDisabledOnline(player.uniqueId)
        
        if (wasDisabled) {
            playerDataManager.setMessagesDisabledCached(player.uniqueId, false)
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_MESSAGES_ENABLED, player))
            logger.info("${player.name} enabled their private messages")
        } else {
            playerDataManager.setMessagesDisabledCached(player.uniqueId, true)
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_TOGGLE_MESSAGES_DISABLED, player))
            logger.info("${player.name} disabled their private messages")
        }

        plugin.launch(Dispatchers.IO) {
            playerDataManager.setMessagesDisabled(player.uniqueId, !wasDisabled)
        }
        
        return !wasDisabled // Return new state (true = enabled, false = disabled)
    }
    
    /**
     * Check if a player can send chat messages
     */
    fun canSendChat(player: Player): Boolean {
        if (!configManager.config.chatToggle.enableChatToggle) {
            return true
        }

        // Check global toggle first
        if (configManager.config.chatToggle.globalChatDisabled) {
            if (player.hasPermission("zchat.bypass.globalchat")) {
                return true
            }
            return false
        }

        // Staff can always bypass personal chat toggle
        if (player.hasPermission("zchat.bypass.chattoggle")) {
            return true
        }

        return !playerDataManager.isChatDisabledOnline(player.uniqueId)
    }
    
    /**
     * Check if a player can receive private messages
     */
    fun canReceiveMessages(player: Player): Boolean {
        if (!configManager.config.chatToggle.enableMessageToggle) {
            return true
        }

        return !playerDataManager.isMessagesDisabledOnline(player.uniqueId)
    }
    
    /**
     * Check if a player can send private messages
     */
    fun canSendMessages(player: Player): Boolean {
        if (!configManager.config.chatToggle.enableMessageToggle) {
            return true
        }

        // Check global toggle first
        if (configManager.config.chatToggle.globalMessagesDisabled) {
            if (player.hasPermission("zchat.bypass.globalmessages")) {
                return true
            }
            return false
        }

        // Staff can always bypass personal message toggle
        if (player.hasPermission("zchat.bypass.messagetoggle")) {
            return true
        }

        return !playerDataManager.isMessagesDisabledOnline(player.uniqueId)
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
        playerDataManager.setChatDisabledCached(player.uniqueId, false)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setChatDisabled(player.uniqueId, false)
            logger.info("Chat force-enabled for ${player.name}")
        }
    }
    
    /**
     * Force disable chat for a player (admin command)
     */
    fun forceDisableChat(player: Player) {
        playerDataManager.setChatDisabledCached(player.uniqueId, true)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setChatDisabled(player.uniqueId, true)
            logger.info("Chat force-disabled for ${player.name}")
        }
    }
    
    /**
     * Force enable messages for a player (admin command)
     */
    fun forceEnableMessages(player: Player) {
        playerDataManager.setMessagesDisabledCached(player.uniqueId, false)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setMessagesDisabled(player.uniqueId, false)
            logger.info("Messages force-enabled for ${player.name}")
        }
    }
    
    /**
     * Force disable messages for a player (admin command)
     */
    fun forceDisableMessages(player: Player) {
        playerDataManager.setMessagesDisabledCached(player.uniqueId, true)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setMessagesDisabled(player.uniqueId, true)
            logger.info("Messages force-disabled for ${player.name}")
        }
    }
    
    /**
     * Force enable both chat and messages for a player (admin command)
     */
    fun forceEnableAll(player: Player) {
        playerDataManager.setChatDisabledCached(player.uniqueId, false)
        playerDataManager.setMessagesDisabledCached(player.uniqueId, false)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setChatDisabled(player.uniqueId, false)
            playerDataManager.setMessagesDisabled(player.uniqueId, false)
            logger.info("Chat and messages force-enabled for ${player.name}")
        }
    }
    
    /**
     * Force disable both chat and messages for a player (admin command)
     */
    fun forceDisableAll(player: Player) {
        playerDataManager.setChatDisabledCached(player.uniqueId, true)
        playerDataManager.setMessagesDisabledCached(player.uniqueId, true)
        plugin.launch(Dispatchers.IO) {
            playerDataManager.setChatDisabled(player.uniqueId, true)
            playerDataManager.setMessagesDisabled(player.uniqueId, true)
            logger.info("Chat and messages force-disabled for ${player.name}")
        }
    }
    
    /**
     * Check if global chat is disabled
     */
    fun isGlobalChatDisabled(): Boolean {
        return configManager.config.chatToggle.globalChatDisabled
    }

    /**
     * Check if global messages are disabled
     */
    fun isGlobalMessagesDisabled(): Boolean {
        return configManager.config.chatToggle.globalMessagesDisabled
    }

    /**
     * Toggle global chat (admin command)
     */
    fun toggleGlobalChat(): Boolean {
        val newState = !configManager.config.chatToggle.globalChatDisabled
        val newConfig = configManager.config.copy(
            chatToggle = configManager.config.chatToggle.copy(globalChatDisabled = newState)
        )
        return configManager.updateConfig(newConfig)
    }

    /**
     * Toggle global messages (admin command)
     */
    fun toggleGlobalMessages(): Boolean {
        val newState = !configManager.config.chatToggle.globalMessagesDisabled
        val newConfig = configManager.config.copy(
            chatToggle = configManager.config.chatToggle.copy(globalMessagesDisabled = newState)
        )
        return configManager.updateConfig(newConfig)
    }

    /**
     * Toggle both global chat and messages (admin command)
     */
    fun toggleGlobalBoth(): Boolean {
        val newChatState = !configManager.config.chatToggle.globalChatDisabled
        val newMessageState = !configManager.config.chatToggle.globalMessagesDisabled
        val newConfig = configManager.config.copy(
            chatToggle = configManager.config.chatToggle.copy(
                globalChatDisabled = newChatState,
                globalMessagesDisabled = newMessageState
            )
        )
        return configManager.updateConfig(newConfig)
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
            playerDataManager.setChatDisabledCached(player.uniqueId, false)
            playerDataManager.setMessagesDisabledCached(player.uniqueId, false)

            plugin.launch(Dispatchers.IO) {
                playerDataManager.setChatDisabled(player.uniqueId, false)
                playerDataManager.setMessagesDisabled(player.uniqueId, false)
            }
        }
    }
    
    /**
     * Get statistics about chat toggles
     */
    fun getToggleStats(): Map<String, Int> {
        val online = org.bukkit.Bukkit.getOnlinePlayers()
        val chatDisabledCount = online.count { !canSendChat(it) }
        val messageDisabledCount = online.count { !canReceiveMessages(it) }
        return mapOf(
            "chat_disabled" to chatDisabledCount,
            "messages_disabled" to messageDisabledCount,
            "total_online" to online.size
        )
    }

    /**
     * Clear all toggle states (admin command)
     */
    fun clearAllToggles() {
        plugin.launch(Dispatchers.IO) {
            playerDataManager.clearAllToggleStates()
            logger.info("Cleared all chat toggles and message toggles")
        }
    }
}