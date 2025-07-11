package lol.mcplugs.minimessagechatplugin.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class Config(
    @field:Comment("Chat formatting configuration including rank-based formats, world formats, and interactive elements")
    val chatFormat: ChatFormatConfig = ChatFormatConfig(),
    
    @field:Comment("Placeholder configuration for built-in placeholders, custom placeholders, and PlaceholderAPI integration")
    val placeholders: PlaceholderConfig = PlaceholderConfig(),
    
    @field:Comment("Permission configuration for format selection and feature access control")
    val permissions: PermissionConfig = PermissionConfig(),
    
    @field:Comment("Feature toggles and message configuration for chat, join/leave, death, and other server messages")
    val features: FeatureConfig = FeatureConfig(),
    
    @field:Comment("Private messaging system configuration including formats, cooldowns, and permissions")
    val privateMessages: PrivateMessageConfig = PrivateMessageConfig(),
    
    @field:Comment("Chat toggle system allowing players to disable chat and private messages")
    val chatToggle: ChatToggleConfig = ChatToggleConfig(),
    
    @field:Comment("Social spy system for moderators to monitor private messages and commands")
    val socialSpy: SocialSpyConfig = SocialSpyConfig(),
    
    @field:Comment("All configurable messages used throughout the plugin. Supports MiniMessage formatting and placeholders.")
    val messages: MessagesConfig = MessagesConfig()
)

@ConfigSerializable
data class ChatFormatConfig(
    @field:Comment("Default chat format used when no group or world format applies. Supports MiniMessage syntax and placeholders.")
    val defaultFormat: String = "<gray>[<white><player_name></white>]</gray> <gray><message></gray>",
    
    @field:Comment("Group-based chat formats mapped by group/rank name. Higher priority groups should be listed first in rankedFormatPriority.")
    val groupFormats: Map<String, String> = mapOf(
        "owner" to "<gradient:red:gold>[OWNER]</gradient> <gradient:gold:yellow><player_name></gradient> <gray>»</gray> <white><message></white>",
        "admin" to "<red>[ADMIN]</red> <gold><player_name></gold> <gray>»</gray> <white><message></white>",
        "moderator" to "<blue>[MOD]</blue> <yellow><player_name></yellow> <gray>»</gray> <white><message></white>",
        "helper" to "<green>[HELPER]</green> <lime><player_name></lime> <gray>»</gray> <white><message></white>",
        "vip" to "<green>[VIP]</green> <aqua><player_name></aqua> <gray>»</gray> <white><message></white>",
        "premium" to "<gold>[PREMIUM]</gold> <yellow><player_name></yellow> <gray>»</gray> <white><message></white>",
        "donor" to "<light_purple>[DONOR]</light_purple> <pink><player_name></pink> <gray>»</gray> <white><message></white>",
        "member" to "<gray>[MEMBER]</gray> <white><player_name></white> <gray>»</gray> <gray><message></gray>",
        "default" to "<gray>[<white><player_name></white>]</gray> <gray><message></gray>"
    ),
    
    @field:Comment("World-specific chat formats mapped by world name. Useful for different gamemodes or themed worlds.")
    val worldFormats: Map<String, String> = mapOf(
        "world" to "<green>[Overworld]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_nether" to "<red>[Nether]</red> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_the_end" to "<dark_purple>[The End]</dark_purple> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "creative" to "<yellow>[Creative]</yellow> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "survival" to "<green>[Survival]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>"
    ),
    
    @field:Comment("Enable or disable group-based chat formats. When disabled, only default format is used.")
    val enableGroupFormats: Boolean = true,
    
    @field:Comment("Enable or disable world-specific chat formats. When disabled, world formats are ignored.")
    val enableWorldFormats: Boolean = false,
    
    @field:Comment("Priority order for format selection. Options: 'permission', 'world', 'group', 'default'. First match wins.")
    val formatPriority: List<String> = listOf("permission", "world", "group", "default"),
    
    @field:Comment("Enable ranked format system with automatic priority based on rankedFormatPriority list.")
    val enableRankedFormats: Boolean = true,
    
    @field:Comment("Priority order for ranked formats. Higher ranks should be listed first. Only used when enableRankedFormats is true.")
    val rankedFormatPriority: List<String> = listOf("owner", "admin", "moderator", "helper", "vip", "premium", "donor", "member", "default"),
    
    @field:Comment("Enable hover messages when players hover over names in chat. Messages are defined in hoverMessages map.")
    val enableHoverMessages: Boolean = true,
    
    @field:Comment("Custom hover messages shown when hovering over player names, mapped by rank/group. Supports MiniMessage and \\n for newlines.")
    val hoverMessages: Map<String, String> = mapOf(
        "admin" to "<red>Administrator</red>\n<gray>Click to message</gray>",
        "moderator" to "<blue>Moderator</blue>\n<gray>Click to message</gray>",
        "vip" to "<green>VIP Member</green>\n<gray>Click to message</gray>",
        "default" to "<gray>Player</gray>\n<gray>Click to message</gray>"
    ),
    
    @field:Comment("Enable click actions when players click on names in chat. Actions are defined in clickActions map.")
    val enableClickActions: Boolean = true,
    
    @field:Comment("Custom click actions for player names, mapped by rank/group. Supports: suggest_command, run_command, open_url, copy_to_clipboard")
    val clickActions: Map<String, String> = mapOf(
        "default" to "suggest_command:/msg <player_name> "
    )
)

@ConfigSerializable
data class PlaceholderConfig(
    @field:Comment("Enable built-in placeholders like <player_name>, <max_players>, <time>, etc.")
    val enableBuiltinPlaceholders: Boolean = true,
    
    @field:Comment("Custom server-specific placeholders. Use {placeholder_name} in formats to reference these values.")
    val customPlaceholders: Map<String, String> = mapOf(
        "server_name" to "My Server",
        "website" to "example.com"
    ),
    
    @field:Comment("""
        Enable PlaceholderAPI integration for external plugin placeholders.
        
        Requirements:
        - PlaceholderAPI plugin must be installed on the server
        - This plugin will automatically detect PlaceholderAPI availability
        
        Usage in chat formats:
        - Use standard PlaceholderAPI syntax: %plugin_placeholder%
        - Example: "%player_level% %vault_rank% %luckperms_prefix%"
        - Placeholders are automatically converted to MiniMessage format
        
        Supported PlaceholderAPI features:
        - All registered PlaceholderAPI expansions
        - Player-specific placeholders
        - Server-wide placeholders
        - Custom expansion placeholders
        
        Performance:
        - Placeholders are cached and processed efficiently
        - Failed placeholders fallback gracefully
        - Timeout protection prevents server lag
    """)
    val enablePlaceholderAPI: Boolean = true,
    
    @field:Comment("Timeout in milliseconds for PlaceholderAPI placeholder resolution to prevent server lag.")
    val placeholderAPITimeout: Long = 1000L
)

@ConfigSerializable
data class PermissionConfig(
    @field:Comment("Enable permission-based format selection. When true, players need specific permissions to use group formats.")
    val usePermissionBasedFormats: Boolean = true,
    
    @field:Comment("Permission prefix for format-specific permissions. Players need '<prefix><group>' permission for group formats.")
    val formatPermissionPrefix: String = "chatplugin.format.",
    
    @field:Comment("Permission required for players to use color codes in chat messages.")
    val colorPermission: String = "chatplugin.color",
    
    @field:Comment("Permission required for players to use text formatting (bold, italic, etc.) in chat messages.")
    val formattingPermission: String = "chatplugin.formatting",
    
    @field:Comment("Permission required for players to post clickable URLs in chat messages.")
    val urlPermission: String = "chatplugin.url",
    
    @field:Comment("Permission required for players to mention other players using @username syntax.")
    val mentionPermission: String = "chatplugin.mention"
)

@ConfigSerializable
data class FeatureConfig(
    // === CHAT MESSAGE FEATURES ===
    @field:Comment("Enable custom chat message formatting. When false, chat messages use vanilla formatting.")
    val enableChatFormatting: Boolean = true,
    
    @field:Comment("Allow players to use color codes in chat (requires colorPermission).")
    val enableColorCodes: Boolean = true,
    
    @field:Comment("Allow players to use text formatting like bold, italic, etc. (requires formattingPermission).")
    val enableFormatting: Boolean = true,
    
    @field:Comment("Enable automatic URL detection and clickable links in chat (requires urlPermission).")
    val enableUrls: Boolean = true,
    
    @field:Comment("Enable @username mentions with click-to-message functionality (requires mentionPermission).")
    val enableMentions: Boolean = true,
    
    @field:Comment("Enable chat cooldown system to prevent spam.")
    val enableChatCooldown: Boolean = false,
    
    @field:Comment("Cooldown time in seconds between chat messages (only applies when enableChatCooldown is true).")
    val chatCooldownSeconds: Int = 3,
    
    // === JOIN MESSAGES ===
    @field:Comment("Enable custom join messages. When false, no join messages are sent.")
    val enableJoinMessages: Boolean = true,
    
    @field:Comment("Custom join message format. Set to empty string to disable join messages entirely.")
    val joinMessage: String = "<green>+ <yellow><player_name></yellow> joined the server</green>",
    
    // === LEAVE MESSAGES ===
    @field:Comment("Enable custom leave messages. When false, no leave messages are sent.")
    val enableLeaveMessages: Boolean = true,
    
    @field:Comment("Custom leave message format. Set to empty string to disable leave messages entirely.")
    val leaveMessage: String = "<red>- <yellow><player_name></yellow> left the server</red>",
    
    // === DEATH MESSAGES ===
    @field:Comment("Enable custom death messages. When false, vanilla death messages are used.")
    val enableDeathMessages: Boolean = true,
    
    @field:Comment("Completely disable death messages. When true, no death messages are sent at all.")
    val disableDeathMessages: Boolean = false,
    
    @field:Comment("Custom death message formats mapped by death cause. Leave empty to use vanilla death messages.")
    val customDeathMessages: Map<String, String> = mapOf(),
    
    // === OTHER FEATURES ===
    @field:Comment("Enable custom advancement/achievement messages. When false, vanilla messages are used.")
    val enableAdvancementMessages: Boolean = true,
    
    @field:Comment("Enable chat message logging to console for moderation purposes.")
    val enableChatLogging: Boolean = true,
    
    @field:Comment("Enable chat filter system (placeholder for future implementation).")
    val enableChatFilter: Boolean = false
)

@ConfigSerializable
data class PrivateMessageConfig(
    @field:Comment("Enable the private messaging system (/msg, /tell, /message, /reply, /r commands).")
    val enablePrivateMessages: Boolean = true,
    
    @field:Comment("Message format sent to the sender of a private message. Supports MiniMessage and placeholders.")
    val senderFormat: String = "<gray>[<yellow>You</yellow> -> <green>{recipient}</green>]</gray> <white><message></white>",
    
    @field:Comment("Message format sent to the recipient of a private message. Supports MiniMessage and placeholders.")
    val recipientFormat: String = "<gray>[<green>{sender}</green> -> <yellow>You</yellow>]</gray> <white><message></white>",
    
    @field:Comment("Enable cooldown system for private messages to prevent spam.")
    val enableMessageCooldown: Boolean = true,
    
    @field:Comment("Cooldown time in seconds between private messages.")
    val messageCooldownSeconds: Int = 2,
    
    @field:Comment("Message shown when trying to message a player who is not online.")
    val playerNotFoundMessage: String = "<red>Player '{player}' is not online!</red>",
    
    @field:Comment("Message shown when trying to message a player who has messages disabled.")
    val messagesDisabledMessage: String = "<red>{player} has private messages disabled!</red>",
    
    @field:Comment("Enable logging of private messages to console for moderation purposes.")
    val enableMessageLogging: Boolean = true,
    
    @field:Comment("Allow players to use colors and formatting in private messages (requires permissions).")
    val allowFormattingInMessages: Boolean = true
)

@ConfigSerializable
data class ChatToggleConfig(
    @field:Comment("Enable chat toggle functionality allowing players to disable public chat.")
    val enableChatToggle: Boolean = true,
    
    @field:Comment("Enable message toggle functionality allowing players to disable private messages.")
    val enableMessageToggle: Boolean = true,
    
    @field:Comment("Persist toggle states across server restarts and player reconnections.")
    val persistToggleState: Boolean = true,
    
    @field:Comment("When toggling chat, also toggle private messages automatically. Set to false for independent toggles.")
    val linkChatAndMessages: Boolean = false,
    
    @field:Comment("Message shown when a player enables their chat.")
    val chatEnabledMessage: String = "<green>Chat enabled! You can now send and see chat messages.</green>",
    
    @field:Comment("Message shown when a player disables their chat.")
    val chatDisabledMessage: String = "<red>Chat disabled! You will not see chat messages.</red>",
    
    @field:Comment("Message shown when a player enables their private messages.")
    val messagesEnabledMessage: String = "<green>Private messages enabled! You can now receive messages.</green>",
    
    @field:Comment("Message shown when a player disables their private messages.")
    val messagesDisabledMessage: String = "<red>Private messages disabled! You will not receive messages.</red>"
)

@ConfigSerializable
data class SocialSpyConfig(
    @field:Comment("Enable social spy system for moderators to monitor private messages.")
    val enableSocialSpy: Boolean = true,
    
    @field:Comment("Enable command spy to monitor player commands (requires chatplugin.commandspy permission).")
    val enableCommandSpy: Boolean = false,
    
    @field:Comment("Format for social spy messages showing private message monitoring.")
    val socialSpyFormat: String = "<dark_gray>[<red>SPY</red>]</dark_gray> <gray>{sender} -> {recipient}:</gray> <white><message></white>",
    
    @field:Comment("Format for command spy messages showing command monitoring.")
    val commandSpyFormat: String = "<dark_gray>[<blue>CMD</blue>]</dark_gray> <gray>{player}:</gray> <yellow>{command}</yellow>",
    
    @field:Comment("Message shown when social spy is enabled for a moderator.")
    val socialSpyEnabledMessage: String = "<green>Social spy enabled! You will now see private messages.</green>",
    
    @field:Comment("Message shown when social spy is disabled for a moderator.")
    val socialSpyDisabledMessage: String = "<red>Social spy disabled! You will no longer see private messages.</red>",
    
    @field:Comment("Ignore messages between moderators (players with chatplugin.socialspy permission).")
    val ignoreModerators: Boolean = true,
    
    @field:Comment("Log social spy messages to console for audit purposes.")
    val logToConsole: Boolean = true,
    
    @field:Comment("Persist social spy states across server restarts and moderator reconnections.")
    val persistSocialSpyState: Boolean = true
)

@ConfigSerializable
data class MessagesConfig(
    @field:Comment("Command-related messages")
    val commands: CommandMessagesConfig = CommandMessagesConfig(),
    
    @field:Comment("Private messaging system messages")
    val privateMessages: PrivateMessageMessagesConfig = PrivateMessageMessagesConfig(),
    
    @field:Comment("Chat system messages")
    val chat: ChatMessagesConfig = ChatMessagesConfig(),
    
    @field:Comment("Chat toggle system messages")
    val chatToggle: ChatToggleMessagesConfig = ChatToggleMessagesConfig(),
    
    @field:Comment("Social spy system messages")
    val socialSpy: SocialSpyMessagesConfig = SocialSpyMessagesConfig(),
    
    @field:Comment("Error and system messages")
    val system: SystemMessagesConfig = SystemMessagesConfig()
)

@ConfigSerializable
data class CommandMessagesConfig(
    @field:Comment("Message shown when a command can only be used by players")
    val playerOnly: String = "<red>This command can only be used by players!</red>",
    
    @field:Comment("Message shown when a player lacks permission for a command")
    val noPermission: String = "<red>You don't have permission to use this command!</red>",
    
    @field:Comment("Message shown when configuration is successfully reloaded")
    val reloadSuccess: String = "<green>Configuration reloaded successfully!</green>",
    
    @field:Comment("Message shown when configuration reload fails")
    val reloadFailed: String = "<red>Failed to reload configuration. Check console for errors.</red>",
    
    @field:Comment("Message shown when a player is not found")
    val playerNotFound: String = "<red>Player '<player>' is not online!</red>",
    
    @field:Comment("Message shown when a feature is successfully enabled")
    val featureEnabled: String = "<green><feature> enabled!</green>",
    
    @field:Comment("Message shown when a feature is successfully disabled")
    val featureDisabled: String = "<green><feature> disabled!</green>",
    
    @field:Comment("Message shown when a configuration update fails")
    val updateFailed: String = "<red>Failed to update configuration!</red>",
    
    @field:Comment("Message shown when a format is successfully updated")
    val formatUpdated: String = "<green><type> format updated successfully!</green>"
)

@ConfigSerializable
data class PrivateMessageMessagesConfig(
    @field:Comment("Message shown when private messages are disabled")
    val systemDisabled: String = "<red>Private messages are currently disabled.</red>",
    
    @field:Comment("Message shown when a player is on cooldown")
    val cooldown: String = "<red>You must wait <time> seconds before sending another message!</red>",
    
    @field:Comment("Message shown when target player is not found")
    val playerNotFound: String = "<red>Player '<player>' is not online!</red>",
    
    @field:Comment("Message shown when trying to message yourself")
    val selfMessage: String = "<red>You cannot send a message to yourself!</red>",
    
    @field:Comment("Message shown when target player has messages disabled")
    val targetMessagesDisabled: String = "<red><player> has private messages disabled!</red>",
    
    @field:Comment("Message shown when no one has sent a message to reply to")
    val noReplyTarget: String = "<red>No one has sent you a message to reply to!</red>",
    
    @field:Comment("Message shown when reply target is no longer online")
    val replyTargetOffline: String = "<red>The player you're trying to reply to is no longer online!</red>"
)

@ConfigSerializable
data class ChatMessagesConfig(
    @field:Comment("Message shown when a player has chat disabled and tries to send a message")
    val disabledSelf: String = "<red>You have chat disabled! Use /chatplugin toggle chat to enable it.</red>",
    
    @field:Comment("Message shown when there's an error formatting a chat message")
    val formattingError: String = "<red>An error occurred while formatting your message.</red>",
    
    @field:Comment("Message shown when a player is on chat cooldown")
    val cooldown: String = "<red>You must wait <time> seconds before sending another message!</red>"
)

@ConfigSerializable
data class ChatToggleMessagesConfig(
    @field:Comment("Message shown when chat toggle system is disabled")
    val systemDisabled: String = "<red>Chat toggle is currently disabled.</red>",
    
    @field:Comment("Message shown when message toggle system is disabled")
    val messageToggleDisabled: String = "<red>Message toggle is currently disabled.</red>",
    
    @field:Comment("Message shown when a player enables their chat")
    val chatEnabled: String = "<green>Chat enabled! You can now send and see chat messages.</green>",
    
    @field:Comment("Message shown when a player disables their chat")
    val chatDisabled: String = "<red>Chat disabled! You will not see chat messages.</red>",
    
    @field:Comment("Message shown when a player enables their private messages")
    val messagesEnabled: String = "<green>Private messages enabled! You can now receive messages.</green>",
    
    @field:Comment("Message shown when a player disables their private messages")
    val messagesDisabled: String = "<red>Private messages disabled! You will not receive messages.</red>"
)

@ConfigSerializable
data class SocialSpyMessagesConfig(
    @field:Comment("Message shown when social spy system is disabled")
    val systemDisabled: String = "<red>Social spy is currently disabled.</red>",
    
    @field:Comment("Message shown when a player lacks social spy permission")
    val noPermission: String = "<red>You don't have permission to use social spy!</red>",
    
    @field:Comment("Message shown when social spy is enabled")
    val enabled: String = "<green>Social spy enabled! You can now see private messages.</green>",
    
    @field:Comment("Message shown when social spy is disabled")
    val disabled: String = "<red>Social spy disabled! You will no longer see private messages.</red>"
)

@ConfigSerializable
data class SystemMessagesConfig(
    @field:Comment("Message shown for general errors")
    val error: String = "<red>An error occurred. Please try again.</red>",
    
    @field:Comment("Message shown when an operation is successful")
    val success: String = "<green>Operation completed successfully!</green>",
    
    @field:Comment("Message shown when data is cleared")
    val dataCleared: String = "<green>Cleared <type>!</green>",
    
    @field:Comment("Message shown for invalid usage")
    val invalidUsage: String = "<red>Usage: <usage></red>"
)
