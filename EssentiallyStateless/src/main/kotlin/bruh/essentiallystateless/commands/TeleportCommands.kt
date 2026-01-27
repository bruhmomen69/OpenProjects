package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for teleportation.
 */
class TeleportCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {

    @Command("tp", "teleport")
    @CommandPermission("essentiallystateless.tp")
    suspend fun tp(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        @Optional @SuggestOnlinePlayer playerToTeleport: String?
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val playerToMove = if (playerToTeleport != null) {
            val player = Bukkit.getPlayer(playerToTeleport)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", playerToTeleport)
                })
                return
            }
            player
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        withContext(plugin.entityDispatcher(playerToMove)) {
            playerToMove.teleportAsync(target.location)
        }

        if (playerToMove == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.TP_SUCCESS) {
                unparsed("target", target.name)
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.TP_OTHER) {
                unparsed("player", playerToMove.name)
                unparsed("target", target.name)
            })
        }
    }

    @Command("tphere", "s", "tph")
    @CommandPermission("essentiallystateless.tphere")
    suspend fun tphere(actor: BukkitCommandActor, @SuggestOnlinePlayer targetName: String) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        withContext(plugin.entityDispatcher(target)) {
            target.teleportAsync(player.location)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.TPHERE_SUCCESS) {
            unparsed("player", target.name)
        })
    }

    @Command("tpall")
    @CommandPermission("essentiallystateless.tpall")
    suspend fun tpall(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = if (targetName != null) {
            val player = Bukkit.getPlayer(targetName)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            player
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        val playersToTeleport = Bukkit.getOnlinePlayers().filter { it != target }

        // Teleport all players - teleportAsync is already async, no context needed
        playersToTeleport.map { player ->
            player.teleportAsync(target.location)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.TPALL_SUCCESS) {
            unparsed("count", playersToTeleport.size.toString())
            unparsed("target", target.name)
        })
    }

    @Command("tppos")
    @CommandPermission("essentiallystateless.tppos")
    suspend fun tppos(
        actor: BukkitCommandActor,
        x: Double,
        y: Double,
        z: Double,
        @Optional @SuggestWorld worldName: String?,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        val target = if (targetName != null) {
            val player = Bukkit.getPlayer(targetName)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            player
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        val world = if (worldName != null) {
            val w = Bukkit.getWorld(worldName)
            if (w == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                    unparsed("world", worldName)
                })
                return
            }
            w
        } else {
            target.world
        }

        val location = Location(world, x, y, z, target.location.yaw, target.location.pitch)

        withContext(plugin.entityDispatcher(target)) {
            target.teleportAsync(location)
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.TPPOS_SUCCESS) {
                unparsed("x", x.toInt().toString())
                unparsed("y", y.toInt().toString())
                unparsed("z", z.toInt().toString())
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.TPPOS_OTHER) {
                unparsed("player", target.name)
                unparsed("x", x.toInt().toString())
                unparsed("y", y.toInt().toString())
                unparsed("z", z.toInt().toString())
            })
        }
    }

    @Command("top")
    @CommandPermission("essentiallystateless.top")
    suspend fun top(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.regionDispatcher(player.location)) {
            val highestY = player.world.getHighestBlockYAt(player.location)
            val newLocation = Location(
                player.world,
                player.location.x,
                highestY + 1.0,
                player.location.z,
                player.location.yaw,
                player.location.pitch
            )
            withContext(plugin.entityDispatcher(player)) {
                player.teleportAsync(newLocation)
            }
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.TOP_SUCCESS))
    }

    @Command("bottom")
    @CommandPermission("essentiallystateless.bottom")
    suspend fun bottom(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.regionDispatcher(player.location)) {
            var foundY: Int? = null
            val minY = player.world.minHeight
            
            for (y in minY until player.location.blockY) {
                val block = player.world.getBlockAt(player.location.blockX, y, player.location.blockZ)
                val blockAbove = player.world.getBlockAt(player.location.blockX, y + 1, player.location.blockZ)
                val blockAbove2 = player.world.getBlockAt(player.location.blockX, y + 2, player.location.blockZ)
                
                if (block.type.isSolid && 
                    blockAbove.type == Material.AIR && 
                    blockAbove2.type == Material.AIR) {
                    foundY = y + 1
                    break
                }
            }

            if (foundY != null) {
                val newLocation = Location(
                    player.world,
                    player.location.x,
                    foundY.toDouble(),
                    player.location.z,
                    player.location.yaw,
                    player.location.pitch
                )
                withContext(plugin.entityDispatcher(player)) {
                    player.teleportAsync(newLocation)
                }
                player.sendMessage(translations.getComponent(CommandMessages.BOTTOM_SUCCESS))
            } else {
                player.sendMessage(translations.getComponent(CommandMessages.BOTTOM_FAILED))
            }
        }
    }

    @Command("jump", "j")
    @CommandPermission("essentiallystateless.jump")
    suspend fun jump(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val targetBlock = player.getTargetBlockExact(256)
        if (targetBlock == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.JUMP_FAILED))
            return
        }

        val location = targetBlock.location.add(0.5, 1.0, 0.5)
        location.yaw = player.location.yaw
        location.pitch = player.location.pitch

        withContext(plugin.entityDispatcher(player)) {
            player.teleportAsync(location)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.JUMP_SUCCESS))
    }
}
