package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
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
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val watchlistEntries = runBlocking {
            watchlistRepository.getPlayerWatchlist(player.uniqueId)
        }

        // Get active auctions for watched items
        val watchedAuctions = mutableListOf<Pair<Auction, Boolean>>()
        for (entry in watchlistEntries) {
            val auction = runBlocking { auctionRepository.getById(entry.auctionId) }
            if (auction != null && auction.isActive()) {
                watchedAuctions.add(auction to entry.hasNewActivity)
            } else {
                // Remove expired/cancelled auctions from watchlist
                runBlocking { watchlistRepository.remove(player.uniqueId, entry.auctionId) }
            }
        }

        val menu = menuAPI.simple {
            rows = 6
            title = mm.deserialize("<yellow>My Watchlist <gray>(${watchedAuctions.size})")

            background = MenuUtils.backgroundItem()

            // Clear All button (top right)
            item(8, createClearAllButton(watchedAuctions.size))

            // Display watched auctions
            watchedAuctions.forEachIndexed { index, (auction, hasActivity) ->
                if (index < 42) { // Max 42 items (rows 1-4)
                    val slot = when {
                        index < 9 -> 9 + index
                        index < 18 -> 18 + (index - 9)
                        index < 27 -> 27 + (index - 18)
                        index < 36 -> 36 + (index - 27)
                        else -> 45 + (index - 36)
                    }
                    item(slot, createWatchedAuctionItem(auction, hasActivity))
                }
            }

            // Sort options
            item(48, createSortButton())

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
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
            if (watchedAuctions.isEmpty()) {
                item(22, VItem(XMaterial.BARRIER) {
                    name = mm.deserialize("<red>No Watched Auctions")
                    lore = mutableListOf(
                        mm.deserialize("<gray>You haven't added any"),
                        mm.deserialize("<gray>auctions to your watchlist."),
                        Component.empty(),
                        mm.deserialize("<green>Click an auction's heart icon"),
                        mm.deserialize("<green>to add it to your watchlist!")
                    )
                })
            }
        }

        menuAPI.open(menu, player)
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
                    loreList.add(mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(auction.buyNowPrice!!, economy)}"))
                }
                bruh.auctionhouse.model.AuctionType.BOTH -> {
                    val highestBid = runBlocking { bidRepository.getHighestBid(auction.id) }
                    val currentBid = highestBid?.bidAmount ?: auction.startPrice
                    loreList.add(mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, economy)}"))
                    loreList.add(mm.deserialize("<green>BIN: <gold>${MenuUtils.formatPrice(auction.buyNowPrice!!, economy)}"))
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
                    player.sendMessage(mm.deserialize("<yellow>Removed from watchlist."))
                    open()
                } else {
                    AuctionDetailsMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player, auction).open()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createClearAllButton(count: Int): VItem {
        return VItem(XMaterial.BARRIER) {
            name = mm.deserialize("<red>Clear All")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Remove all auctions from"))
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
                        watchlistRepository.remove(player.uniqueId, entry.auctionId)
                    }
                }
                player.sendMessage(mm.deserialize("<green>Cleared all watched auctions."))
                open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createSortButton(): VItem {
        return VItem(XMaterial.COMPASS) {
            name = mm.deserialize("<yellow>Sort Options")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Sort your watchlist"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<white>• Ending Soon</white>"))
            loreList.add(mm.deserialize("<white>• Price Low/High</white>"))
            loreList.add(mm.deserialize("<white>• Recently Added</white>"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to cycle"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                // For now, just show a message - sorting can be implemented
                player.sendMessage(mm.deserialize("<yellow>Sort feature coming soon!"))
                ClickResult.ALLOW
            }
        }
    }
}
