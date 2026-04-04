package bruh.auctionhouse.model

/**
 * Represents the current status of an order.
 */
enum class OrderStatus {
    /** Waiting to be filled */
    PENDING,
    
    /** Partially filled */
    PARTIAL,
    
    /** Completely filled */
    FILLED,
    
    /** Expired without complete fill */
    EXPIRED,
    
    /** Cancelled by creator */
    CANCELLED
}
