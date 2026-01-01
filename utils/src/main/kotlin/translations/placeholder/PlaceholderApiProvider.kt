package bruh.zchat.utils.translations.placeholder

import org.bukkit.OfflinePlayer

/**
 * Interface for PlaceholderAPI integration.
 * Implementations handle placeholder replacement in strings.
 */
interface PlaceholderApiProvider {
    
    /**
     * Whether PlaceholderAPI is available and this provider is functional.
     */
    val isAvailable: Boolean
    
    /**
     * Replaces PlaceholderAPI placeholders in the given string.
     *
     * @param text The text containing placeholders
     * @param player The player context for placeholder replacement
     * @return The text with placeholders replaced
     */
    fun setPlaceholders(text: String, player: OfflinePlayer): String
}

/**
 * Real implementation that delegates to PlaceholderAPI.
 * Only instantiated when PlaceholderAPI is confirmed to be loaded.
 */
internal class RealPlaceholderApiProvider : PlaceholderApiProvider {
    
    override val isAvailable: Boolean = true
    
    override fun setPlaceholders(text: String, player: OfflinePlayer): String {
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text)
    }
}

/**
 * No-op implementation used when PlaceholderAPI is not available.
 */
internal class NoOpPlaceholderApiProvider : PlaceholderApiProvider {
    
    override val isAvailable: Boolean = false
    
    override fun setPlaceholders(text: String, player: OfflinePlayer): String = text
}
