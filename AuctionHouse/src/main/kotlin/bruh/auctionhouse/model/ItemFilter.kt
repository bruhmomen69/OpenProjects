package bruh.auctionhouse.model

import java.time.Duration
import java.util.UUID

/**
 * Filter options for auction searches.
 *
 * @property searchQuery Text to search in item names
 * @property material Material type to filter by
 * @property auctionType Auction type to filter by
 * @property minPrice Minimum price filter
 * @property maxPrice Maximum price filter
 * @property sellerName Filter by specific seller name
 * @property endingWithin Filter by auctions ending within a duration
 * @property sortBy Sort order for results
 */
data class AuctionFilter(
    val searchQuery: String? = null,
    val material: String? = null,
    val auctionType: AuctionType? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sellerName: String? = null,
    val endingWithin: Duration? = null,
    val sortBy: AuctionSort = AuctionSort.ENDING_SOON
)

/**
 * Sorting options for auction listings.
 */
enum class AuctionSort {
    /** Sort by auctions ending soonest first */
    ENDING_SOON,

    /** Sort by newest auctions first */
    NEWEST,

    /** Sort by lowest price first */
    PRICE_LOW,

    /** Sort by highest price first */
    PRICE_HIGH,

    /** Sort by most bids first */
    MOST_BIDS,

    /** Sort by recently updated auctions first */
    RECENTLY_UPDATED
}

/**
 * Filter options for order searches.
 *
 * @property searchQuery Text to search in item names
 * @property material Material type to filter by
 * @property orderType Order type to filter by
 * @property minPrice Minimum price filter
 * @property maxPrice Maximum price filter
 */
data class OrderFilter(
    val searchQuery: String? = null,
    val material: String? = null,
    val orderType: OrderType? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null
)

/**
 * Sorting options for order listings.
 */
enum class OrderSort {
    /** Sort by newest orders first */
    NEWEST,
    
    /** Sort by lowest price per unit first */
    PRICE_LOW,
    
    /** Sort by highest price per unit first */
    PRICE_HIGH,
    
    /** Sort by most filled orders first */
    MOST_FILLED
}
