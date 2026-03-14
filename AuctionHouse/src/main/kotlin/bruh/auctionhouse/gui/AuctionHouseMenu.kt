package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Main auction house browser menu with pagination, filters, and sorting.
 */
class AuctionHouseMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val orderRepository: OrderRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) : bruh.zchat.utils.menuapi.PaginatedMenu<Auction>() {
    private val mm = MiniMessage.miniMessage()

    private var currentFilter: AuctionFilter
    private var currentPage: Int

    init {
        // Load persisted filter state from PlayerStateManager
        currentFilter = PlayerStateManager.getAuctionFilter(player.uniqueId)
        currentPage = PlayerStateManager.getAuctionPage(player.uniqueId)
    }

    private fun saveFilterState() {
        PlayerStateManager.setAuctionFilter(player.uniqueId, currentFilter)
        PlayerStateManager.setAuctionPage(player.uniqueId, currentPage)
    }

    fun createMenu(page: Int = 0): Menu {
        currentPage = page
        saveFilterState()

        val result = runBlocking {
            auctionService.getActiveAuctions(currentFilter, currentFilter.sortBy, page, 28)
        }

        return createMenuFromResult(result)
    }

    private fun createMenuFromResult(result: bruh.auctionhouse.service.PagedResult<bruh.auctionhouse.model.Auction>): Menu {
        return this.apply {
            items.clear()
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = result.items

            itemRenderer = { auction, _ ->
                createAuctionItem(auction)
            }

            // Background
            background = MenuUtils.backgroundItem()

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

            items[45] = createWatchlistButton()
            items[46] = createFilterButton()
            items[47] = createQuickSortButton()
            items[48] = createSearchButton()
            items[49] = createQuickSellButton()
            items[50] = createMyAuctionsButton()
            items[51] = createTransactionHistoryButton()
            items[52] = createOrdersButton()
        }
    }

    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        val hasEnded = auction.hasEnded()

        val loreList = mutableListOf<Component>()

        loreList.add(mm.deserialize("<gray>ID: <white>${auction.shortId}"))
        loreList.add(Component.empty())

        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_SELLER) {
            unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
        })

        if (hasEnded) {
            loreList.add(mm.deserialize("<red>⚠ Auction Ended"))
        }

        // Price info based on auction type
        when (auction.auctionType) {
            AuctionType.AUCTION -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, economy))
                })
            }
            AuctionType.BIN -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                    unparsed("price", MenuUtils.formatPrice(auction.buyNowPrice ?: 0.0, plugin.economy))
                })
            }
            AuctionType.BOTH -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, economy))
                })
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                        unparsed("price", MenuUtils.formatPrice(binPrice, economy))
                    })
                }
            }
        }

        // Bids
        if (auction.bidCount > 0) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIDS) {
                unparsed("count", auction.bidCount.toString())
            })
        }

        // Time left - show ended status if auction has ended
        val timeDisplay = if (hasEnded) {
            mm.deserialize("<red>Ended")
        } else {
            translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_TIME_LEFT) {
                unparsed("time", MenuUtils.formatTimeRemaining(auction.endsAt))
            }
        }
        loreList.add(timeDisplay)

        // Click instructions
        loreList.add(Component.empty())
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_CLICK_VIEW))

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            lore = loreList

            onClick { _, _ ->
                // Handle click - open auction details
                ClickResult.SwitchMenu(
                    AuctionDetailsMenu(
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
                        player,
                        auction
                    ).createMenu()
                )
            }
        }
    }

    private fun createFilterButton(): VItem {
        val (material, displayName) = when (currentFilter.auctionType) {
            null -> XMaterial.HOPPER to "All Types"
            AuctionType.AUCTION -> XMaterial.GOLD_INGOT to "Auction Only"
            AuctionType.BIN -> XMaterial.EMERALD to "BIN Only"
            AuctionType.BOTH -> XMaterial.DIAMOND to "Auction + BIN"
        }

        return VItem(material) {
            name = mm.deserialize("<yellow>Filter: <white>$displayName")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to change filter"),
                Component.empty(),
                mm.deserialize("<gray>For advanced filters,")
            )
            lore.add(mm.deserialize("<gray>use the Search button"))
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through filter options
                currentFilter = when (currentFilter.auctionType) {
                    null -> currentFilter.copy(auctionType = AuctionType.AUCTION)
                    AuctionType.AUCTION -> currentFilter.copy(auctionType = AuctionType.BIN)
                    AuctionType.BIN -> currentFilter.copy(auctionType = AuctionType.BOTH)
                    AuctionType.BOTH -> currentFilter.copy(auctionType = null)
                }
                saveFilterState()
                ClickResult.SwitchMenu(createMenu(currentPage))
            }
        }
    }

    private fun createQuickSortButton(): VItem {
        val (material, displayName) = when (currentFilter.sortBy) {
            AuctionSort.ENDING_SOON -> XMaterial.CLOCK to "Ending Soon"
            AuctionSort.NEWEST -> XMaterial.ANVIL to "Newest First"
            AuctionSort.PRICE_LOW -> XMaterial.GOLD_NUGGET to "Price: Low to High"
            AuctionSort.PRICE_HIGH -> XMaterial.GOLD_BLOCK to "Price: High to Low"
            AuctionSort.MOST_BIDS -> XMaterial.EXPERIENCE_BOTTLE to "Most Bids"
            AuctionSort.RECENTLY_UPDATED -> XMaterial.BOOK to "Recently Updated"
        }

        return VItem(material) {
            name = translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            lore = mutableListOf(
                mm.deserialize("<gray>Current: <white>$displayName"),
                Component.empty(),
                mm.deserialize("<green>Click to cycle")
            )
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through sort options
                currentFilter = currentFilter.copy(
                    sortBy = when (currentFilter.sortBy) {
                        AuctionSort.ENDING_SOON -> AuctionSort.NEWEST
                        AuctionSort.NEWEST -> AuctionSort.PRICE_LOW
                        AuctionSort.PRICE_LOW -> AuctionSort.PRICE_HIGH
                        AuctionSort.PRICE_HIGH -> AuctionSort.MOST_BIDS
                        AuctionSort.MOST_BIDS -> AuctionSort.RECENTLY_UPDATED
                        AuctionSort.RECENTLY_UPDATED -> AuctionSort.ENDING_SOON
                    }
                )
                saveFilterState()
                ClickResult.SwitchMenu(createMenu(currentPage))
            }
        }
    }

    private fun createSearchButton(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
            lore = mutableListOf(
                mm.deserialize("<gray>Click for advanced search"),
                mm.deserialize("<gray>Filter by name, price, seller,"),
                mm.deserialize("<gray>material, time, and more!"),
                Component.empty(),
                if (currentFilter.searchQuery.isNullOrBlank() && 
                    currentFilter.sellerName.isNullOrBlank() &&
                    currentFilter.material == null &&
                    currentFilter.minPrice == null &&
                    currentFilter.maxPrice == null) {
                    mm.deserialize("<gray>Current: <white>No filters")
                } else {
                    mm.deserialize("<yellow>Active filters:")
                }
            )
            if (!currentFilter.searchQuery.isNullOrBlank()) {
                lore.add(mm.deserialize("  <gray>• Search: <white>${currentFilter.searchQuery}"))
            }
            if (!currentFilter.sellerName.isNullOrBlank()) {
                lore.add(mm.deserialize("  <gray>• Seller: <white>${currentFilter.sellerName}"))
            }
            if (currentFilter.material != null) {
                lore.add(mm.deserialize("  <gray>• Material: <white>${currentFilter.material}"))
            }
            if (currentFilter.minPrice != null) {
                lore.add(mm.deserialize("  <gray>• Min Price: <white>${MenuUtils.formatPrice(currentFilter.minPrice!!, economy)}"))
            }
            if (currentFilter.maxPrice != null) {
                lore.add(mm.deserialize("  <gray>• Max Price: <white>${MenuUtils.formatPrice(currentFilter.maxPrice!!, economy)}"))
            }
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    AdvancedSearchMenu(
                        menuAPI,
                        auctionService,
                        orderService,
                        auctionRepository,
                        bidRepository,
                        config,
                        translationAPI,
                        plugin,
                        economy,
                        player
                    ) {
                        AuctionHouseMenu(
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
                        ).createMenu()
                    }.createMenu()
                )
            }
        }
    }

    private fun createMyAuctionsButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    MyAuctionsMenu(
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
                    ).createMenu()
                )
            }
        }
    }

    private fun createCreateAuctionButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_AUCTION)
            hideAllFlags()

            onClick { _, _ ->
                AuctionCreateMenu(menuAPI, auctionService, config, translationAPI, plugin, player)
                    .createMenuOrNull()
                    ?.let { ClickResult.SwitchMenu(it) }
                    ?: ClickResult.Close
            }
        }
    }

    private fun createOrdersButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
            hideAllFlags()

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
    }

    private fun createCreateOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).createMenu {
                        createMenu(currentPage)
                    }
                )
            }
        }
    }

    private fun createWatchlistButton(): VItem {
        val watchlistCount = runBlocking {
            watchlistRepository.count(player.uniqueId)
        }

        return VItem(XMaterial.COMPASS) {
            name = mm.deserialize("<yellow>My Watchlist")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>View your watched auctions"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Watching: <white>$watchlistCount"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to open watchlist"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(
                    WatchlistMenu(
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
                    ).createMenu()
                )
            }
        }
    }

    private fun createBulkListButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = mm.deserialize("<yellow>Bulk Listing")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>List multiple items at once"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Max: ${config.auctions.bulkListing.maxBulkListings} auctions"))
            if (config.auctions.bulkListing.feeDiscountPercent > 0) {
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Bulk Discount: ${config.auctions.bulkListing.feeDiscountPercent}%"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to open bulk listing"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                BulkListMenu(menuAPI, auctionService, config, translationAPI, plugin, player)
                    .createMenuOrNull()
                    ?.let { ClickResult.SwitchMenu(it) }
                    ?: ClickResult.Close
            }
        }
    }

    private fun createTransactionHistoryButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = mm.deserialize("<yellow>Transaction History")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>View your transaction history"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Click to view transactions"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                TransactionHistoryMenu(menuAPI, plugin.transactionRepository, config, translationAPI, plugin, player)
                    .createMenuOrNull()
                    ?.let { ClickResult.SwitchMenu(it) }
                    ?: ClickResult.Close
            }
        }
    }

    private fun createQuickSellButton(): VItem {
        val heldItem = player.inventory.itemInMainHand
        val hasHeldItem = !heldItem.type.isAir

        return VItem(if (hasHeldItem) XMaterial.EMERALD else XMaterial.GRAY_DYE) {
            name = mm.deserialize("<green>Quick Sell")
            val loreList = mutableListOf<Component>()
            
            if (hasHeldItem) {
                loreList.add(mm.deserialize("<gray>Sell your held item to the"))
                loreList.add(mm.deserialize("<gray>highest buy order instantly!"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<yellow>Item: <white>${heldItem.type.name.replace("_", " ")}"))
                loreList.add(mm.deserialize("<yellow>Amount: <white>${heldItem.amount}"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Click to quick sell"))
            } else {
                loreList.add(mm.deserialize("<gray>Sell items to buy orders"))
                loreList.add(mm.deserialize("<gray>for instant payment!"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<red>Hold an item to quick sell"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (!hasHeldItem) {
                    player.sendMessage(mm.deserialize("<red>Hold an item to use Quick Sell!"))
                    ClickResult.Close
                } else {
                    QuickSellMenu(menuAPI, orderService, config, translationAPI, plugin, economy, player, heldItem)
                        .createMenuOrNull()
                        ?.let { ClickResult.SwitchMenu(it) }
                        ?: ClickResult.Close
                }
            }
        }
    }
}
