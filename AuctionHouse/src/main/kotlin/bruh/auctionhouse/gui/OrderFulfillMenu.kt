package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptIntAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack

/**
 * Menu for fulfilling an order with quantity selection.
 */
class OrderFulfillMenu(
    private val pctx: PlayerMenuContext,
    private val order: Order
) : SimpleMenu() {
    private val inventoryCount = countMatchingItems()
    private val remainingQuantity = order.remainingQuantity()
    private var quantity by menuState(
        if (order.allowPartial) minOf(inventoryCount, remainingQuantity) else remainingQuantity
    )
    private var confirmationPending by menuState(false)

    /**
     * Whether this menu can be opened. Callers should check this before opening.
     * If false, validation messages have already been sent to the player.
     */
    val canOpen: Boolean

    init {
        background = MenuUtils.backgroundItem()

        canOpen = validateCanOpen()
    }

    private fun validateCanOpen(): Boolean {
        if (inventoryCount == 0) {
            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", "0")
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return false
        }

        if (!order.allowPartial && inventoryCount < remainingQuantity) {
            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                unparsed("required", remainingQuantity.toString())
                unparsed("have", inventoryCount.toString())
                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
            })
            return false
        }

        order.minFillQuantity?.let { minFill ->
            if (inventoryCount < minFill) {
                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_MIN_FILL_NOT_MET) {
                    unparsed("min", minFill.toString())
                    unparsed("have", inventoryCount.toString())
                    unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                })
                return false
            }
        }

        return true
    }

    override fun populateItems() {
        items.clear()

        if (!order.allowPartial) {
            buildConfirmOnlyLayout()
        } else {
            buildFullLayout()
        }
    }

    private fun buildFullLayout() {
        rows = 5
        title = pctx.translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

        item(13, createOrderDisplayItem())

        if (order.allowPartial && remainingQuantity > 1 && inventoryCount > 1) {
            item(29, createQuantityDecreaseButton())
            item(30, createQuantityDisplayItem())
            item(31, createQuantityIncreaseButton())
        }

        item(33, createConfirmButton())

        item(36, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderBrowserMenu(pctx))
            }
        })

        item(44, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }

    private fun buildConfirmOnlyLayout() {
        rows = 3
        title = pctx.translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)

        item(13, createOrderDisplayItem())
        item(15, createConfirmButton())

        item(18, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderBrowserMenu(pctx))
            }
        })

        item(26, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }

    private fun createOrderDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        val loreList = mutableListOf<Component>()
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

        loreList.add(Component.empty())

        if (isBuyOrder) {
            loreList.add(pctx.mm.deserialize("<gray>You will <green>SELL <gray>items to the order creator"))
            loreList.add(pctx.mm.deserialize("<gray>and receive <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)} <gray>per item"))
        } else {
            loreList.add(pctx.mm.deserialize("<gray>You will <red>BUY <gray>items from the order creator"))
            loreList.add(pctx.mm.deserialize("<gray>for <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)} <gray>per item"))
        }

        return VItem(material) {
            name = order.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
        }
    }

    private fun createQuantityDisplayItem(): VItem {
        return VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow>Quantity: <white>$quantity")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Remaining: <white>${order.remainingQuantity()}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Click to set custom amount"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                val minQuantity = order.minFillQuantity ?: 1
                pctx.menuAPI.promptIntAsync(
                    pctx.player,
                    "Enter Quantity",
                    quantity,
                    minQuantity,
                    minOf(order.remainingQuantity(), inventoryCount)
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> quantity = result.value
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@OrderFulfillMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createQuantityDecreaseButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>Decrease Quantity")
            hideAllFlags()

            onClick { _, _ ->
                val minQuantity = order.minFillQuantity ?: 1
                if (quantity > minQuantity) {
                    quantity--
                }
                ClickResult.Refresh
            }
        }
    }

    private fun createQuantityIncreaseButton(): VItem {
        return VItem(XMaterial.GREEN_WOOL) {
            name = pctx.mm.deserialize("<green>Increase Quantity")
            hideAllFlags()

            onClick { _, _ ->
                if (quantity < minOf(order.remainingQuantity(), inventoryCount)) {
                    quantity++
                }
                ClickResult.Refresh
            }
        }
    }

    private fun createConfirmButton(): VItem {
        val totalPrice = quantity * order.pricePerUnit
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER
        val isExpensive = totalPrice > pctx.config.gui.confirm.expensiveThreshold

        return VItem(if (confirmationPending) XMaterial.GOLD_BLOCK else XMaterial.EMERALD_BLOCK) {
            name = pctx.mm.deserialize(if (confirmationPending) "<yellow>⚠ Click Again to Confirm" else "<green>Confirm Fulfillment")
            val loreList = mutableListOf<Component>()

            loreList.add(pctx.mm.deserialize("<gray>Quantity: <white>$quantity"))
            loreList.add(pctx.mm.deserialize("<gray>Price per unit: <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)}"))
            loreList.add(Component.empty())

            if (isBuyOrder) {
                loreList.add(pctx.mm.deserialize("<green>You will receive: <gold>${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
            } else {
                loreList.add(pctx.mm.deserialize("<red>You will pay: <gold>${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
            }

            if (isExpensive && !confirmationPending) {
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<red>⚠ High Value Transaction"))
                loreList.add(pctx.mm.deserialize("<yellow>Click again to confirm"))
            } else if (confirmationPending) {
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<yellow>Click to complete purchase"))
            } else {
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<green>Click to confirm"))
            }

            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                if (isExpensive && !confirmationPending) {
                    confirmationPending = true
                    pctx.player.sendMessage(pctx.mm.deserialize("<yellow>⚠ Click again to confirm purchase of <gold>${MenuUtils.formatPrice(totalPrice, pctx.economy)}"))
                    return@onClick ClickResult.Refresh
                }

                controls.runAsync(
                    action = {
                        val currentInventoryCount = countMatchingItems()
                        if (currentInventoryCount < quantity) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                                unparsed("required", quantity.toString())
                                unparsed("have", currentInventoryCount.toString())
                                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                            })
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ITEMS_MAY_HAVE_MOVED))
                            return@runAsync null
                        }

                        val items = findItemsInInventory()
                        if (items.isEmpty() && order.orderType == OrderType.BUY_ORDER) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                                unparsed("required", quantity.toString())
                                unparsed("have", "0")
                                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                            })
                            return@runAsync null
                        } else if (items.sumOf { it.amount } < quantity) {
                            val foundAmount = items.sumOf { it.amount }
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_NOT_ENOUGH_ITEMS) {
                                unparsed("required", quantity.toString())
                                unparsed("have", foundAmount.toString())
                                unparsed("material", order.itemMaterial.name.replace("_", " ").lowercase())
                            })
                            return@runAsync null
                        }

                        pctx.orderService.fulfillOrder(pctx.player, order.id, items)
                    },
                    onSuccess = { result ->
                        if (result != null) {
                            pctx.player.sendMessage(result.message)
                        }
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun findItemsInInventory(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        var remaining = quantity

        for (item in pctx.player.inventory.contents.filterNotNull()) {
            if (remaining <= 0) break

            if (item.type == order.itemMaterial) {
                val itemDisplayName = item.itemMeta?.displayName()?.let { pctx.mm.serialize(it) }
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
        return pctx.player.inventory.contents.filterNotNull().sumOf { item ->
            if (item.type == order.itemMaterial) {
                val itemDisplayName = item.itemMeta?.displayName()?.let { pctx.mm.serialize(it) }
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
