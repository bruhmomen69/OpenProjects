package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
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
import java.time.Duration
import java.time.Instant

/**
 * Menu for viewing auction details and placing bids or buying.
 */
class AuctionDetailsMenu(
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
    private val player: Player,
    private val auction: Auction
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        runBlocking {
            auctionRepository.incrementViewCount(auction.id)
        }

        val menu = menuAPI.simple {
            rows = 5
            title = mm.deserialize("<yellow>Auction ${auction.shortId}")

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

            // Edit Price button (if owner, auction is active, and no bids)
            if (auction.sellerUuid == player.uniqueId && auction.isActive() && auction.bidCount == 0) {
                item(21, createEditPriceButton())
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
                    AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
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
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.NO_BID_HISTORY))
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
                
                // Check if withdrawal is allowed based on cutoff
                val canWithdraw = isPlayerBid && isActive && run {
                    val cutoffMinutes = config.auctions.bidWithdrawal.cutoffMinutes
                    if (cutoffMinutes <= 0) true
                    else {
                        val timeRemaining = java.time.Duration.between(java.time.Instant.now(), auction.endsAt)
                        timeRemaining.toMinutes() >= cutoffMinutes
                    }
                }

                item(slot, VItem(XMaterial.PAPER) {
                    name = mm.deserialize("<gold>Bid: ${MenuUtils.formatPrice(bid.bidAmount, plugin.economy)}")
                    val loreList = mutableListOf<Component>()
                    loreList.add(mm.deserialize("<gray>Bidder: <white>${bid.bidderName}"))
                    loreList.add(mm.deserialize("<gray>Time: <white>${formatBidTime(bid.bidTime)}"))
                    loreList.add(mm.deserialize("<gray>Status: ${if (bid.isOutbid) "<red>Outbid" else "<green>Active"}"))
                    if (isPlayerBid && isActive) {
                        loreList.add(Component.empty())
                        if (canWithdraw) {
                            loreList.add(mm.deserialize("<green>Click to withdraw your bid"))
                        } else {
                            val cutoffMinutes = config.auctions.bidWithdrawal.cutoffMinutes
                            loreList.add(mm.deserialize("<red>Withdrawal locked (last $cutoffMinutes min)"))
                        }
                    }
                    lore = loreList

                    if (canWithdraw) {
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
            val cutoffMinutes = config.auctions.bidWithdrawal.cutoffMinutes
            if (cutoffMinutes > 0) {
                val timeRemaining = java.time.Duration.between(java.time.Instant.now(), auction.endsAt)
                if (timeRemaining.toMinutes() < cutoffMinutes) {
                    player.sendMessage(
                        mm.deserialize("<red>Bid withdrawals are not allowed in the last $cutoffMinutes minutes of an auction.")
                    )
                    return@runBlocking
                }
            }

            val refundAmount = bidRepository.deleteBid(bid.id)

            if (refundAmount != null) {
                economy.deposit(player, java.math.BigDecimal.valueOf(refundAmount))

                auctionRepository.decrementBidCount(auction.id)

                player.sendMessage(
                    mm.deserialize("<green>Bid withdrawn! ${MenuUtils.formatPrice(refundAmount, plugin.economy)} has been refunded.")
                )

                val newHighestBid = bidRepository.getHighestBid(auction.id)
                if (newHighestBid != null) {
                    plugin.server.getPlayer(newHighestBid.bidderUuid)?.let { newWinner ->
                        newWinner.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.BID_NOW_HIGHEST) {
                                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                unparsed("amount", MenuUtils.formatPrice(newHighestBid.bidAmount, plugin.economy))
                            }
                        )
                    }
                }
            } else {
                player.sendMessage(translationAPI.getComponentSync(AuctionMessages.BID_WITHDRAW_FAILED))
            }
        }
    }

    private fun createExtendButton(): VItem {
        val extensionHours = config.auctions.manualExtension.extensionHours
        val extensionFee = config.auctions.manualExtension.extensionFee

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
                player.sendMessage(translationAPI.getComponentSync(AuctionMessages.INSUFFICIENT_FUNDS_EXTENSION))
                return@runBlocking
            }

            // Check manual extension count (separate from anti-snipe auto extensions)
            val currentManualExtensions = auctionRepository.getManualExtensionCount(auction.id)
            if (currentManualExtensions >= config.auctions.manualExtension.maxManualExtensions) {
                player.sendMessage(translationAPI.getComponentSync(AuctionMessages.MAX_EXTENSION_REACHED) {
                    unparsed("max", config.auctions.manualExtension.maxManualExtensions.toString())
                })
                return@runBlocking
            }

            // Charge fee
            economy.withdraw(player, java.math.BigDecimal.valueOf(fee))

            // Extend auction
            val newEndTime = auction.endsAt.plus(java.time.Duration.ofHours(hours.toLong()))
            auctionRepository.updateEndTime(auction.id, newEndTime)
            auctionRepository.incrementManualExtensionCount(auction.id)

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
        val hasEnded = auction.hasEnded()

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>ID: <white>${auction.shortId}"))
            loreList.add(mm.deserialize("<gray>Seller: <white>${if (auction.isAnonymous) "Anonymous" else auction.sellerName}"))

            if (hasEnded) {
                loreList.add(mm.deserialize("<red>⚠ Auction Ended"))
            }

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

            val timeDisplay = if (hasEnded) {
                mm.deserialize("<red>Ended: ${formatAuctionEndTime(auction.endsAt)}")
            } else {
                mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}")
            }
            loreList.add(timeDisplay)
            loreList.add(mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
            loreList.add(mm.deserialize("<gray>Views: <white>${auction.viewCount}"))

            lore = loreList
        }
    }

    private fun formatAuctionEndTime(endTime: Instant): String {
        val duration = Duration.between(endTime, Instant.now())
        return when {
            duration.toDays() > 0 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "Just now"
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

    private fun createEditPriceButton(): VItem {
        return VItem(XMaterial.ANVIL) {
            name = mm.deserialize("<yellow>Edit Prices")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Modify start and BIN prices"))
            loreList.add(mm.deserialize("<gray>Current start: <gold>${MenuUtils.formatPrice(auction.startPrice, plugin.economy)}"))
            auction.buyNowPrice?.let { binPrice ->
                loreList.add(mm.deserialize("<gray>Current BIN: <gold>${MenuUtils.formatPrice(binPrice, plugin.economy)}"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to edit prices"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                openEditPriceMenu()
                ClickResult.CLOSE
            }
        }
    }

    private fun openEditPriceMenu() {
        val menu = menuAPI.simple {
            rows = 5
            title = mm.deserialize("<yellow>Edit Auction Prices")

            background = MenuUtils.backgroundItem()

            // Current prices display
            item(13, VItem(XMaterial.PAPER) {
                name = mm.deserialize("<yellow>Current Prices")
                val loreList = mutableListOf<Component>()
                loreList.add(mm.deserialize("<gray>Start Price: <gold>${MenuUtils.formatPrice(auction.startPrice, plugin.economy)}"))
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(mm.deserialize("<gray>BIN Price: <gold>${MenuUtils.formatPrice(binPrice, plugin.economy)}"))
                }
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<gray>Click buttons below to edit"))
                lore = loreList
                hideAllFlags()
            })

            // Edit start price button
            item(29, VItem(XMaterial.GOLD_NUGGET) {
                name = mm.deserialize("<yellow>Edit Start Price")
                lore = mutableListOf(mm.deserialize("<gray>Current: ${MenuUtils.formatPrice(auction.startPrice, plugin.economy)}"))
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptDouble(
                            player,
                            "New Start Price",
                            auction.startPrice,
                            config.auctions.minStartPrice,
                            config.auctions.maxStartPrice
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                updatePrices(result.value, auction.buyNowPrice)
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    ClickResult.CLOSE
                }
            })

            // Edit BIN price button
            item(31, VItem(XMaterial.EMERALD) {
                name = mm.deserialize("<yellow>Edit BIN Price")
                val loreList = mutableListOf<Component>()
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(mm.deserialize("<gray>Current: ${MenuUtils.formatPrice(binPrice, plugin.economy)}"))
                } ?: run {
                    loreList.add(mm.deserialize("<gray>Not set"))
                }
                loreList.add(mm.deserialize("<gray>Click to set/clear"))
                lore = loreList
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val currentBin = auction.buyNowPrice
                        if (currentBin != null) {
                            // Clear BIN price
                            updatePrices(null, null)
                            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.BIN_PRICE_REMOVED))
                        } else {
                            // Set BIN price
                            val result = menuAPI.promptDouble(
                                player,
                                "New BIN Price",
                                auction.startPrice * 1.5,
                                auction.startPrice * config.auctions.minBinMultiplier,
                                config.auctions.maxStartPrice
                            )
                            when (result) {
                                is AnvilInputResult.Success -> {
                                    updatePrices(null, result.value)
                                }
                                is AnvilInputResult.Cancelled -> {}
                            }
                        }
                    }
                    ClickResult.CLOSE
                }
            })

            // Back button
            item(40, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    open()
                    ClickResult.CLOSE
                }
            })
        }

        menuAPI.open(menu, player)
    }

    private fun updatePrices(newStart: Double?, newBin: Double?) {
        runBlocking {
            val result = auctionService.editAuctionPrice(player, auction.id, newStart, newBin)
            when (result) {
                is bruh.auctionhouse.service.ServiceResult.Success -> {
                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.PRICES_UPDATED))
                    open()
                }
                is bruh.auctionhouse.service.ServiceResult.Failure -> {
                    player.sendMessage(result.message)
                }
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
                        player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                    } else {
                        watchlistRepository.add(player.uniqueId, auction.id)
                        player.sendMessage(translationAPI.getComponentSync(GuiMessages.WATCHLIST_ADDED))
                    }
                }
                open()
                ClickResult.CLOSE
            }
        }
    }
}
