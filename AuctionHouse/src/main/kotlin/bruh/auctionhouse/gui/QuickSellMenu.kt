package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack

class QuickSellMenu(
    private val pctx: PlayerMenuContext,
    private val item: ItemStack
) : SimpleMenu() {

    private var bestOrder: Order? = null
    private var confirmationPending by menuState(false)

    init {
        rows = 4
        title = pctx.mm.deserialize("<green>Quick Sell")
        background = MenuUtils.backgroundItem()

        asyncData<Order?> {
            load {
                if (!pctx.config.orders.enabled) return@load null
                pctx.orderService.findBestBuyOrderForMaterial(item.type)
            }
            onLoaded { order -> bestOrder = order }
        }
    }

    override fun populateItems() {
        items.clear()

        val order = bestOrder

        if (isAsyncLoading) {
            item(13, MenuUtils.loadingAuctionItem())
            return
        }

        if (order == null) {
            item(13, VItem(XMaterial.BARRIER) {
                name = pctx.mm.deserialize("<red>No Buy Orders Available")
                lore = mutableListOf(
                    pctx.mm.deserialize("<gray>There are no buy orders for this item")
                )
                hideAllFlags()
            })
            item(27, MenuUtils.backButton(pctx.translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
                }
            })
            return
        }

        val sellQuantity = minOf(item.amount, order.remainingQuantity())
        val totalPrice = sellQuantity * order.pricePerUnit
        val isExpensive = totalPrice > pctx.config.gui.confirm.expensiveThreshold

        item(11, VItem(XMaterial.matchXMaterial(item.type.name).orElse(XMaterial.STONE)) {
            name = item.itemMeta?.displayName() ?: Component.text(item.type.name.replace("_", " "))
            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Amount: <white>${item.amount}"))
            lore = loreList
            hideAllFlags()
        })

        item(15, VItem(XMaterial.EMERALD) {
            name = pctx.mm.deserialize("<yellow>Best Buy Order")
            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Buyer: <white>${order.creatorName}"))
            loreList.add(pctx.mm.deserialize("<gray>Price: <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)} per unit"))
            loreList.add(pctx.mm.deserialize("<gray>Available: <white>${order.remainingQuantity()}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Sell up to $sellQuantity for ${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
            lore = loreList
            hideAllFlags()
        })

        item(31, VItem(if (confirmationPending) XMaterial.GOLD_BLOCK else XMaterial.EMERALD_BLOCK) {
            name = pctx.mm.deserialize(if (confirmationPending) "<yellow>⚠ Click Again to Confirm" else "<green>Confirm Quick Sell")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Quantity: <white>$sellQuantity"))
            loreList.add(pctx.mm.deserialize("<gray>Price per unit: <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)}"))
            loreList.add(pctx.mm.deserialize("<gray>Total: <gold>${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
            loreList.add(Component.empty())

            if (isExpensive && !confirmationPending) {
                loreList.add(pctx.mm.deserialize("<red>⚠ High Value Transaction"))
                loreList.add(pctx.mm.deserialize("<yellow>Click again to confirm"))
            } else if (confirmationPending) {
                loreList.add(pctx.mm.deserialize("<yellow>Click to complete sale"))
            } else {
                loreList.add(pctx.mm.deserialize("<green>Click to confirm"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                if (isExpensive && !confirmationPending) {
                    confirmationPending = true
                    pctx.player.sendMessage(pctx.mm.deserialize("<yellow>⚠ Click again to confirm sale of <gold>${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
                    return@onClick ClickResult.Deny
                }

                val itemsToSell = listOf(item.clone().apply { amount = sellQuantity })
                controls.runAsync(
                    action = { pctx.orderService.fulfillOrder(pctx.player, order.id, itemsToSell) },
                    onSuccess = { result ->
                        when (result) {
                            is ServiceResult.Success<*> -> {
                                if (sellQuantity >= item.amount) {
                                    pctx.player.inventory.setItemInMainHand(null)
                                } else {
                                    item.amount -= sellQuantity
                                }
                            }
                            is ServiceResult.Failure -> {}
                        }
                        pctx.player.sendMessage(result.message)
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        })

        item(27, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        })
    }
}
