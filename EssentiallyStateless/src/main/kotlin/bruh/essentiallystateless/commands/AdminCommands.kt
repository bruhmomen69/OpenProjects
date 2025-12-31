package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for server administration (kick, ban, sudo, broadcast).
 */
class AdminCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {
    private val miniMessage = MiniMessage.miniMessage()

    @Command("kick")
    @CommandPermission("essentiallystateless.kick")
    fun kick(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        @Optional reason: String?
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val kickReason = reason ?: translations.getString(CommandMessages.KICK_DEFAULT_REASON)

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.kick(miniMessage.deserialize(kickReason))
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.KICK_SUCCESS) {
            unparsed("player", target.name)
            unparsed("reason", kickReason)
        })
    }

    @Command("kickall")
    @CommandPermission("essentiallystateless.kickall")
    fun kickall(actor: BukkitCommandActor, @Optional reason: String?) {
        val kickReason = reason ?: translations.getString(CommandMessages.KICK_DEFAULT_REASON)
        var count = 0

        for (player in Bukkit.getOnlinePlayers()) {
            if (player != actor.sender()) {
                plugin.launch {
                    withContext(plugin.entityDispatcher(player)) {
                        player.kick(miniMessage.deserialize(kickReason))
                    }
                }
                count++
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.KICKALL_SUCCESS) {
            unparsed("count", count.toString())
        })
    }

    @Command("ban")
    @CommandPermission("essentiallystateless.ban")
    fun ban(
        actor: BukkitCommandActor,
        targetName: String,
        @Optional reason: String?
    ) {
        val banReason = reason ?: translations.getString(CommandMessages.BAN_DEFAULT_REASON)
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

        offlinePlayer.banPlayer(banReason, actor.name())

        // Kick if online
        val onlinePlayer = Bukkit.getPlayer(targetName)
        if (onlinePlayer != null) {
            plugin.launch {
                withContext(plugin.entityDispatcher(onlinePlayer)) {
                    onlinePlayer.kick(miniMessage.deserialize(banReason))
                }
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BAN_SUCCESS) {
            unparsed("player", targetName)
            unparsed("reason", banReason)
        })
    }

    @Command("unban", "pardon")
    @CommandPermission("essentiallystateless.unban")
    fun unban(actor: BukkitCommandActor, targetName: String) {
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

        if (!offlinePlayer.isBanned) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.UNBAN_FAILED) {
                unparsed("player", targetName)
            })
            return
        }

        Bukkit.getServer().bannedPlayers.remove(offlinePlayer)

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.UNBAN_SUCCESS) {
            unparsed("player", targetName)
        })
    }

    @Command("banip")
    @CommandPermission("essentiallystateless.banip")
    fun banip(
        actor: BukkitCommandActor,
        ip: String,
        @Optional reason: String?
    ) {
        val banReason = reason ?: translations.getString(CommandMessages.BAN_DEFAULT_REASON)

        // Check if this is a player name instead of IP
        val ipToBan = if (ip.contains(".")) {
            ip
        } else {
            val player = Bukkit.getPlayer(ip)
            player?.address?.address?.hostAddress ?: ip
        }

        Bukkit.getServer().banIP(ipToBan)

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BANIP_SUCCESS) {
            unparsed("ip", ipToBan)
            unparsed("reason", banReason)
        })
    }

    @Command("unbanip", "pardonip")
    @CommandPermission("essentiallystateless.unbanip")
    fun unbanip(actor: BukkitCommandActor, ip: String) {
        val ipBans = Bukkit.getServer().ipBans
        
        if (!ipBans.contains(ip)) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.UNBANIP_FAILED) {
                unparsed("ip", ip)
            })
            return
        }

        Bukkit.getServer().unbanIP(ip)

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.UNBANIP_SUCCESS) {
            unparsed("ip", ip)
        })
    }

    @Command("sudo")
    @CommandPermission("essentiallystateless.sudo")
    fun sudo(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        command: String
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val commandToRun = if (command.startsWith("/")) command.substring(1) else command

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.performCommand(commandToRun)
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SUDO_SUCCESS) {
            unparsed("player", target.name)
            unparsed("command", commandToRun)
        })
    }

    @Command("broadcast", "bc", "bcast")
    @CommandPermission("essentiallystateless.broadcast")
    fun broadcast(actor: BukkitCommandActor, message: String) {
        val format = plugin.config.broadcastFormat
        val formattedMessage = format.replace("<message>", message)
        val component = miniMessage.deserialize(formattedMessage)

        Bukkit.broadcast(component)

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BROADCAST_SENT))
    }

    @Command("broadcastworld")
    @CommandPermission("essentiallystateless.broadcast")
    fun broadcastworld(
        actor: BukkitCommandActor,
        @SuggestWorld worldName: String,
        message: String
    ) {
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", worldName)
            })
            return
        }

        val format = plugin.config.worldBroadcastFormat
        val formattedMessage = format
            .replace("<world>", world.name)
            .replace("<message>", message)
        val component = miniMessage.deserialize(formattedMessage)

        for (player in world.players) {
            player.sendMessage(component)
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BROADCAST_WORLD_SENT) {
            unparsed("world", world.name)
        })
    }
}
