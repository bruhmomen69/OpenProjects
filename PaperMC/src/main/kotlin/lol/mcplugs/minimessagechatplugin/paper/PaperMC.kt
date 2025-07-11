package lol.mcplugs.minimessagechatplugin.paper

import revxrsal.commands.Lamp
import revxrsal.commands.bukkit.BukkitLamp
import lol.mcplugs.minimessagechatplugin.paper.commands.ChatPluginCommands
import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import lol.mcplugs.minimessagechatplugin.paper.listeners.ChatListener
import lol.mcplugs.minimessagechatplugin.paper.services.ChatFormattingService
import org.bukkit.plugin.java.JavaPlugin

class PaperMC : JavaPlugin() {
    private lateinit var configManager: ConfigManager
    private lateinit var chatFormattingService: ChatFormattingService
    private lateinit var lamp: Lamp<*>

    override fun onEnable() {
        // Initialize configuration
        configManager = ConfigManager(dataFolder.toPath())
        if (!configManager.loadConfig()) {
            logger.severe("Failed to load configuration! Using defaults.")
        }

        // Initialize services
        chatFormattingService = ChatFormattingService(configManager)

        // Initialize command framework
        lamp = BukkitLamp.builder(this).build()
        
        // Register commands
        val commands = ChatPluginCommands(configManager, chatFormattingService)
        lamp.register(commands)
        lamp.register(ChatPluginCommands.FormatCommands(configManager))
        lamp.register(ChatPluginCommands.ToggleCommands(configManager))

        // Register event listeners
        server.pluginManager.registerEvents(ChatListener(configManager, chatFormattingService), this)

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
