package bruh.auctionhouse.model

/**
 * Result of a claim operation on a consolidated expired item.
 *
 * @property success Whether the claim was successful
 * @property claimedQuantity The actual quantity claimed (may be less than requested)
 * @property message Message describing the result
 */
data class ClaimResult(
    val success: Boolean,
    val claimedQuantity: Int,
    val message: String
)
