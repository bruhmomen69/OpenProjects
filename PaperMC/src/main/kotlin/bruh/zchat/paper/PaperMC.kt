package bruh.zchat.paper

import bruh.zchat.paper.commands.*
import revxrsal.commands.Lamp
import revxrsal.commands.bukkit.BukkitLamp
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.listeners.ChatMessageListener
import bruh.zchat.paper.listeners.PlayerJoinQuitListener
import bruh.zchat.paper.listeners.PlayerDeathListener
import bruh.zchat.paper.listeners.PlayerAdvancementListener
import bruh.zchat.paper.listeners.InventoryProtectionListener
import bruh.zchat.paper.services.ChatFormattingService
import bruh.zchat.paper.services.PlaceholderAPIService
import bruh.zchat.paper.services.ChatToggleService
import bruh.zchat.paper.services.SocialSpyService
import bruh.zchat.paper.services.PrivateMessageService
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.services.ChatInventoryPlaceholderService
import org.bukkit.plugin.java.JavaPlugin

class PaperMC : JavaPlugin() {
    private lateinit var configManager: ConfigManager
    private lateinit var placeholderAPIService: PlaceholderAPIService
    private lateinit var chatInventoryPlaceholderService: ChatInventoryPlaceholderService
    private lateinit var messageFormattingService: MessageFormattingService
    private lateinit var chatToggleService: ChatToggleService
    private lateinit var socialSpyService: SocialSpyService
    private lateinit var privateMessageService: PrivateMessageService
    private lateinit var chatFormattingService: ChatFormattingService
    private lateinit var lamp: Lamp<*>

    override fun onEnable() {
        // Initialize configuration
        configManager = ConfigManager(dataFolder.toPath())
        if (!configManager.loadConfig()) {
            logger.severe("Failed to load configuration! Using defaults.")
        }

        // Update the configuration
        configManager.saveConfig()

        // Initialize services
        placeholderAPIService = PlaceholderAPIService(configManager)
        messageFormattingService = MessageFormattingService(configManager, placeholderAPIService)
        chatInventoryPlaceholderService = ChatInventoryPlaceholderService(this, configManager, messageFormattingService)
        chatToggleService = ChatToggleService(configManager, messageFormattingService)
        socialSpyService = SocialSpyService(configManager, messageFormattingService)
        privateMessageService = PrivateMessageService(configManager, messageFormattingService, chatToggleService, socialSpyService)
        chatFormattingService = ChatFormattingService(configManager, messageFormattingService)

        // Initialize command framework
        lamp = BukkitLamp.builder(this).build()
        
        // Register commands
        val commands = ChatPluginCommands(configManager, chatFormattingService, messageFormattingService)
        lamp.register(commands)
        lamp.register(ChatPluginCommands.FormatCommands(configManager))
        lamp.register(ChatPluginCommands.ToggleCommands(configManager))
        
        // Register private message commands
        lamp.register(MessageCommand(privateMessageService, messageFormattingService))

        // Register inventory view command
        lamp.register(InventoryViewCommand(chatInventoryPlaceholderService))
        
        // Register chat toggle and admin commands
        lamp.register(ChatToggleCommands(chatToggleService, socialSpyService, privateMessageService, messageFormattingService))
        lamp.register(ChatAdminCommands(chatToggleService, socialSpyService, privateMessageService, messageFormattingService))

        // Register event listeners
        server.pluginManager.registerEvents(ChatMessageListener(configManager, chatFormattingService, chatToggleService, messageFormattingService, chatInventoryPlaceholderService), this)
        server.pluginManager.registerEvents(PlayerJoinQuitListener(configManager, chatFormattingService, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerDeathListener(configManager, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerAdvancementListener(configManager, messageFormattingService), this)
        server.pluginManager.registerEvents(InventoryProtectionListener(), this)

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
        logger.info("  - PlaceholderAPI: ${placeholderAPIService.isEnabled()}")
    }

    override fun onDisable() {
        // Clear any cooldowns
        if (::chatFormattingService.isInitialized) {
            chatFormattingService.clearAllCooldowns()
        }
        
        // Save configuration
        if (::configManager.isInitialized) {
            configManager.saveConfig()
        }
        
        logger.info("ZealousChat disabled!")
    }
}
