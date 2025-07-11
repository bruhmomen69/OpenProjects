package lol.mcplugs.minimessagechatplugin.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class Config(
    @Comment("your comment goes here")
    val chatFormat: ChatFormatConfig = ChatFormatConfig(),
    val placeholders: PlaceholderConfig = PlaceholderConfig(),
    val permissions: PermissionConfig = PermissionConfig(),
    val features: FeatureConfig = FeatureConfig()
)

@ConfigSerializable
data class ChatFormatConfig(
    val defaultFormat: String = "<gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>",
    val groupFormats: Map<String, String> = mapOf(
        "owner" to "<gradient:red:gold>[OWNER]</gradient> <gradient:gold:yellow>{player_name}</gradient> <gray>»</gray> <white>{message}</white>",
        "admin" to "<red>[ADMIN]</red> <gold>{player_name}</gold> <gray>»</gray> <white>{message}</white>",
        "moderator" to "<blue>[MOD]</blue> <yellow>{player_name}</yellow> <gray>»</gray> <white>{message}</white>",
        "helper" to "<green>[HELPER]</green> <lime>{player_name}</lime> <gray>»</gray> <white>{message}</white>",
        "vip" to "<green>[VIP]</green> <aqua>{player_name}</aqua> <gray>»</gray> <white>{message}</white>",
        "premium" to "<gold>[PREMIUM]</gold> <yellow>{player_name}</yellow> <gray>»</gray> <white>{message}</white>",
        "donor" to "<light_purple>[DONOR]</light_purple> <pink>{player_name}</pink> <gray>»</gray> <white>{message}</white>",
        "member" to "<gray>[MEMBER]</gray> <white>{player_name}</white> <gray>»</gray> <gray>{message}</gray>",
        "default" to "<gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
    ),
    val worldFormats: Map<String, String> = mapOf(
        "world" to "<green>[Overworld]</green> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>",
        "world_nether" to "<red>[Nether]</red> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>",
        "world_the_end" to "<dark_purple>[The End]</dark_purple> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>",
        "creative" to "<yellow>[Creative]</yellow> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>",
        "survival" to "<green>[Survival]</green> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
    ),
    val enableGroupFormats: Boolean = true,
    val enableWorldFormats: Boolean = false,
    val formatPriority: List<String> = listOf("permission", "world", "group", "default"),
    val enableRankedFormats: Boolean = true,
    val rankedFormatPriority: List<String> = listOf("owner", "admin", "moderator", "helper", "vip", "premium", "donor", "member", "default"),
    val enableHoverMessages: Boolean = true,
    val hoverMessages: Map<String, String> = mapOf(
        "admin" to "<red>Administrator</red>\n<gray>Click to message</gray>",
        "moderator" to "<blue>Moderator</blue>\n<gray>Click to message</gray>",
        "vip" to "<green>VIP Member</green>\n<gray>Click to message</gray>",
        "default" to "<gray>Player</gray>\n<gray>Click to message</gray>"
    ),
    val enableClickActions: Boolean = true,
    val clickActions: Map<String, String> = mapOf(
        "default" to "suggest_command:/msg {player_name} "
    )
)

@ConfigSerializable
data class PlaceholderConfig(
    val enableBuiltinPlaceholders: Boolean = true,
    val customPlaceholders: Map<String, String> = mapOf(
        "server_name" to "My Server",
        "website" to "example.com"
    ),
    val enablePlaceholderAPI: Boolean = true,
    val placeholderAPITimeout: Long = 1000L
)

@ConfigSerializable
data class PermissionConfig(
    val usePermissionBasedFormats: Boolean = true,
    val formatPermissionPrefix: String = "chatplugin.format.",
    val colorPermission: String = "chatplugin.color",
    val formattingPermission: String = "chatplugin.formatting",
    val urlPermission: String = "chatplugin.url",
    val mentionPermission: String = "chatplugin.mention"
)

@ConfigSerializable
data class FeatureConfig(
    val enableColorCodes: Boolean = true,
    val enableFormatting: Boolean = true,
    val enableUrls: Boolean = true,
    val enableMentions: Boolean = true,
    val enableChatFilter: Boolean = false,
    val enableChatCooldown: Boolean = false,
    val chatCooldownSeconds: Int = 3,
    val enableJoinLeaveMessages: Boolean = true,
    val joinMessage: String = "<green>+ <yellow>{player_name}</yellow> joined the server</green>",
    val leaveMessage: String = "<red>- <yellow>{player_name}</yellow> left the server</red>",
    val enableDeathMessages: Boolean = true,
    val customDeathMessages: Map<String, String> = mapOf(),
    val enableAdvancementMessages: Boolean = true,
    val enableChatLogging: Boolean = true
)
