package bruh.auctionhouse.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a fill (partial or complete) of an order.
 *
 * @property id Unique identifier for the fill (database auto-increment)
 * @property orderId UUID of the order being filled
 * @property fillerUuid UUID of the player who filled the order
 * @property fillerName Name of the filler
 * @property quantity Quantity filled in this transaction
 * @property pricePerUnit Price per unit paid/received
 * @property totalPrice Total price for this fill
 * @property filledAt When the fill occurred
 */
data class OrderFill(
    val id: Long = 0,
    val orderId: UUID,
    val fillerUuid: UUID,
    val fillerName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val totalPrice: Double,
    val filledAt: Instant
)
