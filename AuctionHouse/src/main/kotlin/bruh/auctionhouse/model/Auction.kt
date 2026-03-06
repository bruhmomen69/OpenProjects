package bruh.auctionhouse.model

import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Represents an auction listing in the auction house.
 *
 * @property id Unique identifier for the auction
 * @property sellerUuid UUID of the player who created the auction
 * @property sellerName Name of the seller
 * @property itemStack The actual item being auctioned (serialized)
 * @property itemMaterial Material type for filtering
 * @property itemDisplayName Display name of the item for searching
 * @property auctionType Type of auction (AUCTION, BIN, or BOTH)
 * @property startPrice Starting bid price
 * @property buyNowPrice Buy-it-now price (null if BIN not available)
 * @property reservePrice Minimum price that must be met for auction to succeed
 * @property minIncrement Minimum bid increment
 * @property status Current status of the auction
 * @property createdAt When the auction was created
 * @property endsAt When the auction expires
 * @property soldAt When the auction was sold (null if not sold)
 * @property soldToUuid UUID of buyer (null if not sold)
 * @property soldToName Name of buyer (null if not sold)
 * @property finalPrice Final sale price (null if not sold)
 * @property viewCount Number of times the auction has been viewed
 * @property bidCount Number of bids placed
 * @property isAnonymous Whether the seller is hidden
 * @property extensionCount Number of times the auction has been extended (anti-snipe auto extensions)
 * @property manualExtensionCount Number of manual extensions by seller
 */
data class Auction(
    val id: UUID,
    val sellerUuid: UUID,
    val sellerName: String,
    val itemStack: ItemStack,
    val itemMaterial: String,
    val itemDisplayName: String?,

    val auctionType: AuctionType,
    val startPrice: Double,
    val buyNowPrice: Double?,
    val reservePrice: Double?,
    val minIncrement: Double,

    val status: AuctionStatus,
    val createdAt: Instant,
    val endsAt: Instant,
    val soldAt: Instant? = null,
    val soldToUuid: UUID? = null,
    val soldToName: String? = null,
    val finalPrice: Double? = null,

    val viewCount: Int = 0,
    val bidCount: Int = 0,
    val isAnonymous: Boolean = false,
    val extensionCount: Int = 0,
    val manualExtensionCount: Int = 0
) {
    val shortId: String get() = id.toString().take(8)
    
    fun isActive(): Boolean = status == AuctionStatus.ACTIVE
    
    fun hasEnded(): Boolean = status != AuctionStatus.ACTIVE || Instant.now().isAfter(endsAt)
    
    fun canBid(): Boolean = isActive() && (auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH)
    
    fun canBuyNow(): Boolean = isActive() && (auctionType == AuctionType.BIN || auctionType == AuctionType.BOTH) && buyNowPrice != null
}
