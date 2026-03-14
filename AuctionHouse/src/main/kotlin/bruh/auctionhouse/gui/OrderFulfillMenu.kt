package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
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
    private val orderRepository: OrderRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val order: Order
) : bruh.zchat.utils.menuapi.SimpleMenu() {
    private val mm = MiniMessage.miniMessage()
    private val inventoryCount = countMatchingItems()
    private val remainingQuantity = order.remainingQuantity()
    private var quantity: Int
    private var confirmationPending = false

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

    fun createMenuOrNull(): Menu? {
        // Check if player has any matching items
        if (inventoryCount == 0) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", "0")
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return null
        }

        // For non-partial fills, check if player has enough items
        if (!order.allowPartial && inventoryCount < remainingQuantity) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", inventoryCount.toString())
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return null
        }

        // For partial fills with min fill quantity, check if player has at least the minimum
        order.minFillQuantity?.let { minFill ->
            if (inventoryCount < minFill) {
                player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_MIN_FILL_NOT_MET) {
                    unparsed("min", minFill.toString())
                    unparsed("have", inventoryCount.toString())
                    unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                })
                return null
            }
        }

        // If partial fills not allowed, skip directly to confirmation (no quantity selection)
        return if (!order.allowPartial) {
            createConfirmOnlyMenu()
        } else {
            createFullMenu()
        }
    }

    fun createMenu(): Menu = createMenuOrNull()
        ?: error("OrderFulfillMenu cannot be created for this player/order combination")

    private fun createFullMenu(): Menu {
        return this.apply {
            items.clear()
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
    }

    private fun createConfirmOnlyMenu(): Menu {
        return this.apply {
            items.clear()
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
            item(18, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.Close
                }
            }
            item(26, closeItem)
        }
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
                }
                createMenuOrNull()?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
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
                createMenuOrNull()?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
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
                createMenuOrNull()?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
            }
        }
    }

    private fun createConfirmButton(): VItem {
        val totalPrice = quantity * order.pricePerUnit
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER
        val isExpensive = totalPrice > config.gui.confirm.expensiveThreshold

        return VItem(if (confirmationPending) XMaterial.GOLD_BLOCK else XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize(if (confirmationPending) "<yellow>⚠ Click Again to Confirm" else "<green>Confirm Fulfillment")
            val loreList = mutableListOf<Component>()

            loreList.add(mm.deserialize("<gray>Quantity: <white>$quantity"))
            loreList.add(mm.deserialize("<gray>Price per unit: <gold>${MenuUtils.formatPrice(order.pricePerUnit, plugin.economy)}"))
            loreList.add(Component.empty())

            if (isBuyOrder) {
                loreList.add(mm.deserialize("<green>You will receive: <gold>${MenuUtils.formatPrice(totalPrice, plugin.economy)}"))
            } else {
                loreList.add(mm.deserialize("<red>You will pay: <gold>${MenuUtils.formatPrice(totalPrice, plugin.economy)}"))
            }

            if (isExpensive && !confirmationPending) {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<red>⚠ High Value Transaction"))
                loreList.add(mm.deserialize("<yellow>Click again to confirm"))
            } else if (confirmationPending) {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<yellow>Click to complete purchase"))
            } else {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Click to confirm"))
            }

            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (isExpensive && !confirmationPending) {
                    confirmationPending = true
                    player.sendMessage(mm.deserialize("<yellow>⚠ Click again to confirm purchase of <gold>${MenuUtils.formatPrice(totalPrice, plugin.economy)}"))
                    return@onClick createMenuOrNull()?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
                }

                runBlocking {
                    // Re-count items immediately before fulfillment to catch inventory changes
                    val currentInventoryCount = countMatchingItems()
                    if (currentInventoryCount < quantity) {
                        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                            unparsed("required", quantity.toString())
                            unparsed("have", currentInventoryCount.toString())
                            unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                        })
                        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ITEMS_MAY_HAVE_MOVED))
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
                ClickResult.Close
            }
        }
    }

    private fun findItemsInInventory(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        var remaining = quantity

        for (item in player.inventory.contents.filterNotNull()) {
            if (remaining <= 0) break

            if (item.type == order.itemMaterial) {
                val itemDisplayName = item.itemMeta?.displayName()?.let { MiniMessage.miniMessage().serialize(it) }
                if (order.itemDisplayName != null && order.itemDisplayName != itemDisplayName) {
                    continue
                }

                if (order.itemNbtHash != null) {
                    val itemNbtHash = computeItemNbtHash(item)
                    if (order.itemNbtHash != itemNbtHash) {
                        continue
                    }
                }

                if (order.itemLoreHash != null) {
                    val itemLoreHash = computeItemLoreHash(item)
                    if (order.itemLoreHash != itemLoreHash) {
                        continue
                    }
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
                val itemDisplayName = item.itemMeta?.displayName()?.let { mm.serialize(it) }
                if (order.itemDisplayName != null && order.itemDisplayName != itemDisplayName) {
                    return@sumOf 0
                }

                if (order.itemNbtHash != null) {
                    val itemNbtHash = computeItemNbtHash(item)
                    if (order.itemNbtHash != itemNbtHash) {
                        return@sumOf 0
                    }
                }

                if (order.itemLoreHash != null) {
                    val itemLoreHash = computeItemLoreHash(item)
                    if (order.itemLoreHash != itemLoreHash) {
                        return@sumOf 0
                    }
                }

                item.amount
            } else {
                0
            }
        }
    }

    private fun computeItemNbtHash(item: ItemStack): String {
        val meta = item.itemMeta ?: return ""
        val sb = StringBuilder()
        
        meta.enchants.forEach { (enchant, level) ->
            sb.append(enchant.key.key).append(":").append(level).append(";")
        }
        
        if (meta.hasCustomModelData()) {
            sb.append("cmd:").append(meta.customModelData).append(";")
        }
        
        meta.itemFlags.forEach { flag ->
            sb.append("flag:").append(flag.name).append(";")
        }
        
        return sb.toString().hashCode().toString()
    }

    private fun computeItemLoreHash(item: ItemStack): String {
        val meta = item.itemMeta ?: return ""
        val lore = meta.lore ?: return ""
        return lore.joinToString("|").hashCode().toString()
    }
}
