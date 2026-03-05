package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
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
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val order: Order
) {
    private val mm = MiniMessage.miniMessage()
    private val inventoryCount = countMatchingItems()
    private val remainingQuantity = order.remainingQuantity()
    private var quantity: Int

    init {
        // Calculate default quantity based on partial fill settings
        quantity = if (order.allowPartial) {
            // For partial fills: default to min(inventory count, remaining quantity)
            minOf(inventoryCount, remainingQuantity)
        } else {
            // For non-partial: must fill the full remaining amount
            remainingQuantity
        }
    }

    /**
     * Opens the fulfillment menu. Returns false if player doesn't have enough items.
     */
    fun open(): Boolean {
        // Check if player has any matching items
        if (inventoryCount == 0) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", "0")
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return false
        }

        // For non-partial fills, check if player has enough items
        if (!order.allowPartial && inventoryCount < remainingQuantity) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", inventoryCount.toString())
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return false
        }

        // For partial fills with min fill quantity, check if player has at least the minimum
        order.minFillQuantity?.let { minFill ->
            if (inventoryCount < minFill) {
                player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_MIN_FILL_NOT_MET) {
                    unparsed("min", minFill.toString())
                    unparsed("have", inventoryCount.toString())
                    unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                })
                return false
            }
        }

        // If partial fills not allowed, skip directly to confirmation (no quantity selection)
        if (!order.allowPartial) {
            // Just show confirm dialog with full amount required
            openConfirmOnlyMenu()
        } else {
            openFullMenu()
        }
        return true
    }

    private fun openFullMenu() {
        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

            background = MenuUtils.backgroundItem()

            // Order item display
            item(13, createOrderDisplayItem())

            // Quantity selector (only show if partial fills allowed and there's more than 1 to fill)
            if (order.allowPartial && remainingQuantity > 1 && inventoryCount > 1) {
                item(29, createQuantityDecreaseButton())
                item(30, createQuantityDisplayItem())
                item(31, createQuantityIncreaseButton())
            }

            // Confirm button
            item(33, createConfirmButton())
        }

        menuAPI.open(menu, player)
    }

    private fun openConfirmOnlyMenu() {
        val menu = menuAPI.simple {
            rows = 3
            title = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

            background = MenuUtils.backgroundItem()

            // Order item display in center
            item(13, createOrderDisplayItem())

            // Confirm button (right side)
            item(15, createConfirmButton())

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    OrderBrowserMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            item(18, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(26, closeItem)
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
                        minOf(order.remainingQuantity(), inventoryCount)
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
                if (quantity < minOf(order.remainingQuantity(), inventoryCount)) {
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
                    // Re-count items immediately before fulfillment to catch inventory changes
                    val currentInventoryCount = countMatchingItems()
                    if (currentInventoryCount < quantity) {
                        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                            unparsed("required", quantity.toString())
                            unparsed("have", currentInventoryCount.toString())
                            unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                        })
                        player.sendMessage(mm.deserialize("<red>Items may have been moved or dropped since opening this menu."))
                        return@runBlocking
                    }

                    // Re-find items to ensure we have the actual current items
                    val items = findItemsInInventory()
                    if (items.isEmpty() && order.orderType == OrderType.BUY_ORDER) {
                        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                            unparsed("required", quantity.toString())
                            unparsed("have", "0")
                            unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                        })
                    } else if (items.sumOf { it.amount } < quantity) {
                        // Handle case where user selected more than they currently have
                        val foundAmount = items.sumOf { it.amount }
                        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                            unparsed("required", quantity.toString())
                            unparsed("have", foundAmount.toString())
                            unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                        })
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

    /**
     * Counts how many matching items the player has in their inventory.
     */
    private fun countMatchingItems(): Int {
        return player.inventory.contents.filterNotNull().sumOf { item ->
            if (item.type == order.itemMaterial) {
                // Check display name match if specified
                val itemDisplayName = item.itemMeta?.displayName()?.let { mm.serialize(it) }
                if (order.itemDisplayName != null && order.itemDisplayName != itemDisplayName) {
                    0
                } else {
                    item.amount
                }
            } else {
                0
            }
        }
    }
}
