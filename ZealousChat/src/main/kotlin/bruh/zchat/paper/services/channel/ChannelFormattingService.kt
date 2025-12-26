package bruh.zchat.paper.services.channel

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.MessageFormattingService
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Formats chat messages for channels using channel-specific formats.
 */
class ChannelFormattingService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val channelService: ChannelService
) {

    fun formatChannelMessage(
        sender: Player?,
        viewer: Audience,
        baseMessageComponent: Component,
        routing: ChannelRouting,
        senderNameOverride: String? = null
    ): Component {
        val instance = routing.instance ?: return baseMessageComponent
        val definition = channelService.getDefinitionByName(instance.nameKey)
            ?: return baseMessageComponent

        val format = resolveFormatForSender(sender, definition)
        val placeholders = mutableMapOf<String, Component>()
        placeholders["message"] = baseMessageComponent

        val stringPlaceholders = mapOf(
            "channel_name" to definition.name,
            "channel_identifier" to instance.identifier,
            "channel_display_name" to definition.displayName
        )

        val allowColors = sender?.let { configManager.config.chat.enableColorCodes && it.hasPermission(configManager.config.permissions.colorPermission) } ?: true
        val allowFormatting = sender?.let { configManager.config.chat.enableTextFormatting && it.hasPermission(configManager.config.permissions.formattingPermission) } ?: true
        val playerForPlaceholders = sender

        return messageFormattingService.formatMessageComponent(
            format = format,
            player = playerForPlaceholders,
            stringPlaceholders = stringPlaceholders + mapOf("sender" to (senderNameOverride ?: sender?.name ?: "Unknown")),
            componentPlaceholders = placeholders,
            processUrls = sender?.let { configManager.config.chat.enableUrls && it.hasPermission(configManager.config.permissions.urlPermission) } ?: false,
            processMentions = sender?.let { configManager.config.chat.enableMentions && it.hasPermission(configManager.config.permissions.mentionPermission) } ?: false,
            allowColors = allowColors,
            allowFormatting = allowFormatting
        )
    }

    private fun resolveFormatForSender(sender: Player?, definition: ChannelDefinition): String {
        // Check ranked/group formats first based on requiredPermission matching permission prefix.
        val prefix = configManager.config.permissions.formatPermissionPrefix
        definition.groupFormats.forEach { cfg ->
            val perm = cfg.requiredPermission
            if (sender != null && perm.isNotBlank() && (sender.hasPermission("$prefix$perm") || sender.hasPermission(perm))) {
                return cfg.chatFormat
            }
        }
        return definition.chatFormat
    }
}
