package bruh.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

/**
 * Message keys for Order-related messages.
 */
enum class OrderMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // Order creation
    ORDER_CREATED("order_created", "<green>Order created successfully!"),
    ORDER_CANCELLED("order_cancelled", "<yellow>Order cancelled."),
    ORDER_NOT_FOUND("order_not_found", "<red>Order not found."),
    ORDER_NOT_OWNER("order_not_owner", "<red>You don't own this order."),
    ORDER_INVALID_QUANTITY("order_invalid_quantity", "<red>Quantity must be between {min} and {max}."),
    ORDER_INVALID_PRICE("order_invalid_price", "<red>Price per unit must be between {min} and {max}."),
    ORDER_MAX_REACHED("order_max_reached", "<red>You can only have {max} active orders."),
    
    // Order filling
    ORDER_FILLED("order_filled", "<green>Your order has been completely filled!"),
    ORDER_PARTIAL_FILL("order_partial_fill", "<yellow>Your order was partially filled ({filled}/{total})."),
    ORDER_FULFILLED("order_fulfilled", "<green>You fulfilled an order and received <gold>{amount}</gold>!"),
    ORDER_NOT_ENOUGH_ITEMS("order_not_enough_items", "<red>You don't have enough items to fulfill this order."),
    ORDER_MIN_FILL_NOT_MET("order_min_fill_not_met", "<red>You must fulfill at least {min} items."),
    ORDER_WRONG_ITEM("order_wrong_item", "<red>The items don't match the order requirements."),
    
    // Order status
    ORDER_EXPIRED("order_expired", "<yellow>Your order has expired."),
    ORDER_ALREADY_FILLED("order_already_filled", "<red>This order has already been filled."),
    ORDER_SYSTEM_DISABLED("order_system_disabled", "<red>The order system is currently disabled.");
}