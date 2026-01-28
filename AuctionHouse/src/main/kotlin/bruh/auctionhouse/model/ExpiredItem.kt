package bruh.auctionhouse.model

import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Represents an expired item that can be claimed by its owner.
 *
 * @property id Unique identifier for the expired item entry
 * @property ownerUuid UUID of the player who owns this item
 * @property ownerName Name of the owner
 * @property itemType Type of source (AUCTION_ITEM or ORDER_ITEM)
 * @property sourceId UUID of the original auction or order
 * @property itemStack The item stack to be claimed
 * @property reason Reason for expiration (e.g., "EXPIRED", "CANCELLED")
 * @property expiredAt When the item expired
 * @property claimed Whether the item has been claimed
 * @property claimedAt When the item was claimed (null if not claimed)
 */
data class ExpiredItem(
    val id: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val itemType: ExpiredItemType,
    val sourceId: UUID,
    val itemStack: ItemStack,
    val reason: String,
    val expiredAt: Instant,
    val claimed: Boolean = false,
    val claimedAt: Instant? = null
)
