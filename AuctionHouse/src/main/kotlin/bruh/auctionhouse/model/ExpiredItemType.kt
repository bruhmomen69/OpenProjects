package bruh.auctionhouse.model

/**
 * Represents the type of an expired item.
 */
enum class ExpiredItemType {
    /** Item from an expired or cancelled auction */
    AUCTION_ITEM,
    
    /** Item from an expired or cancelled order */
    ORDER_ITEM
}
