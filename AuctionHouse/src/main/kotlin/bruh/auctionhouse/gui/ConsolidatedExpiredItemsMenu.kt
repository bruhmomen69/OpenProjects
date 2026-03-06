package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.OrderService
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Menu for retrieving expired/cancelled items from consolidated groups.
 * This replaces the original ExpiredItemsMenu with a consolidated view.
 */
class ConsolidatedExpiredItemsMenu(
    private val menuAPI: MenuAPI,
    private val consolidatedService: ConsolidatedExpiredItemService,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val orderRepository: OrderRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val consolidatedItems = runBlocking {
            consolidatedService.getPlayerConsolidatedItems(player.uniqueId)
        }

        if (consolidatedItems.isEmpty()) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.NO_CLAIMABLE_ITEMS))
            // Open the main auction house menu instead
            AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
            return
        }

        val menu = menuAPI.paginated<ConsolidatedExpiredItem> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.EXPIRED_ITEMS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = consolidatedItems

            itemRenderer = { consolidatedItem, _ ->
                createConsolidatedItemDisplay(consolidatedItem)
            }

            background = MenuUtils.backgroundItem()

            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            staticItems[49] = backItem

            // Claim All button
            staticItems[45] = createClaimAllButton(consolidatedItems)
        }

        menuAPI.open(menu, player)
    }

    private fun createClaimAllButton(items: List<ConsolidatedExpiredItem>): VItem {
        val totalItems = items.sumOf { it.remainingQuantity() }
        
        return VItem(XMaterial.CHEST) {
            name = mm.deserialize("<green>Claim All Items")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Total items: <white>$totalItems"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<yellow>Click to claim all items"))
            loreList.add(mm.deserialize("<gray>Items that don't fit will remain."))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    claimAllItems(items)
                }
                ClickResult.CLOSE
            }
        }
    }

    private suspend fun claimAllItems(items: List<ConsolidatedExpiredItem>) {
        if (items.isEmpty()) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.NO_CLAIMABLE_ITEMS))
            return
        }

        var totalClaimed = 0
        var totalRemaining = 0
        val itemsToRefresh = mutableListOf<ConsolidatedExpiredItem>()

        for (item in items) {
            if (item.remainingQuantity() <= 0) continue
            
            val result = consolidatedService.claimItems(player, item, item.remainingQuantity())
            if (result.success) {
                totalClaimed += result.claimedQuantity
                if (result.claimedQuantity < item.remainingQuantity()) {
                    totalRemaining += (item.remainingQuantity() - result.claimedQuantity)
                }
            } else {
                totalRemaining += item.remainingQuantity()
            }
            
            itemsToRefresh.add(item)
        }

        // Send summary message
        if (totalClaimed > 0 && totalRemaining > 0) {
            player.sendMessage(mm.deserialize("<green>Claimed <white>$totalClaimed <green>items. <yellow>$totalRemaining <yellow>items remaining (inventory full)."))
        } else if (totalClaimed > 0) {
            player.sendMessage(mm.deserialize("<green>Claimed <white>$totalClaimed <green>items."))
        } else if (totalRemaining > 0) {
            player.sendMessage(mm.deserialize("<red>Inventory full! <yellow>$totalRemaining <yellow>items remaining."))
        }

        // Refresh the menu
        open()
    }

    private fun createConsolidatedItemDisplay(item: ConsolidatedExpiredItem): VItem {
        val material = XMaterial.matchXMaterial(item.itemMaterial.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(mm.deserialize("<gray>Available: <green>${item.remainingQuantity()}"))
        loreList.add(mm.deserialize("<gray>Total: <white>${item.totalQuantity}"))
        loreList.add(mm.deserialize("<gray>Source: <white>${item.itemType}"))
        loreList.add(mm.deserialize("<gray>Reason: <white>${item.reason}"))
        loreList.add(Component.empty())
        loreList.add(mm.deserialize("<yellow>Left-click to claim items"))
        loreList.add(mm.deserialize("<gray>(Shift-click to claim max amount)"))

        return VItem(material) {
            name = item.itemDisplayName?.let { mm.deserialize(it) }
                ?: Component.text(item.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { clickType, _ ->
                runBlocking {
                    if (clickType.isShiftClick) {
                        // Try to claim max available
                        openClaimMenu(item, item.remainingQuantity())
                    } else {
                        // Open quantity selector with smart default
                        val defaultQuantity = minOf(64, item.remainingQuantity())
                        openClaimMenu(item, defaultQuantity)
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun openClaimMenu(item: ConsolidatedExpiredItem, initialQuantity: Int) {
        ClaimQuantityMenu(
            menuAPI = menuAPI,
            consolidatedService = consolidatedService,
            auctionService = auctionService,
            orderService = orderService,
            auctionRepository = auctionRepository,
            bidRepository = bidRepository,
            orderRepository = orderRepository,
            watchlistRepository = watchlistRepository,
            config = config,
            translationAPI = translationAPI,
            plugin = plugin,
            economy = economy,
            player = player,
            consolidatedItem = item,
            initialQuantity = initialQuantity
        ).open()
    }
}
