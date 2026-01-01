package bruh.zchat.utils.translations

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * Caching:
 * - Components are cached per (locale, key, custom placeholders)
 * - Uses LRU + expire-after-write with a short TTL (default 20 seconds)
 * - Third-party placeholders (PlaceholderAPI, MiniPlaceholders) are *not* part of cache keys
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
 *     unparsed("player", playerName)
 * }
 *
 * // Switch language
 * translations.switchLanguage("fr")
 * ```
 */
class TranslationAPI(
    private val translationsDirectory: Path,
    private val cacheMaxSize: Long = 1000,
    private val cacheTtl: Duration = 20.seconds
) {
    private val loader = TranslationLoader(translationsDirectory)
    private val miniMessage = MiniMessage.miniMessage()
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

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
    // Cache key format: "comp:<locale>|<fullKey>[|placeholder-signature]" or "mm:<miniMessageString>"
    private val componentCache = InMemoryKache<String, Component>(maxSize = cacheMaxSize) {
        strategy = KacheStrategy.LRU
        // Short TTL to keep placeholder-backed data reasonably fresh
        expireAfterWriteDuration = cacheTtl
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
        val cacheKey = buildBaseCacheKey(fullKey, audience)

        // Fast path: cached component
        val cached = componentCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // Get and process text
        var text = getStringByFullKey(fullKey, key.default)
        val player = placeholderIntegration.getPlayerFromAudience(audience)

        // Replace PlaceholderAPI placeholders (not part of cache key by design)
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Build resolver with MiniPlaceholders (also not part of cache key)
        val resolver = placeholderIntegration.buildCombinedResolver(TagResolver.empty(), audience)

        val component = withContext(Dispatchers.Default) {
            miniMessage.deserialize(text, resolver)
        }

        componentCache.put(cacheKey, component)
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
        val fullKey = getFullKey(key)
        var text = getStringByFullKey(fullKey, key.default)
        val player = placeholderIntegration.getPlayerFromAudience(audience)

        // Replace PlaceholderAPI placeholders (not part of cache key by design)
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Base cache key for this message
        val baseKey = buildBaseCacheKey(fullKey, audience)
        val keyBuilder = StringBuilder(baseKey)

        // Build tag resolver with custom placeholders and string caching
        val componentBuilder = ComponentBuilder(
            miniMessage = miniMessage,
            stringCache = { stringValue ->
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
            },
            cacheKeyBuilder = keyBuilder,
            plainTextSerializer = plainTextSerializer
        )

        componentBuilder.builder()

        // Now that placeholder signatures have been appended, check cache
        val finalCacheKey = keyBuilder.toString()
        val cached = componentCache.get(finalCacheKey)
        if (cached != null) {
            return cached
        }

        val builtResolver = componentBuilder.build()

        // Combine with MiniPlaceholders resolver (not part of cache key)
        val tagResolver = placeholderIntegration.buildCombinedResolver(builtResolver, audience)

        val component = withContext(Dispatchers.Default) {
            miniMessage.deserialize(text, tagResolver)
        }

        componentCache.put(finalCacheKey, component)
        return component
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
        val cacheKey = buildBaseCacheKey(fullKey, audience)

        // Fast path: cached component
        val cached = cacheGetBlocking(cacheKey)
        if (cached != null) {
            return cached
        }

        var text = getStringByFullKey(fullKey, key.default)
        val player = placeholderIntegration.getPlayerFromAudience(audience)

        // Replace PlaceholderAPI placeholders (not part of cache key)
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Build resolver with MiniPlaceholders (also not part of cache key)
        val resolver = placeholderIntegration.buildCombinedResolver(TagResolver.empty(), audience)

        val component = miniMessage.deserialize(text, resolver)
        cachePutBlocking(cacheKey, component)
        return component
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
        val fullKey = getFullKey(key)
        var text = getStringByFullKey(fullKey, key.default)
        val player = placeholderIntegration.getPlayerFromAudience(audience)

        // Replace PlaceholderAPI placeholders (not part of cache key)
        text = placeholderIntegration.replacePlaceholderApi(text, player)

        // Base cache key for this message
        val baseKey = buildBaseCacheKey(fullKey, audience)
        val keyBuilder = StringBuilder(baseKey)

        val componentBuilder = SyncComponentBuilder(
            miniMessage = miniMessage,
            cacheKeyBuilder = keyBuilder,
            plainTextSerializer = plainTextSerializer
        )
        componentBuilder.builder()

        // Check cache after placeholder signatures have been appended
        val finalCacheKey = keyBuilder.toString()
        val cached = cacheGetBlocking(finalCacheKey)
        if (cached != null) {
            return cached
        }

        val builtResolver = componentBuilder.build()

        // Combine with MiniPlaceholders resolver (not part of cache key)
        val tagResolver = placeholderIntegration.buildCombinedResolver(builtResolver, audience)

        val component = miniMessage.deserialize(text, tagResolver)
        cachePutBlocking(finalCacheKey, component)
        return component
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
     * Builds the base cache key for a translation component.
     * If the audience is a per-recipient target (player / CommandSender), a stable
     * audience identifier is appended to avoid cross-recipient cache pollution.
     * Otherwise, the key is global for the given locale + full key.
     */
    private fun buildBaseCacheKey(fullKey: String, audience: Audience?): String {
        val audienceId = buildAudienceId(audience)
        return if (audienceId != null) {
            "comp:$currentLocale|$fullKey|aud=$audienceId"
        } else {
            "comp:$currentLocale|$fullKey"
        }
    }

    /**
     * Builds a stable audience identifier when possible.
     *
     * - If the audience is a player (OfflinePlayer), we use its UUID.
     * - If the audience is a Bukkit CommandSender, we use its name.
     * - Otherwise, we return null and treat the audience as a multi-recipient target.
     */
    private fun buildAudienceId(audience: Audience?): String? {
        if (audience == null) return null

        // Prefer player UUID when available (covers Player as well)
        val offlinePlayer = placeholderIntegration.getPlayerFromAudience(audience)
        if (offlinePlayer != null) {
            return "player:${offlinePlayer.uniqueId}"
        }

        // Fallback: detect CommandSender via reflection to avoid hard dependency
        return try {
            val senderClass = Class.forName("org.bukkit.command.CommandSender")
            if (!senderClass.isInstance(audience)) {
                null
            } else {
                val nameMethod = senderClass.getMethod("getName")
                val name = nameMethod.invoke(audience) as? String ?: return null
                "sender:$name"
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Blocking wrappers for using the suspend Kache API from synchronous contexts.
     */
    private fun cacheGetBlocking(key: String): Component? = runBlocking {
        componentCache.get(key)
    }

    private fun cachePutBlocking(key: String, value: Component) {
        runBlocking {
            componentCache.put(key, value)
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
