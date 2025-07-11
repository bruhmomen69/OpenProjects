package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class ChatFormattingService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val logger = LoggerFactory.getLogger(ChatFormattingService::class.java)
    private val chatCooldowns = ConcurrentHashMap<UUID, Long>()

    fun formatMessage(player: Player, message: String): Component {
        val config = configManager.config
        
        // Check cooldown
        if (config.chat.enableCooldown && !player.hasPermission("chatplugin.bypass.cooldown")) {
            val lastMessage = chatCooldowns[player.uniqueId] ?: 0
            val cooldownTime = config.chat.cooldownSeconds * 1000L
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastMessage < cooldownTime) {
                val remainingTime = (cooldownTime - (currentTime - lastMessage)) / 1000.0
                throw ChatCooldownException("You must wait ${String.format("%.1f", remainingTime)} seconds before sending another message!")
            }
            
            chatCooldowns[player.uniqueId] = currentTime
        }
        
        // Process message content using MessageFormattingService
        val processedMessage = messageFormattingService.processMessageContent(player, message)
        
        // Get the appropriate format
        val format = getFormatForPlayer(player)
        
        // Add hover and click actions if enabled
        val enhancedFormat = addInteractiveElements(format, player)
        
        // Use MessageFormattingService to format the final message
        val additionalPlaceholders = mapOf("message" to processedMessage)
        val allowColors = config.chat.enableColorCodes && player.hasPermission(config.permissions.colorPermission)
        val allowFormatting = config.chat.enableTextFormatting && player.hasPermission(config.permissions.formattingPermission)
        
        return try {
            messageFormattingService.formatMessage(
                format = enhancedFormat,
                player = player,
                additionalPlaceholders = additionalPlaceholders,
                processUrls = config.chat.enableUrls && player.hasPermission(config.permissions.urlPermission),
                processMentions = config.chat.enableMentions && player.hasPermission(config.permissions.mentionPermission),
                allowColors = allowColors,
                allowFormatting = allowFormatting
            )
        } catch (e: Exception) {
            logger.warn("Failed to format message for player ${player.name}: $enhancedFormat", e)
            messageFormattingService.formatMessage(
                format = "<gray>[<player_name>]</gray> <white><message></white>",
                player = player,
                additionalPlaceholders = additionalPlaceholders,
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
        }
    }
    
    private fun getFormatForPlayer(player: Player): String {
        val config = configManager.config.chatFormat
        
        // Check format priority
        for (priority in config.formatPriority) {
            when (priority) {
                "permission" -> {
                    if (configManager.config.permissions.usePermissionBasedFormats) {
                        val permissionFormat = findPermissionBasedFormat(player, config)
                        if (permissionFormat != null) return permissionFormat
                    }
                }
                "world" -> {
                    if (config.enableWorldFormats) {
                        val worldFormat = config.worldFormats[player.world.name]
                        if (worldFormat != null) return worldFormat
                    }
                }
                "group" -> {
                    if (config.enableGroupFormats) {
                        val groupFormat = findGroupFormat(player, config.groupFormats)
                        if (groupFormat != null) return groupFormat
                    }
                }
                "default" -> {
                    return config.defaultFormat
                }
            }
        }
        
        return config.defaultFormat
    }
    
    private fun findPermissionBasedFormat(player: Player, config: lol.mcplugs.minimessagechatplugin.paper.config.ChatFormatConfig): String? {
        // Check ranked formats if enabled
        if (config.enableRankedFormats) {
            for (rank in config.rankedFormatPriority) {
                if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$rank")) {
                    return config.groupFormats[rank]
                }
            }
        }
        
        // Check for specific format permissions
        val formatPrefix = configManager.config.permissions.formatPermissionPrefix
        for ((group, format) in config.groupFormats) {
            if (player.hasPermission("$formatPrefix$group")) {
                return format
            }
        }
        
        return null
    }
    
    private fun findGroupFormat(player: Player, groupFormats: Map<String, String>): String? {
        val config = configManager.config.chatFormat
        
        // Check ranked formats if enabled
        if (config.enableRankedFormats) {
            for (rank in config.rankedFormatPriority) {
                if (player.hasPermission("group.$rank") || player.hasPermission(rank)) {
                    return groupFormats[rank]
                }
            }
        }
        
        // Fallback to checking group names directly
        for ((group, format) in groupFormats) {
            if (player.hasPermission("group.$group") || player.hasPermission(group)) {
                return format
            }
        }
        
        return null
    }
    
    
    private fun addInteractiveElements(format: String, player: Player): String {
        val config = configManager.config.chatFormat
        var result = format
        
        // Add hover messages if enabled
        if (config.enableHoverMessages) {
            val playerRank = getPlayerRank(player)
            val hoverMessage = config.hoverMessages[playerRank] ?: config.hoverMessages["default"]
            
            if (hoverMessage != null) {
                // Process hover message with basic placeholder replacement
                val processedHover = hoverMessage.replace("{player_name}", player.name)
                
                // Wrap player_name placeholder with hover
                result = result.replace("<player_name>", 
                    "<hover:show_text:'$processedHover'><player_name></hover>")
            }
        }
        
        // Add click actions if enabled
        if (config.enableClickActions) {
            val playerRank = getPlayerRank(player)
            val clickAction = config.clickActions[playerRank] ?: config.clickActions["default"]
            
            if (clickAction != null) {
                // Process click action with basic placeholder replacement
                val processedClick = clickAction.replace("{player_name}", player.name)
                
                // Wrap player_name placeholder with click action
                if (result.contains("<hover:")) {
                    // If hover is already present, add click inside hover
                    result = result.replace("<hover:show_text:'", "<click:$processedClick><hover:show_text:'")
                    result = result.replace("</hover>", "</hover></click>")
                } else {
                    // Add click action directly
                    result = result.replace("<player_name>", 
                        "<click:$processedClick><player_name></click>")
                }
            }
        }
        
        return result
    }
    
    private fun getPlayerRank(player: Player): String {
        val config = configManager.config.chatFormat
        
        // Check ranked formats if enabled
        if (config.enableRankedFormats) {
            for (rank in config.rankedFormatPriority) {
                if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$rank") ||
                    player.hasPermission("group.$rank") || 
                    player.hasPermission(rank)) {
                    return rank
                }
            }
        }
        
        // Check other group formats
        for ((group, _) in config.groupFormats) {
            if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$group") ||
                player.hasPermission("group.$group") || 
                player.hasPermission(group)) {
                return group
            }
        }
        
        return "default"
    }
    
    
    fun reloadPlaceholders() {
        // Reload MessageFormattingService
        messageFormattingService.reload()
        logger.info("Placeholder cache cleared and MessageFormattingService reloaded")
    }
    
    fun clearCooldown(player: Player) {
        chatCooldowns.remove(player.uniqueId)
    }
    
    fun clearAllCooldowns() {
        chatCooldowns.clear()
    }
}

class ChatCooldownException(message: String) : Exception(message)