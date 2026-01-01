package bruh.zchat.utils.translations

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes

/**
 * Data class representing a registered enum category.
 */
internal data class RegisteredEnum(
    val prefix: String,
    val enumClass: KClass<out Enum<*>>,
    val enumConstants: Array<out Enum<*>>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegisteredEnum) return false
        return prefix == other.prefix && enumClass == other.enumClass
    }

    override fun hashCode(): Int {
        return 31 * prefix.hashCode() + enumClass.hashCode()
    }
}

/**
 * Main translation API for managing localized messages.
 *
 * Features:
 * - Register multiple MessageKey enums with unique prefixes
 * - Load translations from properties files
 * - MiniMessage component support with LRU caching
 * - Automatic fallback to default values
 * - ISO-639 language file generation
 *
 * Usage:
 * ```kotlin
 * val translations = TranslationAPI(dataFolder.toPath().resolve("translations"))
 * translations.register("gui", GuiMessages::class)
 * translations.register("commands", CommandMessages::class)
 * translations.load()
 *
 * // Get string
 * val message = translations.getString(GuiMessages.WELCOME)
 *
 * // Get component with placeholders
 * val component = translations.getComponent(GuiMessages.GREETING) {
 *     placeholder("player", playerName)
 * }
 *
 * // Switch language
 * translations.switchLanguage("fr")
 * ```
 */
class TranslationAPI(
    private val translationsDirectory: Path,
    private val cacheMaxSize: Long = 1000,
    private val cacheTtlMinutes: Int = 30
) {
    private val loader = TranslationLoader(translationsDirectory)
    private val miniMessage = MiniMessage.miniMessage()

    // Registered enums: enumClass -> RegisteredEnum
    private val registeredEnums = ConcurrentHashMap<KClass<*>, RegisteredEnum>()

    // Reverse lookup: prefix -> RegisteredEnum
    private val prefixToEnum = ConcurrentHashMap<String, RegisteredEnum>()

    // Current locale
    @Volatile
    private var currentLocale: String = "en"

    // Current translations (locale -> translations)
    private val translations = ConcurrentHashMap<String, Properties>()

    // English defaults (always loaded for fallback)
    private var englishDefaults: Properties = Properties()

    // LRU cache for parsed MiniMessage components
    // Cache key format: "<locale>:<fullKey>" or "mm:<miniMessageString>"
    private val componentCache = InMemoryKache<String, Component>(maxSize = cacheMaxSize) {
        strategy = KacheStrategy.LRU
        expireAfterAccessDuration = cacheTtlMinutes.minutes
    }

    // Mutex for load operations
    private val loadMutex = Mutex()

    // Fast lookup: MessageKey instance -> full key (cached for performance)
    private val keyCache = ConcurrentHashMap<MessageKey, String>()

    // Initialized flag
    @Volatile
    private var initialized = false

    // Placeholder integration for PlaceholderAPI and MiniPlaceholders
    private val placeholderIntegration = PlaceholderIntegration()

    /**
     * Registers a MessageKey enum with a unique prefix.
     *
     * @param prefix The prefix for all keys in this enum (e.g., "gui", "commands")
     * @param enumClass The enum class implementing MessageKey
     * @throws IllegalArgumentException if prefix is already registered or enum doesn't implement MessageKey
     */
    inline fun <reified T> register(prefix: String, enumClass: KClass<T>)
            where T : Enum<T>, T : MessageKey {
        registerInternal(prefix, enumClass, enumValues<T>())
    }

    /**
     * Internal registration method.
     */
    @PublishedApi
    internal fun <T> registerInternal(
        prefix: String,
        enumClass: KClass<T>,
        constants: Array<T>
    ) where T : Enum<T>, T : MessageKey {
        require(prefix.isNotBlank()) { "Prefix cannot be blank" }
        require(!prefixToEnum.containsKey(prefix)) { "Prefix '$prefix' is already registered" }
        require(!registeredEnums.containsKey(enumClass)) { "Enum ${enumClass.simpleName} is already registered" }

        val registered = RegisteredEnum(prefix, enumClass, constants)
        registeredEnums[enumClass] = registered
        prefixToEnum[prefix] = registered

        // Pre-cache key mappings for fast lookup
        constants.forEach { constant ->
            keyCache[constant] = "${prefix}.${constant.key}"
        }
    }

    /**
     * Loads all translations and generates missing language files.
     * Must be called after all enums are registered.
     */
    suspend fun load(): Unit = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            // Build complete key -> default map from all registered enums
            val allKeys = buildAllKeysMap()

            // Generate/update all language files
            loader.generateAllLanguageFiles(allKeys)

            // Load English defaults (always needed for fallback)
            englishDefaults = loader.loadTranslations("en")

            // Load current locale
            if (currentLocale != "en") {
                translations[currentLocale] = loader.loadTranslations(currentLocale)
            }

            // Clear component cache on reload
            componentCache.clear()

            // Detect available placeholder plugins
            placeholderIntegration.detectPlugins()

            initialized = true
        }
    }

    /**
     * Builds a map of all translation keys to their default values.
     */
    private fun buildAllKeysMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        registeredEnums.values.forEach { registered ->
            registered.enumConstants.forEach { constant ->
                val messageKey = constant as MessageKey
                val fullKey = "${registered.prefix}.${messageKey.key}"
                result[fullKey] = messageKey.default
            }
        }
        return result
    }

    /**
     * Switches the current language.
     *
     * @param locale The locale to switch to (e.g., "en", "fr", "de_DE")
     */
    suspend fun switchLanguage(locale: String): Unit = loadMutex.withLock {
        require(ISO639Languages.isValidLocale(locale)) { "Invalid locale: $locale" }

        if (currentLocale == locale) return@withLock

        withContext(Dispatchers.IO) {
            currentLocale = locale

            // Load translations for new locale if not English
            if (locale != "en" && !translations.containsKey(locale)) {
                translations[locale] = loader.loadTranslations(locale)
            }

            // Clear component cache on language change
            componentCache.clear()
        }
    }

    /**
     * Gets the current locale.
     */
    fun getCurrentLocale(): String = currentLocale

    /**
     * Gets a translated string for a MessageKey.
     * Falls back to English default if translation is missing.
     *
     * @param key The MessageKey to look up
     * @return The translated string or default value
     */
    fun getString(key: MessageKey): String {
        val fullKey = getFullKey(key)
        return getStringByFullKey(fullKey, key.default)
    }

    /**
     * Gets a translated string by full key.
     */
    private fun getStringByFullKey(fullKey: String, default: String): String {
        // Try current locale first
        val currentTranslations = if (currentLocale == "en") {
            englishDefaults
        } else {
            translations[currentLocale]
        }

        val translation = currentTranslations?.getProperty(fullKey)
        if (!translation.isNullOrBlank()) {
            return translation
        }

        // Fall back to English
        val englishTranslation = englishDefaults.getProperty(fullKey)
        if (!englishTranslation.isNullOrBlank()) {
            return englishTranslation
        }

        // Fall back to default
        return default
    }

    /**
     * Gets a translated Component for a MessageKey.
     * Uses LRU caching for parsed MiniMessage components.
     *
     * @param key The MessageKey to look up
     * @param audience Optional audience for PlaceholderAPI/MiniPlaceholders replacement
     * @return The translated Component
     */
    suspend fun getComponent(key: MessageKey, audience: Audience? = null): Component {
        val fullKey = getFullKey(key)
        val player = placeholderIntegration.getPlayerFromAudience(audience)
        
        // If we have placeholder plugins and an audience, don't cache (placeholders are dynamic)
        val useCache = player == null && !placeholderIntegration.isMiniPlaceholdersAvailable()
        
        if (useCache) {
            val cacheKey = "$currentLocale:$fullKey"
            val cached = componentCache.get(cacheKey)
            if (cached != null) {
                return cached
            }
        }

        // Get and process text
        var text = getStringByFullKey(fullKey, key.default)
        
        // Replace PlaceholderAPI placeholders
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Build resolver with MiniPlaceholders
        val resolver = placeholderIntegration.buildCombinedResolver(TagResolver.empty(), audience)
        
        val component = withContext(Dispatchers.Default) {
            miniMessage.deserialize(text, resolver)
        }

        if (useCache) {
            val cacheKey = "$currentLocale:$fullKey"
            componentCache.put(cacheKey, component)
        }
        return component
    }

    /**
     * Gets a translated Component with placeholders.
     *
     * @param key The MessageKey to look up
     * @param audience Optional audience for PlaceholderAPI/MiniPlaceholders replacement
     * @param builder DSL builder for configuring placeholders
     * @return The translated Component with placeholders applied
     */
    suspend fun getComponent(
        key: MessageKey,
        audience: Audience? = null,
        builder: suspend ComponentBuilder.() -> Unit
    ): Component {
        var text = getString(key)
        val player = placeholderIntegration.getPlayerFromAudience(audience)
        
        // Replace PlaceholderAPI placeholders
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Build tag resolver with placeholders
        val componentBuilder = ComponentBuilder(miniMessage) { stringValue ->
            // Cache string -> Component conversions
            val stringCacheKey = "mm:$stringValue"
            val cached = componentCache.get(stringCacheKey)
            if (cached != null) {
                cached
            } else {
                val parsed = withContext(Dispatchers.Default) {
                    miniMessage.deserialize(stringValue)
                }
                componentCache.put(stringCacheKey, parsed)
                parsed
            }
        }
        componentBuilder.builder()
        val builtResolver = componentBuilder.build()
        
        // Combine with MiniPlaceholders resolver
        val tagResolver = placeholderIntegration.buildCombinedResolver(builtResolver, audience)

        // Parse with placeholders (not cached as placeholders vary)
        return withContext(Dispatchers.Default) {
            miniMessage.deserialize(text, tagResolver)
        }
    }

    /**
     * Synchronous version of getComponent (without caching for string placeholders).
     * Use when not in a coroutine context.
     *
     * @param key The MessageKey to look up
     * @param audience Optional audience for PlaceholderAPI/MiniPlaceholders replacement
     * @return The translated Component
     */
    fun getComponentSync(key: MessageKey, audience: Audience? = null): Component {
        val fullKey = getFullKey(key)
        var text = getStringByFullKey(fullKey, key.default)
        val player = placeholderIntegration.getPlayerFromAudience(audience)
        
        // Replace PlaceholderAPI placeholders
        text = placeholderIntegration.replacePlaceholderApi(text, player)
        
        // Build resolver with MiniPlaceholders
        val resolver = placeholderIntegration.buildCombinedResolver(TagResolver.empty(), audience)
        
        return miniMessage.deserialize(text, resolver)
    }

    /**
     * Synchronous version of getComponent with placeholders.
     *
     * @param key The MessageKey to look up
     * @param audience Optional audience for PlaceholderAPI/MiniPlaceholders replacement
     * @param builder DSL builder for configuring placeholders
     * @return The translated Component with placeholders applied
     */
    fun getComponentSync(
        key: MessageKey,
        audience: Audience? = null,
        builder: SyncComponentBuilder.() -> Unit
    ): Component {
        var text = getString(key)
        val player = placeholderIntegration.getPlayerFromAudience(audience)
        
        // Replace PlaceholderAPI placeholders
        text = placeholderIntegration.replacePlaceholderApi(text, player)
        
        val componentBuilder = SyncComponentBuilder(miniMessage)
        componentBuilder.builder()
        val builtResolver = componentBuilder.build()
        
        // Combine with MiniPlaceholders resolver
        val tagResolver = placeholderIntegration.buildCombinedResolver(builtResolver, audience)
        
        return miniMessage.deserialize(text, tagResolver)
    }

    /**
     * Gets the full translation key for a MessageKey.
     * Uses cached lookup for performance.
     */
    private fun getFullKey(key: MessageKey): String {
        return keyCache.getOrPut(key) {
            // Find the registered enum for this key
            val enumClass = key::class
            val registered = registeredEnums[enumClass]
                ?: throw IllegalArgumentException("Enum ${enumClass.simpleName} is not registered")
            "${registered.prefix}.${key.key}"
        }
    }

    /**
     * Gets all registered prefixes.
     */
    fun getRegisteredPrefixes(): Set<String> = prefixToEnum.keys.toSet()

    /**
     * Gets all available locales.
     */
    suspend fun getAvailableLocales(): List<String> = loader.getAvailableLocales()

    /**
     * Checks if the API is initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Clears all caches.
     */
    suspend fun clearCache() {
        componentCache.clear()
        loader.clearCache()
    }

    /**
     * Reloads translations from disk.
     */
    suspend fun reload() {
        clearCache()
        load()
    }
}
