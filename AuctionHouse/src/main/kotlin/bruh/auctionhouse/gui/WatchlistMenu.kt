package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.model.Bid
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.model.WatchlistEntry
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

private data class WatchlistData(
    val entries: List<WatchlistEntry>,
    val auctions: List<Auction>,
    val orders: List<Order>,
    val highestBids: Map<UUID, Bid?>
)

/**
 * Menu for viewing and managing a player's watchlist.
 */
class WatchlistMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    enum class WatchlistSort {
        ENDING_SOON, PRICE_LOW, PRICE_HIGH, RECENTLY_ADDED
    }

    enum class WatchlistTab {
        AUCTIONS, ORDERS, ALL
    }

    private var currentSort by menuState(WatchlistSort.ENDING_SOON)
    private var currentTab by menuState(WatchlistTab.AUCTIONS)
    private var watchlistData: WatchlistData? = null

    init {
        rows = 6
        title = pctx.mm.deserialize("<yellow>My Watchlist")
        background = MenuUtils.backgroundItem()

        asyncData<WatchlistData> {
            load {
                val entries = pctx.watchlistRepository.getPlayerWatchlist(pctx.player.uniqueId)
                val auctionIds = entries.filter { it.auctionId != null }.map { it.auctionId!! }
                val orderIds = entries.filter { it.orderId != null }.map { it.orderId!! }
                val auctions = pctx.auctionRepository.getByIds(auctionIds)
                val orders = pctx.orderRepository.getByIds(orderIds)

                // Clean up expired/cancelled entries
                val activeAuctionIds = auctions.filter { it.isActive() }.map { it.id }.toSet()
                val activeOrderIds = orders.filter { it.isActive() }.map { it.id }.toSet()
                for (entry in entries) {
                    if (entry.auctionId != null && entry.auctionId !in activeAuctionIds) {
                        pctx.watchlistRepository.remove(pctx.player.uniqueId, entry.auctionId)
                    }
                    if (entry.orderId != null && entry.orderId !in activeOrderIds) {
                        pctx.watchlistRepository.removeOrder(pctx.player.uniqueId, entry.orderId)
                    }
                }

                val activeAuctions = auctions.filter { it.isActive() }
                val activeOrders = orders.filter { it.isActive() }

                // Pre-load highest bids for auction-type items
                val highestBids = activeAuctions
                    .filter { it.auctionType != AuctionType.BIN }
                    .associate { it.id to pctx.bidRepository.getHighestBid(it.id) }

                WatchlistData(entries, activeAuctions, activeOrders, highestBids)
            }
            onLoaded { data -> watchlistData = data }
        }
    }

    override fun populateItems() {
        items.clear()

        val data = watchlistData

        val watchedAuctions = data?.auctions?.map { auction ->
            val hasActivity = data.entries.find { it.auctionId == auction.id }?.hasNewActivity ?: false
            auction to hasActivity
        } ?: emptyList()

        val watchedOrders = data?.orders?.map { order ->
            val hasActivity = data.entries.find { it.orderId == order.id }?.hasNewActivity ?: false
            order to hasActivity
        } ?: emptyList()

        // Sort auctions
        val sortedAuctions = when (currentSort) {
            WatchlistSort.ENDING_SOON -> watchedAuctions.sortedBy { it.first.endsAt }
            WatchlistSort.PRICE_LOW -> watchedAuctions.sortedBy { it.first.buyNowPrice ?: it.first.startPrice }
            WatchlistSort.PRICE_HIGH -> watchedAuctions.sortedByDescending { it.first.buyNowPrice ?: it.first.startPrice }
            WatchlistSort.RECENTLY_ADDED -> watchedAuctions.sortedByDescending {
                data?.entries?.find { entry -> entry.auctionId == it.first.id }?.addedAt ?: Instant.MIN
            }
        }

        // Sort orders
        val sortedOrders = when (currentSort) {
            WatchlistSort.ENDING_SOON -> watchedOrders.sortedBy { it.first.expiresAt }
            WatchlistSort.PRICE_LOW -> watchedOrders.sortedBy { it.first.pricePerUnit }
            WatchlistSort.PRICE_HIGH -> watchedOrders.sortedByDescending { it.first.pricePerUnit }
            WatchlistSort.RECENTLY_ADDED -> watchedOrders.sortedByDescending {
                data?.entries?.find { entry -> entry.orderId == it.first.id }?.addedAt ?: Instant.MIN
            }
        }

        val totalCount = when (currentTab) {
            WatchlistTab.AUCTIONS -> watchedAuctions.size
            WatchlistTab.ORDERS -> watchedOrders.size
            WatchlistTab.ALL -> watchedAuctions.size + watchedOrders.size
        }

        title = pctx.mm.deserialize("<yellow>My Watchlist <gray>($totalCount)")

        // Tab buttons
        item(3, createTabButton(WatchlistTab.ALL, watchedAuctions.size + watchedOrders.size))
        item(4, createTabButton(WatchlistTab.AUCTIONS, watchedAuctions.size))
        item(5, createTabButton(WatchlistTab.ORDERS, watchedOrders.size))

        // Clear All button
        item(8, createClearAllButton(watchedAuctions.size + watchedOrders.size))

        var slotIndex = 0
        val startSlot = 9

        // Display watched auctions
        if (currentTab == WatchlistTab.AUCTIONS || currentTab == WatchlistTab.ALL) {
            for ((auction, hasActivity) in sortedAuctions) {
                if (slotIndex < 36) {
                    item(startSlot + slotIndex, createWatchedAuctionItem(auction, hasActivity))
                    slotIndex++
                }
            }
        }

        // Display watched orders
        if (currentTab == WatchlistTab.ORDERS || currentTab == WatchlistTab.ALL) {
            for ((order, hasActivity) in sortedOrders) {
                if (slotIndex < 36) {
                    item(startSlot + slotIndex, createWatchedOrderItem(order, hasActivity))
                    slotIndex++
                }
            }
        }

        // Sort button
        item(48, createSortButton())

        // Back button
        item(45, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.SwitchMenu(AuctionHouseMenu(pctx)) }
        })

        // Close button
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })

        // Empty state
        if (watchedAuctions.isEmpty() && watchedOrders.isEmpty() && data != null) {
            item(22, VItem(XMaterial.BARRIER) {
                name = pctx.mm.deserialize("<red>No Watched Items")
                lore = mutableListOf(
                    pctx.mm.deserialize("<gray>You haven't added any"),
                    pctx.mm.deserialize("<gray>auctions or orders to your watchlist."),
                    Component.empty(),
                    pctx.mm.deserialize("<green>Click the heart icon"),
                    pctx.mm.deserialize("<green>to add items to your watchlist!")
                )
            })
        }
    }

    private fun createTabButton(tab: WatchlistTab, count: Int): VItem {
        val isSelected = currentTab == tab
        val (name, material) = when (tab) {
            WatchlistTab.ALL -> "All" to XMaterial.CHEST
            WatchlistTab.AUCTIONS -> "Auctions" to XMaterial.GOLD_INGOT
            WatchlistTab.ORDERS -> "Orders" to XMaterial.DIAMOND
        }

        return VItem(material) {
            this.name = pctx.mm.deserialize("${if (isSelected) "<green>" else "<gray>"}$name <white>($count)")
            hideAllFlags()

            onClick { _, _ ->
                currentTab = tab
                ClickResult.Deny
            }
        }
    }

    private fun createWatchedAuctionItem(auction: Auction, hasNewActivity: Boolean): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()

            if (hasNewActivity) {
                loreList.add(pctx.mm.deserialize("<red>⚠ New Activity!"))
                loreList.add(Component.empty())
            }

            when (auction.auctionType) {
                AuctionType.AUCTION -> {
                    val highestBid = watchlistData?.highestBids?.get(auction.id)
                    val currentBid = highestBid?.bidAmount ?: auction.startPrice
                    loreList.add(pctx.mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, pctx.economy)}"))
                    highestBid?.let {
                        loreList.add(pctx.mm.deserialize("<gray>Highest: <white>${it.bidderName}"))
                    }
                }
                AuctionType.BIN -> {
                    auction.buyNowPrice?.let { price ->
                        loreList.add(pctx.mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(price, pctx.economy)}"))
                    }
                }
                AuctionType.BOTH -> {
                    val highestBid = watchlistData?.highestBids?.get(auction.id)
                    val currentBid = highestBid?.bidAmount ?: auction.startPrice
                    loreList.add(pctx.mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, pctx.economy)}"))
                    auction.buyNowPrice?.let { price ->
                        loreList.add(pctx.mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(price, pctx.economy)}"))
                    }
                }
            }

            val timeLeft = MenuUtils.formatTimeRemaining(auction.endsAt)
            val timeColor = when {
                auction.endsAt.isBefore(Instant.now().plus(Duration.ofHours(1))) -> "<red>"
                auction.endsAt.isBefore(Instant.now().plus(Duration.ofDays(1))) -> "<yellow>"
                else -> "<green>"
            }
            loreList.add(pctx.mm.deserialize("<gray>Time Left: ${timeColor}${timeLeft}"))
            loreList.add(pctx.mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))

            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to view details"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to remove from watchlist"))

            lore = loreList
            hideAllFlags()

            onClick { click, controls ->
                if (click.isRightClick) {
                    controls.runAsync(
                        action = { pctx.watchlistRepository.remove(pctx.player.uniqueId, auction.id) },
                        onSuccess = {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                            controls.reloadData()
                        }
                    )
                    ClickResult.Deny
                } else {
                    ClickResult.SwitchMenu(AuctionDetailsMenu(pctx, auction))
                }
            }
        }
    }

    private fun createWatchedOrderItem(order: Order, hasNewActivity: Boolean): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        return VItem(material) {
            name = order.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))

            val loreList = mutableListOf<Component>()

            if (hasNewActivity) {
                loreList.add(pctx.mm.deserialize("<red>⚠ New Activity!"))
                loreList.add(Component.empty())
            }

            loreList.add(pctx.translationAPI.getComponentSync(
                if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
            ))

            loreList.add(pctx.mm.deserialize("<yellow>Quantity: <white>${order.quantityFilled}/${order.quantityRequested}"))
            loreList.add(pctx.mm.deserialize("<yellow>Price: <gold>${MenuUtils.formatPrice(order.pricePerUnit, pctx.economy)}/each"))

            val timeLeft = MenuUtils.formatTimeRemaining(order.expiresAt)
            val timeColor = when {
                order.expiresAt.isBefore(Instant.now().plus(Duration.ofHours(1))) -> "<red>"
                order.expiresAt.isBefore(Instant.now().plus(Duration.ofDays(1))) -> "<yellow>"
                else -> "<green>"
            }
            loreList.add(pctx.mm.deserialize("<gray>Time Left: ${timeColor}${timeLeft}"))

            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to fulfill"))
            loreList.add(pctx.mm.deserialize("<red>Right-click to remove from watchlist"))

            lore = loreList
            hideAllFlags()

            onClick { click, controls ->
                if (click.isRightClick) {
                    controls.runAsync(
                        action = { pctx.watchlistRepository.removeOrder(pctx.player.uniqueId, order.id) },
                        onSuccess = {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                            controls.reloadData()
                        }
                    )
                    ClickResult.Deny
                } else {
                    ClickResult.SwitchMenu(OrderFulfillMenu(pctx, order))
                }
            }
        }
    }

    private fun createClearAllButton(count: Int): VItem {
        return VItem(XMaterial.BARRIER) {
            name = pctx.mm.deserialize("<red>Clear All")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Remove all items from"),
                pctx.mm.deserialize("<gray>your watchlist"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Currently watching: <white>$count"),
                Component.empty(),
                pctx.mm.deserialize("<red>Click to clear all")
            )
            hideAllFlags()

            onClick { _, controls ->
                controls.runAsync(
                    action = {
                        val entries = pctx.watchlistRepository.getPlayerWatchlist(pctx.player.uniqueId)
                        for (entry in entries) {
                            if (entry.auctionId != null) {
                                pctx.watchlistRepository.remove(pctx.player.uniqueId, entry.auctionId)
                            } else if (entry.orderId != null) {
                                pctx.watchlistRepository.removeOrder(pctx.player.uniqueId, entry.orderId)
                            }
                        }
                    },
                    onSuccess = {
                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_CLEARED))
                        controls.reloadData()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createSortButton(): VItem {
        val (displayName, description) = when (currentSort) {
            WatchlistSort.ENDING_SOON -> "Ending Soon" to "Sort by auctions ending first"
            WatchlistSort.PRICE_LOW -> "Price: Low to High" to "Sort by lowest price first"
            WatchlistSort.PRICE_HIGH -> "Price: High to Low" to "Sort by highest price first"
            WatchlistSort.RECENTLY_ADDED -> "Recently Added" to "Sort by most recently added"
        }

        return VItem(XMaterial.COMPASS) {
            name = pctx.mm.deserialize("<yellow>Sort Options")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Sort your watchlist"),
                Component.empty(),
                pctx.mm.deserialize("<green>Current: <white>$displayName"),
                pctx.mm.deserialize("<gray>$description"),
                Component.empty(),
                pctx.mm.deserialize("<white>• Ending Soon</white>"),
                pctx.mm.deserialize("<white>• Price Low/High</white>"),
                pctx.mm.deserialize("<white>• Recently Added</white>"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to cycle")
            )
            hideAllFlags()

            onClick { _, _ ->
                currentSort = when (currentSort) {
                    WatchlistSort.ENDING_SOON -> WatchlistSort.PRICE_LOW
                    WatchlistSort.PRICE_LOW -> WatchlistSort.PRICE_HIGH
                    WatchlistSort.PRICE_HIGH -> WatchlistSort.RECENTLY_ADDED
                    WatchlistSort.RECENTLY_ADDED -> WatchlistSort.ENDING_SOON
                }
                ClickResult.Deny
            }
        }
    }
}
