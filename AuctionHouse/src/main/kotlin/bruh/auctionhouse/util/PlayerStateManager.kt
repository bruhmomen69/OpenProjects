package bruh.auctionhouse.util

import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.PlayerState
import bruh.auctionhouse.model.TransactionType
import bruh.auctionhouse.gui.OrderCreateState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized manager for all player-related state in the auction house.
 * This provides a single source of truth for transient UI state that doesn't need database persistence.
 *
 * State managed:
 * - Auction browsing filters and pagination
 * - Watchlist sort preferences
 * - Transaction history filters
 * - Order creation state
 * - Admin menu target player selections
 *
 * All state is stored in memory and cleared when players leave or the server shuts down.
 * For persistent data like bans, use the database repository instead.
 */
object PlayerStateManager {
    // Transient in-memory state storage
    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()

    /**
     * Gets the state for a player, creating a default state if none exists.
     *
     * @param playerId The UUID of the player
     * @return The player's current state, or a new default state
     */
    fun getState(playerId: UUID): PlayerState {
        return playerStates.computeIfAbsent(playerId) { PlayerState.createDefault(it) }
    }

    /**
     * Updates the state for a player.
     *
     * @param playerId The UUID of the player
     * @param state The new state to store
     */
    fun setState(playerId: UUID, state: PlayerState) {
        playerStates[playerId] = state
    }

    /**
     * Removes all state for a player.
     * Call this when a player disconnects to free memory.
     *
     * @param playerId The UUID of the player
     */
    fun clearState(playerId: UUID) {
        playerStates.remove(playerId)
    }

    /**
     * Clears all player state.
     * Call this on plugin disable to free memory.
     */
    fun clearAllState() {
        playerStates.clear()
    }

    // Convenience methods for common operations

    /**
     * Gets the auction filter for a player.
     */
    fun getAuctionFilter(playerId: UUID): AuctionFilter {
        return getState(playerId).auctionFilter
    }

    /**
     * Sets the auction filter for a player and resets to page 0.
     */
    fun setAuctionFilter(playerId: UUID, filter: AuctionFilter) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withFilter(filter))
    }

    /**
     * Gets the current auction page for a player.
     */
    fun getAuctionPage(playerId: UUID): Int {
        return getState(playerId).auctionPage
    }

    /**
     * Sets the current auction page for a player.
     */
    fun setAuctionPage(playerId: UUID, page: Int) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withPage(page))
    }

    /**
     * Gets the watchlist sort preference for a player.
     */
    fun getWatchlistSort(playerId: UUID): PlayerState.WatchlistSort {
        return getState(playerId).watchlistSort
    }

    /**
     * Sets the watchlist sort preference for a player.
     */
    fun setWatchlistSort(playerId: UUID, sort: PlayerState.WatchlistSort) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withWatchlistSort(sort))
    }

    /**
     * Gets the transaction filter for a player.
     */
    fun getTransactionFilter(playerId: UUID): TransactionType? {
        return getState(playerId).transactionFilter
    }

    /**
     * Sets the transaction filter for a player.
     */
    fun setTransactionFilter(playerId: UUID, filter: TransactionType?) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withTransactionFilter(filter))
    }

    /**
     * Gets the order creation state for a player.
     */
    fun getOrderCreateState(playerId: UUID): OrderCreateState? {
        return getState(playerId).orderCreateState
    }

    /**
     * Sets the order creation state for a player.
     */
    fun setOrderCreateState(playerId: UUID, state: OrderCreateState?) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withOrderCreateState(state))
    }

    /**
     * Gets the admin target player for a player.
     *
     * @return Pair of UUID and name, or null if not set
     */
    fun getAdminTarget(playerId: UUID): Pair<UUID, String>? {
        val state = getState(playerId)
        return if (state.adminTargetPlayer != null && state.adminTargetPlayerName != null) {
            Pair(state.adminTargetPlayer, state.adminTargetPlayerName)
        } else {
            null
        }
    }

    /**
     * Sets the admin target player for a player.
     */
    fun setAdminTarget(playerId: UUID, targetUuid: UUID, targetName: String) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withAdminTarget(targetUuid, targetName))
    }

    /**
     * Clears the admin target player for a player.
     */
    fun clearAdminTarget(playerId: UUID) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withAdminTarget(null, null))
    }

    /**
     * Gets the admin auction status filter for a player.
     */
    fun getAdminAuctionStatusFilter(playerId: UUID): AuctionStatus? {
        return getState(playerId).adminAuctionStatusFilter
    }

    /**
     * Sets the admin auction status filter for a player.
     */
    fun setAdminAuctionStatusFilter(playerId: UUID, status: AuctionStatus?) {
        val currentState = getState(playerId)
        setState(playerId, currentState.withAdminAuctionStatusFilter(status))
    }

    /**
     * Gets the number of players with stored state.
     */
    fun getStoredStateCount(): Int {
        return playerStates.size
    }

    /**
     * Gets all player IDs with stored state.
     */
    fun getAllStoredPlayerIds(): Set<UUID> {
        return playerStates.keys.toSet()
    }
}
