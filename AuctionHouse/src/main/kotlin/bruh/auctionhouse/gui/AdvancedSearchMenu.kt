package bruh.auctionhouse.gui

import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDoubleAsync
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.time.Duration

/**
 * Advanced search menu with comprehensive filtering options.
 */
class AdvancedSearchMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    private var searchQuery: String? = PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).searchQuery
    private var sellerName: String? = PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).sellerName
    private var material: XMaterial? = PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).material?.let { XMaterial.matchXMaterial(it).orElse(null) }
    private var minPrice: Double? = PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).minPrice
    private var maxPrice: Double? = PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).maxPrice
    private var auctionType by menuState<AuctionType?>(PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).auctionType)
    private var endingWithin by menuState<Duration?>(PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).endingWithin)
    private var sortBy by menuState(PlayerStateManager.getAuctionFilter(pctx.player.uniqueId).sortBy)

    init {
        rows = 6
        title = pctx.mm.deserialize("<yellow>Advanced Search")
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        // Row 1: Search Query, Seller Name, Material Selector
        item(10, createSearchQueryItem())
        item(13, createSellerNameItem())
        item(16, createMaterialSelectorItem())

        // Row 2: Min Price, Max Price, Price Range Preset
        item(19, createMinPriceItem())
        item(22, createMaxPriceItem())
        item(25, createPricePresetItem())

        // Row 3: Auction Type Filter, Ending Soon Filter, Sort Options
        item(28, createAuctionTypeFilterItem())
        item(31, createEndingSoonFilterItem())
        item(34, createSortOptionsItem())

        // Row 4: Active Filters Display, Clear All Filters
        item(40, createActiveFiltersDisplayItem())
        item(42, createClearAllButton())

        // Row 5: Back, Apply Search, Close
        item(45, createBackButton())
        item(49, createApplySearchButton())
        item(53, createCloseButton())
    }

    private fun createSearchQueryItem(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = pctx.mm.deserialize("<yellow>Search Query")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Search by item name or material"))
            loreList.add(pctx.mm.deserialize("<gray>Partial matches supported"))
            loreList.add(pctx.mm.deserialize("<gray>Case-insensitive"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>${searchQuery ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to set"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    searchQuery = null
                    ClickResult.Refresh
                } else {
                    pctx.menuAPI.promptTextAsync(
                        pctx.player,
                        "Search Query",
                        searchQuery ?: ""
                    ).thenAccept { result ->
                        when (result) {
                            is AnvilInputResult.Success -> {
                                searchQuery = result.value.takeIf { it.isNotBlank() }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                            pctx.menuAPI.open(this@AdvancedSearchMenu, pctx.player)
                        })
                    }
                    ClickResult.Deny
                }
            }
        }
    }

    private fun createSellerNameItem(): VItem {
        return VItem(XMaterial.PLAYER_HEAD) {
            name = pctx.mm.deserialize("<yellow>Seller Name")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Find auctions from specific players"))
            loreList.add(pctx.mm.deserialize("<gray>Enter exact player name"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>${sellerName ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to set"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    sellerName = null
                    ClickResult.Refresh
                } else {
                    pctx.menuAPI.promptTextAsync(
                        pctx.player,
                        "Seller Name",
                        sellerName ?: ""
                    ).thenAccept { result ->
                        when (result) {
                            is AnvilInputResult.Success -> {
                                sellerName = result.value.takeIf { it.isNotBlank() }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                            pctx.menuAPI.open(this@AdvancedSearchMenu, pctx.player)
                        })
                    }
                    ClickResult.Deny
                }
            }
        }
    }

    private fun createMaterialSelectorItem(): VItem {
        val displayMaterial = material ?: XMaterial.HOPPER

        return VItem(displayMaterial) {
            name = pctx.mm.deserialize("<yellow>Material Filter")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Filter by specific item material"))
            loreList.add(pctx.mm.deserialize("<gray>Opens material picker menu"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>${material?.name ?: "Any"}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to select"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    this@AdvancedSearchMenu.material = null
                    ClickResult.Refresh
                } else {
                    ClickResult.SwitchMenu(
                        MaterialPickerMenu(pctx) { selectedMaterial ->
                            this@AdvancedSearchMenu.material = selectedMaterial
                            this@AdvancedSearchMenu
                        }
                    )
                }
            }
        }
    }

    private fun createMinPriceItem(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = pctx.mm.deserialize("<yellow>Minimum Price")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Set minimum price filter"))
            loreList.add(pctx.mm.deserialize("<gray>Only show auctions >= this price"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>${minPrice?.let { MenuUtils.formatPrice(it, pctx.economy) } ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to set"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    minPrice = null
                    ClickResult.Refresh
                } else {
                    pctx.menuAPI.promptDoubleAsync(
                        pctx.player,
                        "Minimum Price",
                        minPrice,
                        0.0,
                        Double.MAX_VALUE
                    ).thenAccept { result ->
                        when (result) {
                            is AnvilInputResult.Success -> {
                                minPrice = result.value.takeIf { it > 0 }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                            pctx.menuAPI.open(this@AdvancedSearchMenu, pctx.player)
                        })
                    }
                    ClickResult.Deny
                }
            }
        }
    }

    private fun createMaxPriceItem(): VItem {
        return VItem(XMaterial.GOLD_BLOCK) {
            name = pctx.mm.deserialize("<yellow>Maximum Price")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Set maximum price filter"))
            loreList.add(pctx.mm.deserialize("<gray>Only show auctions <= this price"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>${maxPrice?.let { MenuUtils.formatPrice(it, pctx.economy) } ?: "None"}"))
            loreList.add(Component.empty())
            if (minPrice != null && maxPrice != null && maxPrice!! < minPrice!!) {
                loreList.add(pctx.mm.deserialize("<red>Warning: Max < Min!"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to set"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    maxPrice = null
                    ClickResult.Refresh
                } else {
                    pctx.menuAPI.promptDoubleAsync(
                        pctx.player,
                        "Maximum Price",
                        maxPrice,
                        0.0,
                        Double.MAX_VALUE
                    ).thenAccept { result ->
                        when (result) {
                            is AnvilInputResult.Success -> {
                                maxPrice = result.value.takeIf { it > 0 }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                            pctx.menuAPI.open(this@AdvancedSearchMenu, pctx.player)
                        })
                    }
                    ClickResult.Deny
                }
            }
        }
    }

    private fun createPricePresetItem(): VItem {
        val (presetMaterial, displayName, presetLore) = getPricePresetInfo()

        return VItem(presetMaterial) {
            name = pctx.mm.deserialize("<yellow>Price Preset")
            val loreList = mutableListOf<Component>()
            loreList.addAll(presetLore)
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to cycle presets"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cyclePricePreset()
                ClickResult.Refresh
            }
        }
    }

    private fun getPricePresetInfo(): Triple<XMaterial, String, List<Component>> {
        return when {
            maxPrice == null -> Triple(
                XMaterial.GRAY_DYE,
                "Any",
                listOf(pctx.mm.deserialize("<gray>No price range set"))
            )
            maxPrice!! <= 100 -> Triple(
                XMaterial.LIME_DYE,
                "Under $100",
                listOf(pctx.mm.deserialize("<gray>Preset: Under $100"))
            )
            maxPrice!! <= 1000 -> Triple(
                XMaterial.YELLOW_DYE,
                "Under $1,000",
                listOf(pctx.mm.deserialize("<gray>Preset: Under $1,000"))
            )
            maxPrice!! <= 10000 -> Triple(
                XMaterial.ORANGE_DYE,
                "Under $10,000",
                listOf(pctx.mm.deserialize("<gray>Preset: Under $10,000"))
            )
            else -> Triple(
                XMaterial.RED_DYE,
                "Custom",
                listOf(pctx.mm.deserialize("<gray>Custom price range"))
            )
        }
    }

    private fun cyclePricePreset() {
        maxPrice = when {
            maxPrice == null -> 100.0
            maxPrice!! <= 100 -> 1000.0
            maxPrice!! <= 1000 -> 10000.0
            maxPrice!! <= 10000 -> null
            else -> null
        }
        minPrice = 0.0
    }

    private fun createAuctionTypeFilterItem(): VItem {
        val (material, displayName) = when (auctionType) {
            null -> XMaterial.HOPPER to "All Types"
            AuctionType.AUCTION -> XMaterial.GOLD_INGOT to "Auction Only"
            AuctionType.BIN -> XMaterial.EMERALD to "BIN Only"
            AuctionType.BOTH -> XMaterial.DIAMOND to "Auction + BIN"
        }

        return VItem(material) {
            name = pctx.mm.deserialize("<yellow>Auction Type")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Filter by auction type"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<white>• Auction Only</white> - Bidding only"))
            loreList.add(pctx.mm.deserialize("<white>• BIN Only</white> - Buy-it-now only"))
            loreList.add(pctx.mm.deserialize("<white>• Auction + BIN</white> - Both options"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                auctionType = when (auctionType) {
                    null -> AuctionType.AUCTION
                    AuctionType.AUCTION -> AuctionType.BIN
                    AuctionType.BIN -> AuctionType.BOTH
                    AuctionType.BOTH -> null
                }
                ClickResult.Deny
            }
        }
    }

    private fun createEndingSoonFilterItem(): VItem {
        val (material, displayName, color) = getEndingSoonInfo()

        return VItem(material) {
            name = pctx.mm.deserialize("${color}Ending Soon Filter")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Find auctions ending within"))
            loreList.add(pctx.mm.deserialize("<gray>specific timeframes"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cycleEndingSoon()
                ClickResult.Deny
            }
        }
    }

    private fun getEndingSoonInfo(): Triple<XMaterial, String, String> {
        val duration = endingWithin ?: return Triple(XMaterial.GRAY_DYE, "Any Time", "<gray>")
        return when {
            duration <= Duration.ofHours(1) -> Triple(XMaterial.RED_DYE, "Within 1 Hour", "<red>")
            duration <= Duration.ofHours(24) -> Triple(XMaterial.YELLOW_DYE, "Within 24 Hours", "<yellow>")
            duration <= Duration.ofDays(3) -> Triple(XMaterial.LIME_DYE, "Within 3 Days", "<green>")
            duration <= Duration.ofDays(7) -> Triple(XMaterial.BLUE_DYE, "Within 7 Days", "<blue>")
            else -> Triple(XMaterial.GRAY_DYE, "Any Time", "<gray>")
        }
    }

    private fun cycleEndingSoon() {
        val current = endingWithin
        endingWithin = when {
            current == null -> Duration.ofHours(1)
            current <= Duration.ofHours(1) -> Duration.ofHours(24)
            current <= Duration.ofHours(24) -> Duration.ofDays(3)
            current <= Duration.ofDays(3) -> Duration.ofDays(7)
            else -> null
        }
    }

    private fun createSortOptionsItem(): VItem {
        val (material, displayName) = getSortInfo()

        return VItem(material) {
            name = pctx.mm.deserialize("<yellow>Sort Options")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Change how results are ordered"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cycleSortOption()
                ClickResult.Deny
            }
        }
    }

    private fun getSortInfo(): Pair<XMaterial, String> {
        return when (sortBy) {
            AuctionSort.ENDING_SOON -> XMaterial.CLOCK to "Ending Soon"
            AuctionSort.NEWEST -> XMaterial.ANVIL to "Newest First"
            AuctionSort.PRICE_LOW -> XMaterial.GOLD_NUGGET to "Price: Low to High"
            AuctionSort.PRICE_HIGH -> XMaterial.GOLD_BLOCK to "Price: High to Low"
            AuctionSort.MOST_BIDS -> XMaterial.EXPERIENCE_BOTTLE to "Most Bids"
            AuctionSort.RECENTLY_UPDATED -> XMaterial.BOOK to "Recently Updated"
        }
    }

    private fun cycleSortOption() {
        sortBy = when (sortBy) {
            AuctionSort.ENDING_SOON -> AuctionSort.NEWEST
            AuctionSort.NEWEST -> AuctionSort.PRICE_LOW
            AuctionSort.PRICE_LOW -> AuctionSort.PRICE_HIGH
            AuctionSort.PRICE_HIGH -> AuctionSort.MOST_BIDS
            AuctionSort.MOST_BIDS -> AuctionSort.RECENTLY_UPDATED
            AuctionSort.RECENTLY_UPDATED -> AuctionSort.ENDING_SOON
        }
    }

    private fun createActiveFiltersDisplayItem(): VItem {
        val activeFilters = getActiveFiltersList()

        return VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow>Active Filters")
            val loreList = mutableListOf<Component>()
            if (activeFilters.isEmpty()) {
                loreList.add(pctx.mm.deserialize("<gray>No active filters"))
                loreList.add(pctx.mm.deserialize("<gray>Set filters above to refine search"))
            } else {
                loreList.add(pctx.mm.deserialize("<gray>Click filter to clear:"))
                loreList.add(Component.empty())
                activeFilters.forEach { (name, value) ->
                    loreList.add(pctx.mm.deserialize("<red>✖ <gray>$name<white>: $value"))
                }
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<gray>${activeFilters.size} filter(s) active"))
            }
            lore = loreList
            hideAllFlags()
        }
    }

    private fun getActiveFiltersList(): List<Pair<String, String>> {
        val filters = mutableListOf<Pair<String, String>>()
        searchQuery?.let { filters.add("Search" to "\"$it\"") }
        sellerName?.let { filters.add("Seller" to it) }
        material?.let { filters.add("Material" to it.name.replace("_", " ")) }
        minPrice?.let { filters.add("Min Price" to MenuUtils.formatPrice(it, pctx.economy)) }
        maxPrice?.let { filters.add("Max Price" to MenuUtils.formatPrice(it, pctx.economy)) }
        auctionType?.let { filters.add("Type" to it.name) }
        endingWithin?.let { filters.add("Ending" to formatDuration(it)) }
        return filters
    }

    private fun formatDuration(duration: Duration): String {
        return when {
            duration.toHours() < 1 -> "${duration.toMinutes()}m"
            duration.toDays() < 1 -> "${duration.toHours()}h"
            else -> "${duration.toDays()}d"
        }
    }

    private fun createClearAllButton(): VItem {
        return VItem(XMaterial.BARRIER) {
            name = pctx.mm.deserialize("<red>Clear All Filters")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Reset all filters to default"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<red>Click to clear all"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                this@AdvancedSearchMenu.searchQuery = null
                this@AdvancedSearchMenu.sellerName = null
                this@AdvancedSearchMenu.material = null
                this@AdvancedSearchMenu.minPrice = null
                this@AdvancedSearchMenu.maxPrice = null
                this@AdvancedSearchMenu.auctionType = null
                this@AdvancedSearchMenu.endingWithin = null
                this@AdvancedSearchMenu.sortBy = AuctionSort.ENDING_SOON
                ClickResult.Refresh
            }
        }
    }

    private fun createApplySearchButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = pctx.mm.deserialize("<green>Apply Search")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Apply all filters and view results"))
            loreList.add(Component.empty())
            val activeCount = getActiveFiltersList().size
            loreList.add(pctx.mm.deserialize("<gray>Active filters: <white>$activeCount"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to search"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                applySearch()
            }
        }
    }

    private fun applySearch(): ClickResult {
        val filter = AuctionFilter(
            searchQuery = searchQuery,
            sellerName = sellerName,
            material = material?.name,
            minPrice = minPrice,
            maxPrice = maxPrice,
            auctionType = auctionType,
            endingWithin = endingWithin,
            sortBy = sortBy
        )

        val activeCount = getActiveFiltersList().size
        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.SEARCH_APPLYING) {
            unparsed("count", activeCount.toString())
        })
        PlayerStateManager.setAuctionFilter(pctx.player.uniqueId, filter)
        PlayerStateManager.setAuctionPage(pctx.player.uniqueId, 0)
        return ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
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
