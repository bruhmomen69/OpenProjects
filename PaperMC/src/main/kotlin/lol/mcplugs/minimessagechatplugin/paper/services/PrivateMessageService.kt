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
 * Service for handling private messages between players with MiniMessage formatting
 */
class PrivateMessageService(
    private val configManager: ConfigManager,
    private val placeholderAPIService: PlaceholderAPIService,
    private val chatToggleService: ChatToggleService,
    private val socialSpyService: SocialSpyService
) {
    private val logger = LoggerFactory.getLogger(PrivateMessageService::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    
    // Track last message senders for reply functionality
    private val lastSenders = ConcurrentHashMap<UUID, UUID>()
    
    // Track message cooldowns
    private val messageCooldowns = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Send a private message from one player to another
     */
    fun sendPrivateMessage(sender: Player, recipientName: String, message: String): Boolean {
        val config = configManager.config.privateMessages
        
        // Check if private messages are enabled
        if (!config.enablePrivateMessages) {
            sender.sendMessage(miniMessage.deserialize("<red>Private messages are currently disabled.</red>"))
            return false
        }
        
        // Check cooldown
        if (!sender.hasPermission("chatplugin.bypass.cooldown") && config.enableMessageCooldown) {
            val lastMessage = messageCooldowns[sender.uniqueId] ?: 0
            val cooldownTime = config.messageCooldownSeconds * 1000L
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastMessage < cooldownTime) {
                val remainingTime = (cooldownTime - (currentTime - lastMessage)) / 1000.0
                sender.sendMessage(miniMessage.deserialize("<red>You must wait ${String.format("%.1f", remainingTime)} seconds before sending another message!</red>"))
                return false
            }
            
            messageCooldowns[sender.uniqueId] = currentTime
        }
        
        // Find recipient
        val recipient = Bukkit.getPlayer(recipientName)
        if (recipient == null) {
            sender.sendMessage(miniMessage.deserialize(config.playerNotFoundMessage.replace("{player}", recipientName)))
            return false
        }
        
        // Check if recipient is the same as sender
        if (recipient.uniqueId == sender.uniqueId) {
            sender.sendMessage(miniMessage.deserialize("<red>You cannot send a message to yourself!</red>"))
            return false
        }
        
        // Check if recipient has messages disabled
        if (!chatToggleService.canReceiveMessages(recipient)) {
            sender.sendMessage(miniMessage.deserialize(config.messagesDisabledMessage.replace("{player}", recipient.name)))
            return false
        }
        
        // Process message content
        val processedMessage = processMessageContent(sender, message)
        
        // Create formatted messages
        val senderMessage = createFormattedMessage(config.senderFormat, sender, recipient, processedMessage)
        val recipientMessage = createFormattedMessage(config.recipientFormat, sender, recipient, processedMessage)
        
        // Send messages
        sender.sendMessage(senderMessage)
        recipient.sendMessage(recipientMessage)
        
        // Update last sender for reply functionality
        lastSenders[recipient.uniqueId] = sender.uniqueId
        
        // Log message if enabled
        if (config.enableMessageLogging) {
            logger.info("[MSG] ${sender.name} -> ${recipient.name}: $message")
        }
        
        // Send to social spy if enabled
        socialSpyService.broadcastPrivateMessage(sender, recipient, message)
        
        return true
    }
    
    /**
     * Reply to the last person who sent a message
     */
    fun replyToLastSender(sender: Player, message: String): Boolean {
        val lastSenderUUID = lastSenders[sender.uniqueId]
        if (lastSenderUUID == null) {
            sender.sendMessage(miniMessage.deserialize("<red>No one has sent you a message to reply to!</red>"))
            return false
        }
        
        val lastSender = Bukkit.getPlayer(lastSenderUUID)
        if (lastSender == null) {
            sender.sendMessage(miniMessage.deserialize("<red>The player you're trying to reply to is no longer online!</red>"))
            lastSenders.remove(sender.uniqueId)
            return false
        }
        
        return sendPrivateMessage(sender, lastSender.name, message)
    }
    
    /**
     * Process message content (handle colors, formatting, etc.)
     */
    private fun processMessageContent(sender: Player, message: String): String {
        var processedMessage = message
        val config = configManager.config
        
        // Strip formatting if player doesn't have permission
        if (!config.features.enableFormatting || !sender.hasPermission(config.permissions.formattingPermission)) {
            processedMessage = stripFormatting(processedMessage)
        }
        
        // Strip colors if player doesn't have permission
        if (!config.features.enableColorCodes || !sender.hasPermission(config.permissions.colorPermission)) {
            processedMessage = stripColors(processedMessage)
        }
        
        return processedMessage
    }
    
    /**
     * Create a formatted message using MiniMessage
     */
    private fun createFormattedMessage(format: String, sender: Player, recipient: Player, message: String): Component {
        // Create placeholder resolver
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
            logger.warn("Failed to parse private message format: $format", e)
            miniMessage.deserialize("<gray>[${sender.name} -> ${recipient.name}]</gray> <white>$message</white>")
        }
    }
    
    
    /**
     * Strip MiniMessage formatting tags
     */
    private fun stripFormatting(message: String): String {
        return message.replace(Regex("</?(?:bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf)>"), "")
    }
    
    /**
     * Strip MiniMessage color tags
     */
    private fun stripColors(message: String): String {
        return message
            .replace(Regex("</?(?:color:[^>]+|[a-z_]+|#[0-9a-fA-F]{6})>"), "")
            .replace(Regex("<[^>]*>"), "")
    }
    
    /**
     * Clear message cooldown for a player
     */
    fun clearCooldown(player: Player) {
        messageCooldowns.remove(player.uniqueId)
    }
    
    /**
     * Clear all message cooldowns
     */
    fun clearAllCooldowns() {
        messageCooldowns.clear()
    }
    
    /**
     * Remove last sender tracking when player leaves
     */
    fun handlePlayerQuit(player: Player) {
        lastSenders.remove(player.uniqueId)
        messageCooldowns.remove(player.uniqueId)
        
        // Remove this player as a last sender for others
        lastSenders.values.removeAll { it == player.uniqueId }
    }
    
    /**
     * Get the last sender for a player (for debugging/info)
     */
    fun getLastSender(player: Player): Player? {
        val lastSenderUUID = lastSenders[player.uniqueId] ?: return null
        return Bukkit.getPlayer(lastSenderUUID)
    }
}