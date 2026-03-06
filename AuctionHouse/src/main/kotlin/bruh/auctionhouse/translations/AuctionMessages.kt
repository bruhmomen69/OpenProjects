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
    AUCTION_CREATED_FEE("auction_created_fee", "<yellow>Listing fee: <gold>{fee}</gold> charged."),
    AUCTION_INVALID_ITEM("auction_invalid_item", "<red>You cannot auction this item."),
    AUCTION_BLACKLISTED("auction_blacklisted", "<red>This item type is blacklisted."),
    AUCTION_PRICE_TOO_LOW("auction_price_too_low", "<red>Price must be at least <gold>{min}</gold>."),
    AUCTION_PRICE_TOO_HIGH("auction_price_too_high", "<red>Price cannot exceed <gold>{max}</gold>."),
    AUCTION_MAX_REACHED("auction_max_reached", "<red>You can only have {max} active auctions."),
    
    // Bidding
    BID_PLACED("bid_placed", "<green>Bid placed! You are now the highest bidder."),
    BID_TOO_LOW("bid_too_low", "<red>Your bid must be at least <gold>{min}</gold>."),
    BID_OUTBID("bid_outbid", "<red>You have been outbid on <gold>{item}</gold>! New bid: <gold>{amount}</gold>."),
    BID_NO_BALANCE("bid_no_balance", "<red>You don't have enough money for this bid."),
    BID_CANNOT_ON_BIN("bid_cannot_on_bin", "<red>This is a BIN-only auction. Use /ah buy."),
    
    // BIN
    BIN_PURCHASED("bin_purchased", "<green>Purchased <gold>{item}</gold> for <gold>{price}</gold>!"),
    BIN_ALREADY_SOLD("bin_already_sold", "<red>This item has already been sold."),
    BIN_NO_BALANCE("bin_no_balance", "<red>You don't have enough money to buy this item."),
    
    // Auction end
    AUCTION_SOLD("auction_sold", "<green>Your <gold>{item}</gold> sold for <gold>{price}</gold>!"),
    AUCTION_WON("auction_won", "<green>You won <gold>{item}</gold> for <gold>{price}</gold>!"),
    AUCTION_EXPIRED("auction_expired", "<yellow>Your auction for <gold>{item}</gold> has expired."),
    AUCTION_CANCELLED("auction_cancelled", "<yellow>Your auction has been cancelled."),
    AUCTION_NOT_FOUND("auction_not_found", "<red>Auction not found."),
    AUCTION_NOT_OWNER("auction_not_owner", "<red>You don't own this auction."),
    AUCTION_ALREADY_ENDED("auction_already_ended", "<red>This auction has already ended."),
    
    // Expired items
    EXPIRED_RETRIEVED("expired_retrieved", "<green>Retrieved <gold>{item}</gold>."),
    EXPIRED_INVENTORY_FULL("expired_inventory_full", "<red>Your inventory is full!"),
    EXPIRED_NONE("expired_none", "<gray>You have no expired items to retrieve."),

    // Login notifications
    BID_OUTBID_LOGIN("bid_outbid_login", "<yellow>Welcome back! You were outbid on <gold>{count}</gold> auction(s) while you were away."),
    AUCTION_SOLD_LOGIN("auction_sold_login", "<yellow>Welcome back! <gold>{count}</gold> of your auction(s) sold while you were away."),

    // Admin
    ADMIN_PURGED("admin_purged", "<green>Purged {count} old records."),
    ADMIN_TOGGLE_ON("admin_toggle_on", "<green>Auction House enabled."),
    ADMIN_TOGGLE_OFF("admin_toggle_off", "<red>Auction House disabled."),
    ADMIN_GIVEN("admin_given", "<green>Gave auction item to {player}."),
    ADMIN_REFUNDED("admin_refunded", "<green>Refunded auction to {player}."),

    // Bulk Operations
    BULK_LISTING_CREATED("bulk_listing_created", "<green>Created {count} auctions. Total fees: {fee}"),
    BULK_LISTING_PARTIAL("bulk_listing_partial", "<yellow>Created {success}/{total} auctions. {failed} failed."),
    BULK_LISTING_MAX_REACHED("bulk_listing_max_reached", "<red>Cannot create more than {max} auctions at once."),
    BULK_LISTING_NO_ITEMS("bulk_listing_no_items", "<red>You don't have enough items for bulk listing."),
    BULK_BUY_PURCHASED("bulk_buy_purchased", "<green>Purchased {count} items for {total}."),
    BULK_BUY_PARTIAL("bulk_buy_partial", "<yellow>Purchased {success}/{total} items. {failed} failed."),
    BULK_BUY_MAX_REACHED("bulk_buy_max_reached", "<red>Cannot purchase more than {max} items at once."),
    BULK_BUY_CART_ADDED("bulk_buy_cart_added", "<green>Added item to bulk buy cart."),
    BULK_BUY_CART_REMOVED("bulk_buy_cart_removed", "<red>Removed item from cart."),

    // Transaction History
    TRANSACTION_HISTORY_EXPORTED("transaction_history_exported", "<green>Exported {count} transactions to book."),
    TRANSACTION_HISTORY_NONE("transaction_history_none", "<gray>No transactions found."),

    // Admin Commands
    ADMIN_CANCELLED_REASON("admin_cancelled_reason", "<red>Your auction was cancelled by an admin. Reason: {reason}"),
    ADMIN_DELETED("admin_deleted", "<red>Auction deleted without refund. Reason: {reason}"),
    ADMIN_FORCE_SOLD("admin_force_sold", "<green>Auction forced sold to {player}."),
    ADMIN_BANNED("admin_banned", "<red>Player {player} has been banned from the auction house. Reason: {reason}"),
    ADMIN_UNBANNED("admin_unbanned", "<green>Player {player} has been unbanned."),
    ADMIN_BLACKLIST_ADDED("admin_blacklist_added", "<green>Added {material} to blacklist."),
    ADMIN_BLACKLIST_REMOVED("admin_blacklist_removed", "<green>Removed {material} from blacklist."),
    ADMIN_STATS_TITLE("admin_stats_title", "<green>=== AuctionHouse Statistics ==="),
    ADMIN_STATS_ACTIVE_AUCTIONS("admin_stats_active_auctions", "<gray>Active Auctions: <white>{count}"),
    ADMIN_STATS_ACTIVE_ORDERS("admin_stats_active_orders", "<gray>Active Orders: <white>{count}"),
    ADMIN_STATS_MONEY_CIRCULATION("admin_stats_money_circulation", "<gray>Money in Circulation: <gold>{amount}"),
    ADMIN_PLAYER_NOT_FOUND("admin_player_not_found", "<red>Player not found."),
    ADMIN_PLAYER_BANNED("admin_player_banned", "<red>This player is banned from the auction house.");
}