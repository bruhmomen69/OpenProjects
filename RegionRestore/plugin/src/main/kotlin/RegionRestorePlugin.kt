package bruh.regionrestore

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.runBlocking
import revxrsal.commands.bukkit.BukkitLamp
import revxrsal.commands.autocomplete.SuggestionProvider
import bruh.regionrestore.cmd.SuggestTemplateName
import bruh.regionrestore.cmd.SuggestVersionId
import bruh.regionrestore.cmd.SuggestVersionNumber
import bruh.regionrestore.cmd.SuggestInstanceId
import bruh.regionrestore.api.RegionRestore as RegionRestoreApiHolder
import bruh.regionrestore.api.createRegionRestoreApi
import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.cmd.RegionRestoreCommands
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.config.RegionRestoreConfigLoader
import bruh.regionrestore.loader.PaperNmsAdapterLoader
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.selection.SelectionService
import bruh.regionrestore.selection.SelectionWandService
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.template.TemplateCache
import bruh.regionrestore.timer.SchedulerService
import bruh.regionrestore.translations.CommandMessages
import bruh.regionrestore.translations.GuiMessages
import bruh.regionrestore.hooks.PlaceholderAPIHook
import bruh.regionrestore.hooks.MiniPlaceholdersHook
import bruh.zchat.utils.itemapi.ItemAPI
import bruh.zchat.utils.itemapi.HoconItemDataStore
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import bruh.zchat.utils.translations.translationApi

class RegionRestorePlugin : SuspendingJavaPlugin() {
    lateinit var nmsAdapter: PaperNmsAdapter
        private set
    lateinit var config: RegionRestoreConfig
        private set
    lateinit var templateCache: TemplateCache
        private set
    lateinit var templateRepository: TemplateRepository
        private set
    lateinit var notificationService: NotificationService
        private set
    lateinit var schedulerService: SchedulerService
        private set
    lateinit var massClonerService: MassClonerService
        private set
    lateinit var configLoader: RegionRestoreConfigLoader
        private set
    lateinit var menuAPI: MenuAPI
        private set
    lateinit var translations: TranslationAPI
        private set
    lateinit var itemAPI: ItemAPI
        private set
    lateinit var selectionService: SelectionService
        private set
    lateinit var selectionWandService: SelectionWandService
        private set

    override suspend fun onLoadAsync() {
        nmsAdapter = PaperNmsAdapterLoader.load()
        slF4JLogger.info("Loaded NMS adapter for Minecraft version ${nmsAdapter.minecraftVersion}")

        configLoader = RegionRestoreConfigLoader(dataFolder.toPath(), slF4JLogger)
        config = configLoader.load(nmsAdapter)

        slF4JLogger.info("Loaded configuration")
    }

    override suspend fun onEnableAsync() {
        slF4JLogger.info("Loading RegionRestore...")

        // Initialize translation system
        translations = translationApi()
        translations.register("commands", CommandMessages::class)
        translations.register("gui", GuiMessages::class)
        slF4JLogger.info("Translation system initialized")
        // Switch to configured language before loading to ensure correct files are read
        translations.switchLanguage(config.language)
        slF4JLogger.info("Switched to language ${config.language}")
        translations.load()
        slF4JLogger.info("Translation system loaded")

        menuAPI = MenuAPI(this)

        // Initialize ItemAPI for tracked items (wand, etc.)
        // Using HoconItemDataStore for file-based persistence
        val itemsDataStore = HoconItemDataStore(dataFolder.toPath().resolve("items"))
        itemAPI = ItemAPI(this, itemsDataStore)

        // Initialize selection system
        selectionService = SelectionService()
        selectionWandService = SelectionWandService(this, itemAPI, selectionService, translations)
        slF4JLogger.info("Selection wand system initialized")

        templateRepository = TemplateRepository(dataFolder.toPath(), slF4JLogger, nmsAdapter)

        // Initialize cache with settings from config
        templateCache = TemplateCache(
            repository = templateRepository,
            ttlMinutes = config.templates.cacheTtlMinutes,
            lruMaxSize = config.templates.cacheMaxSize
        )
        templateRepository.setCache(templateCache)
        slF4JLogger.info("Initialized template cache (TTL=${config.templates.cacheTtlMinutes}m, max=${config.templates.cacheMaxSize})")

        // Load template repository (scans templates folder, logs what's available)
        templateRepository.load()

        notificationService = NotificationService(this, config.notifications)

        schedulerService = SchedulerService(this, notificationService, config.restore, config.notifications, nmsAdapter)

        massClonerService = MassClonerService(this, nmsAdapter, templateRepository, schedulerService, config.massCloner, config.restore, templateCache)
        massClonerService.initialize()

        // Expose public API for other plugins
        val api = createRegionRestoreApi(
            config = config,
            configLoader = configLoader,
            nmsAdapter = nmsAdapter,
            templateRepository = templateRepository,
            notificationService = notificationService,
            schedulerService = schedulerService,
            massClonerService = massClonerService,
            plugin = this
        )
        RegionRestoreApiHolder.setApi(api)

        setupCommands()
        setupPlaceholderHooks()

        slF4JLogger.info("RegionRestore enabled!")
    }

    override suspend fun onDisableAsync() {
        slF4JLogger.info("Disabling RegionRestore...")

        massClonerService.saveState()
        schedulerService.cancelAll()
        massClonerService.shutdown()
        selectionWandService.close()
        itemAPI.close()
        templateCache.clear()
        templateRepository.save()

        // Clear API reference
        RegionRestoreApiHolder.clearApi()

        slF4JLogger.info("RegionRestore disabled!")
    }

    private fun setupCommands() {
        val lamp = BukkitLamp.builder(this)
            .suggestionProviders { providers ->
                // Template name suggestions
                providers.addProviderForAnnotation(SuggestTemplateName::class.java) { _ ->
                    SuggestionProvider { _ ->
                        runBlocking { templateRepository.listTemplates() }
                    }
                }
                
                // Version ID suggestions for string parameters - includes special "active" keyword
                providers.addProviderForAnnotation(SuggestVersionId::class.java) { _ ->
                    SuggestionProvider { _ ->
                        val allVersionIds = runBlocking {
                            templateRepository.listTemplates().flatMap { templateName ->
                                templateRepository.getTemplateVersions(templateName)
                                    ?.map { it.versionId.toString() }
                                    ?: emptyList()
                            }.distinct()
                        }
                        listOf("active") + allVersionIds
                    }
                }

                // Numeric-only version ID suggestions for Int parameters
                providers.addProviderForAnnotation(SuggestVersionNumber::class.java) { _ ->
                    SuggestionProvider { _ ->
                        runBlocking {
                            templateRepository.listTemplates().flatMap { templateName ->
                                templateRepository.getTemplateVersions(templateName)
                                    ?.map { it.versionId.toString() }
                                    ?: emptyList()
                            }.distinct()
                        }
                    }
                }

                // Instance ID suggestions for commands that take an instanceId parameter
                providers.addProviderForAnnotation(SuggestInstanceId::class.java) { _ ->
                    SuggestionProvider { _ ->
                        massClonerService.listInstances(null, null)
                            .map { it.instanceId.toString() }
                            .distinct()
                    }
                }
            }
            .build()
        lamp.register(RegionRestoreCommands(
            nmsAdapter,
            templateRepository,
            schedulerService,
            config,
            massClonerService,
            menuAPI,
            this,
            translations,
            selectionService,
            selectionWandService
        ))
    }

    private fun setupPlaceholderHooks() {
        // Register PlaceholderAPI expansion if available
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            try {
                PlaceholderAPIHook(this, massClonerService, templateRepository).register()
                slF4JLogger.info("PlaceholderAPI expansion registered")
            } catch (e: Exception) {
                slF4JLogger.warn("Failed to register PlaceholderAPI expansion", e)
            }
        }

        // Register MiniPlaceholders expansion if available
        if (server.pluginManager.isPluginEnabled("MiniPlaceholders")) {
            try {
                MiniPlaceholdersHook.register(massClonerService, templateRepository)
                slF4JLogger.info("MiniPlaceholders expansion registered")
            } catch (e: Exception) {
                slF4JLogger.warn("Failed to register MiniPlaceholders expansion", e)
            }
        }
    }
}
