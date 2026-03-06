package bruh.auctionhouse

import bruh.auctionhouse.commands.AuctionAdminCommands
import bruh.auctionhouse.commands.AuctionHouseCommands
import bruh.auctionhouse.commands.OrderCommands
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.config.AuctionHouseConfigLoader
import bruh.auctionhouse.database.AuctionHouseSchema
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.ConsolidatedExpiredItemRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.database.NotificationRepository
import bruh.auctionhouse.database.OrderFillRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.economy.VaultEconomyProvider
import bruh.auctionhouse.hooks.PlaceholderAPIHook
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.ConsolidatedExpiredItemsMigration
import bruh.auctionhouse.service.ExpiredItemManager
import bruh.auctionhouse.service.ExpirationService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.DatabaseConfig
import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.createDatabase
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import bruh.zchat.utils.translations.translationApi
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import org.slf4j.Logger
import revxrsal.commands.bukkit.BukkitLamp

/**
 * AuctionHouse - A GUI-based Auction House plugin with dual-mode auctions and Order system.
 *
 * Features:
 * - Auction System with bidding and Buy-It-Now (BIN)
 * - Order system for bulk item requests
 * - Vault economy integration
 * - Configurable fees and restrictions
 * - Translation system support
 * - PlaceholderAPI integration
 * - Automatic expiration handling
 */
class AuctionHousePlugin : SuspendingJavaPlugin() {

    companion object {
        lateinit var instance: AuctionHousePlugin
            private set
    }

    lateinit var config: AuctionHouseConfig
        private set
    lateinit var configLoader: AuctionHouseConfigLoader
        private set
    lateinit var translations: TranslationAPI
        private set
    lateinit var economy: EconomyProvider
        private set
    lateinit var menuAPI: MenuAPI
        private set
    lateinit var auctionService: AuctionService
        private set
    lateinit var orderService: OrderService
        private set

    internal lateinit var database: Database
        private set
    lateinit var auctionRepository: AuctionRepository
        private set
    lateinit var bidRepository: BidRepository
        private set
    lateinit var orderRepository: OrderRepository
        private set
    lateinit var orderFillRepository: OrderFillRepository
        private set
    lateinit var expiredItemRepository: ExpiredItemRepository
        private set
    lateinit var consolidatedExpiredItemRepository: ConsolidatedExpiredItemRepository
        private set
    lateinit var consolidatedExpiredItemService: ConsolidatedExpiredItemService
        private set
    lateinit var transactionRepository: TransactionRepository
        private set
    lateinit var watchlistRepository: WatchlistRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var expirationService: ExpirationService
        private set

    /**
     * Tracks whether the plugin is fully enabled and ready.
     */
    var isReady: Boolean = false
        private set

    override suspend fun onLoadAsync() {
        instance = this
        slF4JLogger.info("Loading AuctionHouse...")
    }

    override suspend fun onEnableAsync() {
        slF4JLogger.info("Enabling AuctionHouse v${pluginMeta.version}...")

        // Step 1: Load configuration
        configLoader = AuctionHouseConfigLoader(dataFolder.toPath(), slF4JLogger)
        config = configLoader.load()
        slF4JLogger.info("Configuration loaded")

        // Step 2: Initialize translation system
        translations = translationApi()
        translations.register("auctions", AuctionMessages::class)
        translations.register("orders", OrderMessages::class)
        translations.register("gui", GuiMessages::class)
        translations.switchLanguage(config.language)
        translations.load()
        slF4JLogger.info("Translation system initialized with language: ${config.language}")

        // Step 3: Initialize economy
        economy = VaultEconomyProvider(this, slF4JLogger, config.economy)
        if (!economy.isAvailable) {
            slF4JLogger.error("Failed to initialize economy! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }
        slF4JLogger.info("Economy provider initialized: ${economy.name}")

        // Step 4: Initialize database
        val dbConfig = DatabaseConfig(
            dialect = config.database.type,
            sqliteFile = config.database.sqliteFile,
            host = config.database.host,
            port = config.database.port,
            database = config.database.database,
            username = config.database.username,
            password = config.database.password,
            poolSize = config.database.poolSize
        )

        database = createDatabase(dbConfig) {
            schema(AuctionHouseSchema)
        }

        val migrationReport = database.initialize()
        slF4JLogger.info("Database initialized: ${migrationReport.totalApplied} migrations applied")

        // Step 5: Create repositories
        auctionRepository = AuctionRepository(database)
        bidRepository = BidRepository(database)
        orderRepository = OrderRepository(database)
        orderFillRepository = OrderFillRepository(database)
        expiredItemRepository = ExpiredItemRepository(database)
        consolidatedExpiredItemRepository = ConsolidatedExpiredItemRepository(database)
        transactionRepository = TransactionRepository(database)
        watchlistRepository = WatchlistRepository(database)
        notificationRepository = NotificationRepository(database)
        slF4JLogger.info("Repositories created")

        // Step 5.5: Run data migration if needed
        val migration = ConsolidatedExpiredItemsMigration(
            database, expiredItemRepository, consolidatedExpiredItemRepository, slF4JLogger
        )
        migration.migrate()
        slF4JLogger.info("Data migration check completed")

        // Step 6: Create services
        val serverId = server.name

        val expiredItemManager = ExpiredItemManager(
            expiredItemRepository, consolidatedExpiredItemRepository
        )

        consolidatedExpiredItemService = ConsolidatedExpiredItemService(
            consolidatedExpiredItemRepository, expiredItemRepository
        )

        auctionService = AuctionService(
            this, config, database, auctionRepository, bidRepository, expiredItemRepository,
            expiredItemManager, transactionRepository, economy, translations, serverId
        )

        orderService = OrderService(
            this, config, orderRepository, orderFillRepository, expiredItemRepository,
            expiredItemManager, transactionRepository, economy, translations, serverId
        )

        slF4JLogger.info("Services created")

        // Step 7: Initialize MenuAPI
        menuAPI = MenuAPI(this)

        // Step 8: Initialize and start expiration service
        expirationService = ExpirationService(
            this, auctionService, orderService, config, slF4JLogger
        )
        expirationService.start()

        // Step 8.5: Register player listener for login notifications
        val playerListener = bruh.auctionhouse.listeners.PlayerListener(
            this, config, translations, auctionService, bidRepository, orderRepository, orderFillRepository
        )
        server.pluginManager.registerEvents(playerListener, this)
        slF4JLogger.info("Player listener registered for login notifications")

        // Step 9: Register commands
        val lamp = BukkitLamp.builder(this).build()
        lamp.register(AuctionHouseCommands(this, config, auctionService, orderService, consolidatedExpiredItemService, translations, menuAPI, economy))
        lamp.register(OrderCommands(this, config, orderService, translations, menuAPI))
        lamp.register(AuctionAdminCommands(this, config, auctionService, auctionRepository, transactionRepository, economy, translations, menuAPI))
        slF4JLogger.info("Commands registered")

        // Step 10: Register PlaceholderAPI hook if available
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            PlaceholderAPIHook(
                this,
                auctionRepository,
                orderRepository,
                consolidatedExpiredItemService
            ).register()
            slF4JLogger.info("PlaceholderAPI integration enabled")
        } else {
            slF4JLogger.info("PlaceholderAPI not found - placeholders disabled")
        }

        isReady = true
        slF4JLogger.info("AuctionHouse enabled successfully!")
        slF4JLogger.info("Language: ${config.language}")
        slF4JLogger.info("Database: ${config.database.type}")
    }

    override suspend fun onDisableAsync() {
        slF4JLogger.info("Disabling AuctionHouse...")
        isReady = false

        // Stop expiration service
        if (::expirationService.isInitialized) {
            expirationService.stop()
        }

        // Close MenuAPI
        if (::menuAPI.isInitialized) {
            menuAPI.close()
        }

        // Close database
        if (::database.isInitialized) {
            database.close()
        }

        // Save configuration
        if (::configLoader.isInitialized && ::config.isInitialized) {
            configLoader.save(config)
        }

        slF4JLogger.info("AuctionHouse disabled.")
    }


    /**
     * Reloads the plugin configuration.
     */
    suspend fun reloadPluginConfig() {
        slF4JLogger.info("Reloading configuration...")
        config = configLoader.reload()
        translations.switchLanguage(config.language)
        translations.load()
        slF4JLogger.info("Configuration reloaded")
    }

    /**
     * Gets the plugin logger.
     */
    fun getSlf4jLogger(): Logger = slF4JLogger
}
