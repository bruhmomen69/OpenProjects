package bruh.zchat.utils.translations

import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a TranslationAPI instance for this plugin.
 * Translation files will be stored in <plugin.dataFolder>/translations/
 *
 * @param cacheMaxSize Maximum number of cached MiniMessage components (default 1000)
 * @param cacheTtl Time-to-live for cached components (default 20 seconds)
 * @return A new TranslationAPI instance
 */
fun JavaPlugin.translationApi(
    cacheMaxSize: Long = 1000,
    cacheTtl: Duration = 20.seconds
): TranslationAPI {
    val translationsPath = dataFolder.toPath().resolve("translations")
    return TranslationAPI(translationsPath, cacheMaxSize, cacheTtl)
}

/**
 * Creates a TranslationAPI instance with a custom subdirectory.
 *
 * @param subdirectory The subdirectory name within the plugin's data folder
 * @param cacheMaxSize Maximum number of cached MiniMessage components (default 1000)
 * @param cacheTtl Time-to-live for cached components (default 20 seconds)
 * @return A new TranslationAPI instance
 */
fun JavaPlugin.translationApi(
    subdirectory: String,
    cacheMaxSize: Long = 1000,
    cacheTtl: Duration = 20.seconds
): TranslationAPI {
    val translationsPath = dataFolder.toPath().resolve(subdirectory)
    return TranslationAPI(translationsPath, cacheMaxSize, cacheTtl)
}
