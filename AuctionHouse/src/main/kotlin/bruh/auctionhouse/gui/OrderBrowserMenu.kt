package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

class OrderBrowserMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<Order>() {
    private var currentFilter by menuState(PlayerStateManager.getOrderFilter(pctx.player.uniqueId))
    private var currentSort by menuState(OrderSort.NEWEST)

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
        background = MenuUtils.backgroundItem()

        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
        loadingPlaceholder = MenuUtils.loadingOrderItem()
        emptyPlaceholder = MenuUtils.emptyOrdersItem()

        itemRenderer = { order, _ -> createOrderItem(order) }

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }
        pageIndicatorRenderer = { current, total ->
            VItem(XMaterial.PAPER) {
                name = Component.text("Page $current/$total")
            }
        }

        asyncData<List<Order>> {
            load {
                PlayerStateManager.setOrderFilter(pctx.player.uniqueId, currentFilter)
                pctx.orderService.getActiveOrders(currentFilter, currentSort, 0, Int.MAX_VALUE).items
            }
            onLoaded { orders -> dataSource = orders }
        }
    }

    override fun populateItems() {
        items.clear()

        items[45] = createMyOrdersButton()
        items[46] = createFilterButton()
        items[47] = createSortButton()
        items[48] = createSearchButton()
        items[49] = createBackButton()
        items[50] = createSellOrderButton()
        items[51] = createBuyOrderButton()
    }

    private fun createMyOrdersButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.MY_ORDERS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(MyOrdersMenu(pctx))
            }
        }
    }

    private fun createSearchButton(): VItem {
        val hasActiveFilters = !currentFilter.searchQuery.isNullOrBlank() ||
            currentFilter.minPrice != null ||
            currentFilter.maxPrice != null

        return VItem(XMaterial.OAK_SIGN) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Click to search orders"))
            loreList.add(Component.empty())
            
            if (hasActiveFilters) {
                loreList.add(pctx.mm.deserialize("<yellow>Active filters:"))
                currentFilter.searchQuery?.let {
                    loreList.add(pctx.mm.deserialize("  <gray>• Search: <white>$it"))
                }
                currentFilter.minPrice?.let {
                    loreList.add(pctx.mm.deserialize("  <gray>• Min Price: <white>${MenuUtils.formatPrice(it, pctx.economy)}"))
                }
                currentFilter.maxPrice?.let {
                    loreList.add(pctx.mm.deserialize("  <gray>• Max Price: <white>${MenuUtils.formatPrice(it, pctx.economy)}"))
                }
            } else {
                loreList.add(pctx.mm.deserialize("<gray>Current: <white>No filters"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(
                    pctx.player,
                    "Search Orders",
                    currentFilter.searchQuery ?: ""
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            currentFilter = currentFilter.copy(searchQuery = result.value.ifBlank { null })
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@OrderBrowserMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createBuyOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to create a buy order"))
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderCreateMenu(pctx))
            }
        }
    }

    private fun createSellOrderButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = pctx.mm.deserialize("<yellow>Create Sell Order")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Click to create a sell order"))
            loreList.add(pctx.mm.deserialize("<red>You must hold an item to sell!"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (pctx.player.inventory.itemInMainHand.type.isAir) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_MUST_HOLD_ITEM))
                }
                ClickResult.SwitchMenu(
                    OrderCreateMenu(pctx, bruh.auctionhouse.model.OrderType.SELL_ORDER)
                )
            }
        }
    }

    private fun createOrderItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER
        val isOwnOrder = order.creatorUuid == pctx.player.uniqueId

        val loreList = mutableListOf<Component>()

        loreList.add(pctx.mm.deserialize("<gray>ID: <white>${order.shortId}"))
        loreList.add(Component.empty())

        loreList.add(pctx.translationAPI.getComponentSync(
            if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
        ))

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_QUANTITY) {
            unparsed("current", order.quantityFilled.toString())
            unparsed("total", order.quantityRequested.toString())
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PRICE) {
            unparsed("price", MenuUtils.formatPrice(order.pricePerUnit, pctx.economy))
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TOTAL) {
            unparsed("total", MenuUtils.formatPrice(order.totalValue(), pctx.economy))
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_REQUESTER) {
            unparsed("player", order.creatorName)
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(order.expiresAt))
        })

        if (order.allowPartial) {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        loreList.add(Component.empty())
        if (isOwnOrder) {
            loreList.add(pctx.mm.deserialize("<yellow><bold>Your Order</bold></yellow>"))
            loreList.add(pctx.mm.deserialize("<gray>Click to manage or cancel"))
        } else {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_CLICK_FILL))
            loreList.add(pctx.mm.deserialize("<yellow>Right-click to add to watchlist"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
            
            if (isOwnOrder) {
                glow()
            }

            onClick { clickType, controls ->
                if (isOwnOrder) {
                    ClickResult.SwitchMenu(
                        OrderManageMenu(pctx, order) { this@OrderBrowserMenu }
                    )
                } else {
                    if (clickType.isRightClick) {
                        controls.runAsync(
                            action = {
                                val isWatching = pctx.watchlistRepository.isWatchingOrder(pctx.player.uniqueId, order.id)
                                if (isWatching) {
                                    pctx.watchlistRepository.removeOrder(pctx.player.uniqueId, order.id)
                                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                                } else {
                                    pctx.watchlistRepository.addOrder(pctx.player.uniqueId, order.id, order.orderType)
                                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_ADDED))
                                }
                            },
                            onSuccess = { controls.reloadData() }
                        )
                        ClickResult.Deny
                    } else {
                        val menu = OrderFulfillMenu(pctx, order)
                        if (menu.canOpen) {
                            ClickResult.SwitchMenu(menu)
                        } else {
                            ClickResult.Deny
                        }
                    }
                }
            }
        }
    }

    private fun createFilterButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = when (currentFilter.orderType) {
                null -> pctx.mm.deserialize("<yellow>Filter: <white>All")
                OrderType.BUY_ORDER -> pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_BUY_ORDERS)
                OrderType.SELL_ORDER -> pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_SELL_ORDERS)
            }
            hideAllFlags()

            onClick { _, controls ->
                currentFilter = when (currentFilter.orderType) {
                    null -> currentFilter.copy(orderType = OrderType.BUY_ORDER)
                    OrderType.BUY_ORDER -> currentFilter.copy(orderType = OrderType.SELL_ORDER)
                    OrderType.SELL_ORDER -> currentFilter.copy(orderType = null)
                }
                controls.reloadData()
                ClickResult.Deny
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
            name = pctx.translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Current: <white>$displayName"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to cycle")
            )
            hideAllFlags()

            onClick { _, controls ->
                currentSort = when (currentSort) {
                    OrderSort.NEWEST -> OrderSort.PRICE_LOW
                    OrderSort.PRICE_LOW -> OrderSort.PRICE_HIGH
                    OrderSort.PRICE_HIGH -> OrderSort.MOST_FILLED
                    OrderSort.MOST_FILLED -> OrderSort.NEWEST
                }
                controls.reloadData()
                ClickResult.Deny
            }
        }
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        }
    }
}
