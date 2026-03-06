package bruh.auctionhouse.service

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.Order
import net.kyori.adventure.text.Component

/**
 * Sealed class representing the result of a service operation.
 */
sealed class ServiceResult<out T> {
    /**
     * Represents a successful operation with data.
     */
    data class Success<T>(val data: T) : ServiceResult<T>()

    /**
     * Represents a failed operation with an error message.
     */
    data class Failure(val message: Component) : ServiceResult<Nothing>()
}

/**
 * Result of placing a bid on an auction.
 */
data class BidResult(
    val success: Boolean,
    val isOutbid: Boolean,
    val previousBidder: String?,
    val message: Component
)

/**
 * Result of a BIN purchase.
 */
data class PurchaseResult(
    val success: Boolean,
    val auction: Auction?,
    val message: Component
)

/**
 * Result of creating an auction.
 */
data class CreateAuctionResult(
    val success: Boolean,
    val auction: Auction?,
    val feeCharged: Double,
    val message: Component
)

/**
 * Result of creating an order.
 */
data class CreateOrderResult(
    val success: Boolean,
    val order: Order?,
    val feeCharged: Double,
    val message: Component
)

/**
 * Result of fulfilling an order.
 */
data class FulfillResult(
    val success: Boolean,
    val quantityFilled: Int,
    val amountEarned: Double,
    val message: Component
)

/**
 * Paged result wrapper for listing operations.
 */
data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int
)

/**
 * Result of creating bulk auctions.
 */
data class BulkListingResult(
    val success: Boolean,
    val auctionsCreated: Int,
    val auctionsFailed: Int,
    val totalFeesCharged: Double,
    val message: Component
)
