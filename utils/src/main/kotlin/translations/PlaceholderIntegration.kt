package bruh.zchat.utils.translations

import bruh.zchat.utils.translations.placeholder.*
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

/**
 * Handles integration with PlaceholderAPI and MiniPlaceholders.
 * Detects plugin availability and provides placeholder replacement functionality.
 *
 * This class uses inheritance-based providers that are only instantiated when
 * the respective plugins are available. This avoids reflection for method calls
 * while still being defensive about missing dependencies.
 */
class PlaceholderIntegration {

    private var placeholderApiProvider: PlaceholderApiProvider = NoOpPlaceholderApiProvider()
    private var miniPlaceholdersProvider: MiniPlaceholdersProvider = NoOpMiniPlaceholdersProvider()

    /**
     * Detects available placeholder plugins and initializes providers.
     * Should be called during TranslationAPI.load().
     */
    fun detectPlugins() {
        // Check if Bukkit is available (might be running in tests)
        val bukkitAvailable = try {
            Class.forName("org.bukkit.Bukkit")
            Bukkit.getPluginManager()
            true
        } catch (e: Exception) {
            false
        }

        if (!bukkitAvailable) {
            placeholderApiProvider = NoOpPlaceholderApiProvider()
            miniPlaceholdersProvider = NoOpMiniPlaceholdersProvider()
            return
        }

        // Try to create PlaceholderAPI provider
        placeholderApiProvider = try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                // This will fail with NoClassDefFoundError if PlaceholderAPI isn't loaded
                RealPlaceholderApiProvider()
            } else {
                NoOpPlaceholderApiProvider()
            }
        } catch (e: Exception) {
            NoOpPlaceholderApiProvider()
        }

        // Try to create MiniPlaceholders provider
        miniPlaceholdersProvider = try {
            if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
                // This will fail with NoClassDefFoundError if MiniPlaceholders isn't loaded
                RealMiniPlaceholdersProvider()
            } else {
                NoOpMiniPlaceholdersProvider()
            }
        } catch (e: Exception) {
            NoOpMiniPlaceholdersProvider()
        }
    }

    /**
     * Returns whether PlaceholderAPI is available.
     */
    fun isPlaceholderApiAvailable(): Boolean = placeholderApiProvider.isAvailable

    /**
     * Returns whether MiniPlaceholders is available.
     */
    fun isMiniPlaceholdersAvailable(): Boolean = miniPlaceholdersProvider.isAvailable

    /**
     * Replaces PlaceholderAPI placeholders in the given string.
     * Returns the original string if PlaceholderAPI is not available or player is null.
     *
     * @param text The text to process
     * @param player The player context for placeholders (can be null)
     * @return The processed text with PlaceholderAPI placeholders replaced
     */
    fun replacePlaceholderApi(text: String, player: OfflinePlayer?): String {
        if (player == null) return text
        return placeholderApiProvider.setPlaceholders(text, player)
    }

    /**
     * Builds a combined TagResolver that includes MiniPlaceholders resolvers.
     *
     * @param existingResolver An existing TagResolver to combine with
     * @param audience The audience for audience-specific placeholders (can be null)
     * @return Combined TagResolver
     */
    fun buildCombinedResolver(existingResolver: TagResolver, audience: Audience?): TagResolver {
        if (!miniPlaceholdersProvider.isAvailable) {
            return existingResolver
        }

        val resolvers = mutableListOf(existingResolver)

        // Add global placeholders
        resolvers.add(miniPlaceholdersProvider.globalPlaceholders())

        // Add audience placeholders if we have an audience
        if (audience != null) {
            resolvers.add(miniPlaceholdersProvider.audiencePlaceholders())
        }

        return TagResolver.resolver(resolvers)
    }

    /**
     * Extracts an OfflinePlayer from an Audience if possible.
     *
     * @param audience The audience to extract from
     * @return The OfflinePlayer, or null if not applicable
     */
    fun getPlayerFromAudience(audience: Audience?): OfflinePlayer? {
        return audience as? OfflinePlayer
    }
}
