package bruh.commands.commonservercommands.commands

import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Commands for server and player information.
 */
class InfoCommands(
    private val plugin: CommandPlugin,
    private val translations: TranslationAPI
) {

    @Command("gc", "lag", "mem", "memory", "tps", "uptime", "entities")
    @CommandPermission("essentiallystateless.gc")
    suspend fun gc(actor: BukkitCommandActor) {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val percentUsed = ((usedMemory.toDouble() / maxMemory.toDouble()) * 100).roundToInt()

        val tps = Bukkit.getTPS()
        val tpsFormatted = "%.2f, %.2f, %.2f".format(tps[0], tps[1], tps[2])

        val uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime
        val uptime = formatDuration(Duration.ofMillis(uptimeMillis))

        val worldCount = Bukkit.getWorlds().size
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()

        var entityCount = 0
        for (world in Bukkit.getWorlds()) {
            entityCount += world.entityCount
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_HEADER))
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_MEMORY) {
            unparsed("used", usedMemory.toString())
            unparsed("max", maxMemory.toString())
            unparsed("percent", percentUsed.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_TPS) {
            unparsed("tps", tpsFormatted)
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_UPTIME) {
            unparsed("uptime", uptime)
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_WORLDS) {
            unparsed("count", worldCount.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_PLAYERS) {
            unparsed("online", onlinePlayers.toString())
            unparsed("max", maxPlayers.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GC_ENTITIES) {
            unparsed("count", entityCount.toString())
        })
    }

    @Command("list", "who", "online", "playerlist")
    @CommandPermission("essentiallystateless.list")
    suspend fun list(actor: BukkitCommandActor) {
        val players = Bukkit.getOnlinePlayers()
        val maxPlayers = Bukkit.getMaxPlayers()

        actor.sender().sendMessage(translations.getComponent(CommandMessages.LIST_HEADER) {
            unparsed("count", players.size.toString())
            unparsed("max", maxPlayers.toString())
        })

        if (players.isEmpty()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.LIST_EMPTY))
        } else {
            val playerNames = players.joinToString(", ") { it.name }
            actor.sender().sendMessage(translations.getComponent(CommandMessages.LIST_PLAYERS) {
                unparsed("players", playerNames)
            })
        }
    }

    @Command("whois")
    @CommandPermission("essentiallystateless.whois")
    suspend fun whois(actor: BukkitCommandActor, @SuggestOnlinePlayer targetName: String) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val ip = target.address?.address?.hostAddress ?: "Unknown"
        val maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_HEADER) {
            unparsed("player", target.name)
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_UUID) {
            unparsed("uuid", target.uniqueId.toString())
        })
        if (actor.sender().hasPermission("essentiallystateless.whois.ip")) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_IP) {
                unparsed("ip", ip)
            })
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_GAMEMODE) {
            unparsed("mode", target.gameMode.name.lowercase())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_HEALTH) {
            unparsed("health", "%.1f".format(target.health))
            unparsed("max", "%.1f".format(maxHealth))
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_FOOD) {
            unparsed("food", target.foodLevel.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_LEVEL) {
            unparsed("level", target.level.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_LOCATION) {
            unparsed("world", target.world.name)
            unparsed("x", target.location.blockX.toString())
            unparsed("y", target.location.blockY.toString())
            unparsed("z", target.location.blockZ.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_FLYING) {
            unparsed("flying", target.isFlying.toString())
        })
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WHOIS_OP) {
            unparsed("op", target.isOp.toString())
        })
    }

    @Command("near")
    @CommandPermission("essentiallystateless.near")
    suspend fun near(actor: BukkitCommandActor, @Optional radius: Int?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val searchRadius = radius ?: plugin.config.nearRadius

        actor.sender().sendMessage(translations.getComponent(CommandMessages.NEAR_HEADER) {
            unparsed("radius", searchRadius.toString())
        })

        var found = false
        for (other in Bukkit.getOnlinePlayers()) {
            if (other == player) continue
            if (other.world != player.world) continue

            val distance = player.location.distance(other.location)
            if (distance <= searchRadius) {
                found = true
                actor.sender().sendMessage(translations.getComponent(CommandMessages.NEAR_PLAYER) {
                    unparsed("player", other.name)
                    unparsed("distance", "%.1f".format(distance))
                })
            }
        }

        if (!found) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.NEAR_NONE))
        }
    }

    @Command("seen")
    @CommandPermission("essentiallystateless.seen")
    suspend fun seen(actor: BukkitCommandActor, targetName: String) {
        val online = Bukkit.getPlayer(targetName)
        if (online != null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.SEEN_ONLINE) {
                unparsed("player", online.name)
            })
            return
        }

        val offline = Bukkit.getOfflinePlayer(targetName)
        if (!offline.hasPlayedBefore()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.SEEN_NEVER) {
                unparsed("player", targetName)
            })
            return
        }

        val lastPlayed = offline.lastPlayed
        val duration = Duration.between(Instant.ofEpochMilli(lastPlayed), Instant.now())
        val timeAgo = formatDuration(duration)

        actor.sender().sendMessage(translations.getComponent(CommandMessages.SEEN_OFFLINE) {
            unparsed("player", offline.name ?: targetName)
            unparsed("time", timeAgo)
        })
    }

    @Command("ping", "pong")
    @CommandPermission("essentiallystateless.ping")
    suspend fun ping(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
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

        val ping = target.ping

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PING_SELF) {
                unparsed("ping", ping.toString())
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PING_OTHER) {
                unparsed("player", target.name)
                unparsed("ping", ping.toString())
            })
        }
    }

    @Command("getpos", "coords", "position")
    @CommandPermission("essentiallystateless.getpos")
    suspend fun getpos(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
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

        val loc = target.location

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.GETPOS_SELF) {
                unparsed("x", "%.2f".format(loc.x))
                unparsed("y", "%.2f".format(loc.y))
                unparsed("z", "%.2f".format(loc.z))
                unparsed("world", loc.world.name)
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.GETPOS_OTHER) {
                unparsed("player", target.name)
                unparsed("x", "%.2f".format(loc.x))
                unparsed("y", "%.2f".format(loc.y))
                unparsed("z", "%.2f".format(loc.z))
                unparsed("world", loc.world.name)
            })
        }
    }

    @Command("compass", "direction")
    @CommandPermission("essentiallystateless.compass")
    suspend fun compass(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val yaw = ((player.location.yaw % 360) + 360) % 360
        val direction = when {
            yaw >= 337.5 || yaw < 22.5 -> "South"
            yaw >= 22.5 && yaw < 67.5 -> "Southwest"
            yaw >= 67.5 && yaw < 112.5 -> "West"
            yaw >= 112.5 && yaw < 157.5 -> "Northwest"
            yaw >= 157.5 && yaw < 202.5 -> "North"
            yaw >= 202.5 && yaw < 247.5 -> "Northeast"
            yaw >= 247.5 && yaw < 292.5 -> "East"
            yaw >= 292.5 && yaw < 337.5 -> "Southeast"
            else -> "Unknown"
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.COMPASS_DIRECTION) {
            unparsed("direction", direction)
            unparsed("yaw", "%.1f".format(yaw))
        })
    }

    @Command("depth")
    @CommandPermission("essentiallystateless.depth")
    suspend fun depth(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val y = player.location.blockY
        val seaLevel = player.world.seaLevel

        val status = when {
            y > seaLevel -> translations.getString(CommandMessages.DEPTH_ABOVE_SEA)
            y == seaLevel -> translations.getString(CommandMessages.DEPTH_SEA_LEVEL)
            else -> translations.getString(CommandMessages.DEPTH_BELOW_SEA)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.DEPTH_LEVEL) {
            unparsed("y", y.toString())
            unparsed("status", status)
        })
    }

    @Command("playtime")
    @CommandPermission("essentiallystateless.playtime")
    suspend fun playtime(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = if (targetName != null) {
            val player = Bukkit.getPlayer(targetName)
            if (player == null) {
                // Try offline player
                val offline = Bukkit.getOfflinePlayer(targetName)
                if (!offline.hasPlayedBefore()) {
                    actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                        unparsed("player", targetName)
                    })
                    return
                }
                offline
            } else {
                player
            }
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        val ticksPlayed = target.getStatistic(Statistic.PLAY_ONE_MINUTE)
        val duration = Duration.ofSeconds(ticksPlayed / 20L)
        val timeFormatted = formatDuration(duration)

        val name = when (target) {
            is Player -> target.name
            else -> target.name ?: targetName ?: "Unknown"
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYTIME_SELF) {
                unparsed("time", timeFormatted)
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYTIME_OTHER) {
                unparsed("player", name)
                unparsed("time", timeFormatted)
            })
        }
    }

    private fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }
}
