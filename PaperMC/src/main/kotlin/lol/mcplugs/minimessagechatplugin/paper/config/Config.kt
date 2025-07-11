package lol.mcplugs.minimessagechatplugin.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class Config(
    @Comment("Chat formatting configuration including rank-based formats, world formats, and interactive elements")
    val chatFormat: ChatFormatConfig = ChatFormatConfig(),
    
    @Comment("Placeholder configuration for built-in placeholders, custom placeholders, and PlaceholderAPI integration")
    val placeholders: PlaceholderConfig = PlaceholderConfig(),
    
    @Comment("Permission configuration for format selection and feature access control")
    val permissions: PermissionConfig = PermissionConfig(),
    
    @Comment("Feature toggles and message configuration for chat, join/leave, death, and other server messages")
    val features: FeatureConfig = FeatureConfig(),
    
    @Comment("Private messaging system configuration including formats, cooldowns, and permissions")
    val privateMessages: PrivateMessageConfig = PrivateMessageConfig(),
    
    @Comment("Chat toggle system allowing players to disable chat and private messages")
    val chatToggle: ChatToggleConfig = ChatToggleConfig(),
    
    @Comment("Social spy system for moderators to monitor private messages and commands")
    val socialSpy: SocialSpyConfig = SocialSpyConfig()
)

@ConfigSerializable
data class ChatFormatConfig(
    @Comment("Default chat format used when no group or world format applies. Supports MiniMessage syntax and placeholders.")
    val defaultFormat: String = "<gray>[<white><player_name></white>]</gray> <gray><message></gray>",
    
    @Comment("Group-based chat formats mapped by group/rank name. Higher priority groups should be listed first in rankedFormatPriority.")
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
    
    @Comment("World-specific chat formats mapped by world name. Useful for different gamemodes or themed worlds.")
    val worldFormats: Map<String, String> = mapOf(
        "world" to "<green>[Overworld]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_nether" to "<red>[Nether]</red> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_the_end" to "<dark_purple>[The End]</dark_purple> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "creative" to "<yellow>[Creative]</yellow> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "survival" to "<green>[Survival]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>"
    ),
    
    @Comment("Enable or disable group-based chat formats. When disabled, only default format is used.")
    val enableGroupFormats: Boolean = true,
    
    @Comment("Enable or disable world-specific chat formats. When disabled, world formats are ignored.")
    val enableWorldFormats: Boolean = false,
    
    @Comment("Priority order for format selection. Options: 'permission', 'world', 'group', 'default'. First match wins.")
    val formatPriority: List<String> = listOf("permission", "world", "group", "default"),
    
    @Comment("Enable ranked format system with automatic priority based on rankedFormatPriority list.")
    val enableRankedFormats: Boolean = true,
    
    @Comment("Priority order for ranked formats. Higher ranks should be listed first. Only used when enableRankedFormats is true.")
    val rankedFormatPriority: List<String> = listOf("owner", "admin", "moderator", "helper", "vip", "premium", "donor", "member", "default"),
    
    @Comment("Enable hover messages when players hover over names in chat. Messages are defined in hoverMessages map.")
    val enableHoverMessages: Boolean = true,
    
    @Comment("Custom hover messages shown when hovering over player names, mapped by rank/group. Supports MiniMessage and \\n for newlines.")
    val hoverMessages: Map<String, String> = mapOf(
        "admin" to "<red>Administrator</red>\n<gray>Click to message</gray>",
        "moderator" to "<blue>Moderator</blue>\n<gray>Click to message</gray>",
        "vip" to "<green>VIP Member</green>\n<gray>Click to message</gray>",
        "default" to "<gray>Player</gray>\n<gray>Click to message</gray>"
    ),
    
    @Comment("Enable click actions when players click on names in chat. Actions are defined in clickActions map.")
    val enableClickActions: Boolean = true,
    
    @Comment("Custom click actions for player names, mapped by rank/group. Supports: suggest_command, run_command, open_url, copy_to_clipboard")
    val clickActions: Map<String, String> = mapOf(
        "default" to "suggest_command:/msg <player_name> "
    )
)

@ConfigSerializable
data class PlaceholderConfig(
    @Comment("Enable built-in placeholders like <player_name>, <max_players>, <time>, etc.")
    val enableBuiltinPlaceholders: Boolean = true,
    
    @Comment("Custom server-specific placeholders. Use {placeholder_name} in formats to reference these values.")
    val customPlaceholders: Map<String, String> = mapOf(
        "server_name" to "My Server",
        "website" to "example.com"
    ),
    
    @Comment("""
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
    
    @Comment("Timeout in milliseconds for PlaceholderAPI placeholder resolution to prevent server lag.")
    val placeholderAPITimeout: Long = 1000L
)

@ConfigSerializable
data class PermissionConfig(
    @Comment("Enable permission-based format selection. When true, players need specific permissions to use group formats.")
    val usePermissionBasedFormats: Boolean = true,
    
    @Comment("Permission prefix for format-specific permissions. Players need '<prefix><group>' permission for group formats.")
    val formatPermissionPrefix: String = "chatplugin.format.",
    
    @Comment("Permission required for players to use color codes in chat messages.")
    val colorPermission: String = "chatplugin.color",
    
    @Comment("Permission required for players to use text formatting (bold, italic, etc.) in chat messages.")
    val formattingPermission: String = "chatplugin.formatting",
    
    @Comment("Permission required for players to post clickable URLs in chat messages.")
    val urlPermission: String = "chatplugin.url",
    
    @Comment("Permission required for players to mention other players using @username syntax.")
    val mentionPermission: String = "chatplugin.mention"
)

@ConfigSerializable
data class FeatureConfig(
    // === CHAT MESSAGE FEATURES ===
    @Comment("Enable custom chat message formatting. When false, chat messages use vanilla formatting.")
    val enableChatFormatting: Boolean = true,
    
    @Comment("Allow players to use color codes in chat (requires colorPermission).")
    val enableColorCodes: Boolean = true,
    
    @Comment("Allow players to use text formatting like bold, italic, etc. (requires formattingPermission).")
    val enableFormatting: Boolean = true,
    
    @Comment("Enable automatic URL detection and clickable links in chat (requires urlPermission).")
    val enableUrls: Boolean = true,
    
    @Comment("Enable @username mentions with click-to-message functionality (requires mentionPermission).")
    val enableMentions: Boolean = true,
    
    @Comment("Enable chat cooldown system to prevent spam.")
    val enableChatCooldown: Boolean = false,
    
    @Comment("Cooldown time in seconds between chat messages (only applies when enableChatCooldown is true).")
    val chatCooldownSeconds: Int = 3,
    
    // === JOIN MESSAGES ===
    @Comment("Enable custom join messages. When false, no join messages are sent.")
    val enableJoinMessages: Boolean = true,
    
    @Comment("Custom join message format. Set to empty string to disable join messages entirely.")
    val joinMessage: String = "<green>+ <yellow><player_name></yellow> joined the server</green>",
    
    // === LEAVE MESSAGES ===
    @Comment("Enable custom leave messages. When false, no leave messages are sent.")
    val enableLeaveMessages: Boolean = true,
    
    @Comment("Custom leave message format. Set to empty string to disable leave messages entirely.")
    val leaveMessage: String = "<red>- <yellow><player_name></yellow> left the server</red>",
    
    // === DEATH MESSAGES ===
    @Comment("Enable custom death messages. When false, vanilla death messages are used.")
    val enableDeathMessages: Boolean = true,
    
    @Comment("Completely disable death messages. When true, no death messages are sent at all.")
    val disableDeathMessages: Boolean = false,
    
    @Comment("Custom death message formats mapped by death cause. Leave empty to use vanilla death messages.")
    val customDeathMessages: Map<String, String> = mapOf(),
    
    // === OTHER FEATURES ===
    @Comment("Enable custom advancement/achievement messages. When false, vanilla messages are used.")
    val enableAdvancementMessages: Boolean = true,
    
    @Comment("Enable chat message logging to console for moderation purposes.")
    val enableChatLogging: Boolean = true,
    
    @Comment("Enable chat filter system (placeholder for future implementation).")
    val enableChatFilter: Boolean = false
)

@ConfigSerializable
data class PrivateMessageConfig(
    @Comment("Enable the private messaging system (/msg, /tell, /message, /reply, /r commands).")
    val enablePrivateMessages: Boolean = true,
    
    @Comment("Message format sent to the sender of a private message. Supports MiniMessage and placeholders.")
    val senderFormat: String = "<gray>[<yellow>You</yellow> -> <green>{recipient}</green>]</gray> <white><message></white>",
    
    @Comment("Message format sent to the recipient of a private message. Supports MiniMessage and placeholders.")
    val recipientFormat: String = "<gray>[<green>{sender}</green> -> <yellow>You</yellow>]</gray> <white><message></white>",
    
    @Comment("Enable cooldown system for private messages to prevent spam.")
    val enableMessageCooldown: Boolean = true,
    
    @Comment("Cooldown time in seconds between private messages.")
    val messageCooldownSeconds: Int = 2,
    
    @Comment("Message shown when trying to message a player who is not online.")
    val playerNotFoundMessage: String = "<red>Player '{player}' is not online!</red>",
    
    @Comment("Message shown when trying to message a player who has messages disabled.")
    val messagesDisabledMessage: String = "<red>{player} has private messages disabled!</red>",
    
    @Comment("Enable logging of private messages to console for moderation purposes.")
    val enableMessageLogging: Boolean = true,
    
    @Comment("Allow players to use colors and formatting in private messages (requires permissions).")
    val allowFormattingInMessages: Boolean = true
)

@ConfigSerializable
data class ChatToggleConfig(
    @Comment("Enable chat toggle functionality allowing players to disable public chat.")
    val enableChatToggle: Boolean = true,
    
    @Comment("Enable message toggle functionality allowing players to disable private messages.")
    val enableMessageToggle: Boolean = true,
    
    @Comment("Persist toggle states across server restarts and player reconnections.")
    val persistToggleState: Boolean = true,
    
    @Comment("When toggling chat, also toggle private messages automatically. Set to false for independent toggles.")
    val linkChatAndMessages: Boolean = false,
    
    @Comment("Message shown when a player enables their chat.")
    val chatEnabledMessage: String = "<green>Chat enabled! You can now send and see chat messages.</green>",
    
    @Comment("Message shown when a player disables their chat.")
    val chatDisabledMessage: String = "<red>Chat disabled! You will not see chat messages.</red>",
    
    @Comment("Message shown when a player enables their private messages.")
    val messagesEnabledMessage: String = "<green>Private messages enabled! You can now receive messages.</green>",
    
    @Comment("Message shown when a player disables their private messages.")
    val messagesDisabledMessage: String = "<red>Private messages disabled! You will not receive messages.</red>"
)

@ConfigSerializable
data class SocialSpyConfig(
    @Comment("Enable social spy system for moderators to monitor private messages.")
    val enableSocialSpy: Boolean = true,
    
    @Comment("Enable command spy to monitor player commands (requires chatplugin.commandspy permission).")
    val enableCommandSpy: Boolean = false,
    
    @Comment("Format for social spy messages showing private message monitoring.")
    val socialSpyFormat: String = "<dark_gray>[<red>SPY</red>]</dark_gray> <gray>{sender} -> {recipient}:</gray> <white><message></white>",
    
    @Comment("Format for command spy messages showing command monitoring.")
    val commandSpyFormat: String = "<dark_gray>[<blue>CMD</blue>]</dark_gray> <gray>{player}:</gray> <yellow>{command}</yellow>",
    
    @Comment("Message shown when social spy is enabled for a moderator.")
    val socialSpyEnabledMessage: String = "<green>Social spy enabled! You will now see private messages.</green>",
    
    @Comment("Message shown when social spy is disabled for a moderator.")
    val socialSpyDisabledMessage: String = "<red>Social spy disabled! You will no longer see private messages.</red>",
    
    @Comment("Ignore messages between moderators (players with chatplugin.socialspy permission).")
    val ignoreModerators: Boolean = true,
    
    @Comment("Log social spy messages to console for audit purposes.")
    val logToConsole: Boolean = true,
    
    @Comment("Persist social spy states across server restarts and moderator reconnections.")
    val persistSocialSpyState: Boolean = true
)
