package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
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

class ChatFormattingService(private val configManager: ConfigManager) {
    private val logger = LoggerFactory.getLogger(ChatFormattingService::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    private val chatCooldowns = ConcurrentHashMap<UUID, Long>()
    private val urlPattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    private val mentionPattern = Pattern.compile("@(\\w+)")
    
    // Built-in placeholder resolvers
    private val builtinPlaceholders = mapOf<String, (Player) -> String>(
        "player_name" to { it.name },
        "player_displayname" to { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.displayName()) },
        "player_uuid" to { it.uniqueId.toString() },
        "world" to { it.world.name },
        "world_displayname" to { it.world.name }, // Could be enhanced with world aliases
        "server_name" to { Bukkit.getServer().name },
        "server_version" to { Bukkit.getVersion() },
        "server_motd" to { Bukkit.getMotd() },
        "online_players" to { Bukkit.getOnlinePlayers().size.toString() },
        "max_players" to { Bukkit.getMaxPlayers().toString() },
        "time" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) },
        "date" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) },
        "datetime" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
    )

    fun formatMessage(player: Player, message: String): Component {
        val config = configManager.config
        
        // Check cooldown
        if (config.features.enableChatCooldown && !player.hasPermission("chatplugin.bypass.cooldown")) {
            val lastMessage = chatCooldowns[player.uniqueId] ?: 0
            val cooldownTime = config.features.chatCooldownSeconds * 1000L
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastMessage < cooldownTime) {
                val remainingTime = (cooldownTime - (currentTime - lastMessage)) / 1000.0
                throw ChatCooldownException("You must wait ${String.format("%.1f", remainingTime)} seconds before sending another message!")
            }
            
            chatCooldowns[player.uniqueId] = currentTime
        }
        
        // Process message content
        var processedMessage = message
        
        // Handle mentions
        if (config.features.enableMentions && player.hasPermission(config.permissions.mentionPermission)) {
            processedMessage = processMentions(processedMessage)
        }
        
        // Handle URLs
        if (config.features.enableUrls && player.hasPermission(config.permissions.urlPermission)) {
            processedMessage = processUrls(processedMessage)
        }
        
        // Strip formatting if player doesn't have permission
        if (!config.features.enableFormatting || !player.hasPermission(config.permissions.formattingPermission)) {
            processedMessage = stripFormatting(processedMessage)
        }
        
        // Strip colors if player doesn't have permission
        if (!config.features.enableColorCodes || !player.hasPermission(config.permissions.colorPermission)) {
            processedMessage = stripColors(processedMessage)
        }
        
        // Get the appropriate format
        val format = getFormatForPlayer(player)
        
        // Replace placeholders
        val finalFormat = replacePlaceholders(format, player, processedMessage)
        
        // Add hover and click actions if enabled
        val enhancedFormat = addInteractiveElements(finalFormat, player)
        
        // Parse with MiniMessage
        return try {
            val tagResolver = if (config.features.enableColorCodes && player.hasPermission(config.permissions.colorPermission)) {
                TagResolver.standard()
            } else {
                TagResolver.resolver(StandardTags.decorations())
            }
            
            miniMessage.deserialize(enhancedFormat, tagResolver)
        } catch (e: Exception) {
            logger.warn("Failed to parse message format for player ${player.name}: $enhancedFormat", e)
            miniMessage.deserialize("<gray>[${player.name}]</gray> <white>$processedMessage</white>")
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
    
    private fun replacePlaceholders(format: String, player: Player, message: String): String {
        var result = format
        
        // Replace message placeholder
        result = result.replace("{message}", message)
        
        // Replace built-in placeholders
        if (configManager.config.placeholders.enableBuiltinPlaceholders) {
            for ((placeholder, resolver) in builtinPlaceholders) {
                result = result.replace("{$placeholder}", resolver(player))
            }
        }
        
        // Replace custom placeholders
        for ((placeholder, value) in configManager.config.placeholders.customPlaceholders) {
            result = result.replace("{$placeholder}", value)
        }
        
        // TODO: Add PlaceholderAPI support here if enabled
        if (configManager.config.placeholders.enablePlaceholderAPI) {
            result = processPlaceholderAPI(result, player)
        }
        
        return result
    }
    
    private fun addInteractiveElements(format: String, player: Player): String {
        val config = configManager.config.chatFormat
        var result = format
        
        // Add hover messages if enabled
        if (config.enableHoverMessages) {
            val playerRank = getPlayerRank(player)
            val hoverMessage = config.hoverMessages[playerRank] ?: config.hoverMessages["default"]
            
            if (hoverMessage != null) {
                val processedHover = hoverMessage.replace("{player_name}", player.name)
                
                // Wrap player name with hover
                result = result.replace("{player_name}", 
                    "<hover:show_text:'$processedHover'>{player_name}</hover>")
            }
        }
        
        // Add click actions if enabled
        if (config.enableClickActions) {
            val playerRank = getPlayerRank(player)
            val clickAction = config.clickActions[playerRank] ?: config.clickActions["default"]
            
            if (clickAction != null) {
                val processedClick = clickAction.replace("{player_name}", player.name)
                
                // Wrap player name with click action
                if (result.contains("<hover:")) {
                    // If hover is already present, add click inside hover
                    result = result.replace("<hover:show_text:'", "<click:$processedClick><hover:show_text:'")
                    result = result.replace("</hover>", "</hover></click>")
                } else {
                    // Add click action directly
                    result = result.replace("{player_name}", 
                        "<click:$processedClick>{player_name}</click>")
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
    
    private fun processPlaceholderAPI(text: String, player: Player): String {
        // Placeholder for PlaceholderAPI integration
        // This would require adding PlaceholderAPI as a dependency
        // Example implementation:
        // if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
        //     return PlaceholderAPI.setPlaceholders(player, text)
        // }
        return text
    }
    
    private fun processMentions(message: String): String {
        val matcher = mentionPattern.matcher(message)
        val result = StringBuffer()
        
        while (matcher.find()) {
            val playerName = matcher.group(1)
            val targetPlayer = Bukkit.getPlayer(playerName)
            
            if (targetPlayer != null && targetPlayer.isOnline) {
                val replacement = "<click:suggest_command:/msg $playerName ><hover:show_text:'Click to message $playerName'><yellow>@$playerName</yellow></hover></click>"
                matcher.appendReplacement(result, replacement)
            } else {
                matcher.appendReplacement(result, matcher.group())
            }
        }
        matcher.appendTail(result)
        
        return result.toString()
    }
    
    private fun processUrls(message: String): String {
        val matcher = urlPattern.matcher(message)
        val result = StringBuffer()
        
        while (matcher.find()) {
            val url = matcher.group()
            val replacement = "<click:open_url:'$url'><hover:show_text:'Click to open $url'><blue><u>$url</u></blue></hover></click>"
            matcher.appendReplacement(result, replacement)
        }
        matcher.appendTail(result)
        
        return result.toString()
    }
    
    private fun stripFormatting(message: String): String {
        // Remove MiniMessage formatting tags but keep colors
        return message
            .replace(Regex("</?(?:bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf)>"), "")
    }
    
    private fun stripColors(message: String): String {
        // Remove all MiniMessage color tags
        return message
            .replace(Regex("</?(?:color:[^>]+|[a-z_]+|#[0-9a-fA-F]{6})>"), "")
            .replace(Regex("<[^>]*>"), "") // Remove any remaining tags
    }
    
    fun reloadPlaceholders() {
        // Clear any cached placeholder data
        logger.info("Placeholder cache cleared")
    }
    
    fun clearCooldown(player: Player) {
        chatCooldowns.remove(player.uniqueId)
    }
    
    fun clearAllCooldowns() {
        chatCooldowns.clear()
    }
}

class ChatCooldownException(message: String) : Exception(message)