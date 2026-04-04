package bruh.auctionhouse.model

/**
 * Represents the type of an order.
 */
enum class OrderType {
    /** Requesting to buy items from other players */
    BUY_ORDER,
    
    /** Offering to sell items to other players */
    SELL_ORDER
}
