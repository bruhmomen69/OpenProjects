package bruh.auctionhouse.model

import bruh.auctionhouse.gui.OrderCreateState
import java.time.Duration
import java.util.UUID

/**
 * Comprehensive state data for a player's auction house interactions.
 * This class consolidates all transient UI state that doesn't need to be persisted.
 *
 * @property playerId The UUID of the player this state belongs to
 * @property auctionFilter Current auction filter settings
 * @property auctionPage Current page in the auction house browser
 * @property watchlistSort Current sort preference for watchlist
 * @property transactionFilter Current filter for transaction history
 * @property orderCreateState State for order creation (if in progress)
 * @property adminTargetPlayer For admin menus: the UUID of the player being viewed
 * @property adminTargetPlayerName For admin menus: the name of the player being viewed
 * @property adminAuctionStatusFilter For admin menus: filter for auction status
 */
data class PlayerState(
    val playerId: UUID,
    val auctionFilter: AuctionFilter = AuctionFilter(),
    val auctionPage: Int = 0,
    val watchlistSort: WatchlistSort = WatchlistSort.ENDING_SOON,
    val transactionFilter: TransactionType? = null,
    val orderCreateState: OrderCreateState? = null,
    val adminTargetPlayer: UUID? = null,
    val adminTargetPlayerName: String? = null,
    val adminAuctionStatusFilter: AuctionStatus? = null
) {
    /**
     * Sort options for the watchlist menu.
     */
    enum class WatchlistSort {
        /** Sort by auctions ending soonest first */
        ENDING_SOON,

        /** Sort by lowest price first */
        PRICE_LOW,

        /** Sort by highest price first */
        PRICE_HIGH,

        /** Sort by most recently added */
        RECENTLY_ADDED
    }

    /**
     * Creates a copy with updated auction filter and resets to page 0.
     */
    fun withFilter(filter: AuctionFilter): PlayerState {
        return copy(auctionFilter = filter, auctionPage = 0)
    }

    /**
     * Creates a copy with updated auction page.
     */
    fun withPage(page: Int): PlayerState {
        return copy(auctionPage = page)
    }

    /**
     * Creates a copy with updated watchlist sort preference.
     */
    fun withWatchlistSort(sort: WatchlistSort): PlayerState {
        return copy(watchlistSort = sort)
    }

    /**
     * Creates a copy with updated transaction filter.
     */
    fun withTransactionFilter(filter: TransactionType?): PlayerState {
        return copy(transactionFilter = filter)
    }

    /**
     * Creates a copy with order creation state.
     */
    fun withOrderCreateState(state: OrderCreateState?): PlayerState {
        return copy(orderCreateState = state)
    }

    /**
     * Creates a copy with admin target player info.
     */
    fun withAdminTarget(playerId: UUID?, playerName: String?): PlayerState {
        return copy(adminTargetPlayer = playerId, adminTargetPlayerName = playerName)
    }

    /**
     * Creates a copy with admin auction status filter.
     */
    fun withAdminAuctionStatusFilter(status: AuctionStatus?): PlayerState {
        return copy(adminAuctionStatusFilter = status)
    }

    companion object {
        /**
         * Creates a default state for a player.
         */
        fun createDefault(playerId: UUID): PlayerState {
            return PlayerState(playerId)
        }
    }
}
