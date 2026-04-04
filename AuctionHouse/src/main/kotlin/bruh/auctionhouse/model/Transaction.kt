package bruh.auctionhouse.model

import java.time.Instant
import java.util.UUID

/**
 * Represents an economic transaction for audit logging.
 *
 * @property id Unique identifier for the transaction (database auto-increment)
 * @property transactionType Type of transaction
 * @property fromUuid UUID of sender (null for system)
 * @property fromName Name of sender
 * @property toUuid UUID of receiver (null for system)
 * @property toName Name of receiver
 * @property amount Amount of money transferred
 * @property taxAmount Tax amount deducted
 * @property itemMaterial Material of item involved (if applicable)
 * @property itemQuantity Quantity of items involved (if applicable)
 * @property referenceId UUID of related auction/order
 * @property timestamp When the transaction occurred
 * @property serverId Identifier of the server where transaction occurred
 */
data class Transaction(
    val id: Long = 0,
    val transactionType: TransactionType,
    val fromUuid: UUID?,
    val fromName: String?,
    val toUuid: UUID?,
    val toName: String?,
    val amount: Double,
    val taxAmount: Double = 0.0,
    val itemMaterial: String?,
    val itemQuantity: Int?,
    val referenceId: UUID?,
    val timestamp: Instant,
    val serverId: String
)
