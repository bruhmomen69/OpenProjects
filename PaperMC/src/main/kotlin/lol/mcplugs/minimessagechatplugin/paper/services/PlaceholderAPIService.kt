package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
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
    private val placeholderPattern = Pattern.compile("%([^%]+)%")
    
    init {
        checkPlaceholderAPIAvailability()
    }
    
    /**
     * Check if PlaceholderAPI is available and enabled
     */
    private fun checkPlaceholderAPIAvailability() {
        placeholderAPIAvailable = try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
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
    fun createPlaceholderAPIResolver(player: Player, text: String): TagResolver {
        if (!isEnabled()) {
            return TagResolver.empty()
        }
        
        val resolvers = mutableListOf<TagResolver>()
        
        try {
            // Find all PlaceholderAPI placeholders in the text
            val matcher = placeholderPattern.matcher(text)
            val foundPlaceholders = mutableSetOf<String>()
            
            while (matcher.find()) {
                val placeholder = matcher.group(1)
                foundPlaceholders.add(placeholder)
            }
            
            // Process each unique placeholder
            for (placeholder in foundPlaceholders) {
                val result = processPlaceholderAPI(player, "%$placeholder%")
                if (result != "%$placeholder%") { // Only add if placeholder was actually resolved
                    resolvers.add(Placeholder.unparsed(placeholder, result))
                }
            }
            
        } catch (e: Exception) {
            logger.warn("Error processing PlaceholderAPI placeholders for player ${player.name}", e)
        }
        
        return TagResolver.resolver(resolvers)
    }
    
    /**
     * Process a single PlaceholderAPI placeholder
     */
    private fun processPlaceholderAPI(player: Player, placeholder: String): String {
        return try {
            // Use reflection to call PlaceholderAPI.setPlaceholders safely
            val placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val setPlaceholdersMethod = placeholderAPIClass.getMethod("setPlaceholders", Player::class.java, String::class.java)
            setPlaceholdersMethod.invoke(null, player, placeholder) as String
        } catch (e: Exception) {
            logger.debug("Failed to process PlaceholderAPI placeholder '$placeholder' for player ${player.name}: ${e.message}")
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
            val setPlaceholdersMethod = placeholderAPIClass.getMethod("setPlaceholders", Player::class.java, String::class.java)
            setPlaceholdersMethod.invoke(null, player, text) as String
        } catch (e: Exception) {
            logger.warn("Error processing PlaceholderAPI text for player ${player.name}", e)
            text
        }
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