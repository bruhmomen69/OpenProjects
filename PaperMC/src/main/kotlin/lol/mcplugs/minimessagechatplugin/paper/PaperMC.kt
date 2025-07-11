package lol.mcplugs.minimessagechatplugin.paper

import revxrsal.commands.Lamp
import revxrsal.commands.bukkit.BukkitLamp
import lol.mcplugs.minimessagechatplugin.paper.commands.ChatPluginCommands
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.listeners.ChatMessageListener
import lol.mcplugs.minimessagechatplugin.paper.listeners.PlayerJoinQuitListener
import lol.mcplugs.minimessagechatplugin.paper.listeners.PlayerDeathListener
import lol.mcplugs.minimessagechatplugin.paper.listeners.PlayerAdvancementListener
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import lol.mcplugs.minimessagechatplugin.paper.services.PlaceholderAPIService
import lol.mcplugs.minimessagechatplugin.paper.services.ChatToggleService
import lol.mcplugs.minimessagechatplugin.paper.services.SocialSpyService
import lol.mcplugs.minimessagechatplugin.paper.services.PrivateMessageService
import lol.mcplugs.minimessagechatplugin.paper.services.MessageFormattingService
import lol.mcplugs.minimessagechatplugin.paper.commands.*
import org.bukkit.plugin.java.JavaPlugin

class PaperMC : JavaPlugin() {
    private lateinit var configManager: ConfigManager
    private lateinit var placeholderAPIService: PlaceholderAPIService
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
        lamp.register(ReplyCommand(privateMessageService, messageFormattingService))
        
        // Register chat toggle and admin commands
        lamp.register(ChatToggleCommands(chatToggleService, socialSpyService, privateMessageService, messageFormattingService))
        lamp.register(ChatAdminCommands(chatToggleService, socialSpyService, privateMessageService, messageFormattingService))

        // Register event listeners
        server.pluginManager.registerEvents(ChatMessageListener(configManager, chatFormattingService, chatToggleService, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerJoinQuitListener(configManager, chatFormattingService, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerDeathListener(configManager, messageFormattingService), this)
        server.pluginManager.registerEvents(PlayerAdvancementListener(configManager, messageFormattingService), this)

        logger.info("MiniMessageChatPlugin enabled successfully!")
        logger.info("Features enabled:")
        logger.info("  - Chat formatting: ${configManager.config.features.enableColorCodes}")
        logger.info("  - Group formats: ${configManager.config.chatFormat.enableGroupFormats}")
        logger.info("  - World formats: ${configManager.config.chatFormat.enableWorldFormats}")
        logger.info("  - Join messages: ${configManager.config.features.enableJoinMessages}")
        logger.info("  - Leave messages: ${configManager.config.features.enableLeaveMessages}")
        logger.info("  - Death messages: ${configManager.config.features.enableDeathMessages}")
        logger.info("  - Chat cooldown: ${configManager.config.features.enableChatCooldown}")
        logger.info("  - Mentions: ${configManager.config.features.enableMentions}")
        logger.info("  - URLs: ${configManager.config.features.enableUrls}")
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
        
        logger.info("MiniMessageChatPlugin disabled!")
    }
}
