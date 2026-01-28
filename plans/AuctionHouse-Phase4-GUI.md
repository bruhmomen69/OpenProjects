# Phase 4: GUI Layer - Detailed Implementation Plan

This phase creates all the menu classes using the `menuapi` utilities.

---

## Step 1: Create Menu Utility Classes

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/MenuUtils.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.economy.EconomyProvider
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.time.Duration
import java.time.Instant

object MenuUtils {
    private val mm = MiniMessage.miniMessage()
    
    fun createBackgroundItem(): VItem {
        return VItem(XMaterial.GRAY_STAINED_GLASS_PANE) {
            name = Component.empty()
            hideAllFlags()
        }
    }
    
    fun createPreviousPageItem(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.ARROW) {
            name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
    }
    
    fun createNextPageItem(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.ARROW) {
            name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }
    }
    
    fun createCloseItem(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.BARRIER) {
            name = translationAPI.getComponentSync(GuiMessages.CLOSE)
        }
    }
    
    fun createBackItem(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = translationAPI.getComponentSync(GuiMessages.BACK)
        }
    }
    
    fun formatTimeLeft(endTime: Instant): String {
        val duration = Duration.between(Instant.now(), endTime)
        
        return when {
            duration.isNegative -> "Ended"
            duration.toDays() > 0 -> "${duration.toDays()}d ${duration.toHoursPart()}h"
            duration.toHours() > 0 -> "${duration.toHours()}h ${duration.toMinutesPart()}m"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ${duration.toSecondsPart()}s"
            else -> "${duration.toSeconds()}s"
        }
    }
    
    fun formatPrice(price: Double, economy: EconomyProvider): Component {
        return economy.format(price)
    }
}
```

---

## Step 2: Create Main Auction Browser Menu

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/AuctionHouseMenu.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class AuctionHouseMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter = AuctionFilter()
    private var currentSort = AuctionSort.ENDING_SOON
    
    fun open(page: Int = 0) {
        // Load auctions first
        val result = runBlocking {
            auctionService.getActiveAuctions(currentFilter, currentSort, page, 28)
        }
        
        val menu = menuAPI.paginated<Auction> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)
            
            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
            
            dataSource = result.items
            
            itemRenderer = { auction, _ ->
                createAuctionItem(auction)
            }
            
            // Background
            background = MenuUtils.createBackgroundItem()
            
            // Navigation - these are handled automatically by PaginatedMenu
            // but we can customize the items
            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }
            pageIndicatorRenderer = { current, total ->
                VItem(XMaterial.PAPER) {
                    name = Component.text("Page $current/$total")
                }
            }
            
            // Static control items
            staticItems[46] = createFilterButton()
            staticItems[47] = createSortButton()
            staticItems[48] = createSearchButton()
            staticItems[50] = createMyAuctionsButton()
            staticItems[51] = createCreateAuctionButton()
            staticItems[52] = createOrdersButton()
        }
        
        menuAPI.open(menu, player)
    }
    
    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        
        return VItem(material) {
            name = auction.itemDisplayName?.let { 
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            
            lore = buildList {
                // Seller
                add(translationAPI.getComponentSync(GuiMessages.LORE_SELLER) {
                    unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
                })
                
                // Price info based on auction type
                when (auction.auctionType) {
                    AuctionType.AUCTION -> {
                        add(translationAPI.getComponentSync(GuiMessages.LORE_PRICE_AUCTION) {
                            parsed("bid", "${auction.startPrice}") // Will be replaced with actual bid
                        })
                    }
                    AuctionType.BIN -> {
                        add(translationAPI.getComponentSync(GuiMessages.LORE_PRICE_BIN) {
                            parsed("price", "${auction.buyNowPrice}")
                        })
                    }
                    AuctionType.BOTH -> {
                        add(translationAPI.getComponentSync(GuiMessages.LORE_PRICE_AUCTION) {
                            parsed("bid", "${auction.startPrice}")
                        })
                        add(translationAPI.getComponentSync(GuiMessages.LORE_PRICE_BIN) {
                            parsed("price", "${auction.buyNowPrice}")
                        })
                    }
                }
                
                // Time left
                add(translationAPI.getComponentSync(GuiMessages.LORE_TIME_LEFT) {
                    unparsed("time", MenuUtils.formatTimeLeft(auction.endsAt))
                })
                
                // Click instructions
                add(Component.empty())
                if (auction.canBid()) {
                    add(translationAPI.getComponentSync(GuiMessages.LORE_CLICK_TO_BID))
                }
                if (auction.canBuyNow()) {
                    add(translationAPI.getComponentSync(GuiMessages.LORE_CLICK_TO_BUY))
                }
            }
            
            onClickRun { _, _ ->
                // Handle click - open auction details or place bid
                AuctionDetailsMenu(menuAPI, auctionService, translationAPI, player, auction).open()
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createFilterButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = translationAPI.getComponentSync(GuiMessages.FILTER_ALL)
            onClickRun { _, _ ->
                // Open filter menu - stays open
                ClickResult.ALLOW
            }
        }
    }
    
    private fun createSortButton(): VItem {
        return VItem(XMaterial.COMPASS) {
            name = translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            onClickRun { _, _ ->
                // Open sort menu - stays open
                ClickResult.ALLOW
            }
        }
    }
    
    private fun createSearchButton(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
        }
    }
    
    private fun createMyAuctionsButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            onClickRun { _, _ ->
                MyAuctionsMenu(menuAPI, auctionService, translationAPI, player).open()
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createCreateAuctionButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_AUCTION)
            onClickRun { _, _ ->
                AuctionCreateMenu(menuAPI, auctionService, translationAPI, player).open()
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createOrdersButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
            onClickRun { _, _ ->
                // Open order browser
                ClickResult.CLOSE
            }
        }
    }
}
```

---

## Step 3: Create Auction Details Menu

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/AuctionDetailsMenu.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class AuctionDetailsMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val player: Player,
    private val auction: Auction
) {
    private val mm = MiniMessage.miniMessage()
    
    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)
            
            background = MenuUtils.createBackgroundItem()
            
            // Display the auction item
            item(13, createAuctionDisplayItem())
            
            // Bid button (if applicable)
            if (auction.canBid()) {
                item(29, createBidButton())
            }
            
            // Buy Now button (if applicable)
            if (auction.canBuyNow()) {
                item(33, createBuyNowButton())
            }
            
            // Back button
            item(36, MenuUtils.createBackItem(translationAPI)) {
                onClickRun { _, _ ->
                    // Go back to main menu
                    AuctionHouseMenu(menuAPI, auctionService, translationAPI, player).open()
                    ClickResult.CLOSE
                }
            }
            
            // Close button
            item(44, MenuUtils.createCloseItem(translationAPI)) {
                onClickRun { _, _ ->
                    ClickResult.CLOSE
                }
            }
        }
        
        menuAPI.open(menu, player)
    }
    
    private fun createAuctionDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        
        return VItem(material) {
            name = auction.itemDisplayName?.let { 
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            
            lore = buildList {
                add(Component.empty())
                add(mm.deserialize("<gray>Seller: <white>${if (auction.isAnonymous) "Anonymous" else auction.sellerName}"))
                
                if (auction.auctionType == AuctionType.AUCTION || auction.auctionType == AuctionType.BOTH) {
                    add(mm.deserialize("<yellow>Current Bid: <gold>${auction.startPrice}"))
                }
                
                auction.buyNowPrice?.let {
                    add(mm.deserialize("<green>Buy Now: <gold>$it"))
                }
                
                add(mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeLeft(auction.endsAt)}"))
                add(mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
                add(mm.deserialize("<gray>Views: <white>${auction.viewCount}"))
            }
        }
    }
    
    private fun createBidButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = mm.deserialize("<green>Place Bid")
            lore = listOf(
                mm.deserialize("<gray>Click to place a bid"),
                mm.deserialize("<gray>Minimum increment: ${auction.minIncrement}")
            )
            
            onClickRun { _, _ ->
                // Use AnvilInput for bid amount
                menuAPI.launch {
                    val result = menuAPI.promptDouble(player, "Enter Bid Amount", "0", 0.0, Double.MAX_VALUE)
                    result?.let { amount ->
                        val bidResult = auctionService.placeBid(player, auction.id, amount)
                        player.sendMessage(bidResult.message)
                    }
                }
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createBuyNowButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Buy Now")
            lore = listOf(
                mm.deserialize("<gray>Price: <gold>${auction.buyNowPrice}"),
                mm.deserialize("<gray>Click to purchase instantly")
            )
            
            onClickRun { _, _ ->
                // Confirm and buy
                runBlocking {
                    val result = auctionService.buyNow(player, auction.id)
                    player.sendMessage(result.message)
                }
                ClickResult.CLOSE
            }
        }
    }
}
```

---

## Step 4: Create Auction Creation Menu using FormInput

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/AuctionCreateForm.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.zchat.utils.menuapi.BooleanInput
import bruh.zchat.utils.menuapi.EnumInput
import bruh.zchat.utils.menuapi.FormField
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.NumberInput
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration

/**
 * Form data class for creating an auction.
 * Uses MenuAPI's FormInput system for data collection.
 */
data class AuctionCreateData(
    @FormField(index = 0, label = "Auction Type", description = "Choose auction type")
    @EnumInput
    var auctionType: AuctionType = AuctionType.BOTH,
    
    @FormField(index = 1, label = "Start Price", description = "Minimum bid price")
    @NumberInput(min = 1.0, step = 1.0)
    var startPrice: Double = 100.0,
    
    @FormField(index = 2, label = "BIN Price", description = "Buy It Now price (0 for none)")
    @NumberInput(min = 0.0, step = 1.0)
    var binPrice: Double = 0.0,
    
    @FormField(index = 3, label = "Duration (Hours)", description = "How long the auction runs")
    @NumberInput(min = 1.0, max = 168.0, step = 1.0)
    var durationHours: Int = 24,
    
    @FormField(index = 4, label = "Anonymous", description = "Hide your identity")
    @BooleanInput(trueLabel = "Yes", falseLabel = "No", trueMaterial = "LIME_DYE", falseMaterial = "GRAY_DYE")
    var anonymous: Boolean = false
)

class AuctionCreateForm(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {
    /**
     * Opens the auction creation form and returns the collected data.
     */
    suspend fun open(player: Player, item: ItemStack): AuctionCreateData? {
        val formData = AuctionCreateData()
        
        val result = menuAPI.getFormData(formData) {
            title = "Create Auction"
            // Form fields are automatically collected based on annotations
        }.open(player)
        
        return when (result) {
            is bruh.zchat.utils.menuapi.FormResult.Success -> result.data
            else -> null
        }
    }
}
```

### Alternative: Traditional Menu-based Creation

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/AuctionCreateMenu.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.time.Duration

class AuctionCreateMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var auctionItem = player.inventory.itemInMainHand
    private var startPrice = 100.0
    private var binPrice: Double? = null
    private var duration = Duration.ofHours(24)
    private var anonymous = false
    private var auctionType = AuctionType.BOTH
    
    fun open() {
        // Check if player is holding an item
        if (auctionItem.type.isAir) {
            player.sendMessage(mm.deserialize("<red>You must hold an item to sell!"))
            return
        }
        
        refreshMenu()
    }
    
    private fun refreshMenu() {
        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.CREATE_AUCTION_TITLE)
            
            background = MenuUtils.createBackgroundItem()
            
            // Item slot
            item(13, createItemDisplay())
            
            // Auction type selector
            item(29, createTypeButton())
            
            // Start price
            item(30, createStartPriceButton())
            
            // BIN price
            item(31, createBinPriceButton())
            
            // Duration
            item(32, createDurationButton())
            
            // Anonymous toggle
            item(33, createAnonymousButton())
            
            // Confirm button
            item(38, createConfirmButton())
            
            // Cancel button
            item(42, createCancelButton())
        }
        
        menuAPI.open(menu, player)
    }
    
    private fun createItemDisplay(): VItem {
        return VItem.fromMaterial(auctionItem.type) {
            name = auctionItem.itemMeta?.displayName() 
                ?: Component.text(auctionItem.type.name.replace("_", " "))
        }
    }
    
    private fun createTypeButton(): VItem {
        val (material, name) = when (auctionType) {
            AuctionType.AUCTION -> XMaterial.GOLD_INGOT to "Auction Only"
            AuctionType.BIN -> XMaterial.EMERALD to "BIN Only"
            AuctionType.BOTH -> XMaterial.DIAMOND to "Auction + BIN"
        }
        
        return VItem(material) {
            name = mm.deserialize("<yellow>Type: <white>$name")
            lore = listOf(mm.deserialize("<gray>Click to change"))
            
            onClickRun { _, _ ->
                auctionType = when (auctionType) {
                    AuctionType.AUCTION -> AuctionType.BIN
                    AuctionType.BIN -> AuctionType.BOTH
                    AuctionType.BOTH -> AuctionType.AUCTION
                }
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }
    
    private fun createStartPriceButton(): VItem {
        return VItem(XMaterial.GOLD_NUGGET) {
            name = mm.deserialize("<yellow>Start Price: <gold>$startPrice")
            lore = listOf(mm.deserialize("<gray>Click to change"))
            
            onClickRun { _, _ ->
                menuAPI.launch {
                    val newPrice = menuAPI.promptDouble(player, "Enter Start Price", startPrice.toString(), 0.0, Double.MAX_VALUE)
                    newPrice?.let { startPrice = it }
                    refreshMenu()
                }
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createBinPriceButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = if (binPrice != null) {
                mm.deserialize("<green>BIN Price: <gold>$binPrice")
            } else {
                mm.deserialize("<gray>BIN Price: <red>Not Set")
            }
            lore = listOf(
                mm.deserialize("<gray>Click to set"),
                mm.deserialize("<gray>Right-click to clear")
            )
            
            onClickRun { ctx, _ ->
                if (ctx.isRightClick) {
                    binPrice = null
                    refreshMenu()
                    ClickResult.ALLOW
                } else {
                    menuAPI.launch {
                        val newPrice = menuAPI.promptDouble(player, "Enter BIN Price", binPrice?.toString() ?: "", 0.0, Double.MAX_VALUE)
                        newPrice?.let { binPrice = it }
                        refreshMenu()
                    }
                    ClickResult.CLOSE
                }
            }
        }
    }
    
    private fun createDurationButton(): VItem {
        return VItem(XMaterial.CLOCK) {
            name = mm.deserialize("<yellow>Duration: <white>${duration.toHours()}h")
            lore = listOf(mm.deserialize("<gray>Click to change"))
            
            onClickRun { _, _ ->
                menuAPI.launch {
                    val hours = menuAPI.promptInt(player, "Enter Duration (hours)", duration.toHours().toString(), 1, 168)
                    hours?.let { duration = Duration.ofHours(it.toLong()) }
                    refreshMenu()
                }
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createAnonymousButton(): VItem {
        val material = if (anonymous) XMaterial.LIME_DYE else XMaterial.GRAY_DYE
        return VItem(material) {
            name = mm.deserialize("<yellow>Anonymous: <white>${if (anonymous) "Yes" else "No"}")
            lore = listOf(mm.deserialize("<gray>Click to toggle"))
            
            onClickRun { _, _ ->
                anonymous = !anonymous
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }
    
    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.LIME_WOOL) {
            name = mm.deserialize("<green>Confirm")
            lore = listOf(mm.deserialize("<gray>Click to create auction"))
            
            onClickRun { _, _ ->
                runBlocking {
                    val actualBinPrice = if (auctionType == AuctionType.BOTH || auctionType == AuctionType.BIN) binPrice else null
                    val result = auctionService.createAuction(
                        player, auctionItem, auctionType, startPrice, actualBinPrice, duration, anonymous
                    )
                    player.sendMessage(result.message)
                    
                    if (result.success) {
                        player.inventory.setItemInMainHand(null)
                    }
                }
                ClickResult.CLOSE
            }
        }
    }
    
    private fun createCancelButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = mm.deserialize("<red>Cancel")
            lore = listOf(mm.deserialize("<gray>Click to cancel"))
            
            onClickRun { _, _ ->
                ClickResult.CLOSE
            }
        }
    }
}
```

---

## Step 5: Create My Auctions Menu

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/MyAuctionsMenu.kt` (Create)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class MyAuctionsMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    
    fun open() {
        val auctions = runBlocking {
            auctionService.getPlayerAuctions(player.uniqueId, null)
        }
        
        val menu = menuAPI.paginated<Auction> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            
            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
            
            dataSource = auctions
            
            itemRenderer = { auction, _ ->
                createMyAuctionItem(auction)
            }
            
            background = MenuUtils.createBackgroundItem()
            
            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }
            
            // Back button
            staticItems[49] = MenuUtils.createBackItem(translationAPI).apply {
                onClickRun { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, translationAPI, player).open()
                    ClickResult.CLOSE
                }
            }
        }
        
        menuAPI.open(menu, player)
    }
    
    private fun createMyAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        
        return VItem(material) {
            name = auction.itemDisplayName?.let { 
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            
            lore = buildList {
                add(mm.deserialize("<gray>Status: <white>${auction.status}"))
                
                if (auction.status == AuctionStatus.ACTIVE) {
                    add(mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeLeft(auction.endsAt)}"))
                    add(Component.empty())
                    add(mm.deserialize("<red>Click to cancel"))
                }
            }
            
            onClickRun { _, _ ->
                if (auction.status == AuctionStatus.ACTIVE) {
                    runBlocking {
                        val result = auctionService.cancelAuction(player, auction.id)
                        when (result) {
                            is bruh.auctionhouse.service.ServiceResult.Success -> {
                                player.sendMessage(translationAPI.getComponentSync(
                                    bruh.auctionhouse.translations.AuctionMessages.AUCTION_CANCELLED
                                ))
                            }
                            is bruh.auctionhouse.service.ServiceResult.Failure -> {
                                player.sendMessage(result.message)
                            }
                        }
                    }
                    // Refresh menu
                    open()
                }
                ClickResult.CLOSE
            }
        }
    }
}
```

---

## Step 6: Create MenuTree-based Navigation (Alternative Architecture)

For a more structured navigation system, consider using MenuTree:

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/gui/AuctionHouseNavigator.kt` (Create - Optional)
```kotlin
package bruh.auctionhouse.gui

import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.MenuTreeResult
import bruh.zchat.utils.menuapi.menuTree
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Alternative navigation using MenuTree for hierarchical menu structure.
 * This provides automatic back/close buttons and navigation history.
 */
class AuctionHouseNavigator(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {
    suspend fun open(player: Player): MenuTreeResult {
        return menuAPI.menuTree {
            title = Component.text("Auction House")
            background = MenuUtils.createBackgroundItem()
            
            // Browse Auctions
            submenu("browse", "Browse Auctions", XMaterial.CHEST) {
                dynamicItems { player ->
                    // Load and create auction items dynamically
                    val auctions = auctionService.getActiveAuctions(
                        bruh.auctionhouse.model.AuctionFilter(),
                        bruh.auctionhouse.model.AuctionSort.ENDING_SOON,
                        0, 28
                    )
                    
                    auctions.items.map { auction ->
                        action(
                            id = "auction_${auction.id}",
                            title = auction.itemDisplayName ?: auction.itemMaterial,
                            material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
                        ) { p ->
                            // Handle auction click
                            AuctionDetailsMenu(menuAPI, auctionService, translationAPI, p, auction).open()
                        }
                    }
                }
            }
            
            // My Auctions
            action("my_auctions", "My Auctions", XMaterial.ENDER_CHEST) { p ->
                MyAuctionsMenu(menuAPI, auctionService, translationAPI, p).open()
            }
            
            // Create Auction
            action("create", "Create Auction", XMaterial.EMERALD) { p ->
                AuctionCreateMenu(menuAPI, auctionService, translationAPI, p).open()
            }
            
            // Orders
            submenu("orders", "Orders", XMaterial.BOOK) {
                action("browse_orders", "Browse Orders", XMaterial.PAPER) { p ->
                    // Open order browser
                }
                
                action("my_orders", "My Orders", XMaterial.BOOKSHELF) { p ->
                    // Open my orders
                }
                
                action("create_order", "Create Order", XMaterial.WRITABLE_BOOK) { p ->
                    // Open create order
                }
            }
            
            // Expired Items
            action("expired", "Expired Items", XMaterial.BONE) { p ->
                ExpiredItemsMenu(menuAPI, auctionService, translationAPI, p).open()
            }
            
        }.open(player)
    }
}
```

---

## Step 7: Update Command Classes to Pass MenuAPI

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/commands/AuctionHouseCommands.kt` (Update)

Add `MenuAPI` to constructor and update menu openings:

```kotlin
@Command("ah", "auctionhouse")
class AuctionHouseCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI  // Add this
) {
    @Subcommand
    fun openMenu(player: Player) {
        if (!plugin.isEnabledFlag) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        AuctionHouseMenu(menuAPI, auctionService, translationAPI, player).open()
    }
    // ... rest of the class
}
```

---

## Step 8: Update Main Plugin Class

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/AuctionHousePlugin.kt` (Update)

Ensure MenuAPI is initialized and passed to commands:

```kotlin
class AuctionHousePlugin : SuspendingJavaPlugin() {
    // ... existing fields ...
    
    lateinit var menuAPI: MenuAPI
        private set
    
    override suspend fun onEnableAsync() {
        // ... existing initialization ...
        
        // Initialize MenuAPI
        menuAPI = MenuAPI(this)
        
        // Register commands with MenuAPI
        registerCommands()
        
        // ... rest of initialization ...
    }
    
    private fun registerCommands() {
        val lamp = BukkitLamp.builder(this).build()
        lamp.register(AuctionHouseCommands(this, config, auctionService, translationAPI, menuAPI))
        lamp.register(OrderCommands(this, config, orderService, translationAPI, menuAPI))
        lamp.register(AuctionAdminCommands(this, auctionService, translationAPI))
    }
    
    override suspend fun onDisableAsync() {
        // Close MenuAPI
        if (::menuAPI.isInitialized) {
            menuAPI.close()
        }
        
        // ... rest of cleanup ...
    }
}
```

---

## Phase 4 Completion Checklist

After completing Phase 4, you should have:

- [ ] `MenuUtils.kt` - Utility functions for menu creation
- [ ] `AuctionHouseMenu.kt` - Main paginated auction browser
- [ ] `AuctionDetailsMenu.kt` - Single auction view with bid/buy actions
- [ ] `AuctionCreateMenu.kt` - Create auction with form inputs (or `AuctionCreateForm.kt` using FormInput)
- [ ] `MyAuctionsMenu.kt` - View and manage own auctions
- [ ] `ExpiredItemsMenu.kt` - Retrieve expired items
- [ ] `AuctionHouseNavigator.kt` (Optional) - MenuTree-based navigation
- [ ] Updated `AuctionHouseCommands.kt` with MenuAPI parameter
- [ ] Updated `AuctionHousePlugin.kt` with MenuAPI initialization

## Key API Patterns Used

1. **Menu Creation**: Use `menuAPI.simple { }` and `menuAPI.paginated<T> { }`
2. **VItem Clicks**: Use `onClickRun { ctx, controls -> }` inside the VItem builder
3. **Click Results**: Return `ClickResult.CLOSE`, `ClickResult.ALLOW`, `ClickResult.REFRESH`, or `ClickResult.DENY`
4. **Opening Menus**: Use `menuAPI.open(menu, player)` not `menu.open(player)`
5. **Form Input**: Use `menuAPI.getFormData(dataClass) { }.open(player)` for form-based input
6. **Anvil Input**: Use `menuAPI.promptText()`, `menuAPI.promptInt()`, `menuAPI.promptDouble()`
7. **MenuTree**: Use `menuAPI.menuTree { }.open(player)` for hierarchical navigation

## Build Verification

```bash
./gradlew :AuctionHouse:build
./gradlew :AuctionHouse:shadowJar
```
