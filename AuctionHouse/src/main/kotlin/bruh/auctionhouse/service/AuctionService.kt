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
import bruh.zchat.utils.database.TransactionIsolation
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

        // Validate prices
        if (startPrice < 0) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(0.0)))
                }
            )
        }
        if (binPrice != null && binPrice < 0) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(0.0)))
                }
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

        // Create auction - if this fails, attempt rollback of fee and item
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

        try {
            auctionRepository.create(auction)
        } catch (e: Exception) {
            logger.error("Failed to create auction for ${seller.name}, attempting rollback", e)
            // Rollback: refund fee and return item
            if (totalFee > 0) {
                try {
                    economy.deposit(seller, BigDecimal.valueOf(totalFee))
                } catch (refundError: Exception) {
                    logger.error("Failed to refund listing fee for ${seller.name}", refundError)
                }
            }
            try {
                withContext(plugin.entityDispatcher(seller)) {
                    seller.inventory.addItem(item)
                }
            } catch (itemError: Exception) {
                logger.error("Failed to return item to ${seller.name}, storing as expired", itemError)
                try {
                    expiredItemManager.storeExpiredItem(
                        ownerUuid = seller.uniqueId,
                        ownerName = seller.name,
                        itemType = ExpiredItemType.AUCTION_ITEM,
                        sourceId = auction.id,
                        item = item,
                        reason = "CREATE_FAILED"
                    )
                } catch (storeError: Exception) {
                    logger.error("CRITICAL: Lost item for ${seller.name} during rollback", storeError)
                }
            }
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_CREATION_FAILED)
            )
        }

        CreateAuctionResult(
            true, auction, totalFee,
            translationAPI.getComponentSync(AuctionMessages.AUCTION_CREATED)
        )
    }

    /**
     * Places a bid on an auction.
     * Uses a database transaction to prevent race conditions where concurrent bids
     * could refund the same previous bidder or both claim to be the highest bid.
     *
     * @param bidder The player placing the bid
     * @param auctionId The ID of the auction
     * @param amount The bid amount
     * @return The result of the bid attempt
     */
    suspend fun placeBid(bidder: Player, auctionId: UUID, amount: Double): BidResult = withContext(Dispatchers.IO) {
        // Validate bid amount
        if (amount <= 0) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.BID_TOO_LOW) {
                    unparsed("min", economy.format(BigDecimal.valueOf(1.0)))
                }
            )
        }

        // Pre-validate auction and bidder (outside transaction - fast reads)
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

        // Pre-check minimum bid (best-effort outside transaction)
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

        // Charge new bidder (outside transaction - economy is external).
        // Wrapped in try-catch so if the DB transaction throws, we refund the bidder.
        economy.withdraw(bidder, BigDecimal.valueOf(amount))

        // Store previous bidder info for notification outside transaction
        var prevBidderUuid: UUID? = null
        var prevBidderName: String? = null
        var prevBidAmount: Double? = null

        try {
            // Atomic database operations inside transaction with SERIALIZABLE isolation
            // to prevent phantom reads where two bidders could both think they won.
            val txResult = database.transaction(isolation = TransactionIsolation.SERIALIZABLE) {
                // Re-read highest bid inside transaction for consistency
                val currentHighest = bidRepository.getHighestBid(this, auctionId)

                // Re-validate minimum bid with locked state
                val txMinBid = currentHighest?.bidAmount?.plus(auction.minIncrement) ?: auction.startPrice
                if (amount < txMinBid) {
                    // Bid doesn't meet actual minimum after concurrent read — will refund outside transaction
                    return@transaction false
                }

                // Mark previous highest bid as outbid atomically and capture info only if we succeeded.
                // The SQL includes AND is_outbid = FALSE, so if another concurrent transaction
                // already marked it, this returns 0 rows and we skip the refund.
                currentHighest?.let { prevBid ->
                    val marked = bidRepository.markAsOutbid(this, prevBid.id)
                    if (marked > 0) {
                        // We were the one who marked it - capture for refund outside transaction
                        prevBidderUuid = prevBid.bidderUuid
                        prevBidderName = prevBid.bidderName
                        prevBidAmount = prevBid.bidAmount
                    }
                }

                // Create bid inside transaction
                val bid = Bid(
                    auctionId = auctionId,
                    bidderUuid = bidder.uniqueId,
                    bidderName = bidder.name,
                    bidAmount = amount,
                    bidTime = Instant.now()
                )
                bidRepository.create(this, bid)
                auctionRepository.incrementBidCount(this, auctionId)

                // CRITICAL: Increment auction version for optimistic locking.
                // This ensures that markAsSoldWithVersion() (used by buyNow() and processExpiredAuction())
                // will fail if this bid was placed after they read the auction.
                // Without this, the following race condition could occur:
                //   1. processExpiredAuction() reads auction (version=1, highestBid=A)
                //   2. placeBid() creates bid for B, but does NOT increment version
                //   3. processExpiredAuction() calls markAsSoldWithVersion(version=1) -> SUCCEEDS
                //   4. buyNow() calls markAsSoldWithVersion(version=1) -> ALSO SUCCEEDS (double-sell!)
                // With version increment, step 4 would fail because version is now 2.
                auctionRepository.incrementVersion(this, auctionId)

                // Anti-snipe: extend auction if bid placed near end (inside transaction)
                // Re-read auction to get current end time (may have been extended by concurrent bid).
                if (config.auctions.antiSnipe.enabled) {
                    val currentAuction = auctionRepository.getById(this, auctionId)
                    if (currentAuction != null) {
                        val timeRemaining = Duration.between(Instant.now(), currentAuction.endsAt)
                        if (timeRemaining.toMinutes() <= config.auctions.antiSnipe.thresholdMinutes) {
                            val currentExtensions = auctionRepository.getExtensionCount(this, auctionId)
                            if (currentExtensions < config.auctions.antiSnipe.maxAutoExtensions) {
                                val newEndTime = currentAuction.endsAt.plus(
                                    Duration.ofMinutes(config.auctions.antiSnipe.extensionMinutes.toLong())
                                )
                                auctionRepository.updateEndTime(this, auctionId, newEndTime)
                                auctionRepository.incrementExtensionCount(this, auctionId)
                                logger.info("Anti-snipe triggered for auction $auctionId, extending to $newEndTime (auto extension ${currentExtensions + 1}/${config.auctions.antiSnipe.maxAutoExtensions})")
                            } else {
                                logger.debug("Anti-snipe max auto extensions reached for auction $auctionId")
                            }
                        }
                    } else {
                        logger.warn("Auction $auctionId not found during anti-snipe check within transaction")
                    }
                }

                true
            }

            // If transaction returned false (e.g., bid too low after re-read), refund the bidder
            if (!txResult) {
                economy.deposit(bidder, BigDecimal.valueOf(amount))
                return@withContext BidResult(
                    false, false, null,
                    translationAPI.getComponentSync(AuctionMessages.BID_TOO_LOW) {
                        unparsed("min", economy.format(BigDecimal.valueOf(minBid)))
                    }
                )
            }
        } catch (e: Exception) {
            // Transaction threw — refund bidder to prevent economic loss, then re-throw
            logger.error("Transaction failed for bid on auction $auctionId by ${bidder.name}, refunding ${amount}", e)
            try {
                economy.deposit(bidder, BigDecimal.valueOf(amount))
            } catch (refundError: Exception) {
                logger.error("CRITICAL: Failed to refund bidder ${bidder.name} (${bidder.uniqueId}) ${amount} after transaction failure", refundError)
            }
            throw e
        }

        // Refund previous bidder (outside transaction - economy is external and can't be rolled back)
        if (prevBidderUuid != null) {
            val prevBidder = plugin.server.getOfflinePlayer(prevBidderUuid!!)
            economy.deposit(prevBidder, BigDecimal.valueOf(prevBidAmount!!))
        }

        // Notifications (outside transaction - uses player entity dispatcher)
        prevBidderUuid?.let { uuid ->
            plugin.server.getPlayer(uuid)?.let { player ->
                player.sendMessage(
                    translationAPI.getComponentSync(AuctionMessages.BID_OUTBID) {
                        unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                        unparsed("amount", economy.format(BigDecimal.valueOf(amount)))
                    }
                )
                playSound(player, config.notifications.sounds.outbid)
            }
        }

        BidResult(
            true,
            prevBidderUuid != null,
            prevBidderName,
            translationAPI.getComponentSync(AuctionMessages.BID_PLACED)
        )
    }

    /**
     * Purchases an auction using Buy-It-Now.
     * Uses optimistic locking to prevent double-purchases where two players
     * click buy-now simultaneously.
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

        // Charge buyer (outside transaction - economy is external).
        // Wrapped in try-catch so if the DB transaction throws, we refund the buyer.
        economy.withdraw(buyer, BigDecimal.valueOf(binPrice))

        // Calculate fees
        val saleFee = calculateFee(binPrice, config.auctions.saleFee)
        val sellerAmount = binPrice - saleFee

        // Store previous bidder info for notification and refund
        var prevBidderUuid: UUID? = null
        var prevBidAmount: Double? = null

        try {
            // Atomic database operations inside transaction with optimistic locking
            val rowsAffected = database.transaction {
                // Mark as sold with version check (returns 0 if already sold or version mismatch)
                val sold = auctionRepository.markAsSoldWithVersion(
                    this, auctionId, buyer.uniqueId, buyer.name, binPrice, auction.version
                )

                if (sold == 0) {
                    // Auction was already sold by another player - will refund buyer outside transaction
                    return@transaction 0
                }

                // Mark highest bidder as outbid atomically (DB only - no economy ops inside transaction).
                // Already protected by optimistic locking above, but this is defensive.
                val highestBid = bidRepository.getHighestBid(this, auctionId)
                highestBid?.let { bid ->
                    val marked = bidRepository.markAsOutbid(this, bid.id)
                    if (marked > 0) {
                        prevBidderUuid = bid.bidderUuid
                        prevBidAmount = bid.bidAmount
                    }
                }

                // Log transaction (uses transaction scope for atomicity)
                transactionRepository.create(
                    this,
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

                1
            }

            // If markAsSold failed (race condition - another player bought it), refund buyer
            if (rowsAffected == 0) {
                economy.deposit(buyer, BigDecimal.valueOf(binPrice))
                return@withContext PurchaseResult(
                    false, auction,
                    translationAPI.getComponentSync(AuctionMessages.BIN_ALREADY_SOLD)
                )
            }
        } catch (e: Exception) {
            // Transaction threw — refund buyer to prevent economic loss, then re-throw
            logger.error("Transaction failed for BIN purchase of auction $auctionId by ${buyer.name}, refunding ${binPrice}", e)
            try {
                economy.deposit(buyer, BigDecimal.valueOf(binPrice))
            } catch (refundError: Exception) {
                logger.error("CRITICAL: Failed to refund buyer ${buyer.name} (${buyer.uniqueId}) ${binPrice} after transaction failure", refundError)
            }
            throw e
        }

        // Economy operations (outside transaction - economy is external and can't be rolled back)
        // Pay seller
        val seller = plugin.server.getOfflinePlayer(auction.sellerUuid)
        economy.deposit(seller, BigDecimal.valueOf(sellerAmount))

        // Refund previous highest bidder if any
        if (prevBidderUuid != null) {
            val prevBidder = plugin.server.getOfflinePlayer(prevBidderUuid!!)
            economy.deposit(prevBidder, BigDecimal.valueOf(prevBidAmount!!))
        }

        // Give item to buyer (outside transaction - uses entity dispatcher)
        giveItemOrStoreExpired(
            buyer,
            buyer.uniqueId,
            buyer.name,
            auction.itemStack,
            auction.id,
            ExpiredItemType.AUCTION_ITEM,
            "BIN_PURCHASE"
        )

        // Notify seller (outside transaction)
        plugin.server.getPlayer(auction.sellerUuid)?.let { sellerPlayer ->
            sellerPlayer.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                    unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                    unparsed("price", economy.format(BigDecimal.valueOf(binPrice)))
                }
            )
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

        // Refund highest bidder - use atomic markAsOutbid to prevent double-refund.
        // The SQL includes AND is_outbid = FALSE, so only one caller will succeed.
        val highestBid = bidRepository.getHighestBid(auctionId)
        var shouldRefund = false
        var bidderUuid: UUID? = null
        var bidderName: String? = null
        var bidAmount: Double? = null

        highestBid?.let { bid ->
            // Atomic check-and-mark: returns 1 if we marked it, 0 if already outbid
            val marked = bidRepository.markAsOutbid(bid.id)
            if (marked > 0) {
                shouldRefund = true
                bidderUuid = bid.bidderUuid
                bidderName = bid.bidderName
                bidAmount = bid.bidAmount
            }
        }

        // Refund outside transaction (economy is external)
        if (shouldRefund && bidderUuid != null) {
            val bidder = plugin.server.getOfflinePlayer(bidderUuid!!)
            economy.deposit(bidder, BigDecimal.valueOf(bidAmount!!))
            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.AUCTION_BID_RETURN,
                    fromUuid = null,
                    fromName = null,
                    toUuid = bidderUuid!!,
                    toName = bidderName!!,
                    amount = bidAmount!!,
                    taxAmount = 0.0,
                    itemMaterial = null,
                    itemQuantity = null,
                    referenceId = auctionId,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
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
        // Process in batches to avoid loading too many records at once
        // Break on too many consecutive errors to prevent infinite loops
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 10

        while (consecutiveErrors < maxConsecutiveErrors) {
            val batch = auctionRepository.getExpiredAuctions()
            if (batch.isEmpty()) break

            var batchHadError = false
            for (auction in batch) {
                try {
                    processExpiredAuction(auction)
                } catch (e: Exception) {
                    logger.error("Error processing expired auction ${auction.id}", e)
                    batchHadError = true
                }
            }

            if (batchHadError) {
                consecutiveErrors++
            } else {
                consecutiveErrors = 0
            }
        }

        if (consecutiveErrors >= maxConsecutiveErrors) {
            logger.warn("Stopped processing expired auctions after $maxConsecutiveErrors consecutive error batches")
        }
    }

    /**
     * Processes a single expired auction.
     *
     * CRITICAL CONCURRENCY NOTES:
     * - This method runs concurrently with buyNow() and placeBid() on the same auction.
     * - We use optimistic locking (version check) to prevent double-sells.
     * - All reads (auction, highestBid) MUST happen inside the transaction to ensure
     *   consistency - reading outside creates TOCTOU race conditions.
     * - The markAsSoldWithVersion() call checks both version AND status='ACTIVE',
     *   protecting against concurrent buyNow() which also uses this method.
     * - If the version check fails, another process sold the auction first - we skip silently.
     *
     * @see markAsSoldWithVersion for the optimistic locking implementation
     * @see placeBid for how auction version is incremented on bid placement
     */
    private suspend fun processExpiredAuction(auction: Auction) {
        try {
            // Process sale within transaction with optimistic locking.
            // Returns winner info if successful, null if auction was already processed
            // by another thread (buyNow, concurrent expiration, etc.)
            val saleResult = database.transaction {
                // MUST re-read auction inside transaction to get current version and status.
                // The auction passed to this function may be stale - another process could
                // have sold or expired it between getExpiredAuctions() and now.
                val currentAuction = auctionRepository.getById(this, auction.id)
                if (currentAuction == null || currentAuction.status != AuctionStatus.ACTIVE) {
                    // Already sold or expired by another process - nothing to do
                    return@transaction null
                }

                // MUST get highest bid inside transaction for consistency.
                // If we read outside the transaction (previous implementation), a concurrent
                // bid could be placed after our read but before markAsSoldWithVersion(),
                // causing us to sell to a stale (outbid) bidder.
                val highestBid = bidRepository.getHighestBid(this, auction.id)
                    ?: return@transaction null

                // Check reserve price with fresh bid data
                val reserveMet = currentAuction.reservePrice?.let { reserve ->
                    highestBid.bidAmount >= reserve
                } ?: true // No reserve price means any bid wins

                if (!reserveMet) {
                    // Reserve not met - will be handled after transaction
                    return@transaction null
                }

                // OPTIMISTIC LOCKING: Mark as sold with version check.
                // The SQL includes WHERE status = 'ACTIVE' AND version = ?:
                //   - status='ACTIVE' protects against concurrent buyNow() (which also checks this)
                //   - version=? protects against any concurrent modification (bids placeBid increments version)
                // If sold == 0, either status changed or version mismatched - another process won the race.
                // This is safe to ignore; the other process handles economy/item delivery.
                val sold = auctionRepository.markAsSoldWithVersion(
                    this,
                    auction.id,
                    highestBid.bidderUuid,
                    highestBid.bidderName,
                    highestBid.bidAmount,
                    currentAuction.version
                )

                if (sold == 0) {
                    // Another process sold/expired this auction first - we do nothing.
                    // No cleanup needed: transaction rolls back, no economy ops happened.
                    return@transaction null
                }

                // Calculate fees and create transaction record
                val saleFee = calculateFee(highestBid.bidAmount, config.auctions.saleFee)
                val sellerAmount = highestBid.bidAmount - saleFee

                transactionRepository.create(
                    this,
                    Transaction(
                        transactionType = TransactionType.AUCTION_SALE,
                        fromUuid = highestBid.bidderUuid,
                        fromName = highestBid.bidderName,
                        toUuid = currentAuction.sellerUuid,
                        toName = currentAuction.sellerName,
                        amount = sellerAmount,
                        taxAmount = saleFee,
                        itemMaterial = currentAuction.itemMaterial,
                        itemQuantity = 1,
                        referenceId = auction.id,
                        timestamp = Instant.now(),
                        serverId = serverId
                    )
                )

                // Return winner info for economy operations outside transaction
                Triple(highestBid.bidderUuid, highestBid.bidderName, highestBid.bidAmount)
            }

            if (saleResult != null) {
                // Auction was sold successfully - process economy and item delivery
                val (winnerUuid, winnerName, winningBid) = saleResult
                val saleFee = calculateFee(winningBid, config.auctions.saleFee)
                val sellerAmount = winningBid - saleFee

                // Pay seller (outside transaction - economy is external)
                try {
                    val seller = plugin.server.getOfflinePlayer(auction.sellerUuid)
                    economy.deposit(seller, BigDecimal.valueOf(sellerAmount))
                } catch (e: Exception) {
                    logger.error("Error paying seller for auction ${auction.id}", e)
                }

                // Give item to winner (outside transaction - failure here won't rollback economy)
                try {
                    plugin.server.getPlayer(winnerUuid)?.let { player ->
                        // Online: give to inventory or store overflow in expired items.
                        // giveItemOrStoreExpired always handles the item (returns true/false
                        // indicating full vs partial delivery, never throws for item loss).
                        giveItemOrStoreExpired(
                            player,
                            winnerUuid,
                            winnerName,
                            auction.itemStack,
                            auction.id,
                            ExpiredItemType.AUCTION_ITEM,
                            "AUCTION_WON"
                        )

                        // Notify winner (separate try — notification failure shouldn't
                        // trigger the outer catch's fallback store since item is delivered)
                        try {
                            player.sendMessage(
                                translationAPI.getComponentSync(AuctionMessages.AUCTION_WON) {
                                    unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                    unparsed("price", economy.format(BigDecimal.valueOf(winningBid)))
                                }
                            )
                            playSound(player, config.notifications.sounds.won)
                        } catch (notifyError: Exception) {
                            logger.warn("Failed to notify winner ${winnerName} for auction ${auction.id}", notifyError)
                        }
                    } ?: run {
                        // Player offline — store in expired items for later retrieval
                        expiredItemManager.storeExpiredItem(
                            ownerUuid = winnerUuid,
                            ownerName = winnerName,
                            itemType = ExpiredItemType.AUCTION_ITEM,
                            sourceId = auction.id,
                            item = auction.itemStack,
                            reason = "WON_AUCTION"
                        )
                    }
                } catch (e: Exception) {
                    // Item delivery itself failed (giveItemOrStoreExpired threw).
                    // Attempt emergency fallback storage to prevent item loss.
                    logger.error("CRITICAL: Item delivery failed for auction ${auction.id}, attempting fallback store", e)
                    try {
                        expiredItemManager.storeExpiredItem(
                            ownerUuid = winnerUuid,
                            ownerName = winnerName,
                            itemType = ExpiredItemType.AUCTION_ITEM,
                            sourceId = auction.id,
                            item = auction.itemStack,
                            reason = "DELIVERY_FAILED"
                        )
                    } catch (storeError: Exception) {
                        logger.error("CRITICAL: Item permanently lost for auction ${auction.id} — winner ${winnerName}", storeError)
                    }
                }

                // Notify seller (separate from winner delivery)
                try {
                    plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
                        seller.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                                unparsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                                unparsed("price", economy.format(BigDecimal.valueOf(winningBid)))
                            }
                        )
                        playSound(seller, config.notifications.sounds.sold)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to notify seller for sold auction ${auction.id}", e)
                }
            } else {
                // Transaction returned null - auction was already processed or reserve not met
                // Check if we need to handle the no-sale case (reserve not met or no bids)
                // Re-read to determine current state
                val currentAuction = auctionRepository.getById(auction.id)
                if (currentAuction?.status == AuctionStatus.ACTIVE) {
                    // Still active - means either no bids or reserve not met
                    // Handle the no-sale case
                    processExpiredAuctionNoSale(auction)
                }
                // If status is not ACTIVE, auction was sold/expired by another process - nothing to do
            }
        } catch (e: Exception) {
            logger.error("Unexpected error processing expired auction ${auction.id}", e)
            // Don't re-throw - continue processing other auctions
        }
    }

    /**
     * Handles the no-sale case for expired auctions (no bids or reserve not met).
     *
     * RACE CONDITION FIX:
     * - Item storage and status update happen atomically within a single transaction.
     * - If either operation fails, both are rolled back and the auction stays ACTIVE for retry.
     * - We re-check status inside the transaction before proceeding to handle concurrent bids.
     * - This eliminates the TOCTOU window where item was stored but auction wasn't marked EXPIRED.
     *
     * @param auction The expired auction (must be verified ACTIVE by caller)
     */
    private suspend fun processExpiredAuctionNoSale(auction: Auction) {
        // All database operations happen inside a single transaction.
        // This ensures atomicity: item storage + status update succeed or fail together.
        val success = database.transaction {
            // RACE CONDITION CHECK: A bid could have been placed while we were
            // processing. Re-read to verify auction is still ACTIVE.
            val currentAuction = auctionRepository.getById(this, auction.id)
            if (currentAuction?.status != AuctionStatus.ACTIVE) {
                // Status changed - another process sold or expired it
                return@transaction false
            }

            // Store item INSIDE transaction - will rollback if status update fails.
            try {
                expiredItemManager.storeExpiredItemWithinTransaction(
                    scope = this,
                    ownerUuid = auction.sellerUuid,
                    ownerName = auction.sellerName,
                    itemType = ExpiredItemType.AUCTION_ITEM,
                    sourceId = auction.id,
                    item = auction.itemStack,
                    reason = "EXPIRED"
                )
            } catch (e: Exception) {
                logger.error("Failed to store expired item for auction ${auction.id}, transaction will rollback", e)
                return@transaction false
            }

            // Mark as EXPIRED only after item is safely stored (within same transaction).
            auctionRepository.updateStatus(this, auction.id, AuctionStatus.EXPIRED)
            true
        }

        if (!success) {
            return
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
        bidRepository.getHighestBid(auction.id)?.let { bid ->
            try {
                val bidder = plugin.server.getOfflinePlayer(bid.bidderUuid)
                economy.deposit(bidder, BigDecimal.valueOf(bid.bidAmount))
            } catch (e: Exception) {
                logger.error("Error refunding bidder ${bid.bidderUuid} for auction ${auction.id}", e)
            }
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
