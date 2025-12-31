package bruh.zchat.utils.translations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * DSL builder for constructing MiniMessage components with placeholders.
 * Supports both Component and String placeholders with proper caching integration.
 */
class ComponentBuilder internal constructor(
    private val miniMessage: MiniMessage,
    private val stringCache: suspend (String) -> Component
) {
    private val resolvers = mutableListOf<TagResolver>()
    private val pendingStringPlaceholders = mutableListOf<Pair<String, String>>()

    /**
     * Adds a component placeholder.
     *
     * @param key The placeholder key (used as <key> in the message)
     * @param component The Component to insert
     */
    fun placeholder(key: String, component: Component) {
        resolvers.add(Placeholder.component(key, component))
    }

    /**
     * Adds a string placeholder that will be parsed as MiniMessage and cached.
     *
     * @param key The placeholder key (used as <key> in the message)
     * @param value The string value to parse as MiniMessage (will be cached)
     */
    fun placeholder(key: String, value: String) {
        pendingStringPlaceholders.add(key to value)
    }

    /**
     * Adds an unparsed string placeholder (no MiniMessage parsing).
     *
     * @param key The placeholder key (used as <key> in the message)
     * @param value The raw string value (will not be parsed)
     */
    fun unparsed(key: String, value: String) {
        resolvers.add(Placeholder.unparsed(key, value))
    }

    /**
     * Builds the final TagResolver combining all placeholders.
     * Must be called from a suspend context to handle string placeholder caching.
     */
    internal suspend fun build(): TagResolver {
        // Process pending string placeholders through cache
        pendingStringPlaceholders.forEach { (key, value) ->
            val component = stringCache(value)
            resolvers.add(Placeholder.component(key, component))
        }

        return if (resolvers.isEmpty()) {
            TagResolver.empty()
        } else {
            TagResolver.resolver(resolvers)
        }
    }
}

/**
 * Synchronous version of ComponentBuilder for non-suspend contexts.
 * String placeholders are parsed directly without caching.
 */
class SyncComponentBuilder internal constructor(
    private val miniMessage: MiniMessage
) {
    private val resolvers = mutableListOf<TagResolver>()

    /**
     * Adds a component placeholder.
     */
    fun placeholder(key: String, component: Component) {
        resolvers.add(Placeholder.component(key, component))
    }

    /**
     * Adds a string placeholder that will be parsed as MiniMessage.
     */
    fun placeholder(key: String, value: String) {
        val component = miniMessage.deserialize(value)
        resolvers.add(Placeholder.component(key, component))
    }

    /**
     * Adds an unparsed string placeholder.
     */
    fun unparsed(key: String, value: String) {
        resolvers.add(Placeholder.unparsed(key, value))
    }

    /**
     * Builds the final TagResolver.
     */
    internal fun build(): TagResolver {
        return if (resolvers.isEmpty()) {
            TagResolver.empty()
        } else {
            TagResolver.resolver(resolvers)
        }
    }
}
