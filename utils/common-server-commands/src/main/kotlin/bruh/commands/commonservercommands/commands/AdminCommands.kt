package bruh.commands.commonservercommands.commands

import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.entityDispatcher
import bruh.commands.commonservercommands.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for server administration (kick, ban, sudo, broadcast).
 */
class AdminCommands(
    private val plugin: CommandPlugin,
    private val translations: TranslationAPI
) {
    private val miniMessage = MiniMessage.miniMessage()

    @Command("kick")
    @CommandPermission("essentiallystateless.kick")
    suspend fun kick(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        @Optional reason: String?
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val kickReason = reason ?: translations.getString(CommandMessages.KICK_DEFAULT_REASON)

        withContext(plugin.entityDispatcher(target)) {
            target.kick(miniMessage.deserialize(kickReason))
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.KICK_SUCCESS) {
            unparsed("player", target.name)
            unparsed("reason", kickReason)
        })
    }

    @Command("kickall")
    @CommandPermission("essentiallystateless.kickall")
    suspend fun kickall(actor: BukkitCommandActor, @Optional reason: String?) {
        val kickReason = reason ?: translations.getString(CommandMessages.KICK_DEFAULT_REASON)
        val playersToKick = Bukkit.getOnlinePlayers()
            .filter { it != actor.sender() }

        // Kick all players concurrently using coroutineScope
        coroutineScope {
            playersToKick.map { player ->
                async(plugin.entityDispatcher(player)) {
                    player.kick(miniMessage.deserialize(kickReason))
                }
            }.awaitAll()
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.KICKALL_SUCCESS) {
            unparsed("count", playersToKick.size.toString())
        })
    }

    @Command("ban")
    @CommandPermission("essentiallystateless.ban")
    suspend fun ban(
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
            withContext(plugin.entityDispatcher(onlinePlayer)) {
                onlinePlayer.kick(miniMessage.deserialize(banReason))
            }
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.BAN_SUCCESS) {
            unparsed("player", targetName)
            unparsed("reason", banReason)
        })
    }

    @Command("unban", "pardon")
    @CommandPermission("essentiallystateless.unban")
    suspend fun unban(actor: BukkitCommandActor, targetName: String) {
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

        if (!offlinePlayer.isBanned) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.UNBAN_FAILED) {
                unparsed("player", targetName)
            })
            return
        }

        Bukkit.getServer().bannedPlayers.remove(offlinePlayer)

        actor.sender().sendMessage(translations.getComponent(CommandMessages.UNBAN_SUCCESS) {
            unparsed("player", targetName)
        })
    }

    @Command("banip")
    @CommandPermission("essentiallystateless.banip")
    suspend fun banip(
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

        actor.sender().sendMessage(translations.getComponent(CommandMessages.BANIP_SUCCESS) {
            unparsed("ip", ipToBan)
            unparsed("reason", banReason)
        })
    }

    @Command("unbanip", "pardonip")
    @CommandPermission("essentiallystateless.unbanip")
    suspend fun unbanip(actor: BukkitCommandActor, ip: String) {
        val ipBans = Bukkit.getServer().ipBans
        
        if (!ipBans.contains(ip)) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.UNBANIP_FAILED) {
                unparsed("ip", ip)
            })
            return
        }

        Bukkit.getServer().unbanIP(ip)

        actor.sender().sendMessage(translations.getComponent(CommandMessages.UNBANIP_SUCCESS) {
            unparsed("ip", ip)
        })
    }

    @Command("sudo")
    @CommandPermission("essentiallystateless.sudo")
    suspend fun sudo(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        command: String
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val commandToRun = if (command.startsWith("/")) command.substring(1) else command

        withContext(plugin.entityDispatcher(target)) {
            target.performCommand(commandToRun)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.SUDO_SUCCESS) {
            unparsed("player", target.name)
            unparsed("command", commandToRun)
        })
    }

    @Command("broadcast", "bc", "bcast")
    @CommandPermission("essentiallystateless.broadcast")
    suspend fun broadcast(actor: BukkitCommandActor, message: String) {
        val format = plugin.config.broadcastFormat
        val formattedMessage = format.replace("<message>", message)
        val component = miniMessage.deserialize(formattedMessage)

        Bukkit.broadcast(component)

        actor.sender().sendMessage(translations.getComponent(CommandMessages.BROADCAST_SENT))
    }

    @Command("broadcastworld")
    @CommandPermission("essentiallystateless.broadcast")
    suspend fun broadcastworld(
        actor: BukkitCommandActor,
        @SuggestWorld worldName: String,
        message: String
    ) {
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
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

        actor.sender().sendMessage(translations.getComponent(CommandMessages.BROADCAST_WORLD_SENT) {
            unparsed("world", world.name)
        })
    }
}
