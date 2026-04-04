package bruh.auctionhouse.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a watched auction or order by a player.
 *
 * @property id Unique identifier for the watchlist entry
 * @property playerUuid UUID of the player watching the auction/order
 * @property auctionId UUID of the auction being watched (null if watching an order)
 * @property orderId UUID of the order being watched (null if watching an auction)
 * @property orderType Type of order (BUY_ORDER or SELL_ORDER, null for auctions)
 * @property addedAt When the item was added to watchlist
 * @property lastNotifiedAt Last time a notification was sent for this item
 * @property hasNewActivity Whether there's new activity since last view
 */
data class WatchlistEntry(
    val id: Long = 0,
    val playerUuid: UUID,
    val auctionId: UUID? = null,
    val orderId: UUID? = null,
    val orderType: OrderType? = null,
    val addedAt: Instant = Instant.now(),
    val lastNotifiedAt: Instant? = null,
    val hasNewActivity: Boolean = false
)

/**
 * Types of notifications that can be sent to players.
 */
enum class NotificationType {
    /** New bid placed on watched auction */
    NEW_BID,
    
    /** Player has been outbid */
    OUTBID,
    
    /** BIN price was reduced */
    PRICE_DROP,
    
    /** Auction ending soon */
    ENDING_SOON,
    
    /** Auction has sold */
    SOLD,
    
    /** Order has been filled */
    ORDER_FILLED,
    
    /** General system notification */
    GENERAL
}

/**
 * Represents a notification for a player.
 *
 * @property id Unique identifier for the notification
 * @property playerUuid UUID of the player receiving the notification
 * @property type Type of notification
 * @property title Short title of the notification
 * @property message Detailed message content
 * @property relatedAuctionId Related auction UUID (if applicable)
 * @property relatedOrderId Related order UUID (if applicable)
 * @property createdAt When the notification was created
 * @property isRead Whether the notification has been read
 * @property expiresAt When the notification expires (null = never)
 */
data class Notification(
    val id: Long = 0,
    val playerUuid: UUID,
    val type: NotificationType,
    val title: String,
    val message: String,
    val relatedAuctionId: UUID? = null,
    val relatedOrderId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val isRead: Boolean = false,
    val expiresAt: Instant? = null
) {
    /**
     * Checks if the notification is expired.
     */
    fun isExpired(): Boolean = expiresAt?.let { Instant.now().isAfter(it) } ?: false
}
