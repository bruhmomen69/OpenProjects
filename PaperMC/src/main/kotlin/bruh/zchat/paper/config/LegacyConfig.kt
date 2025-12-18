package bruh.zchat.paper.config

import bruh.zchat.paper.database.DatabaseType
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class LegacyConfig(
    @field:Comment("Chat formatting configuration including rank-based formats, world formats, and interactive elements")
    val chatFormat: LegacyChatFormatConfig = LegacyChatFormatConfig(),

    @field:Comment("Placeholder configuration for built-in placeholders, custom placeholders, and PlaceholderAPI integration")
    val placeholders: LegacyPlaceholderConfig = LegacyPlaceholderConfig(),

    @field:Comment("Permission configuration for format selection and feature access control")
    val permissions: LegacyPermissionConfig = LegacyPermissionConfig(),

    @field:Comment("Chat message configuration and features")
    val chat: LegacyChatConfig = LegacyChatConfig(),

    @field:Comment("Join and leave message configuration")
    val joinLeave: LegacyJoinLeaveConfig = LegacyJoinLeaveConfig(),

    @field:Comment("Death message configuration")
    val death: LegacyDeathConfig = LegacyDeathConfig(),

    @field:Comment("Advancement message configuration")
    val advancement: LegacyAdvancementConfig = LegacyAdvancementConfig(),

    @field:Comment("Private messaging system configuration including formats, cooldowns, and permissions")
    val privateMessages: LegacyPrivateMessageConfig = LegacyPrivateMessageConfig(),

    @field:Comment("Chat toggle system allowing players to disable chat and private messages")
    val chatToggle: LegacyChatToggleConfig = LegacyChatToggleConfig(),

    @field:Comment("Social spy system for moderators to monitor private messages and commands")
    val socialSpy: LegacySocialSpyConfig = LegacySocialSpyConfig(),

    @field:Comment("Inventory placeholder system for sharing inventories, armor, and other player data in chat")
    val inventoryPlaceholders: LegacyInventoryPlaceholderConfig = LegacyInventoryPlaceholderConfig(),

    @field:Comment("Block system for players to block private messages from others")
    val blocks: LegacyBlockConfig = LegacyBlockConfig(),
    
    @field:Comment("All configurable messages used throughout the plugin. Supports MiniMessage formatting and placeholders.")
    val messages: LegacyMessagesConfig = LegacyMessagesConfig(),
    
    @field:Comment("Configurable swear filter with different filter groups, types (regex, levenshtein), and tiered punishments.")
    val swearFilter: LegacySwearFilterConfig = LegacySwearFilterConfig(),
    
    @field:Comment("Cross-server messaging configuration (requires MySQL)")
    val crossServerMessaging: LegacyCrossServerMessagingConfig = LegacyCrossServerMessagingConfig(),
    
    @field:Comment("Database configuration for player data storage")
    val database: LegacyDatabaseConfig = LegacyDatabaseConfig()
)

@ConfigSerializable
data class LegacyChatFormatConfig(
    val defaultFormat: String = "<gray>[<gradient:white:aqua><player_name></gradient>]</gray> <gray><message></gray>",
    val groupFormats: Map<String, String> = emptyMap(),
    val worldFormats: Map<String, String> = emptyMap(),
    val enableGroupFormats: Boolean = true,
    val enableWorldFormats: Boolean = false,
    val formatPriority: List<String> = listOf("permission", "world", "group", "default"),
    val enableRankedFormats: Boolean = true,
    val rankedFormatPriority: List<String> = emptyList(),
    val enableHoverMessages: Boolean = true,
    val hoverMessages: Map<String, String> = emptyMap(),
    val enableClickActions: Boolean = true,
    val applyInteractiveToEntireMessage: Boolean = false,
    val clickActions: Map<String, String> = emptyMap()
)

@ConfigSerializable
data class LegacyPlaceholderConfig(
    val enableBuiltinPlaceholders: Boolean = true,
    val customPlaceholders: Map<String, String> = emptyMap(),
    val enablePlaceholderAPI: Boolean = true,
    val placeholderAPITimeout: Long = 1000L
)

@ConfigSerializable
data class LegacyPermissionConfig(
    val usePermissionBasedFormats: Boolean = true,
    val formatPermissionPrefix: String = "zchat.format.",
    val colorPermission: String = "zchat.color",
    val formattingPermission: String = "zchat.formatting",
    val urlPermission: String = "zchat.url",
    val mentionPermission: String = "zchat.mention",
    val inventoryPlaceholderPermission: String = "zchat.inventory.placeholders",
    val swearFilterBypassPermission: String = "zchat.bypass.swearfilter"
)

@ConfigSerializable
data class LegacyChatConfig(
    val enableFormatting: Boolean = true,
    val enableColorCodes: Boolean = true,
    val enableTextFormatting: Boolean = true,
    val enableUrls: Boolean = true,
    val enableMentions: Boolean = true,
    val enableCooldown: Boolean = false,
    val cooldownSeconds: Int = 3,
    val enableLogging: Boolean = false,
    val cacheFormats: Boolean = true
)

@ConfigSerializable
data class LegacyJoinLeaveConfig(
    val enableJoin: Boolean = true,
    val joinMessage: String = "",
    val enableJoinHover: Boolean = true,
    val joinHoverMessage: String = "",
    val joinClickAction: String = "suggest_command:/msg <player_name> ",
    val enableLeave: Boolean = true,
    val leaveMessage: String = "",
    val enableLeaveHover: Boolean = true,
    val leaveHoverMessage: String = "",
    val leaveClickAction: String = "suggest_command:/msg <player_name> "
)

@ConfigSerializable
data class LegacyDeathConfig(
    val enabled: Boolean = true,
    val disabled: Boolean = false,
    val messages: Map<String, String> = emptyMap(),
    val defaultMessage: String = "",
    val enableHover: Boolean = true,
    val hoverMessage: String = "",
    val clickAction: String = "suggest_command:/tp <x> <y> <z>"
)

@ConfigSerializable
data class LegacyAdvancementConfig(
    val enabled: Boolean = true,
    val enableHover: Boolean = true,
    val messages: Map<String, String> = emptyMap(),
    val defaultMessage: String = "",
    val hoverMessage: String = "",
    val clickAction: String = ""
)

@ConfigSerializable
data class LegacyPrivateMessageConfig(
    val enablePrivateMessages: Boolean = true,
    val senderFormat: String = "",
    val recipientFormat: String = "",
    val enableMessageCooldown: Boolean = true,
    val messageCooldownSeconds: Int = 2,
    val playerNotFoundMessage: String = "",
    val messagesDisabledMessage: String = "",
    val enableMessageLogging: Boolean = false,
    val allowFormattingInMessages: Boolean = true
)

@ConfigSerializable
data class LegacyChatToggleConfig(
    val enableChatToggle: Boolean = true,
    val enableMessageToggle: Boolean = true,
    val persistToggleState: Boolean = true,
    val linkChatAndMessages: Boolean = false,
    val chatEnabledMessage: String = "",
    val chatDisabledMessage: String = "",
    val messagesEnabledMessage: String = "",
    val messagesDisabledMessage: String = ""
)

@ConfigSerializable
data class LegacyInventoryPlaceholderConfig(
    val enabled: Boolean = true,
    val enableInventoryPlaceholder: Boolean = true,
    val enableEnderPlaceholder: Boolean = true,
    val enableArmorPlaceholder: Boolean = true,
    val enableHandPlaceholder: Boolean = true,
    val enablePositionPlaceholder: Boolean = true,
    val enableHealthPlaceholder: Boolean = true,
    val snapshotRetentionMinutes: Int = 60,
    val inventoryDisplayFormat: String = "",
    val positionDisplayFormat: String = "",
    val healthDisplayFormat: String = "",
    val inventoryHoverFormat: String = "",
    val positionHoverFormat: String = "",
    val healthHoverFormat: String = "",
    val emptyInventoryText: String = "",
    val itemPreviewFormat: String = "",
    val moreItemsText: String = "",
    val maxPreviewItems: Int = 5
)

@ConfigSerializable
data class LegacyBlockConfig(
    val enableBlockSystem: Boolean = false,
    val maxBlocksPerPlayer: Int = 50,
    val persistBlockLists: Boolean = true,
    val blockSelf: Boolean = false,
    val logBlocks: Boolean = true
)

@ConfigSerializable
data class LegacySocialSpyConfig(
    val enableSocialSpy: Boolean = true,
    val enableCommandSpy: Boolean = false,
    val socialSpyFormat: String = "",
    val commandSpyFormat: String = "",
    val socialSpyEnabledMessage: String = "",
    val socialSpyDisabledMessage: String = "",
    val ignoreModerators: Boolean = true,
    val logToConsole: Boolean = true,
    val persistSocialSpyState: Boolean = true
)

@ConfigSerializable
data class LegacyMessagesConfig(
    val commands: LegacyCommandMessagesConfig = LegacyCommandMessagesConfig(),
    val privateMessages: LegacyPrivateMessageMessagesConfig = LegacyPrivateMessageMessagesConfig(),
    val chat: LegacyChatMessagesConfig = LegacyChatMessagesConfig(),
    val chatToggle: LegacyChatToggleMessagesConfig = LegacyChatToggleMessagesConfig(),
    val socialSpy: LegacySocialSpyMessagesConfig = LegacySocialSpyMessagesConfig(),
    val blocks: LegacyBlockMessagesConfig = LegacyBlockMessagesConfig(),
    val inventoryPlaceholders: LegacyInventoryPlaceholderMessagesConfig = LegacyInventoryPlaceholderMessagesConfig(),
    val alerts: LegacyAlertMessagesConfig = LegacyAlertMessagesConfig(),
    val system: LegacySystemMessagesConfig = LegacySystemMessagesConfig()
)

@ConfigSerializable
data class LegacyCommandMessagesConfig(
    val playerOnly: String = "",
    val noPermission: String = "",
    val reloadSuccess: String = "",
    val reloadFailed: String = "",
    val playerNotFound: String = "",
    val featureEnabled: String = "",
    val featureDisabled: String = "",
    val updateFailed: String = "",
    val formatUpdated: String = ""
)

@ConfigSerializable
data class LegacyPrivateMessageMessagesConfig(
    val systemDisabled: String = "",
    val cooldown: String = "",
    val playerNotFound: String = "",
    val selfMessage: String = "",
    val targetMessagesDisabled: String = "",
    val noReplyTarget: String = "",
    val replyTargetOffline: String = "",
    val deliveryFailed: String = ""
)

@ConfigSerializable
data class LegacyChatMessagesConfig(
    val disabledSelf: String = "",
    val formattingError: String = "",
    val cooldown: String = ""
)

@ConfigSerializable
data class LegacyChatToggleMessagesConfig(
    val systemDisabled: String = "",
    val messageToggleDisabled: String = "",
    val chatEnabled: String = "",
    val chatDisabled: String = "",
    val messagesEnabled: String = "",
    val messagesDisabled: String = ""
)

@ConfigSerializable
data class LegacySocialSpyMessagesConfig(
    val systemDisabled: String = "",
    val noPermission: String = "",
    val enabled: String = "",
    val disabled: String = ""
)

@ConfigSerializable
data class LegacyBlockMessagesConfig(
    val systemDisabled: String = "",
    val blocked: String = "",
    val unblocked: String = "",
    val alreadyBlocked: String = "",
    val notBlocked: String = "",
    val blockListEmpty: String = "",
    val blockList: String = "",
    val targetBlockedYou: String = "",
    val maxBlocksReached: String = ""
)

@ConfigSerializable
data class LegacyInventoryPlaceholderMessagesConfig(
    val noPermission: String = "",
    val disabled: String = "",
    val placeholderDisabled: String = "",
    val snapshotNotFound: String = "",
    val snapshotCreationFailed: String = "",
    val viewFailed: String = "",
    val readOnlyInventory: String = "",
    val positionUnavailable: String = "",
    val healthUnavailable: String = ""
)

@ConfigSerializable
data class LegacyAlertMessagesConfig(
    val systemDisabled: String = "",
    val noPermission: String = "",
    val enabled: String = "",
    val disabled: String = "",
    val autoEnabled: String = ""
)

@ConfigSerializable
data class LegacySystemMessagesConfig(
    val error: String = "",
    val success: String = "",
    val dataCleared: String = "",
    val invalidUsage: String = ""
)

@ConfigSerializable
data class LegacySwearFilterConfig(
    val enabled: Boolean = true,
    val filterGroups: List<LegacyFilterGroup> = emptyList(),
    val alerts: LegacyAlertConfig = LegacyAlertConfig()
)

@ConfigSerializable
data class LegacyAlertConfig(
    val enableAlerts: Boolean = true,
    val alertPermission: String = "zchat.alerts.swearfilter",
    val enableByDefault: Boolean = true,
    val showAutoEnabledMessage: Boolean = false,
    val alertMessage: String = "",
    val logToConsole: Boolean = true,
    val consoleAlertMessage: String = "",
    val alertGroups: List<String> = emptyList(),
    val minimumSeverity: Int = 1,
    val alertCooldownSeconds: Int = 5,
    val onlyBeforePunishment: Boolean = false,
    val maxAlertsPerMinute: Int = 3
)

@ConfigSerializable
data class LegacyFilterGroup(
    val name: String = "default",
    val type: String = "regex",
    val distance: Int = 2,
    val filters: List<String> = emptyList(),
    val punishments: Map<Int, List<String>> = emptyMap()
)

@ConfigSerializable
data class LegacyDatabaseConfig(
    val type: String = "sqlite",
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "chatplugin",
    val username: String = "",
    val password: String = "",
    val sqliteFile: String = "database.db",
    val poolSize: Int = 8,
    val connectionTimeout: Long = 30000,
    val maxLifetime: Long = 1800000,
    val leakDetectionThreshold: Long = 30000,
    val autoMigrate: Boolean = true,
    val enableArchive: Boolean = true,
    val dataRetentionDays: Int = 30,
    val maintenanceTime: String = "02:00",
    val enablePerformanceMonitoring: Boolean = false
)

@ConfigSerializable
data class LegacyCrossServerMessagingConfig(
    val enabled: Boolean = true,
    val pollIntervalMillis: Long = 250,
    val heartbeatIntervalSeconds: Int = 10,
    val heartbeatTimeoutSeconds: Int = 25,
    val claimTimeoutSeconds: Int = 60,
    val pollBatchSize: Int = 50,
    val messageRetentionDays: Int = 7
)
