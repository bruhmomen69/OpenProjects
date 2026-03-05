package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Main auction house browser menu with pagination, filters, and sorting.
 */
class AuctionHouseMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter = AuctionFilter()
    private var currentPage = 0

    fun open(page: Int = 0) {
        currentPage = page

        // Load auctions first
        val result = runBlocking {
            auctionService.getActiveAuctions(currentFilter, currentFilter.sortBy, page, 28)
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

            // Static control items
            staticItems[46] = createFilterButton()
            staticItems[47] = createQuickSortButton()
            staticItems[48] = createSearchButton()
            staticItems[49] = createCreateOrderButton()
            staticItems[50] = createMyAuctionsButton()
            staticItems[51] = createCreateAuctionButton()
            staticItems[52] = createOrdersButton()
            staticItems[45] = createWatchlistButton()
        }

        menuAPI.open(menu, player)
    }

    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()

        // Seller
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_SELLER) {
            unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
        })

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

        // Time left
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(auction.endsAt))
        })

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
                AuctionDetailsMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player, auction).open()
                ClickResult.CLOSE
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
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
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
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
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
                AdvancedSearchMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, config, translationAPI, plugin, economy, player) {
                    // Callback to refresh with updated filters
                    // The AdvancedSearchMenu will handle applying filters
                }.open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createMyAuctionsButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                MyAuctionsMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createCreateAuctionButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_AUCTION)
            hideAllFlags()

            onClick { _, _ ->
                AuctionCreateMenu(menuAPI, auctionService, config, translationAPI, plugin, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createOrdersButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                OrderBrowserMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createCreateOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            hideAllFlags()

            onClick { _, _ ->
                OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).open {
                    open(currentPage)
                }
                ClickResult.CLOSE
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
                WatchlistMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }
}
