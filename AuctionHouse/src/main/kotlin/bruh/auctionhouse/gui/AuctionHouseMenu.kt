package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Main auction house browser menu with pagination, filters, and sorting.
 */
class AuctionHouseMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<Auction>() {

    private var currentFilter by menuState(AuctionFilter())
    private var watchlistCount = 0

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)
        background = MenuUtils.backgroundItem()
        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        loadingPlaceholder = MenuUtils.loadingAuctionItem()
        emptyPlaceholder = MenuUtils.emptyAuctionsItem()

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }
        pageIndicatorRenderer = { current, total ->
            VItem(XMaterial.PAPER) {
                name = Component.text("Page $current/$total")
            }
        }

        itemRenderer = { auction, _ ->
            createAuctionItem(auction)
        }

        asyncData<List<Auction>> {
            load { pctx.auctionService.getActiveAuctions(currentFilter, currentFilter.sortBy, 0, Int.MAX_VALUE).items }
            onLoaded { auctions -> dataSource = auctions }
        }

        asyncData<Int> {
            load { pctx.watchlistRepository.count(pctx.player.uniqueId) }
            onLoaded { count -> watchlistCount = count }
        }
    }

    override fun populateItems() {
        items.clear()

        items[45] = createWatchlistButton()
        items[46] = createFilterButton()
        items[47] = createQuickSortButton()
        items[48] = createSearchButton()
        items[49] = createQuickSellButton()
        items[50] = createMyAuctionsButton()
        items[51] = createTransactionHistoryButton()
        items[52] = createOrdersButton()
    }

    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        val hasEnded = auction.hasEnded()

        val loreList = mutableListOf<Component>()

        loreList.add(pctx.mm.deserialize("<gray>ID: <white>${auction.shortId}"))
        loreList.add(Component.empty())

        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_SELLER) {
            unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
        })

        if (hasEnded) {
            loreList.add(pctx.mm.deserialize("<red>⚠ Auction Ended"))
        }

        when (auction.auctionType) {
            AuctionType.AUCTION -> {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, pctx.economy))
                })
            }
            AuctionType.BIN -> {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                    unparsed("price", MenuUtils.formatPrice(auction.buyNowPrice ?: 0.0, pctx.economy))
                })
            }
            AuctionType.BOTH -> {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, pctx.economy))
                })
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                        unparsed("price", MenuUtils.formatPrice(binPrice, pctx.economy))
                    })
                }
            }
        }

        if (auction.bidCount > 0) {
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIDS) {
                unparsed("count", auction.bidCount.toString())
            })
        }

        val timeDisplay = if (hasEnded) {
            pctx.mm.deserialize("<red>Ended")
        } else {
            pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_TIME_LEFT) {
                unparsed("time", MenuUtils.formatTimeRemaining(auction.endsAt))
            }
        }
        loreList.add(timeDisplay)

        loreList.add(Component.empty())
        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_CLICK_VIEW))

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            lore = loreList

            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionDetailsMenu(pctx, auction))
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
            name = pctx.mm.deserialize("<yellow>Filter: <white>$displayName")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to change filter"),
                Component.empty(),
                pctx.mm.deserialize("<gray>For advanced filters,")
            )
            lore.add(pctx.mm.deserialize("<gray>use the Search button"))
            hideAllFlags()

            onClick { _, controls ->
                currentFilter = when (currentFilter.auctionType) {
                    null -> currentFilter.copy(auctionType = AuctionType.AUCTION)
                    AuctionType.AUCTION -> currentFilter.copy(auctionType = AuctionType.BIN)
                    AuctionType.BIN -> currentFilter.copy(auctionType = AuctionType.BOTH)
                    AuctionType.BOTH -> currentFilter.copy(auctionType = null)
                }
                dataSource = emptyList()
                controls.reloadData()
                ClickResult.Deny
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
            name = pctx.translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Current: <white>$displayName"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to cycle")
            )
            hideAllFlags()

            onClick { _, controls ->
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
                dataSource = emptyList()
                controls.reloadData()
                ClickResult.Deny
            }
        }
    }

    private fun createSearchButton(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click for advanced search"),
                pctx.mm.deserialize("<gray>Filter by name, price, seller,"),
                pctx.mm.deserialize("<gray>material, time, and more!"),
                Component.empty(),
                if (currentFilter.searchQuery.isNullOrBlank() &&
                    currentFilter.sellerName.isNullOrBlank() &&
                    currentFilter.material == null &&
                    currentFilter.minPrice == null &&
                    currentFilter.maxPrice == null) {
                    pctx.mm.deserialize("<gray>Current: <white>No filters")
                } else {
                    pctx.mm.deserialize("<yellow>Active filters:")
                }
            )
            if (!currentFilter.searchQuery.isNullOrBlank()) {
                lore.add(pctx.mm.deserialize("  <gray>• Search: <white>${currentFilter.searchQuery}"))
            }
            if (!currentFilter.sellerName.isNullOrBlank()) {
                lore.add(pctx.mm.deserialize("  <gray>• Seller: <white>${currentFilter.sellerName}"))
            }
            if (currentFilter.material != null) {
                lore.add(pctx.mm.deserialize("  <gray>• Material: <white>${currentFilter.material}"))
            }
            if (currentFilter.minPrice != null) {
                lore.add(pctx.mm.deserialize("  <gray>• Min Price: <white>${MenuUtils.formatPrice(currentFilter.minPrice!!, pctx.economy)}"))
            }
            if (currentFilter.maxPrice != null) {
                lore.add(pctx.mm.deserialize("  <gray>• Max Price: <white>${MenuUtils.formatPrice(currentFilter.maxPrice!!, pctx.economy)}"))
            }
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(AdvancedSearchMenu(pctx))
            }
        }
    }

    private fun createMyAuctionsButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(MyAuctionsMenu(pctx))
            }
        }
    }

    private fun createCreateAuctionButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_AUCTION)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionCreateMenu(pctx))
            }
        }
    }

    private fun createOrdersButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderBrowserMenu(pctx))
            }
        }
    }

    private fun createCreateOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(OrderCreateMenu(pctx))
            }
        }
    }

    private fun createWatchlistButton(): VItem {
        return VItem(XMaterial.COMPASS) {
            name = pctx.mm.deserialize("<yellow>My Watchlist")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>View your watched auctions"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Watching: <white>$watchlistCount"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to open watchlist")
            )
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(WatchlistMenu(pctx))
            }
        }
    }

    private fun createBulkListButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = pctx.mm.deserialize("<yellow>Bulk Listing")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>List multiple items at once"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Max: ${pctx.config.auctions.bulkListing.maxBulkListings} auctions")
            )
            if (pctx.config.auctions.bulkListing.feeDiscountPercent > 0) {
                lore.add(Component.empty())
                lore.add(pctx.mm.deserialize("<green>Bulk Discount: ${pctx.config.auctions.bulkListing.feeDiscountPercent}%"))
            }
            lore.add(Component.empty())
            lore.add(pctx.mm.deserialize("<green>Click to open bulk listing"))
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(BulkListMenu(pctx))
            }
        }
    }

    private fun createTransactionHistoryButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = pctx.mm.deserialize("<yellow>Transaction History")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>View your transaction history"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Click to view transactions")
            )
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(TransactionHistoryMenu(pctx))
            }
        }
    }

    private fun createQuickSellButton(): VItem {
        val heldItem = pctx.player.inventory.itemInMainHand
        val hasHeldItem = !heldItem.type.isAir

        return VItem(if (hasHeldItem) XMaterial.EMERALD else XMaterial.GRAY_DYE) {
            name = pctx.mm.deserialize("<green>Quick Sell")
            val loreList = mutableListOf<Component>()

            if (hasHeldItem) {
                loreList.add(pctx.mm.deserialize("<gray>Sell your held item to the"))
                loreList.add(pctx.mm.deserialize("<gray>highest buy order instantly!"))
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<yellow>Item: <white>${heldItem.type.name.replace("_", " ")}"))
                loreList.add(pctx.mm.deserialize("<yellow>Amount: <white>${heldItem.amount}"))
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<green>Click to quick sell"))
            } else {
                loreList.add(pctx.mm.deserialize("<gray>Sell items to buy orders"))
                loreList.add(pctx.mm.deserialize("<gray>for instant payment!"))
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<red>Hold an item to quick sell"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (!hasHeldItem) {
                    pctx.player.sendMessage(pctx.mm.deserialize("<red>Hold an item to use Quick Sell!"))
                    ClickResult.Close
                } else {
                    ClickResult.SwitchMenu(QuickSellMenu(pctx, heldItem))
                }
            }
        }
    }
}
