package bruh.zchat.paper.config

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

    @field:Comment("Chat message configuration and features")
    val chat: ChatConfig = ChatConfig(),

    @field:Comment("Join and leave message configuration")
    val joinLeave: JoinLeaveConfig = JoinLeaveConfig(),

    @field:Comment("Death message configuration")
    val death: DeathConfig = DeathConfig(),

    @field:Comment("Advancement message configuration")
    val advancement: AdvancementConfig = AdvancementConfig(),

    @field:Comment("Private messaging system configuration including formats, cooldowns, and permissions")
    val privateMessages: PrivateMessageConfig = PrivateMessageConfig(),

    @field:Comment("Chat toggle system allowing players to disable chat and private messages")
    val chatToggle: ChatToggleConfig = ChatToggleConfig(),

    @field:Comment("Social spy system for moderators to monitor private messages and commands")
    val socialSpy: SocialSpyConfig = SocialSpyConfig(),

    @field:Comment("Inventory placeholder system for sharing inventories, armor, and other player data in chat")
    val inventoryPlaceholders: InventoryPlaceholderConfig = InventoryPlaceholderConfig(),

    @field:Comment("Block system for players to block private messages from others")
    val blocks: BlockConfig = BlockConfig(),
    
    @field:Comment("All configurable messages used throughout the plugin. Supports MiniMessage formatting and placeholders.")
    val messages: MessagesConfig = MessagesConfig(),
    
    @field:Comment("Configurable swear filter with different filter groups, types (regex, levenshtein), and tiered punishments.")
    val swearFilter: SwearFilterConfig = SwearFilterConfig()
)

@ConfigSerializable
data class ChatFormatConfig(
    @field:Comment("Default chat format used when no group or world format applies. Supports MiniMessage syntax and placeholders.")
    val defaultFormat: String = "<gray>[<gradient:white:aqua><player_name></gradient>]</gray> <gray><message></gray>",
    
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
    
    @field:Comment("Apply hover and click events to the entire chat message instead of just the player name. When enabled, the entire message becomes interactive while preserving inventory placeholder interactions.")
    val applyInteractiveToEntireMessage: Boolean = false,
    
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
        - Use minimessage format (not all placeholders support it)
        - Example: <player_level> <vault_rank> <luckperms_prefix>
        - Placeholders are automatically converted to MiniMessage formatting
        
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
    val mentionPermission: String = "chatplugin.mention",
    
    @field:Comment("Permission required for players to use inventory placeholders in chat.")
    val inventoryPlaceholderPermission: String = "chatplugin.inventory.placeholders"
)

@ConfigSerializable
data class ChatConfig(
    @field:Comment("Enable custom chat message formatting. When false, chat messages use vanilla formatting.")
    val enableFormatting: Boolean = true,
    
    @field:Comment("Allow players to use color codes in chat (requires colorPermission).")
    val enableColorCodes: Boolean = true,
    
    @field:Comment("Allow players to use text formatting like bold, italic, etc. (requires formattingPermission).")
    val enableTextFormatting: Boolean = true,
    
    @field:Comment("Enable automatic URL detection and clickable links in chat (requires urlPermission).")
    val enableUrls: Boolean = true,
    
    @field:Comment("Enable @username mentions with click-to-message functionality (requires mentionPermission).")
    val enableMentions: Boolean = true,
    
    @field:Comment("Enable chat cooldown system to prevent spam.")
    val enableCooldown: Boolean = false,
    
    @field:Comment("Cooldown time in seconds between chat messages (only applies when enableCooldown is true).")
    val cooldownSeconds: Int = 3,
    
    @field:Comment("Enable extra chat message logging to console for moderation purposes.")
    val enableLogging: Boolean = false,
    
    @field:Comment("Cache chat formats for major performance improvements. Breaks compatibility with certain plugins.\n" +
            "If you have compatibility issues, please disable this option.")
    val cacheFormats: Boolean = true
)

@ConfigSerializable
data class JoinLeaveConfig(
    @field:Comment("Enable custom join messages. When false, no join messages are sent.")
    val enableJoin: Boolean = true,
    
    @field:Comment("Custom join message format. Supports placeholders: <player_name>, <player_displayname>, <online_players>, <online_players_after_join>, <max_players>, <original_message>, etc. Set to empty string to disable join messages entirely.")
    val joinMessage: String = "<gradient:dark_green:green>+ <gradient:yellow:gold><player_name></gradient> joined the server</gradient>",
    
    @field:Comment("Enable hover messages for join announcements")
    val enableJoinHover: Boolean = true,
    
    @field:Comment("Hover message format for join messages. Supports placeholders: <player_name>, <display_name>, <ping>, <world>, <x>, <y>, <z>")
    val joinHoverMessage: String = """
        <gradient:yellow:gold><b>Welcome <player_name>!</b></gradient>
        <gray>Joined at: <time></gray>
        <gray>Ping: <ping>ms</gray>
        <gray>Location: <x>, <y>, <z></gray>
        <gray>Click to send a message</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for join messages. Can be 'suggest_command', 'run_command', 'open_url', or 'copy_to_clipboard'")
    val joinClickAction: String = "suggest_command:/msg <player_name> ",
    
    @field:Comment("Enable custom leave messages. When false, no leave messages are sent.")
    val enableLeave: Boolean = true,
    
    @field:Comment("Custom leave message format. Supports placeholders: <player_name>, <player_displayname>, <online_players>, <online_players_after_leave>, <max_players>, <original_message>, etc. Set to empty string to disable leave messages entirely.")
    val leaveMessage: String = "<gradient:red:dark_red>- <gradient:yellow:gold><player_name></gradient> left the server</gradient>",
    
    @field:Comment("Enable hover messages for leave announcements")
    val enableLeaveHover: Boolean = true,
    
    @field:Comment("Hover message format for leave messages. Supports placeholders: <player_name>, <display_name>, <world>, <x>, <y>, <z>")
    val leaveHoverMessage: String = """
        <gradient:yellow:gold><b><player_name> left the game</b></gradient>
        <gray>Last seen: <time></gray>
        <gray>Location: <x>, <y>, <z></gray>
    """.trimIndent(),
    
    @field:Comment("Click action for leave messages. Can be 'suggest_command', 'run_command', 'open_url', or 'copy_to_clipboard'")
    val leaveClickAction: String = "suggest_command:/msg <player_name> "
)

@ConfigSerializable
data class DeathConfig(
    @field:Comment("Enable custom death messages. When false, vanilla death messages are used.")
    val enabled: Boolean = true,
    
    @field:Comment("Completely disable death messages. When true, no death messages are sent at all.")
    val disabled: Boolean = false,
    
    @field:Comment("Custom death message formats mapped by death cause. Use death cause keywords or vanilla death message text as keys.")
    val messages: Map<String, String> = mapOf(
        "DROWNING" to "<blue>💧</blue> <yellow><player_name></yellow> <gray>forgot how to swim</gray>",
        "FALL" to "<red>💥</red> <yellow><player_name></yellow> <gray>fell from a high place</gray>",
        "FIRE" to "<red>🔥</red> <yellow><player_name></yellow> <gray>went up in flames</gray>",
        "LAVA" to "<red>🌋</red> <yellow><player_name></yellow> <gray>tried to swim in lava</gray>",
        "SUFFOCATION" to "<dark_gray>🪨</dark_gray> <yellow><player_name></yellow> <gray>suffocated in a wall</gray>",
        "STARVATION" to "<yellow>🍖</yellow> <yellow><player_name></yellow> <gray>starved to death</gray>",
        "POISON" to "<green>☠️</green> <yellow><player_name></yellow> <gray>was poisoned</gray>",
        "MAGIC" to "<light_purple>✨</light_purple> <yellow><player_name></yellow> <gray>was killed by magic</gray>",
        "WITHER" to "<dark_gray>💀</dark_gray> <yellow><player_name></yellow> <gray>withered away</gray>",
        "FALLING_BLOCK" to "<gray>🪨</gray> <yellow><player_name></yellow> <gray>was squashed by a falling block</gray>",
        "THORNS" to "<green>🌹</green> <yellow><player_name></yellow> <gray>was pricked to death</gray>",
        "DRAGON_BREATH" to "<dark_purple>🐉</dark_purple> <yellow><player_name></yellow> <gray>was roasted by dragon breath</gray>",
        "FLY_INTO_WALL" to "<gray>💨</gray> <yellow><player_name></yellow> <gray>experienced kinetic energy</gray>",
        "HOT_FLOOR" to "<red>🔥</red> <yellow><player_name></yellow> <gray>discovered the floor was lava</gray>",
        "CRAMMING" to "<red>🤏</red> <yellow><player_name></yellow> <gray>was squished too much</gray>",
        "DRYOUT" to "<yellow>🐠</yellow> <yellow><player_name></yellow> <gray>died from dehydration</gray>",
        "ENTITY_ATTACK" to "<red>⚔️</red> <yellow><player_name></yellow> <gray>was slain by <death_attacker></gray>",
        "ENTITY_EXPLOSION" to "<red>💥</red> <yellow><player_name></yellow> <gray>was blown up</gray>",
        "PROJECTILE" to "<yellow>🏹</yellow> <yellow><player_name></yellow> <gray>was shot</gray>",
        "PLAYER_ATTACK" to "<red>⚔️</red> <yellow><player_name></yellow> <gray>was slain by <death_attacker></gray>",
        "VOID" to "<dark_purple>🕳️</dark_purple> <yellow><player_name></yellow> <gray>fell into the void</gray>",
        "LIGHTNING" to "<yellow>⚡</yellow> <yellow><player_name></yellow> <gray>was struck by lightning</gray>",
        "SUICIDE" to "<dark_red>💀</dark_red> <yellow><player_name></yellow> <gray>took their own life</gray>"
    ),
    
    @field:Comment("Backup death message used when no custom death message is found for a specific death cause. Supports placeholders: <player_name>, <player_displayname>, <death_cause>, <original_message>, etc.")
    val defaultMessage: String = "<gray>💀</gray> <yellow><player_name></yellow> <gray>died</gray>",
    
    @field:Comment("Enable hover messages for death announcements")
    val enableHover: Boolean = true,
    
    @field:Comment("Hover message format for death messages. Supports placeholders: <player_name>, <death_cause>, <death_message>, <world>, <x>, <y>, <z>")
    val hoverMessage: String = """
        <gradient:red:dark_red><b>Death Details</b></gradient>
        <gray>Player: <player_name></gray>
        <gray>Cause: <death_cause></gray>
        <gray>Message: <death_message></gray>
        <gray>Location: <x>, <y>, <z></gray>
        <gray>World: <world></gray>
        <gray>Click to teleport</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for death messages. Can be 'suggest_command', 'run_command', 'open_url', or 'copy_to_clipboard'")
    val clickAction: String = "suggest_command:/tp <x> <y> <z>"
)

@ConfigSerializable
data class AdvancementConfig(
    @field:Comment("Enable custom advancement/achievement messages. When false, vanilla messages are used.")
    val enabled: Boolean = true,
    
    @field:Comment("Enable hover messages for advancement announcements")
    val enableHover: Boolean = true,
    
    @field:Comment("Custom advancement message formats mapped by advancement key. Supports placeholders: <player_name>, <advancement_name>, <advancement_description>, <advancement_type>")
    val messages: Map<String, String> = mapOf(
        "story/mine_stone" to "<player_name> just mined their first stone!"
    ),
    
    @field:Comment("Default advancement message format when no specific format is found. Supports placeholders: <player_name>, <advancement_name>, <advancement_description>, <advancement_type>")
    val defaultMessage: String = "<gray>🎯</gray> <yellow><player_name></yellow> <gray>has made the advancement</gray> <green><advancement_name></green>",
    
    @field:Comment("Hover message format for advancements. Supports placeholders: <player_name>, <advancement_name>, <advancement_description>, <advancement_type>")
    val hoverMessage: String = """
        <gradient:yellow:gold><b><advancement_name></b></gradient>
        <gray>Type: <advancement_type></gray>
        
        <advancement_description>
        
        <gray>Click to view advancement</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for advancement messages. Can be 'suggest_command', 'run_command', 'open_url', or 'copy_to_clipboard'")
    val clickAction: String = "suggest_command:/advancement grant @s only <advancement_key>"
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
data class InventoryPlaceholderConfig(
    @field:Comment("Enable inventory placeholder system. When false, inventory placeholders are disabled entirely.")
    val enabled: Boolean = true,
    
    @field:Comment("Enable {inv} and [inv] placeholders for sharing main inventory.")
    val enableInventoryPlaceholder: Boolean = true,
    
    @field:Comment("Enable [ender] placeholder for sharing ender chest contents.")
    val enableEnderPlaceholder: Boolean = true,
    
    @field:Comment("Enable [armor] placeholder for sharing equipped armor.")
    val enableArmorPlaceholder: Boolean = true,
    
    @field:Comment("Enable [hand] placeholder for sharing items in hand.")
    val enableHandPlaceholder: Boolean = true,
    
    @field:Comment("Enable [pos] placeholder for sharing current position.")
    val enablePositionPlaceholder: Boolean = true,
    
    @field:Comment("Enable [health] placeholder for sharing current health and status.")
    val enableHealthPlaceholder: Boolean = true,
    
    @field:Comment("Time in minutes before inventory snapshots are automatically deleted.")
    val snapshotRetentionMinutes: Int = 60,
    
    @field:Comment("Display text format for inventory placeholder. Placeholders: <type>, <count>. Supports MiniMessage formatting.")
    val inventoryDisplayFormat: String = "<yellow>[<type>: <count> items]</yellow>",
    
    @field:Comment("Display text format for position placeholder. Placeholders: <x>, <y>, <z>, <world>. Supports MiniMessage formatting.")
    val positionDisplayFormat: String = "<green>[Pos: <x>, <y>, <z>]</green>",
    
    @field:Comment("Display text format for health placeholder. Placeholders: <health>, <max_health>, <food>, <saturation>. Supports MiniMessage formatting.")
    val healthDisplayFormat: String = "<gradient:red:dark_red>[<gradient:white:gray><health>/<max_health> ❤</gradient>]</gradient>",
    
    @field:Comment("Hover text format for inventory placeholders. Supports \\n for newlines. Placeholders: <player>, <type>, <preview>. Supports MiniMessage formatting.")
    val inventoryHoverFormat: String = "<gradient:gold:yellow><b><player>'s <type></b></gradient>\n<gradient:gray:dark_gray>Click to view</gradient>\n\n<preview>",
    
    @field:Comment("Hover text format for position placeholder. Placeholders: <player>, <x>, <y>, <z>, <world>, <biome>. Supports MiniMessage formatting.")
    val positionHoverFormat: String = "<gradient:yellow:gold><b><player>'s Location</b></gradient>\n<gradient:gray:dark_gray>World: <gradient:aqua:light_purple><world></gradient></gradient>\n<gradient:gray:dark_gray>Biome: <gradient:green:dark_green><biome></gradient></gradient>\n<gradient:gray:dark_gray>Coordinates: <gradient:white:gray><x>, <y>, <z></gradient></gradient>\n<gradient:gray:dark_gray>Click to get directions</gradient>",
    
    @field:Comment("Hover text format for health placeholder. Placeholders: <player>, <health>, <max_health>, <food>, <saturation>, <effects>. Supports MiniMessage formatting.")
    val healthHoverFormat: String = "<gradient:yellow:gold><b><player>'s Status</b></gradient>\n<gradient:red:dark_red>❤ <gradient:white:gray><health>/<max_health></gradient> Health</gradient>\n<gradient:gold:yellow>🍖 <gradient:white:gray><food>/20</gradient> Food</gradient>\n<gradient:yellow:gold>⚡ <gradient:white:gray><saturation></gradient> Saturation</gradient>\n<effects>",
    
    @field:Comment("Text shown when an inventory is empty. Supports MiniMessage formatting.")
    val emptyInventoryText: String = "<gradient:gray:dark_gray>Empty</gradient>",
    
    @field:Comment("Text shown for item preview in hover. Placeholders: <amount>, <item>. Supports MiniMessage formatting.")
    val itemPreviewFormat: String = "<gradient:gray:dark_gray>•</gradient> <gradient:white:gray><amount>x <item></gradient>",
    
    @field:Comment("Text shown when there are more items than can be displayed in preview. Placeholders: <count>. Supports MiniMessage formatting.")
    val moreItemsText: String = "<gradient:gray:dark_gray>... and <gradient:white:gray><count></gradient> more</gradient>",
    
    @field:Comment("Maximum number of items to show in hover preview.")
    val maxPreviewItems: Int = 5
)

@ConfigSerializable
data class BlockConfig(
    @field:Comment("Enable the block system for private messages. Does not support inter server sync, use an external plugin for that.")
    val enableBlockSystem: Boolean = false,
    
    @field:Comment("Maximum number of players a user can block.")
    val maxBlocksPerPlayer: Int = 50,
    
    @field:Comment("Persist block lists across server restarts.")
    val persistBlockLists: Boolean = true,
    
    @field:Comment("Allow players to block themselves (typically false).")
    val blockSelf: Boolean = false,
    
    @field:Comment("Log block and unblock actions to console.")
    val logBlocks: Boolean = true
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

    @field:Comment("Block system messages")
    val blocks: BlockMessagesConfig = BlockMessagesConfig(),

    @field:Comment("Inventory placeholder system messages")
    val inventoryPlaceholders: InventoryPlaceholderMessagesConfig = InventoryPlaceholderMessagesConfig(),
    
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
data class BlockMessagesConfig(
    @field:Comment("Message shown when block system is disabled")
    val systemDisabled: String = "<red>Block system is currently disabled.</red>",
    
    @field:Comment("Message shown when a player is successfully blocked")
    val blocked: String = "<green>You have blocked <player>. They can no longer send you private messages.</green>",
    
    @field:Comment("Message shown when a player is successfully unblocked")
    val unblocked: String = "<green>You have unblocked <player>. They can now send you private messages again.</green>",
    
    @field:Comment("Message shown when trying to block a player who is already blocked")
    val alreadyBlocked: String = "<red><player> is already blocked!</red>",
    
    @field:Comment("Message shown when trying to unblock a player who is not blocked")
    val notBlocked: String = "<red><player> is not blocked!</red>",
    
    @field:Comment("Message shown when the block list is empty")
    val blockListEmpty: String = "<yellow>Your block list is empty.</yellow>",
    
    @field:Comment("Message shown when displaying the block list")
    val blockList: String = "<yellow>Blocked players: <list></yellow>",
    
    @field:Comment("Message shown when a blocked player tries to send a message")
    val targetBlockedYou: String = "<red>You cannot send messages to <player> because they have blocked you.</red>",
    
    @field:Comment("Message shown when reaching the maximum number of blocked players")
    val maxBlocksReached: String = "<red>You have reached the maximum number of blocked players (<max>)!</red>"
)


@ConfigSerializable
data class InventoryPlaceholderMessagesConfig(
    @field:Comment("Message shown when a player doesn't have permission to use inventory placeholders.")
    val noPermission: String = "<red>You don't have permission to use inventory placeholders!</red>",
    
    @field:Comment("Message shown when inventory placeholders are disabled in config.")
    val disabled: String = "<red>Inventory placeholders are currently disabled!</red>",
    
    @field:Comment("Message shown when a specific placeholder type is disabled.")
    val placeholderDisabled: String = "<red>The {type} placeholder is currently disabled!</red>",
    
    @field:Comment("Message shown when an inventory snapshot fails to load.")
    val snapshotNotFound: String = "<red>Inventory snapshot not found or expired.</red>",
    
    @field:Comment("Message shown when failing to create an inventory snapshot.")
    val snapshotCreationFailed: String = "<red>Failed to create inventory snapshot.</red>",
    
    @field:Comment("Message shown when failing to open an inventory view.")
    val viewFailed: String = "<red>Failed to open inventory view.</red>",
    
    @field:Comment("Message shown when trying to interact with a read-only inventory.")
    val readOnlyInventory: String = "<yellow>This is a read-only inventory view.</yellow>",
    
    @field:Comment("Message shown when position data is unavailable.")
    val positionUnavailable: String = "<red>Position data unavailable.</red>",
    
    @field:Comment("Message shown when health data is unavailable.")
    val healthUnavailable: String = "<red>Health data unavailable.</red>"
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

@ConfigSerializable
data class SwearFilterConfig(
    @field:Comment("Enable the swear filter system.")
    val enabled: Boolean = true,

    @field:Comment("A list of filter groups. Each group can have its own type, filters, and punishments.")
    val filterGroups: List<FilterGroup> = listOf(
        FilterGroup(
            name = "Default Regex",
            type = "regex",
            distance = 3,
            filters = listOf("(?i)badword", "(?i)anotherbadword"),
            punishments = mapOf(
                1 to listOf("warn {player} You are not allowed to use that word."),
                3 to listOf("kick {player} You have been warned about your language."),
                5 to listOf("ban {player} Banned for repeated language violations.")
            )
        ),
        FilterGroup(
            name = "Default Levenshtein",
            type = "levenshtein",
            distance = 2,
            filters = listOf("swear", "curse"),
            punishments = mapOf(
                1 to listOf("warn {player} Please watch your language."),
                3 to listOf("mute {player} 10m You have been muted for 10 minutes.")
            )
        )
    )
)

@ConfigSerializable
data class FilterGroup(
    @field:Comment("The name of the filter group.")
    val name: String = "default",

    @field:Comment("The type of filter. Can be 'regex' or 'levenshtein'.")
    val type: String = "regex",

    @field:Comment("For Levenshtein type, this is the maximum distance to consider a word a match.")
    val distance: Int = 2,

    @field:Comment("The list of words or regex patterns to filter.")
    val filters: List<String> = emptyList(),

    @field:Comment("A map of infraction counts to a list of punishment commands. Use {player} for the player's name.")
    val punishments: Map<Int, List<String>> = emptyMap()
)
