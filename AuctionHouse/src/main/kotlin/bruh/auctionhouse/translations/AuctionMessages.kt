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
    ADMIN_REFUNDED("admin_refunded", "<green>Refunded auction to {player}.");
}