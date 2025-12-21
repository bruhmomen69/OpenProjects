package bruh.zchat.paper.commands

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.ChannelService
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.services.ChannelRouting
import bruh.zchat.paper.services.ChannelInstanceKey
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("channel", "channels")
class ChannelCommands(
    private val configManager: ConfigManager,
    private val channelService: ChannelService,
    private val messageFormattingService: MessageFormattingService
) {

    @Subcommand("list")
    fun list(actor: BukkitCommandActor) {
        val definitions = channelService.getDefinitions()
        val lines = buildString {
            append("<gold>Channels:</gold>\n")
            definitions.forEach { def ->
                append("<gray>- <white>${def.displayName}</white> (<yellow>${def.nameKey}</yellow>) cmds=${def.commands.joinToString(",")} allMsg=${def.allMessagesToChannel} cs=${def.crossServerBridge}</gray>\n")
            }
        }
        actor.reply(messageFormattingService.formatMessage(lines, null, processUrls = false, processMentions = false))
    }

    @Subcommand("join")
    @CommandPermission("zchat.channel.use")
    fun join(actor: BukkitCommandActor, name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.formatMessage("<red>Channel not found: $name</red>", player, processUrls = false, processMentions = false))
            return
        }
        val instance = channelService.resolveInstanceForPlayer(player, def)
        if (instance == null) {
            actor.reply(messageFormattingService.formatMessage("<red>Unable to join channel ${def.displayName}: identifier missing.</red>", player, processUrls = false, processMentions = false))
            return
        }
        if (channelService.joinChannel(player, def, explicit = true)) {
            actor.reply(messageFormattingService.formatMessage("<green>Joined channel ${def.displayName} [${instance.identifier}]</green>", player, processUrls = false, processMentions = false))
        } else {
            actor.reply(messageFormattingService.formatMessage("<red>Could not join channel ${def.displayName}</red>", player, processUrls = false, processMentions = false))
        }
    }

    @Subcommand("leave")
    @CommandPermission("zchat.channel.use")
    fun leave(actor: BukkitCommandActor, name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.formatMessage("<red>Channel not found: $name</red>", player, processUrls = false, processMentions = false))
            return
        }
        val stateInstances = channelService.getJoinedInstances(player)
        val target = stateInstances.firstOrNull { it.nameKey == def.nameKey }
        if (target == null) {
            actor.reply(messageFormattingService.formatMessage("<red>You are not in channel ${def.displayName}</red>", player, processUrls = false, processMentions = false))
            return
        }
        if (channelService.leaveChannel(player, target)) {
            actor.reply(messageFormattingService.formatMessage("<yellow>Left channel ${def.displayName}</yellow>", player, processUrls = false, processMentions = false))
        } else {
            actor.reply(messageFormattingService.formatMessage("<red>Failed to leave channel ${def.displayName}</red>", player, processUrls = false, processMentions = false))
        }
    }

    @Subcommand("focus")
    @CommandPermission("zchat.channel.use")
    fun focus(actor: BukkitCommandActor, name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.formatMessage("<red>Channel not found: $name</red>", player, processUrls = false, processMentions = false))
            return
        }
        val joined = channelService.getJoinedInstances(player).firstOrNull { it.nameKey == def.nameKey }
        if (joined == null) {
            actor.reply(messageFormattingService.formatMessage("<red>You are not in channel ${def.displayName}</red>", player, processUrls = false, processMentions = false))
            return
        }
        channelService.setActiveInstance(player, joined)
        actor.reply(messageFormattingService.formatMessage("<green>Active channel set to ${def.displayName}</green>", player, processUrls = false, processMentions = false))
    }

    @Subcommand("who")
    @CommandPermission("zchat.channel.use")
    fun who(actor: BukkitCommandActor, name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.formatMessage("<red>Channel not found: $name</red>", player, processUrls = false, processMentions = false))
            return
        }
        val instances = channelService.getInstancesForDefinition(def.nameKey)
        if (instances.isEmpty()) {
            actor.reply(messageFormattingService.formatMessage("<yellow>No active instances for ${def.displayName}</yellow>", player, processUrls = false, processMentions = false))
            return
        }
        val lines = buildString {
            append("<gold>Members for ${def.displayName}</gold>\n")
            for (inst in instances) {
                val viewers = channelService.getViewersForInstance(inst)
                val names = viewers.joinToString(", ") { it.name }
                append("<gray>- [${inst.identifier}]</gray> <white>${names.ifBlank { "none" }}</white>\n")
            }
        }
        actor.reply(messageFormattingService.formatMessage(lines, player, processUrls = false, processMentions = false))
    }
}
