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
    ORDER_INVALID_QUANTITY("order_invalid_quantity", "<red>Quantity must be between <min> and <max>."),
    ORDER_INVALID_PRICE("order_invalid_price", "<red>Price per unit must be between <min> and <max>."),
    ORDER_MAX_REACHED("order_max_reached", "<red>You can only have <max> active orders."),
    
    // Order filling
    ORDER_FILLED("order_filled", "<green>Your order has been completely filled!"),
    ORDER_PARTIAL_FILL("order_partial_fill", "<yellow>Your order was partially filled (<filled>/<total>)."),
    ORDER_FULFILLED("order_fulfilled", "<green>You fulfilled an order and received <gold><amount></gold>!"),
    ORDER_NOT_ENOUGH_ITEMS("order_not_enough_items", "<red>You don't have enough <white><material></white> to fulfill this order. <gray>Required: <white><required></white>, You have: <white><have></white>"),
    ORDER_MIN_FILL_NOT_MET("order_min_fill_not_met", "<red>This order requires at least <white><min></white> <material>. <gray>You only have: <white><have></white>"),
    ORDER_WRONG_ITEM("order_wrong_item", "<red>The items don't match the order requirements."),
    
    // Order status
    ORDER_EXPIRED("order_expired", "<yellow>Your order has expired."),
    ORDER_ALREADY_FILLED("order_already_filled", "<red>This order has already been filled."),
    ORDER_SYSTEM_DISABLED("order_system_disabled", "<red>The order system is currently disabled."),

    // Login notifications
    ORDER_FILLED_LOGIN("order_filled_login", "<yellow>Welcome back! <gold><count></gold> of your order(s) were filled while you are away."),

    // Service-level messages
    ORDER_INSUFFICIENT_FUNDS("order_insufficient_funds", "<red>You need <amount> to create this order."),
    ORDER_INSUFFICIENT_FUNDS_LISTING("order_insufficient_funds_listing", "<red>You need <amount> to list this order."),
    ORDER_CANNOT_OWN_ORDER("order_cannot_own_order", "<red>You cannot fulfill your own order."),
    ORDER_ITEM_MISMATCH("order_item_mismatch", "<red><reason>"),
    ORDER_TOO_MANY_ITEMS("order_too_many_items", "<red>You provided too many items. Maximum needed: <max>"),
    ORDER_REQUIRES_FULL_QUANTITY("order_requires_full_quantity", "<red>This order requires the full quantity (<quantity>) at once."),
    ORDER_CREATOR_NO_FUNDS("order_creator_no_funds", "<red>The order creator no longer has sufficient funds."),
    ORDER_FILLED_NOTIFICATION("order_filled_notification", "<green>Your buy order was filled! Items are available in your expired items menu."),
    ORDER_FULFILL_NO_MONEY("order_fulfill_no_money", "<red>You don't have enough money to fulfill this order."),
    ORDER_INVENTORY_FULL("order_inventory_full", "<red>Your inventory was full. <count> item(s) have been stored in your expired items."),
    ORDER_MUST_HOLD_ITEM("order_must_hold_item", "<red>You must hold an item to create a sell order."),
    ORDER_CANNOT_EDIT_PARTIAL("order_cannot_edit_partial", "<red>Cannot edit price on orders that have been partially filled."),
    ORDER_PRICE_UPDATED("order_price_updated", "<green>Order price updated to <gold><price></gold> per unit."),
    ORDER_QUICK_SELL_NO_ORDER("order_quick_sell_no_order", "<red>No buy order found for that material."),
    ORDER_QUICK_SELL_CONFIRM("order_quick_sell_confirm", "<yellow>Sell <white><quantity> <material></white> for <gold><price></gold> to <white><buyer></white>?"),
    ORDER_QUICK_SELL_SUCCESS("order_quick_sell_success", "<green>Sold <quantity> <material> for <gold><price></gold>!")
}