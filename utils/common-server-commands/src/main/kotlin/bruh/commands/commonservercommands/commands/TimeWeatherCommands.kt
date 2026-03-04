package bruh.commands.commonservercommands.commands

import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.entityDispatcher
import bruh.commands.commonservercommands.regionDispatcher
import bruh.commands.commonservercommands.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for time and weather management.
 */
class TimeWeatherCommands(
    private val plugin: CommandPlugin,
    private val translations: TranslationAPI
) {

    @Command("time")
    @CommandPermission("essentiallystateless.time")
    suspend fun time(
        actor: BukkitCommandActor,
        @Optional timeValue: String?,
        @Optional @SuggestWorld worldName: String?
    ) {
        val world = resolveWorld(actor, worldName) ?: return

        if (timeValue == null) {
            // Query time
            val ticks = world.time
            val timeOfDay = ticksToTimeString(ticks)
            actor.sender().sendMessage(translations.getComponent(CommandMessages.TIME_QUERY) {
                unparsed("world", world.name)
                unparsed("time", timeOfDay)
                unparsed("ticks", ticks.toString())
            })
            return
        }

        val ticks = parseTime(timeValue)
        if (ticks < 0) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                unparsed("value", timeValue)
            })
            return
        }

        withContext(plugin.regionDispatcher(world.spawnLocation)) {
            world.time = ticks
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.TIME_SET) {
            unparsed("world", world.name)
            unparsed("time", ticksToTimeString(ticks))
        })
    }

    @Command("day")
    @CommandPermission("essentiallystateless.time")
    suspend fun day(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        time(actor, "day", worldName)
    }

    @Command("night")
    @CommandPermission("essentiallystateless.time")
    suspend fun night(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        time(actor, "night", worldName)
    }

    @Command("noon")
    @CommandPermission("essentiallystateless.time")
    suspend fun noon(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        time(actor, "noon", worldName)
    }

    @Command("midnight")
    @CommandPermission("essentiallystateless.time")
    suspend fun midnight(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        time(actor, "midnight", worldName)
    }

    @Command("weather")
    @CommandPermission("essentiallystateless.weather")
    suspend fun weather(
        actor: BukkitCommandActor,
        weatherType: String,
        @Optional @SuggestWorld worldName: String?
    ) {
        val world = resolveWorld(actor, worldName) ?: return

        val weatherName = when (weatherType.lowercase()) {
            "clear", "sun", "sunny" -> translations.getString(CommandMessages.WEATHER_CLEAR)
            "rain", "rainy" -> translations.getString(CommandMessages.WEATHER_RAIN)
            "storm", "thunder", "thunderstorm" -> translations.getString(CommandMessages.WEATHER_STORM)
            else -> {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                    unparsed("value", weatherType)
                })
                return
            }
        }

        withContext(plugin.regionDispatcher(world.spawnLocation)) {
            when (weatherType.lowercase()) {
                "clear", "sun", "sunny" -> {
                    world.setStorm(false)
                    world.isThundering = false
                }
                "rain", "rainy" -> {
                    world.setStorm(true)
                    world.isThundering = false
                }
                "storm", "thunder", "thunderstorm" -> {
                    world.setStorm(true)
                    world.isThundering = true
                }
            }
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.WEATHER_SET) {
            unparsed("world", world.name)
            unparsed("weather", weatherName)
        })
    }

    @Command("sun", "sky")
    @CommandPermission("essentiallystateless.weather")
    suspend fun sun(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        weather(actor, "clear", worldName)
    }

    @Command("rain")
    @CommandPermission("essentiallystateless.weather")
    suspend fun rain(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        weather(actor, "rain", worldName)
    }

    @Command("storm")
    @CommandPermission("essentiallystateless.weather")
    suspend fun storm(actor: BukkitCommandActor, @Optional @SuggestWorld worldName: String?) {
        weather(actor, "storm", worldName)
    }

    @Command("thunder")
    @CommandPermission("essentiallystateless.weather")
    suspend fun thunder(
        actor: BukkitCommandActor,
        @Optional enable: Boolean?,
        @Optional @SuggestWorld worldName: String?
    ) {
        val world = resolveWorld(actor, worldName) ?: return
        val shouldEnable = enable ?: !world.isThundering

        withContext(plugin.regionDispatcher(world.spawnLocation)) {
            world.isThundering = shouldEnable
            if (shouldEnable) {
                world.setStorm(true)
            }
        }

        if (shouldEnable) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.THUNDER_ENABLED) {
                unparsed("world", world.name)
            })
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.THUNDER_DISABLED) {
                unparsed("world", world.name)
            })
        }
    }

    @Command("ptime")
    @CommandPermission("essentiallystateless.ptime")
    suspend fun ptime(
        actor: BukkitCommandActor,
        @Optional timeValue: String?,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        val target = resolvePlayer(actor, targetName) ?: return

        if (timeValue == null || timeValue.equals("reset", ignoreCase = true)) {
            target.resetPlayerTime()
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PTIME_RESET) {
                unparsed("player", target.name)
            })
            return
        }

        val ticks = parseTime(timeValue)
        if (ticks < 0) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                unparsed("value", timeValue)
            })
            return
        }

        target.setPlayerTime(ticks, false)
        actor.sender().sendMessage(translations.getComponent(CommandMessages.PTIME_SET) {
            unparsed("player", target.name)
            unparsed("time", ticksToTimeString(ticks))
        })
    }

    @Command("pweather")
    @CommandPermission("essentiallystateless.pweather")
    suspend fun pweather(
        actor: BukkitCommandActor,
        @Optional weatherType: String?,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        val target = resolvePlayer(actor, targetName) ?: return

        if (weatherType == null || weatherType.equals("reset", ignoreCase = true)) {
            target.resetPlayerWeather()
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PWEATHER_RESET) {
                unparsed("player", target.name)
            })
            return
        }

        val weather = when (weatherType.lowercase()) {
            "clear", "sun", "sunny" -> org.bukkit.WeatherType.CLEAR
            "rain", "rainy", "storm", "downfall" -> org.bukkit.WeatherType.DOWNFALL
            else -> {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                    unparsed("value", weatherType)
                })
                return
            }
        }

        target.setPlayerWeather(weather)
        actor.sender().sendMessage(translations.getComponent(CommandMessages.PWEATHER_SET) {
            unparsed("player", target.name)
            unparsed("weather", weatherType)
        })
    }

    private suspend fun resolveWorld(actor: BukkitCommandActor, worldName: String?): World? {
        return if (worldName != null) {
            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                    unparsed("world", worldName)
                })
            }
            world
        } else if (actor.sender() is Player) {
            (actor.sender() as Player).world
        } else {
            Bukkit.getWorlds().firstOrNull()
        }
    }

    private suspend fun resolvePlayer(actor: BukkitCommandActor, targetName: String?): Player? {
        return if (targetName != null) {
            val player = Bukkit.getPlayer(targetName)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
            }
            player
        } else if (actor.sender() is Player) {
            actor.sender() as Player
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            null
        }
    }

    private fun parseTime(time: String): Long {
        return when (time.lowercase()) {
            "day", "morning" -> 1000L
            "noon" -> 6000L
            "afternoon" -> 9000L
            "sunset", "dusk" -> 12000L
            "night" -> 13000L
            "midnight" -> 18000L
            "sunrise", "dawn" -> 23000L
            else -> time.toLongOrNull() ?: -1
        }
    }

    private fun ticksToTimeString(ticks: Long): String {
        val hours = ((ticks / 1000 + 6) % 24).toInt()
        val minutes = ((ticks % 1000) * 60 / 1000).toInt()
        return String.format("%02d:%02d", hours, minutes)
    }
}
