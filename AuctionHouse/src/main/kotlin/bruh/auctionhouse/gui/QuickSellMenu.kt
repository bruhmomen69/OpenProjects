package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class QuickSellMenu(
    private val menuAPI: MenuAPI,
    private val orderService: OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val item: ItemStack
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }

        val bestOrder = runBlocking {
            orderService.findBestBuyOrderForMaterial(item.type)
        }

        if (bestOrder == null) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_QUICK_SELL_NO_ORDER))
            return
        }

        val sellQuantity = minOf(item.amount, bestOrder.remainingQuantity())
        val totalPrice = sellQuantity * bestOrder.pricePerUnit
        val isExpensive = totalPrice > config.gui.confirm.expensiveThreshold
        var confirmationPending = false

        val menu = menuAPI.simple {
            rows = 4
            title = mm.deserialize("<green>Quick Sell")

            background = MenuUtils.backgroundItem()

            item(11, VItem(XMaterial.matchXMaterial(item.type.name).orElse(XMaterial.STONE)) {
                name = item.itemMeta?.displayName() ?: Component.text(item.type.name.replace("_", " "))
                val loreList = mutableListOf<Component>()
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<gray>Amount: <white>${item.amount}"))
                lore = loreList
                hideAllFlags()
            })

            item(15, VItem(XMaterial.EMERALD) {
                name = mm.deserialize("<yellow>Best Buy Order")
                val loreList = mutableListOf<Component>()
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<gray>Buyer: <white>${bestOrder.creatorName}"))
                loreList.add(mm.deserialize("<gray>Price: <gold>${MenuUtils.formatPrice(bestOrder.pricePerUnit, economy)} per unit"))
                loreList.add(mm.deserialize("<gray>Available: <white>${bestOrder.remainingQuantity()}"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Sell up to $sellQuantity for ${MenuUtils.formatPrice(totalPrice, economy)}"))
                lore = loreList
                hideAllFlags()
            })

            item(31, VItem(if (confirmationPending) XMaterial.GOLD_BLOCK else XMaterial.EMERALD_BLOCK) {
                name = mm.deserialize(if (confirmationPending) "<yellow>⚠ Click Again to Confirm" else "<green>Confirm Quick Sell")
                val loreList = mutableListOf<Component>()
                loreList.add(mm.deserialize("<gray>Quantity: <white>$sellQuantity"))
                loreList.add(mm.deserialize("<gray>Price per unit: <gold>${MenuUtils.formatPrice(bestOrder.pricePerUnit, economy)}"))
                loreList.add(mm.deserialize("<gray>Total: <gold>${MenuUtils.formatPrice(totalPrice, economy)}"))
                loreList.add(Component.empty())
                
                if (isExpensive && !confirmationPending) {
                    loreList.add(mm.deserialize("<red>⚠ High Value Transaction"))
                    loreList.add(mm.deserialize("<yellow>Click again to confirm"))
                } else if (confirmationPending) {
                    loreList.add(mm.deserialize("<yellow>Click to complete sale"))
                } else {
                    loreList.add(mm.deserialize("<green>Click to confirm"))
                }
                lore = loreList
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        if (isExpensive && !confirmationPending) {
                            confirmationPending = true
                            player.sendMessage(mm.deserialize("<yellow>⚠ Click again to confirm sale of <gold>${MenuUtils.formatPrice(totalPrice, economy)}"))
                            open()
                            return@runBlocking
                        }

                        val itemsToSell = listOf(item.clone().apply { amount = sellQuantity })
                        val result = orderService.fulfillOrder(player, bestOrder.id, itemsToSell)
                        
                        when (result) {
                            is ServiceResult.Success<*> -> {
                                if (sellQuantity >= item.amount) {
                                    player.inventory.setItemInMainHand(null)
                                } else {
                                    item.amount -= sellQuantity
                                }
                            }
                            is ServiceResult.Failure -> {}
                        }
                        player.sendMessage(result.message)
                    }
                    ClickResult.CLOSE
                }
            })

            item(27, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, plugin.auctionService, orderService, plugin.auctionRepository, plugin.bidRepository, plugin.orderRepository, plugin.watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            })
        }

        menuAPI.open(menu, player)
    }
}
