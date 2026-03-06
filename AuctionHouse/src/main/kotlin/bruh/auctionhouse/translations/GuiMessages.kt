package bruh.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

/**
 * Message keys for GUI-related messages.
 */
enum class GuiMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // Titles
    MAIN_TITLE("main_title", "Auction House"),
    MY_AUCTIONS_TITLE("my_auctions_title", "My Auctions"),
    MY_ORDERS_TITLE("my_orders_title", "My Orders"),
    ORDERS_TITLE("orders_title", "Order Browser"),
    CREATE_AUCTION_TITLE("create_auction_title", "Create Auction"),
    CREATE_ORDER_TITLE("create_order_title", "Create Order"),
    MATERIAL_PICKER_TITLE("material_picker_title", "Select Material"),
    CLAIMABLE_ITEMS_TITLE("claimable_items_title", "Claimable Items"),
    EXPIRED_ITEMS_TITLE("expired_items_title", "Claimable Items"),
    
    // Navigation
    PREVIOUS_PAGE("previous_page", "<gray>← Previous Page"),
    NEXT_PAGE("next_page", "<gray>Next Page →"),
    BACK("back", "<gray>Back"),
    CLOSE("close", "<red>Close"),
    
    // Sorting
    SORT_TITLE("sort_title", "Sort Options"),
    SORT_ENDING_SOON("sort_ending_soon", "Ending Soon"),
    SORT_NEWEST("sort_newest", "Newest First"),
    SORT_PRICE_LOW("sort_price_low", "Price: Low to High"),
    SORT_PRICE_HIGH("sort_price_high", "Price: High to Low"),
    SORT_MOST_BIDS("sort_most_bids", "Most Bids"),
    
    // Filters
    FILTER_ALL("filter_all", "All Auctions"),
    FILTER_AUCTION("filter_auction", "Auction Only"),
    FILTER_BIN("filter_bin", "Buy It Now Only"),
    FILTER_BOTH("filter_both", "Auction + BIN"),
    
    // Buttons
    BUTTON_CREATE_AUCTION("button_create_auction", "<green>Create Auction"),
    BUTTON_CREATE_ORDER("button_create_order", "<green>Create Order"),
    BUTTON_REFRESH("button_refresh", "<yellow>Refresh"),
    BUTTON_SORT("button_sort", "<yellow>Sort"),
    BUTTON_FILTER("button_filter", "<yellow>Filter"),
    BUTTON_MY_AUCTIONS("button_my_auctions", "<aqua>My Auctions"),
    BUTTON_MY_ORDERS("button_my_orders", "<aqua>My Orders"),
    BUTTON_EXPIRED("button_expired", "<red>Claimable Items"),
    BUTTON_BUY_ORDERS("button_buy_orders", "<green>Buy Orders"),
    BUTTON_SELL_ORDERS("button_sell_orders", "<green>Sell Orders"),
    BUTTON_SEARCH("button_search", "<yellow>Search"),
    
    // Auction item display
    AUCTION_ITEM_NAME("auction_item_name", "<gold>{item}"),
    AUCTION_ITEM_PRICE("auction_item_price", "<yellow>Price: <gold>{price}"),
    AUCTION_ITEM_BIN("auction_item_bin", "<green>BIN: <gold>{price}"),
    AUCTION_ITEM_BID("auction_item_bid", "<yellow>Current Bid: <gold>{price}"),
    AUCTION_ITEM_BIDS("auction_item_bids", "<gray>Bids: <white>{count}"),
    AUCTION_ITEM_TIME_LEFT("auction_item_time_left", "<gray>Time Left: <white>{time}"),
    AUCTION_ITEM_SELLER("auction_item_seller", "<gray>Seller: <white>{seller}"),
    AUCTION_ITEM_ANONYMOUS("auction_item_anonymous", "<gray>Seller: <i>Anonymous</i>"),
    AUCTION_ITEM_CLICK_BID("auction_item_click_bid", "<yellow>Click to place bid"),
    AUCTION_ITEM_CLICK_BUY("auction_item_click_buy", "<green>Click to buy now"),
    AUCTION_ITEM_CLICK_VIEW("auction_item_click_view", "<gray>Click to view details"),
    
    // Order item display
    ORDER_ITEM_NAME("order_item_name", "<gold>{item}"),
    ORDER_ITEM_QUANTITY("order_item_quantity", "<yellow>Quantity: <gold>{current}/{total}"),
    ORDER_ITEM_PRICE("order_item_price", "<yellow>Price per unit: <gold>{price}"),
    ORDER_ITEM_TOTAL("order_item_total", "<yellow>Total value: <gold>{total}"),
    ORDER_ITEM_TYPE("order_item_type", "<gray>Type: {type}"),
    ORDER_ITEM_REQUESTER("order_item_requester", "<gray>Requester: <white>{player}"),
    ORDER_ITEM_TIME_LEFT("order_item_time_left", "<gray>Time Left: <white>{time}"),
    ORDER_ITEM_PARTIAL("order_item_partial", "<gray>Partial fills: <green>Allowed"),
    ORDER_ITEM_NO_PARTIAL("order_item_no_partial", "<gray>Partial fills: <red>Not allowed"),
    ORDER_ITEM_CLICK_FILL("order_item_click_fill", "<green>Click to fulfill order"),
    ORDER_ITEM_CLICK_VIEW("order_item_click_view", "<gray>Click to view details"),
    
    // Order types
    ORDER_TYPE_BUY("order_type_buy", "<green>Buy Order"),
    ORDER_TYPE_SELL("order_type_sell", "<red>Sell Order"),
    
    // Create auction
    CREATE_AUCTION_START_PRICE("create_auction_start_price", "<yellow>Starting Price: <gold>{price}"),
    CREATE_AUCTION_BIN_PRICE("create_auction_bin_price", "<yellow>BIN Price: <gold>{price}"),
    CREATE_AUCTION_DURATION("create_auction_duration", "<yellow>Duration: <gold>{hours}h"),
    CREATE_AUCTION_INCREMENT("create_auction_increment", "<yellow>Increment: <gold>{price}"),
    CREATE_AUCTION_FEE("create_auction_fee", "<gray>Listing Fee: <gold>{fee}"),
    CREATE_AUCTION_CONFIRM("create_auction_confirm", "<green>Click to create auction"),
    
    // Create order
    CREATE_ORDER_MATERIAL("create_order_material", "<yellow>Item: <gold>{item}"),
    CREATE_ORDER_QUANTITY("create_order_quantity", "<yellow>Quantity: <gold>{quantity}"),
    CREATE_ORDER_PRICE("create_order_price", "<yellow>Price per unit: <gold>{price}"),
    CREATE_ORDER_TOTAL("create_order_total", "<yellow>Total: <gold>{total}"),
    CREATE_ORDER_DURATION("create_order_duration", "<yellow>Duration: <gold>{hours}h"),
    CREATE_ORDER_FEE("create_order_fee", "<gray>Listing Fee: <gold>{fee}"),
    CREATE_ORDER_ALLOW_PARTIAL("create_order_allow_partial", "<gray>Allow partial fills: {status}"),
    CREATE_ORDER_CONFIRM("create_order_confirm", "<green>Click to create order"),
    
    // Confirmation
    CONFIRM_TITLE("confirm_title", "Confirm Action"),
    CONFIRM_YES("confirm_yes", "<green>✔ Confirm"),
    CONFIRM_NO("confirm_no", "<red>✘ Cancel"),
    CONFIRM_PURCHASE("confirm_purchase", "<yellow>Are you sure you want to purchase?"),
    CONFIRM_PRICE("confirm_price", "<gray>Price: <gold>{price}"),
    
    // Status
    STATUS_ACTIVE("status_active", "<green>Active"),
    STATUS_SOLD("status_sold", "<red>Sold"),
    STATUS_EXPIRED("status_expired", "<gray>Expired"),
    STATUS_CANCELLED("status_cancelled", "<gray>Cancelled"),
    STATUS_PENDING("status_pending", "<yellow>Pending"),
    STATUS_FILLED("status_filled", "<green>Filled"),
    
    // Empty states
    EMPTY_AUCTIONS("empty_auctions", "<gray>No auctions found."),
    EMPTY_ORDERS("empty_orders", "<gray>No orders found."),
    EMPTY_EXPIRED("empty_expired", "<gray>No claimable items."),
    EMPTY_MY_AUCTIONS("empty_my_auctions", "<gray>You have no active auctions."),
    EMPTY_MY_ORDERS("empty_my_orders", "<gray>You have no active orders."),
    
    // Info
    INFO_CLICK_TO_MODIFY("info_click_to_modify", "<gray>Click numbers to modify"),
    INFO_SHIFT_CLICK_TO_EDIT("info_shift_click_to_edit", "<gray>Shift+Click to type value"),
    
    // Currency
    CURRENCY_FORMAT("currency_format", "<symbol>{amount}"),
    CURRENCY_COMPACT("currency_compact", "{amount}"),

    // Bulk Listing
    BULK_LISTING_TITLE("bulk_listing_title", "Bulk Listing"),
    BULK_LISTING_QUANTITY("bulk_listing_quantity", "<yellow>Quantity: <gold>{quantity}"),
    BULK_LISTING_STACKS("bulk_listing_stacks", "<yellow>Stacks: <gold>{stacks}"),
    BULK_LISTING_TOTAL_ITEMS("bulk_listing_total_items", "<yellow>Total Items: <gold>{total}"),
    BULK_LISTING_FEE_PREVIEW("bulk_listing_fee_preview", "<gray>Total Fees: <gold>{fee}"),
    BULK_LISTING_CONFIRM("bulk_listing_confirm", "<green>Click to create {count} auctions"),
    BULK_LISTING_WARNING("bulk_listing_warning", "<red>Warning: Creating {count} auctions!"),

    // Bulk Buying Cart
    BULK_BUY_CART_TITLE("bulk_buy_cart_title", "Bulk Purchase Cart"),
    BULK_BUY_CART_TOTAL("bulk_buy_cart_total", "<yellow>Total Cost: <gold>{total}"),
    BULK_BUY_CART_SELLER("bulk_buy_cart_seller", "<gray>Seller: <white>{seller}"),
    BULK_BUY_CART_ITEMS("bulk_buy_cart_items", "<gray>Items: <white>{count}"),
    BULK_BUY_CART_CONFIRM("bulk_buy_cart_confirm", "<green>Click to confirm purchase"),
    BULK_BUY_CART_ADD("bulk_buy_cart_add", "<green>Add to Cart"),
    BULK_BUY_CART_REMOVE("bulk_buy_cart_remove", "<red>Remove from Cart"),
    BULK_BUY_CART_EMPTY("bulk_buy_cart_empty", "<gray>Your cart is empty"),
    BULK_BUY_CART_INSUFFICIENT_FUNDS("bulk_buy_cart_insufficient_funds", "<red>Insufficient funds!"),

    // Transaction History
    TRANSACTION_HISTORY_TITLE("transaction_history_title", "Transaction History"),
    TRANSACTION_HISTORY_FILTER_ALL("transaction_history_filter_all", "All Transactions"),
    TRANSACTION_HISTORY_FILTER_PURCHASES("transaction_history_filter_purchases", "Purchases"),
    TRANSACTION_HISTORY_FILTER_SALES("transaction_history_filter_sales", "Sales"),
    TRANSACTION_HISTORY_FILTER_FEES("transaction_history_filter_fees", "Fees"),
    TRANSACTION_HISTORY_FILTER_REFUNDS("transaction_history_filter_refunds", "Refunds"),
    TRANSACTION_HISTORY_DATE_RANGE("transaction_history_date_range", "<gray>Date Range: <white>{range}"),
    TRANSACTION_HISTORY_DETAILS("transaction_history_details", "<gray>Click to view details"),
    TRANSACTION_HISTORY_EXPORT("transaction_history_export", "<yellow>Export to Book"),

    // Transaction Details
    TRANSACTION_DETAILS_TITLE("transaction_details_title", "Transaction Details"),
    TRANSACTION_DETAILS_ID("transaction_details_id", "<gray>Transaction ID: <white>{id}"),
    TRANSACTION_DETAILS_TYPE("transaction_details_type", "<gray>Type: <white>{type}"),
    TRANSACTION_DETAILS_DATE("transaction_details_date", "<gray>Date: <white>{date}"),
    TRANSACTION_DETAILS_PARTIES("transaction_details_parties", "<gray>Parties: <white>{from} → {to}"),
    TRANSACTION_DETAILS_AMOUNT("transaction_details_amount", "<gray>Amount: <gold>{amount}"),
    TRANSACTION_DETAILS_FEE("transaction_details_fee", "<gray>Fee: <gold>{fee}"),
    TRANSACTION_DETAILS_ITEM("transaction_details_item", "<gray>Item: <white>{item}"),
    TRANSACTION_DETAILS_REFERENCE("transaction_details_reference", "<gray>Reference: <white>{ref}"),

    // Admin Dashboard
    ADMIN_DASHBOARD_TITLE("admin_dashboard_title", "AuctionHouse Admin Dashboard"),
    ADMIN_VIEW_PLAYER("admin_view_player", "<yellow>View Player Auctions"),
    ADMIN_SEARCH_AUCTIONS("admin_search_auctions", "<yellow>Search Auctions"),
    ADMIN_ACTIVE_AUCTIONS("admin_active_auctions", "<yellow>Active Auctions"),
    ADMIN_BLACKLIST("admin_blacklist", "<red>Blacklist Management"),
    ADMIN_BANNED_PLAYERS("admin_banned_players", "<red>Banned Players"),
    ADMIN_STATISTICS("admin_statistics", "<green>Statistics"),
    ADMIN_PURGE_DATA("admin_purge_data", "<red>Purge Data"),
    ADMIN_BACKUP_DATA("admin_backup_data", "<green>Backup Data"),
    ADMIN_RELOAD_CONFIG("admin_reload_config", "<yellow>Reload Config"),
    ADMIN_ACTIVITY_LOG("admin_activity_log", "<gray>Recent Activity Log"),
    ADMIN_PENDING_REPORTS("admin_pending_reports", "<red>Pending Reports: {count}"),
    ADMIN_PLAYER_STATS("admin_player_stats", "<gray>Player Statistics"),
    ADMIN_FORCE_SELL("admin_force_sell", "<red>Force Sell Auction"),
    ADMIN_REFUND_PLAYER("admin_refund_player", "<green>Refund Player");
}