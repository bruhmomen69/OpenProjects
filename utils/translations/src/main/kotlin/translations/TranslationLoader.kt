package bruh.zchat.utils.translations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles loading and saving of translation files.
 * Manages translation files in the <dataFolder>/translations/ directory.
 */
class TranslationLoader(
    private val translationsDirectory: Path
) {
    private val fileMutex = Mutex()
    private val loadedTranslations = ConcurrentHashMap<String, Properties>()

    /**
     * Initializes the translations directory if it doesn't exist.
     */
    suspend fun initializeDirectory(): Unit = withContext(Dispatchers.IO) {
        if (!Files.exists(translationsDirectory)) {
            Files.createDirectories(translationsDirectory)
        }
    }

    /**
     * Generates all ISO-639 language files.
     * English file will contain defaults, others will be empty.
     *
     * @param validKeys Set of all valid translation keys with their defaults
     */
    suspend fun generateAllLanguageFiles(
        validKeys: Map<String, String>
    ): Unit = withContext(Dispatchers.IO) {
        initializeDirectory()

        // Generate files for all ISO 639-1 languages
        ISO639Languages.ALL_LANGUAGES.keys.forEach { languageCode ->
            val filePath = translationsDirectory.resolve("$languageCode.properties")
            
            if (languageCode == "en") {
                // English file gets all defaults
                updateTranslationFile(filePath, validKeys, populateDefaults = true)
            } else {
                // Other languages get empty files (if file doesn't exist)
                if (!Files.exists(filePath)) {
                    createEmptyPropertiesFile(filePath, languageCode)
                } else {
                    // Clean up obsolete keys from existing files
                    updateTranslationFile(filePath, validKeys, populateDefaults = false)
                }
            }
        }
    }

    /**
     * Updates a translation file: removes obsolete keys, adds missing keys (optionally with defaults).
     */
    private suspend fun updateTranslationFile(
        filePath: Path,
        validKeys: Map<String, String>,
        populateDefaults: Boolean
    ) = fileMutex.withLock {
        val properties = Properties()
        
        // Load existing translations if file exists
        if (Files.exists(filePath)) {
            Files.newInputStream(filePath).use { inputStream ->
                properties.load(inputStream)
            }
        }

        // Remove obsolete keys
        val keysToRemove = properties.keys.filter { it !in validKeys.keys }
        keysToRemove.forEach { properties.remove(it) }

        // Add missing keys (with defaults if specified, empty otherwise)
        validKeys.forEach { (key, default) ->
            if (!properties.containsKey(key)) {
                properties.setProperty(key, if (populateDefaults) default else "")
            }
        }

        // Save updated file
        Files.newOutputStream(filePath).use { outputStream ->
            val languageCode = filePath.fileName.toString().removeSuffix(".properties")
            val languageName = ISO639Languages.ALL_LANGUAGES[languageCode] ?: languageCode
            properties.store(outputStream, "Translation file for $languageName ($languageCode)")
        }
    }

    /**
     * Creates an empty properties file for a language.
     */
    private suspend fun createEmptyPropertiesFile(
        filePath: Path,
        languageCode: String
    ) = fileMutex.withLock {
        val properties = Properties()
        Files.newOutputStream(filePath).use { outputStream ->
            val languageName = ISO639Languages.ALL_LANGUAGES[languageCode] ?: languageCode
            properties.store(outputStream, "Translation file for $languageName ($languageCode)")
        }
    }

    /**
     * Loads translations for a specific locale.
     * Falls back to base language if region-specific file doesn't exist.
     *
     * @param locale The locale to load (e.g., "en_US", "fr", "de_DE")
     * @return Properties containing the translations, or empty Properties if file doesn't exist
     */
    suspend fun loadTranslations(locale: String): Properties = withContext(Dispatchers.IO) {
        // Try locale-specific file first (e.g., en_US.properties)
        val localeFilePath = translationsDirectory.resolve("$locale.properties")
        if (Files.exists(localeFilePath)) {
            return@withContext loadPropertiesFile(localeFilePath).also {
                loadedTranslations[locale] = it
            }
        }

        // Fall back to base language (e.g., en.properties)
        val baseLanguage = ISO639Languages.getBaseLanguage(locale)
        val baseFilePath = translationsDirectory.resolve("$baseLanguage.properties")
        if (Files.exists(baseFilePath)) {
            return@withContext loadPropertiesFile(baseFilePath).also {
                loadedTranslations[locale] = it
            }
        }

        // Return empty properties if no file found
        Properties().also { loadedTranslations[locale] = it }
    }

    /**
     * Loads a properties file from disk.
     */
    private fun loadPropertiesFile(filePath: Path): Properties {
        val properties = Properties()
        try {
            Files.newInputStream(filePath).use { inputStream ->
                properties.load(inputStream)
            }
        } catch (e: IOException) {
            // Return empty properties on error
        }
        return properties
    }

    /**
     * Gets cached translations for a locale.
     *
     * @param locale The locale to get translations for
     * @return Cached Properties or null if not loaded
     */
    fun getCachedTranslations(locale: String): Properties? {
        return loadedTranslations[locale]
    }

    /**
     * Clears all cached translations.
     */
    fun clearCache() {
        loadedTranslations.clear()
    }

    /**
     * Gets all available locales that have translation files.
     */
    suspend fun getAvailableLocales(): List<String> = withContext(Dispatchers.IO) {
        if (!Files.exists(translationsDirectory)) {
            return@withContext emptyList()
        }

        Files.list(translationsDirectory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".properties") }
                .map { it.fileName.toString().removeSuffix(".properties") }
                .toList()
        }
    }
}
