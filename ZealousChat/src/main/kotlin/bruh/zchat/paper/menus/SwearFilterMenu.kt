package bruh.zchat.paper.menus

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import bruh.zchat.paper.swearfilter.InfractionManager
import bruh.zchat.utils.menuapi.ClickContext
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenu
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenuAPI
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenuInstance
import bruh.zchat.paper.PaperMC
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * Actions available in the swear filter management menu.
 */
enum class SwearFilterActions {
    CLOSE,
    VIEW_GROUP_DETAILS,
    VIEW_PLAYER_INFRACTIONS,
    CLEAR_PLAYER_INFRACTIONS,
    TOGGLE_FILTER,
    PREVIOUS_PAGE,
    NEXT_PAGE
}

/**
 * Configurable menu for managing swear filter settings and viewing infractions.
 */
class SwearFilterMenu(
    menuApi: ConfigurableMenuAPI,
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val infractionManager: InfractionManager
) : ConfigurableMenu<SwearFilterActions>(
    menuApi = menuApi,
    configName = "swearfilter",
    actionClass = SwearFilterActions::class
) {
    private val miniMessage = MiniMessage.miniMessage()
    
    // Track current view state per player
    private val playerViews = mutableMapOf<UUID, SwearFilterView>()
    private val playerPages = mutableMapOf<UUID, Int>()

    private enum class SwearFilterView {
        MAIN,
        GROUP_DETAILS,
        PLAYER_INFRACTIONS
    }

    override val actionHandlers: Map<SwearFilterActions, (ClickContext, ConfigurableMenuInstance<SwearFilterActions>) -> ClickResult> = mapOf(
        SwearFilterActions.CLOSE to { _: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            instance.close()
            ClickResult.CLOSE
        },
        SwearFilterActions.VIEW_GROUP_DETAILS to { ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            handleViewGroupDetails(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.VIEW_PLAYER_INFRACTIONS to { ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            playerViews[ctx.player.uniqueId] = SwearFilterView.PLAYER_INFRACTIONS
            playerPages[ctx.player.uniqueId] = 0
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.CLEAR_PLAYER_INFRACTIONS to { ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            handleClearInfractions(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.TOGGLE_FILTER to { ctx: ClickContext, _: ConfigurableMenuInstance<SwearFilterActions> ->
            ctx.player.sendMessage(miniMessage.deserialize(
                "<yellow>To toggle the swear filter, edit the config file and reload.</yellow>"
            ))
            ClickResult.DENY
        },
        SwearFilterActions.PREVIOUS_PAGE to { ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            val currentPage = playerPages[ctx.player.uniqueId] ?: 0
            if (currentPage > 0) {
                playerPages[ctx.player.uniqueId] = currentPage - 1
                refreshDisplay(instance)
            }
            ClickResult.DENY
        },
        SwearFilterActions.NEXT_PAGE to { ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions> ->
            val currentPage = playerPages[ctx.player.uniqueId] ?: 0
            playerPages[ctx.player.uniqueId] = currentPage + 1
            refreshDisplay(instance)
            ClickResult.DENY
        }
    )

    override val onClose: ((Player, ConfigurableMenuInstance<SwearFilterActions>) -> Unit) = { player, _ ->
        playerViews.remove(player.uniqueId)
        playerPages.remove(player.uniqueId)
    }

    /**
     * Opens the swear filter management menu for an admin.
     */
    fun openForAdmin(player: Player) {
        if (!player.hasPermission(configManager.config.gui.swearFilterGuiPermission)) {
            player.sendMessage(miniMessage.deserialize("<red>You don't have permission to access this menu.</red>"))
            return
        }
        
        playerViews[player.uniqueId] = SwearFilterView.MAIN
        playerPages[player.uniqueId] = 0
        val instance = open(player)
        refreshDisplay(instance)
    }

    private fun refreshDisplay(instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val player = instance.player
        val view = playerViews[player.uniqueId] ?: SwearFilterView.MAIN
        
        // Clear content area and any dynamic actions
        val contentSlots = (config.rows - 1) * 9
        for (slot in 0 until contentSlots) {
            instance.setItemAt(slot, null)
            instance.registerActionSlot(slot, null)
        }
        
        when (view) {
            SwearFilterView.MAIN -> displayMainView(instance)
            SwearFilterView.GROUP_DETAILS -> displayGroupDetailsView(instance)
            SwearFilterView.PLAYER_INFRACTIONS -> displayPlayerInfractionsView(instance)
        }
    }

    private fun displayMainView(instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val filterConfig = configManager.config.swearFilter
        
        // Status indicator at slot 4
        val statusItem = ItemStack(if (filterConfig.enabled) Material.LIME_DYE else Material.GRAY_DYE)
        statusItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize(
                if (filterConfig.enabled) "<green><bold>Swear Filter: ENABLED</bold></green>"
                else "<red><bold>Swear Filter: DISABLED</bold></red>"
            ))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Filter groups: ${filterConfig.filterGroups.size}</gray>"),
                miniMessage.deserialize("<gray>Blocked message: ${if (filterConfig.enableBlockedMessage) "Yes" else "No"}</gray>")
            ))
        }
        instance.setItemAt(4, statusItem)

        // Display filter groups starting at slot 10
        filterConfig.filterGroups.forEachIndexed { index, group ->
            if (index >= 21) return@forEachIndexed // Max 21 groups displayed
            
            val groupItem = createGroupItem(group)
            instance.setItemAt(10 + index + (index / 7) * 2, groupItem)
        }

        // View player infractions button at slot 40
        val infractionsItem = ItemStack(Material.BOOK)
        infractionsItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow><bold>View Player Infractions</bold></yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to view online players' infractions</gray>")
            ))
        }
        instance.setItemAt(40, infractionsItem)
    }

    private fun createGroupItem(group: FilterGroup): ItemStack {
        val material = when (group.type.lowercase()) {
            "regex" -> Material.COMPARATOR
            "smart", "mixed", "auto" -> Material.REDSTONE
            "levenshtein" -> Material.REPEATER
            "dice-sorensen", "dice" -> Material.HOPPER
            else -> Material.BARRIER
        }
        
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>${group.name}</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Type: <white>${group.type}</white></gray>"),
                miniMessage.deserialize("<gray>Distance: <white>${group.distance}</white></gray>"),
                miniMessage.deserialize("<gray>Filters: <white>${group.filters.size}</white></gray>"),
                miniMessage.deserialize("<gray>Punishments: <white>${group.punishments.size}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to view details</yellow>")
            ))
        }
        return item
    }

    private fun displayGroupDetailsView(instance: ConfigurableMenuInstance<SwearFilterActions>) {
        // Display a back button
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back to Main</gray>"))
        }
        instance.setItemAt(0, backItem)
        
        // Show info that editing requires config changes
        val infoItem = ItemStack(Material.OAK_SIGN)
        infoItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Filter Group Details</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>To edit filter groups,</gray>"),
                miniMessage.deserialize("<gray>modify config.conf and reload.</gray>")
            ))
        }
        instance.setItemAt(4, infoItem)
    }

    private fun displayPlayerInfractionsView(instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val currentPage = playerPages[instance.player.uniqueId] ?: 0
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        val itemsPerPage = (config.rows - 1) * 9 - 2 // Reserve slots for navigation
        
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, onlinePlayers.size)
        val pageItems = if (startIndex < onlinePlayers.size) {
            onlinePlayers.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Back button at slot 0
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back to Main</gray>"))
        }
        instance.setItemAt(0, backItem)

        // Display player heads with infractions and register clear action
        pageItems.forEachIndexed { index, onlinePlayer ->
            plugin.launch(Dispatchers.IO) {
                val infractions = infractionManager.getPlayerInfractions(onlinePlayer.uniqueId)
                
                plugin.launch(plugin.globalRegionDispatcher) {
                    val playerItem = ItemStack(Material.PLAYER_HEAD)
                    playerItem.editMeta { meta ->
                        if (meta is org.bukkit.inventory.meta.SkullMeta) {
                            meta.setOwningPlayer(onlinePlayer)
                        }
                        meta.displayName(miniMessage.deserialize("<yellow>${onlinePlayer.name}</yellow>"))
                        
                        val loreLines = mutableListOf<net.kyori.adventure.text.Component>()
                        if (infractions.isEmpty()) {
                            loreLines.add(miniMessage.deserialize("<green>No infractions</green>"))
                        } else {
                            loreLines.add(miniMessage.deserialize("<red>Infractions:</red>"))
                            infractions.forEach { (group, count) ->
                                loreLines.add(miniMessage.deserialize("<gray>- $group: <white>$count</white></gray>"))
                            }
                        }
                        loreLines.add(miniMessage.deserialize(""))
                        loreLines.add(miniMessage.deserialize("<yellow>Click to clear infractions</yellow>"))
                        meta.lore(loreLines)
                    }
                    val slot = 9 + index
                    instance.setItemAt(slot, playerItem)
                    instance.registerActionSlot(slot, SwearFilterActions.CLEAR_PLAYER_INFRACTIONS)
                }
            }
        }
    }

    private fun handleViewGroupDetails(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        playerViews[ctx.player.uniqueId] = SwearFilterView.GROUP_DETAILS
        refreshDisplay(instance)
    }

    private fun handleClearInfractions(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val slot = ctx.slot
        if (slot < 9) return // Navigation row
        
        val currentPage = playerPages[ctx.player.uniqueId] ?: 0
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        val itemsPerPage = (config.rows - 1) * 9 - 2
        
        val index = currentPage * itemsPerPage + (slot - 9)
        if (index >= onlinePlayers.size) return
        
        val targetPlayer = onlinePlayers[index]
        
        plugin.launch(Dispatchers.IO) {
            val success = infractionManager.resetAllInfractions(targetPlayer.uniqueId)
            
            plugin.launch(plugin.globalRegionDispatcher) {
                if (success) {
                    ctx.player.sendMessage(miniMessage.deserialize(
                        "<green>Cleared all infractions for <yellow>${targetPlayer.name}</yellow></green>"
                    ))
                    refreshDisplay(instance)
                } else {
                    ctx.player.sendMessage(miniMessage.deserialize(
                        "<red>Failed to clear infractions for ${targetPlayer.name}</red>"
                    ))
                }
            }
        }
    }
}
