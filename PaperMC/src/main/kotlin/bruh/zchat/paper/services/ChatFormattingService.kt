package bruh.zchat.paper.services

import bruh.zchat.paper.config.ChatFormatConfig
import bruh.zchat.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToLong

class ChatFormattingService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val logger = LoggerFactory.getLogger(ChatFormattingService::class.java)
    private val chatCooldowns = ConcurrentHashMap<UUID, Long>()

    fun formatMessage(player: Player, message: String): Component {
        return formatMessageWithComponent(player, Component.text(message))
    }

    /**
     * Returns if the message should be sent.
     * Throws [ChatCooldownException] if the player is on cooldown
     */
    fun applyCooldown(player: Player) {
        val config = configManager.config

        // Check cooldown
        if (config.chat.enableCooldown && !player.hasPermission("zchat.bypass.cooldown")) {
            val lastMessage = chatCooldowns[player.uniqueId] ?: 0
            val cooldownTime = config.chat.cooldownSeconds * 1000L
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastMessage < cooldownTime) {
                val remainingTime = ceil((cooldownTime - (currentTime - lastMessage)) / 1000.0).roundToLong()
                throw ChatCooldownException(
                    remainingTime.toString()
                )
            }

            chatCooldowns[player.uniqueId] = currentTime
        }
    }

    @JvmOverloads
    fun formatMessageWithComponent(
        player: Player,
        messageComponent: Component,
        applyCooldown: Boolean = true
    ): Component {
        val config = configManager.config

        if (applyCooldown) {
            applyCooldown(player)
        }

        // Get the appropriate format
        val format = getFormatForPlayer(player)

        // Add hover and click actions if enabled
        val enhancedFormat = addInteractiveElements(format, player)

        // Use MessageFormattingService to format the final message with the component
        val additionalPlaceholders = mapOf("message" to messageComponent)
        val allowColors = config.chat.enableColorCodes && player.hasPermission(config.permissions.colorPermission)
        val allowFormatting =
            config.chat.enableTextFormatting && player.hasPermission(config.permissions.formattingPermission)

        return try {
            val formattedMessage = messageFormattingService.formatMessageComponent(
                format = enhancedFormat,
                player = player,
                additionalPlaceholders = additionalPlaceholders,
                processUrls = config.chat.enableUrls && player.hasPermission(config.permissions.urlPermission),
                processMentions = config.chat.enableMentions && player.hasPermission(config.permissions.mentionPermission),
                allowColors = allowColors,
                allowFormatting = allowFormatting
            )

            // Apply entire message hover/click if enabled
            if (config.chatFormat.applyInteractiveToEntireMessage) {
                applyEntireMessageInteractive(formattedMessage, player)
            } else {
                formattedMessage
            }
        } catch (e: Exception) {
            logger.warn("Failed to format message for player ${player.name}: $enhancedFormat", e)
            messageFormattingService.formatMessageComponent(
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
        val messages = configManager.messages.chatFormat

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
                        val worldFormat = messages.worldFormats[player.world.name]
                        if (worldFormat != null) return worldFormat
                    }
                }

                "group" -> {
                    if (config.enableGroupFormats) {
                        val groupFormat = findGroupFormat(player, messages.groupFormats)
                        if (groupFormat != null) return groupFormat
                    }
                }

                "default" -> {
                    return messages.defaultFormat
                }
            }
        }

        return messages.defaultFormat
    }

    private fun findPermissionBasedFormat(player: Player, config: ChatFormatConfig): String? {
        // Check ranked formats if enabled
        if (config.enableRankedFormats) {
            val messages = configManager.messages.chatFormat
            for (rank in messages.groupFormatPriority) {
                if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$rank")) {
                    return messages.groupFormats[rank]
                }
            }
        }

        // Check for specific format permissions
        val formatPrefix = configManager.config.permissions.formatPermissionPrefix
        val messages = configManager.messages.chatFormat
        for ((group, format) in messages.groupFormats) {
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
            val messages = configManager.messages.chatFormat
            for (rank in messages.groupFormatPriority) {
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
        val messages = configManager.messages.chatFormat
        var result = format

        // Add hover messages if enabled
        if (config.enableHoverMessages) {
            val playerRank = getPlayerRank(player)
            val hoverMessage = messages.hoverMessages[playerRank] ?: messages.hoverMessages["default"]

            if (hoverMessage != null) {
                // Process hover message with basic placeholder replacement
                val processedHover = hoverMessage.replace("{player_name}", player.name)

                // Wrap player_name placeholder with hover
                result = result.replace(
                    "<player_name>",
                    "<hover:show_text:'$processedHover'><player_name></hover>"
                )
            }
        }

        // Add click actions if enabled
        if (config.enableClickActions) {
            val playerRank = getPlayerRank(player)
            val clickAction = messages.clickActions[playerRank] ?: messages.clickActions["default"]

            if (clickAction != null) {
                // Process click action with basic placeholder replacement
                val processedClick = clickAction.replace("{player_name}", player.name)

                // Parse the click action to get the proper MiniMessage format
                val clickTag = when {
                    processedClick.startsWith("suggest_command:") -> "click:suggest_command:'${
                        processedClick.removePrefix(
                            "suggest_command:"
                        )
                    }'"

                    processedClick.startsWith("run_command:") -> "click:run_command:'${processedClick.removePrefix("run_command:")}'"
                    processedClick.startsWith("open_url:") -> "click:open_url:'${processedClick.removePrefix("open_url:")}'"
                    processedClick.startsWith("copy_to_clipboard:") -> "click:copy_to_clipboard:'${
                        processedClick.removePrefix(
                            "copy_to_clipboard:"
                        )
                    }'"

                    else -> "click:suggest_command:'$processedClick'" // Default to suggest_command
                }

                // Wrap player_name placeholder with click action
                if (result.contains("<hover:")) {
                    // If hover is already present, add click inside hover
                    result = result.replace("<hover:show_text:'", "<$clickTag><hover:show_text:'")
                    result = result.replace("</hover>", "</hover></click>")
                } else {
                    // Add click action directly
                    result = result.replace(
                        "<player_name>",
                        "<$clickTag><player_name></click>"
                    )
                }
            }
        }

        return result
    }

    private fun getPlayerRank(player: Player): String {
        val config = configManager.config.chatFormat

        // Check ranked formats if enabled
        if (config.enableRankedFormats) {
            val messages = configManager.messages.chatFormat
            for (rank in messages.groupFormatPriority) {
                if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$rank") ||
                    player.hasPermission("group.$rank") ||
                    player.hasPermission(rank)
                ) {
                    return rank
                }
            }
        }

        // Check other group formats
        val messages = configManager.messages.chatFormat
        for ((group, _) in messages.groupFormats) {
            if (player.hasPermission("${configManager.config.permissions.formatPermissionPrefix}$group") ||
                player.hasPermission("group.$group") ||
                player.hasPermission(group)
            ) {
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

    /**
     * Applies hover and click events to the entire message while preserving inventory placeholder interactions
     */
    private fun applyEntireMessageInteractive(message: Component, player: Player): Component {
        val config = configManager.config.chatFormat
        val messages = configManager.messages.chatFormat

        // Get player rank for hover/click configuration
        val playerRank = getPlayerRank(player)
        val hoverMessage = messages.hoverMessages[playerRank] ?: messages.hoverMessages["default"]
        val clickAction = messages.clickActions[playerRank] ?: messages.clickActions["default"]

        // If no hover or click is configured, return original message
        if (hoverMessage == null && clickAction == null) {
            return message
        }

        // Create the interactive wrapper
        var wrappedMessage = message

        // Add hover event if configured
        if (config.enableHoverMessages && hoverMessage != null) {
            val processedHover = hoverMessage.replace("{player_name}", player.name)
            val hoverComponent = messageFormattingService.formatMessage(processedHover, player)
            wrappedMessage = wrappedMessage.hoverEvent(HoverEvent.showText(hoverComponent))
        }

        // Add click event if configured
        if (config.enableClickActions && clickAction != null) {
            val processedClick = clickAction.replace("{player_name}", player.name)
            val clickEvent = when {
                processedClick.startsWith("suggest_command:") ->
                    ClickEvent.suggestCommand(processedClick.removePrefix("suggest_command:"))

                processedClick.startsWith("run_command:") ->
                    ClickEvent.runCommand(processedClick.removePrefix("run_command:"))

                processedClick.startsWith("open_url:") ->
                    ClickEvent.openUrl(processedClick.removePrefix("open_url:"))

                processedClick.startsWith("copy_to_clipboard:") ->
                    ClickEvent.copyToClipboard(processedClick.removePrefix("copy_to_clipboard:"))

                else -> ClickEvent.suggestCommand(processedClick) // Default to suggest_command
            }
            wrappedMessage = wrappedMessage.clickEvent(clickEvent)
        }

        return wrappedMessage
    }
}

class ChatCooldownException(message: String) : Exception(message)