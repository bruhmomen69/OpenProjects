package bruh.auctionhouse.gui

import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDoubleAsync
import bruh.zchat.utils.menuapi.promptIntAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.time.Duration
import java.time.Instant

/**
 * Main menu for creating buy/sell orders.
 * Features separate stack and item controls for easy quantity input.
 *
 * Slot Layout:
 * 13 - Selected Material display / Item display for sell orders
 * 19 - Stack decrement (buy orders only)
 * 20 - Stacks display (click to edit) (buy orders only)
 * 21 - Stack increment (buy orders only)
 * 22 - Order type toggle
 * 28 - Item decrement (buy orders only)
 * 29 - Items display (click to edit) (buy orders only)
 * 30 - Item increment (buy orders only)
 * 32 - Price per unit
 * 33 - Duration
 * 38 - Allow partial toggle (buy orders only)
 * 39 - NBT match toggle (buy orders only)
 * 41 - Lore match toggle (buy orders only)
 * 40 - Confirm
 * 45 - Back
 * 53 - Close
 */
class OrderCreateMenu(
    private val pctx: PlayerMenuContext,
    initialOrderType: OrderType = OrderType.BUY_ORDER
) : SimpleMenu() {
    private var state by menuState(OrderCreateState())
    private var orderType by menuState(initialOrderType)

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.CREATE_ORDER_TITLE)
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        // For sell orders, get item from player's hand
        if (orderType == OrderType.SELL_ORDER) {
            val itemInHand = pctx.player.inventory.itemInMainHand
            if (!itemInHand.type.isAir) {
                state = state.copy(
                    selectedMaterial = itemInHand.type,
                    stacks = itemInHand.amount / 64,
                    items = itemInHand.amount % 64
                )
            }
        }

        item(13, createMaterialDisplay())

        // Order type toggle
        item(22, createOrderTypeToggle())

        // Buy order controls (only show for buy orders)
        if (orderType == OrderType.BUY_ORDER) {
            // Stack controls
            item(19, createStackDecrement())
            item(20, createStacksDisplay())
            item(21, createStackIncrement())

            // Item controls
            item(28, createItemDecrement())
            item(29, createItemsDisplay())
            item(30, createItemIncrement())

            // Matching options
            item(38, createPartialToggle())
            item(39, createNbtMatchToggle())
            item(41, createLoreMatchToggle())
        }

        item(32, createPriceButton())
        item(33, createDurationButton())

        item(45, createBackButton())
        item(49, createConfirmButton())
        item(53, createCloseButton())
    }

    private fun createOrderTypeToggle(): VItem {
        val isBuyOrder = orderType == OrderType.BUY_ORDER
        return VItem(if (isBuyOrder) XMaterial.DIAMOND else XMaterial.GOLD_INGOT) {
            name = pctx.mm.deserialize(if (isBuyOrder) "<blue>Buy Order" else "<yellow>Sell Order")
            val loreList = mutableListOf<Component>()
            if (isBuyOrder) {
                loreList.add(pctx.mm.deserialize("<gray>Buying items from other players"))
                loreList.add(pctx.mm.deserialize("<gray>You will pay when someone sells to you"))
            } else {
                loreList.add(pctx.mm.deserialize("<gray>Selling items to other players"))
                loreList.add(pctx.mm.deserialize("<red>You must hold the item to sell"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                orderType = if (isBuyOrder) OrderType.SELL_ORDER else OrderType.BUY_ORDER
                ClickResult.Deny
            }
        }
    }

    private fun createMaterialDisplay(): VItem {
        return if (state.selectedMaterial != null) {
            val xMaterial = XMaterial.matchXMaterial(state.selectedMaterial!!)
            VItem(xMaterial) {
                name = Component.text(state.selectedMaterial!!.name.replace("_", " "))
                lore = mutableListOf(
                    pctx.mm.deserialize("<gray>Total: <white>${state.totalQuantity} items"),
                    pctx.mm.deserialize("<gray>${state.stacks} stacks + ${state.items} items"),
                    Component.empty(),
                    pctx.mm.deserialize("<yellow>Click to change material")
                )
                hideAllFlags()

                onClick { _, _ ->
                    openMaterialPicker()
                }
            }
        } else {
            VItem(XMaterial.BARRIER) {
                name = pctx.mm.deserialize("<red>No material selected")
                lore = mutableListOf(
                    pctx.mm.deserialize("<gray>Click to select a material")
                )
                hideAllFlags()

                onClick { _, _ ->
                    openMaterialPicker()
                }
            }
        }
    }

    private fun openMaterialPicker(): ClickResult {
        return ClickResult.SwitchMenu(MaterialPickerMenu(pctx) { material ->
            state = state.copy(selectedMaterial = material.parseMaterial())
            this@OrderCreateMenu
        })
    }

    private fun createStackDecrement(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>-1 Stack")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Remove 64 items"),
                pctx.mm.deserialize("<yellow>Shift-click: -10 stacks")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(-64, isShift)
                ClickResult.Deny
            }
        }
    }

    private fun createStacksDisplay(): VItem {
        return VItem(XMaterial.CHEST) {
            name = pctx.mm.deserialize("<yellow>${state.stacks} Stacks")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>${state.stacks * 64} items from stacks"),
                pctx.mm.deserialize("<gray>Click to set stacks directly"),
                Component.empty(),
                pctx.mm.deserialize("<yellow>Total: ${state.totalQuantity} items")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptIntAsync(
                    pctx.player,
                    "Enter number of stacks",
                    state.stacks,
                    0,
                    OrderCreateState.MAX_STACKS
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            state = state.copy(stacks = result.value)
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@OrderCreateMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createStackIncrement(): VItem {
        return VItem(XMaterial.GREEN_WOOL) {
            name = pctx.mm.deserialize("<green>+1 Stack")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Add 64 items"),
                pctx.mm.deserialize("<yellow>Shift-click: +10 stacks")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(64, isShift)
                ClickResult.Deny
            }
        }
    }

    private fun createItemDecrement(): VItem {
        return VItem(XMaterial.REDSTONE) {
            name = pctx.mm.deserialize("<red>-1 Item")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Remove 1 item"),
                pctx.mm.deserialize("<yellow>Shift-click: -10 items")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(-1, isShift)
                ClickResult.Deny
            }
        }
    }

    private fun createItemsDisplay(): VItem {
        return VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow>${state.items} Items")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Individual items (0-63)"),
                pctx.mm.deserialize("<gray>Beyond full stacks"),
                pctx.mm.deserialize("<gray>Click to set items directly"),
                Component.empty(),
                pctx.mm.deserialize("<yellow>Auto-converts at 64")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptIntAsync(
                    pctx.player,
                    "Enter number of items (0-63)",
                    state.items,
                    0,
                    63
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            state = state.withItems(result.value)
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@OrderCreateMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createItemIncrement(): VItem {
        return VItem(XMaterial.SLIME_BALL) {
            name = pctx.mm.deserialize("<green>+1 Item")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Add 1 item"),
                pctx.mm.deserialize("<yellow>Shift-click: +10 items"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Auto-converts to stack at 64")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(1, isShift)
                ClickResult.Deny
            }
        }
    }

    private fun createPriceButton(): VItem {
        val totalValue = state.totalValue
        return VItem(XMaterial.GOLD_NUGGET) {
            name = pctx.mm.deserialize("<yellow>Price: <gold>${MenuUtils.formatPrice(state.pricePerUnit, pctx.economy)}")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Per unit price"),
                Component.empty(),
                pctx.mm.deserialize("<white>Total: ${MenuUtils.formatPrice(totalValue, pctx.economy)}"),
                pctx.mm.deserialize("<gray>Click to change price"),
                pctx.mm.deserialize("<gray>Right-click to set total value")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                if (ctx.isRightClick) {
                    promptTotalValue()
                } else {
                    promptPricePerUnit()
                }
                ClickResult.Deny
            }
        }
    }

    private fun promptPricePerUnit() {
        pctx.menuAPI.promptDoubleAsync(
            pctx.player,
            "Enter price per item",
            state.pricePerUnit,
            pctx.config.orders.minPricePerUnit,
            pctx.config.orders.maxPricePerUnit
        ).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    state = state.copy(pricePerUnit = result.value)
                }
                is AnvilInputResult.Cancelled -> {}
            }
            pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                pctx.menuAPI.open(this@OrderCreateMenu, pctx.player)
            })
        }
    }

    private fun promptTotalValue() {
        pctx.menuAPI.promptDoubleAsync(
            pctx.player,
            "Enter total value",
            state.totalValue,
            pctx.config.orders.minPricePerUnit,
            pctx.config.orders.maxPricePerUnit * state.totalQuantity.coerceAtLeast(1)
        ).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    val total = result.value
                    if (state.totalQuantity > 0) {
                        state = state.copy(pricePerUnit = total / state.totalQuantity)
                    }
                }
                is AnvilInputResult.Cancelled -> {}
            }
            pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                pctx.menuAPI.open(this@OrderCreateMenu, pctx.player)
            })
        }
    }

    private fun createDurationButton(): VItem {
        val durationHours = state.duration.toHours()
        return VItem(XMaterial.CLOCK) {
            name = pctx.mm.deserialize("<yellow>Duration: <white>${durationHours}h")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to change duration"),
                pctx.mm.deserialize("<gray>Shift-click for shorter"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Expires: ${formatExpiryTime()}")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val durations = listOf(24L, 48L, 72L, 168L)
                val currentIndex = durations.indexOf(durationHours).coerceAtLeast(0)
                val newIndex = if (ctx.isShiftClick) {
                    (currentIndex - 1 + durations.size) % durations.size
                } else {
                    (currentIndex + 1) % durations.size
                }
                state = state.copy(duration = Duration.ofHours(durations[newIndex]))
                ClickResult.Deny
            }
        }
    }

    private fun formatExpiryTime(): String {
        val expiry = Instant.now().plus(state.duration)
        val duration = Duration.between(Instant.now(), expiry)
        return when {
            duration.toDays() > 0 -> "${duration.toDays()}d ${duration.toHoursPart()}h"
            duration.toHours() > 0 -> "${duration.toHours()}h ${duration.toMinutesPart()}m"
            else -> "${duration.toMinutes()}m"
        }
    }

    private fun createPartialToggle(): VItem {
        return VItem(if (state.allowPartial) XMaterial.LIME_DYE else XMaterial.GRAY_DYE) {
            name = pctx.mm.deserialize("<yellow>Allow Partial: <white>${if (state.allowPartial) "Yes" else "No"}")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Allow orders to be filled partially"),
                pctx.mm.deserialize("<gray>Click to toggle"),
                Component.empty(),
                if (state.allowPartial) {
                    pctx.mm.deserialize("<gray>Min fill: ${state.minFillQuantity ?: "Not set"}")
                } else {
                    pctx.mm.deserialize("<gray>Full quantity required")
                }
            )
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(
                    allowPartial = !state.allowPartial,
                    minFillQuantity = if (!state.allowPartial) null else state.minFillQuantity
                )
                ClickResult.Deny
            }
        }
    }

    private fun createNbtMatchToggle(): VItem {
        return VItem(if (state.requireExactNbt) XMaterial.COMMAND_BLOCK else XMaterial.CRAFTING_TABLE) {
            name = pctx.mm.deserialize("<yellow>Match NBT: <white>${if (state.requireExactNbt) "Yes" else "No"}")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Require exact NBT data match"))
            loreList.add(pctx.mm.deserialize("<gray>Enchants, custom model data, etc."))
            if (state.requireExactNbt) {
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<green>Only matching NBT will fulfill"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(requireExactNbt = !state.requireExactNbt)
                ClickResult.Deny
            }
        }
    }

    private fun createLoreMatchToggle(): VItem {
        return VItem(if (state.requireExactLore) XMaterial.WRITABLE_BOOK else XMaterial.BOOK) {
            name = pctx.mm.deserialize("<yellow>Match Lore: <white>${if (state.requireExactLore) "Yes" else "No"}")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Require exact lore match"))
            loreList.add(pctx.mm.deserialize("<gray>All lore lines must match"))
            if (state.requireExactLore) {
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<green>Only matching lore will fulfill"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(requireExactLore = !state.requireExactLore)
                ClickResult.Deny
            }
        }
    }

    private fun createConfirmButton(): VItem {
        val isBuyOrder = orderType == OrderType.BUY_ORDER
        val canConfirm = if (isBuyOrder) state.isValid() else state.selectedMaterial != null && state.totalQuantity > 0
        val listingFee = calculateListingFee()

        return VItem(if (canConfirm) XMaterial.EMERALD_BLOCK else XMaterial.REDSTONE_BLOCK) {
            name = if (canConfirm) {
                pctx.mm.deserialize("<green>Create ${if (isBuyOrder) "Buy" else "Sell"} Order")
            } else {
                pctx.mm.deserialize("<red>Cannot Create Order")
            }
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Listing Fee: <gold>${MenuUtils.formatPrice(listingFee, pctx.economy)}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<white>Summary:"))
            loreList.add(pctx.mm.deserialize("<gray>Type: ${if (isBuyOrder) "<blue>Buy Order" else "<yellow>Sell Order"}"))
            loreList.add(pctx.mm.deserialize("<gray>Item: ${state.selectedMaterial?.name ?: "None"}"))
            loreList.add(pctx.mm.deserialize("<gray>Quantity: ${state.totalQuantity} items"))
            loreList.add(pctx.mm.deserialize("<gray>Price: ${MenuUtils.formatPrice(state.pricePerUnit, pctx.economy)}/item"))
            loreList.add(pctx.mm.deserialize("<gray>Total: ${MenuUtils.formatPrice(state.totalValue, pctx.economy)}"))
            loreList.add(Component.empty())
            if (canConfirm) {
                if (isBuyOrder) {
                    loreList.add(pctx.mm.deserialize("<green>Click to create buy order"))
                } else {
                    loreList.add(pctx.mm.deserialize("<green>Click to create sell order"))
                    loreList.add(pctx.mm.deserialize("<red>Item will be removed from your inventory!"))
                }
            } else {
                loreList.add(pctx.mm.deserialize("<red>Select a material and quantity"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                if (!canConfirm) return@onClick ClickResult.Deny

                controls.runAsync(
                    action = {
                        val material = state.selectedMaterial ?: return@runAsync null

                        if (orderType == OrderType.BUY_ORDER) {
                            pctx.orderService.createBuyOrder(
                                creator = pctx.player,
                                material = material,
                                displayName = state.itemDisplayName,
                                quantity = state.totalQuantity,
                                pricePerUnit = state.pricePerUnit,
                                allowPartial = state.allowPartial,
                                minFillQuantity = state.minFillQuantity,
                                duration = state.duration,
                                requireExactNbt = state.requireExactNbt,
                                requireExactLore = state.requireExactLore
                            )
                        } else {
                            val itemInHand = pctx.player.inventory.itemInMainHand
                            if (itemInHand.type != material || itemInHand.amount < state.totalQuantity) {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(OrderMessages.ORDER_MUST_HOLD_ITEM))
                                return@runAsync null
                            }

                            val itemForOrder = itemInHand.clone().apply { amount = state.totalQuantity }
                            pctx.orderService.createSellOrder(
                                creator = pctx.player,
                                item = itemForOrder,
                                pricePerUnit = state.pricePerUnit,
                                duration = state.duration
                            )
                        }
                    },
                    onSuccess = { result ->
                        if (result != null) {
                            pctx.player.sendMessage(result.message)
                        }
                        if (result?.success == true) {
                            pctx.menuAPI.open(OrderBrowserMenu(pctx), pctx.player)
                        } else {
                            controls.close()
                        }
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun calculateListingFee(): Double {
        val amount = state.totalValue
        val feeConfig = pctx.config.orders.listingFee
        val fee = when (feeConfig.type.uppercase()) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }
        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }

    private fun createBackButton(): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BACK)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderBrowserMenu(pctx))
            }
        }
    }

    private fun createCloseButton(): VItem {
        return MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.Close
            }
        }
    }
}
