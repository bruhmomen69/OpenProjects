package bruh.zchat.utils.translations.placeholder

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * Interface for MiniPlaceholders integration.
 * Implementations provide TagResolvers for MiniMessage parsing.
 */
interface MiniPlaceholdersProvider {
    
    /**
     * Whether MiniPlaceholders is available and this provider is functional.
     */
    val isAvailable: Boolean
    
    /**
     * Gets the global placeholders TagResolver.
     *
     * @return TagResolver for global placeholders
     */
    fun globalPlaceholders(): TagResolver
    
    /**
     * Gets the audience placeholders TagResolver.
     *
     * @return TagResolver for audience-specific placeholders
     */
    fun audiencePlaceholders(): TagResolver
}

/**
 * Real implementation that delegates to MiniPlaceholders API.
 * Only instantiated when MiniPlaceholders is confirmed to be loaded.
 */
internal class RealMiniPlaceholdersProvider : MiniPlaceholdersProvider {
    
    override val isAvailable: Boolean = true
    
    override fun globalPlaceholders(): TagResolver {
        return io.github.miniplaceholders.api.MiniPlaceholders.globalPlaceholders()
    }
    
    override fun audiencePlaceholders(): TagResolver {
        return io.github.miniplaceholders.api.MiniPlaceholders.audiencePlaceholders()
    }
}

/**
 * No-op implementation used when MiniPlaceholders is not available.
 */
internal class NoOpMiniPlaceholdersProvider : MiniPlaceholdersProvider {
    
    override val isAvailable: Boolean = false
    
    override fun globalPlaceholders(): TagResolver = TagResolver.empty()
    
    override fun audiencePlaceholders(): TagResolver = TagResolver.empty()
}
