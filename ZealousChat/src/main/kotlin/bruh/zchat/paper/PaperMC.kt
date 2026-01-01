package bruh.zchat.paper

import bruh.zchat.paper.commands.*
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.BlockMigrationService
import bruh.zchat.paper.database.DBPlayerQueries
import bruh.zchat.paper.database.DatabaseMaintenanceService
import bruh.zchat.paper.database.PlayerDataManager
import bruh.zchat.paper.database.ZealousChatSchema
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.createDatabase
import bruh.zchat.paper.listeners.ChatMessageListener
import bruh.zchat.paper.menus.MenuService
import bruh.zchat.paper.listeners.InventoryProtectionListener
import bruh.zchat.paper.listeners.channel.ChannelCommandListener
import bruh.zchat.paper.listeners.playerstatus.PlayerAdvancementListener
import bruh.zchat.paper.listeners.playerstatus.PlayerDeathListener
import bruh.zchat.paper.listeners.playerstatus.PlayerJoinQuitListener
import bruh.zchat.paper.services.*
import bruh.zchat.paper.services.channel.ChannelFormattingService
import bruh.zchat.paper.services.channel.ChannelService
import bruh.zchat.paper.services.snapshots.FileInventorySnapshotStore
import bruh.zchat.paper.services.snapshots.InventorySnapshotStore
import bruh.zchat.paper.services.snapshots.RedisInventorySnapshotStore
import bruh.zchat.paper.services.snapshots.SqlInventorySnapshotStore
import bruh.zchat.paper.swearfilter.InfractionManager
import bruh.zchat.paper.swearfilter.SwearFilterService
import bruh.zchat.paper.utils.ModrinthUpdateChecker
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext
import revxrsal.commands.Lamp
import revxrsal.commands.bukkit.BukkitLamp

class PaperMC : SuspendingJavaPlugin() {
    private lateinit var configManager: ConfigManager
    private lateinit var database: Database
    private lateinit var playerDataManager: PlayerDataManager
    private lateinit var databaseMaintenanceService: DatabaseMaintenanceService
    private lateinit var scheduledTaskService: ScheduledTaskService
    private lateinit var placeholderAPIService: PlaceholderAPIService
    private lateinit var chatInventoryPlaceholderService: ChatInventoryPlaceholderService
    private lateinit var messageFormattingService: MessageFormattingService
    private lateinit var channelService: ChannelService
    private lateinit var channelFormattingService: ChannelFormattingService
    private lateinit var chatToggleService: ChatToggleService
    private lateinit var socialSpyService: SocialSpyService
    private lateinit var blockService: BlockService
    private lateinit var privateMessageService: PrivateMessageService
    private lateinit var chatFormattingService: ChatFormattingService
    private lateinit var alertService: AlertService
    private lateinit var crossServerMessageBusService: CrossServerMessageBusService
    private lateinit var infractionManager: InfractionManager
    private lateinit var swearFilterService: SwearFilterService
    private lateinit var blockMigrationService: BlockMigrationService
    private lateinit var inventorySnapshotStore: InventorySnapshotStore
    private lateinit var lamp: Lamp<*>
    private lateinit var channelCommandService: ChannelCommandService
    private lateinit var menuService: MenuService
    private lateinit var chatInputService: ChatInputService

    // Server instance ID for cross-server messaging
    val serverInstanceId = java.util.UUID.randomUUID().toString()

    override suspend fun onEnableAsync() {
        // Initialize configuration
        configManager = ConfigManager(dataFolder.toPath())
        if (!configManager.loadConfig()) {
            logger.severe("Failed to load configuration! Using defaults.")
        }

        // Update the configuration
        configManager.saveConfig()

        // Register permissions manually if loaded via PaperLoader
        if (PaperMCLoader.loadedByPaperLoader) {
            logger.info("Plugin loaded via PaperLoader, manually registering permissions...")
            val permissionRegistrar = PermissionRegistrar(this)
            if (!permissionRegistrar.registerPermissions()) {
                logger.warning("Failed to register permissions from plugin.yml. Permissions may not work correctly.")
            }
        }

        ModrinthUpdateChecker(
            projectId = "zealouschat",
            loader = "paper",
            minecraftVersion = server.minecraftVersion
        ).checkVersion { latestVersion ->
            val currentVersion = description.version
            if (ModrinthUpdateChecker.isNewerVersion(latestVersion, currentVersion)) {
                logger.warning(
                    "A newer version of ZealousChat is available on Modrinth (current=$currentVersion, latest=$latestVersion): " +
                            "https://modrinth.com/project/zealouschat"
                )
            }
        }

        // Initialize database with new API
        val dbConfig = createDatabaseConfig()
        database = createDatabase(dbConfig) {
            schema(ZealousChatSchema)
        }
        
        // Run database migrations
        try {
            val migrationReport = database.initialize()
            logger.info("Database migrations completed: ${migrationReport.totalApplied} migrations applied")
            if (migrationReport.hasChanges) {
                logger.info(migrationReport.toSummary())
            }
        } catch (e: Exception) {
            slF4JLogger.error("Failed to run database migrations: ${e.message}", e)
            server.pluginManager.disablePlugin(this)
            return
        }
        
        val dbPlayerQueries = DBPlayerQueries(database)
        playerDataManager = PlayerDataManager(database, dbPlayerQueries)

        // Initialize services
        placeholderAPIService = PlaceholderAPIService(configManager, this)
        messageFormattingService = MessageFormattingService(configManager, placeholderAPIService)
        channelService = ChannelService(this, configManager, placeholderAPIService)
        channelFormattingService = ChannelFormattingService(configManager, messageFormattingService, channelService)
        inventorySnapshotStore = createInventorySnapshotStore()
        chatInventoryPlaceholderService = ChatInventoryPlaceholderService(
            this,
            configManager,
            messageFormattingService,
            inventorySnapshotStore,
            serverInstanceId,
            placeholderAPIService
        )
        chatToggleService = ChatToggleService(this, configManager, messageFormattingService, playerDataManager)
        socialSpyService = SocialSpyService(configManager, messageFormattingService)
        alertService = bruh.zchat.paper.services.AlertService(this, configManager, messageFormattingService)
        blockService =
            BlockService(configManager, messageFormattingService, socialSpyService, dbPlayerQueries, playerDataManager)
        privateMessageService = PrivateMessageService(
            configManager,
            messageFormattingService,
            chatToggleService,
            socialSpyService,
            blockService,
            playerDataManager
        )
        crossServerMessageBusService = CrossServerMessageBusService(
            this,
            configManager,
            dbPlayerQueries,
            playerDataManager,
            privateMessageService,
            socialSpyService,
            messageFormattingService,
            channelService,
            channelFormattingService,
            serverInstanceId
        )
        // Wire up circular dependency
        privateMessageService.crossServerMessageBusService = crossServerMessageBusService

        chatFormattingService = ChatFormattingService(configManager, messageFormattingService)
        infractionManager = InfractionManager(dbPlayerQueries, playerDataManager)
        swearFilterService =
            SwearFilterService(this, configManager, infractionManager, alertService, messageFormattingService)
        chatInputService = ChatInputService(this)
        blockMigrationService = BlockMigrationService(database, dataFolder.toPath(), configManager.storage.database.dataRetentionDays)

        // Initialize menu service for GUI menus
        menuService = MenuService(
            plugin = this,
            configManager = configManager,
            blockService = blockService,
            playerDataManager = playerDataManager,
            messageFormattingService = messageFormattingService,
            infractionManager = infractionManager,
            chatInputService = chatInputService
        )
        menuService.initialize()

        // Initialize ChannelCommandService
        channelCommandService = ChannelCommandService(
            channelService,
            messageFormattingService,
            configManager,
            configManager.messages,
            this
        )

        // Set channel command aliases
        channelCommandService.updateChannelsCommandAlias()

        // Register dynamic channel commands if full tab completion is enabled
        if (configManager.channels.settings.enableFullTabCompletion && configManager.channels.settings.enabled) {
            channelCommandService.registerDynamicChannelCommands()
        }

        // Initialize maintenance services
        databaseMaintenanceService = DatabaseMaintenanceService(dbPlayerQueries, configManager.storage.database)
        scheduledTaskService = ScheduledTaskService(
            this,
            configManager,
            databaseMaintenanceService,
            playerDataManager,
            crossServerMessageBusService,
            channelService
        )

        // Schedule maintenance tasks
        scheduledTaskService.scheduleMaintenanceTasks()
        scheduledTaskService.scheduleChannelIdentifierRefresh()

        // Schedule cross-server tasks
        if (configManager.storage.crossServerMessaging.enabled) {
            val isRedisBackend = configManager.storage.crossServerMessaging.backend.equals("redis", ignoreCase = true)
            if (database.dialect == DatabaseDialect.MYSQL || database.dialect == DatabaseDialect.POSTGRES) {
                // Initialize Redis backend if selected
                if (isRedisBackend) {
                    try {
                        crossServerMessageBusService.start()
                    } catch (e: Exception) {
                        slF4JLogger.error(
                            "Failed to start Redis cross-server message bus; disabling cross-server messaging",
                            e
                        )
                        val newStorage = configManager.storage.copy(
                            crossServerMessaging = configManager.storage.crossServerMessaging.copy(enabled = false)
                        )
                        configManager.updateStorage(newStorage)
                    }
                }
                if (configManager.storage.crossServerMessaging.enabled) {
                    scheduledTaskService.scheduleCrossServerTasks(serverInstanceId)
                    logger.info("Cross-server messaging enabled with Server ID: $serverInstanceId (backend=${configManager.storage.crossServerMessaging.backend})")
                }
            } else {
                logger.info("Cross-server messaging requires MySQL. Disabling crossServerMessaging.enabled in config.")
                val newStorage = configManager.storage.copy(
                    crossServerMessaging = configManager.storage.crossServerMessaging.copy(enabled = false)
                )
                configManager.updateStorage(newStorage)
            }
        }

        // Migrate existing data if enabled (run on async dispatcher)
        if (configManager.storage.database.autoMigrate) {
            launch {
                withContext(asyncDispatcher) {
                    try {
                        val migrationResult = blockMigrationService.migrateBlockData()
                        if (migrationResult.success) {
                            slF4JLogger.info("Block migration: ${migrationResult.message}")
                        } else {
                            slF4JLogger.warn("Block migration failed: ${migrationResult.message}")
                        }
                    } catch (e: Exception) {
                        slF4JLogger.error("Failed to migrate block data", e)
                    }
                }
            }
        }

        // Initialize command framework
        ChannelSuggestionProviders.initialize(channelService)
        lamp = BukkitLamp.builder(this).build()

        // Register commands
        val commands = ChatPluginCommands(
            configManager,
            chatFormattingService,
            messageFormattingService,
            alertService,
            channelCommandService
        )
        lamp.register(commands)
        lamp.register(ChatPluginCommands.FormatCommands(configManager))
        lamp.register(ChatPluginCommands.ToggleCommands(configManager))

        if (configManager.channels.settings.enabled) {
            lamp.register(ChannelCommands(configManager, channelService, messageFormattingService))
        }

        // Register private message commands
        lamp.register(MessageCommand(privateMessageService, messageFormattingService, this))

        // Register block commands
        lamp.register(MessageCommand.BlockCommand(blockService, messageFormattingService, menuService, this))

        // Register inventory view command
        lamp.register(InventoryViewCommand(this, chatInventoryPlaceholderService))

        // Register chat toggle and admin commands
        lamp.register(
            ChatToggleCommands(
                chatToggleService,
                socialSpyService,
                privateMessageService,
                messageFormattingService
            )
        )
        lamp.register(
            ChatAdminCommands(
                configManager,
                chatToggleService,
                socialSpyService,
                privateMessageService,
                messageFormattingService,
                blockService,
                alertService,
                menuService,
                this
            )
        )

        // Register alert commands
        lamp.register(AlertCommands(alertService, messageFormattingService))

        // Register global toggle commands
        lamp.register(
            GlobalToggleCommands(
                configManager,
                chatToggleService,
                messageFormattingService
            )
        )

        // Register event listeners
        server.pluginManager.registerEvents(
            ChatMessageListener(
                configManager,
                chatFormattingService,
                channelFormattingService,
                channelService,
                chatToggleService,
                messageFormattingService,
                chatInventoryPlaceholderService,
                swearFilterService,
                socialSpyService,
                crossServerMessageBusService,
                this
            ), this
        )
        server.pluginManager.registerEvents(
            PlayerJoinQuitListener(
                configManager,
                chatFormattingService,
                chatToggleService,
                messageFormattingService,
                playerDataManager,
                alertService,
                channelService,
                this
            ), this
        )
        server.pluginManager.registerEvents(
            ChannelCommandListener(
                channelCommandService,
            ), this
        )
        server.pluginManager.registerEvents(PlayerDeathListener(configManager, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerAdvancementListener(configManager, messageFormattingService), this)
        server.pluginManager.registerEvents(InventoryProtectionListener(), this)

        // Switch to global region dispatcher for final setup
        withContext(globalRegionDispatcher) {
            logger.info("ZealousChat enabled successfully!")
            logger.info("Features enabled:")
            logger.info("  - Chat formatting: ${configManager.config.chat.enableFormatting}")
            logger.info("  - Colors: ${configManager.config.chat.enableColorCodes}")
            logger.info("  - Text formatting: ${configManager.config.chat.enableTextFormatting}")
            logger.info("  - Group formats: ${configManager.config.chatFormat.enableGroupFormats}")
            logger.info("  - World formats: ${configManager.config.chatFormat.enableWorldFormats}")
            logger.info("  - Join messages: ${configManager.config.joinLeave.enableJoin}")
            logger.info("  - Leave messages: ${configManager.config.joinLeave.enableLeave}")
            logger.info("  - Death messages: ${configManager.config.death.enabled}")
            logger.info("  - Chat cooldown: ${configManager.config.chat.enableCooldown} (${configManager.config.chat.cooldownSeconds}s)")
            logger.info("  - Mentions: ${configManager.config.chat.enableMentions}")
            logger.info("  - URLs: ${configManager.config.chat.enableUrls}")
            logger.info("  - Private messages: ${configManager.config.privateMessages.enablePrivateMessages}")
            logger.info("  - Social spy: ${configManager.config.socialSpy.enableSocialSpy}")
            logger.info("  - Swear filter: ${configManager.config.swearFilter.enabled}")
            logger.info("  - PlaceholderAPI: ${placeholderAPIService.isEnabled()}")
        }
    }

    override suspend fun onDisableAsync() {
        // Unregister dynamic channel commands
        if (::channelCommandService.isInitialized) {
            channelCommandService.unregisterDynamicChannelCommands()
        }

        // Cancel scheduled tasks
        if (::scheduledTaskService.isInitialized) {
            scheduledTaskService.cancelAllTasks()
        }

        // Close Redis backend resources (if any)
        if (::crossServerMessageBusService.isInitialized) {
            crossServerMessageBusService.close()
        }

        // Close menu service
        if (::menuService.isInitialized) {
            menuService.close()
        }

        // Close chat input service
        if (::chatInputService.isInitialized) {
            chatInputService.close()
        }

        // Close inventory snapshot store
        if (::inventorySnapshotStore.isInitialized) {
            inventorySnapshotStore.close()
        }

        // Close database
        if (::database.isInitialized) {
            database.close()
        }

        // Save configuration
        if (::configManager.isInitialized) {
            configManager.saveConfig()
        }

        logger.info("ZealousChat disabled!")
    }

    private fun createDatabaseConfig(): bruh.zchat.utils.database.DatabaseConfig {
        val dbConfig = configManager.storage.database
        return bruh.zchat.utils.database.DatabaseConfig(
            dialect = dbConfig.type,
            host = dbConfig.host,
            port = dbConfig.port,
            database = dbConfig.database,
            username = dbConfig.username,
            password = dbConfig.password,
            sqliteFile = dbConfig.sqliteFile,
            poolName = "ZealousChat-Pool",
            poolSize = dbConfig.poolSize,
            connectionTimeout = dbConfig.connectionTimeout,
            maxLifetime = dbConfig.maxLifetime,
            leakDetectionThreshold = dbConfig.leakDetectionThreshold
        )
    }

    private fun createInventorySnapshotStore(): InventorySnapshotStore {
        val storageCfg = configManager.storage
        val backend = storageCfg.inventorySnapshots.backend.lowercase()
        return when (backend) {
            "fs" -> FileInventorySnapshotStore(
                dataFolder.toPath().resolve("inventory_snapshots"),
                configManager
            )

            "sql" -> SqlInventorySnapshotStore(database)
            "redis" -> {
                val redisCfg = storageCfg.database.redis
                RedisInventorySnapshotStore(
                    redisConfig = redisCfg,
                    keyPrefix = storageCfg.inventorySnapshots.redisKeyPrefix
                )
            }

            else -> {
                logger.warning("Unknown inventorySnapshots backend '$backend', defaulting to fs")
                FileInventorySnapshotStore(
                    dataFolder.toPath().resolve("inventory_snapshots"),
                    configManager
                )
            }
        }
    }
}
