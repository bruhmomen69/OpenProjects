package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
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
 * Menu for browsing buy/sell orders.
 */
class OrderBrowserMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter = OrderFilter()
    private var currentSort = OrderSort.NEWEST
    private var currentPage = 0

    fun open(page: Int = 0) {
        currentPage = page

        if (!config.orders.enabled) {
            player.sendMessage(mm.deserialize("<red>The order system is currently disabled."))
            return
        }

        // Load orders
        val orders = runBlocking {
            orderService.getActiveOrders(currentFilter, currentSort, page, 28)
        }

        val menu = menuAPI.paginated<Order> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = orders.items

            itemRenderer = { order, _ ->
                createOrderItem(order)
            }

            // Background
            background = MenuUtils.backgroundItem()

            // Navigation
            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }
            pageIndicatorRenderer = { current, total ->
                VItem(XMaterial.PAPER) {
                    name = Component.text("Page $current/$total")
                }
            }

            // Static control items
            staticItems[46] = createFilterButton()
            staticItems[47] = createSortButton()
            staticItems[48] = createSellOrderButton()
            staticItems[50] = createBuyOrderButton()
            staticItems[49] = createBackButton()
        }

        menuAPI.open(menu, player)
    }

    private fun createBuyOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            lore = mutableListOf(mm.deserialize("<gray>Click to create a buy order"))
            hideAllFlags()

            onClick { _, _ ->
                OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).open {
                    open(currentPage)
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createSellOrderButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = mm.deserialize("<yellow>Create Sell Order")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to create a sell order"))
            loreList.add(mm.deserialize("<red>You must hold an item to sell!"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                // Check if player is holding an item
                if (player.inventory.itemInMainHand.type.isAir) {
                    player.sendMessage(mm.deserialize("<red>You must hold an item to create a sell order!"))
                    return@onClick
                }
                // Open sell order creation - reuse OrderCreateMenu but with sell mode
                // For now, we'll open the create menu and let them select
                OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).open {
                    open(currentPage)
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createOrderItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()

        // Order type
        loreList.add(translationAPI.getComponentSync(
            if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
        ))

        // Quantity
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_QUANTITY) {
            unparsed("current", order.quantityFilled.toString())
            unparsed("total", order.quantityRequested.toString())
        })

        // Price
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PRICE) {
            unparsed("price", MenuUtils.formatPrice(order.pricePerUnit, plugin.economy))
        })

        // Total value
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TOTAL) {
            unparsed("total", MenuUtils.formatPrice(order.totalValue(), plugin.economy))
        })

        // Requester
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_REQUESTER) {
            unparsed("player", order.creatorName)
        })

        // Time left
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(order.expiresAt))
        })

        // Partial fills
        if (order.allowPartial) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        // Click instruction
        loreList.add(Component.empty())
        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_CLICK_FILL))

        return VItem(material) {
            name = order.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                val menu = OrderFulfillMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, config, translationAPI, plugin, economy, player, order)
                val opened = menu.open()
                if (opened) ClickResult.CLOSE else ClickResult.ALLOW
            }
        }
    }

    private fun createFilterButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = when (currentFilter.orderType) {
                null -> mm.deserialize("<yellow>Filter: <white>All")
                OrderType.BUY_ORDER -> translationAPI.getComponentSync(GuiMessages.BUTTON_BUY_ORDERS)
                OrderType.SELL_ORDER -> translationAPI.getComponentSync(GuiMessages.BUTTON_SELL_ORDERS)
            }
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through filter options
                currentFilter = when (currentFilter.orderType) {
                    null -> currentFilter.copy(orderType = OrderType.BUY_ORDER)
                    OrderType.BUY_ORDER -> currentFilter.copy(orderType = OrderType.SELL_ORDER)
                    OrderType.SELL_ORDER -> currentFilter.copy(orderType = null)
                }
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
            }
        }
    }

    private fun createSortButton(): VItem {
        return VItem(XMaterial.COMPASS) {
            name = translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through sort options
                currentSort = when (currentSort) {
                    OrderSort.NEWEST -> OrderSort.PRICE_LOW
                    OrderSort.PRICE_LOW -> OrderSort.PRICE_HIGH
                    OrderSort.PRICE_HIGH -> OrderSort.MOST_FILLED
                    OrderSort.MOST_FILLED -> OrderSort.NEWEST
                }
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
            }
        }
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(translationAPI).apply {
            onClick { _, _ ->
                AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }
}
