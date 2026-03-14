package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class MyOrdersMenu(
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

    fun createMenu(): Menu {
        val orders = runBlocking {
            orderService.getPlayerOrders(player.uniqueId, null)
        }

        return this.apply {
            items.clear()
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MY_ORDERS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = orders

            itemRenderer = { order, _ ->
                createMyOrderItem(order)
            }

            background = MenuUtils.backgroundItem()

            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }

            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    OrderBrowserMenu(
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
                    ).createMenuOrNull()?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
                }
            }
            items[49] = backItem
        }
    }

    private fun createMyOrderItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

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

        loreList.add(mm.deserialize("<gray>Status: ${getStatusColor(order.status)}${order.status}"))

        if (order.status == OrderStatus.PENDING || order.status == OrderStatus.PARTIAL) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
                unparsed("time", MenuUtils.formatTimeRemaining(order.expiresAt))
            })
            
            if (order.status == OrderStatus.PARTIAL) {
                loreList.add(mm.deserialize("<gray>Filled: <green>${order.quantityFilled}/${order.quantityRequested}"))
            }
            
            if (order.allowPartial) {
                loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
            } else {
                loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
            }
            
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<yellow>Click to manage"))
        } else if (order.status == OrderStatus.FILLED) {
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Order completed!"))
            loreList.add(mm.deserialize("<gray>Click to view details"))
        } else if (order.status == OrderStatus.EXPIRED) {
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<red>Order expired"))
            loreList.add(mm.deserialize("<gray>Click to view details"))
        } else if (order.status == OrderStatus.CANCELLED) {
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Order cancelled"))
            loreList.add(mm.deserialize("<gray>Click to view details"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                when (order.status) {
                    OrderStatus.PENDING, OrderStatus.PARTIAL -> {
                        val manageMenu = OrderManageMenu(menuAPI, orderService, config, translationAPI, plugin, player, order)
                        return@onClick ClickResult.SwitchMenu(manageMenu.createMenu { createMenu() })
                    }
                    OrderStatus.FILLED, OrderStatus.EXPIRED, OrderStatus.CANCELLED -> {
                        return@onClick ClickResult.SwitchMenu(createOrderDetailsMenu(order))
                    }
                }
                ClickResult.Close
            }
        }
    }

    private fun createOrderDetailsMenu(order: Order): Menu {
        return bruh.zchat.utils.menuapi.SimpleMenu().apply {
            rows = 5
            title = mm.deserialize("<yellow>Order Details - ${order.shortId}")

            background = MenuUtils.backgroundItem()

            item(13, createOrderDetailDisplayItem(order))

            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(createMenu())
                }
            }
            item(36, backItem)

            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.Close
                }
            }
            item(44, closeItem)
        }
    }

    private fun createOrderDetailDisplayItem(order: Order): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
        loreList.add(Component.empty())
        loreList.add(mm.deserialize("<gray>ID: <white>${order.shortId}"))
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

        loreList.add(mm.deserialize("<gray>Status: ${getStatusColor(order.status)}${order.status}"))

        if (order.status == OrderStatus.FILLED) {
            loreList.add(mm.deserialize("<gray>Completed at: <white>${order.filledAt}"))
        } else if (order.status == OrderStatus.EXPIRED) {
            loreList.add(mm.deserialize("<gray>Expired at: <white>${order.expiresAt}"))
        } else if (order.status == OrderStatus.CANCELLED) {
            loreList.add(mm.deserialize("<gray>Order was cancelled"))
        }

        if (order.allowPartial) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        order.minFillQuantity?.let { min ->
            loreList.add(mm.deserialize("<gray>Min fill: <white>$min"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let { mm.deserialize(it) }
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
