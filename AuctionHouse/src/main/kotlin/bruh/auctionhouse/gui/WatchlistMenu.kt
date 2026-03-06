package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderType
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
import java.util.UUID

/**
 * Menu for viewing and managing a player's watchlist.
 */
class WatchlistMenu(
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
) {
    private val mm = MiniMessage.miniMessage()
    private var currentSort = WatchlistSort.ENDING_SOON
    private var currentTab = WatchlistTab.AUCTIONS

    enum class WatchlistSort {
        ENDING_SOON,
        PRICE_LOW,
        PRICE_HIGH,
        RECENTLY_ADDED
    }

    enum class WatchlistTab {
        AUCTIONS,
        ORDERS,
        ALL
    }

    fun open() {
        val watchlistEntries = runBlocking {
            watchlistRepository.getPlayerWatchlist(player.uniqueId)
        }

        // Get active auctions for watched items
        val watchedAuctions = mutableListOf<Pair<Auction, Boolean>>()
        for (entry in watchlistEntries.filter { it.auctionId != null }) {
            val auction = runBlocking { auctionRepository.getById(entry.auctionId!!) }
            if (auction != null && auction.isActive()) {
                watchedAuctions.add(auction to entry.hasNewActivity)
            } else {
                // Remove expired/cancelled auctions from watchlist
                runBlocking { watchlistRepository.remove(player.uniqueId, entry.auctionId!!) }
            }
        }

        // Get active orders for watched items
        val watchedOrders = mutableListOf<Pair<Order, Boolean>>()
        for (entry in watchlistEntries.filter { it.orderId != null }) {
            val order = runBlocking { orderRepository.getById(entry.orderId!!) }
            if (order != null && order.isActive()) {
                watchedOrders.add(order to entry.hasNewActivity)
            } else {
                // Remove expired/cancelled orders from watchlist
                runBlocking { watchlistRepository.removeOrder(player.uniqueId, entry.orderId!!) }
            }
        }

        // Sort the watched auctions based on current sort order
        val sortedAuctions = when (currentSort) {
            WatchlistSort.ENDING_SOON -> watchedAuctions.sortedBy { it.first.endsAt }
            WatchlistSort.PRICE_LOW -> watchedAuctions.sortedBy { 
                it.first.buyNowPrice ?: it.first.startPrice 
            }
            WatchlistSort.PRICE_HIGH -> watchedAuctions.sortedByDescending { 
                it.first.buyNowPrice ?: it.first.startPrice 
            }
            WatchlistSort.RECENTLY_ADDED -> watchedAuctions.sortedByDescending {
                watchlistEntries.find { entry -> entry.auctionId == it.first.id }?.addedAt
                    ?: java.time.Instant.MIN
            }
        }

        // Sort the watched orders based on current sort order
        val sortedOrders = when (currentSort) {
            WatchlistSort.ENDING_SOON -> watchedOrders.sortedBy { it.first.expiresAt }
            WatchlistSort.PRICE_LOW -> watchedOrders.sortedBy { it.first.pricePerUnit }
            WatchlistSort.PRICE_HIGH -> watchedOrders.sortedByDescending { it.first.pricePerUnit }
            WatchlistSort.RECENTLY_ADDED -> watchedOrders.sortedByDescending {
                watchlistEntries.find { entry -> entry.orderId == it.first.id }?.addedAt
                    ?: java.time.Instant.MIN
            }
        }

        val totalCount = when (currentTab) {
            WatchlistTab.AUCTIONS -> watchedAuctions.size
            WatchlistTab.ORDERS -> watchedOrders.size
            WatchlistTab.ALL -> watchedAuctions.size + watchedOrders.size
        }

        val menu = menuAPI.simple {
            rows = 6
            title = mm.deserialize("<yellow>My Watchlist <gray>($totalCount)")

            background = MenuUtils.backgroundItem()

            // Tab buttons
            item(3, createTabButton(WatchlistTab.ALL, watchedAuctions.size + watchedOrders.size))
            item(4, createTabButton(WatchlistTab.AUCTIONS, watchedAuctions.size))
            item(5, createTabButton(WatchlistTab.ORDERS, watchedOrders.size))

            // Clear All button (top right)
            item(8, createClearAllButton(watchedAuctions.size + watchedOrders.size))

            var slotIndex = 0
            val startSlot = 9

            // Display watched auctions
            if (currentTab == WatchlistTab.AUCTIONS || currentTab == WatchlistTab.ALL) {
                sortedAuctions.forEachIndexed { index, (auction, hasActivity) ->
                    if (slotIndex < 36) {
                        val slot = startSlot + slotIndex
                        item(slot, createWatchedAuctionItem(auction, hasActivity))
                        slotIndex++
                    }
                }
            }

            // Display watched orders
            if (currentTab == WatchlistTab.ORDERS || currentTab == WatchlistTab.ALL) {
                sortedOrders.forEachIndexed { index, (order, hasActivity) ->
                    if (slotIndex < 36) {
                        val slot = startSlot + slotIndex
                        item(slot, createWatchedOrderItem(order, hasActivity))
                        slotIndex++
                    }
                }
            }

            // Sort options
            item(48, createSortButton())

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            item(45, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(53, closeItem)

            // Empty state
            if (watchedAuctions.isEmpty() && watchedOrders.isEmpty()) {
                item(22, VItem(XMaterial.BARRIER) {
                    name = mm.deserialize("<red>No Watched Items")
                    lore = mutableListOf(
                        mm.deserialize("<gray>You haven't added any"),
                        mm.deserialize("<gray>auctions or orders to your watchlist."),
                        Component.empty(),
                        mm.deserialize("<green>Click the heart icon"),
                        mm.deserialize("<green>to add items to your watchlist!")
                    )
                })
            }
        }

        menuAPI.open(menu, player)
    }

    private fun createTabButton(tab: WatchlistTab, count: Int): VItem {
        val isSelected = currentTab == tab
        val (name, material) = when (tab) {
            WatchlistTab.ALL -> "All" to XMaterial.CHEST
            WatchlistTab.AUCTIONS -> "Auctions" to XMaterial.GOLD_INGOT
            WatchlistTab.ORDERS -> "Orders" to XMaterial.DIAMOND
        }

        return VItem(material) {
            this.name = mm.deserialize("${if (isSelected) "<green>" else "<gray>"}$name <white>($count)")
            hideAllFlags()

            onClick { _, _ ->
                currentTab = tab
                open()
                ClickResult.ALLOW
            }
        }
    }

    private fun createWatchedOrderItem(order: Order, hasNewActivity: Boolean): VItem {
        val material = XMaterial.matchXMaterial(order.itemMaterial.name).orElse(XMaterial.STONE)
        val isBuyOrder = order.orderType == OrderType.BUY_ORDER

        return VItem(material) {
            name = order.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(order.itemMaterial.name.replace("_", " "))

            val loreList = mutableListOf<Component>()

            // Activity indicator
            if (hasNewActivity) {
                loreList.add(mm.deserialize("<red>⚠ New Activity!"))
                loreList.add(Component.empty())
            }

            // Order type badge
            loreList.add(translationAPI.getComponentSync(
                if (isBuyOrder) GuiMessages.ORDER_TYPE_BUY else GuiMessages.ORDER_TYPE_SELL
            ))

            // Quantity info
            loreList.add(mm.deserialize("<yellow>Quantity: <white>${order.quantityFilled}/${order.quantityRequested}"))

            // Price per unit
            loreList.add(mm.deserialize("<yellow>Price: <gold>${MenuUtils.formatPrice(order.pricePerUnit, economy)}/each"))

            // Time remaining
            val timeLeft = MenuUtils.formatTimeRemaining(order.expiresAt)
            val timeColor = when {
                order.expiresAt.isBefore(java.time.Instant.now().plus(java.time.Duration.ofHours(1))) -> "<red>"
                order.expiresAt.isBefore(java.time.Instant.now().plus(java.time.Duration.ofDays(1))) -> "<yellow>"
                else -> "<green>"
            }
            loreList.add(mm.deserialize("<gray>Time Left: ${timeColor}${timeLeft}"))

            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to fulfill"))
            loreList.add(mm.deserialize("<red>Right-click to remove from watchlist"))

            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    runBlocking {
                        watchlistRepository.removeOrder(player.uniqueId, order.id)
                    }
                    player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                    open()
                } else {
                    OrderFulfillMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player, order).open()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createWatchedAuctionItem(auction: Auction, hasNewActivity: Boolean): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()

            // Activity indicator
            if (hasNewActivity) {
                loreList.add(mm.deserialize("<red>⚠ New Activity!"))
                loreList.add(Component.empty())
            }

            // Price info
            when (auction.auctionType) {
                bruh.auctionhouse.model.AuctionType.AUCTION -> {
                    val highestBid = runBlocking { bidRepository.getHighestBid(auction.id) }
                    val currentBid = highestBid?.bidAmount ?: auction.startPrice
                    loreList.add(mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, economy)}"))
                    highestBid?.let {
                        loreList.add(mm.deserialize("<gray>Highest: <white>${it.bidderName}"))
                    }
                }
                bruh.auctionhouse.model.AuctionType.BIN -> {
                    auction.buyNowPrice?.let { price ->
                        loreList.add(mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(price, economy)}"))
                    }
                }
                bruh.auctionhouse.model.AuctionType.BOTH -> {
                    val highestBid = runBlocking { bidRepository.getHighestBid(auction.id) }
                    val currentBid = highestBid?.bidAmount ?: auction.startPrice
                    loreList.add(mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, economy)}"))
                    auction.buyNowPrice?.let { price ->
                        loreList.add(mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(price, economy)}"))
                    }
                }
            }

            // Time remaining with color coding
            val timeLeft = MenuUtils.formatTimeRemaining(auction.endsAt)
            val timeColor = when {
                auction.endsAt.isBefore(java.time.Instant.now().plus(java.time.Duration.ofHours(1))) -> "<red>"
                auction.endsAt.isBefore(java.time.Instant.now().plus(java.time.Duration.ofDays(1))) -> "<yellow>"
                else -> "<green>"
            }
            loreList.add(mm.deserialize("<gray>Time Left: ${timeColor}${timeLeft}"))

            // Bids
            loreList.add(mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))

            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to view details"))
            loreList.add(mm.deserialize("<red>Right-click to remove from watchlist"))

            lore = loreList
            hideAllFlags()

            onClick { click, _ ->
                if (click.isRightClick) {
                    runBlocking {
                        watchlistRepository.remove(player.uniqueId, auction.id)
                    }
                    player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                    open()
                } else {
                    AuctionDetailsMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player, auction).open()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createClearAllButton(count: Int): VItem {
        return VItem(XMaterial.BARRIER) {
            name = mm.deserialize("<red>Clear All")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Remove all items from"))
            loreList.add(mm.deserialize("<gray>your watchlist"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Currently watching: <white>$count"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<red>Click to clear all"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                val watchlistEntries = runBlocking {
                    watchlistRepository.getPlayerWatchlist(player.uniqueId)
                }
                watchlistEntries.forEach { entry ->
                    runBlocking {
                        if (entry.auctionId != null) {
                            watchlistRepository.remove(player.uniqueId, entry.auctionId)
                        } else if (entry.orderId != null) {
                            watchlistRepository.removeOrder(player.uniqueId, entry.orderId)
                        }
                    }
                }
                player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_CLEARED))
                open()
                ClickResult.CLOSE
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
            name = mm.deserialize("<yellow>Sort Options")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Sort your watchlist"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Current: <white>$displayName"))
            loreList.add(mm.deserialize("<gray>$description"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<white>• Ending Soon</white>"))
            loreList.add(mm.deserialize("<white>• Price Low/High</white>"))
            loreList.add(mm.deserialize("<white>• Recently Added</white>"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through sort options
                currentSort = when (currentSort) {
                    WatchlistSort.ENDING_SOON -> WatchlistSort.PRICE_LOW
                    WatchlistSort.PRICE_LOW -> WatchlistSort.PRICE_HIGH
                    WatchlistSort.PRICE_HIGH -> WatchlistSort.RECENTLY_ADDED
                    WatchlistSort.RECENTLY_ADDED -> WatchlistSort.ENDING_SOON
                }
                open()
                ClickResult.CLOSE
            }
        }
    }
}
