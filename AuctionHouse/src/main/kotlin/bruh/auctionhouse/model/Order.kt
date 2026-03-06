package bruh.auctionhouse.model

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Represents a buy or sell order in the order system.
 *
 * @property id Unique identifier for the order
 * @property creatorUuid UUID of the player who created the order
 * @property creatorName Name of the creator
 * @property orderType Type of order (BUY_ORDER or SELL_ORDER)
 * @property itemMaterial Material being requested/offered
 * @property itemDisplayName Display name filter for matching items
 * @property itemLoreHash Hash of lore for matching specific items
 * @property itemNbtHash Hash of NBT data for matching specific items
 * @property itemStack The actual item stack (for sell orders with specific items)
 * @property quantityRequested Total quantity requested/offered
 * @property quantityFilled Quantity already filled
 * @property pricePerUnit Price per single item
 * @property totalPrice Total price for the full quantity
 * @property status Current status of the order
 * @property createdAt When the order was created
 * @property expiresAt When the order expires
 * @property filledAt When the order was fully filled (null if not filled)
 * @property allowPartial Whether partial fills are allowed
 * @property minFillQuantity Minimum quantity for partial fills
 */
data class Order(
    val id: UUID,
    val creatorUuid: UUID,
    val creatorName: String,
    val orderType: OrderType,
    
    val itemMaterial: Material,
    val itemDisplayName: String?,
    val itemLoreHash: String?,
    val itemNbtHash: String?,
    val itemStack: ItemStack?,
    
    val quantityRequested: Int,
    val quantityFilled: Int = 0,
    val pricePerUnit: Double,
    val totalPrice: Double,
    
    val status: OrderStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val filledAt: Instant? = null,
    
    val allowPartial: Boolean = true,
    val minFillQuantity: Int? = null
) {
    val shortId: String get() = id.toString().take(8)
    
    fun isActive(): Boolean = status == OrderStatus.PENDING || status == OrderStatus.PARTIAL
    
    fun remainingQuantity(): Int = quantityRequested - quantityFilled
    
    fun totalValue(): Double = quantityRequested * pricePerUnit
    
    fun remainingValue(): Double = remainingQuantity() * pricePerUnit
}
