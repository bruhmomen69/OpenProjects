package bruh.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

/**
 * Message keys for Auction-related messages.
 */
enum class AuctionMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // General errors
    PLAYER_ONLY("player_only", "<red>This command can only be run by a player."),
    NO_PERMISSION("no_permission", "<red>You don't have permission to do that."),
    CONFIG_RELOADED("config_reloaded", "<green>Configuration reloaded."),
    
    // Auction creation
    AUCTION_CREATED("auction_created", "<green>Your auction has been created!"),
    AUCTION_CREATED_FEE("auction_created_fee", "<yellow>Listing fee: <gold><fee></gold> charged."),
    AUCTION_INVALID_ITEM("auction_invalid_item", "<red>You cannot auction this item."),
    AUCTION_BLACKLISTED("auction_blacklisted", "<red>This item type is blacklisted."),
    AUCTION_PRICE_TOO_LOW("auction_price_too_low", "<red>Price must be at least <gold><min></gold>."),
    AUCTION_PRICE_TOO_HIGH("auction_price_too_high", "<red>Price cannot exceed <gold><max></gold>."),
    AUCTION_MAX_REACHED("auction_max_reached", "<red>You can only have <max> active auctions."),
    
    // Bidding
    BID_PLACED("bid_placed", "<green>Bid placed! You are now the highest bidder."),
    BID_TOO_LOW("bid_too_low", "<red>Your bid must be at least <gold><min></gold>."),
    BID_OUTBID("bid_outbid", "<red>You have been outbid on <gold><item></gold>! New bid: <gold><amount></gold>."),
    BID_NO_BALANCE("bid_no_balance", "<red>You don't have enough money for this bid."),
    BID_CANNOT_ON_BIN("bid_cannot_on_bin", "<red>This is a BIN-only auction. Use /ah buy."),
    BID_NOW_HIGHEST("bid_now_highest", "<green>You are now the highest bidder on <gold><item></gold> with a bid of <gold><amount></gold>!"),
    
    // BIN
    BIN_PURCHASED("bin_purchased", "<green>Purchased <gold><item></gold> for <gold><price></gold>!"),
    BIN_ALREADY_SOLD("bin_already_sold", "<red>This item has already been sold."),
    BIN_NO_BALANCE("bin_no_balance", "<red>You don't have enough money to buy this item."),
    
    // Auction end
    AUCTION_SOLD("auction_sold", "<green>Your <gold><item></gold> sold for <gold><price></gold>!"),
    AUCTION_WON("auction_won", "<green>You won <gold><item></gold> for <gold><price></gold>!"),
    AUCTION_EXPIRED("auction_expired", "<yellow>Your auction for <gold><item></gold> has expired."),
    AUCTION_CANCELLED("auction_cancelled", "<yellow>Your auction has been cancelled."),
    AUCTION_NOT_FOUND("auction_not_found", "<red>Auction not found."),
    AUCTION_NOT_OWNER("auction_not_owner", "<red>You don't own this auction."),
    AUCTION_ALREADY_ENDED("auction_already_ended", "<red>This auction has already ended."),
    
    // Expired items
    EXPIRED_RETRIEVED("expired_retrieved", "<green>Retrieved <gold><item></gold>."),
    EXPIRED_INVENTORY_FULL("expired_inventory_full", "<red>Your inventory is full!"),
    EXPIRED_NONE("expired_none", "<gray>You have no expired items to retrieve."),

    // Login notifications
    BID_OUTBID_LOGIN("bid_outbid_login", "<yellow>Welcome back! You were outbid on <gold><count></gold> auction(s) while you were away."),
    AUCTION_SOLD_LOGIN("auction_sold_login", "<yellow>Welcome back! <gold><count></gold> of your auction(s) sold while you were away."),

    // Admin
    ADMIN_PURGED("admin_purged", "<green>Purged <count> old records."),
    ADMIN_TOGGLE_ON("admin_toggle_on", "<green>Auction House enabled."),
    ADMIN_TOGGLE_OFF("admin_toggle_off", "<red>Auction House disabled."),
    ADMIN_GIVEN("admin_given", "<green>Gave auction item to <player>."),
    ADMIN_REFUNDED("admin_refunded", "<green>Refunded auction to <player>."),

    // Bulk Operations
    BULK_LISTING_CREATED("bulk_listing_created", "<green>Created <count> auctions. Total fees: <fee>"),
    BULK_LISTING_PARTIAL("bulk_listing_partial", "<yellow>Created <success>/<total> auctions. <failed> failed."),
    BULK_LISTING_MAX_REACHED("bulk_listing_max_reached", "<red>Cannot create more than <max> auctions at once."),
    BULK_LISTING_NO_ITEMS("bulk_listing_no_items", "<red>You don't have enough items for bulk listing."),
    BULK_BUY_PURCHASED("bulk_buy_purchased", "<green>Purchased <count> items for <total>."),
    BULK_BUY_PARTIAL("bulk_buy_partial", "<yellow>Purchased <success>/<total> items. <failed> failed."),
    BULK_BUY_MAX_REACHED("bulk_buy_max_reached", "<red>Cannot purchase more than <max> items at once."),
    BULK_BUY_CART_ADDED("bulk_buy_cart_added", "<green>Added item to bulk buy cart."),
    BULK_BUY_CART_REMOVED("bulk_buy_cart_removed", "<red>Removed item from cart."),

    // Transaction History
    TRANSACTION_HISTORY_EXPORTED("transaction_history_exported", "<green>Exported <count> transactions to book."),
    TRANSACTION_HISTORY_NONE("transaction_history_none", "<gray>No transactions found."),

    // Admin Commands
    ADMIN_CANCELLED_REASON("admin_cancelled_reason", "<red>Your auction was cancelled by an admin. Reason: <reason>"),
    ADMIN_DELETED("admin_deleted", "<red>Auction deleted without refund. Reason: <reason>"),
    ADMIN_DELETED_NOTIFICATION("admin_deleted_notification", "<red>Your auction was deleted by an admin.<reason>"),
    ADMIN_REFUNDED_NOTIFICATION("admin_refunded_notification", "<green>You received a refund of <amount><reason>"),
    ADMIN_REFUNDED_SUCCESS("admin_refunded_success", "<green>Refunded <amount> to <player>."),
    ADMIN_STATS_PLAYER("admin_stats_player", "<green>=== Statistics for <player> ===</green>\n<gray>Total Auctions Created: <white><total></white>\n<gray>Active Auctions: <white><active></white>\n<gray>Sold Auctions: <white><sold></white>"),
    ADMIN_STATS_GLOBAL("admin_stats_global", "<green>=== AuctionHouse Statistics ===</green>\n<gray>Active Auctions: <white><auctions></white>\n<gray>Active Orders: <white><orders></white>"),
    ADMIN_VIEW_NO_AUCTIONS("admin_view_no_auctions", "<gray><player> has no <filter> auctions."),
    ADMIN_VIEW_AUCTIONS_LIST("admin_view_auctions_list", "<green>=== <player>'s Auctions (<count>) ===</green>\n<list><more>"),
    ADMIN_FORCE_SOLD("admin_force_sold", "<green>Auction forced sold to <player>."),
    ADMIN_BANNED("admin_banned", "<red>Player <player> has been banned from the auction house. Reason: <reason>"),
    ADMIN_UNBANNED("admin_unbanned", "<green>Player <player> has been unbanned."),
    ADMIN_BLACKLIST_ADDED("admin_blacklist_added", "<green>Added <material> to blacklist."),
    ADMIN_BLACKLIST_REMOVED("admin_blacklist_removed", "<green>Removed <material> from blacklist."),
    ADMIN_BLACKLIST_ALREADY_EXISTS("admin_blacklist_already_exists", "<yellow><material> is already blacklisted."),
    ADMIN_BLACKLIST_NOT_FOUND("admin_blacklist_not_found", "<yellow><material> is not blacklisted."),
    ADMIN_BLACKLIST_INVALID("admin_blacklist_invalid", "<red>Invalid material: <material>."),
    ADMIN_REFUND_SENT("admin_refund_sent", "<green>You received a refund of <amount><reason>"),
    ADMIN_STATS_TITLE("admin_stats_title", "<green>=== AuctionHouse Statistics ==="),
    ADMIN_STATS_ACTIVE_AUCTIONS("admin_stats_active_auctions", "<gray>Active Auctions: <white><count>"),
    ADMIN_STATS_ACTIVE_ORDERS("admin_stats_active_orders", "<gray>Active Orders: <white><count>"),
    ADMIN_STATS_MONEY_CIRCULATION("admin_stats_money_circulation", "<gray>Money in Circulation: <gold><amount>"),
    ADMIN_STATUS("admin_status", "<green>AuctionHouse Status:</green>\n<gray>Enabled: <white><enabled></white>\n<gray>Version: <white><version></white>"),
    ADMIN_PLAYER_NOT_FOUND("admin_player_not_found", "<red>Player not found: <player>."),
    ADMIN_PLAYER_BANNED("admin_player_banned", "<red>This player is banned from the auction house."),
    ADMIN_BAN_NOTIFICATION("admin_ban_notification", "<red>Reason: <reason>"),
    ADMIN_BAN_SUCCESS("admin_ban_success", "<green>Banned <player> from auction house"),
    ADMIN_BAN_AUCTIONS_WILL_BE_CANCELLED("admin_ban_auctions_will_be_cancelled", "<yellow>Their auctions will be cancelled"),
    ADMIN_BAN_ALREADY_BANNED("admin_ban_already_banned", "<yellow><player> is already banned"),
    ADMIN_UNBAN_SUCCESS("admin_unban_success", "<green>Unbanned <player> from auction house"),
    ADMIN_UNBAN_NOT_BANNED("admin_unban_not_banned", "<yellow><player> is not banned"),
    ADMIN_BAN_PLAYER_NOTIFICATION("admin_ban_player_notification", "<red>You have been banned from the auction house<duration>.</red>"),
    ADMIN_BAN_AUCTIONS_CANCELLED("admin_ban_auctions_cancelled", "<yellow>Cancelled <count> active auctions."),
    ADMIN_BANNED_LIST_TITLE("admin_banned_list_title", "<green>=== Banned Players ===</green>"),
    ADMIN_BANNED_LIST_EMPTY("admin_banned_list_empty", "<gray>No banned players (feature placeholder)</gray>"),
    ADMIN_BLACKLIST_LIST_TITLE("admin_blacklist_list_title", "<green>=== Blacklisted Materials (<count>) ===</green>"),
    ADMIN_BLACKLIST_LIST_ITEM("admin_blacklist_list_item", "<gray>- <material>"),
    ADMIN_BLACKLIST_LIST_MORE("admin_blacklist_list_more", "<gray>...and <count> more"),

    // Service-level messages
    MUST_HOLD_ITEM("must_hold_item", "<red>You must hold an item to sell!"),
    BIN_PRICE_TOO_LOW("bin_price_too_low", "<red>BIN price must be at least <min> (<multiplier>x start price)."),
    BIN_PRICE_MUST_BE_GREATER("bin_price_must_be_greater", "<red>BIN price must be greater than start price!"),
    INSUFFICIENT_FUNDS_LISTING("insufficient_funds_listing", "<red>You need <amount> to list this auction."),
    CANNOT_BID_OWN_AUCTION("cannot_bid_own_auction", "<red>You cannot bid on your own auction."),
    CANNOT_BUY_OWN_AUCTION("cannot_buy_own_auction", "<red>You cannot buy your own auction."),
    NO_BIN_PRICE("no_bin_price", "<red>This auction does not have a buy-it-now price."),
    CANNOT_EDIT_ENDED("cannot_edit_ended", "<red>Cannot edit prices on an ended auction."),
    CANNOT_EDIT_BID_PLACED("cannot_edit_bid_placed", "<red>Cannot edit prices after bids have been placed."),
    INVENTORY_FULL_STORED("inventory_full_stored", "<red>Your inventory was full. <count> item(s) have been stored in your expired items."),
    CONFIRM_EXPENSIVE_AUCTION("confirm_expensive_auction", "<yellow>⚠ Confirm expensive auction: Type 'confirm' in chat within 10 seconds"),
    NO_BID_HISTORY("no_bid_history", "<red>No bid history available."),
    BID_WITHDRAW_FAILED("bid_withdraw_failed", "<red>Failed to withdraw bid."),
    INSUFFICIENT_FUNDS_EXTENSION("insufficient_funds_extension", "<red>You don't have enough money for the extension fee."),
    MAX_EXTENSION_REACHED("max_extension_reached", "<red>Maximum manual extension limit reached (<max>)."),
    BIN_PRICE_REMOVED("bin_price_removed", "<green>BIN price removed."),
    PRICES_UPDATED("prices_updated", "<green>Prices updated successfully!"),
    BULK_LISTING_DISABLED("bulk_listing_disabled", "<red>Bulk listing is currently disabled."),
    BULK_LISTING_CONFIRM("bulk_listing_confirm", "<yellow>Creating <count> auctions. Confirm by clicking again."),
    TRANSACTION_HISTORY_DISABLED("transaction_history_disabled", "<red>Transaction history is currently disabled."),
    DATE_RANGE_FILTERING_SOON("date_range_filtering_soon", "<yellow>Date range filtering coming soon!"),
    SEARCH_FEATURE_INTEGRATED("search_feature_integrated", "<yellow>Search feature integrated with main menu"),
    SEARCH_APPLYING("search_applying", "<green>Applying search with <count> filters..."),
    FEATURE_COMING_SOON("feature_coming_soon", "<yellow><feature> - feature coming soon"),
    NO_CLAIMABLE_ITEMS("no_claimable_items", "<gray>You have no claimable items to retrieve."),
    INVENTORY_FULL("inventory_full", "<red>Your inventory is full! Clear some space and try again."),
    ITEM_RETRIEVED("item_retrieved", "<green>Item retrieved successfully!"),
    PARTIAL_RETRIEVAL("partial_retrieval", "<yellow>Partial retrieval! Some items couldn't fit and remain in expired items."),
    PARTIAL_RETRIEVAL_COUNT("partial_retrieval_count", "<yellow>Partial retrieval! Retrieved <available>/<total> items. The rest remain in expired items."),
    ITEMS_MAY_HAVE_MOVED("items_may_have_moved", "<red>Items may have been moved or dropped since opening this menu."),
    INSUFFICIENT_FUNDS_BULK_LISTING("insufficient_funds_bulk_listing", "<red>You need <amount> to list <quantity> auctions."),
    BULK_LISTING_FAILED("bulk_listing_failed", "<red>Failed to create any auctions.");
}