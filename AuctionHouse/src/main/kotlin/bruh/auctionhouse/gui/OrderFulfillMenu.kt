package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptInt
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Menu for fulfilling an order with quantity selection.
 */
class OrderFulfillMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player,
    private val order: Order
) {
    private val mm = MiniMessage.miniMessage()
    private var quantity = order.remainingQuantity()

    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

            background = MenuUtils.backgroundItem()

            // Order item display
            item(13, createOrderDisplayItem())

            // Quantity selector (if partial fills allowed)
            if (order.allowPartial && order.remainingQuantity() > 1) {
                item(29, createQuantityDecreaseButton())
                item(30, createQuantityDisplayItem())
                item(31, createQuantityIncreaseButton())
            }

            // Confirm button
            item(33, createConfirmButton())

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    OrderBrowserMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            }
            item(36, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(44, closeItem)
        }

        menuAPI.open(menu, player)
    }

    private fun createOrderDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
        loreList.add(Component.empty())

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

        loreList.add(Component.empty())

        // Instructions based on order type
        if (isBuyOrder) {
            loreList.add(mm.deserialize("<gray>You will <green>SELL <gray>items to the order creator"))
            loreList.add(mm.deserialize("<gray>and receive <gold>${MenuUtils.formatPrice(order.pricePerUnit, plugin.economy)} <gray>per item"))
        } else {
            loreList.add(mm.deserialize("<gray>You will <red>BUY <gray>items from the order creator"))
            loreList.add(mm.deserialize("<gray>for <gold>${MenuUtils.formatPrice(order.pricePerUnit, plugin.economy)} <gray>per item"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
        }
    }

    private fun createQuantityDisplayItem(): VItem {
        return VItem(XMaterial.PAPER) {
            name = mm.deserialize("<yellow>Quantity: <white>$quantity")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Remaining: <white>${order.remainingQuantity()}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Click to set custom amount"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val minQuantity = order.minFillQuantity ?: 1
                    val result = menuAPI.promptInt(
                        player,
                        "Enter Quantity",
                        quantity,
                        minQuantity,
                        order.remainingQuantity()
                    )
                    when (result) {
                        is AnvilInputResult.Success -> quantity = result.value
                        is AnvilInputResult.Cancelled -> {}
                    }
                    open()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createQuantityDecreaseButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = mm.deserialize("<red>Decrease Quantity")
            hideAllFlags()

            onClick { _, _ ->
                val minQuantity = order.minFillQuantity ?: 1
                if (quantity > minQuantity) {
                    quantity--
                }
                open()
                ClickResult.ALLOW
            }
        }
    }

    private fun createQuantityIncreaseButton(): VItem {
        return VItem(XMaterial.GREEN_WOOL) {
            name = mm.deserialize("<green>Increase Quantity")
            hideAllFlags()

            onClick { _, _ ->
                if (quantity < order.remainingQuantity()) {
                    quantity++
                }
                open()
                ClickResult.ALLOW
            }
        }
    }

    private fun createConfirmButton(): VItem {
        val totalPrice = quantity * order.pricePerUnit
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Confirm Fulfillment")
            val loreList = mutableListOf<Component>()

            loreList.add(mm.deserialize("<gray>Quantity: <white>$quantity"))
            loreList.add(mm.deserialize("<gray>Price per unit: <gold>${MenuUtils.formatPrice(order.pricePerUnit, plugin.economy)}"))
            loreList.add(Component.empty())

            if (isBuyOrder) {
                loreList.add(mm.deserialize("<green>You will receive: <gold>${MenuUtils.formatPrice(totalPrice, plugin.economy)}"))
            } else {
                loreList.add(mm.deserialize("<red>You will pay: <gold>${MenuUtils.formatPrice(totalPrice, plugin.economy)}"))
            }

            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to confirm"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val items = findItemsInInventory()
                    if (items.isEmpty() && order.orderType == OrderType.BUY_ORDER) {
                        player.sendMessage(mm.deserialize("<red>You don't have any ${order.itemMaterial.name.replace("_", " ").lowercase()} in your inventory!"))
                    } else {
                        val result = orderService.fulfillOrder(player, order.id, items)
                        player.sendMessage(result.message)
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun findItemsInInventory(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        var remaining = quantity

        for (item in player.inventory.contents.filterNotNull()) {
            if (remaining <= 0) break

            if (item.type == order.itemMaterial) {
                // Check display name match if specified
                val itemDisplayName = item.itemMeta?.displayName()?.let { MiniMessage.miniMessage().serialize(it) }
                if (order.itemDisplayName != null && order.itemDisplayName != itemDisplayName) {
                    continue
                }

                val toTake = minOf(remaining, item.amount)
                val clone = item.clone()
                clone.amount = toTake
                items.add(clone)
                remaining -= toTake
            }
        }

        return items
    }
}
