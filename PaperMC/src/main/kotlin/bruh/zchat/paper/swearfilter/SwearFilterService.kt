package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.services.AlertService
import bruh.zchat.paper.utils.Levenshtein
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class SwearFilterService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val infractionManager: InfractionManager,
    private val alertService: AlertService,
    private val messageFormattingService: MessageFormattingService
) {
    private val regexCache = ConcurrentHashMap<String, Regex>()

    fun checkMessage(player: Player, message: String): Boolean {
        if (!configManager.config.swearFilter.enabled) {
            return false
        }
        
        // Check bypass permission
        if (player.hasPermission(configManager.config.permissions.swearFilterBypassPermission)) {
            return false
        }

        for (group in configManager.config.swearFilter.filterGroups) {
            if (isMatch(group, message)) {
                // Send blocked message to player if enabled
                if (configManager.config.swearFilter.enableBlockedMessage) {
                    plugin.launch(plugin.globalRegionDispatcher) {
                        val blockedMessage = messageFormattingService.getConfigMessage(
                            "swearFilter.blockedMessage", 
                            player
                        )
                        player.sendMessage(blockedMessage)
                    }
                }
                
                plugin.launch(plugin.asyncDispatcher) {
                    handleInfraction(player, message, group)
                }
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

    private suspend fun handleInfraction(player: Player, originalMessage: String, group: FilterGroup) {
        val newInfractionCount = infractionManager.addInfraction(player.uniqueId, group.name)
        
        // Send alert BEFORE punishments
        plugin.launch(plugin.globalRegionDispatcher) {
            alertService.sendViolationAlert(player, originalMessage, group, newInfractionCount)
        }
        
        val punishments = group.punishments[newInfractionCount]

        if (punishments != null) {
            for (command in punishments) {
                val formattedCommand = command.replace("{player}", player.name)
                plugin.launch(plugin.globalRegionDispatcher) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand)
                }
            }
        }
    }
}
