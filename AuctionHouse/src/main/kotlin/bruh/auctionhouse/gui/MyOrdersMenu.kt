package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

class MyOrdersMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<Order>() {

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.MY_ORDERS_TITLE)
        background = MenuUtils.backgroundItem()

        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        itemRenderer = { order, _ -> createMyOrderItem(order) }

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }

        asyncData<List<Order>> {
            load { pctx.orderService.getPlayerOrders(pctx.player.uniqueId, null) }
            onLoaded { orders -> dataSource = orders }
        }
    }

    override fun populateItems() {
        items.clear()

        items[49] = MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderBrowserMenu(pctx))
            }
        }
    }

    private fun createMyOrderItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

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

        loreList.add(pctx.mm.deserialize("<gray>Status: ${getStatusColor(order.status)}${order.status}"))

        if (order.status == OrderStatus.PENDING || order.status == OrderStatus.PARTIAL) {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
                unparsed("time", MenuUtils.formatTimeRemaining(order.expiresAt))
            })
            
            if (order.status == OrderStatus.PARTIAL) {
                loreList.add(pctx.mm.deserialize("<gray>Filled: <green>${order.quantityFilled}/${order.quantityRequested}"))
            }
            
            if (order.allowPartial) {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
            } else {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
            }
            
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<yellow>Click to manage"))
        } else if (order.status == OrderStatus.FILLED) {
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Order completed!"))
            loreList.add(pctx.mm.deserialize("<gray>Click to view details"))
        } else if (order.status == OrderStatus.EXPIRED) {
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<red>Order expired"))
            loreList.add(pctx.mm.deserialize("<gray>Click to view details"))
        } else if (order.status == OrderStatus.CANCELLED) {
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Order cancelled"))
            loreList.add(pctx.mm.deserialize("<gray>Click to view details"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                when (order.status) {
                    OrderStatus.PENDING, OrderStatus.PARTIAL -> {
                        val manageMenu = OrderManageMenu(pctx, order) { this@MyOrdersMenu }
                        return@onClick ClickResult.SwitchMenu(manageMenu)
                    }
                    OrderStatus.FILLED, OrderStatus.EXPIRED, OrderStatus.CANCELLED -> {
                        return@onClick ClickResult.SwitchMenu(createOrderDetailsMenu(order))
                    }
                }
                ClickResult.Close
            }
        }
    }

    private fun createOrderDetailsMenu(order: Order): SimpleMenu {
        return SimpleMenu().apply {
            rows = 5
            title = pctx.mm.deserialize("<yellow>Order Details - ${order.shortId}")

            background = MenuUtils.backgroundItem()

            item(13, createOrderDetailDisplayItem(order))

            item(36, MenuUtils.backButton(pctx.translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(this@MyOrdersMenu)
                }
            })

            item(44, MenuUtils.closeButton(pctx.translationAPI).apply {
                onClick { _, _ -> ClickResult.Close }
            })
        }
    }

    private fun createOrderDetailDisplayItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
        loreList.add(Component.empty())
        loreList.add(pctx.mm.deserialize("<gray>ID: <white>${order.shortId}"))
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

        loreList.add(pctx.mm.deserialize("<gray>Status: ${getStatusColor(order.status)}${order.status}"))

        if (order.status == OrderStatus.FILLED) {
            loreList.add(pctx.mm.deserialize("<gray>Completed at: <white>${order.filledAt}"))
        } else if (order.status == OrderStatus.EXPIRED) {
            loreList.add(pctx.mm.deserialize("<gray>Expired at: <white>${order.expiresAt}"))
        } else if (order.status == OrderStatus.CANCELLED) {
            loreList.add(pctx.mm.deserialize("<gray>Order was cancelled"))
        }

        if (order.allowPartial) {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        order.minFillQuantity?.let { min ->
            loreList.add(pctx.mm.deserialize("<gray>Min fill: <white>$min"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let { pctx.mm.deserialize(it) }
                ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
        }
    }

    private fun getStatusColor(status: OrderStatus): String {
        return when (status) {
            OrderStatus.PENDING -> "<yellow>"
            OrderStatus.PARTIAL -> "<gold>"
            OrderStatus.FILLED -> "<green>"
            OrderStatus.EXPIRED -> "<red>"
            OrderStatus.CANCELLED -> "<gray>"
        }
    }
}
