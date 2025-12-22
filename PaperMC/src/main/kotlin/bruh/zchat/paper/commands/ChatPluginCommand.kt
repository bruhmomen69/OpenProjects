package bruh.zchat.paper.commands

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.AlertService
import bruh.zchat.paper.services.ChannelCommandService
import bruh.zchat.paper.services.ChatFormattingService
import bruh.zchat.paper.services.MessageFormattingService
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("chatplugin", "zealouschat", "zchat")
class ChatPluginCommands(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val messageFormattingService: MessageFormattingService,
    private val alertService: AlertService,
    private val channelCommandService: ChannelCommandService
) {

    @Subcommand("reload")
    @CommandPermission("zchat.admin.reload")
    fun reload(actor: BukkitCommandActor) {
        val success = configManager.reloadConfig()
        if (success) {
            chatFormattingService.reloadPlaceholders()
            alertService.reload()
            channelCommandService.updateChannelsCommandAlias()
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.COMMANDS_RELOAD_SUCCESS))
        } else {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.COMMANDS_RELOAD_FAILED))
        }
    }

    @Subcommand("info")
    @CommandPermission("zchat.admin.info")
    fun info(actor: BukkitCommandActor) {
        val config = configManager.config
        val messages = configManager.messages
        val message = """
            <gold>===== ZealousChat Info =====</gold>
            <yellow>Default Format:</yellow> <gray>${messages.chatFormat.defaultFormat}</gray>
            <yellow>Group Formats Enabled:</yellow> <gray>${config.chatFormat.enableGroupFormats}</gray>
            <yellow>World Formats Enabled:</yellow> <gray>${config.chatFormat.enableWorldFormats}</gray>
            <yellow>PlaceholderAPI Enabled:</yellow> <gray>${config.placeholders.enablePlaceholderAPI}</gray>
            <yellow>Features:</yellow>
            <gray>  - Colors: ${config.chat.enableColorCodes}</gray>
            <gray>  - Text Formatting: ${config.chat.enableTextFormatting}</gray>
            <gray>  - URLs: ${config.chat.enableUrls}</gray>
            <gray>  - Mentions: ${config.chat.enableMentions}</gray>
            <gray>  - Chat Cooldown: ${config.chat.enableCooldown} (${config.chat.cooldownSeconds}s)</gray>
            <gray>  - Join Messages: ${config.joinLeave.enableJoin}</gray>
            <gray>  - Leave Messages: ${config.joinLeave.enableLeave}</gray>
            <gray>  - Death Messages: ${config.death.enabled}</gray>
            <gray>  - Advancement Messages: ${config.advancement.enabled}</gray>
        """.trimIndent()

        actor.reply(MiniMessage.miniMessage().deserialize(message))
    }

    @Subcommand("test")
    @CommandPermission("zchat.admin.test")
    fun test(actor: BukkitCommandActor, message: String) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.COMMANDS_PLAYER_ONLY))
            return
        }

        val player = actor.sender() as Player
        val formattedMessage = chatFormattingService.formatMessage(player, message)
        actor.reply(MiniMessage.miniMessage().deserialize("<yellow>Test Result:</yellow>"))
        actor.reply(formattedMessage)
    }

    @Command("chatplugin format", "zealouschat format", "zchat format")
    class FormatCommands(
        private val configManager: ConfigManager
    ) {
        @Subcommand("set default")
        @CommandPermission("zchat.admin.format")
        fun setDefault(actor: BukkitCommandActor, format: String) {
            val newMessages = configManager.messages.copy(
                chatFormat = configManager.messages.chatFormat.copy(
                    defaultFormat = format
                )
            )

            if (configManager.updateMessages(newMessages)) {
                actor.reply(
                    MiniMessage.miniMessage().deserialize("<green>Default format updated successfully!</green>")
                )
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("set group")
        @CommandPermission("zchat.admin.format")
        fun setGroup(actor: BukkitCommandActor, groupName: String, format: String) {
            val newGroupFormats = configManager.messages.chatFormat.groupFormats.toMutableMap()
            newGroupFormats[groupName] = format

            val newMessages = configManager.messages.copy(
                chatFormat = configManager.messages.chatFormat.copy(
                    groupFormats = newGroupFormats
                )
            )

            if (configManager.updateMessages(newMessages)) {
                actor.reply(
                    MiniMessage.miniMessage()
                        .deserialize("<green>Group format for '$groupName' updated successfully!</green>")
                )
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("set world")
        @CommandPermission("zchat.admin.format")
        fun setWorld(actor: BukkitCommandActor, worldName: String, format: String) {
            val newWorldFormats = configManager.messages.chatFormat.worldFormats.toMutableMap()
            newWorldFormats[worldName] = format

            val newMessages = configManager.messages.copy(
                chatFormat = configManager.messages.chatFormat.copy(
                    worldFormats = newWorldFormats
                )
            )

            if (configManager.updateMessages(newMessages)) {
                actor.reply(
                    MiniMessage.miniMessage()
                        .deserialize("<green>World format for '$worldName' updated successfully!</green>")
                )
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("list")
        @CommandPermission("zchat.admin.format")
        fun list(actor: BukkitCommandActor) {
            val messages = configManager.messages.chatFormat
            val message = StringBuilder("<gold>===== Chat Formats =====</gold>\n")

            message.append("<yellow>Default:</yellow> <gray>${messages.defaultFormat}</gray>\n")

            if (messages.groupFormats.isNotEmpty()) {
                message.append("<yellow>Group Formats:</yellow>\n")
                messages.groupFormats.forEach { (group, format) ->
                    message.append("<gray>  $group:</gray> <white>$format</white>\n")
                }
            }

            if (messages.worldFormats.isNotEmpty()) {
                message.append("<yellow>World Formats:</yellow>\n")
                messages.worldFormats.forEach { (world, format) ->
                    message.append("<gray>  $world:</gray> <white>$format</white>\n")
                }
            }

            actor.reply(MiniMessage.miniMessage().deserialize(message.toString()))
        }
    }

    @Command("chatplugin toggle", "zealouschat toggle", "zchat toggle")
    class ToggleCommands(
        private val configManager: ConfigManager
    ) {

        @Subcommand("colors")
        @CommandPermission("zchat.admin.toggle")
        fun toggleColors(actor: BukkitCommandActor) {
            val chatConfig = configManager.config.chat
            val newValue = !chatConfig.enableColorCodes
            val newConfig = configManager.config.copy(
                chat = chatConfig.copy(enableColorCodes = newValue)
            )

            if (configManager.updateConfig(newConfig)) {
                val status = if (newValue) "enabled" else "disabled"
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Color codes $status!</green>"))
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("formatting")
        @CommandPermission("zchat.admin.toggle")
        fun toggleFormatting(actor: BukkitCommandActor) {
            val chatConfig = configManager.config.chat
            val newValue = !chatConfig.enableTextFormatting
            val newConfig = configManager.config.copy(
                chat = chatConfig.copy(enableTextFormatting = newValue)
            )

            if (configManager.updateConfig(newConfig)) {
                val status = if (newValue) "enabled" else "disabled"
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Text formatting $status!</green>"))
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("mentions")
        @CommandPermission("zchat.admin.toggle")
        fun toggleMentions(actor: BukkitCommandActor) {
            val chatConfig = configManager.config.chat
            val newValue = !chatConfig.enableMentions
            val newConfig = configManager.config.copy(
                chat = chatConfig.copy(enableMentions = newValue)
            )

            if (configManager.updateConfig(newConfig)) {
                val status = if (newValue) "enabled" else "disabled"
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Player mentions $status!</green>"))
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }

        @Subcommand("cooldown")
        @CommandPermission("zchat.admin.toggle")
        fun toggleCooldown(actor: BukkitCommandActor) {
            val chatConfig = configManager.config.chat
            val newValue = !chatConfig.enableCooldown
            val newConfig = configManager.config.copy(
                chat = chatConfig.copy(enableCooldown = newValue)
            )

            if (configManager.updateConfig(newConfig)) {
                val status = if (newValue) "enabled" else "disabled"
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Chat cooldown $status!</green>"))
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Failed to update configuration!</red>"))
            }
        }
    }
}
