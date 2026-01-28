package bruh.auctionhouse.model

/**
 * Represents the type of an economic transaction.
 */
enum class TransactionType {
    /** Auction sale completed */
    AUCTION_SALE,
    
    /** Bid refund when outbid */
    AUCTION_BID_RETURN,
    
    /** BIN purchase completed */
    BIN_PURCHASE,
    
    /** Order fill completed */
    ORDER_FILL,
    
    /** Order refund when cancelled/expired */
    ORDER_REFUND,
    
    /** Fee charged for listing */
    FEE_LISTING,
    
    /** Fee charged on sale */
    FEE_SALE,
    
    /** Fee charged on order fill */
    FILL_FEE
}
