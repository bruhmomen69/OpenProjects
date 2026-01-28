# Phase 3: Service Layer - Detailed Implementation Plan

This phase creates the business logic services: AuctionService, OrderService, ExpirationService, and NotificationService.

---

## Step 1: Create Result Classes

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/service/ServiceResults.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.service

import bruh.zchat.auctionhouse.model.Auction
import bruh.zchat.auctionhouse.model.Order
import net.kyori.adventure.text.Component

sealed class ServiceResult<out T> {
    data class Success<T>(val data: T) : ServiceResult<T>()
    data class Failure(val message: Component) : ServiceResult<Nothing>()
}

data class BidResult(
    val success: Boolean,
    val isOutbid: Boolean,
    val previousBidder: String?,
    val message: Component
)

data class PurchaseResult(
    val success: Boolean,
    val auction: Auction?,
    val message: Component
)

data class CreateAuctionResult(
    val success: Boolean,
    val auction: Auction?,
    val feeCharged: Double,
    val message: Component
)

data class CreateOrderResult(
    val success: Boolean,
    val order: Order?,
    val feeCharged: Double,
    val message: Component
)

data class FulfillResult(
    val success: Boolean,
    val quantityFilled: Int,
    val amountEarned: Double,
    val message: Component
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int
)
```

---

## Step 2: Create AuctionService

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/service/AuctionService.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.service

import bruh.zchat.auctionhouse.AuctionHousePlugin
import bruh.zchat.auctionhouse.config.AuctionHouseConfig
import bruh.zchat.auctionhouse.database.*
import bruh.zchat.auctionhouse.economy.EconomyProvider
import bruh.zchat.auctionhouse.model.*
import bruh.zchat.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuctionService(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    private val transactionRepository: TransactionRepository,
    private val economy: EconomyProvider,
    private val translationAPI: TranslationAPI,
    private val serverId: String
) {
    private val mm = MiniMessage.miniMessage()
    
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
                    parsed("min", economy.formatRaw(config.auctions.minStartPrice))
                }
            )
        }
        
        if (startPrice > config.auctions.maxStartPrice) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_PRICE_TOO_HIGH) {
                    parsed("max", economy.formatRaw(config.auctions.maxStartPrice))
                }
            )
        }
        
        // Check BIN price
        if (binPrice != null && type != AuctionType.AUCTION) {
            val minBin = startPrice * config.auctions.minBinMultiplier
            if (binPrice < minBin) {
                return@withContext CreateAuctionResult(
                    false, null, 0.0,
                    mm.deserialize("<red>BIN price must be at least ${economy.formatRaw(minBin)} (${config.auctions.minBinMultiplier}x start price)")
                )
            }
        }
        
        // Check concurrent auctions
        val activeCount = auctionRepository.countPlayerAuctions(seller.uniqueId, AuctionStatus.ACTIVE)
        if (activeCount >= config.auctions.maxConcurrentAuctions) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                translationAPI.getComponentSync(AuctionMessages.AUCTION_MAX_REACHED) {
                    parsed("max", config.auctions.maxConcurrentAuctions.toString())
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
        if (totalFee > 0 && !economy.hasBalance(seller.uniqueId, totalFee)) {
            return@withContext CreateAuctionResult(
                false, null, 0.0,
                mm.deserialize("<red>You need ${economy.formatRaw(totalFee)} to list this auction.")
            )
        }
        
        // Charge fee
        if (totalFee > 0) {
            economy.withdraw(seller.uniqueId, totalFee)
            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.LISTING_FEE,
                    fromUuid = seller.uniqueId,
                    fromName = seller.name,
                    toUuid = null,
                    toName = null,
                    amount = totalFee,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
        }
        
        // Remove item from inventory
        seller.inventory.removeItem(item)
        
        // Create auction
        val auction = Auction(
            id = UUID.randomUUID(),
            sellerUuid = seller.uniqueId,
            sellerName = seller.name,
            itemStack = item.clone(),
            itemMaterial = item.type.name,
            itemDisplayName = item.itemMeta?.displayName()?.let { mm.serialize(it) },
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
    
    suspend fun placeBid(bidder: Player, auctionId: UUID, amount: Double): BidResult = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext BidResult(false, false, null, translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
        
        if (!auction.canBid()) {
            return@withContext BidResult(false, false, null, translationAPI.getComponentSync(AuctionMessages.BID_CANNOT_ON_BIN))
        }
        
        if (auction.sellerUuid == bidder.uniqueId) {
            return@withContext BidResult(false, false, null, mm.deserialize("<red>You cannot bid on your own auction."))
        }
        
        // Get current highest bid
        val highestBid = bidRepository.getHighestBid(auctionId)
        val minBid = highestBid?.bidAmount?.plus(auction.minIncrement) ?: auction.startPrice
        
        if (amount < minBid) {
            return@withContext BidResult(
                false, false, null,
                translationAPI.getComponentSync(AuctionMessages.BID_TOO_LOW) {
                    parsed("min", economy.formatRaw(minBid))
                }
            )
        }
        
        // Check balance
        if (!economy.hasBalance(bidder.uniqueId, amount)) {
            return@withContext BidResult(false, false, null, translationAPI.getComponentSync(AuctionMessages.BID_NO_BALANCE))
        }
        
        // Refund previous bidder
        highestBid?.let { prevBid ->
            economy.deposit(prevBid.bidderUuid, prevBid.bidAmount)
            bidRepository.markAsOutbid(prevBid.id)
            
            // Notify previous bidder (async)
            plugin.server.getPlayer(prevBid.bidderUuid)?.let { player ->
                player.sendMessage(
                    translationAPI.getComponentSync(AuctionMessages.BID_OUTBID) {
                        parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                        parsed("amount", economy.formatRaw(amount))
                    }
                )
            }
        }
        
        // Charge new bidder
        economy.withdraw(bidder.uniqueId, amount)
        
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
                // Extend auction (implementation depends on schema - may need to add extension count to model)
            }
        }
        
        BidResult(true, highestBid != null, highestBid?.bidderName, translationAPI.getComponentSync(AuctionMessages.BID_PLACED))
    }
    
    suspend fun buyNow(buyer: Player, auctionId: UUID): PurchaseResult = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext PurchaseResult(false, null, translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
        
        if (!auction.canBuyNow()) {
            return@withContext PurchaseResult(false, null, translationAPI.getComponentSync(AuctionMessages.BIN_ALREADY_SOLD))
        }
        
        if (auction.sellerUuid == buyer.uniqueId) {
            return@withContext PurchaseResult(false, null, mm.deserialize("<red>You cannot buy your own auction."))
        }
        
        val binPrice = auction.buyNowPrice!!
        
        if (!economy.hasBalance(buyer.uniqueId, binPrice)) {
            return@withContext PurchaseResult(false, null, translationAPI.getComponentSync(AuctionMessages.BIN_NO_BALANCE))
        }
        
        // Refund highest bidder if any
        val highestBid = bidRepository.getHighestBid(auctionId)
        highestBid?.let { bid ->
            economy.deposit(bid.bidderUuid, bid.bidAmount)
        }
        
        // Charge buyer
        economy.withdraw(buyer.uniqueId, binPrice)
        
        // Calculate fees and pay seller
        val saleFee = calculateFee(binPrice, config.auctions.saleFee)
        val sellerAmount = binPrice - saleFee
        
        economy.deposit(auction.sellerUuid, sellerAmount)
        
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
        
        // Give item to buyer
        withContext(Dispatchers.Main) {
            if (buyer.inventory.firstEmpty() == -1) {
                buyer.world.dropItemNaturally(buyer.location, auction.itemStack)
            } else {
                buyer.inventory.addItem(auction.itemStack)
            }
        }
        
        // Notify seller
        plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
            seller.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                    parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                    parsed("price", economy.formatRaw(binPrice))
                }
            )
        }
        
        PurchaseResult(true, auction, translationAPI.getComponentSync(AuctionMessages.BIN_PURCHASED) {
            parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
            parsed("price", economy.formatRaw(binPrice))
        })
    }
    
    suspend fun cancelAuction(player: Player, auctionId: UUID): ServiceResult<Auction> = withContext(Dispatchers.IO) {
        val auction = auctionRepository.getById(auctionId)
            ?: return@withContext ServiceResult.Failure(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
        
        if (auction.sellerUuid != player.uniqueId && !player.hasPermission("auctionhouse.admin.cancel")) {
            return@withContext ServiceResult.Failure(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_OWNER))
        }
        
        if (!auction.isActive()) {
            return@withContext ServiceResult.Failure(translationAPI.getComponentSync(AuctionMessages.AUCTION_ALREADY_ENDED))
        }
        
        // Refund highest bidder
        val highestBid = bidRepository.getHighestBid(auctionId)
        highestBid?.let { bid ->
            economy.deposit(bid.bidderUuid, bid.bidAmount)
            transactionRepository.create(
                Transaction(
                    transactionType = TransactionType.AUCTION_BID_RETURN,
                    fromUuid = null,
                    fromName = null,
                    toUuid = bid.bidderUuid,
                    toName = bid.bidderName,
                    amount = bid.bidAmount,
                    referenceId = auctionId,
                    timestamp = Instant.now(),
                    serverId = serverId
                )
            )
        }
        
        // Return item to seller
        expiredItemRepository.create(
            ExpiredItem(
                id = UUID.randomUUID(),
                ownerUuid = auction.sellerUuid,
                ownerName = auction.sellerName,
                itemType = ExpiredItemType.AUCTION_ITEM,
                sourceId = auctionId,
                itemStack = auction.itemStack,
                reason = "CANCELLED",
                expiredAt = Instant.now()
            )
        )
        
        // Mark as cancelled
        auctionRepository.updateStatus(auctionId, AuctionStatus.CANCELLED)
        
        ServiceResult.Success(auction)
    }
    
    suspend fun getActiveAuctions(filter: AuctionFilter, sort: AuctionSort, page: Int, pageSize: Int): PagedResult<Auction> {
        val auctions = auctionRepository.getActiveAuctions(filter, sort, page, pageSize)
        val totalCount = auctionRepository.countPlayerAuctions(UUID.randomUUID(), AuctionStatus.ACTIVE) // Need total count query
        val totalPages = (totalCount + pageSize - 1) / pageSize
        
        return PagedResult(auctions, page, totalPages, totalCount)
    }
    
    suspend fun getPlayerAuctions(playerId: UUID, status: AuctionStatus?): List<Auction> {
        return auctionRepository.getPlayerAuctions(playerId, status)
    }
    
    suspend fun processExpiredAuctions() {
        val expiredAuctions = auctionRepository.getExpiredAuctions()
        
        for (auction in expiredAuctions) {
            val highestBid = bidRepository.getHighestBid(auction.id)
            
            if (highestBid != null && auction.reservePrice?.let { highestBid.bidAmount >= it } != false) {
                // Auction sold
                val saleFee = calculateFee(highestBid.bidAmount, config.auctions.saleFee)
                val sellerAmount = highestBid.bidAmount - saleFee
                
                economy.deposit(auction.sellerUuid, sellerAmount)
                
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
                
                auctionRepository.markAsSold(auction.id, highestBid.bidderUuid, highestBid.bidderName, highestBid.bidAmount)
                
                // Give item to winner
                plugin.server.getPlayer(highestBid.bidderUuid)?.let { player ->
                    withContext(Dispatchers.Main) {
                        if (player.inventory.firstEmpty() == -1) {
                            player.world.dropItemNaturally(player.location, auction.itemStack)
                        } else {
                            player.inventory.addItem(auction.itemStack)
                        }
                    }
                    player.sendMessage(
                        translationAPI.getComponentSync(AuctionMessages.AUCTION_WON) {
                            parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                            parsed("price", economy.formatRaw(highestBid.bidAmount))
                        }
                    )
                }
                
                // Notify seller
                plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
                    seller.sendMessage(
                        translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD) {
                            parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                            parsed("price", economy.formatRaw(highestBid.bidAmount))
                        }
                    )
                }
            } else {
                // Auction expired without sale
                auctionRepository.updateStatus(auction.id, AuctionStatus.EXPIRED)
                
                // Return item to seller
                expiredItemRepository.create(
                    ExpiredItem(
                        id = UUID.randomUUID(),
                        ownerUuid = auction.sellerUuid,
                        ownerName = auction.sellerName,
                        itemType = ExpiredItemType.AUCTION_ITEM,
                        sourceId = auction.id,
                        itemStack = auction.itemStack,
                        reason = "EXPIRED",
                        expiredAt = Instant.now()
                    )
                )
                
                // Notify seller
                plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
                    seller.sendMessage(
                        translationAPI.getComponentSync(AuctionMessages.AUCTION_EXPIRED) {
                            parsed("item", auction.itemDisplayName ?: auction.itemMaterial)
                        }
                    )
                }
                
                // Refund highest bidder if exists but didn't meet reserve
                highestBid?.let { bid ->
                    economy.deposit(bid.bidderUuid, bid.bidAmount)
                }
            }
        }
    }
    
    private fun calculateFee(amount: Double, feeConfig: bruh.zchat.auctionhouse.config.FeeConfig): Double {
        val fee = when (feeConfig.type) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }
        
        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }
}
```

---

## Step 3: Create OrderService

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/service/OrderService.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.service

import bruh.zchat.auctionhouse.AuctionHousePlugin
import bruh.zchat.auctionhouse.config.AuctionHouseConfig
import bruh.zchat.auctionhouse.database.*
import bruh.zchat.auctionhouse.economy.EconomyProvider
import bruh.zchat.auctionhouse.model.*
import bruh.zchat.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OrderService(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val orderRepository: OrderRepository,
    private val orderFillRepository: OrderFillRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    private val transactionRepository: TransactionRepository,
    private val economy: EconomyProvider,
    private val translationAPI: TranslationAPI,
    private val serverId: String
) {
    private val mm = MiniMessage.miniMessage()
    
    suspend fun createBuyOrder(
        creator: Player,
        material: Material,
        displayName: String?,
        quantity: Int,
        pricePerUnit: Double,
        allowPartial: Boolean,
        minFillQuantity: Int?,
        duration: Duration
    ): CreateOrderResult = withContext(Dispatchers.IO) {
        if (!config.orders.enabled) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED)
            )
        }
        
        // Validate quantity
        if (quantity < config.orders.minQuantity || quantity > config.orders.maxQuantity) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_QUANTITY) {
                    parsed("min", config.orders.minQuantity.toString())
                    parsed("max", config.orders.maxQuantity.toString())
                }
            )
        }
        
        // Validate price
        if (pricePerUnit < config.orders.minPricePerUnit || pricePerUnit > config.orders.maxPricePerUnit) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_PRICE) {
                    parsed("min", economy.formatRaw(config.orders.minPricePerUnit))
                    parsed("max", economy.formatRaw(config.orders.maxPricePerUnit))
                }
            )
        }
        
        // Check concurrent orders
        val activeCount = orderRepository.countPlayerOrders(creator.uniqueId, OrderStatus.PENDING)
        if (activeCount >= config.orders.maxConcurrentOrders) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_MAX_REACHED) {
                    parsed("max", config.orders.maxConcurrentOrders.toString())
                }
            )
        }
        
        // Calculate total cost
        val totalCost = quantity * pricePerUnit
        val listingFee = calculateFee(totalCost, config.orders.listingFee)
        val totalRequired = totalCost + listingFee
        
        // Check balance
        if (!economy.hasBalance(creator.uniqueId, totalRequired)) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                mm.deserialize("<red>You need ${economy.formatRaw(totalRequired)} to create this order.")
            )
        }
        
        // Charge fees
        economy.withdraw(creator.uniqueId, totalRequired)
        
        // Create order
        val order = Order(
            id = UUID.randomUUID(),
            creatorUuid = creator.uniqueId,
            creatorName = creator.name,
            orderType = OrderType.BUY_ORDER,
            itemMaterial = material,
            itemDisplayName = displayName,
            itemLoreHash = null,
            itemNbtHash = null,
            itemStack = null,
            quantityRequested = quantity,
            quantityFilled = 0,
            pricePerUnit = pricePerUnit,
            totalPrice = totalCost,
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(duration),
            allowPartial = allowPartial,
            minFillQuantity = minFillQuantity
        )
        
        orderRepository.create(order)
        
        CreateOrderResult(true, order, listingFee, translationAPI.getComponentSync(OrderMessages.ORDER_CREATED))
    }
    
    suspend fun createSellOrder(
        creator: Player,
        item: ItemStack,
        pricePerUnit: Double,
        duration: Duration
    ): CreateOrderResult = withContext(Dispatchers.IO) {
        if (!config.orders.enabled) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED)
            )
        }
        
        val quantity = item.amount
        
        // Validate price
        if (pricePerUnit < config.orders.minPricePerUnit || pricePerUnit > config.orders.maxPricePerUnit) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_PRICE) {
                    parsed("min", economy.formatRaw(config.orders.minPricePerUnit))
                    parsed("max", economy.formatRaw(config.orders.maxPricePerUnit))
                }
            )
        }
        
        // Check concurrent orders
        val activeCount = orderRepository.countPlayerOrders(creator.uniqueId, OrderStatus.PENDING)
        if (activeCount >= config.orders.maxConcurrentOrders) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_MAX_REACHED) {
                    parsed("max", config.orders.maxConcurrentOrders.toString())
                }
            )
        }
        
        val listingFee = calculateFee(quantity * pricePerUnit, config.orders.listingFee)
        
        // Charge listing fee
        if (listingFee > 0 && !economy.hasBalance(creator.uniqueId, listingFee)) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                mm.deserialize("<red>You need ${economy.formatRaw(listingFee)} to list this order.")
            )
        }
        
        if (listingFee > 0) {
            economy.withdraw(creator.uniqueId, listingFee)
        }
        
        // Remove items from inventory
        creator.inventory.removeItem(item)
        
        // Create order
        val order = Order(
            id = UUID.randomUUID(),
            creatorUuid = creator.uniqueId,
            creatorName = creator.name,
            orderType = OrderType.SELL_ORDER,
            itemMaterial = item.type,
            itemDisplayName = item.itemMeta?.displayName()?.let { mm.serialize(it) },
            itemLoreHash = null, // Calculate hash if needed
            itemNbtHash = null,
            itemStack = item.clone(),
            quantityRequested = quantity,
            quantityFilled = 0,
            pricePerUnit = pricePerUnit,
            totalPrice = quantity * pricePerUnit,
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(duration),
            allowPartial = false, // Sell orders typically don't allow partial fills
            minFillQuantity = null
        )
        
        orderRepository.create(order)
        
        CreateOrderResult(true, order, listingFee, translationAPI.getComponentSync(OrderMessages.ORDER_CREATED))
    }
    
    suspend fun fulfillOrder(
        filler: Player,
        orderId: UUID,
        items: List<ItemStack>
    ): FulfillResult = withContext(Dispatchers.IO) {
        val order = orderRepository.getById(orderId)
            ?: return@withContext FulfillResult(false, 0, 0.0, translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
        
        if (!order.isActive()) {
            return@withContext FulfillResult(false, 0, 0.0, translationAPI.getComponentSync(OrderMessages.ORDER_ALREADY_FILLED))
        }
        
        if (order.creatorUuid == filler.uniqueId) {
            return@withContext FulfillResult(false, 0, 0.0, mm.deserialize("<red>You cannot fulfill your own order."))
        }
        
        // Validate items match order requirements
        val totalQuantity = items.sumOf { it.amount }
        val remaining = order.remainingQuantity()
        
        if (totalQuantity > remaining) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                mm.deserialize("<red>You provided too many items. Maximum needed: $remaining")
            )
        }
        
        if (!order.allowPartial && totalQuantity < remaining) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                mm.deserialize("<red>This order requires the full quantity ($remaining) at once.")
            )
        }
        
        order.minFillQuantity?.let { min ->
            if (totalQuantity < min) {
                return@withContext FulfillResult(
                    false, 0, 0.0,
                    translationAPI.getComponentSync(OrderMessages.ORDER_MIN_FILL_NOT_MET) {
                        parsed("min", min.toString())
                    }
                )
            }
        }
        
        // Calculate earnings
        val fillPrice = totalQuantity * order.pricePerUnit
        val fillFee = calculateFee(fillPrice, config.orders.fillFee)
        val earnings = fillPrice - fillFee
        
        // For buy orders: creator pays filler
        // For sell orders: filler pays creator
        when (order.orderType) {
            OrderType.BUY_ORDER -> {
                // Check creator still has funds
                if (!economy.hasBalance(order.creatorUuid, fillPrice)) {
                    return@withContext FulfillResult(
                        false, 0, 0.0,
                        mm.deserialize("<red>The order creator no longer has sufficient funds.")
                    )
                }
                
                // Transfer funds
                economy.withdraw(order.creatorUuid, fillPrice)
                economy.deposit(filler.uniqueId, earnings)
                
                // Give items to creator
                withContext(Dispatchers.Main) {
                    plugin.server.getPlayer(order.creatorUuid)?.let { creator ->
                        items.forEach { item ->
                            if (creator.inventory.firstEmpty() == -1) {
                                creator.world.dropItemNaturally(creator.location, item)
                            } else {
                                creator.inventory.addItem(item)
                            }
                        }
                    }
                }
            }
            OrderType.SELL_ORDER -> {
                // Filler buys items from creator
                if (!economy.hasBalance(filler.uniqueId, fillPrice)) {
                    return@withContext FulfillResult(
                        false, 0, 0.0,
                        mm.deserialize("<red>You don't have enough money to fulfill this order.")
                    )
                }
                
                economy.withdraw(filler.uniqueId, fillPrice)
                economy.deposit(order.creatorUuid, earnings)
                
                // Give items to filler
                withContext(Dispatchers.Main) {
                    items.forEach { item ->
                        if (filler.inventory.firstEmpty() == -1) {
                            filler.world.dropItemNaturally(filler.location, item)
                        } else {
                            filler.inventory.addItem(item)
                        }
                    }
                }
            }
        }
        
        // Remove items from filler
        withContext(Dispatchers.Main) {
            items.forEach { item ->
                filler.inventory.removeItemAnySlot(item)
            }
        }
        
        // Create fill record
        val fill = OrderFill(
            orderId = orderId,
            fillerUuid = filler.uniqueId,
            fillerName = filler.name,
            quantity = totalQuantity,
            pricePerUnit = order.pricePerUnit,
            totalPrice = fillPrice,
            filledAt = Instant.now()
        )
        orderFillRepository.create(fill)
        
        // Update order status
        val newFilledQuantity = order.quantityFilled + totalQuantity
        val newStatus = when {
            newFilledQuantity >= order.quantityRequested -> OrderStatus.FILLED
            else -> OrderStatus.PARTIAL
        }
        orderRepository.updateFillStatus(orderId, newFilledQuantity, newStatus)
        
        // Log transaction
        transactionRepository.create(
            Transaction(
                transactionType = TransactionType.ORDER_FILL,
                fromUuid = if (order.orderType == OrderType.BUY_ORDER) order.creatorUuid else filler.uniqueId,
                fromName = if (order.orderType == OrderType.BUY_ORDER) order.creatorName else filler.name,
                toUuid = if (order.orderType == OrderType.BUY_ORDER) filler.uniqueId else order.creatorUuid,
                toName = if (order.orderType == OrderType.BUY_ORDER) filler.name else order.creatorName,
                amount = earnings,
                taxAmount = fillFee,
                itemMaterial = order.itemMaterial.name,
                itemQuantity = totalQuantity,
                referenceId = orderId,
                timestamp = Instant.now(),
                serverId = serverId
            )
        )
        
        // Notify order creator
        plugin.server.getPlayer(order.creatorUuid)?.let { creator ->
            if (newStatus == OrderStatus.FILLED) {
                creator.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_FILLED))
            } else {
                creator.sendMessage(
                    translationAPI.getComponentSync(OrderMessages.ORDER_PARTIAL_FILL) {
                        parsed("filled", newFilledQuantity.toString())
                        parsed("total", order.quantityRequested.toString())
                    }
                )
            }
        }
        
        FulfillResult(true, totalQuantity, earnings, translationAPI.getComponentSync(OrderMessages.ORDER_FULFILLED) {
            parsed("amount", economy.formatRaw(earnings))
        })
    }
    
    suspend fun cancelOrder(player: Player, orderId: UUID): ServiceResult<Order> = withContext(Dispatchers.IO) {
        val order = orderRepository.getById(orderId)
            ?: return@withContext ServiceResult.Failure(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
        
        if (order.creatorUuid != player.uniqueId && !player.hasPermission("auctionhouse.admin.cancel")) {
            return@withContext ServiceResult.Failure(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_OWNER))
        }
        
        if (!order.isActive()) {
            return@withContext ServiceResult.Failure(translationAPI.getComponentSync(OrderMessages.ORDER_ALREADY_FILLED))
        }
        
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED)
        
        // Return items or refund based on order type
        when (order.orderType) {
            OrderType.SELL_ORDER -> {
                // Return items to seller
                order.itemStack?.let { itemStack ->
                    expiredItemRepository.create(
                        ExpiredItem(
                            id = UUID.randomUUID(),
                            ownerUuid = order.creatorUuid,
                            ownerName = order.creatorName,
                            itemType = ExpiredItemType.ORDER_ITEM,
                            sourceId = orderId,
                            itemStack = itemStack,
                            reason = "CANCELLED",
                            expiredAt = Instant.now()
                        )
                    )
                }
            }
            OrderType.BUY_ORDER -> {
                // Refund remaining money
                val remainingValue = order.remainingValue()
                if (remainingValue > 0) {
                    economy.deposit(order.creatorUuid, remainingValue)
                    transactionRepository.create(
                        Transaction(
                            transactionType = TransactionType.ORDER_REFUND,
                            fromUuid = null,
                            fromName = null,
                            toUuid = order.creatorUuid,
                            toName = order.creatorName,
                            amount = remainingValue,
                            referenceId = orderId,
                            timestamp = Instant.now(),
                            serverId = serverId
                        )
                    )
                }
            }
        }
        
        ServiceResult.Success(order)
    }
    
    suspend fun getActiveOrders(filter: OrderFilter, sort: OrderSort, page: Int, pageSize: Int): PagedResult<Order> {
        val orders = orderRepository.getActiveOrders(filter, sort, page, pageSize)
        val totalCount = orderRepository.countPlayerOrders(UUID.randomUUID(), OrderStatus.PENDING)
        val totalPages = (totalCount + pageSize - 1) / pageSize
        
        return PagedResult(orders, page, totalPages, totalCount)
    }
    
    suspend fun getPlayerOrders(playerId: UUID, status: OrderStatus?): List<Order> {
        return orderRepository.getPlayerOrders(playerId, status)
    }
    
    suspend fun processExpiredOrders() {
        val expiredOrders = orderRepository.getExpiredOrders()
        
        for (order in expiredOrders) {
            orderRepository.updateStatus(order.id, OrderStatus.EXPIRED)
            
            // Return items or refund
            when (order.orderType) {
                OrderType.SELL_ORDER -> {
                    order.itemStack?.let { itemStack ->
                        expiredItemRepository.create(
                            ExpiredItem(
                                id = UUID.randomUUID(),
                                ownerUuid = order.creatorUuid,
                                ownerName = order.creatorName,
                                itemType = ExpiredItemType.ORDER_ITEM,
                                sourceId = order.id,
                                itemStack = itemStack,
                                reason = "EXPIRED",
                                expiredAt = Instant.now()
                            )
                        )
                    }
                }
                OrderType.BUY_ORDER -> {
                    val remainingValue = order.remainingValue()
                    if (remainingValue > 0) {
                        economy.deposit(order.creatorUuid, remainingValue)
                    }
                }
            }
            
            // Notify creator
            plugin.server.getPlayer(order.creatorUuid)?.let { creator ->
                creator.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_EXPIRED))
            }
        }
    }
    
    private fun calculateFee(amount: Double, feeConfig: bruh.zchat.auctionhouse.config.FeeConfig): Double {
        val fee = when (feeConfig.type) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }
        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }
}
```

---

## Phase 3 Completion Checklist

After completing Phase 3, you should have:

- [ ] `ServiceResults.kt` with result data classes
- [ ] `AuctionService.kt` with full auction business logic
- [ ] `OrderService.kt` with full order business logic

## Build Verification

```bash
./gradlew :AuctionHouse:build
```

The plugin should compile with all service classes ready for Phase 4 (GUI Layer).
