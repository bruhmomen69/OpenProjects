package bruh.zchat.paper.commands

import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import bruh.zchat.paper.services.PrivateMessageService
import bruh.zchat.paper.services.ChatToggleService
import bruh.zchat.paper.services.SocialSpyService
import bruh.zchat.paper.services.MessageFormattingService
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Values

/**
 * Commands for private messaging, chat toggle, and social spy functionality
 */
class MessageCommand(
    private val privateMessageService: PrivateMessageService,
    private val messageFormattingService: MessageFormattingService
) {
    @Command("msg", "message", "tell", "whisper", "w")
    fun msg(actor: BukkitCommandActor, recipient: String, message: String) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }
        
        val player = actor.sender() as Player
        privateMessageService.sendPrivateMessage(player, recipient, message)
    }

    @Command("reply", "r", "respond")
    fun reply(actor: BukkitCommandActor, message: String) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }

        val player = actor.sender() as Player
        privateMessageService.replyToLastSender(player, message)
    }
}
/**
 * Chat toggle commands as subcommands of the main chatplugin command
 */
@Command("chatplugin toggle", "zealouschat toggle", "zchat toggle", "chattoggle", "ct", "chatstatus")
class ChatToggleCommands(
    private val chatToggleService: ChatToggleService,
    private val socialSpyService: SocialSpyService,
    private val privateMessageService: PrivateMessageService,
    private val messageFormattingService: MessageFormattingService
) {
    
    @Subcommand("chat")
    fun toggleChat(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }
        
        val player = actor.sender() as Player
        chatToggleService.toggleChat(player)
    }
    
    /**
     * Toggle private messages for the executing player.
     * This command allows players to enable or disable their private message reception.
     * Only players can execute this command.
     *
     * @param actor The command actor executing the command
     */
    @Subcommand("messages")
    fun toggleMessages(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }
        
        val player = actor.sender() as Player
        chatToggleService.togglePrivateMessages(player)
    }
    
    @Subcommand("socialspy")
    fun toggleSocialSpy(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }
        
        val player = actor.sender() as Player
        socialSpyService.toggleSocialSpy(player)
    }
    
    @Subcommand("status")
    fun status(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.reply(messageFormattingService.getConfigMessage("commands.player_only"))
            return
        }
        
        val player = actor.sender() as Player
        val chatStatus = chatToggleService.getChatStatus(player)
        val socialSpyStatus = if (socialSpyService.hasSocialSpyEnabled(player)) "enabled" else "disabled"
        val lastSender = privateMessageService.getLastSender(player)?.name ?: "none"
        
        val message = """
            <gold>===== Your Chat Status =====</gold>
            <yellow>Chat Status:</yellow> <gray>$chatStatus</gray>
            <yellow>Social Spy:</yellow> <gray>$socialSpyStatus</gray>
            <yellow>Last Message From:</yellow> <gray>$lastSender</gray>
        """.trimIndent()
        
        actor.reply(MiniMessage.miniMessage().deserialize(message))
    }
}

/**
 * Admin commands for managing chat features
 */
@Command("chatplugin admin", "zealouschat admin", "zchat admin")
class ChatAdminCommands(
    private val chatToggleService: ChatToggleService,
    private val socialSpyService: SocialSpyService,
    private val privateMessageService: PrivateMessageService,
    private val messageFormattingService: MessageFormattingService
) {
    @Subcommand("toggle chat")
    fun adminToggleChat(actor: BukkitCommandActor, playerName: String, enable: Boolean) {
        val targetPlayer = org.bukkit.Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Player '$playerName' is not online!</red>"))
            return
        }
        
        if (enable) {
            chatToggleService.forceEnableChat(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<green>Enabled chat for ${targetPlayer.name}</green>"))
        } else {
            chatToggleService.forceDisableChat(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Disabled chat for ${targetPlayer.name}</red>"))
        }
    }
    
    @Subcommand("toggle messages")
    fun adminToggleMessages(actor: BukkitCommandActor, playerName: String, enable: Boolean) {
        val targetPlayer = org.bukkit.Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Player '$playerName' is not online!</red>"))
            return
        }
        
        if (enable) {
            chatToggleService.forceEnableMessages(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<green>Enabled private messages for ${targetPlayer.name}</green>"))
        } else {
            chatToggleService.forceDisableMessages(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Disabled private messages for ${targetPlayer.name}</red>"))
        }
    }
    
    @Subcommand("toggle all")
    fun adminToggleAll(actor: BukkitCommandActor, playerName: String, enable: Boolean) {
        val targetPlayer = org.bukkit.Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Player '$playerName' is not online!</red>"))
            return
        }
        
        if (enable) {
            chatToggleService.forceEnableAll(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<green>Enabled chat and messages for ${targetPlayer.name}</green>"))
        } else {
            chatToggleService.forceDisableAll(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Disabled chat and messages for ${targetPlayer.name}</red>"))
        }
    }
    
    @Subcommand("socialspy")
    fun adminSocialSpy(actor: BukkitCommandActor, playerName: String, enable: Boolean) {
        val targetPlayer = org.bukkit.Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Player '$playerName' is not online!</red>"))
            return
        }
        
        if (enable) {
            if (socialSpyService.forceEnableSocialSpy(targetPlayer)) {
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Enabled social spy for ${targetPlayer.name}</green>"))
            } else {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>${targetPlayer.name} doesn't have permission for social spy!</red>"))
            }
        } else {
            socialSpyService.forceDisableSocialSpy(targetPlayer)
            actor.reply(MiniMessage.miniMessage().deserialize("<red>Disabled social spy for ${targetPlayer.name}</red>"))
        }
    }
    
    @Subcommand("stats")
    fun adminStats(actor: BukkitCommandActor) {
        val toggleStats = chatToggleService.getToggleStats()
        val spyStats = socialSpyService.getSocialSpyStats()
        
        val message = """
            <gold>===== Chat Plugin Statistics =====</gold>
            <yellow>Chat Toggles:</yellow>
            <gray>  - Chat Disabled: ${toggleStats["chat_disabled"]}</gray>
            <gray>  - Messages Disabled: ${toggleStats["messages_disabled"]}</gray>
            <gray>  - Total Online: ${toggleStats["total_online"]}</gray>
            
            <yellow>Social Spy:</yellow>
            <gray>  - Total Spy Users: ${spyStats["total_spy_users"]}</gray>
            <gray>  - Online Spy Users: ${spyStats["online_spy_users"]}</gray>
            <gray>  - Active Spies: ${(spyStats["spy_user_names"] as List<*>).joinToString(", ")}</gray>
        """.trimIndent()
        
        actor.reply(MiniMessage.miniMessage().deserialize(message))
    }
    
    @Subcommand("clear")
    fun adminClear(actor: BukkitCommandActor, @Values("toggles", "socialspy", "cooldowns", "all") type: String) {
        when (type.lowercase()) {
            "toggles" -> {
                chatToggleService.clearAllToggles()
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Cleared all chat toggles!</green>"))
            }
            "socialspy" -> {
                socialSpyService.clearAllSocialSpy()
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Cleared all social spy states!</green>"))
            }
            "cooldowns" -> {
                privateMessageService.clearAllCooldowns()
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Cleared all message cooldowns!</green>"))
            }
            "all" -> {
                chatToggleService.clearAllToggles()
                socialSpyService.clearAllSocialSpy()
                privateMessageService.clearAllCooldowns()
                actor.reply(MiniMessage.miniMessage().deserialize("<green>Cleared all chat data!</green>"))
            }
            else -> {
                actor.reply(MiniMessage.miniMessage().deserialize("<red>Usage: /chatplugin admin clear <toggles|socialspy|cooldowns|all></red>"))
            }
        }
    }
}