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
    BUTTON_CREATE_AUCTION_DESC("button_create_auction_desc", "<gray>List an item for sale"),
    BUTTON_CREATE_ORDER("button_create_order", "<green>Create Order"),
    BUTTON_CREATE_ORDER_DESC("button_create_order_desc", "<gray>Create a buy order"),
    BUTTON_REFRESH("button_refresh", "<yellow>Refresh"),
    BUTTON_REFRESH_DESC("button_refresh_desc", "<gray>Reload the current view"),
    BUTTON_SORT("button_sort", "<yellow>Sort"),
    BUTTON_SORT_DESC("button_sort_desc", "<gray>Change sorting order"),
    BUTTON_FILTER("button_filter", "<yellow>Filter"),
    BUTTON_FILTER_DESC("button_filter_desc", "<gray>Filter visible items"),
    BUTTON_MY_AUCTIONS("button_my_auctions", "<aqua>My Auctions"),
    BUTTON_MY_AUCTIONS_DESC("button_my_auctions_desc", "<gray>View your active auctions"),
    BUTTON_MY_ORDERS("button_my_orders", "<aqua>My Orders"),
    BUTTON_MY_ORDERS_DESC("button_my_orders_desc", "<gray>View your active orders"),
    BUTTON_EXPIRED("button_expired", "<red>Claimable Items"),
    BUTTON_EXPIRED_DESC("button_expired_desc", "<gray>View items ready to claim"),
    BUTTON_BUY_ORDERS("button_buy_orders", "<green>Buy Orders"),
    BUTTON_BUY_ORDERS_DESC("button_buy_orders_desc", "<gray>Filter to buy orders"),
    BUTTON_SELL_ORDERS("button_sell_orders", "<green>Sell Orders"),
    BUTTON_SELL_ORDERS_DESC("button_sell_orders_desc", "<gray>Filter to sell orders"),
    BUTTON_SEARCH("button_search", "<yellow>Search"),
    BUTTON_SEARCH_DESC("button_search_desc", "<gray>Search and filter results"),
    BUTTON_AUCTION_HOUSE("button_auction_house", "<gold>Auction House"),
    BUTTON_AUCTION_HOUSE_DESC("button_auction_house_desc", "<gray>Browse all auctions"),
    BUTTON_BACK_DESC("button_back_desc", "<gray>Return to previous menu"),
    BUTTON_CLOSE_DESC("button_close_desc", "<gray>Close this menu"),
    BUTTON_WATCHLIST("button_watchlist", "<yellow>My Watchlist"),
    BUTTON_WATCHLIST_DESC("button_watchlist_desc", "<gray>View your watched auctions"),
    BUTTON_QUICK_SELL("button_quick_sell", "<green>Quick Sell"),
    BUTTON_QUICK_SELL_DESC("button_quick_sell_desc", "<gray>Sell items to buy orders"),
    BUTTON_TRANSACTION_HISTORY("button_transaction_history", "<yellow>Transaction History"),
    BUTTON_TRANSACTION_HISTORY_DESC("button_transaction_history_desc", "<gray>View your transaction history"),
    BUTTON_ORDERS("button_orders", "<light_purple>Orders"),
    BUTTON_ORDERS_DESC("button_orders_desc", "<gray>Browse buy and sell orders"),
    BUTTON_CREATE_SELL_ORDER("button_create_sell_order", "<yellow>Create Sell Order"),
    BUTTON_CREATE_SELL_ORDER_DESC("button_create_sell_order_desc", "<gray>Create a sell order"),
    
    // Auction item display
    AUCTION_ITEM_NAME("auction_item_name", "<gold><item>"),
    AUCTION_ITEM_PRICE("auction_item_price", "<yellow>Price: <gold><price>"),
    AUCTION_ITEM_BIN("auction_item_bin", "<green>BIN: <gold><price>"),
    AUCTION_ITEM_BID("auction_item_bid", "<yellow>Current Bid: <gold><price>"),
    AUCTION_ITEM_BIDS("auction_item_bids", "<gray>Bids: <white><count>"),
    AUCTION_ITEM_TIME_LEFT("auction_item_time_left", "<gray>Time Left: <white><time>"),
    AUCTION_ITEM_SELLER("auction_item_seller", "<gray>Seller: <white><seller>"),
    AUCTION_ITEM_ANONYMOUS("auction_item_anonymous", "<gray>Seller: <i>Anonymous</i>"),
    AUCTION_ITEM_CLICK_BID("auction_item_click_bid", "<yellow>Click to place bid"),
    AUCTION_ITEM_CLICK_BUY("auction_item_click_buy", "<green>Click to buy now"),
    AUCTION_ITEM_CLICK_VIEW("auction_item_click_view", "<gray>Click to view details"),
    
    // Order item display
    ORDER_ITEM_NAME("order_item_name", "<gold><item>"),
    ORDER_ITEM_QUANTITY("order_item_quantity", "<yellow>Quantity: <gold><current>/<total>"),
    ORDER_ITEM_PRICE("order_item_price", "<yellow>Price per unit: <gold><price>"),
    ORDER_ITEM_TOTAL("order_item_total", "<yellow>Total value: <gold><total>"),
    ORDER_ITEM_TYPE("order_item_type", "<gray>Type: <type>"),
    ORDER_ITEM_REQUESTER("order_item_requester", "<gray>Requester: <white><player>"),
    ORDER_ITEM_TIME_LEFT("order_item_time_left", "<gray>Time Left: <white><time>"),
    ORDER_ITEM_PARTIAL("order_item_partial", "<gray>Partial fills: <green>Allowed"),
    ORDER_ITEM_NO_PARTIAL("order_item_no_partial", "<gray>Partial fills: <red>Not allowed"),
    ORDER_ITEM_CLICK_FILL("order_item_click_fill", "<green>Click to fulfill order"),
    ORDER_ITEM_CLICK_VIEW("order_item_click_view", "<gray>Click to view details"),
    
    // Order types
    ORDER_TYPE_BUY("order_type_buy", "<green>Buy Order"),
    ORDER_TYPE_SELL("order_type_sell", "<red>Sell Order"),
    
    // Create auction
    CREATE_AUCTION_START_PRICE("create_auction_start_price", "<yellow>Starting Price: <gold><price>"),
    CREATE_AUCTION_BIN_PRICE("create_auction_bin_price", "<yellow>BIN Price: <gold><price>"),
    CREATE_AUCTION_DURATION("create_auction_duration", "<yellow>Duration: <gold><hours>h"),
    CREATE_AUCTION_INCREMENT("create_auction_increment", "<yellow>Increment: <gold><price>"),
    CREATE_AUCTION_FEE("create_auction_fee", "<gray>Listing Fee: <gold><fee>"),
    CREATE_AUCTION_CONFIRM("create_auction_confirm", "<green>Click to create auction"),
    
    // Create order
    CREATE_ORDER_MATERIAL("create_order_material", "<yellow>Item: <gold><item>"),
    CREATE_ORDER_QUANTITY("create_order_quantity", "<yellow>Quantity: <gold><quantity>"),
    CREATE_ORDER_PRICE("create_order_price", "<yellow>Price per unit: <gold><price>"),
    CREATE_ORDER_TOTAL("create_order_total", "<yellow>Total: <gold><total>"),
    CREATE_ORDER_DURATION("create_order_duration", "<yellow>Duration: <gold><hours>h"),
    CREATE_ORDER_FEE("create_order_fee", "<gray>Listing Fee: <gold><fee>"),
    CREATE_ORDER_ALLOW_PARTIAL("create_order_allow_partial", "<gray>Allow partial fills: <status>"),
    CREATE_ORDER_CONFIRM("create_order_confirm", "<green>Click to create order"),
    
    // Confirmation
    CONFIRM_TITLE("confirm_title", "Confirm Action"),
    CONFIRM_YES("confirm_yes", "<green>✔ Confirm"),
    CONFIRM_NO("confirm_no", "<red>✘ Cancel"),
    CONFIRM_PURCHASE("confirm_purchase", "<yellow>Are you sure you want to purchase?"),
    CONFIRM_PRICE("confirm_price", "<gray>Price: <gold><price>"),
    
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
    CURRENCY_FORMAT("currency_format", "<symbol><amount>"),
    CURRENCY_COMPACT("currency_compact", "<amount>"),

    // Bulk Listing
    BULK_LISTING_TITLE("bulk_listing_title", "Bulk Listing"),
    BULK_LISTING_QUANTITY("bulk_listing_quantity", "<yellow>Quantity: <gold><quantity>"),
    BULK_LISTING_STACKS("bulk_listing_stacks", "<yellow>Stacks: <gold><stacks>"),
    BULK_LISTING_TOTAL_ITEMS("bulk_listing_total_items", "<yellow>Total Items: <gold><total>"),
    BULK_LISTING_FEE_PREVIEW("bulk_listing_fee_preview", "<gray>Total Fees: <gold><fee>"),
    BULK_LISTING_CONFIRM("bulk_listing_confirm", "<green>Click to create <count> auctions"),
    BULK_LISTING_WARNING("bulk_listing_warning", "<red>Warning: Creating <count> auctions!"),

    // Bulk Buying Cart
    BULK_BUY_CART_TITLE("bulk_buy_cart_title", "Bulk Purchase Cart"),
    BULK_BUY_CART_TOTAL("bulk_buy_cart_total", "<yellow>Total Cost: <gold><total>"),
    BULK_BUY_CART_SELLER("bulk_buy_cart_seller", "<gray>Seller: <white><seller>"),
    BULK_BUY_CART_ITEMS("bulk_buy_cart_items", "<gray>Items: <white><count>"),
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
    TRANSACTION_HISTORY_DATE_RANGE("transaction_history_date_range", "<gray>Date Range: <white><range>"),
    TRANSACTION_HISTORY_DETAILS("transaction_history_details", "<gray>Click to view details"),
    TRANSACTION_HISTORY_EXPORT("transaction_history_export", "<yellow>Export to Book"),

    // Transaction Details
    TRANSACTION_DETAILS_TITLE("transaction_details_title", "Transaction Details"),
    TRANSACTION_DETAILS_ID("transaction_details_id", "<gray>Transaction ID: <white><id>"),
    TRANSACTION_DETAILS_TYPE("transaction_details_type", "<gray>Type: <white><type>"),
    TRANSACTION_DETAILS_DATE("transaction_details_date", "<gray>Date: <white><date>"),
    TRANSACTION_DETAILS_PARTIES("transaction_details_parties", "<gray>Parties: <white><from> → <to>"),
    TRANSACTION_DETAILS_AMOUNT("transaction_details_amount", "<gray>Amount: <gold><amount>"),
    TRANSACTION_DETAILS_FEE("transaction_details_fee", "<gray>Fee: <gold><fee>"),
    TRANSACTION_DETAILS_ITEM("transaction_details_item", "<gray>Item: <white><item>"),
    TRANSACTION_DETAILS_REFERENCE("transaction_details_reference", "<gray>Reference: <white><ref>"),

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
    ADMIN_PENDING_REPORTS("admin_pending_reports", "<red>Pending Reports: <count>"),
    ADMIN_PLAYER_STATS("admin_player_stats", "<gray>Player Statistics"),
    ADMIN_FORCE_SELL("admin_force_sell", "<red>Force Sell Auction"),
    ADMIN_REFUND_PLAYER("admin_refund_player", "<green>Refund Player"),

    // Watchlist
    WATCHLIST_TITLE("watchlist_title", "My Watchlist (<count>)"),
    WATCHLIST_REMOVED("watchlist_removed", "<yellow>Removed from watchlist."),
    WATCHLIST_ADDED("watchlist_added", "<green>Added to watchlist!"),
    WATCHLIST_CLEARED("watchlist_cleared", "<green>Cleared all watched auctions."),
    WATCHLIST_EMPTY("watchlist_empty", "<gray>Your watchlist is empty."),
    WATCHLIST_EMPTY_INSTRUCTIONS("watchlist_empty_instructions", "<gray>Browse auctions and click the"),
    WATCHLIST_EMPTY_INSTRUCTIONS_2("watchlist_empty_instructions_2", "<gray>button to add them here."),
    WATCHLIST_NEW_ACTIVITY("watchlist_new_activity", "⚠ New Activity!"),
    WATCHLIST_CLICK_VIEW("watchlist_click_view", "<gray>Click to view"),
    WATCHLIST_CLICK_REMOVE("watchlist_click_remove", "<red>Click to remove"),
    WATCHLIST_CLEAR_ALL("watchlist_clear_all", "<red>Clear All"),
    WATCHLIST_CLEAR_ALL_LORE("watchlist_clear_all_lore", "<gray>Remove all auctions from watchlist"),
    WATCHLIST_SORT("watchlist_sort", "<yellow>Sort: <sort>"),
    WATCHLIST_SORT_ENDING_SOON("watchlist_sort_ending_soon", "Ending Soon"),
    WATCHLIST_SORT_NEWEST("watchlist_sort_newest", "Newest First"),
    WATCHLIST_SORT_PRICE_LOW("watchlist_sort_price_low", "Price: Low to High"),
    WATCHLIST_SORT_PRICE_HIGH("watchlist_sort_price_high", "Price: High to Low"),

    // Advanced Search
    ADVANCED_SEARCH_TITLE("advanced_search_title", "Advanced Search"),
    SEARCH_FILTER_QUERY("search_filter_query", "<yellow>Search Query"),
    SEARCH_FILTER_QUERY_DESC("search_filter_query_desc", "<gray>Search by item name"),
    SEARCH_FILTER_QUERY_DESC_2("search_filter_query_desc_2", "<gray>Click to set search term"),
    SEARCH_FILTER_QUERY_DESC_3("search_filter_query_desc_3", "<gray>Current: <white><query>"),
    SEARCH_FILTER_SELLER("search_filter_seller", "<yellow>Seller Name"),
    SEARCH_FILTER_SELLER_DESC("search_filter_seller_desc", "<gray>Filter by seller"),
    SEARCH_FILTER_SELLER_DESC_2("search_filter_seller_desc_2", "<gray>Click to filter by player"),
    SEARCH_FILTER_SELLER_DESC_3("search_filter_seller_desc_3", "<gray>Current: <white><seller>"),
    SEARCH_FILTER_MATERIAL("search_filter_material", "<yellow>Material Type"),
    SEARCH_FILTER_MATERIAL_DESC("search_filter_material_desc", "<gray>Filter by material"),
    SEARCH_FILTER_MATERIAL_DESC_2("search_filter_material_desc_2", "<gray>Click to select material"),
    SEARCH_FILTER_MATERIAL_DESC_3("search_filter_material_desc_3", "<gray>Current: <white><material>"),
    SEARCH_FILTER_MIN_PRICE("search_filter_min_price", "<yellow>Minimum Price"),
    SEARCH_FILTER_MIN_PRICE_DESC("search_filter_min_price_desc", "<gray>Set minimum price"),
    SEARCH_FILTER_MIN_PRICE_DESC_2("search_filter_min_price_desc_2", "<gray>Click to set minimum"),
    SEARCH_FILTER_MIN_PRICE_DESC_3("search_filter_min_price_desc_3", "<gray>Current: <white><price>"),
    SEARCH_FILTER_MAX_PRICE("search_filter_max_price", "<yellow>Maximum Price"),
    SEARCH_FILTER_MAX_PRICE_DESC("search_filter_max_price_desc", "<gray>Set maximum price"),
    SEARCH_FILTER_MAX_PRICE_DESC_2("search_filter_max_price_desc_2", "<gray>Click to set maximum"),
    SEARCH_FILTER_MAX_PRICE_DESC_3("search_filter_max_price_desc_3", "<gray>Current: <white><price>"),
    SEARCH_FILTER_PRESET("search_filter_preset", "<yellow>Price Preset: <preset>"),
    SEARCH_FILTER_PRESET_DESC("search_filter_preset_desc", "<gray>Quick price filters"),
    SEARCH_FILTER_PRESET_DESC_2("search_filter_preset_desc_2", "<gray>Click to cycle presets"),
    SEARCH_FILTER_PRESET_ALL("search_filter_preset_all", "All Prices"),
    SEARCH_FILTER_PRESET_UNDER_1K("search_filter_preset_under_1k", "Under 1K"),
    SEARCH_FILTER_PRESET_1K_10K("search_filter_preset_1k_10k", "1K - 10K"),
    SEARCH_FILTER_PRESET_10K_100K("search_filter_preset_10k_100k", "10K - 100K"),
    SEARCH_FILTER_PRESET_OVER_100K("search_filter_preset_over_100k", "100K+"),
    SEARCH_FILTER_TYPE("search_filter_type", "<yellow>Auction Type: <type>"),
    SEARCH_FILTER_TYPE_DESC("search_filter_type_desc", "<gray>Filter by auction type"),
    SEARCH_FILTER_TYPE_DESC_2("search_filter_type_desc_2", "<gray>Click to cycle types"),
    SEARCH_FILTER_TYPE_ALL("search_filter_type_all", "All Types"),
    SEARCH_FILTER_TYPE_AUCTION("search_filter_type_auction", "Auction Only"),
    SEARCH_FILTER_TYPE_BIN("search_filter_type_bin", "BIN Only"),
    SEARCH_FILTER_ENDING("search_filter_ending", "<yellow>Ending Soon: <status>"),
    SEARCH_FILTER_ENDING_DESC("search_filter_ending_desc", "<gray>Show auctions ending soon"),
    SEARCH_FILTER_ENDING_DESC_2("search_filter_ending_desc_2", "<gray>Click to toggle"),
    SEARCH_FILTER_ENDING_ENABLED("search_filter_ending_enabled", "Enabled"),
    SEARCH_FILTER_ENDING_DISABLED("search_filter_ending_disabled", "Disabled"),
    SEARCH_SORT("search_sort", "<yellow>Sort: <sort>"),
    SEARCH_SORT_DESC("search_sort_desc", "<gray>Click to change sort"),
    SEARCH_ACTIVE_FILTERS("search_active_filters", "<green>Active Filters:"),
    SEARCH_ACTIVE_FILTERS_NONE("search_active_filters_none", "<gray>None"),
    SEARCH_CLEAR_ALL("search_clear_all", "<red>Clear All Filters"),
    SEARCH_CLEAR_ALL_DESC("search_clear_all_desc", "<gray>Remove all filters"),
    SEARCH_APPLY("search_apply", "<green>Apply Search"),
    SEARCH_APPLY_DESC("search_apply_desc", "<gray>Apply and search"),
    SEARCH_APPLY_DESC_2("search_apply_desc_2", "<yellow><count> filters active"),

    // Auction Details
    AUCTION_DETAILS_TITLE("auction_details_title", "Auction Details"),
    AUCTION_DETAILS_STATUS("auction_details_status", "<gray>Status: <color><status>"),
    AUCTION_DETAILS_SOLD_FOR("auction_details_sold_for", "<gray>Sold for: <gold><price>"),
    AUCTION_DETAILS_BUYER("auction_details_buyer", "<gray>Buyer: <white><buyer>"),
    AUCTION_DETAILS_SOLD_AT("auction_details_sold_at", "<gray>Sold at: <white><date>"),
    AUCTION_DETAILS_EXPIRED_AT("auction_details_expired_at", "<gray>Expired at: <white><date>"),
    AUCTION_DETAILS_CANCELLED("auction_details_cancelled", "<gray>Cancelled"),
    AUCTION_DETAILS_BIDS("auction_details_bids", "<gray>Bids: <white><count>"),
    AUCTION_DETAILS_VIEWS("auction_details_views", "<gray>Views: <white><count>"),

    // Consolidated Expired Items
    CONSOLIDATED_EXPIRED_TITLE("consolidated_expired_title", "All Claimable Items"),
    CONSOLIDATED_EXPIRED_EMPTY("consolidated_expired_empty", "<gray>You have no claimable items to retrieve."),

    // Expensive Transaction Warning
    EXPENSIVE_TRANSACTION_WARNING("expensive_transaction_warning", "<red>⚠ High Value Transaction"),
    EXPENSIVE_TRANSACTION_THRESHOLD("expensive_transaction_threshold", "<red>Threshold: <threshold>"),
    CONFIRM_EXPENSIVE_AUCTION("confirm_expensive_auction", "<yellow>⚠ Confirm expensive auction: Type 'confirm' in chat within 10 seconds"),
    BULK_LISTING_CONFIRMATION("bulk_listing_confirmation", "<yellow>Creating <count> auctions. Confirm by clicking again."),

    // Action labels
    ACTION_CLICK_TO_VIEW("action_click_to_view", "<green>Click to view"),
    ACTION_CLICK_TO_CREATE("action_click_to_create", "<green>Click to create"),
    ACTION_CLICK_TO_BROWSE("action_click_to_browse", "<green>Click to browse"),
    ACTION_CLICK_TO_CLOSE("action_click_to_close", "<red>Click to close"),
    ACTION_CLICK_TO_OPEN("action_click_to_open", "<green>Click to open"),
    ACTION_CLICK_TO_CYCLE("action_click_to_cycle", "<green>Click to cycle"),
    ACTION_CLICK_TO_SEARCH("action_click_to_search", "<green>Click to search"),
    ACTION_CLICK_TO_GO_BACK("action_click_to_go_back", "<green>Click to go back"),
    ACTION_CLICK_TO_QUICK_SELL("action_click_to_quick_sell", "<green>Click to quick sell"),

    // Filter labels
    FILTER_TYPE_ALL("filter_type_all", "All Types"),
    FILTER_TYPE_AUCTION_ONLY("filter_type_auction_only", "Auction Only"),
    FILTER_TYPE_BIN_ONLY("filter_type_bin_only", "BIN Only"),
    FILTER_TYPE_BOTH("filter_type_both", "Auction + BIN"),
    FILTER_ORDER_TYPE_ALL("filter_order_type_all", "All"),
    FILTER_LABEL("filter_label", "<yellow>Filter: <white><type>"),
    FILTER_CURRENT("filter_current", "<gray>Current: <white><filter>"),
    FILTER_NO_FILTERS("filter_no_filters", "<gray>Current: <white>No filters"),
    FILTER_ACTIVE("filter_active", "<yellow>Active filters:"),
    FILTER_FOR_ADVANCED("filter_for_advanced", "<gray>For advanced filters,"),
    FILTER_USE_SEARCH("filter_use_search", "<gray>use the Search button"),

    // Sort labels
    SORT_DISPLAY_ENDING_SOON("sort_display_ending_soon", "Ending Soon"),
    SORT_DISPLAY_NEWEST("sort_display_newest", "Newest First"),
    SORT_DISPLAY_PRICE_LOW("sort_display_price_low", "Price: Low to High"),
    SORT_DISPLAY_PRICE_HIGH("sort_display_price_high", "Price: High to Low"),
    SORT_DISPLAY_MOST_BIDS("sort_display_most_bids", "Most Bids"),
    SORT_DISPLAY_RECENTLY_UPDATED("sort_display_recently_updated", "Recently Updated"),
    SORT_DISPLAY_MOST_FILLED("sort_display_most_filled", "Most Filled"),
    SORT_CURRENT("sort_current", "<gray>Current: <white><sort>"),

    // Item info labels
    ITEM_ID("item_id", "<gray>ID: <white><id>"),
    ITEM_WATCHING("item_watching", "<gray>Watching: <white><count>"),
    ITEM_LABEL("item_label", "<yellow>Item: <white><item>"),
    ITEM_AMOUNT("item_amount", "<yellow>Amount: <white><amount>"),

    // Status messages
    STATUS_AUCTION_ENDED("status_auction_ended", "<red>⚠ Auction Ended"),
    STATUS_ENDED("status_ended", "<red>Ended"),
    STATUS_YOUR_ORDER("status_your_order", "<yellow><bold>Your Order</bold></yellow>"),
    STATUS_HOLD_ITEM_TO_SELL("status_hold_item_to_sell", "<red>Hold an item to sell!"),
    STATUS_HOLD_ITEM_TO_QUICK_SELL("status_hold_item_to_quick_sell", "<red>Hold an item to quick sell"),

    // Order item info
    ORDER_CLICK_MANAGE("order_click_manage", "<gray>Click to manage or cancel"),
    ORDER_RIGHT_CLICK_WATCHLIST("order_right_click_watchlist", "<yellow>Right-click to add to watchlist"),

    // Page indicator
    PAGE_INDICATOR("page_indicator", "Page <current>/<total>"),

    // Quick Sell
    QUICK_SELL_NO_ITEM("quick_sell_no_item", "<red>Hold an item to use Quick Sell!"),

    // Search prompt
    SEARCH_PROMPT_ORDERS("search_prompt_orders", "Search Orders"),
    ;
}