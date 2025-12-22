package bruh.zchat.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class Config(
    @field:Comment("Chat formatting configuration including priorities and interactive toggles. For chat format templates, see messages.conf")
    val chatFormat: ChatFormatConfig = ChatFormatConfig(),

    @field:Comment("Placeholder configuration for built-in placeholders, custom placeholders, and PlaceholderAPI integration")
    val placeholders: PlaceholderConfig = PlaceholderConfig(),

    @field:Comment("Permission configuration for format selection and feature access control")
    val permissions: PermissionConfig = PermissionConfig(),

    @field:Comment("Chat message features and cooldowns")
    val chat: ChatConfig = ChatConfig(),

    @field:Comment("Join and leave message toggles")
    val joinLeave: JoinLeaveConfig = JoinLeaveConfig(),

    @field:Comment("Death message toggles")
    val death: DeathConfig = DeathConfig(),

    @field:Comment("Advancement message toggles")
    val advancement: AdvancementConfig = AdvancementConfig(),

    @field:Comment("Private messaging system toggles and cooldowns")
    val privateMessages: PrivateMessageConfig = PrivateMessageConfig(),

    @field:Comment("Chat toggle system settings")
    val chatToggle: ChatToggleConfig = ChatToggleConfig(),

    @field:Comment("Social spy system monitoring settings")
    val socialSpy: SocialSpyConfig = SocialSpyConfig(),

    @field:Comment("Inventory placeholder system settings")
    val inventoryPlaceholders: InventoryPlaceholderConfig = InventoryPlaceholderConfig(),

    @field:Comment("Block system settings")
    val blocks: BlockConfig = BlockConfig(),
    
    @field:Comment("Configurable swear filter settings and punishments")
    val swearFilter: SwearFilterConfig = SwearFilterConfig()
)

@ConfigSerializable
data class ChatFormatConfig(
    @field:Comment("Enable or disable group-based chat formats. When disabled, only default format is used. \nGroup formats (and other chat formats) are configured in messages.conf.")
    val enableGroupFormats: Boolean = true,
    
    @field:Comment("Enable or disable world-specific chat formats. When disabled, world formats are ignored. World formats (and other chat formats) are configured in messages.conf.")
    val enableWorldFormats: Boolean = false,
    
    @field:Comment("Priority order for format selection. Options: 'permission', 'world', 'group', 'default'. First match wins.")
    val formatPriority: List<String> = listOf("permission", "world", "group", "default"),

    @field:Comment("Enable hover messages when players hover over names in chat.")
    val enableHoverMessages: Boolean = true,
    
    @field:Comment("Enable click actions when players click on names in chat.")
    val enableClickActions: Boolean = true,
    
    @field:Comment("Apply hover and click events to the entire chat message instead of just the player name.")
    val applyInteractiveToEntireMessage: Boolean = false
)

@ConfigSerializable
data class PlaceholderConfig(
    @field:Comment("Enable built-in placeholders like <player_name>, <max_players>, <time>, etc.")
    val enableBuiltinPlaceholders: Boolean = true,
    
    @field:Comment("Custom server-specific placeholders mapping. Values moved to messages config if they are display strings.")
    val customPlaceholders: Map<String, String> = mapOf(
        "server_name" to "My Server",
        "website" to "example.com"
    ),
    
    @field:Comment("Enable PlaceholderAPI integration for external plugin placeholders.")
    val enablePlaceholderAPI: Boolean = true,
    
    @field:Comment("Timeout in milliseconds for PlaceholderAPI placeholder resolution.")
    val placeholderAPITimeout: Long = 1000L
)

@ConfigSerializable
data class PermissionConfig(
    @field:Comment("Enable permission-based format selection.")
    val usePermissionBasedFormats: Boolean = true,
    
    @field:Comment("Permission prefix for format-specific permissions.")
    val formatPermissionPrefix: String = "zchat.format.",
    
    @field:Comment("Permission required for players to use color codes in chat messages.")
    val colorPermission: String = "zchat.color",
    
    @field:Comment("Permission required for players to use text formatting in chat messages.")
    val formattingPermission: String = "zchat.formatting",
    
    @field:Comment("Permission required for players to post clickable URLs in chat messages.")
    val urlPermission: String = "zchat.url",
    
    @field:Comment("Permission required for players to mention other players using @username syntax.")
    val mentionPermission: String = "zchat.mention",
    
    @field:Comment("Permission required for players to use inventory placeholders in chat.")
    val inventoryPlaceholderPermission: String = "zchat.inventory.placeholders",
    
    @field:Comment("Permission required for players to bypass the swear filter.")
    val swearFilterBypassPermission: String = "zchat.bypass.swearfilter"
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
    
    @field:Comment("Cooldown time in seconds between chat messages.")
    val cooldownSeconds: Int = 3,
    
    @field:Comment("Enable extra chat message logging to console for moderation purposes.")
    val enableLogging: Boolean = false,
    
    @field:Comment("Cache chat formats for major performance improvements.")
    val cacheFormats: Boolean = true
)

@ConfigSerializable
data class JoinLeaveConfig(
    @field:Comment("Enable custom join messages.")
    val enableJoin: Boolean = true,
    
    @field:Comment("Enable hover messages for join announcements")
    val enableJoinHover: Boolean = true,
    
    @field:Comment("Enable custom leave messages.")
    val enableLeave: Boolean = true,
    
    @field:Comment("Enable hover messages for leave announcements")
    val enableLeaveHover: Boolean = true
)

@ConfigSerializable
data class DeathConfig(
    @field:Comment("Enable custom death messages. When false, vanilla death messages are used.")
    val enabled: Boolean = true,
    
    @field:Comment("Completely disable death messages.")
    val disabled: Boolean = false,
    
    @field:Comment("Enable hover messages for death announcements")
    val enableHover: Boolean = true
)

@ConfigSerializable
data class AdvancementConfig(
    @field:Comment("Enable custom advancement/achievement messages.")
    val enabled: Boolean = true,
    
    @field:Comment("Enable hover messages for advancement announcements")
    val enableHover: Boolean = true
)

@ConfigSerializable
data class PrivateMessageConfig(
    @field:Comment("Enable the private messaging system.")
    val enablePrivateMessages: Boolean = true,
    
    @field:Comment("Enable cooldown system for private messages.")
    val enableMessageCooldown: Boolean = true,
    
    @field:Comment("Cooldown time in seconds between private messages.")
    val messageCooldownSeconds: Int = 2,
    
    @field:Comment("Enable logging of private messages to console.")
    val enableMessageLogging: Boolean = false,
    
    @field:Comment("Allow players to use colors and formatting in private messages.")
    val allowFormattingInMessages: Boolean = true
)

@ConfigSerializable
data class ChatToggleConfig(
    @field:Comment("Enable chat toggle functionality.")
    val enableChatToggle: Boolean = true,
    
    @field:Comment("Enable message toggle functionality.")
    val enableMessageToggle: Boolean = true,
    
    @field:Comment("Persist toggle states across server restarts.")
    val persistToggleState: Boolean = true,
    
    @field:Comment("When toggling chat, also toggle private messages automatically.")
    val linkChatAndMessages: Boolean = false
)

@ConfigSerializable
data class InventoryPlaceholderConfig(
    @field:Comment("Enable inventory placeholder system.")
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
    
    @field:Comment("Maximum number of items to show in hover preview.")
    val maxPreviewItems: Int = 5,
    
    @field:Comment("Click action configuration for different placeholder types.")
    val clickActions: ClickActionsConfig = ClickActionsConfig()
)

@ConfigSerializable
data class BlockConfig(
    @field:Comment("Enable the block system for private messages.")
    val enableBlockSystem: Boolean = false,
    
    @field:Comment("Maximum number of players a user can block.")
    val maxBlocksPerPlayer: Int = 50,
    
    @field:Comment("Persist block lists across server restarts.")
    val persistBlockLists: Boolean = true,
    
    @field:Comment("Allow players to block themselves.")
    val blockSelf: Boolean = false,
    
    @field:Comment("Log block and unblock actions to console.")
    val logBlocks: Boolean = true
)

@ConfigSerializable
data class SocialSpyConfig(
    @field:Comment("Enable social spy system for moderators to monitor private messages.")
    val enableSocialSpy: Boolean = true,
    
    @field:Comment("Enable command spy to monitor player commands.")
    val enableCommandSpy: Boolean = false,
    
    @field:Comment("Ignore messages between moderators.")
    val ignoreModerators: Boolean = true,
    
    @field:Comment("Enable channel chat spy to monitor channel messages.")
    val enableChannelSpy: Boolean = true,

    @field:Comment("Ignore channel messages sent by moderators when spying.")
    val ignoreModeratorsForChannelSpy: Boolean = true,

    @field:Comment("Log social spy messages to console.")
    val logToConsole: Boolean = true,
    
    @field:Comment("Persist social spy states across server restarts.")
    val persistSocialSpyState: Boolean = true
)

@ConfigSerializable
data class SwearFilterConfig(
    @field:Comment("Enable the swear filter system.")
    val enabled: Boolean = true,
    
    @field:Comment("Enable sending a message to players when their message is blocked.")
    val enableBlockedMessage: Boolean = true,

    @field:Comment("A list of filter groups. Each group can have its own type, filters, and punishments.")
    val filterGroups: List<FilterGroup> = listOf(
        FilterGroup(
            name = "Default Regex",
            type = "regex",
            distance = 3,
            filters = listOf("(?i)\\bshit\\b", "(?i)\\bfuck\\b"),
            punishments = mapOf(
                1 to listOf("warn {player} You are not allowed to use that word."),
                3 to listOf("kick {player} You have been warned about your language."),
                5 to listOf("ban {player} Banned for repeated language violations.")
            )
        ),
        FilterGroup(
            name = "Default Levenshtein",
            type = "levenshtein",
            distance = 3,
            filters = listOf("shit", "fuck"),
            punishments = mapOf(
                1 to listOf("warn {player} You are not allowed to use that word."),
                3 to listOf("kick {player} You have been warned about your language."),
                5 to listOf("ban {player} Banned for repeated language violations.")
            )
        ),
        FilterGroup(
            name = "Default Links",
            type = "regex",
            distance = 3,
            filters = listOf(
                "(?i)(?:https?://)\\S+",
                "(?i)\\bwww\\.\\S+",
                "(?i)\\b(?:discord\\.gg|discord(?:app)?\\.com/invite)/[A-Za-z0-9-]+\\b",
                "(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:[a-z]{2,63})(?::\\d{1,5})?(?:/\\S*)?\\b",
                "(?i)\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/\\S*)?\\b"
            ),
            punishments = mapOf(
                1 to listOf("warn {player} You are not allowed to use that word."),
                3 to listOf("kick {player} You have been warned about your language."),
                5 to listOf("ban {player} Banned for repeated language violations.")
            )
        )
    ),
    
    @field:Comment("Alert system configuration for swear filter violations.")
    val alerts: AlertConfig = AlertConfig()
)

@ConfigSerializable
data class AlertConfig(
    @field:Comment("Enable swear filter violation alerts to staff.")
    val enableAlerts: Boolean = true,
    
    @field:Comment("Permission required to receive swear filter alerts.")
    val alertPermission: String = "zchat.alerts.swearfilter",
    
    @field:Comment("Enable alerts by default for players with alert permission.")
    val enableByDefault: Boolean = true,
    
    @field:Comment("Send notification when alerts are automatically enabled on join.")
    val showAutoEnabledMessage: Boolean = false,
    
    @field:Comment("Show alerts in console for logging.")
    val logToConsole: Boolean = true,
    
    @field:Comment("Which filter groups should trigger alerts (empty list = all groups).")
    val alertGroups: List<String> = emptyList(),
    
    @field:Comment("Minimum severity level to trigger alerts (1-5).")
    val minimumSeverity: Int = 1,
    
    @field:Comment("Alert cooldown in seconds to prevent spam.")
    val alertCooldownSeconds: Int = 5,
    
    @field:Comment("Only show alerts from players who haven't reached the punishment threshold yet.")
    val onlyBeforePunishment: Boolean = false,
    
    @field:Comment("Maximum alerts per minute per player.")
    val maxAlertsPerMinute: Int = 3
)

@ConfigSerializable
data class ClickActionsConfig(
    @field:Comment("Click command for position placeholder. Built-in placeholders: {player}, {x}, {y}, {z}, {world}. " +
            "If PlaceholderAPI is enabled, you can also use any %placeholder% from PlaceholderAPI (e.g., %player_health%, %player_level%).")
    val positionCommand: String = "/tpa {player}",
    
    @field:Comment("Click command for health placeholder. Built-in placeholders: {player}. " +
            "If PlaceholderAPI is enabled, you can also use any %placeholder% from PlaceholderAPI (e.g., %player_health%, %player_food%).")
    val healthCommand: String = "/tpa {player}",
    
    @field:Comment("Click action type for position. Can be 'suggest' (places command in chat) or 'run' (executes command directly).")
    val positionActionType: String = "suggest",
    
    @field:Comment("Click action type for health. Can be 'suggest' (places command in chat) or 'run' (executes command directly).")
    val healthActionType: String = "suggest"
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

    @field:Comment("A map of infraction counts to a list of punishment commands.")
    val punishments: Map<Int, List<String>> = emptyMap()
)
