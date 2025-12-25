package bruh.zchat.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class GeneralChannelSettings(
    @field:Comment("Use channels at all? Disabled by default as most servers do not need them.\n" +
            "WARNING: You must reboot your server for changes to this setting to take effect.")
    val enabled: Boolean = false,

    @field:Comment(
        "Enable full tab completion for per-channel commands."
    )
    val enableFullTabCompletion: Boolean = true,
)

@ConfigSerializable
data class ChannelsConfig(
    @field:Comment("General channel settings")
    val settings: GeneralChannelSettings = GeneralChannelSettings(),

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
        """
        The commands to interact with this channel (e.g., `sc` for `/sc` staff chat).

        Behavior:
        - Without arguments: Toggles channel membership (join/leave)
        - With arguments: Sends message to channel without changing membership

        Active Channel Rules:
        1. Players can be in multiple channels but only one active at a time
        2. Messages go to active channel when allMessagesToChannel=true
        3. Active channel is automatically set when:
           - First joining a channel with allMessagesToChannel=true
           - During auto-join on login
        4. Players can change active channel using channel commands
        
        Players can also switch their active channel channel using `/channel focus <channel_name>`.
        """
    )
    val commands: List<String> = listOf("examplechat"),
    @field:Comment(
        "While in this channel, should all messages you send in chat be sent to this channel, or only ones sent with the channel command prefix?\n" +
                "Off (StaffChat) Example: You can only send messages to this channel with `/sc my message here`, assuming `sc` is configured in commands above. \n" +
                "On (per world chat) Example: Every message you sent is sent to the per world chat channel."
    )
    val allMessagesToChannel: Boolean = false,
    @field:Comment(
        "When sending a message to this channel using a command (e.g., `/sc message here`), should this channel automatically become your active channel?\n" +
                "If enabled, sending a message to this channel will set it as your active channel, meaning subsequent messages (if allMessagesToChannel is enabled) will go to this channel.\n" +
                "This only affects message sending via commands, not channel toggle behavior.\n" +
                "Default: false"
    )
    val autoFocusOnMessage: Boolean = false,
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
    @field:Comment("Use the cross server messaging features from storage.conf to bridge this across servers. \n" +
            "Redis transport is required to be setup for cross server messaging in storage.conf if you use this.")
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
    val multiple: Boolean = true,
    @field:Comment("If enabled, automatically set an active channel when auto-joining. If disabled, players must manually set their active channel (e.g., via /channel focus).")
    val setActiveOnJoin: Boolean = false
)