package bruh.zchat.paper.menus

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.PlayerDataManager
import bruh.zchat.paper.services.BlockService
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.swearfilter.InfractionManager
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenuAPI
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * Service that manages configurable GUI menus for ZealousChat.
 * Uses the ConfigurableMenuAPI to load menus from HOCON configuration files.
 */
class MenuService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val blockService: BlockService,
    private val playerDataManager: PlayerDataManager,
    private val messageFormattingService: MessageFormattingService,
    private val infractionManager: InfractionManager
) : Closeable {
    private val logger = LoggerFactory.getLogger(MenuService::class.java)
    
    private var menuApi: MenuAPI? = null
    private var configurableMenuApi: ConfigurableMenuAPI? = null
    
    private var blockListMenu: BlockListMenu? = null
    private var swearFilterMenu: SwearFilterMenu? = null

    /**
     * Initializes the menu system if GUIs are enabled in config.
     */
    fun initialize() {
        val guiConfig = configManager.config.gui
        
        if (!guiConfig.enabled) {
            logger.info("GUI menus are disabled in config")
            return
        }
        
        menuApi = MenuAPI(plugin)
        configurableMenuApi = menuApi!!.configurable()
        
        // Register block list menu if enabled
        if (guiConfig.enableBlockListGui && configManager.config.blocks.enableBlockSystem) {
            blockListMenu = BlockListMenu(
                menuApi = configurableMenuApi!!,
                plugin = plugin,
                blockService = blockService,
                playerDataManager = playerDataManager,
                messageFormattingService = messageFormattingService,
                configManager = configManager
            )
            configurableMenuApi!!.register(blockListMenu!!)
            logger.info("Block list GUI menu registered")
        }
        
        // Register swear filter menu if enabled
        if (guiConfig.enableSwearFilterGui) {
            swearFilterMenu = SwearFilterMenu(
                menuApi = configurableMenuApi!!,
                plugin = plugin,
                configManager = configManager,
                infractionManager = infractionManager
            )
            configurableMenuApi!!.register(swearFilterMenu!!)
            logger.info("Swear filter GUI menu registered")
        }
    }

    /**
     * Opens the block list GUI for a player.
     * Returns true if GUI was opened, false if GUI is disabled.
     */
    fun openBlockListGui(player: Player): Boolean {
        val menu = blockListMenu ?: return false
        menu.openForPlayer(player)
        return true
    }

    /**
     * Opens the swear filter management GUI for an admin.
     * Returns true if GUI was opened, false if GUI is disabled.
     */
    fun openSwearFilterGui(player: Player): Boolean {
        val menu = swearFilterMenu ?: return false
        menu.openForAdmin(player)
        return true
    }

    /**
     * Checks if block list GUI is available.
     */
    fun isBlockListGuiEnabled(): Boolean = blockListMenu != null

    /**
     * Checks if swear filter GUI is available.
     */
    fun isSwearFilterGuiEnabled(): Boolean = swearFilterMenu != null

    /**
     * Reloads all menu configurations.
     */
    fun reloadMenus() {
        configurableMenuApi?.reloadAll()
    }

    override fun close() {
        menuApi?.close()
        menuApi = null
        configurableMenuApi = null
        blockListMenu = null
        swearFilterMenu = null
    }
}
