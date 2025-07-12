package bruh.zchat.paper.utils

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.MessageFormattingService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Utility class for enhancing messages with hover and click actions.
 */
class MessageEnhancer(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val timeFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    private val legacySerializer = LegacyComponentSerializer.legacySection()

    /**
     * Enhances a message with hover and click actions based on the message type.
     *
     * @param message The base message component
     * @param player The player this message is about
     * @param messageType The type of message (join, leave, death, etc.)
     * @param additionalPlaceholders Additional placeholders to include in hover/click actions
     * @return The enhanced message component
     */
    fun enhanceMessage(
        message: Component,
        player: Player,
        messageType: MessageType,
        additionalPlaceholders: Map<String, String> = emptyMap()
    ): Component {
        val builder = Component.text().append(message)
        
        // Get the appropriate configuration based on message type
        val (hoverEnabled, hoverFormat, clickAction) = when (messageType) {
            MessageType.JOIN -> Triple(
                configManager.config.joinLeave.enableJoinHover,
                configManager.config.joinLeave.joinHoverMessage,
                configManager.config.joinLeave.joinClickAction
            )
            MessageType.LEAVE -> Triple(
                configManager.config.joinLeave.enableLeaveHover,
                configManager.config.joinLeave.leaveHoverMessage,
                configManager.config.joinLeave.leaveClickAction
            )
            MessageType.DEATH -> Triple(
                configManager.config.death.enableHover,
                configManager.config.death.hoverMessage,
                configManager.config.death.clickAction
            )
            MessageType.ADVANCEMENT -> Triple(
                configManager.config.advancement.enableHover,
                configManager.config.advancement.hoverMessage,
                configManager.config.advancement.clickAction
            )
        }

        // Add hover event if enabled
        if (hoverEnabled) {
            val hoverText = formatHoverText(player, hoverFormat, messageType, additionalPlaceholders)
            if (hoverText != null) {
                builder.hoverEvent(HoverEvent.showText(hoverText))
            }
        }

        // Add click action if configured
        if (!clickAction.isNullOrBlank()) {
            val clickEvent = createClickEvent(player, clickAction, messageType, additionalPlaceholders)
            if (clickEvent != null) {
                builder.clickEvent(clickEvent)
            }
        }

        return builder.build()
    }

    /**
     * Formats the hover text with placeholders.
     */
    private fun formatHoverText(
        player: Player,
        format: String,
        messageType: MessageType,
        additionalPlaceholders: Map<String, String>
    ): Component? {
        if (format.isBlank()) return null

        val placeholders = buildPlaceholders(player, messageType, additionalPlaceholders)
        
        return try {
            messageFormattingService.formatMessageComponent(
                format = format,
                player = player,
                additionalPlaceholders = placeholders,
                processUrls = false,
                processMentions = false,
                allowColors = true,
                allowFormatting = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a click event based on the action string.
     */
    private fun createClickEvent(
        player: Player,
        action: String,
        messageType: MessageType,
        additionalPlaceholders: Map<String, String>
    ): ClickEvent? {
        if (action.isBlank()) return null

        val (actionType, actionValue) = when {
            action.startsWith("suggest_command:") -> 
                ClickEvent.Action.SUGGEST_COMMAND to action.substringAfter("suggest_command:")
            action.startsWith("run_command:") -> 
                ClickEvent.Action.RUN_COMMAND to action.substringAfter("run_command:")
            action.startsWith("open_url:") -> 
                ClickEvent.Action.OPEN_URL to action.substringAfter("open_url:")
            action.startsWith("copy_to_clipboard:") -> 
                ClickEvent.Action.COPY_TO_CLIPBOARD to action.substringAfter("copy_to_clipboard:")
            else -> null to null
        } ?: return null

        val placeholders = buildPlaceholders(player, messageType, additionalPlaceholders)
        val processedValue = placeholders.entries.fold(actionValue) { acc, (key, value) ->
            acc?.replace("<$key>", this.legacySerializer.serialize(value))
        }


        return ClickEvent.clickEvent(actionType!!, processedValue!!)
    }

    /**
     * Builds a map of common placeholders.
     */
    private fun buildPlaceholders(
        player: Player,
        messageType: MessageType,
        additionalPlaceholders: Map<String, String>
    ): Map<String, Component> {
        val location = player.location
        val now = Instant.now()
        
        val basePlaceholders: MutableMap<String, Component> = mutableMapOf(
            "player_name" to Component.text(player.name),
            "display_name" to Component.text(player.displayName().toString()),
            "uuid" to Component.text(player.uniqueId.toString()),
            "world" to Component.text(location.world?.name ?: "unknown"),
            "x" to Component.text(location.blockX.toString()),
            "y" to Component.text(location.blockY.toString()),
            "z" to Component.text(location.blockZ.toString()),
            "yaw" to Component.text(location.yaw.toString()),
            "pitch" to Component.text(location.pitch.toString()),
            "time" to Component.text(timeFormatter.format(now)),
            "timestamp" to Component.text(now.epochSecond.toString()),
            "ping" to Component.text(player.ping.toString()),
            // Add more common placeholders as needed
        )

        for (additionalPlaceholder in additionalPlaceholders) {
            basePlaceholders[additionalPlaceholder.key] = legacySerializer.deserialize(additionalPlaceholder.value)
        }
        
        return basePlaceholders
    }

    /**
     * Type of message being enhanced.
     */
    enum class MessageType {
        JOIN, LEAVE, DEATH, ADVANCEMENT
    }
}
