package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class OrderBrowserMenu(
    private val menuAPI: MenuAPI,
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
) : bruh.zchat.utils.menuapi.PaginatedMenu<Order>() {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter = PlayerStateManager.getOrderFilter(player.uniqueId)
    private var currentSort = OrderSort.NEWEST
    private var currentPage = 0

    fun createMenu(page: Int = 0): Menu = createMenuOrNull(page)
        ?: error("OrderBrowserMenu cannot be created when order system is disabled")

    fun createMenuOrNull(page: Int = 0): Menu? {
        currentPage = page
        PlayerStateManager.setOrderFilter(player.uniqueId, currentFilter)

        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return null
        }

        val orders = runBlocking {
            orderService.getActiveOrders(currentFilter, currentSort, page, 28)
        }

        return this.apply {
            items.clear()
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = orders.items

            itemRenderer = { order, _ ->
                createOrderItem(order)
            }

            background = MenuUtils.backgroundItem()

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

            items[45] = createMyOrdersButton()
            items[46] = createFilterButton()
            items[47] = createSortButton()
            items[48] = createSearchButton()
            items[50] = createSellOrderButton()
            items[51] = createBuyOrderButton()
            items[49] = createBackButton()
        }
    }

    private fun createMyOrdersButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = translationAPI.getComponentSync(GuiMessages.MY_ORDERS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    MyOrdersMenu(
                        menuAPI,
                        auctionService,
                        orderService,
                        auctionRepository,
                        bidRepository,
                        orderRepository,
                        watchlistRepository,
                        config,
                        translationAPI,
                        plugin,
                        economy,
                        player
                    ).createMenu()
                )
            }
        }
    }

    private fun createSearchButton(): VItem {
        val hasActiveFilters = !currentFilter.searchQuery.isNullOrBlank() ||
            currentFilter.minPrice != null ||
            currentFilter.maxPrice != null

        return VItem(XMaterial.OAK_SIGN) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to search orders"))
            loreList.add(Component.empty())
            
            if (hasActiveFilters) {
                loreList.add(mm.deserialize("<yellow>Active filters:"))
                currentFilter.searchQuery?.let {
                    loreList.add(mm.deserialize("  <gray>• Search: <white>$it"))
                }
                currentFilter.minPrice?.let {
                    loreList.add(mm.deserialize("  <gray>• Min Price: <white>${MenuUtils.formatPrice(it, economy)}"))
                }
                currentFilter.maxPrice?.let {
                    loreList.add(mm.deserialize("  <gray>• Max Price: <white>${MenuUtils.formatPrice(it, economy)}"))
                }
            } else {
                loreList.add(mm.deserialize("<gray>Current: <white>No filters"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptText(
                        player,
                        "Search Orders",
                        currentFilter.searchQuery ?: ""
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            currentFilter = currentFilter.copy(searchQuery = result.value.ifBlank { null })
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                createMenuOrNull(currentPage)?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
            }
        }
    }

    private fun createBuyOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            lore = mutableListOf(mm.deserialize("<gray>Click to create a buy order"))
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).createMenu {
                        createMenu(currentPage)
                    }
                )
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
                if (player.inventory.itemInMainHand.type.isAir) {
                    player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_MUST_HOLD_ITEM))
                }
                ClickResult.SwitchMenu(
                    OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).createMenu {
                        createMenu(currentPage)
                    }
                )
            }
        }
    }

    private fun createOrderItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER
        val isOwnOrder = order.creatorUuid == player.uniqueId

        val isWatching = runBlocking {
            watchlistRepository.isWatchingOrder(player.uniqueId, order.id)
        }

        val loreList = mutableListOf<Component>()

        loreList.add(mm.deserialize("<gray>ID: <white>${order.shortId}"))
        loreList.add(Component.empty())

        loreList.add(translationAPI.getComponentSync(
            if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
        ))

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_QUANTITY) {
            unparsed("current", order.quantityFilled.toString())
            unparsed("total", order.quantityRequested.toString())
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PRICE) {
            unparsed("price", MenuUtils.formatPrice(order.pricePerUnit, plugin.economy))
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TOTAL) {
            unparsed("total", MenuUtils.formatPrice(order.totalValue(), plugin.economy))
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_REQUESTER) {
            unparsed("player", order.creatorName)
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(order.expiresAt))
        })

        if (order.allowPartial) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        loreList.add(Component.empty())
        if (isOwnOrder) {
            loreList.add(mm.deserialize("<yellow><bold>Your Order</bold></yellow>"))
            loreList.add(mm.deserialize("<gray>Click to manage or cancel"))
        } else {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_CLICK_FILL))
            loreList.add(if (isWatching) {
                mm.deserialize("<red>Right-click to remove from watchlist")
            } else {
                mm.deserialize("<yellow>Right-click to add to watchlist")
            })
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
            
            if (isOwnOrder || isWatching) {
                glow()
            }

            onClick { clickType, _ ->
                if (isOwnOrder) {
                    val menu = OrderManageMenu(menuAPI, orderService, config, translationAPI, plugin, player, order)
                    ClickResult.SwitchMenu(menu.createMenu { createMenu(currentPage) })
                } else {
                    if (clickType.isRightClick) {
                        runBlocking {
                            if (isWatching) {
                                watchlistRepository.removeOrder(player.uniqueId, order.id)
                                player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                            } else {
                                watchlistRepository.addOrder(player.uniqueId, order.id, order.orderType)
                                player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_ADDED))
                            }
                        }
                        createMenuOrNull(currentPage)?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
                    } else {
                        val menu = OrderFulfillMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player, order)
                        menu.createMenuOrNull()
                            ?.let { ClickResult.SwitchMenu(it) }
                            ?: ClickResult.Deny
                    }
                }
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
                currentFilter = when (currentFilter.orderType) {
                    null -> currentFilter.copy(orderType = OrderType.BUY_ORDER)
                    OrderType.BUY_ORDER -> currentFilter.copy(orderType = OrderType.SELL_ORDER)
                    OrderType.SELL_ORDER -> currentFilter.copy(orderType = null)
                }
                createMenuOrNull(currentPage)?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
            }
        }
    }

    private fun createSortButton(): VItem {
        val (material, displayName) = when (currentSort) {
            OrderSort.NEWEST -> XMaterial.ANVIL to "Newest First"
            OrderSort.PRICE_LOW -> XMaterial.GOLD_NUGGET to "Price: Low to High"
            OrderSort.PRICE_HIGH -> XMaterial.GOLD_BLOCK to "Price: High to Low"
            OrderSort.MOST_FILLED -> XMaterial.EXPERIENCE_BOTTLE to "Most Filled"
        }

        return VItem(material) {
            name = translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            lore = mutableListOf(
                mm.deserialize("<gray>Current: <white>$displayName"),
                Component.empty(),
                mm.deserialize("<green>Click to cycle")
            )
            hideAllFlags()

            onClick { _, _ ->
                currentSort = when (currentSort) {
                    OrderSort.NEWEST -> OrderSort.PRICE_LOW
                    OrderSort.PRICE_LOW -> OrderSort.PRICE_HIGH
                    OrderSort.PRICE_HIGH -> OrderSort.MOST_FILLED
                    OrderSort.MOST_FILLED -> OrderSort.NEWEST
                }
                createMenuOrNull(currentPage)?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
            }
        }
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    AuctionHouseMenu(
                        menuAPI,
                        auctionService,
                        orderService,
                        auctionRepository,
                        bidRepository,
                        orderRepository,
                        watchlistRepository,
                        config,
                        translationAPI,
                        plugin,
                        economy,
                        player
                    ).createMenu()
                )
            }
        }
    }
}
