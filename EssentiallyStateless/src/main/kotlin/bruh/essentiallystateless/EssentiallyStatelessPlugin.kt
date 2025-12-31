package bruh.essentiallystateless

import bruh.essentiallystateless.commands.*
import bruh.essentiallystateless.config.EssentiallyStatelessConfig
import bruh.essentiallystateless.config.EssentiallyStatelessConfigLoader
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import bruh.zchat.utils.translations.translationApi
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import org.bukkit.GameMode
import org.bukkit.entity.EntityType
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.BukkitLamp

/**
 * EssentiallyStateless - A stateless essential commands plugin.
 * 
 * This plugin provides essential server commands without storing any runtime-modified state.
 * All state is stored within Minecraft itself (player data, world data, etc.).
 */
class EssentiallyStatelessPlugin : SuspendingJavaPlugin() {
    lateinit var config: EssentiallyStatelessConfig
        private set
    lateinit var configLoader: EssentiallyStatelessConfigLoader
        private set
    lateinit var menuAPI: MenuAPI
        private set
    lateinit var translations: TranslationAPI
        private set

    override suspend fun onLoadAsync() {
        configLoader = EssentiallyStatelessConfigLoader(dataFolder.toPath(), slF4JLogger)
        config = configLoader.load()
    }

    override suspend fun onEnableAsync() {
        slF4JLogger.info("Loading EssentiallyStateless...")

        // Initialize translation system
        translations = translationApi()
        translations.register("commands", CommandMessages::class)
        translations.switchLanguage(config.language)
        translations.load()
        slF4JLogger.info("Translation system initialized")

        menuAPI = MenuAPI(this)

        setupCommands()

        slF4JLogger.info("EssentiallyStateless enabled!")
    }

    override suspend fun onDisableAsync() {
        slF4JLogger.info("Disabling EssentiallyStateless...")
        slF4JLogger.info("EssentiallyStateless disabled!")
    }

    private fun setupCommands() {
        val lamp = BukkitLamp.builder(this)
            .suggestionProviders { providers ->
                // GameMode suggestions
                providers.addProviderForAnnotation(SuggestGameMode::class.java) { _ ->
                    SuggestionProvider { _ ->
                        GameMode.entries.map { it.name.lowercase() }
                    }
                }
                
                // World suggestions
                providers.addProviderForAnnotation(SuggestWorld::class.java) { _ ->
                    SuggestionProvider { _ ->
                        server.worlds.map { it.name }
                    }
                }
                
                // Online player suggestions
                providers.addProviderForAnnotation(SuggestOnlinePlayer::class.java) { _ ->
                    SuggestionProvider { _ ->
                        server.onlinePlayers.map { it.name }
                    }
                }
                
                // Entity type suggestions
                providers.addProviderForAnnotation(SuggestEntityType::class.java) { _ ->
                    SuggestionProvider { _ ->
                        EntityType.entries
                            .filter { it.isSpawnable && it.isAlive }
                            .map { it.name.lowercase() }
                    }
                }
            }
            .build()

        // Register all command classes
        lamp.register(GameModeCommands(this, translations))
        lamp.register(TimeWeatherCommands(this, translations))
        lamp.register(PlayerCommands(this, translations))
        lamp.register(TeleportCommands(this, translations))
        lamp.register(InventoryCommands(this, translations, menuAPI))
        lamp.register(ItemCommands(this, translations))
        lamp.register(WorldCommands(this, translations))
        lamp.register(AdminCommands(this, translations))
        lamp.register(InfoCommands(this, translations))
        lamp.register(FunCommands(this, translations))
        lamp.register(EssentiallyStatelessMainCommand(this, translations, configLoader))
    }
}
