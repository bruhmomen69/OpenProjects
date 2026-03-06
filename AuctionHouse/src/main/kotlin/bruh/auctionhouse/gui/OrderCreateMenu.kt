package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.menuapi.promptInt
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

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
 * 25 - Item decrement (buy orders only)
 * 26 - Items display (click to edit) (buy orders only)
 * 27 - Item increment (buy orders only)
 * 30 - Price per unit
 * 31 - Duration
 * 32 - Allow partial toggle (buy orders only)
 * 40 - Confirm
 * 45 - Back
 * 53 - Close
 */
class OrderCreateMenu(
    private val menuAPI: MenuAPI,
    private val orderService: OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val economy: EconomyProvider,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var state = OrderCreateState()
    private var orderType = bruh.auctionhouse.model.OrderType.BUY_ORDER
    private var onCloseCallback: (() -> Unit)? = null

    fun open(onCloseCallback: (() -> Unit)? = null) {
        this.onCloseCallback = onCloseCallback
        // For sell orders, get item from player's hand
        if (orderType == bruh.auctionhouse.model.OrderType.SELL_ORDER) {
            val itemInHand = player.inventory.itemInMainHand
            if (!itemInHand.type.isAir) {
                state = state.copy(
                    selectedMaterial = itemInHand.type,
                    stacks = itemInHand.amount / 64,
                    items = itemInHand.amount % 64
                )
            }
        }
        refreshMenu()
    }

    private fun refreshMenu() {
        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.CREATE_ORDER_TITLE)

            background = MenuUtils.backgroundItem()

            item(13, createMaterialDisplay())

            // Order type toggle
            item(22, createOrderTypeToggle())

            // Buy order controls (only show for buy orders)
            if (orderType == bruh.auctionhouse.model.OrderType.BUY_ORDER) {
                item(19, createStackDecrement())
                item(20, createStacksDisplay())
                item(21, createStackIncrement())

                item(25, createItemDecrement())
                item(26, createItemsDisplay())
                item(27, createItemIncrement())

                item(32, createPartialToggle())
                
                // NBT/Lore matching options (row 4)
                item(38, createNbtMatchToggle())
                item(41, createLoreMatchToggle())
            }

            item(30, createPriceButton())
            item(31, createDurationButton())

            item(40, createConfirmButton())

            item(45, createBackButton())
            item(53, createCloseButton())
        }

        menuAPI.open(menu, player)
    }

    private fun createOrderTypeToggle(): VItem {
        val isBuyOrder = orderType == bruh.auctionhouse.model.OrderType.BUY_ORDER
        return VItem(if (isBuyOrder) XMaterial.DIAMOND else XMaterial.GOLD_INGOT) {
            name = mm.deserialize(if (isBuyOrder) "<blue>Buy Order" else "<yellow>Sell Order")
            val loreList = mutableListOf<Component>()
            if (isBuyOrder) {
                loreList.add(mm.deserialize("<gray>Buying items from other players"))
                loreList.add(mm.deserialize("<gray>You will pay when someone sells to you"))
            } else {
                loreList.add(mm.deserialize("<gray>Selling items to other players"))
                loreList.add(mm.deserialize("<red>You must hold the item to sell"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                orderType = if (isBuyOrder) {
                    bruh.auctionhouse.model.OrderType.SELL_ORDER
                } else {
                    bruh.auctionhouse.model.OrderType.BUY_ORDER
                }
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createMaterialDisplay(): VItem {
        return if (state.selectedMaterial != null) {
            val xMaterial = XMaterial.matchXMaterial(state.selectedMaterial!!)
            VItem(xMaterial) {
                name = Component.text(state.selectedMaterial!!.name.replace("_", " "))
                lore = mutableListOf(
                    mm.deserialize("<gray>Total: <white>${state.totalQuantity} items"),
                    mm.deserialize("<gray>${state.stacks} stacks + ${state.items} items"),
                    Component.empty(),
                    mm.deserialize("<yellow>Click to change material")
                )
                hideAllFlags()

                onClick { _, _ ->
                    openMaterialPicker()
                    ClickResult.CLOSE
                }
            }
        } else {
            VItem(XMaterial.BARRIER) {
                name = mm.deserialize("<red>No material selected")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to select a material")
                )
                hideAllFlags()

                onClick { _, _ ->
                    openMaterialPicker()
                    ClickResult.CLOSE
                }
            }
        }
    }

    private fun openMaterialPicker() {
        val picker = MaterialPickerMenu(menuAPI, config, translationAPI) { material ->
            state = state.copy(selectedMaterial = material.parseMaterial())
            refreshMenu()
        }
        picker.openForPlayer(player)
    }

    private fun createStackDecrement(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = mm.deserialize("<red>-1 Stack")
            lore = mutableListOf(
                mm.deserialize("<gray>Remove 64 items"),
                mm.deserialize("<yellow>Shift-click: -10 stacks")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(-64, isShift)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createStacksDisplay(): VItem {
        return VItem(XMaterial.CHEST) {
            name = mm.deserialize("<yellow>${state.stacks} Stacks")
            lore = mutableListOf(
                mm.deserialize("<gray>${state.stacks * 64} items from stacks"),
                mm.deserialize("<gray>Click to set stacks directly"),
                Component.empty(),
                mm.deserialize("<yellow>Total: ${state.totalQuantity} items")
            )
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptInt(
                        player,
                        "Enter number of stacks",
                        state.stacks,
                        0,
                        OrderCreateState.MAX_STACKS
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            state = state.copy(stacks = result.value)
                            refreshMenu()
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createStackIncrement(): VItem {
        return VItem(XMaterial.GREEN_WOOL) {
            name = mm.deserialize("<green>+1 Stack")
            lore = mutableListOf(
                mm.deserialize("<gray>Add 64 items"),
                mm.deserialize("<yellow>Shift-click: +10 stacks")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(64, isShift)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createItemDecrement(): VItem {
        return VItem(XMaterial.REDSTONE) {
            name = mm.deserialize("<red>-1 Item")
            lore = mutableListOf(
                mm.deserialize("<gray>Remove 1 item"),
                mm.deserialize("<yellow>Shift-click: -10 items")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(-1, isShift)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createItemsDisplay(): VItem {
        return VItem(XMaterial.PAPER) {
            name = mm.deserialize("<yellow>${state.items} Items")
            lore = mutableListOf(
                mm.deserialize("<gray>Individual items (0-63)"),
                mm.deserialize("<gray>Beyond full stacks"),
                mm.deserialize("<gray>Click to set items directly"),
                Component.empty(),
                mm.deserialize("<yellow>Auto-converts at 64")
            )
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptInt(
                        player,
                        "Enter number of items (0-63)",
                        state.items,
                        0,
                        63
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            state = state.withItems(result.value)
                            refreshMenu()
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createItemIncrement(): VItem {
        return VItem(XMaterial.SLIME_BALL) {
            name = mm.deserialize("<green>+1 Item")
            lore = mutableListOf(
                mm.deserialize("<gray>Add 1 item"),
                mm.deserialize("<yellow>Shift-click: +10 items"),
                Component.empty(),
                mm.deserialize("<gray>Auto-converts to stack at 64")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                val isShift = ctx.isShiftClick
                state = state.withQuantityDelta(1, isShift)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createPriceButton(): VItem {
        val totalValue = state.totalValue
        return VItem(XMaterial.GOLD_NUGGET) {
            name = mm.deserialize("<yellow>Price: <gold>${MenuUtils.formatPrice(state.pricePerUnit, economy)}")
            lore = mutableListOf(
                mm.deserialize("<gray>Per unit price"),
                Component.empty(),
                mm.deserialize("<white>Total: ${MenuUtils.formatPrice(totalValue, economy)}"),
                mm.deserialize("<gray>Click to change price"),
                mm.deserialize("<gray>Right-click to set total value")
            )
            hideAllFlags()

            onClick { ctx, _ ->
                if (ctx.isRightClick) {
                    promptTotalValue()
                } else {
                    promptPricePerUnit()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun promptPricePerUnit() {
        runBlocking {
            val result = menuAPI.promptDouble(
                player,
                "Enter price per item",
                state.pricePerUnit,
                config.orders.minPricePerUnit,
                config.orders.maxPricePerUnit
            )
            when (result) {
                is AnvilInputResult.Success -> {
                    state = state.copy(pricePerUnit = result.value)
                    refreshMenu()
                }
                is AnvilInputResult.Cancelled -> {}
            }
        }
    }

    private fun promptTotalValue() {
        runBlocking {
            val result = menuAPI.promptDouble(
                player,
                "Enter total value",
                state.totalValue,
                config.orders.minPricePerUnit,
                config.orders.maxPricePerUnit * state.totalQuantity.coerceAtLeast(1)
            )
            when (result) {
                is AnvilInputResult.Success -> {
                    val total = result.value
                    if (state.totalQuantity > 0) {
                        state = state.copy(pricePerUnit = total / state.totalQuantity)
                    }
                    refreshMenu()
                }
                is AnvilInputResult.Cancelled -> {}
            }
        }
    }

    private fun createDurationButton(): VItem {
        val durationHours = state.duration.toHours()
        return VItem(XMaterial.CLOCK) {
            name = mm.deserialize("<yellow>Duration: <white>${durationHours}h")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to change duration"),
                mm.deserialize("<gray>Shift-click for shorter"),
                Component.empty(),
                mm.deserialize("<gray>Expires: ${formatExpiryTime()}")
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
                refreshMenu()
                ClickResult.ALLOW
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
            name = mm.deserialize("<yellow>Allow Partial: <white>${if (state.allowPartial) "Yes" else "No"}")
            lore = mutableListOf(
                mm.deserialize("<gray>Allow orders to be filled partially"),
                mm.deserialize("<gray>Click to toggle"),
                Component.empty(),
                if (state.allowPartial) {
                    mm.deserialize("<gray>Min fill: ${state.minFillQuantity ?: "Not set"}")
                } else {
                    mm.deserialize("<gray>Full quantity required")
                }
            )
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(
                    allowPartial = !state.allowPartial,
                    minFillQuantity = if (!state.allowPartial) null else state.minFillQuantity
                )
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createNbtMatchToggle(): VItem {
        return VItem(if (state.requireExactNbt) XMaterial.COMMAND_BLOCK else XMaterial.CRAFTING_TABLE) {
            name = mm.deserialize("<yellow>Match NBT: <white>${if (state.requireExactNbt) "Yes" else "No"}")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Require exact NBT data match"))
            loreList.add(mm.deserialize("<gray>Enchants, custom model data, etc."))
            if (state.requireExactNbt) {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Only matching NBT will fulfill"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(requireExactNbt = !state.requireExactNbt)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createLoreMatchToggle(): VItem {
        return VItem(if (state.requireExactLore) XMaterial.WRITABLE_BOOK else XMaterial.BOOK) {
            name = mm.deserialize("<yellow>Match Lore: <white>${if (state.requireExactLore) "Yes" else "No"}")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Require exact lore match"))
            loreList.add(mm.deserialize("<gray>All lore lines must match"))
            if (state.requireExactLore) {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Only matching lore will fulfill"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to toggle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                state = state.copy(requireExactLore = !state.requireExactLore)
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createConfirmButton(): VItem {
        val isBuyOrder = orderType == bruh.auctionhouse.model.OrderType.BUY_ORDER
        val canConfirm = if (isBuyOrder) state.isValid() else state.selectedMaterial != null && state.totalQuantity > 0
        val listingFee = calculateListingFee()

        return VItem(if (canConfirm) XMaterial.EMERALD_BLOCK else XMaterial.REDSTONE_BLOCK) {
            name = if (canConfirm) {
                mm.deserialize("<green>Create ${if (isBuyOrder) "Buy" else "Sell"} Order")
            } else {
                mm.deserialize("<red>Cannot Create Order")
            }
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Listing Fee: <gold>${MenuUtils.formatPrice(listingFee, economy)}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<white>Summary:"))
            loreList.add(mm.deserialize("<gray>Type: ${if (isBuyOrder) "<blue>Buy Order" else "<yellow>Sell Order"}"))
            loreList.add(mm.deserialize("<gray>Item: ${state.selectedMaterial?.name ?: "None"}"))
            loreList.add(mm.deserialize("<gray>Quantity: ${state.totalQuantity} items"))
            loreList.add(mm.deserialize("<gray>Price: ${MenuUtils.formatPrice(state.pricePerUnit, economy)}/item"))
            loreList.add(mm.deserialize("<gray>Total: ${MenuUtils.formatPrice(state.totalValue, economy)}"))
            loreList.add(Component.empty())
            if (canConfirm) {
                if (isBuyOrder) {
                    loreList.add(mm.deserialize("<green>Click to create buy order"))
                } else {
                    loreList.add(mm.deserialize("<green>Click to create sell order"))
                    loreList.add(mm.deserialize("<red>Item will be removed from your inventory!"))
                }
            } else {
                loreList.add(mm.deserialize("<red>Select a material and quantity"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (canConfirm) {
                    createOrder()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun calculateListingFee(): Double {
        val amount = state.totalValue
        val feeConfig = config.orders.listingFee
        val fee = when (feeConfig.type.uppercase()) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }
        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }

    private fun createOrder() {
        runBlocking {
            val material = state.selectedMaterial ?: return@runBlocking

            val result = if (orderType == bruh.auctionhouse.model.OrderType.BUY_ORDER) {
                // Create buy order with NBT/lore matching options
                orderService.createBuyOrder(
                    creator = player,
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
                // Create sell order - need item from inventory
                val itemInHand = player.inventory.itemInMainHand
                if (itemInHand.type != material || itemInHand.amount < state.totalQuantity) {
                    player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_MUST_HOLD_ITEM))
                    return@runBlocking
                }

                val itemForOrder = itemInHand.clone().apply { amount = state.totalQuantity }
                orderService.createSellOrder(
                    creator = player,
                    item = itemForOrder,
                    pricePerUnit = state.pricePerUnit,
                    duration = state.duration
                )
            }

            player.sendMessage(result.message)

            if (result.success) {
                onCloseCallback?.invoke()
            }
        }
    }

    private fun createBackButton(): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = translationAPI.getComponentSync(GuiMessages.BACK)
            hideAllFlags()

            onClick { _, _ ->
                onCloseCallback?.invoke()
                ClickResult.CLOSE
            }
        }
    }

    private fun createCloseButton(): VItem {
        return MenuUtils.closeButton(translationAPI).apply {
            onClick { _, _ ->
                ClickResult.CLOSE
            }
        }
    }
}
