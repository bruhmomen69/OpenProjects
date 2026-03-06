package bruh.auctionhouse.service

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.config.FeeConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.model.Bid
import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
import bruh.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.math.BigDecimal
import java.time.Duration
import bruh.zchat.utils.database.Database
import java.time.Instant
import java.util.UUID

/**
 * Service layer for auction business logic.
 * Handles creation, bidding, purchasing, and management of auctions.
 */
class AuctionService(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val database: Database,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    private val expiredItemManager: ExpiredItemManager,
    private val transactionRepository: TransactionRepository,
    private val economy: EconomyProvider,
    private val translationAPI: TranslationAPI,
    private val serverId: String
) {
    private val mm = MiniMessage.miniMessage()
    private val logger = plugin.slF4JLogger

    /**
     * Creates a new auction listing.
     *
     * @param seller The player creating the auction
     * @param item The item being auctioned
     * @param type The type of auction (AUCTION, BIN, or BOTH)
     * @param startPrice The starting bid price
     * @param binPrice The buy-it-now price (null if not available)
     * @param duration How long the auction will run
     * @param anonymous Whether to hide the seller's identity
     * @return The result of the creation attempt
     */
    suspend fun createAuction(
        seller: Player,
        item: ItemStack,
        type: AuctionType,
        startPrice: Double,
        binPrice: Double?,
        duration: Duration,
        anonymous: Boolean
    ): CreateAuctionResult = withContext(Dispatchers.IO) {
        // Validate item
        if (item.type.isAir || item.amount == 0) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_INVALID_ITEM)
            )
        }

        // Check blacklist
        if (config.restrictions.blacklistedMaterials.contains(item.type.name)) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_BLACKLISTED)
            )
        }

        // Check price limits
        if (startPrice < config.auctions.minStartPrice) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(config.auctions.minStartPrice)))
                }
            )
        }

        if (startPrice > config.auctions.maxStartPrice) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_HIGH) {
                    unparsed("max", economy.format(BigDecimal.valueOf(config.auctions.maxStartPrice)))
                }
            )
        }

        // Check BIN price
        if (binPrice != null && type != AuctionType.AUCTION) {
            val minBin = startPrice * config.auctions.minBinMultiplier
            if (binPrice < minBin) {
                return@withContext CreateAuctionResult(
                    false, null, 0.0,
                    translationAPI.getComponent(AuctionMessages.BIN_PRICE_TOO_LOW) {
                        unparsed("min", economy.format(BigDecimal.valueOf(minBin)))
                        unparsed("multiplier", config.auctions.minBinMultiplier.toString())
                    }
                )
            }
        }

        // Check concurrent auctions
        val activeCount = auctionRepository.countPlayerAuctions(seller.uniqueId, AuctionStatus.ACTIVE)
        if (activeCount >= config.auctions.maxConcurrentAuctions) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_MAX_REACHED) {
                    unparsed("max", config.auctions.maxConcurrentAuctions.toString())
                }
            )
        }

        // Calculate listing fee
        val listingFee = calculateFee(startPrice, config.auctions.listingFee)

        // Add anonymous fee
        val totalFee = if (anonymous && config.auctions.display.allowAnonymous) {
            listingFee + config.auctions.display.anonymousFee
        } else listingFee

        // Check if seller can afford fee
        if (totalFee > 0 && !economy.has(seller, BigDecimal.valueOf(totalFee))) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponent(AuctionMessages.INSUFFICIENT_FUNDS_LISTING) {
                    unparsed("amount", economy.format(BigDecimal.valueOf(totalFee)))
                }
            )
        }

        // Charge fee
        if (totalFee > 0) {
            economy.withdraw(seller, BigDecimal.valueOf(totalFee))
            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.FEE_LISTING,
                    fromUuid = seller.uniqueId,
                    fromName = seller.name,
                    toUuid = null,
                    toName = null,
                    amount = totalFee,
                    taxAmount = 0.0,
                    itemMaterial = null,
                    itemQuantity = null,
                    referenceId = null,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
        }

        // Remove item from inventory
        withContext(plugin.entityDispatcher(seller)) {
            seller.inventory.removeItem(item)
        }

        // Create auction
        val auction = Auction(
            id = UUID.randomUUID(),
            sellerUuid = seller.uniqueId,
            sellerName = seller.name,
            itemStack = item.clone(),
            itemMaterial = item.type.name,
            itemDisplayName = run {
                val meta = item.itemMeta
                if (meta != null && meta.hasDisplayName()) {
                    meta.displayName()?.let { mm.serialize(it) }
                } else {
                    null
                }
            },
            auctionType = type,
            startPrice = startPrice,
            buyNowPrice = binPrice,
            reservePrice = null,
            minIncrement = config.auctions.defaultIncrement,
            status = AuctionStatus.ACTIVE,
            createdAt = Instant.now(),
            endsAt = Instant.now().plus(duration),
            isAnonymous = anonymous && config.auctions.display.allowAnonymous
        )

        auctionRepository.create(auction)

        CreateAuctionResult(
            true, auction, totalFee,
            translationAPI.getComponentSync(AuctionMessages.AUCTION_CREATED)
        )
    }

    /**
     * Places a bid on an auction.
     *
     * @param bidder The player placing the bid
     * @param auctionId The ID of the auction
     * @param amount The bid amount
     * @return The result of the bid attempt
     */
    suspend fun placeBid(bidder: Player, auctionId: UUID, amount: Double): BidResult = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND)
            )

        if (!auction.canBid()) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.BID_CANNOT_ON_BIN)
            )
        }

        if (auction.sellerUuid == bidder.uniqueId) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponent(AuctionMessages.CANNOT_BID_OWN_AUCTION)
            )
        }

        // Get current highest bid
        val highestBid = bidRepository.getHighestBid(auctionId)
        val minBid = highestBid?.bidAmount?.plus(auction.minIncrement) ?: auction.startPrice

        if (amount < minBid) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.BID_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(minBid)))
                }
            )
        }

        // Check balance
        if (!economy.has(bidder, BigDecimal.valueOf(amount))) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.BID_NO_BALANCE)
            )
        }

        // Refund previous bidder
        highestBid?.let { prevBid ->
            val prevBidder = plugin.server.getOfflinePlayer(prevBid.bidderUuid)
            economy.deposit(prevBidder, BigDecimal.valueOf(prevBid.bidAmount))
            bidRepository.markAsOutbid(prevBid.id)

            // Notify previous bidder (async)
            plugin.server.getPlayer(prevBid.bidderUuid)?.let { player ->
                player.sendMessage(
                    translationAPI.getComponentSync(AuctionMessages.BID_OUTBID) {
                        unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                        unparsed("amount", economy.format(BigDecimal.valueOf(amount)))
                    }
                )
                // Play outbid sound
                playSound(player, config.notifications.sounds.outbid)
            }
        }

        // Charge new bidder
        economy.withdraw(bidder, BigDecimal.valueOf(amount))

        // Create bid
        val bid = Bid(
            auctionId = auctionId,
            bidderUuid = bidder.uniqueId,
            bidderName = bidder.name,
            bidAmount = amount,
            bidTime = Instant.now()
        )

        bidRepository.create(bid)
        auctionRepository.incrementBidCount(auctionId)

        // Anti-snipe: extend auction if bid placed near end
        if (config.auctions.antiSnipe.enabled) {
            val timeRemaining = Duration.between(Instant.now(), auction.endsAt)
            if (timeRemaining.toMinutes() <= config.auctions.antiSnipe.thresholdMinutes) {
                // Check if max auto extensions reached (anti-snipe only)
                val currentExtensions = auctionRepository.getExtensionCount(auctionId)
                if (currentExtensions < config.auctions.antiSnipe.maxAutoExtensions) {
                    val newEndTime = auction.endsAt.plus(
                        Duration.ofMinutes(config.auctions.antiSnipe.extensionMinutes.toLong())
                    )
                    auctionRepository.updateEndTime(auctionId, newEndTime)
                    auctionRepository.incrementExtensionCount(auctionId)
                    logger.info("Anti-snipe triggered for auction $auctionId, extending to $newEndTime (auto extension ${currentExtensions + 1}/${config.auctions.antiSnipe.maxAutoExtensions})")
                } else {
                    logger.debug("Anti-snipe max auto extensions reached for auction $auctionId")
                }
            }
        }

        BidResult(
            true,
            highestBid != null,
            highestBid?.bidderName,
            translationAPI.getComponentSync(AuctionMessages.BID_PLACED)
        )
    }

    /**
     * Purchases an auction using Buy-It-Now.
     *
     * @param buyer The player purchasing the item
     * @param auctionId The ID of the auction
     * @return The result of the purchase attempt
     */
    suspend fun buyNow(buyer: Player, auctionId: UUID): PurchaseResult = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext PurchaseResult(
                false, null,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND)
            )

        if (!auction.canBuyNow()) {
            return@withContext PurchaseResult(
                false, null,
                translationAPI.getComponentSync(AuctionMessages.BIN_ALREADY_SOLD)
            )
        }

        if (auction.sellerUuid == buyer.uniqueId) {
            return@withContext PurchaseResult(
                false, null,
                translationAPI.getComponent(AuctionMessages.CANNOT_BUY_OWN_AUCTION)
            )
        }

        val binPrice = auction.buyNowPrice
            ?: return@withContext PurchaseResult(
                false, null,
                translationAPI.getComponent(AuctionMessages.NO_BIN_PRICE)
            )

        if (!economy.has(buyer, BigDecimal.valueOf(binPrice))) {
            return@withContext PurchaseResult(
                false, null,
                translationAPI.getComponentSync(AuctionMessages.BIN_NO_BALANCE)
            )
        }

        // Refund highest bidder if any
        // Re-fetch to ensure we get the current highest bid and check isOutbid to prevent race conditions
        val highestBid = bidRepository.getHighestBid(auctionId)
        highestBid?.let { bid ->
            // Double-check that the bid is not already outbid (race condition prevention)
            if (!bid.isOutbid) {
                val prevBidder = plugin.server.getOfflinePlayer(bid.bidderUuid)
                economy.deposit(prevBidder, BigDecimal.valueOf(bid.bidAmount))
                // Mark as outbid to prevent duplicate refunds
                bidRepository.markAsOutbid(bid.id)
            }
        }

        // Charge buyer
        economy.withdraw(buyer, BigDecimal.valueOf(binPrice))

        // Calculate fees and pay seller
        val saleFee = calculateFee(binPrice, config.auctions.saleFee)
        val sellerAmount = binPrice - saleFee

        val seller = plugin.server.getOfflinePlayer(auction.sellerUuid)
        economy.deposit(seller, BigDecimal.valueOf(sellerAmount))

        // Mark as sold
        auctionRepository.markAsSold(auctionId, buyer.uniqueId, buyer.name, binPrice)

        // Log transactions
        transactionRepository.create(
            Transaction(
                transactionType = TransactionType.AUCTION_SALE,
                fromUuid = buyer.uniqueId,
                fromName = buyer.name,
                toUuid = auction.sellerUuid,
                toName = auction.sellerName,
                amount = sellerAmount,
                taxAmount = saleFee,
                itemMaterial = auction.itemMaterial,
                itemQuantity = 1,
                referenceId = auctionId,
                timestamp = Instant.now(),
                serverId = serverId
            )
        )

        // Give item to buyer (store in expired items if inventory full)
        giveItemOrStoreExpired(
            buyer,
            buyer.uniqueId,
            buyer.name,
            auction.itemStack,
            auction.id,
            ExpiredItemType.AUCTION_ITEM,
            "BIN_PURCHASE"
        )

        // Notify seller
        plugin.server.getPlayer(auction.sellerUuid)?.let { sellerPlayer ->
            sellerPlayer.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                    unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                    unparsed("price", economy.format(BigDecimal.valueOf(binPrice)))
                }
            )
            // Play sold sound
            playSound(sellerPlayer, config.notifications.sounds.sold)
        }

        // Play won sound for buyer
        playSound(buyer, config.notifications.sounds.won)

        PurchaseResult(
            true, auction,
            translationAPI.getComponentSync(AuctionMessages.BIN_PURCHASED) {
                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                unparsed("price", economy.format(BigDecimal.valueOf(binPrice)))
            }
        )
    }

    /**
     * Cancels an auction.
     *
     * @param player The player attempting to cancel (must be owner or admin)
     * @param auctionId The ID of the auction to cancel
     * @return The result of the cancellation attempt
     */
    suspend fun cancelAuction(player: Player, auctionId: UUID): ServiceResult<Auction> = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND)
            )

        if (auction.sellerUuid != player.uniqueId && !player.hasPermission("auctionhouse.admin.cancel")) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_OWNER)
            )
        }

        if (!auction.isActive()) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_ALREADY_ENDED)
            )
        }

        // Refund highest bidder
        val highestBid = bidRepository.getHighestBid(auctionId)
        highestBid?.let { bid ->
            // Double-check that the bid is not already outbid (race condition prevention)
            if (!bid.isOutbid) {
                val bidder = plugin.server.getOfflinePlayer(bid.bidderUuid)
                economy.deposit(bidder, BigDecimal.valueOf(bid.bidAmount))
                bidRepository.markAsOutbid(bid.id)
                transactionRepository.create(
                    Transaction(
                        transactionType = TransactionType.AUCTION_BID_RETURN,
                        fromUuid = null,
                        fromName = null,
                        toUuid = bid.bidderUuid,
                        toName = bid.bidderName,
                        amount = bid.bidAmount,
                        taxAmount = 0.0,
                        itemMaterial = null,
                        itemQuantity = null,
                        referenceId = auctionId,
                        timestamp = Instant.now(),
                        serverId = serverId
                    )
                )
            }
        }

        // Return item to seller
        expiredItemManager.storeExpiredItem(
            ownerUuid = auction.sellerUuid,
            ownerName = auction.sellerName,
            itemType = ExpiredItemType.AUCTION_ITEM,
            sourceId = auctionId,
            item = auction.itemStack,
            reason = "CANCELLED"
        )

        // Mark as cancelled
        auctionRepository.updateStatus(auctionId, AuctionStatus.CANCELLED)

        ServiceResult.Success(auction)
    }

    /**
     * Edits the price of an auction.
     * Only allowed if no bids have been placed and auction is still active.
     *
     * @param player The player attempting to edit (must be owner)
     * @param auctionId The ID of the auction to edit
     * @param newStartPrice New starting price (optional, null to keep current)
     * @param newBuyNowPrice New buy-it-now price (optional, null to keep current)
     * @return The result of the edit attempt
     */
    suspend fun editAuctionPrice(
        player: Player,
        auctionId: UUID,
        newStartPrice: Double?,
        newBuyNowPrice: Double?
    ): ServiceResult<Auction> = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND)
            )

        // Check ownership
        if (auction.sellerUuid != player.uniqueId && !player.hasPermission("auctionhouse.admin.edit")) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_OWNER)
            )
        }

        // Check auction is active
        if (!auction.isActive()) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponent(AuctionMessages.CANNOT_EDIT_ENDED)
            )
        }

        // Check no bids have been placed
        val highestBid = bidRepository.getHighestBid(auctionId)
        if (highestBid != null) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponent(AuctionMessages.CANNOT_EDIT_BID_PLACED)
            )
        }

        // Validate new start price if provided
        val finalStartPrice = newStartPrice ?: auction.startPrice
        if (newStartPrice != null) {
            if (newStartPrice < config.auctions.minStartPrice) {
                return@withContext ServiceResult.Failure(
                    translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_LOW) {
                        unparsed("min", economy.format(BigDecimal.valueOf(config.auctions.minStartPrice)))
                    }
                )
            }
            if (newStartPrice > config.auctions.maxStartPrice) {
                return@withContext ServiceResult.Failure(
                    translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_HIGH) {
                        unparsed("max", economy.format(BigDecimal.valueOf(config.auctions.maxStartPrice)))
                    }
                )
            }
        }

        // Validate new BIN price if provided
        val finalBuyNowPrice = newBuyNowPrice ?: auction.buyNowPrice
        if (newBuyNowPrice != null) {
            val minBin = finalStartPrice * config.auctions.minBinMultiplier
            if (newBuyNowPrice < minBin) {
                return@withContext ServiceResult.Failure(
                    translationAPI.getComponent(AuctionMessages.BIN_PRICE_TOO_LOW) {
                        unparsed("min", economy.format(BigDecimal.valueOf(minBin)))
                        unparsed("multiplier", config.auctions.minBinMultiplier.toString())
                    }
                )
            }
        }

        // Update prices in database
        auctionRepository.updatePrices(auctionId, newStartPrice, newBuyNowPrice)

        ServiceResult.Success(auction)
    }

    /**
     * Gets active auctions with filtering, sorting, and pagination.
     *
     * @param filter Filter criteria
     * @param sort Sort order
     * @param page Page number (0-indexed)
     * @param pageSize Number of items per page
     * @return Paged result of auctions
     */
    suspend fun getActiveAuctions(
        filter: AuctionFilter,
        sort: AuctionSort,
        page: Int,
        pageSize: Int
    ): PagedResult<Auction> = withContext(Dispatchers.IO) {
        val auctions = auctionRepository.getActiveAuctions(filter, sort, page, pageSize)
        // Get accurate total count
        val total = auctionRepository.countActiveAuctions(filter)
        val totalPages = (total + pageSize - 1) / pageSize

        PagedResult(auctions, page, totalPages.coerceAtLeast(1), total)
    }

    /**
     * Gets auctions for a specific player.
     *
     * @param playerId The player's UUID
     * @param status Optional status filter
     * @return List of auctions
     */
    suspend fun getPlayerAuctions(playerId: UUID, status: AuctionStatus?): List<Auction> =
        auctionRepository.getPlayerAuctions(playerId, status)

    /**
     * Gets an auction by ID.
     *
     * @param auctionId The auction UUID
     * @return The auction, or null if not found
     */
    suspend fun getAuction(auctionId: UUID): Auction? =
        auctionRepository.getById(auctionId)

    suspend fun findAuctionByShortId(shortId: String): Auction? =
        auctionRepository.findByShortId(shortId)

    suspend fun processExpiredAuctions() = withContext(Dispatchers.IO) {
        val expiredAuctions = auctionRepository.getExpiredAuctions()

        for (auction in expiredAuctions) {
            try {
                processExpiredAuction(auction)
            } catch (e: Exception) {
                logger.error("Error processing expired auction ${auction.id}", e)
            }
        }
    }

    private suspend fun processExpiredAuction(auction: Auction) {
        try {
            val highestBid = bidRepository.getHighestBid(auction.id)

            val reserveMet = auction.reservePrice?.let { reserve ->
                highestBid != null && highestBid.bidAmount >= reserve
            } ?: true // No reserve price means any bid wins

            if (highestBid != null && reserveMet) {
                // Auction sold - wrap database operations in transaction
                database.transaction {
                    try {
                        val saleFee = calculateFee(highestBid.bidAmount, config.auctions.saleFee)
                        val sellerAmount = highestBid.bidAmount - saleFee

                        val seller = plugin.server.getOfflinePlayer(auction.sellerUuid)
                        economy.deposit(seller, BigDecimal.valueOf(sellerAmount))

                        transactionRepository.create(
                            Transaction(
                                transactionType = TransactionType.AUCTION_SALE,
                                fromUuid = highestBid.bidderUuid,
                                fromName = highestBid.bidderName,
                                toUuid = auction.sellerUuid,
                                toName = auction.sellerName,
                                amount = sellerAmount,
                                taxAmount = saleFee,
                                itemMaterial = auction.itemMaterial,
                                itemQuantity = 1,
                                referenceId = auction.id,
                                timestamp = Instant.now(),
                                serverId = serverId
                            )
                        )

                        auctionRepository.markAsSold(
                            auction.id,
                            highestBid.bidderUuid,
                            highestBid.bidderName,
                            highestBid.bidAmount
                        )
                    } catch (e: Exception) {
                        logger.error("Error processing sold auction ${auction.id} - economy/database operations failed", e)
                        throw e // Re-throw to trigger transaction rollback
                    }
                }

                // Give item to winner (outside transaction - failure here won't rollback economy)
                try {
                    plugin.server.getPlayer(highestBid.bidderUuid)?.let { player ->
                        giveItemOrStoreExpired(
                            player,
                            highestBid.bidderUuid,
                            highestBid.bidderName,
                            auction.itemStack,
                            auction.id,
                            ExpiredItemType.AUCTION_ITEM,
                            "AUCTION_WON"
                        )
                        player.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.AUCTION_WON) {
                                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                unparsed("price", economy.format(BigDecimal.valueOf(highestBid.bidAmount)))
                            }
                        )
                        playSound(player, config.notifications.sounds.won)
                    } ?: run {
                        // Player offline - store in expired items
                        expiredItemManager.storeExpiredItem(
                            ownerUuid = highestBid.bidderUuid,
                            ownerName = highestBid.bidderName,
                            itemType = ExpiredItemType.AUCTION_ITEM,
                            sourceId = auction.id,
                            item = auction.itemStack,
                            reason = "WON_AUCTION"
                        )
                    }

                    // Notify seller
                    plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
                        seller.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                unparsed("price", economy.format(BigDecimal.valueOf(highestBid.bidAmount)))
                            }
                        )
                        playSound(seller, config.notifications.sounds.sold)
                    }
                } catch (e: Exception) {
                    logger.error("Error delivering item/notification for auction ${auction.id}", e)
                    // Item delivery failure logged but doesn't rollback economy
                }
            } else {
                // Auction expired without sale - wrap database operations in transaction
                database.transaction {
                    try {
                        auctionRepository.updateStatus(auction.id, AuctionStatus.EXPIRED)

                        // Return item to seller
                        expiredItemManager.storeExpiredItem(
                            ownerUuid = auction.sellerUuid,
                            ownerName = auction.sellerName,
                            itemType = ExpiredItemType.AUCTION_ITEM,
                            sourceId = auction.id,
                            item = auction.itemStack,
                            reason = "EXPIRED"
                        )
                    } catch (e: Exception) {
                        logger.error("Error processing expired auction ${auction.id} - database operations failed", e)
                        throw e
                    }
                }

                // Notify seller (outside transaction)
                try {
                    plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
                        seller.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.AUCTION_EXPIRED) {
                                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                            }
                        )
                        playSound(seller, config.notifications.sounds.expired)
                    }
                } catch (e: Exception) {
                    logger.error("Error notifying seller for expired auction ${auction.id}", e)
                }

                // Refund highest bidder if exists but didn't meet reserve
                highestBid?.let { bid ->
                    try {
                        val bidder = plugin.server.getOfflinePlayer(bid.bidderUuid)
                        economy.deposit(bidder, BigDecimal.valueOf(bid.bidAmount))
                    } catch (e: Exception) {
                        logger.error("Error refunding bidder ${bid.bidderUuid} for auction ${auction.id}", e)
                        // Log for manual recovery - economy operation failed
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error processing expired auction ${auction.id}", e)
            // Don't re-throw - continue processing other auctions
        }
    }

    /**
     * Calculates a fee based on the amount and fee configuration.
     *
     * @param amount The base amount
     * @param feeConfig The fee configuration
     * @return The calculated fee
     */
    private fun calculateFee(amount: Double, feeConfig: FeeConfig): Double {
        val fee = when (feeConfig.type.uppercase()) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }

        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }

    /**
     * Attempts to give an item to a player. If their inventory is full or partially full,
     * stores the excess in the expired items system instead of dropping on the ground.
     *
     * @param player The player to give the item to
     * @param ownerUuid The UUID of the item owner (for expired item storage)
     * @param ownerName The name of the item owner (for expired item storage)
     * @param itemStack The item to give
     * @param sourceId The source auction/order ID
     * @param itemType The type of expired item
     * @param reason The reason for storage if needed
     * @return True if the full item was given, false if partially or fully stored as expired
     */
    private suspend fun giveItemOrStoreExpired(
        player: org.bukkit.entity.Player,
        ownerUuid: UUID,
        ownerName: String,
        itemStack: ItemStack,
        sourceId: UUID,
        itemType: ExpiredItemType,
        reason: String
    ): Boolean = withContext(plugin.entityDispatcher(player)) {
        val remaining = player.inventory.addItem(itemStack.clone())

        if (remaining.isEmpty()) {
            return@withContext true // Full success
        }

        // Store overflow in expired items instead of dropping
        val totalRemaining = remaining.values.sumOf { it.amount }
        expiredItemManager.storeExpiredItems(
            ownerUuid = ownerUuid,
            ownerName = ownerName,
            itemType = itemType,
            sourceId = sourceId,
            items = remaining.values.toList(),
            reason = "$reason (INVENTORY_FULL)"
        )

        player.sendMessage(
            translationAPI.getComponentSync(AuctionMessages.INVENTORY_FULL_STORED) {
                unparsed("count", totalRemaining.toString())
            }
        )

        false // Partial or no success - stored in expired items
    }

    /**
     * Plays a sound to a player if the sound is configured.
     */
    private fun playSound(player: org.bukkit.entity.Player, soundName: String) {
        try {
            val sound = org.bukkit.Sound.valueOf(soundName)
            player.playSound(player.location, sound, 1.0f, 1.0f)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid sound configured: $soundName")
        }
    }

    /**
     * Creates multiple auctions in bulk.
     *
     * @param seller The player creating the auctions
     * @param item The item stack being auctioned (will be removed from inventory)
     * @param quantity Number of auctions to create (1 item per auction)
     * @param type The type of auction (AUCTION, BIN, or BOTH)
     * @param startPrice The starting bid price for each auction
     * @param binPrice The buy-it-now price (null if not available)
     * @param duration How long each auction will run
     * @param anonymous Whether to hide the seller's identity
     * @return The result of the bulk creation attempt
     */
    suspend fun createBulkAuctions(
        seller: Player,
        item: ItemStack,
        quantity: Int,
        type: AuctionType,
        startPrice: Double,
        binPrice: Double?,
        duration: Duration,
        anonymous: Boolean
    ): BulkListingResult = withContext(Dispatchers.IO) {
        // Check if bulk listing is enabled
        if (!config.auctions.bulkListing.enabled) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponent(AuctionMessages.BULK_LISTING_DISABLED)
            )
        }

        // Check max quantity
        if (quantity > config.auctions.bulkListing.maxBulkListings) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.BULK_LISTING_MAX_REACHED) {
                    unparsed("max", config.auctions.bulkListing.maxBulkListings.toString())
                }
            )
        }

        // Validate item
        if (item.type.isAir || item.amount == 0) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_INVALID_ITEM)
            )
        }

        // Check if player has enough items
        if (item.amount < quantity) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.BULK_LISTING_NO_ITEMS)
            )
        }

        // Check blacklist
        if (config.restrictions.blacklistedMaterials.contains(item.type.name)) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_BLACKLISTED)
            )
        }

        // Check price limits
        if (startPrice < config.auctions.minStartPrice) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(config.auctions.minStartPrice)))
                }
            )
        }

        if (startPrice > config.auctions.maxStartPrice) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_HIGH) {
                    unparsed("max", economy.format(BigDecimal.valueOf(config.auctions.maxStartPrice)))
                }
            )
        }

        // Check BIN price
        if (binPrice != null && type != AuctionType.AUCTION) {
            val minBin = startPrice * config.auctions.minBinMultiplier
            if (binPrice < minBin) {
                return@withContext BulkListingResult(
                    false, 0, 0, 0.0,
                    translationAPI.getComponent(AuctionMessages.BIN_PRICE_TOO_LOW) {
                        unparsed("min", economy.format(BigDecimal.valueOf(minBin)))
                        unparsed("multiplier", config.auctions.minBinMultiplier.toString())
                    }
                )
            }
        }

        // Check concurrent auctions
        val activeCount = auctionRepository.countPlayerAuctions(seller.uniqueId, AuctionStatus.ACTIVE)
        val maxAuctions = config.auctions.maxConcurrentAuctions
        if (activeCount + quantity > maxAuctions) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_MAX_REACHED) {
                    unparsed("max", maxAuctions.toString())
                }
            )
        }

        // Calculate fee per auction with bulk discount
        val baseFee = calculateFee(startPrice, config.auctions.listingFee)
        val discount = config.auctions.bulkListing.feeDiscountPercent / 100.0
        val feePerAuction = (baseFee + if (anonymous && config.auctions.display.allowAnonymous) config.auctions.display.anonymousFee else 0.0) * (1.0 - discount)
        val totalFee = feePerAuction * quantity

        // Check if seller can afford total fees
        if (totalFee > 0 && !economy.has(seller, BigDecimal.valueOf(totalFee))) {
            return@withContext BulkListingResult(
                false, 0, 0, 0.0,
                translationAPI.getComponent(AuctionMessages.INSUFFICIENT_FUNDS_BULK_LISTING) {
                    unparsed("amount", economy.format(BigDecimal.valueOf(totalFee)))
                    unparsed("quantity", quantity.toString())
                }
            )
        }

        // Charge total fee upfront
        if (totalFee > 0) {
            economy.withdraw(seller, BigDecimal.valueOf(totalFee))
            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.FEE_LISTING,
                    fromUuid = seller.uniqueId,
                    fromName = seller.name,
                    toUuid = null,
                    toName = null,
                    amount = totalFee,
                    taxAmount = 0.0,
                    itemMaterial = null,
                    itemQuantity = quantity,
                    referenceId = null,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
        }

        // Create auctions first - only remove items for successful auctions
        var auctionsCreated = 0
        var auctionsFailed = 0
        var feesCharged = 0.0

        for (i in 1..quantity) {
            try {
                val auction = Auction(
                    id = UUID.randomUUID(),
                    sellerUuid = seller.uniqueId,
                    sellerName = seller.name,
                    itemStack = item.clone().apply { amount = 1 },
                    itemMaterial = item.type.name,
                    itemDisplayName = run {
                        val meta = item.itemMeta
                        if (meta != null && meta.hasDisplayName()) {
                            meta.displayName()?.let { mm.serialize(it) }
                        } else {
                            null
                        }
                    },
                    auctionType = type,
                    startPrice = startPrice,
                    buyNowPrice = binPrice,
                    reservePrice = null,
                    minIncrement = config.auctions.defaultIncrement,
                    status = AuctionStatus.ACTIVE,
                    createdAt = Instant.now(),
                    endsAt = Instant.now().plus(duration),
                    isAnonymous = anonymous && config.auctions.display.allowAnonymous
                )

                auctionRepository.create(auction)
                auctionsCreated++
                feesCharged += feePerAuction
            } catch (e: Exception) {
                logger.error("Failed to create bulk auction $i/$quantity for ${seller.name}", e)
                auctionsFailed++
            }
        }

        // Only remove items that were successfully created as auctions
        if (auctionsCreated > 0) {
            val itemToRemove = item.clone().apply { amount = auctionsCreated }
            withContext(plugin.entityDispatcher(seller)) {
                seller.inventory.removeItem(itemToRemove)
            }
        }

        // Handle partial failures - refund fees for failed auctions
        if (auctionsFailed > 0 && totalFee > 0) {
            val refundAmount = feePerAuction * auctionsFailed
            economy.deposit(seller, BigDecimal.valueOf(refundAmount))
            feesCharged -= refundAmount

            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.REFUND,
                    fromUuid = null,
                    fromName = null,
                    toUuid = seller.uniqueId,
                    toName = seller.name,
                    amount = refundAmount,
                    taxAmount = 0.0,
                    itemMaterial = null,
                    itemQuantity = auctionsFailed,
                    referenceId = null,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
        }

        val success = auctionsCreated > 0
        val message = when {
            auctionsFailed == 0 -> translationAPI.getComponent(AuctionMessages.BULK_LISTING_CREATED) {
                unparsed("count", auctionsCreated.toString())
                unparsed("fee", economy.format(BigDecimal.valueOf(feesCharged)))
            }
            auctionsCreated > 0 -> translationAPI.getComponent(AuctionMessages.BULK_LISTING_PARTIAL) {
                unparsed("success", auctionsCreated.toString())
                unparsed("total", quantity.toString())
                unparsed("failed", auctionsFailed.toString())
            }
            else -> translationAPI.getComponent(AuctionMessages.BULK_LISTING_FAILED)
        }

        BulkListingResult(
            success,
            auctionsCreated,
            auctionsFailed,
            feesCharged,
            message
        )
    }
}
