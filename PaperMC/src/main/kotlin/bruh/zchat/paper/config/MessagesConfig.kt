package bruh.zchat.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class MessagesConfig(
    @field:Comment("Chat formatting messages and interactive elements")
    val chatFormat: ChatFormatMessages = ChatFormatMessages(),

    @field:Comment("Join and leave messages and interactive elements")
    val joinLeave: JoinLeaveMessages = JoinLeaveMessages(),

    @field:Comment("Death message configurations")
    val death: DeathMessages = DeathMessages(),

    @field:Comment("Advancement message configurations")
    val advancement: AdvancementMessages = AdvancementMessages(),

    @field:Comment("Private messaging system messages")
    val privateMessages: PrivateMessageMessages = PrivateMessageMessages(),

    @field:Comment("Chat system messages including toggles and cooldowns")
    val chat: ChatMessages = ChatMessages(),

    @field:Comment("Social spy system display formats")
    val socialSpy: SocialSpyMessages = SocialSpyMessages(),

    @field:Comment("Inventory placeholder display and hover formats")
    val inventoryPlaceholders: InventoryPlaceholderMessages = InventoryPlaceholderMessages(),

    @field:Comment("Block system notifications")
    val blocks: BlockMessages = BlockMessages(),

    @field:Comment("Swear filter alert formats")
    val swearFilter: SwearFilterMessages = SwearFilterMessages(),

    @field:Comment("Command responses")
    val commands: CommandMessages = CommandMessages(),

    @field:Comment("Generic system and error messages")
    val system: SystemMessages = SystemMessages()
)

@ConfigSerializable
data class ChatFormatMessages(
    @field:Comment("Default chat format used when no group or world format applies")
    val defaultFormat: String = "<gray>[<gradient:white:aqua><player_name></gradient>]</gray> <gray><message></gray>",
    
    @field:Comment("Group-based chat formats mapped by group/rank name")
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
    
    @field:Comment("World-specific chat formats")
    val worldFormats: Map<String, String> = mapOf(
        "world" to "<green>[Overworld]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_nether" to "<red>[Nether]</red> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "world_the_end" to "<dark_purple>[The End]</dark_purple> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "creative" to "<yellow>[Creative]</yellow> <gray>[<white><player_name></white>]</gray> <gray><message></gray>",
        "survival" to "<green>[Survival]</green> <gray>[<white><player_name></white>]</gray> <gray><message></gray>"
    ),

    @field:Comment("Custom hover messages shown when hovering over player names")
    val hoverMessages: Map<String, String> = mapOf(
        "admin" to "<red>Administrator</red>\n<gray>Click to message</gray>",
        "moderator" to "<blue>Moderator</blue>\n<gray>Click to message</gray>",
        "vip" to "<green>VIP Member</green>\n<gray>Click to message</gray>",
        "default" to "<gray>Player</gray>\n<gray>Click to message</gray>"
    ),

    @field:Comment("Custom click actions for player names")
    val clickActions: Map<String, String> = mapOf(
        "default" to "suggest_command:/msg <player_name> "
    )
)

@ConfigSerializable
data class JoinLeaveMessages(
    @field:Comment("Custom join message format")
    val joinMessage: String = "<gradient:dark_green:green>+ <gradient:yellow:gold><player_name></gradient> joined the server</gradient>",
    
    @field:Comment("Hover message format for join messages")
    val joinHoverMessage: String = """
        <gradient:yellow:gold><b>Welcome <player_name>!</b></gradient>
        <gray>Joined at: <time></gray>
        <gray>Ping: <ping>ms</gray>
        <gray>Location: <x>, <y>, <z></gray>
        <gray>Click to send a message</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for join messages")
    val joinClickAction: String = "suggest_command:/msg <player_name> ",
    
    @field:Comment("Custom leave message format")
    val leaveMessage: String = "<gradient:red:dark_red>- <gradient:yellow:gold><player_name></gradient> left the server</gradient>",
    
    @field:Comment("Hover message format for leave messages")
    val leaveHoverMessage: String = """
        <gradient:yellow:gold><b><player_name> left the game</b></gradient>
        <gray>Last seen: <time></gray>
        <gray>Location: <x>, <y>, <z></gray>
    """.trimIndent(),
    
    @field:Comment("Click action for leave messages")
    val leaveClickAction: String = "suggest_command:/msg <player_name> "
)

@ConfigSerializable
data class DeathMessages(
    @field:Comment("Custom death message formats mapped by death cause")
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
    
    @field:Comment("Backup death message")
    val defaultMessage: String = "<gray>💀</gray> <yellow><player_name></yellow> <gray>died</gray>",
    
    @field:Comment("Hover message format for death messages")
    val hoverMessage: String = """
        <gradient:red:dark_red><b>Death Details</b></gradient>
        <gray>Player: <player_name></gray>
        <gray>Cause: <death_cause></gray>
        <gray>Message: <death_message></gray>
        <gray>Location: <x>, <y>, <z></gray>
        <gray>World: <world></gray>
        <gray>Click to teleport</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for death messages")
    val clickAction: String = "suggest_command:/tp <x> <y> <z>"
)

@ConfigSerializable
data class AdvancementMessages(
    @field:Comment("Custom advancement message formats mapped by advancement key")
    val messages: Map<String, String> = mapOf(
        "story/mine_stone" to "<player_name> just mined their first stone!"
    ),
    
    @field:Comment("Default advancement message format")
    val defaultMessage: String = "<gray>🎯</gray> <yellow><player_name></yellow> <gray>has made the advancement</gray> <green><advancement_name></green>",
    
    @field:Comment("Hover message format for advancements")
    val hoverMessage: String = """
        <gradient:yellow:gold><b><advancement_name></b></gradient>
        <gray>Type: <advancement_type></gray>
        
        <advancement_description>
        
        <gray>Click to view advancement</gray>
    """.trimIndent(),
    
    @field:Comment("Click action for advancement messages")
    val clickAction: String = "suggest_command:/advancement grant @s only <advancement_key>"
)

@ConfigSerializable
data class PrivateMessageMessages(
    @field:Comment("Message format sent to the sender")
    val senderFormat: String = "<gray>[<yellow>You</yellow> -> <green>{recipient}</green>]</gray> <white><message></white>",
    
    @field:Comment("Message format sent to the recipient")
    val recipientFormat: String = "<gray>[<green>{sender}</green> -> <yellow>You</yellow>]</gray> <white><message></white>",

    @field:Comment("Message shown when trying to message a player who is not online")
    val playerNotFound: String = "<red>Player '<recipient>' is not online!</red>",
    
    @field:Comment("Message shown when trying to message a player who has messages disabled")
    val targetMessagesDisabled: String = "<red><player> has private messages disabled!</red>",

    @field:Comment("Message shown when private messages are globally disabled")
    val systemDisabled: String = "<red>Private messages are currently disabled.</red>",
    
    @field:Comment("Message shown when a player is on cooldown")
    val cooldown: String = "<red>You must wait <time> seconds before sending another message!</red>",
    
    @field:Comment("Message shown when trying to message yourself")
    val selfMessage: String = "<red>You cannot send a message to yourself!</red>",
    
    @field:Comment("Message shown when no one has sent a message to reply to")
    val noReplyTarget: String = "<red>No one has sent you a message to reply to!</red>",
    
    @field:Comment("Message shown when reply target is no longer online")
    val replyTargetOffline: String = "<red>The player you're trying to reply to is no longer online!</red>",
    
    @field:Comment("Message shown when a cross-server private message could not be delivered")
    val deliveryFailed: String = "<red>Could not deliver your message to <player> because they went offline.</red>"
)

@ConfigSerializable
data class ChatMessages(
    @field:Comment("Message shown when a player has chat disabled and tries to send a message")
    val disabledSelf: String = "<red>You have chat disabled! Use /chatplugin toggle chat to enable it.</red>",
    
    @field:Comment("Message shown when there's an error formatting a chat message")
    val formattingError: String = "<red>An error occurred while formatting your message.</red>",
    
    @field:Comment("Message shown when a player is on chat cooldown")
    val cooldown: String = "<red>You must wait <time> seconds before sending another message!</red>",

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
data class SocialSpyMessages(
    @field:Comment("Format for social spy messages monitoring private messages")
    val socialSpyFormat: String = "<dark_gray>[<red>SPY</red>]</dark_gray> <gray>{sender} -> {recipient}:</gray> <white><message></white>",
    
    @field:Comment("Format for command spy messages monitoring player commands")
    val commandSpyFormat: String = "<dark_gray>[<blue>CMD</blue>]</dark_gray> <gray>{player}:</gray> <yellow>{command}</yellow>",
    
    @field:Comment("Message shown when social spy is enabled")
    val enabled: String = "<green>Social spy enabled! You can now see private messages.</green>",
    
    @field:Comment("Message shown when social spy is disabled")
    val disabled: String = "<red>Social spy disabled! You will no longer see private messages.</red>",

    @field:Comment("Message shown when social spy system is disabled")
    val systemDisabled: String = "<red>Social spy is currently disabled.</red>",
    
    @field:Comment("Message shown when a player lacks social spy permission")
    val noPermission: String = "<red>You don't have permission to use social spy!</red>"
)

@ConfigSerializable
data class InventoryPlaceholderMessages(
    @field:Comment("Display text format for inventory placeholder")
    val inventoryDisplayFormat: String = "<yellow>[<type>: <count> items]</yellow>",
    
    @field:Comment("Display text format for position placeholder")
    val positionDisplayFormat: String = "<green>[Pos: <x>, <y>, <z>]</green>",
    
    @field:Comment("Display text format for health placeholder")
    val healthDisplayFormat: String = "<gradient:red:dark_red>[<gradient:white:gray><health>/<max_health> ❤</gradient>]</gradient>",
    
    @field:Comment("Hover text format for inventory placeholders")
    val inventoryHoverFormat: String = "<gradient:gold:yellow><b><player>'s <type></b></gradient>\n<gradient:gray:dark_gray>Click to view</gradient>\n\n<preview>",
    
    @field:Comment("Hover text format for position placeholder")
    val positionHoverFormat: String = "<gradient:yellow:gold><b><player>'s Location</b></gradient>\n<gradient:gray:dark_gray>World: <gradient:aqua:light_purple><world></gradient></gradient>\n<gradient:gray:dark_gray>Biome: <gradient:green:dark_green><biome></gradient></gradient>\n<gradient:gray:dark_gray>Coordinates: <gradient:white:gray><x>, <y>, <z></gradient></gradient>\n<gradient:gray:dark_gray>Click to get directions</gradient>",
    
    @field:Comment("Hover text format for health placeholder")
    val healthHoverFormat: String = "<gradient:yellow:gold><b><player>'s Status</b></gradient>\n<gradient:red:dark_red>❤ <gradient:white:gray><health>/<max_health></gradient> Health</gradient>\n<gradient:gold:yellow>🍖 <gradient:white:gray><food>/20</gradient> Food</gradient>\n<gradient:yellow:gold>⚡ <gradient:white:gray><saturation></gradient> Saturation</gradient>\n<effects>",
    
    @field:Comment("Text shown when an inventory is empty")
    val emptyInventoryText: String = "<gradient:gray:dark_gray>Empty</gradient>",
    
    @field:Comment("Text shown for item preview in hover")
    val itemPreviewFormat: String = "<gradient:gray:dark_gray>•</gradient> <gradient:white:gray><amount>x <item></gradient>",
    
    @field:Comment("Text shown when there are more items than can be displayed in preview")
    val moreItemsText: String = "<gradient:gray:dark_gray>... and <gradient:white:gray><count></gradient> more</gradient>",

    @field:Comment("Message shown when a player doesn't have permission to use inventory placeholders")
    val noPermission: String = "<red>You don't have permission to use inventory placeholders!</red>",
    
    @field:Comment("Message shown when inventory placeholders are disabled in config")
    val disabled: String = "<red>Inventory placeholders are currently disabled!</red>",
    
    @field:Comment("Message shown when a specific placeholder type is disabled")
    val placeholderDisabled: String = "<red>The {type} placeholder is currently disabled!</red>",
    
    @field:Comment("Message shown when an inventory snapshot fails to load")
    val snapshotNotFound: String = "<red>Inventory snapshot not found or expired.</red>",
    
    @field:Comment("Message shown when failing to create an inventory snapshot")
    val snapshotCreationFailed: String = "<red>Failed to create inventory snapshot.</red>",
    
    @field:Comment("Message shown when failing to open an inventory view")
    val viewFailed: String = "<red>Failed to open inventory view.</red>",
    
    @field:Comment("Message shown when trying to interact with a read-only inventory")
    val readOnlyInventory: String = "<yellow>This is a read-only inventory view.</yellow>",
    
    @field:Comment("Message shown when position data is unavailable")
    val positionUnavailable: String = "<red>Position data unavailable.</red>",
    
    @field:Comment("Message shown when health data is unavailable")
    val healthUnavailable: String = "<red>Health data unavailable.</red>"
)

@ConfigSerializable
data class BlockMessages(
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
data class SwearFilterMessages(
    @field:Comment("Alert message format for swear filter violations")
    val alertMessage: String = "<red>[ALERT]</red> <yellow><player_name></yellow> <gray>triggered swear filter</gray> <gold>'<group_name>'</gold> <gray>with message:</gray> <white>'<original_message>'</white>",
    
    @field:Comment("Console alert message format")
    val consoleAlertMessage: String = "[ALERT] <player_name> triggered <group_name> filter: <original_message>",

    @field:Comment("Message shown when alerts system is disabled")
    val alertsDisabled: String = "<red>The alert system is disabled in the configuration.</red>",
    
    @field:Comment("Message shown when a player lacks permission to receive alerts")
    val alertsNoPermission: String = "<red>You don't have permission to receive swear filter alerts.</red>",
    
    @field:Comment("Message shown when alerts are enabled for a player")
    val alertsEnabled: String = "<green>Swear filter alerts enabled. You will now be notified of violations.</green>",
    
    @field:Comment("Message shown when alerts are disabled for a player")
    val alertsDisabledPersonal: String = "<red>Swear filter alerts disabled. You will no longer receive notifications.</red>",
    
    @field:Comment("Message shown when alerts are automatically enabled on join")
    val alertsAutoEnabled: String = "",
    
    @field:Comment("Message shown to players when their message is blocked by the swear filter")
    val blockedMessage: String = "<red>Your message could not be sent.</red>"
)

@ConfigSerializable
data class CommandMessages(
    @field:Comment("Message shown when a command can only be used by players")
    val playerOnly: String = "<red>This command can only be used by players!</red>",
    
    @field:Comment("Message shown when a player lacks permission for a command")
    val noPermission: String = "<red>You don't have permission to use this command!</red>",
    
    @field:Comment("Message shown when configuration is successfully reloaded")
    val reloadSuccess: String = "<green>Configuration reloaded successfully!</green>",
    
    @field:Comment("Message shown when configuration reload fails")
    val reloadFailed: String = "<red>Failed to reload configuration. Check console for errors.</red>",
    
    @field:Comment("Message shown when a player is not found")
    val playerNotFound: String = "<red>Player '<recipient>' is not online!</red>",
    
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
data class SystemMessages(
    @field:Comment("Message shown for general errors")
    val error: String = "<red>An error occurred. Please try again.</red>",
    
    @field:Comment("Message shown when an operation is successful")
    val success: String = "<green>Operation completed successfully!</green>",
    
    @field:Comment("Message shown when data is cleared")
    val dataCleared: String = "<green>Cleared <type>!</green>",
    
    @field:Comment("Message shown for invalid usage")
    val invalidUsage: String = "<red>Usage: <usage></red>"
)
