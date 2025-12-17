package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import bruh.zchat.paper.utils.Levenshtein
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.regex.PatternSyntaxException

class SwearFilterService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val infractionManager: InfractionManager
) {
    private val regexCache = mutableMapOf<String, Regex>()

    fun checkMessage(player: Player, message: String): Boolean {
        if (!configManager.config.swearFilter.enabled) {
            return false
        }

        for (group in configManager.config.swearFilter.filterGroups) {
            if (isMatch(group, message)) {
                handleInfraction(player, group)
                return true
            }
        }
        return false
    }

    private fun isMatch(group: FilterGroup, message: String): Boolean {
        return when (group.type.lowercase()) {
            "regex" -> group.filters.any { pattern ->
                val regex = regexCache.getOrPut(pattern) {
                    try {
                        Regex(pattern)
                    } catch (e: PatternSyntaxException) {
                        plugin.logger.warning("Invalid regex pattern in swear filter config will be ignored: $pattern")
                        Regex("\\b\\B") // A regex that never matches.
                    }
                }
                regex.containsMatchIn(message)
            }
            "levenshtein" -> {
                val words = message.split(Regex("\\s+"))
                words.any { word ->
                    group.filters.any { filterWord ->
                        Levenshtein.distance(word.lowercase(), filterWord.lowercase()) <= group.distance
                    }
                }
            }
            else -> false
        }
    }

    private fun handleInfraction(player: Player, group: FilterGroup) {
        val newInfractionCount = infractionManager.addInfraction(player, group.name)
        val punishments = group.punishments[newInfractionCount]

        if (punishments != null) {
            for (command in punishments) {
                val formattedCommand = command.replace("{player}", player.name)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand)
                })
            }
        }
    }
}
