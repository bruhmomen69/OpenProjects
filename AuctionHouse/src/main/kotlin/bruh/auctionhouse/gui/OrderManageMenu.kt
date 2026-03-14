package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDoubleAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.util.concurrent.CompletableFuture

class OrderManageMenu(
    private val pctx: PlayerMenuContext,
    private val order: Order,
    private val parentMenu: () -> Menu
) : SimpleMenu() {
    private var currentOrder by menuState(order)

    init {
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        val canEditPrice = currentOrder.isActive() && currentOrder.quantityFilled == 0
        rows = if (canEditPrice) 5 else 4
        title = pctx.mm.deserialize("<yellow>Order ${currentOrder.shortId}")

        item(13, createOrderDisplayItem())

        if (canEditPrice) {
            item(29, createEditPriceButton())
        }

        item(if (canEditPrice) 31 else 29, createCancelButton())
        item(33, createBackButton())
    }

    private fun createOrderDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(currentOrder.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = currentOrder.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
        loreList.add(Component.empty())
        loreList.add(pctx.mm.deserialize("<gray>ID: <white>${currentOrder.shortId}"))
        loreList.add(Component.empty())

        loreList.add(pctx.translationAPI.getComponentSync(
            if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
        ))

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_QUANTITY) {
            unparsed("current", currentOrder.quantityFilled.toString())
            unparsed("total", currentOrder.quantityRequested.toString())
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PRICE) {
            unparsed("price", MenuUtils.formatPrice(currentOrder.pricePerUnit, pctx.economy))
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TOTAL) {
            unparsed("total", MenuUtils.formatPrice(currentOrder.totalValue(), pctx.economy))
        })

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(currentOrder.expiresAt))
        })

        if (currentOrder.allowPartial) {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_PARTIAL))
        } else {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.ORDER_ITEM_NO_PARTIAL))
        }

        return VItem(material) {
            name = currentOrder.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(currentOrder.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
            glow()
        }
    }

    private fun createEditPriceButton(): VItem {
        return VItem(XMaterial.ANVIL) {
            name = pctx.mm.deserialize("<yellow>Edit Price")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Current: <gold>${MenuUtils.formatPrice(currentOrder.pricePerUnit, pctx.economy)} per unit"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to edit price"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptDoubleAsync(
                    pctx.player,
                    "New Price Per Unit",
                    currentOrder.pricePerUnit,
                    pctx.config.orders.minPricePerUnit,
                    pctx.config.orders.maxPricePerUnit
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            CompletableFuture.supplyAsync {
                                kotlinx.coroutines.runBlocking { pctx.orderService.editOrderPrice(pctx.player, currentOrder.id, result.value) }
                            }.thenAccept { editResult ->
                                pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                    when (editResult) {
                                        is ServiceResult.Success -> {
                                            currentOrder = editResult.data
                                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_PRICE_UPDATED) {
                                                unparsed("price", MenuUtils.formatPrice(result.value, pctx.economy))
                                            })
                                        }
                                        is ServiceResult.Failure -> {
                                            pctx.player.sendMessage(editResult.message)
                                        }
                                    }
                                    pctx.menuAPI.open(this@OrderManageMenu, pctx.player)
                                })
                            }
                        }
                        is AnvilInputResult.Cancelled -> {
                            pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                pctx.menuAPI.open(this@OrderManageMenu, pctx.player)
                            })
                        }
                    }
                }
                ClickResult.Deny
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.BARRIER) {
            name = pctx.mm.deserialize("<red>Cancel Order")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Cancel this order and"))
            if (currentOrder.orderType == OrderType.BUY_ORDER) {
                loreList.add(pctx.mm.deserialize("<gray>receive a refund for unfilled items"))
                val remainingValue = currentOrder.remainingValue()
                if (remainingValue > 0) {
                    loreList.add(Component.empty())
                    loreList.add(pctx.mm.deserialize("<gray>Refund: <gold>${MenuUtils.formatPrice(remainingValue, pctx.economy)}"))
                }
            } else {
                loreList.add(pctx.mm.deserialize("<gray>get your items back"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<yellow>Click to cancel"))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                controls.runAsync(
                    action = { pctx.orderService.cancelOrder(pctx.player, currentOrder.id) },
                    onSuccess = { result ->
                        when (result) {
                            is ServiceResult.Success -> {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_CANCELLED))
                                pctx.menuAPI.open(parentMenu(), pctx.player)
                            }
                            is ServiceResult.Failure -> {
                                pctx.player.sendMessage(result.message)
                                controls.close()
                            }
                        }
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createBackButton(): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BACK)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(parentMenu())
            }
        }
    }
}
