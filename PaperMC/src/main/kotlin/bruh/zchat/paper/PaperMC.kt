package bruh.zchat.paper

import bruh.zchat.paper.commands.*
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.*
import bruh.zchat.paper.listeners.*
import bruh.zchat.paper.services.*
import bruh.zchat.paper.services.AlertService
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
    private lateinit var databaseService: DatabaseService
    private lateinit var playerDataManager: PlayerDataManager
    private lateinit var databaseMaintenanceService: DatabaseMaintenanceService
    private lateinit var scheduledTaskService: ScheduledTaskService
    private lateinit var placeholderAPIService: PlaceholderAPIService
    private lateinit var chatInventoryPlaceholderService: ChatInventoryPlaceholderService
    private lateinit var messageFormattingService: MessageFormattingService
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
    private lateinit var lamp: Lamp<*>
    
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

        // Initialize database
        val dbConfig = createDatabaseConfig()
        databaseService = DatabaseService(dbConfig, this)
        playerDataManager = PlayerDataManager(databaseService)

        // Run database migrations
        try {
            val migrationResult = databaseService.migrate()
            logger.info("Database migrations completed: ${migrationResult.migrationsExecuted} migrations executed")
        } catch (e: Exception) {
            slF4JLogger.error("Failed to run database migrations: ${e.message}", e)
            server.pluginManager.disablePlugin(this)
            return
        }

        // Initialize services
        placeholderAPIService = PlaceholderAPIService(configManager)
        messageFormattingService = MessageFormattingService(configManager, placeholderAPIService)
        chatInventoryPlaceholderService = ChatInventoryPlaceholderService(this, configManager, messageFormattingService)
        chatToggleService = ChatToggleService(this, configManager, messageFormattingService, playerDataManager)
        socialSpyService = SocialSpyService(configManager, messageFormattingService)
        alertService = bruh.zchat.paper.services.AlertService(this, configManager, messageFormattingService)
        blockService =
            BlockService(configManager, messageFormattingService, socialSpyService, databaseService, playerDataManager)
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
            databaseService,
            playerDataManager,
            privateMessageService,
            socialSpyService,
            messageFormattingService,
            serverInstanceId
        )
        // Wire up circular dependency
        privateMessageService.crossServerMessageBusService = crossServerMessageBusService
        
        chatFormattingService = ChatFormattingService(configManager, messageFormattingService)
        infractionManager = InfractionManager(databaseService, playerDataManager)
        swearFilterService = SwearFilterService(this, configManager, infractionManager, alertService)
        blockMigrationService = BlockMigrationService(databaseService, dataFolder.toPath(), dbConfig.dataRetentionDays)

        // Initialize maintenance services
        databaseMaintenanceService = DatabaseMaintenanceService(databaseService, dbConfig)
        scheduledTaskService = ScheduledTaskService(this, configManager, databaseMaintenanceService, playerDataManager, crossServerMessageBusService)
        
        // Schedule maintenance tasks
        scheduledTaskService.scheduleMaintenanceTasks()
        
        // Schedule cross-server tasks
        if (configManager.config.crossServerMessaging.enabled) {
            if (databaseService.databaseType == DatabaseType.MYSQL) {
                scheduledTaskService.scheduleCrossServerTasks(serverInstanceId)
                logger.info("Cross-server messaging enabled with Server ID: $serverInstanceId")
            } else {
                logger.info("Cross-server messaging requires MySQL. Disabling crossServerMessaging.enabled in config.")
                val newConfig = configManager.config.copy(
                    crossServerMessaging = configManager.config.crossServerMessaging.copy(enabled = false)
                )
                configManager.updateConfig(newConfig)
            }
        }

        // Migrate existing data if enabled (run on async dispatcher)
        if (configManager.config.database.autoMigrate) {
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
        lamp = BukkitLamp.builder(this).build()

        // Register commands
        val commands = ChatPluginCommands(configManager, chatFormattingService, messageFormattingService, alertService)
        lamp.register(commands)
        lamp.register(ChatPluginCommands.FormatCommands(configManager))
        lamp.register(ChatPluginCommands.ToggleCommands(configManager))

        // Register private message commands
        lamp.register(MessageCommand(privateMessageService, messageFormattingService, this))

        // Register block commands
        lamp.register(MessageCommand.BlockCommand(blockService, messageFormattingService, this))

        // Register inventory view command
        lamp.register(InventoryViewCommand(chatInventoryPlaceholderService))

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
                chatToggleService,
                socialSpyService,
                privateMessageService,
                messageFormattingService,
                blockService,
                alertService,
                this
            )
        )
        
        // Register alert commands
        lamp.register(bruh.zchat.paper.commands.AlertCommands(alertService, messageFormattingService))

        // Register event listeners
        server.pluginManager.registerEvents(
            ChatMessageListener(
                configManager,
                chatFormattingService,
                chatToggleService,
                messageFormattingService,
                chatInventoryPlaceholderService,
                swearFilterService
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
                this
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
            logger.info("  - Swear filter alerts: ${configManager.config.swearFilter.alerts.enableAlerts}")
            logger.info("  - PlaceholderAPI: ${placeholderAPIService.isEnabled()}")
        }
    }

    override suspend fun onDisableAsync() {
        // Cancel scheduled tasks
        if (::scheduledTaskService.isInitialized) {
            scheduledTaskService.cancelAllTasks()
        }

        // Close database
        if (::databaseService.isInitialized) {
            databaseService.close()
        }

        // Save configuration
        if (::configManager.isInitialized) {
            configManager.saveConfig()
        }

        logger.info("ZealousChat disabled!")
    }

    private fun createDatabaseConfig(): DatabaseConfig {
        val dbConfig = configManager.config.database
        val dbType = when (dbConfig.type.lowercase()) {
            "mysql" -> DatabaseType.MYSQL
            "sqlite" -> DatabaseType.SQLITE
            else -> DatabaseType.SQLITE
        }

        return DatabaseConfig(
            type = dbType,
            host = dbConfig.host,
            port = dbConfig.port,
            database = dbConfig.database,
            username = dbConfig.username,
            password = dbConfig.password,
            sqliteFile = dbConfig.sqliteFile,
            poolSize = dbConfig.poolSize,
            connectionTimeout = dbConfig.connectionTimeout,
            maxLifetime = dbConfig.maxLifetime,
            leakDetectionThreshold = dbConfig.leakDetectionThreshold,
            autoMigrate = dbConfig.autoMigrate,
            enableArchive = dbConfig.enableArchive,
            dataRetentionDays = dbConfig.dataRetentionDays
        )
    }
}
