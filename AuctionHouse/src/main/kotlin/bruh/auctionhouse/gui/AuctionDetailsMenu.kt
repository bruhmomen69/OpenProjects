package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.math.BigDecimal

/**
 * Menu for viewing auction details and placing bids or buying.
 */
class AuctionDetailsMenu(
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
    private val player: Player,
    private val auction: Auction
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        // Increment view count
        runBlocking {
            auctionRepository.incrementViewCount(auction.id)
        }

        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)

            background = MenuUtils.backgroundItem()

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

            // Cancel button (if owner)
            if (auction.sellerUuid == player.uniqueId || player.hasPermission("auctionhouse.admin.cancel")) {
                item(31, createCancelButton())
            }

            // Extend button (if owner and auction is active)
            if (auction.sellerUuid == player.uniqueId && auction.isActive()) {
                item(23, createExtendButton())
            }

            // Bid History button (if there are bids)
            if (auction.bidCount > 0 && config.auctions.display.showBidHistory) {
                item(22, createBidHistoryButton())
            }

            // Watchlist button (heart icon)
            item(20, createWatchlistButton())

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            item(36, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(44, closeItem)
        }

        menuAPI.open(menu, player)
    }

    private fun createBidHistoryButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = mm.deserialize("<yellow>Bid History")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to view bid history"))
            loreList.add(mm.deserialize("<gray>Total bids: <white>${auction.bidCount}"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                openBidHistoryMenu()
                ClickResult.CLOSE
            }
        }
    }

    private fun openBidHistoryMenu() {
        val bidHistory = runBlocking {
            bidRepository.getBidHistory(auction.id, config.auctions.display.maxBidHistory)
        }

        if (bidHistory.isEmpty()) {
            player.sendMessage(mm.deserialize("<red>No bid history available."))
            return
        }

        // Check if player has an active bid on this auction
        val playerActiveBid = runBlocking {
            bidRepository.getPlayerActiveBid(player.uniqueId, auction.id)
        }

        val menu = menuAPI.simple {
            rows = 6
            title = mm.deserialize("<yellow>Bid History - ${auction.itemDisplayName ?: auction.itemMaterial}")

            background = MenuUtils.backgroundItem()

            // Display bid history items
            bidHistory.forEachIndexed { index, bid ->
                val slot = when {
                    index < 7 -> 10 + index
                    index < 14 -> 19 + (index - 7)
                    index < 21 -> 28 + (index - 14)
                    else -> 37 + (index - 21)
                }

                val isPlayerBid = bid.bidderUuid == player.uniqueId
                val isActive = !bid.isOutbid

                item(slot, VItem(XMaterial.PAPER) {
                    name = mm.deserialize("<gold>Bid: ${MenuUtils.formatPrice(bid.bidAmount, plugin.economy)}")
                    val loreList = mutableListOf<Component>()
                    loreList.add(mm.deserialize("<gray>Bidder: <white>${bid.bidderName}"))
                    loreList.add(mm.deserialize("<gray>Time: <white>${formatBidTime(bid.bidTime)}"))
                    loreList.add(mm.deserialize("<gray>Status: ${if (bid.isOutbid) "<red>Outbid" else "<green>Active"}"))
                    if (isPlayerBid && isActive) {
                        loreList.add(Component.empty())
                        loreList.add(mm.deserialize("<yellow>Click to withdraw your bid"))
                    }
                    lore = loreList

                    if (isPlayerBid && isActive) {
                        onClick { _, _ ->
                            withdrawBid(bid)
                            ClickResult.CLOSE
                        }
                    }
                })
            }

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    open()
                    ClickResult.CLOSE
                }
            }
            item(49, backItem)
        }

        menuAPI.open(menu, player)
    }

    private fun withdrawBid(bid: bruh.auctionhouse.model.Bid) {
        runBlocking {
            // Get the bid amount for refund
            val refundAmount = bidRepository.deleteBid(bid.id)

            if (refundAmount != null) {
                // Refund the player
                economy.deposit(player, java.math.BigDecimal.valueOf(refundAmount))

                // Decrement bid count
                auctionRepository.decrementBidCount(auction.id)

                player.sendMessage(
                    mm.deserialize("<green>Bid withdrawn! ${MenuUtils.formatPrice(refundAmount, plugin.economy)} has been refunded.")
                )
            } else {
                player.sendMessage(mm.deserialize("<red>Failed to withdraw bid."))
            }
        }
    }

    private fun createExtendButton(): VItem {
        val extensionHours = 24 // Default extension: 24 hours
        val extensionFee = 100.0 // Fixed fee for extension

        return VItem(XMaterial.CLOCK) {
            name = mm.deserialize("<yellow>Extend Auction")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Extend by: <white>${extensionHours} hours"))
            loreList.add(mm.deserialize("<gray>Extension fee: <gold>${MenuUtils.formatPrice(extensionFee, plugin.economy)}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to extend auction"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                extendAuction(extensionHours, extensionFee)
                ClickResult.CLOSE
            }
        }
    }

    private fun extendAuction(hours: Int, fee: Double) {
        runBlocking {
            // Check if player can afford the fee
            if (!economy.has(player, java.math.BigDecimal.valueOf(fee))) {
                player.sendMessage(mm.deserialize("<red>You don't have enough money for the extension fee."))
                return@runBlocking
            }

            // Check extension count
            val currentExtensions = auctionRepository.getExtensionCount(auction.id)
            if (currentExtensions >= config.auctions.antiSnipe.maxExtensions) {
                player.sendMessage(mm.deserialize("<red>Maximum extension limit reached."))
                return@runBlocking
            }

            // Charge fee
            economy.withdraw(player, java.math.BigDecimal.valueOf(fee))

            // Extend auction
            val newEndTime = auction.endsAt.plus(java.time.Duration.ofHours(hours.toLong()))
            auctionRepository.updateEndTime(auction.id, newEndTime)
            auctionRepository.incrementExtensionCount(auction.id)

            player.sendMessage(
                mm.deserialize("<green>Auction extended by <white>${hours} hours</white>! New end time: <yellow>$newEndTime")
            )
        }
    }

    private fun formatBidTime(time: java.time.Instant): String {
        val duration = java.time.Duration.between(time, java.time.Instant.now())
        return when {
            duration.toHours() > 24 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "Just now"
        }
    }

    private fun createAuctionDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Seller: <white>${if (auction.isAnonymous) "Anonymous" else auction.sellerName}"))

            if (auction.auctionType == AuctionType.AUCTION || auction.auctionType == AuctionType.BOTH) {
                // Fetch the actual highest bid
                val highestBid = runBlocking { bidRepository.getHighestBid(auction.id) }
                val currentBid = highestBid?.bidAmount ?: auction.startPrice
                loreList.add(mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, plugin.economy)}"))
                if (highestBid != null) {
                    loreList.add(mm.deserialize("<gray>Highest Bidder: <white>${highestBid.bidderName}"))
                }
                loreList.add(mm.deserialize("<gray>Increment: <white>${MenuUtils.formatPrice(auction.minIncrement, plugin.economy)}"))
            }

            auction.buyNowPrice?.let {
                loreList.add(mm.deserialize("<green>Buy Now: <gold>${MenuUtils.formatPrice(it, plugin.economy)}"))
            }

            loreList.add(mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}"))
            loreList.add(mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
            loreList.add(mm.deserialize("<gray>Views: <white>${auction.viewCount}"))

            lore = loreList
        }
    }

    private fun createBidButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = mm.deserialize("<green>Place Bid")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to place a bid"))
            loreList.add(mm.deserialize("<gray>Minimum increment: ${MenuUtils.formatPrice(auction.minIncrement, plugin.economy)}"))
            lore = loreList

            onClick { _, _ ->
                // Use runBlocking since we're in a non-suspend context
                runBlocking {
                    val result = menuAPI.promptDouble(
                        player,
                        "Enter Bid Amount",
                        null,
                        auction.startPrice,
                        Double.MAX_VALUE
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val bidResult = auctionService.placeBid(player, auction.id, result.value)
                            player.sendMessage(bidResult.message)
                        }
                        is AnvilInputResult.Cancelled -> {
                            // User cancelled, do nothing
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createBuyNowButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Buy Now")
            val loreList = mutableListOf<Component>()
            auction.buyNowPrice?.let { price ->
                loreList.add(mm.deserialize("<gray>Price: <gold>${MenuUtils.formatPrice(price, plugin.economy)}"))
            }
            loreList.add(mm.deserialize("<gray>Click to purchase instantly"))
            lore = loreList

            onClick { _, _ ->
                runBlocking {
                    val result = auctionService.buyNow(player, auction.id)
                    player.sendMessage(result.message)
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = mm.deserialize("<red>Cancel Auction")
            lore = mutableListOf(mm.deserialize("<gray>Click to cancel this auction"))

            onClick { _, _ ->
                runBlocking {
                    val result = auctionService.cancelAuction(player, auction.id)
                    when (result) {
                        is bruh.auctionhouse.service.ServiceResult.Success -> {
                            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED))
                        }
                        is bruh.auctionhouse.service.ServiceResult.Failure -> {
                            player.sendMessage(result.message)
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createWatchlistButton(): VItem {
        val isWatching = runBlocking {
            watchlistRepository.isWatching(player.uniqueId, auction.id)
        }

        return VItem(if (isWatching) XMaterial.RED_DYE else XMaterial.GRAY_DYE) {
            name = mm.deserialize(if (isWatching) "<red>❤ Watching" else "<gray>♡ Add to Watchlist")
            val loreList = mutableListOf<Component>()
            if (isWatching) {
                loreList.add(mm.deserialize("<gray>This auction is in your"))
                loreList.add(mm.deserialize("<gray>watchlist"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<red>Click to remove"))
            } else {
                loreList.add(mm.deserialize("<gray>Get notified about:"))
                loreList.add(mm.deserialize("<gray>• New bids"))
                loreList.add(mm.deserialize("<gray>• Price drops"))
                loreList.add(mm.deserialize("<gray>• Ending soon"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<green>Click to add to watchlist"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    if (isWatching) {
                        watchlistRepository.remove(player.uniqueId, auction.id)
                        player.sendMessage(mm.deserialize("<yellow>Removed from watchlist."))
                    } else {
                        watchlistRepository.add(player.uniqueId, auction.id)
                        player.sendMessage(mm.deserialize("<green>Added to watchlist!"))
                    }
                }
                open()
                ClickResult.CLOSE
            }
        }
    }
}
