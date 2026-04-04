package bruh.auctionhouse.model

/**
 * Represents the current status of an auction.
 */
enum class AuctionStatus {
    /** Currently running and accepting bids/purchases */
    ACTIVE,
    
    /** Sold via BIN or winning bid */
    SOLD,
    
    /** Ended without sale (expired) */
    EXPIRED,
    
    /** Cancelled by seller */
    CANCELLED
}
