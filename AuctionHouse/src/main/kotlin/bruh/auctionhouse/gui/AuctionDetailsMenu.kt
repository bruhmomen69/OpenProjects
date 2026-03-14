package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.model.Bid
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDoubleAsync
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Menu for viewing auction details and placing bids or buying.
 */
class AuctionDetailsMenu(
    private val pctx: PlayerMenuContext,
    private val auction: Auction
) : SimpleMenu() {

    private var highestBid: Bid? = null
    private var isWatching by menuState(false)

    init {
        rows = 5
        title = pctx.mm.deserialize("<yellow>Auction ${auction.shortId}")
        background = MenuUtils.backgroundItem()

        // Fire-and-forget: increment view count
        asyncData<Unit> {
            load { pctx.auctionRepository.incrementViewCount(auction.id) }
            onLoaded { }
        }

        // Load highest bid for display
        asyncData<Bid?> {
            load { pctx.bidRepository.getHighestBid(auction.id) }
            onLoaded { bid -> highestBid = bid }
        }

        // Load watchlist state
        asyncData<Boolean> {
            load { pctx.watchlistRepository.isWatching(pctx.player.uniqueId, auction.id) }
            onLoaded { watching -> isWatching = watching }
        }
    }

    override fun populateItems() {
        items.clear()

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

        // Cancel button (if owner or admin)
        if (auction.sellerUuid == pctx.player.uniqueId || pctx.player.hasPermission("auctionhouse.admin.cancel")) {
            item(31, createCancelButton())
        }

        // Edit Price button (if owner, auction is active, and no bids)
        if (auction.sellerUuid == pctx.player.uniqueId && auction.isActive() && auction.bidCount == 0) {
            item(21, createEditPriceButton())
        }

        // Extend button (if owner and auction is active)
        if (auction.sellerUuid == pctx.player.uniqueId && auction.isActive()) {
            item(23, createExtendButton())
        }

        // Bid History button (if there are bids)
        if (auction.bidCount > 0 && pctx.config.auctions.display.showBidHistory) {
            item(22, createBidHistoryButton())
        }

        // Watchlist button (heart icon)
        item(20, createWatchlistButton())

        // Back button
        item(36, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        })

        // Close button
        item(44, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }

    private fun createAuctionDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        val hasEnded = auction.hasEnded()

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>ID: <white>${auction.shortId}"))
            loreList.add(pctx.mm.deserialize("<gray>Seller: <white>${if (auction.isAnonymous) "Anonymous" else auction.sellerName}"))

            if (hasEnded) {
                loreList.add(pctx.mm.deserialize("<red>⚠ Auction Ended"))
            }

            if (auction.auctionType == AuctionType.AUCTION || auction.auctionType == AuctionType.BOTH) {
                val currentBid = highestBid?.bidAmount ?: auction.startPrice
                loreList.add(pctx.mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(currentBid, pctx.economy)}"))
                if (highestBid != null) {
                    loreList.add(pctx.mm.deserialize("<gray>Highest Bidder: <white>${highestBid!!.bidderName}"))
                }
                loreList.add(pctx.mm.deserialize("<gray>Increment: <white>${MenuUtils.formatPrice(auction.minIncrement, pctx.economy)}"))
            }

            auction.buyNowPrice?.let {
                loreList.add(pctx.mm.deserialize("<green>Buy Now: <gold>${MenuUtils.formatPrice(it, pctx.economy)}"))
            }

            val timeDisplay = if (hasEnded) {
                pctx.mm.deserialize("<red>Ended: ${formatAuctionEndTime(auction.endsAt)}")
            } else {
                pctx.mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}")
            }
            loreList.add(timeDisplay)
            loreList.add(pctx.mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
            loreList.add(pctx.mm.deserialize("<gray>Views: <white>${auction.viewCount}"))

            lore = loreList
        }
    }

    private fun createBidButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = pctx.mm.deserialize("<green>Place Bid")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Click to place a bid"))
            loreList.add(pctx.mm.deserialize("<gray>Minimum increment: ${MenuUtils.formatPrice(auction.minIncrement, pctx.economy)}"))
            lore = loreList

            onClick { _, _ ->
                pctx.menuAPI.promptDoubleAsync(
                    pctx.player,
                    "Enter Bid Amount",
                    null,
                    auction.startPrice,
                    Double.MAX_VALUE
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                val bidResult = pctx.auctionService.placeBid(pctx.player, auction.id, result.value)
                                pctx.player.sendMessage(bidResult.message)
                            }
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.Deny
            }
        }
    }

    private fun createBuyNowButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = pctx.mm.deserialize("<green>Buy Now")
            val loreList = mutableListOf<Component>()
            auction.buyNowPrice?.let { price ->
                loreList.add(pctx.mm.deserialize("<gray>Price: <gold>${MenuUtils.formatPrice(price, pctx.economy)}"))
            }
            loreList.add(pctx.mm.deserialize("<gray>Click to purchase instantly"))
            lore = loreList

            onClick { _, controls ->
                controls.runAsync(
                    action = { pctx.auctionService.buyNow(pctx.player, auction.id) },
                    onSuccess = { result ->
                        pctx.player.sendMessage(result.message)
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>Cancel Auction")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to cancel this auction"))

            onClick { _, controls ->
                controls.runAsync(
                    action = { pctx.auctionService.cancelAuction(pctx.player, auction.id) },
                    onSuccess = { result ->
                        when (result) {
                            is ServiceResult.Success -> {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED))
                            }
                            is ServiceResult.Failure -> {
                                pctx.player.sendMessage(result.message)
                            }
                        }
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createEditPriceButton(): VItem {
        return VItem(XMaterial.ANVIL) {
            name = pctx.mm.deserialize("<yellow>Edit Prices")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Modify start and BIN prices"))
            loreList.add(pctx.mm.deserialize("<gray>Current start: <gold>${MenuUtils.formatPrice(auction.startPrice, pctx.economy)}"))
            auction.buyNowPrice?.let { binPrice ->
                loreList.add(pctx.mm.deserialize("<gray>Current BIN: <gold>${MenuUtils.formatPrice(binPrice, pctx.economy)}"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to edit prices"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(createEditPriceMenu())
            }
        }
    }

    private fun createEditPriceMenu(): SimpleMenu {
        return SimpleMenu().apply {
            rows = 5
            title = pctx.mm.deserialize("<yellow>Edit Auction Prices")
            background = MenuUtils.backgroundItem()

            // Current prices display
            item(13, VItem(XMaterial.PAPER) {
                name = pctx.mm.deserialize("<yellow>Current Prices")
                val loreList = mutableListOf<Component>()
                loreList.add(pctx.mm.deserialize("<gray>Start Price: <gold>${MenuUtils.formatPrice(auction.startPrice, pctx.economy)}"))
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(pctx.mm.deserialize("<gray>BIN Price: <gold>${MenuUtils.formatPrice(binPrice, pctx.economy)}"))
                }
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<gray>Click buttons below to edit"))
                lore = loreList
                hideAllFlags()
            })

            // Edit start price button
            item(29, VItem(XMaterial.GOLD_NUGGET) {
                name = pctx.mm.deserialize("<yellow>Edit Start Price")
                lore = mutableListOf(pctx.mm.deserialize("<gray>Current: ${MenuUtils.formatPrice(auction.startPrice, pctx.economy)}"))
                hideAllFlags()

                onClick { _, _ ->
                    pctx.menuAPI.promptDoubleAsync(
                        pctx.player,
                        "New Start Price",
                        auction.startPrice,
                        pctx.config.auctions.minStartPrice,
                        pctx.config.auctions.maxStartPrice
                    ).thenAccept { inputResult ->
                        when (inputResult) {
                            is AnvilInputResult.Success -> {
                                handlePriceUpdate(inputResult.value, auction.buyNowPrice)
                            }
                            is AnvilInputResult.Cancelled -> {
                                pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                    pctx.menuAPI.open(createEditPriceMenu(), pctx.player)
                                })
                            }
                        }
                    }
                    ClickResult.Deny
                }
            })

            // Edit BIN price button
            item(31, VItem(XMaterial.EMERALD) {
                name = pctx.mm.deserialize("<yellow>Edit BIN Price")
                val loreList = mutableListOf<Component>()
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(pctx.mm.deserialize("<gray>Current: ${MenuUtils.formatPrice(binPrice, pctx.economy)}"))
                } ?: run {
                    loreList.add(pctx.mm.deserialize("<gray>Not set"))
                }
                loreList.add(pctx.mm.deserialize("<gray>Click to set/clear"))
                lore = loreList
                hideAllFlags()

                onClick { _, controls ->
                    val currentBin = auction.buyNowPrice
                    if (currentBin != null) {
                        // Clear BIN price — menu still open, use controls
                        controls.runAsync(
                            action = { pctx.auctionService.editAuctionPrice(pctx.player, auction.id, null, null) },
                            onSuccess = { result ->
                                when (result) {
                                    is ServiceResult.Success -> {
                                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.BIN_PRICE_REMOVED))
                                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.PRICES_UPDATED))
                                        pctx.menuAPI.open(AuctionDetailsMenu(pctx, auction), pctx.player)
                                    }
                                    is ServiceResult.Failure -> {
                                        pctx.player.sendMessage(result.message)
                                        controls.close()
                                    }
                                }
                            }
                        )
                    } else {
                        // Set BIN price via prompt
                        pctx.menuAPI.promptDoubleAsync(
                            pctx.player,
                            "New BIN Price",
                            auction.startPrice * 1.5,
                            auction.startPrice * pctx.config.auctions.minBinMultiplier,
                            pctx.config.auctions.maxStartPrice
                        ).thenAccept { inputResult ->
                            when (inputResult) {
                                is AnvilInputResult.Success -> {
                                    handlePriceUpdate(null, inputResult.value)
                                }
                                is AnvilInputResult.Cancelled -> {
                                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                        pctx.menuAPI.open(createEditPriceMenu(), pctx.player)
                                    })
                                }
                            }
                        }
                    }
                    ClickResult.Deny
                }
            })

            // Back button
            item(40, MenuUtils.backButton(pctx.translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(this@AuctionDetailsMenu)
                }
            })
        }
    }

    private fun handlePriceUpdate(newStart: Double?, newBin: Double?) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = pctx.auctionService.editAuctionPrice(pctx.player, auction.id, newStart, newBin)
            pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                when (result) {
                    is ServiceResult.Success -> {
                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.PRICES_UPDATED))
                        pctx.menuAPI.open(AuctionDetailsMenu(pctx, auction), pctx.player)
                    }
                    is ServiceResult.Failure -> {
                        pctx.player.sendMessage(result.message)
                    }
                }
            })
        }
    }

    private fun createExtendButton(): VItem {
        val extensionHours = pctx.config.auctions.manualExtension.extensionHours
        val extensionFee = pctx.config.auctions.manualExtension.extensionFee

        return VItem(XMaterial.CLOCK) {
            name = pctx.mm.deserialize("<yellow>Extend Auction")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Extend by: <white>${extensionHours} hours"))
            loreList.add(pctx.mm.deserialize("<gray>Extension fee: <gold>${MenuUtils.formatPrice(extensionFee, pctx.economy)}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to extend auction"))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                controls.runAsync(
                    action = {
                        if (!pctx.economy.has(pctx.player, BigDecimal.valueOf(extensionFee))) {
                            return@runAsync null as Instant?
                        }
                        val currentManualExtensions = pctx.auctionRepository.getManualExtensionCount(auction.id)
                        if (currentManualExtensions >= pctx.config.auctions.manualExtension.maxManualExtensions) {
                            return@runAsync null as Instant?
                        }
                        pctx.economy.withdraw(pctx.player, BigDecimal.valueOf(extensionFee))
                        val newEndTime = auction.endsAt.plus(Duration.ofHours(extensionHours.toLong()))
                        pctx.auctionRepository.updateEndTime(auction.id, newEndTime)
                        pctx.auctionRepository.incrementManualExtensionCount(auction.id)
                        newEndTime
                    },
                    onSuccess = { newEndTime ->
                        if (newEndTime != null) {
                            pctx.player.sendMessage(
                                pctx.mm.deserialize("<green>Auction extended by <white>${extensionHours} hours</white>! New end time: <yellow>$newEndTime")
                            )
                            pctx.menuAPI.open(AuctionDetailsMenu(pctx, auction), pctx.player)
                        } else {
                            // Determine which check failed and notify
                            if (!pctx.economy.has(pctx.player, BigDecimal.valueOf(extensionFee))) {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.INSUFFICIENT_FUNDS_EXTENSION))
                            } else {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.MAX_EXTENSION_REACHED) {
                                    unparsed("max", pctx.config.auctions.manualExtension.maxManualExtensions.toString())
                                })
                            }
                        }
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createBidHistoryButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = pctx.mm.deserialize("<yellow>Bid History")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Click to view bid history"))
            loreList.add(pctx.mm.deserialize("<gray>Total bids: <white>${auction.bidCount}"))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                controls.runAsync(
                    action = {
                        pctx.bidRepository.getBidHistory(auction.id, pctx.config.auctions.display.maxBidHistory)
                    },
                    onSuccess = { bidHistory ->
                        if (bidHistory.isEmpty()) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.NO_BID_HISTORY))
                            return@runAsync
                        }
                        pctx.menuAPI.open(buildBidHistoryMenu(bidHistory), pctx.player)
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun buildBidHistoryMenu(bidHistory: List<Bid>): SimpleMenu {
        return SimpleMenu().apply {
            rows = 6
            title = pctx.mm.deserialize("<yellow>Bid History - ${auction.itemDisplayName ?: auction.itemMaterial}")
            background = MenuUtils.backgroundItem()

            bidHistory.forEachIndexed { index, bid ->
                val slot = when {
                    index < 7 -> 10 + index
                    index < 14 -> 19 + (index - 7)
                    index < 21 -> 28 + (index - 14)
                    else -> 37 + (index - 21)
                }

                val isPlayerBid = bid.bidderUuid == pctx.player.uniqueId
                val isActive = !bid.isOutbid

                val canWithdraw = isPlayerBid && isActive && run {
                    val cutoffMinutes = pctx.config.auctions.bidWithdrawal.cutoffMinutes
                    if (cutoffMinutes <= 0) true
                    else {
                        val timeRemaining = Duration.between(Instant.now(), auction.endsAt)
                        timeRemaining.toMinutes() >= cutoffMinutes
                    }
                }

                item(slot, VItem(XMaterial.PAPER) {
                    name = pctx.mm.deserialize("<gold>Bid: ${MenuUtils.formatPrice(bid.bidAmount, pctx.economy)}")
                    val loreList = mutableListOf<Component>()
                    loreList.add(pctx.mm.deserialize("<gray>Bidder: <white>${bid.bidderName}"))
                    loreList.add(pctx.mm.deserialize("<gray>Time: <white>${formatBidTime(bid.bidTime)}"))
                    loreList.add(pctx.mm.deserialize("<gray>Status: ${if (bid.isOutbid) "<red>Outbid" else "<green>Active"}"))
                    if (isPlayerBid && isActive) {
                        loreList.add(Component.empty())
                        if (canWithdraw) {
                            loreList.add(pctx.mm.deserialize("<green>Click to withdraw your bid"))
                        } else {
                            val cutoffMinutes = pctx.config.auctions.bidWithdrawal.cutoffMinutes
                            loreList.add(pctx.mm.deserialize("<red>Withdrawal locked (last $cutoffMinutes min)"))
                        }
                    }
                    lore = loreList

                    if (canWithdraw) {
                        onClick { _, controls ->
                            controls.runAsync(
                                action = {
                                    val cutoffMinutes = pctx.config.auctions.bidWithdrawal.cutoffMinutes
                                    if (cutoffMinutes > 0) {
                                        val timeRemaining = Duration.between(Instant.now(), auction.endsAt)
                                        if (timeRemaining.toMinutes() < cutoffMinutes) {
                                            return@runAsync false
                                        }
                                    }

                                    val refundAmount = pctx.bidRepository.deleteBid(bid.id)
                                    if (refundAmount != null) {
                                        pctx.economy.deposit(pctx.player, BigDecimal.valueOf(refundAmount))
                                        pctx.auctionRepository.decrementBidCount(auction.id)

                                        pctx.player.sendMessage(
                                            pctx.mm.deserialize("<green>Bid withdrawn! ${MenuUtils.formatPrice(refundAmount, pctx.economy)} has been refunded.")
                                        )

                                        val newHighestBid = pctx.bidRepository.getHighestBid(auction.id)
                                        if (newHighestBid != null) {
                                            pctx.plugin.server.getPlayer(newHighestBid.bidderUuid)?.let { newWinner ->
                                                newWinner.sendMessage(
                                                    pctx.translationAPI.getComponentSync(AuctionMessages.BID_NOW_HIGHEST) {
                                                        unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                                        unparsed("amount", MenuUtils.formatPrice(newHighestBid.bidAmount, pctx.economy))
                                                    }
                                                )
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                                onSuccess = { success ->
                                    if (!success) {
                                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.BID_WITHDRAW_FAILED))
                                    }
                                    controls.close()
                                }
                            )
                            ClickResult.Deny
                        }
                    }
                })
            }

            // Back button
            item(49, MenuUtils.backButton(pctx.translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(AuctionDetailsMenu(pctx, auction))
                }
            })
        }
    }

    private fun createWatchlistButton(): VItem {
        return VItem(if (isWatching) XMaterial.RED_DYE else XMaterial.GRAY_DYE) {
            name = pctx.mm.deserialize(if (isWatching) "<red>❤ Watching" else "<gray>♡ Add to Watchlist")
            val loreList = mutableListOf<Component>()
            if (isWatching) {
                loreList.add(pctx.mm.deserialize("<gray>This auction is in your"))
                loreList.add(pctx.mm.deserialize("<gray>watchlist"))
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<red>Click to remove"))
            } else {
                loreList.add(pctx.mm.deserialize("<gray>Get notified about:"))
                loreList.add(pctx.mm.deserialize("<gray>• New bids"))
                loreList.add(pctx.mm.deserialize("<gray>• Price drops"))
                loreList.add(pctx.mm.deserialize("<gray>• Ending soon"))
                loreList.add(Component.empty())
                loreList.add(pctx.mm.deserialize("<green>Click to add to watchlist"))
            }
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                val wasWatching = isWatching
                controls.runAsync(
                    action = {
                        if (wasWatching) {
                            pctx.watchlistRepository.remove(pctx.player.uniqueId, auction.id)
                        } else {
                            pctx.watchlistRepository.add(pctx.player.uniqueId, auction.id)
                        }
                    },
                    onSuccess = {
                        if (wasWatching) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_REMOVED))
                        } else {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.WATCHLIST_ADDED))
                        }
                        isWatching = !wasWatching
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun formatBidTime(time: Instant): String {
        val duration = Duration.between(time, Instant.now())
        return when {
            duration.toHours() > 24 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "Just now"
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
}
