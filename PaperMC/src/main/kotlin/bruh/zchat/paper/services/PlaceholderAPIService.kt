package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Service for optional PlaceholderAPI integration.
 * Bridges PlaceholderAPI placeholders to MiniMessage's TagResolver system.
 */
class PlaceholderAPIService(private val configManager: ConfigManager) {
    private val logger = LoggerFactory.getLogger(PlaceholderAPIService::class.java)
    private var placeholderAPIAvailable = false
    private val modernPlaceholderPattern = Pattern.compile("<([^<>]+)>")
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val legacyAmpersandSerializer = LegacyComponentSerializer.legacyAmpersand()

    init {
        checkPlaceholderAPIAvailability()
    }

    /**
     * Check if PlaceholderAPI is available and enabled
     */
    private fun checkPlaceholderAPIAvailability() {
        placeholderAPIAvailable = try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
        } catch (e: ClassNotFoundException) {
            false
        }

        if (placeholderAPIAvailable) {
            logger.info("PlaceholderAPI detected and enabled - external placeholders will be processed")
        } else {
            logger.info("PlaceholderAPI not found - only built-in placeholders will be available")
        }
    }

    /**
     * Check if PlaceholderAPI integration is enabled and available
     */
    fun isEnabled(): Boolean {
        return configManager.config.placeholders.enablePlaceholderAPI && placeholderAPIAvailable
    }

    /**
     * Create a TagResolver for PlaceholderAPI placeholders
     * This bridges PlaceholderAPI's %placeholder% format to MiniMessage's <placeholder> format
     */
    fun createPlaceholderAPIResolver(player: Player, text: String): Pair<TagResolver, String> {
        if (!isEnabled()) {
            logger.debug("PlaceholderAPI integration is disabled")
            return Pair(TagResolver.empty(), text)
        }

        val resolvers = mutableListOf<TagResolver>()

        try {
            // Find all PlaceholderAPI placeholders in the text
            val matcher2 = modernPlaceholderPattern.matcher(text)
            val foundPlaceholders = mutableSetOf<String>()

            while (matcher2.find()) {
                val placeholder = matcher2.group(1)
                logger.debug("Found modern PlaceholderAPI placeholder '$placeholder' for player ${player.name}: ```${matcher2.groupCount()}```")
                foundPlaceholders.add(placeholder)
            }

            if (foundPlaceholders.isEmpty()) {
                logger.debug("No PlaceholderAPI placeholders found for player ${player.name} ($text)")
            }

            // Process each unique placeholder
            for (placeholder in foundPlaceholders) {
                val result = processPlaceholderAPI(player, "%$placeholder%")
                if (result != "%$placeholder%") { // Only add if placeholder was actually resolved
                    resolvers.add(
                        Placeholder.parsed(
                            placeholder, MiniMessage.miniMessage().serializeOr(
                                legacyAmpersandSerializer.deserialize(result), result
                            )!!
                        )
                    )
                }
            }

        } catch (e: Exception) {
            logger.warn("Error processing PlaceholderAPI placeholders for player ${player.name}", e)
        }

        resolvers.add(
            TagResolver.resolver(
                "papi"
            ) { argumentQueue: ArgumentQueue, context: Context ->
                val rawStr = argumentQueue.pop()
                val papiStr = "%$rawStr%"
                val result = processPlaceholderAPI(player, papiStr)

                Tag.inserting(legacySerializer.deserialize(result))
            })

        return Pair(TagResolver.resolver(resolvers), processPlaceholderAPI(player, text))
    }

    /**
     * Process a single PlaceholderAPI placeholder
     */
    fun processPlaceholderAPI(player: Player, placeholder: String): String {
        return try {
            PlaceholderAPI.setPlaceholders(player, placeholder)
        } catch (e: Exception) {
            logger.warn("Failed to process PlaceholderAPI placeholder '$placeholder' for player ${player.name}: ${e.message}")
            placeholder // Return original if processing fails
        }
    }

    /**
     * Process a text string with PlaceholderAPI placeholders
     * This is a fallback method for direct string processing
     */
    fun processText(player: Player, text: String): String {
        if (!isEnabled()) {
            return text
        }

        return try {
            val placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val setPlaceholdersMethod =
                placeholderAPIClass.getMethod("setPlaceholders", Player::class.java, String::class.java)
            setPlaceholdersMethod.invoke(null, player, text) as String
        } catch (e: Exception) {
            logger.warn("Error processing PlaceholderAPI text for player ${player.name}", e)
            text
        }
    }

    /**
     * Lightweight helper used for channel identifier resolution.
     */
    fun parsePlaceholders(player: Player, text: String): String {
        return processText(player, text)
    }

    /**
     * Get available PlaceholderAPI placeholders (for debugging/info purposes)
     */
    fun getAvailablePlaceholders(): List<String> {
        if (!isEnabled()) {
            return emptyList()
        }

        return try {
            val placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val getRegisteredIdentifiersMethod = placeholderAPIClass.getMethod("getRegisteredIdentifiers")
            @Suppress("UNCHECKED_CAST")
            (getRegisteredIdentifiersMethod.invoke(null) as Set<String>).toList()
        } catch (e: Exception) {
            logger.warn("Error getting PlaceholderAPI identifiers", e)
            emptyList()
        }
    }

    /**
     * Reload PlaceholderAPI service (called when config is reloaded)
     */
    fun reload() {
        checkPlaceholderAPIAvailability()
        logger.info("PlaceholderAPI service reloaded - enabled: ${isEnabled()}")
    }
}