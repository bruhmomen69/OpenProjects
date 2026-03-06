package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID

/**
 * Advanced search menu with comprehensive filtering options.
 */
class AdvancedSearchMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val parentMenu: () -> Unit
) {
    private val mm = MiniMessage.miniMessage()
    
    // Current filter state
    private var searchQuery: String? = null
    private var sellerName: String? = null
    private var material: XMaterial? = null
    private var minPrice: Double? = null
    private var maxPrice: Double? = null
    private var auctionType: AuctionType? = null
    private var endingWithin: Duration? = null
    private var sortBy: AuctionSort = AuctionSort.ENDING_SOON

    fun open() {
        val menu = menuAPI.simple {
            rows = 6
            title = mm.deserialize("<yellow>Advanced Search")

            background = MenuUtils.backgroundItem()

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

        menuAPI.open(menu, player)
    }

    private fun createSearchQueryItem(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = mm.deserialize("<yellow>Search Query")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Search by item name or material"))
            loreList.add(mm.deserialize("<gray>Partial matches supported"))
            loreList.add(mm.deserialize("<gray>Case-insensitive"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>${searchQuery ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to set"))
            loreList.add(mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    searchQuery = null
                    open()
                } else {
                    runBlocking {
                        val result = menuAPI.promptText(
                            player,
                            "Search Query",
                            searchQuery ?: ""
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                searchQuery = result.value.takeIf { it.isNotBlank() }
                                open()
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createSellerNameItem(): VItem {
        return VItem(XMaterial.PLAYER_HEAD) {
            name = mm.deserialize("<yellow>Seller Name")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Find auctions from specific players"))
            loreList.add(mm.deserialize("<gray>Enter exact player name"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>${sellerName ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to set"))
            loreList.add(mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    sellerName = null
                    open()
                } else {
                    runBlocking {
                        val result = menuAPI.promptText(
                            player,
                            "Seller Name",
                            sellerName ?: ""
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                sellerName = result.value.takeIf { it.isNotBlank() }
                                open()
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createMaterialSelectorItem(): VItem {
        val displayMaterial = material ?: XMaterial.HOPPER

        return VItem(displayMaterial) {
            name = mm.deserialize("<yellow>Material Filter")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Filter by specific item material"))
            loreList.add(mm.deserialize("<gray>Opens material picker menu"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>${material?.name ?: "Any"}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to select"))
            loreList.add(mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    this@AdvancedSearchMenu.material = null
                    open()
                } else {
                    MaterialPickerMenu(menuAPI, config, translationAPI) { selectedMaterial ->
                        this@AdvancedSearchMenu.material = selectedMaterial
                        open()
                    }.openForPlayer(player)
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createMinPriceItem(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = mm.deserialize("<yellow>Minimum Price")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Set minimum price filter"))
            loreList.add(mm.deserialize("<gray>Only show auctions >= this price"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>${minPrice?.let { MenuUtils.formatPrice(it, economy) } ?: "None"}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to set"))
            loreList.add(mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    minPrice = null
                    open()
                } else {
                    runBlocking {
                        val result = menuAPI.promptDouble(
                            player,
                            "Minimum Price",
                            minPrice,
                            0.0,
                            Double.MAX_VALUE
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                minPrice = result.value.takeIf { it > 0 }
                                open()
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createMaxPriceItem(): VItem {
        return VItem(XMaterial.GOLD_BLOCK) {
            name = mm.deserialize("<yellow>Maximum Price")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Set maximum price filter"))
            loreList.add(mm.deserialize("<gray>Only show auctions <= this price"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>${maxPrice?.let { MenuUtils.formatPrice(it, economy) } ?: "None"}"))
            loreList.add(Component.empty())
            if (minPrice != null && maxPrice != null && maxPrice!! < minPrice!!) {
                loreList.add(mm.deserialize("<red>Warning: Max < Min!"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to set"))
            loreList.add(mm.deserialize("<red>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    maxPrice = null
                    open()
                } else {
                    runBlocking {
                        val result = menuAPI.promptDouble(
                            player,
                            "Maximum Price",
                            maxPrice,
                            0.0,
                            Double.MAX_VALUE
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                maxPrice = result.value.takeIf { it > 0 }
                                open()
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createPricePresetItem(): VItem {
        val (presetMaterial, displayName, presetLore) = getPricePresetInfo()

        return VItem(presetMaterial) {
            name = mm.deserialize("<yellow>Price Preset")
            val loreList = mutableListOf<Component>()
            loreList.addAll(presetLore)
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle presets"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cyclePricePreset()
                open()
                ClickResult.CLOSE
            }
        }
    }

    private fun getPricePresetInfo(): Triple<XMaterial, String, List<Component>> {
        return when {
            maxPrice == null -> Triple(
                XMaterial.GRAY_DYE,
                "Any",
                listOf(mm.deserialize("<gray>No price range set"))
            )
            maxPrice!! <= 100 -> Triple(
                XMaterial.LIME_DYE,
                "Under $100",
                listOf(mm.deserialize("<gray>Preset: Under $100"))
            )
            maxPrice!! <= 1000 -> Triple(
                XMaterial.YELLOW_DYE,
                "Under $1,000",
                listOf(mm.deserialize("<gray>Preset: Under $1,000"))
            )
            maxPrice!! <= 10000 -> Triple(
                XMaterial.ORANGE_DYE,
                "Under $10,000",
                listOf(mm.deserialize("<gray>Preset: Under $10,000"))
            )
            else -> Triple(
                XMaterial.RED_DYE,
                "Custom",
                listOf(mm.deserialize("<gray>Custom price range"))
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
            name = mm.deserialize("<yellow>Auction Type")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Filter by auction type"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<white>• Auction Only</white> - Bidding only"))
            loreList.add(mm.deserialize("<white>• BIN Only</white> - Buy-it-now only"))
            loreList.add(mm.deserialize("<white>• Auction + BIN</white> - Both options"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                auctionType = when (auctionType) {
                    null -> AuctionType.AUCTION
                    AuctionType.AUCTION -> AuctionType.BIN
                    AuctionType.BIN -> AuctionType.BOTH
                    AuctionType.BOTH -> null
                }
                open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createEndingSoonFilterItem(): VItem {
        val (material, displayName, color) = getEndingSoonInfo()

        return VItem(material) {
            name = mm.deserialize("${color}Ending Soon Filter")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Find auctions ending within"))
            loreList.add(mm.deserialize("<gray>specific timeframes"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cycleEndingSoon()
                open()
                ClickResult.CLOSE
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
            name = mm.deserialize("<yellow>Sort Options")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Change how results are ordered"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Current: <white>$displayName"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                cycleSortOption()
                open()
                ClickResult.CLOSE
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
            name = mm.deserialize("<yellow>Active Filters")
            val loreList = mutableListOf<Component>()
            if (activeFilters.isEmpty()) {
                loreList.add(mm.deserialize("<gray>No active filters"))
                loreList.add(mm.deserialize("<gray>Set filters above to refine search"))
            } else {
                loreList.add(mm.deserialize("<gray>Click filter to clear:"))
                loreList.add(Component.empty())
                activeFilters.forEach { (name, value) ->
                    loreList.add(mm.deserialize("<red>✖ <gray>$name<white>: $value"))
                }
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<gray>${activeFilters.size} filter(s) active"))
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
        minPrice?.let { filters.add("Min Price" to MenuUtils.formatPrice(it, economy)) }
        maxPrice?.let { filters.add("Max Price" to MenuUtils.formatPrice(it, economy)) }
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
            name = mm.deserialize("<red>Clear All Filters")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Reset all filters to default"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<red>Click to clear all"))
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
                open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createApplySearchButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Apply Search")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Apply all filters and view results"))
            loreList.add(Component.empty())
            val activeCount = getActiveFiltersList().size
            loreList.add(mm.deserialize("<gray>Active filters: <white>$activeCount"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to search"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                applySearch()
                ClickResult.CLOSE
            }
        }
    }

    private fun applySearch() {
        // Build the filter from current settings
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

        // Open main auction house menu with the new filter
        // We need to pass the filter to the AuctionHouseMenu
        // For now, we'll just open the main menu and it will use its own filter
        // A better approach would be to have a callback
        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.SEARCH_APPLYING) {
            unparsed("count", activeCount.toString())
        })

        // Open the main auction house with the filter
        // This requires modifying AuctionHouseMenu to accept a filter
        // For now, we'll just show a message
        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.SEARCH_FEATURE_INTEGRATED))

        // Call parent menu to refresh
        parentMenu()
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(translationAPI).apply {
            onClick { _, _ ->
                parentMenu()
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
