package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class OrderManageMenu(
    private val menuAPI: MenuAPI,
    private val orderService: OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player,
    private val order: Order
) {
    private val mm = MiniMessage.miniMessage()
    private var currentOrder = order

    fun open(onCloseCallback: () -> Unit) {
        val canEditPrice = currentOrder.isActive() && currentOrder.quantityFilled == 0

        val menu = menuAPI.simple {
            rows = if (canEditPrice) 5 else 4
            title = mm.deserialize("<yellow>Order ${currentOrder.shortId}")

            background = MenuUtils.backgroundItem()

            item(13, createOrderDisplayItem())
            
            if (canEditPrice) {
                item(29, createEditPriceButton(onCloseCallback))
            }
            
            item(if (canEditPrice) 31 else 29, createCancelButton(onCloseCallback))
            item(if (canEditPrice) 33 else 33, createBackButton(onCloseCallback))
        }

        menuAPI.open(menu, player)
    }

    private fun createOrderDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(currentOrder.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = currentOrder.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
        loreList.add(Component.empty())
        loreList.add(mm.deserialize("<gray>ID: <white>${currentOrder.shortId}"))
        loreList.add(Component.empty())

        loreList.add(translationAPI.getComponentSync(
            if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
        ))

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_QUANTITY) {
            unparsed("current", currentOrder.quantityFilled.toString())
            unparsed("total", currentOrder.quantityRequested.toString())
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PRICE) {
            unparsed("price", MenuUtils.formatPrice(currentOrder.pricePerUnit, plugin.economy))
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TOTAL) {
            unparsed("total", MenuUtils.formatPrice(currentOrder.totalValue(), plugin.economy))
        })

        loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(currentOrder.expiresAt))
        })

        if (currentOrder.allowPartial) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        return VItem(material) {
            name = currentOrder.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(currentOrder.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
            glow()
        }
    }

    private fun createEditPriceButton(onCloseCallback: () -> Unit): VItem {
        return VItem(XMaterial.ANVIL) {
            name = mm.deserialize("<yellow>Edit Price")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Current: <gold>${MenuUtils.formatPrice(currentOrder.pricePerUnit, plugin.economy)} per unit"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to edit price"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptDouble(
                        player,
                        "New Price Per Unit",
                        currentOrder.pricePerUnit,
                        config.orders.minPricePerUnit,
                        config.orders.maxPricePerUnit
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val editResult = orderService.editOrderPrice(player, currentOrder.id, result.value)
                            when (editResult) {
                                is ServiceResult.Success -> {
                                    currentOrder = editResult.data
                                    player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_PRICE_UPDATED) {
                                        unparsed("price", MenuUtils.formatPrice(result.value, plugin.economy))
                                    })
                                    open(onCloseCallback)
                                }
                                is ServiceResult.Failure -> {
                                    player.sendMessage(editResult.message)
                                }
                            }
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createCancelButton(onCloseCallback: () -> Unit): VItem {
        return VItem(XMaterial.BARRIER) {
            name = mm.deserialize("<red>Cancel Order")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Cancel this order and"))
            if (currentOrder.orderType == OrderType.BUY_ORDER) {
                loreList.add(mm.deserialize("<gray>receive a refund for unfilled items"))
                val remainingValue = currentOrder.remainingValue()
                if (remainingValue > 0) {
                    loreList.add(Component.empty())
                    loreList.add(mm.deserialize("<gray>Refund: <gold>${MenuUtils.formatPrice(remainingValue, plugin.economy)}"))
                }
            } else {
                loreList.add(mm.deserialize("<gray>get your items back"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<yellow>Click to cancel"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = orderService.cancelOrder(player, currentOrder.id)
                    when (result) {
                        is ServiceResult.Success -> {
                            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_CANCELLED))
                        }
                        is ServiceResult.Failure -> {
                            player.sendMessage(result.message)
                        }
                    }
                }
                onCloseCallback()
                ClickResult.CLOSE
            }
        }
    }

    private fun createBackButton(onCloseCallback: () -> Unit): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = translationAPI.getComponentSync(GuiMessages.BACK)
            hideAllFlags()

            onClick { _, _ ->
                onCloseCallback()
                ClickResult.CLOSE
            }
        }
    }
}
