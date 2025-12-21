package bruh.zchat.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class ChannelsConfig(
    @field:Comment("Use channels at all? Disabled by default as most servers do not need them.")
    val enabled: Boolean = false,

    @field:Comment("When the user is in a channel with `allMessagesToChannel` enabled, disabling sending message to the no-channel global chat?")
    val channelOnly: Boolean = false,

    @field:Comment(
        "Force tab completion placeholder resolution to run on the main thread to avoid async access warnings.\n" +
                "WARNING: This may cause tab completion delay during high server load.\n" +
                "Only enable if you experience async access warnings in console logs.\n" +
                "Default: false (recommended)"
    )
    val forceMainThreadForTabCompletion: Boolean = false,

    @field:Comment("Configure channel auto joining on login.")
    val autoJoin: AutoJoinConfig = AutoJoinConfig(),

    @field:Comment(
        "List of channels in the order that the user should automatically join on login.\n" +
                "Order in order of most privileged to least privileged. For example, admin chat first, and per world chat last.\n" +
                "If multiple channels have the same permission and both have acceptable identifier statuses, the first one will be joined."
    )
    val channels: List<ChannelConfig> = ArrayList(
        listOf(
            ChannelConfig(
                name = "placeholderapi_player_expansion_per_world_example",
                displayName = "%player_world%",
                commands = listOf("worldchat"),
                identifierCreator = "%player_world%",
                requireIdentifierToJoin = true,
                identifierRefreshTicks = 10
            ),
            ChannelConfig()
        )
    ),
)

@ConfigSerializable
data class ChannelConfig(
    @field:Comment("Name of the channel. Must not contain spaces.")
    val name: String = "Example",
    @field:Comment("Display name of the channel. This is the name that will be displayed in the channel list.")
    val displayName: String = "<green>Example</green>",
    @field:Comment(
        "The commands to use to join/leave this channel ingame (eg `sc` to use `/sc` for staff chat).\n" +
                "When using these commands, add text after them to send a message to this channel only (eg `/sc hello this message ones goes to staff chat`."
    )
    val commands: List<String> = listOf("examplechat"),
    @field:Comment(
        "While in this channel, should all messages you send in chat be sent to this channel, or only ones sent with the channel command prefix?\n" +
                "Off (StaffChat) Example: You can only send messages to this channel with `/sc my message here`, assuming `sc` is configured in commands above. \n" +
                "On (per world chat) Example: Every message you sent is sent to the per world chat channel."
    )
    val allMessagesToChannel: Boolean = false,
    @field:Comment("Required permission to join this channel. Leave empty for no permission.")
    val requiredPermission: String = "",
    @field:Comment(
        "Identifier creator for this channel. \n" +
                "This allows you to have multiple instances of a channel, eg one per world (set to `%player_world%` with the PlaceholderAPI `player` expansion to do this. \n" +
                "You can integrate this with any PlaceholderAPI placeholder, meaning you can have per town/per group chats using settings and information from other plugins. \n" +
                "The placeholder parsed identifier is used as the actual channel internally, so two users in the `perworld` channel but with different parsed placeholders in the " +
                "identifier creator (eg %player_world% resolving to `world` for one user and `world_nether` for another) will be in different channels.\n" +
                "Leave empty for no identifier creator. \n" +
                "The identifier is used to identify the channel instance. It is used to identify the channel instance when joining the channel."
    )
    val identifierCreator: String = "",
    @field:Comment("Require the (placeholderapi replaced) identifier to be not blank to join the channel. Disabled if the raw identifier creator is empty.")
    val requireIdentifierToJoin: Boolean = false,
    @field:Comment("Use the cross server messaging features from storage.conf to bridge this across servers. Redis transport is recommended if you use this.")
    val crossServerBridge: Boolean = false,
    @field:Comment(
        "How often to refresh the identifier (in ticks). Set to 0 to disable. This is used to update the identifier when the player joins a new world, for example.\n" +
                "This means that using this timer, users can switch channel identifiers (actual channels) without running any commands or switching channels."
    )
    val identifierRefreshTicks: Int = 20,
    @field:Comment("Default chat format for this channel. This is the format that will be used when the player has no ranks from the list below, or if the player has no ranks at all.")
    val chatFormat: String = "<gray>[<gradient:white:aqua><player_name></gradient>]</gray> <gray><message></gray>",
    @field:Comment("Group based chat formats, to allow people of different ranks to have different formats inside this channel.")
    val groupFormats: List<ChannelChatFormatInstanceConfig> = listOf(ChannelChatFormatInstanceConfig())
)

@ConfigSerializable
data class ChannelChatFormatInstanceConfig(
    @field:Comment(
        "Required rank for this. This works by checking if you have either of these permissions: `<formatPermissionPrefix>.<channel_name>`. \n" +
                "Format Permission Prefix is set in config.conf, and defaults to `zchat.format.`. Therefore, if this is set to `example`, `zchat.format.example` works."
    )
    val requiredPermission: String = "example",
    @field:Comment("Chat format for this rank. This is the format that will be used when the player has this rank.")
    val chatFormat: String = "<gray>[<gradient:white:aqua><player_name></gradient>]</gray> <gray><message></gray>",
)

@ConfigSerializable
data class AutoJoinConfig(
    @field:Comment("Enable auto joining channels on login. Channels are tried (for permission and identifier) in the order they are specified in the channels list.")
    val enabled: Boolean = false,
    @field:Comment("If auto joining is enabled, also enable the auto joining of multiple channels on login?")
    val multiple: Boolean = true
)