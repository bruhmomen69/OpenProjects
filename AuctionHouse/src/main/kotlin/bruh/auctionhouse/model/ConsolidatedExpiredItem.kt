package bruh.auctionhouse.model

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Represents a consolidated group of expired items from the same source.
 * Multiple item stacks from the same order/auction are grouped together,
 * allowing users to claim specific quantities rather than individual stacks.
 *
 * @property id Unique identifier for the consolidated group (same as source order ID for order items)
 * @property ownerUuid UUID of the player who owns these items
 * @property ownerName Name of the owner
 * @property itemType Type of source (AUCTION_ITEM or ORDER_ITEM)
 * @property sourceId UUID of the original auction or order
 * @property itemMaterial Material type for grouping/filtering
 * @property itemDisplayName Display name for presentation purposes
 * @property totalQuantity Total items available in this group
 * @property claimedQuantity Items already claimed from this group
 * @property itemStack Template item (amount = 1) for display and recreation
 * @property reason Reason for expiration (e.g., "EXPIRED", "CANCELLED")
 * @property expiredAt When the items expired
 * @property lastUpdatedAt When this consolidated record was last updated
 * @property isFullyClaimed Whether all items have been claimed
 */
data class ConsolidatedExpiredItem(
    val id: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val itemType: ExpiredItemType,
    val sourceId: UUID,
    val itemMaterial: Material,
    val itemDisplayName: String?,
    val totalQuantity: Int,
    val claimedQuantity: Int,
    val itemStack: ItemStack,
    val reason: String,
    val expiredAt: Instant,
    val lastUpdatedAt: Instant,
    val isFullyClaimed: Boolean = false
) {
    /**
     * Returns the quantity of items remaining to be claimed.
     */
    fun remainingQuantity(): Int = totalQuantity - claimedQuantity

    /**
     * Returns whether this group has any items remaining to claim.
     */
    fun isEmpty(): Boolean = remainingQuantity() <= 0
}
