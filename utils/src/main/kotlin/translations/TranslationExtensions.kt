package bruh.zchat.utils.translations

import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path

/**
 * Creates a TranslationAPI instance for this plugin.
 * Translation files will be stored in <plugin.dataFolder>/translations/
 *
 * @param cacheMaxSize Maximum number of cached MiniMessage components (default 1000)
 * @param cacheTtlMinutes Time-to-live for cached components in minutes (default 30)
 * @return A new TranslationAPI instance
 */
fun JavaPlugin.translationApi(
    cacheMaxSize: Long = 1000,
    cacheTtlMinutes: Int = 30
): TranslationAPI {
    val translationsPath = dataFolder.toPath().resolve("translations")
    return TranslationAPI(translationsPath, cacheMaxSize, cacheTtlMinutes)
}

/**
 * Creates a TranslationAPI instance with a custom subdirectory.
 *
 * @param subdirectory The subdirectory name within the plugin's data folder
 * @param cacheMaxSize Maximum number of cached MiniMessage components (default 1000)
 * @param cacheTtlMinutes Time-to-live for cached components in minutes (default 30)
 * @return A new TranslationAPI instance
 */
fun JavaPlugin.translationApi(
    subdirectory: String,
    cacheMaxSize: Long = 1000,
    cacheTtlMinutes: Int = 30
): TranslationAPI {
    val translationsPath = dataFolder.toPath().resolve(subdirectory)
    return TranslationAPI(translationsPath, cacheMaxSize, cacheTtlMinutes)
}
