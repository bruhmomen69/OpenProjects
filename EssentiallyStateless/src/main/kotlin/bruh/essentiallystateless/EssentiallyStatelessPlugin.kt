package bruh.essentiallystateless

import bruh.essentiallystateless.commands.*
import bruh.essentiallystateless.config.EssentiallyStatelessConfig
import bruh.essentiallystateless.config.EssentiallyStatelessConfigLoader
import bruh.essentiallystateless.config.toCommonServerCommandsConfig
import bruh.essentiallystateless.translations.CommandMessages
import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.CommonServerCommandsFactory
import bruh.commands.commonservercommands.config.CommonServerCommandsConfig
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
 * 
 * This plugin implements CommandPlugin to allow reuse of common commands from the
 * common-server-commands utility module.
 */
class EssentiallyStatelessPlugin : SuspendingJavaPlugin(), CommandPlugin {
    // Plugin-specific config
    private var essentiallyStatelessConfig: EssentiallyStatelessConfig = EssentiallyStatelessConfig()
    
    lateinit var configLoader: EssentiallyStatelessConfigLoader
        private set
    
    // CommandPlugin interface implementation - delegates to essentiallyStatelessConfig
    override val config: CommonServerCommandsConfig
        get() = essentiallyStatelessConfig.toCommonServerCommandsConfig()
    
    override lateinit var menuAPI: MenuAPI
        private set
    
    override lateinit var translations: TranslationAPI
        private set

    override fun getUnderlyingPlugin(): SuspendingJavaPlugin = this

    override suspend fun onLoadAsync() {
        configLoader = EssentiallyStatelessConfigLoader(dataFolder.toPath(), slF4JLogger)
        essentiallyStatelessConfig = configLoader.load()
    }

    override suspend fun onEnableAsync() {
        slF4JLogger.info("Loading EssentiallyStateless...")

        // Initialize translation system
        translations = translationApi()
        translations.register("commands", CommandMessages::class)
        translations.switchLanguage(essentiallyStatelessConfig.language)
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
                providers.addProviderForAnnotation(bruh.commands.commonservercommands.commands.SuggestGameMode::class.java) { _ ->
                    SuggestionProvider { _ ->
                        GameMode.entries.map { it.name.lowercase() }
                    }
                }
                
                // World suggestions
                providers.addProviderForAnnotation(bruh.commands.commonservercommands.commands.SuggestWorld::class.java) { _ ->
                    SuggestionProvider { _ ->
                        server.worlds.map { it.name }
                    }
                }
                
                // Online player suggestions
                providers.addProviderForAnnotation(bruh.commands.commonservercommands.commands.SuggestOnlinePlayer::class.java) { _ ->
                    SuggestionProvider { _ ->
                        server.onlinePlayers.map { it.name }
                    }
                }
                
                // Entity type suggestions
                providers.addProviderForAnnotation(bruh.commands.commonservercommands.commands.SuggestEntityType::class.java) { _ ->
                    SuggestionProvider { _ ->
                        EntityType.entries
                            .filter { it.isSpawnable && it.isAlive }
                            .map { it.name.lowercase() }
                    }
                }
            }
            .build()

        // Register all common server commands using factory
        val commandFactory = CommonServerCommandsFactory(this)
        commandFactory.createAllCommands().forEach { lamp.register(it) }
        
        // Register plugin-specific main command
        lamp.register(EssentiallyStatelessMainCommand(this, translations, configLoader))
    }
}
