package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.GuiMessages
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
            player.sendMessage(mm.deserialize("<gray>You have no expired items to claim."))
            // Open the main auction house menu instead
            AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
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
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            staticItems[49] = backItem
        }

        menuAPI.open(menu, player)
    }

    private fun createConsolidatedItemDisplay(item: ConsolidatedExpiredItem): VItem {
        val material = XMaterial.matchXMaterial(item.itemMaterial.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(mm.deserialize("<gray>Available: <green>${item.remainingQuantity()}"))
        loreList.add(mm.deserialize("<gray>Total: <white>${item.totalQuantity}"))
        loreList.add(mm.deserialize("<gray>Type: <white>${item.itemType}"))
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
