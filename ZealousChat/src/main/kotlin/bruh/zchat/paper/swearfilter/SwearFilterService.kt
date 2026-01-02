package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.services.AlertService
import bruh.zchat.paper.utils.Levenshtein
import bruh.zchat.paper.utils.DiceSorensen
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException
import kotlin.math.ceil
import kotlin.math.max

class SwearFilterService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val infractionManager: InfractionManager,
    private val alertService: AlertService,
    private val messageFormattingService: MessageFormattingService
) {
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Checks if a string contains regex metacharacters.
     */
    private fun hasRegexMetaChars(text: String): Boolean {
        val regexMetaChars = setOf('.', '*', '+', '?', '^', '$', '[', ']', '(', ')', '{', '}', '|', '\\')
        return text.any { it in regexMetaChars }
    }

    /**
     * Checks if an exclusion pattern matches the given text.
     * Uses simple case-insensitive contains if no regex metacharacters present.
     * Uses regex matching if regex metacharacters are present.
     */
    private fun exclusionMatches(exclusion: String, text: String): Boolean {
        return if (hasRegexMetaChars(exclusion)) {
            try {
                Regex(exclusion, RegexOption.IGNORE_CASE).containsMatchIn(text)
            } catch (e: PatternSyntaxException) {
                plugin.logger.warning("Invalid regex exclusion pattern will be ignored: $exclusion")
                false
            }
        } else {
            text.lowercase().contains(exclusion.lowercase())
        }
    }

    /**
     * Extracts the full word containing the given match range.
     */
    private fun extractSurroundingWord(text: String, matchRange: IntRange): String {
        var start = matchRange.first
        var end = matchRange.last + 1

        // Find word start (go backwards until we hit a non-word character or string boundary)
        while (start > 0 && text[start - 1].isLetterOrDigit()) {
            start--
        }

        // Find word end (go forwards until we hit a non-word character or string boundary)
        while (end < text.length && text[end].isLetterOrDigit()) {
            end++
        }

        return text.substring(start, end)
    }

    /**
     * Checks if the given matched text has an exclusion that applies.
     * For regex filter types: checks against the matched text.
     * For non-regex filter types: checks if the matched word contains any exclusion.
     */
    private fun hasExclusion(group: FilterGroup, matchedText: String): Boolean {
        if (group.exclusions.isEmpty()) {
            return false
        }

        // Check the matched text against all exclusions
        return group.exclusions.any { exclusion ->
            exclusionMatches(exclusion, matchedText)
        }
    }

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
                            MessageKey.SWEAR_FILTER_BLOCKED_MESSAGE, 
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
            "regex" -> {
                group.filters.any { pattern ->
                    val regex = regexCache.getOrPut(pattern) {
                        try {
                            Regex(pattern)
                        } catch (e: PatternSyntaxException) {
                            plugin.logger.warning("Invalid regex pattern in swear filter config will be ignored: $pattern")
                            Regex("\\b\\B") // A regex that never matches.
                        }
                    }
                    regex.findAll(message).any { match ->
                        val matchedText = match.value
                        val surroundingWord = extractSurroundingWord(message, match.range)

                        // Check exclusions for both the matched text and the surrounding word
                        // Only block if neither has an exclusion
                        !hasExclusion(group, matchedText) && !hasExclusion(group, surroundingWord)
                    }
                }
            }

            "levenshtein" -> {
                val words = message.split(Regex("\\s+"))
                // Find all words that match the filter
                val matchedWords = words.filter { word ->
                    group.filters.any { filterWord ->
                        Levenshtein.distance(word.lowercase(), filterWord.lowercase()) <= group.distance
                    }
                }
                // Check if any matched word doesn't have an exclusion
                matchedWords.any { matchedWord ->
                    !hasExclusion(group, matchedWord)
                }
            }

            "dice-sorensen", "dice" -> {
                val threshold = group.distance / 100.0
                val words = message.split(Regex("\\s+"))
                // Find all words that match the filter
                val matchedWords = words.filter { word ->
                    group.filters.any { filterWord ->
                        DiceSorensen.coefficient(word.lowercase(), filterWord.lowercase()) >= threshold
                    }
                }
                // Check if any matched word doesn't have an exclusion
                matchedWords.any { matchedWord ->
                    !hasExclusion(group, matchedWord)
                }
            }

            "smart", "mixed", "auto" -> {
                val diceThreshold = (1.0 - (group.distance / 7.0)).coerceIn(0.20, 0.95)
                val scalingFactor = group.distance / 5.0

                val words = message.split(Regex("\\s+"))
                // Find all words that match the filter
                val matchedWords = words.filter { word ->
                    group.filters.any { filterWord ->
                        val lowerWord = word.lowercase()
                        val lowerFilter = filterWord.lowercase()

                        val scaledLevenshtein = max(
                            group.distance,
                            ceil(lowerFilter.length * scalingFactor).toInt()
                        )

                        val levenshteinMatch = Levenshtein.distance(lowerWord, lowerFilter) <= scaledLevenshtein
                        val diceMatch = DiceSorensen.coefficient(lowerWord, lowerFilter) >= diceThreshold

                        levenshteinMatch || diceMatch
                    }
                }
                // Check if any matched word doesn't have an exclusion
                matchedWords.any { matchedWord ->
                    !hasExclusion(group, matchedWord)
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
