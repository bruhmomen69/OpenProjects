package bruh.auctionhouse.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a bid placed on an auction.
 *
 * @property id Unique identifier for the bid (database auto-increment)
 * @property auctionId UUID of the auction this bid is for
 * @property bidderUuid UUID of the player who placed the bid
 * @property bidderName Name of the bidder
 * @property bidAmount The amount bid
 * @property bidTime When the bid was placed
 * @property isOutbid Whether this bid has been outbid by a higher bid
 */
data class Bid(
    val id: Long = 0,
    val auctionId: UUID,
    val bidderUuid: UUID,
    val bidderName: String,
    val bidAmount: Double,
    val bidTime: Instant,
    val isOutbid: Boolean = false
)
