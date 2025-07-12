package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Centralized service for message formatting, placeholder resolution, and MiniMessage processing.
 * This service can be used by ChatFormattingService, PrivateMessageService, and command responses.
 */
class MessageFormattingService(
    private val configManager: ConfigManager,
    private val placeholderAPIService: PlaceholderAPIService
) {
    private val logger = LoggerFactory.getLogger(MessageFormattingService::class.java)
    private val miniMessage = MiniMessage.miniMessage()
    private val urlPattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    private val mentionPattern = Pattern.compile("@(\\w+)")
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    // Built-in placeholder resolvers
    private val builtinPlaceholders = mapOf<String, (Player?) -> String>(
        "player_name" to { it?.name ?: "Unknown" },
        "player_displayname" to { it?.let { player -> 
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.displayName()) 
        } ?: "Unknown" },
        "player_uuid" to { it?.uniqueId?.toString() ?: "Unknown" },
        "world" to { it?.world?.name ?: "Unknown" },
        "world_displayname" to { it?.world?.name ?: "Unknown" }, // Could be enhanced with world aliases
        "server_name" to { Bukkit.getServer().name },
        "server_version" to { Bukkit.getVersion() },
        "server_motd" to { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Bukkit.getServer().motd()) },
        "online_players" to { Bukkit.getOnlinePlayers().size.toString() },
        "max_players" to { Bukkit.getMaxPlayers().toString() },
        "time" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) },
        "date" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) },
        "datetime" to { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) },
        "death_cause" to { "UNKNOWN" }, // Will be overridden by additionalPlaceholders in death events
        "original_message" to { "" }, // Will be overridden by additionalPlaceholders in various events
        "online_players_after_join" to { Bukkit.getOnlinePlayers().size.toString() }, // Will be overridden in join events
        "online_players_after_leave" to { (Bukkit.getOnlinePlayers().size - 1).toString() } // Will be overridden in quit events
    )

    fun formatMessage(
        format: String,
        player: Player? = null,
        additionalPlaceholders: Map<String, String> = emptyMap(),
        processUrls: Boolean = true,
        processMentions: Boolean = true,
        allowColors: Boolean = true,
        allowFormatting: Boolean = true
    ): Component {
        val componentMap = mutableMapOf<String, Component>()
        additionalPlaceholders.forEach { (key, value) ->
            componentMap[key] = legacySerializer.deserialize(value)
        }
        return formatMessageComponent(
            format = format,
            player = player,
            additionalPlaceholders = componentMap,
            processUrls = processUrls,
            processMentions = processMentions,
            allowColors = allowColors,
            allowFormatting = allowFormatting
        )
    }
    /**
     * Format a message with full processing including placeholders, URLs, mentions, and permissions
     */
    fun formatMessageComponent(
        format: String,
        player: Player? = null,
        additionalPlaceholders: Map<String, Component> = emptyMap(),
        processUrls: Boolean = true,
        processMentions: Boolean = true,
        allowColors: Boolean = true,
        allowFormatting: Boolean = true
    ): Component {
        var processedFormat = format
        
        // Convert legacy placeholders to MiniMessage format
        processedFormat = convertLegacyPlaceholders(processedFormat)
        
        // Process URLs if enabled and player has permission
        if (processUrls && player != null && configManager.config.chat.enableUrls && 
            player.hasPermission(configManager.config.permissions.urlPermission)) {
            processedFormat = processUrls(processedFormat)
        }
        
        // Process mentions if enabled and player has permission
        if (processMentions && player != null && configManager.config.chat.enableMentions && 
            player.hasPermission(configManager.config.permissions.mentionPermission)) {
            processedFormat = processMentions(processedFormat)
        }
        
        // Create TagResolver with all placeholders
        val (placeholderResolver, newProcessedFormat) = createPlaceholderResolver(player, additionalPlaceholders, processedFormat)
        
        // Parse with MiniMessage using proper TagResolver
        return try {
            val baseTagResolver = when {
                !allowColors -> TagResolver.resolver(StandardTags.decorations())
                !allowFormatting -> TagResolver.resolver(StandardTags.color())
                else -> TagResolver.standard()
            }
            
            val combinedResolver = TagResolver.resolver(baseTagResolver, placeholderResolver)
            miniMessage.deserialize(newProcessedFormat, combinedResolver)
        } catch (e: Exception) {
            logger.warn("Failed to parse message format: $newProcessedFormat", e)
            miniMessage.deserialize("<gray>$newProcessedFormat</gray>")
        }
    }

    /**
     * Process message content (strip formatting/colors based on permissions)
     */
    fun processMessageContent(player: Player?, message: String): String {
        var processedMessage = message
        val config = configManager.config
        
        // Strip formatting if player doesn't have permission
        if (player != null && (!config.chat.enableTextFormatting || !player.hasPermission(config.permissions.formattingPermission))) {
            processedMessage = stripFormatting(processedMessage)
        }
        
        // Strip colors if player doesn't have permission
        if (player != null && (!config.chat.enableColorCodes || !player.hasPermission(config.permissions.colorPermission))) {
            processedMessage = stripColors(processedMessage)
        }
        
        return processedMessage
    }

    /**
     * Create a TagResolver with all available placeholders
     */
    private fun createPlaceholderResolver(
        player: Player?, 
        additionalPlaceholders: Map<String, Component>,
        format: String
    ): Pair<TagResolver, String> {
        var mutFormat = format
        val resolvers = mutableListOf<TagResolver>()
        
        // Add additional placeholders first (highest priority)
        for ((placeholder, value) in additionalPlaceholders) {
            resolvers.add(Placeholder.component(placeholder, value))
        }
        
        // Add built-in placeholders
        if (configManager.config.placeholders.enableBuiltinPlaceholders) {
            for ((placeholder, resolver) in builtinPlaceholders) {
                resolvers.add(Placeholder.unparsed(placeholder, resolver(player)))
            }
        }
        
        // Add custom placeholders
        for ((placeholder, value) in configManager.config.placeholders.customPlaceholders) {
            resolvers.add(Placeholder.unparsed(placeholder, value))
        }
        
        // Add PlaceholderAPI support if enabled
        if (player != null && placeholderAPIService.isEnabled()) {
            val placeholderAPIResolver = placeholderAPIService.createPlaceholderAPIResolver(player, mutFormat)
            resolvers.add(placeholderAPIResolver.first)
            mutFormat = placeholderAPIResolver.second
        }
        
        return Pair(TagResolver.resolver(resolvers), mutFormat)
    }

    /**
     * Convert legacy {placeholder} format to MiniMessage <placeholder> format
     */
    private fun convertLegacyPlaceholders(text: String): String {
        var result = text
        
        // Convert built-in placeholders
        for (placeholder in builtinPlaceholders.keys) {
            result = result.replace("{$placeholder}", "<$placeholder>")
        }
        
        // Convert custom placeholders
        for (placeholder in configManager.config.placeholders.customPlaceholders.keys) {
            result = result.replace("{$placeholder}", "<$placeholder>")
        }
        
        // Convert common additional placeholders
        result = result
            .replace("{message}", "<message>")
            .replace("{sender}", "<sender>")
            .replace("{recipient}", "<recipient>")
            .replace("{sender_displayname}", "<sender_displayname>")
            .replace("{recipient_displayname}", "<recipient_displayname>")
            .replace("{player}", "<player_name>") // Common alias
        
        return result
    }

    /**
     * Process URLs in message content
     */
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

    /**
     * Process mentions in message content
     */
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
            .replace(Regex("<[^>]*>"), "") // Remove any remaining tags
    }

    /**
     * Get a configurable message with placeholder processing
     */
    fun getConfigMessage(
        messageKey: String,
        player: Player? = null,
        additionalPlaceholders: Map<String, String> = emptyMap()
    ): Component {
        val messages = configManager.config.messages
        val messageText = getMessageByKey(messages, messageKey) ?: "<red>Message not found: $messageKey</red>"
        
        return formatMessage(
            format = messageText,
            player = player,
            additionalPlaceholders = additionalPlaceholders,
            processUrls = false, // Config messages typically don't need URL processing
            processMentions = false, // Config messages typically don't need mention processing
            allowColors = true,
            allowFormatting = true
        )
    }

    /**
     * Helper to get message by key from config
     */
    private fun getMessageByKey(messages: lol.mcplugs.minimessagechatplugin.paper.config.MessagesConfig, key: String): String? {
        return when (key) {
            // Command messages
            "commands.player_only" -> messages.commands.playerOnly
            "commands.no_permission" -> messages.commands.noPermission
            "commands.reload_success" -> messages.commands.reloadSuccess
            "commands.reload_failed" -> messages.commands.reloadFailed
            "commands.player_not_found" -> messages.commands.playerNotFound
            "commands.feature_enabled" -> messages.commands.featureEnabled
            "commands.feature_disabled" -> messages.commands.featureDisabled
            "commands.update_failed" -> messages.commands.updateFailed
            "commands.format_updated" -> messages.commands.formatUpdated
            
            // Private message messages
            "private_messages.system_disabled" -> messages.privateMessages.systemDisabled
            "private_messages.cooldown" -> messages.privateMessages.cooldown
            "private_messages.player_not_found" -> messages.privateMessages.playerNotFound
            "private_messages.self_message" -> messages.privateMessages.selfMessage
            "private_messages.target_messages_disabled" -> messages.privateMessages.targetMessagesDisabled
            "private_messages.no_reply_target" -> messages.privateMessages.noReplyTarget
            "private_messages.reply_target_offline" -> messages.privateMessages.replyTargetOffline
            
            // Chat messages
            "chat.disabled_self" -> messages.chat.disabledSelf
            "chat.formatting_error" -> messages.chat.formattingError
            "chat.cooldown" -> messages.chat.cooldown
            
            // Chat toggle messages
            "chat_toggle.system_disabled" -> messages.chatToggle.systemDisabled
            "chat_toggle.message_toggle_disabled" -> messages.chatToggle.messageToggleDisabled
            "chat_toggle.chat_enabled" -> messages.chatToggle.chatEnabled
            "chat_toggle.chat_disabled" -> messages.chatToggle.chatDisabled
            "chat_toggle.messages_enabled" -> messages.chatToggle.messagesEnabled
            "chat_toggle.messages_disabled" -> messages.chatToggle.messagesDisabled
            
            // Social spy messages
            "social_spy.system_disabled" -> messages.socialSpy.systemDisabled
            "social_spy.no_permission" -> messages.socialSpy.noPermission
            "social_spy.enabled" -> messages.socialSpy.enabled
            "social_spy.disabled" -> messages.socialSpy.disabled
            
            // System messages
            "system.error" -> messages.system.error
            "system.success" -> messages.system.success
            "system.data_cleared" -> messages.system.dataCleared
            "system.invalid_usage" -> messages.system.invalidUsage
            
            else -> null
        }
    }

    /**
     * Reload placeholder cache
     */
    fun reload() {
        placeholderAPIService.reload()
        logger.info("MessageFormattingService reloaded")
    }
}