package bruh.zchat.paper.commands

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.channel.ChannelService
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.enums.MessageKey
import net.kyori.adventure.text.Component
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.SuggestWith
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
    @CommandPermission("zchat.channel.use.list")
    fun list(actor: BukkitCommandActor) {
        val definitions = channelService.getDefinitions()
        if (definitions.isEmpty()) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_NO_ACTIVE_INSTANCES, actor.asPlayer()))
            return
        }

        val header = messageFormattingService.getConfigMessage(MessageKey.CHANNELS_LIST_HEADER, actor.asPlayer())
        val lines = definitions.map { def ->
            messageFormattingService.getConfigMessage(
                MessageKey.CHANNELS_LIST_FORMAT,
                actor.asPlayer(),
                mapOf(
                    "channel_display_name" to def.displayName,
                    "channel_name_key" to def.nameKey,
                    "commands" to def.commands.joinToString(","),
                    "all_messages" to def.allMessagesToChannel.toString(),
                    "cross_server" to def.crossServerBridge.toString()
                )
            )
        }
        val message = lines.fold(header) { acc, line ->
            acc.append(Component.newline()).append(line)
        }
        actor.reply(message)
    }

    @Subcommand("join")
    @CommandPermission("zchat.channel.use.join")
    fun join(actor: BukkitCommandActor, @SuggestWith(ChannelSuggestionProviders.AvailableChannels::class) name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_NOT_FOUND, player, mapOf("channel_name" to name)))
            return
        }
        
        if (def.requiredPermission.isNotBlank() && !player.hasPermission(def.requiredPermission)) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_NO_PERMISSION_CHANNEL, player, mapOf("channel_display_name" to def.displayName)))
            return
        }
        
        val instance = channelService.resolveInstanceForPlayer(player, def)
        if (instance == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_IDENTIFIER_MISSING, player, mapOf("channel_display_name" to def.displayName)))
            return
        }
        if (channelService.joinChannel(player, def, explicit = true)) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_JOINED, player, mapOf(
                "channel_display_name" to def.displayName,
                "channel_identifier" to instance.identifier
            )))
        } else {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_JOIN_FAILED, player, mapOf("channel_display_name" to def.displayName)))
        }
    }

    @Subcommand("leave")
    @CommandPermission("zchat.channel.use.leave")
    fun leave(actor: BukkitCommandActor, @SuggestWith(ChannelSuggestionProviders.JoinedChannels::class) name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_NOT_FOUND, player, mapOf("channel_name" to name)))
            return
        }
        val stateInstances = channelService.getJoinedInstances(player)
        val target = stateInstances.firstOrNull { it.nameKey == def.nameKey }
        if (target == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_NOT_IN_CHANNEL, player, mapOf("channel_display_name" to def.displayName)))
            return
        }
        if (channelService.leaveChannel(player, target)) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_LEFT, player, mapOf("channel_display_name" to def.displayName)))
        } else {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_LEAVE_FAILED, player, mapOf("channel_display_name" to def.displayName)))
        }
    }

    @Subcommand("focus")
    @CommandPermission("zchat.channel.use.focus")
    fun focus(actor: BukkitCommandActor, @SuggestWith(ChannelSuggestionProviders.JoinedChannels::class) name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_NOT_FOUND, player, mapOf("channel_name" to name)))
            return
        }
        val joined = channelService.getJoinedInstances(player).firstOrNull { it.nameKey == def.nameKey }
        if (joined == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_FOCUS_NOT_JOINED, player, mapOf("channel_display_name" to def.displayName)))
            return
        }
        channelService.setActiveInstance(player, joined)
        actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_ACTIVE_CHANNEL_SET, player, mapOf("channel_display_name" to def.displayName)))
    }

    @Subcommand("who")
    @CommandPermission("zchat.channel.use.who")
    fun who(actor: BukkitCommandActor, @SuggestWith(ChannelSuggestionProviders.AvailableChannels::class) name: String) {
        val player = actor.requirePlayer()
        val def = channelService.getDefinitionByName(name)
        if (def == null) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_CHANNEL_NOT_FOUND, player, mapOf("channel_name" to name)))
            return
        }
        val instances = channelService.getInstancesForDefinition(def.nameKey)
        if (instances.isEmpty()) {
            actor.reply(messageFormattingService.getConfigMessage(MessageKey.CHANNELS_NO_ACTIVE_INSTANCES, player, mapOf("channel_display_name" to def.displayName)))
            return
        }

        val header = messageFormattingService.getConfigMessage(MessageKey.CHANNELS_MEMBERS_LIST_HEADER, player, mapOf("channel_display_name" to def.displayName))
        val instanceLines = instances.map { inst ->
            val viewers = channelService.getViewersForInstance(inst)
            val names = viewers.joinToString(", ") { it.name }
            if (names.isBlank()) {
                messageFormattingService.getConfigMessage(
                    MessageKey.CHANNELS_NO_MEMBERS,
                    player,
                    mapOf(
                        "channel_identifier" to inst.identifier,
                        "member_names" to names
                    )
                )
            } else {
                messageFormattingService.getConfigMessage(
                    MessageKey.CHANNELS_MEMBERS_LIST_INSTANCE,
                    player,
                    mapOf(
                        "channel_identifier" to inst.identifier,
                        "member_names" to names
                    )
                )
            }
        }
        val message = instanceLines.fold(header) { acc, line ->
            acc.append(Component.newline()).append(line)
        }
        actor.reply(message)
    }
}
