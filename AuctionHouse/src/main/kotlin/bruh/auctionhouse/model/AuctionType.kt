package bruh.auctionhouse.model

/**
 * Represents the type of an auction listing.
 */
enum class AuctionType {
    /** Bidding only - players can only place bids */
    AUCTION,
    
    /** Buy It Now only - players can only purchase instantly at the BIN price */
    BIN,
    
    /** Both bidding and BIN available */
    BOTH
}
