package bruh.zchat.paper.menus

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.PlayerDataManager
import bruh.zchat.paper.services.BlockService
import bruh.zchat.paper.services.MessageFormattingService
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
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * Actions available in the block list menu.
 */
enum class BlockListActions {
    CLOSE,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    UNBLOCK_PLAYER
}

/**
 * Configurable menu for managing a player's block list.
 * Shows blocked players in a paginated list with unblock functionality.
 */
class BlockListMenu(
    menuApi: ConfigurableMenuAPI,
    private val plugin: PaperMC,
    private val blockService: BlockService,
    private val playerDataManager: PlayerDataManager,
    private val messageFormattingService: MessageFormattingService,
    private val configManager: ConfigManager
) : ConfigurableMenu<BlockListActions>(
    menuApi = menuApi,
    configName = "blocklist",
    actionClass = BlockListActions::class
) {
    private val miniMessage = MiniMessage.miniMessage()
    
    // Track current page per player
    private val playerPages = mutableMapOf<UUID, Int>()
    private val playerBlockedLists = mutableMapOf<UUID, List<UUID>>()

    override val actionHandlers: Map<BlockListActions, (ClickContext, ConfigurableMenuInstance<BlockListActions>) -> ClickResult> = mapOf(
        BlockListActions.CLOSE to { _: ClickContext, instance: ConfigurableMenuInstance<BlockListActions> ->
            instance.close()
            ClickResult.CLOSE
        },
        BlockListActions.PREVIOUS_PAGE to { ctx: ClickContext, instance: ConfigurableMenuInstance<BlockListActions> ->
            val currentPage = playerPages[ctx.player.uniqueId] ?: 0
            if (currentPage > 0) {
                playerPages[ctx.player.uniqueId] = currentPage - 1
                refreshBlockListDisplay(instance)
            }
            ClickResult.DENY
        },
        BlockListActions.NEXT_PAGE to { ctx: ClickContext, instance: ConfigurableMenuInstance<BlockListActions> ->
            val currentPage = playerPages[ctx.player.uniqueId] ?: 0
            val blockedList = playerBlockedLists[ctx.player.uniqueId] ?: emptyList()
            val maxPage = (blockedList.size - 1) / getItemsPerPage()
            if (currentPage < maxPage) {
                playerPages[ctx.player.uniqueId] = currentPage + 1
                refreshBlockListDisplay(instance)
            }
            ClickResult.DENY
        },
        BlockListActions.UNBLOCK_PLAYER to { ctx: ClickContext, instance: ConfigurableMenuInstance<BlockListActions> ->
            handleUnblock(ctx, instance)
            ClickResult.DENY
        }
    )

    override val onClose: ((Player, ConfigurableMenuInstance<BlockListActions>) -> Unit) = { player, _ ->
        playerPages.remove(player.uniqueId)
        playerBlockedLists.remove(player.uniqueId)
    }

    /**
     * Opens the block list menu for a player.
     */
    fun openForPlayer(player: Player) {
        plugin.launch(Dispatchers.IO) {
            val blockedPlayers = blockService.getBlockedPlayers(player.uniqueId).toList()
            playerBlockedLists[player.uniqueId] = blockedPlayers
            playerPages[player.uniqueId] = 0
            
            plugin.launch(plugin.globalRegionDispatcher) {
                val instance = open(player)
                refreshBlockListDisplay(instance)
            }
        }
    }

    private fun getItemsPerPage(): Int {
        // Calculate based on menu size minus navigation slots
        return (config.rows * 9) - 9  // Reserve bottom row for navigation
    }

    private fun refreshBlockListDisplay(instance: ConfigurableMenuInstance<BlockListActions>) {
        val player = instance.player
        val blockedList = playerBlockedLists[player.uniqueId] ?: return
        val currentPage = playerPages[player.uniqueId] ?: 0
        val itemsPerPage = getItemsPerPage()
        
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, blockedList.size)
        val pageItems = blockedList.subList(startIndex, endIndex)

        // Clear content slots (not navigation) and any dynamic actions
        for (slot in 0 until itemsPerPage) {
            instance.setItemAt(slot, null)
            instance.registerActionSlot(slot, null)
        }

        // Fill with blocked player heads and register unblock action
        pageItems.forEachIndexed { index, blockedUuid ->
            val offlinePlayer = Bukkit.getOfflinePlayer(blockedUuid)
            val playerName = offlinePlayer.name ?: "Unknown"
            
            val item = org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD)
            item.editMeta { meta ->
                if (meta is org.bukkit.inventory.meta.SkullMeta) {
                    meta.setOwningPlayer(offlinePlayer)
                }
                meta.displayName(miniMessage.deserialize("<red>$playerName</red>"))
                meta.lore(listOf(
                    miniMessage.deserialize("<gray>Click to unblock</gray>"),
                    miniMessage.deserialize("<dark_gray>UUID: ${blockedUuid}</dark_gray>")
                ))
            }
            instance.setItemAt(index, item)
            instance.registerActionSlot(index, BlockListActions.UNBLOCK_PLAYER)
        }
    }

    private fun handleUnblock(ctx: ClickContext, instance: ConfigurableMenuInstance<BlockListActions>) {
        val slot = ctx.slot
        val itemsPerPage = getItemsPerPage()
        
        // Only handle clicks in the content area
        if (slot >= itemsPerPage) return
        
        val currentPage = playerPages[ctx.player.uniqueId] ?: 0
        val blockedList = playerBlockedLists[ctx.player.uniqueId] ?: return
        
        val index = currentPage * itemsPerPage + slot
        if (index >= blockedList.size) return
        
        val blockedUuid = blockedList[index]
        
        plugin.launch(Dispatchers.IO) {
            val success = blockService.unblockPlayer(ctx.player.uniqueId, blockedUuid)
            
            if (success) {
                // Update local list
                val updatedList = blockedList.toMutableList()
                updatedList.removeAt(index)
                playerBlockedLists[ctx.player.uniqueId] = updatedList
                
                val blockedName = Bukkit.getOfflinePlayer(blockedUuid).name ?: "Unknown"
                plugin.launch(plugin.globalRegionDispatcher) {
                    ctx.player.sendMessage(miniMessage.deserialize("<green>Unblocked <yellow>$blockedName</yellow></green>"))
                    refreshBlockListDisplay(instance)
                }
            }
        }
    }
}
