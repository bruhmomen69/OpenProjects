package bruh.auctionhouse.database

import bruh.auctionhouse.model.WatchlistEntry
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Repository for watchlist CRUD operations and queries.
 */
class WatchlistRepository(private val database: Database) {

    /**
     * Adds an auction to a player's watchlist.
     */
    suspend fun add(playerUuid: UUID, auctionId: UUID): WatchlistEntry? = withContext(Dispatchers.IO) {
        // Check if already watching
        val existing = getByAuction(playerUuid, auctionId)
        if (existing != null) return@withContext null

        val entry = WatchlistEntry(
            playerUuid = playerUuid,
            auctionId = auctionId
        )

        database.execute(
            sql {
                mysql("INSERT INTO watchlist (player_uuid, auction_id, added_at, last_notified_at, has_new_activity) VALUES (?, ?, ?, ?, ?)")
                sqlite("INSERT INTO watchlist (player_uuid, auction_id, added_at, last_notified_at, has_new_activity) VALUES (?, ?, ?, ?, ?)")
            },
            entry.playerUuid.toString(),
            entry.auctionId.toString(),
            entry.addedAt,
            entry.lastNotifiedAt,
            entry.hasNewActivity
        )

        // Get the created entry with ID
        val id = database.querySingle(
            sql("SELECT id FROM watchlist WHERE player_uuid = ? AND auction_id = ?"),
            playerUuid.toString(),
            auctionId.toString()
        ) { rs -> rs.getLong("id") }

        entry.copy(id = id ?: 0)
    }

    /**
     * Removes an auction from a player's watchlist.
     */
    suspend fun remove(playerUuid: UUID, auctionId: UUID): Boolean = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM watchlist WHERE player_uuid = ? AND auction_id = ?"),
            playerUuid.toString(),
            auctionId.toString()
        ) > 0
    }

    /**
     * Gets a watchlist entry by auction ID.
     */
    suspend fun getByAuction(playerUuid: UUID, auctionId: UUID): WatchlistEntry? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM watchlist WHERE player_uuid = ? AND auction_id = ?"),
            playerUuid.toString(),
            auctionId.toString()
        ) { rs ->
            WatchlistEntry(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                addedAt = rs.getTimestamp("added_at").toInstant(),
                lastNotifiedAt = rs.getTimestamp("last_notified_at")?.toInstant(),
                hasNewActivity = rs.getBoolean("has_new_activity")
            )
        }
    }

    /**
     * Gets all watchlist entries for a player.
     */
    suspend fun getPlayerWatchlist(playerUuid: UUID): List<WatchlistEntry> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM watchlist WHERE player_uuid = ? ORDER BY added_at DESC"),
            playerUuid.toString()
        ) { rs ->
            WatchlistEntry(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                addedAt = rs.getTimestamp("added_at").toInstant(),
                lastNotifiedAt = rs.getTimestamp("last_notified_at")?.toInstant(),
                hasNewActivity = rs.getBoolean("has_new_activity")
            )
        }
    }

    /**
     * Updates the last notified timestamp for a watchlist entry.
     */
    suspend fun updateLastNotified(playerUuid: UUID, auctionId: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE watchlist SET last_notified_at = ?, has_new_activity = FALSE WHERE player_uuid = ? AND auction_id = ?"),
            Instant.now(),
            playerUuid.toString(),
            auctionId.toString()
        )
    }

    /**
     * Marks watchlist entries as having new activity.
     */
    suspend fun markAsHavingActivity(playerUuid: UUID, auctionId: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE watchlist SET has_new_activity = TRUE WHERE player_uuid = ? AND auction_id = ?"),
            playerUuid.toString(),
            auctionId.toString()
        )
    }

    /**
     * Removes all watchlist entries for expired/cancelled auctions.
     */
    suspend fun removeExpiredEntries(auctionIds: List<UUID>) = withContext(Dispatchers.IO) {
        if (auctionIds.isEmpty()) return@withContext
        
        val placeholders = auctionIds.joinToString(",") { "?" }
        database.execute(
            sql("DELETE FROM watchlist WHERE auction_id IN ($placeholders)"),
            *auctionIds.map { it.toString() }.toTypedArray()
        )
    }

    /**
     * Counts watchlist entries for a player.
     */
    suspend fun count(playerUuid: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM watchlist WHERE player_uuid = ?"),
            playerUuid.toString()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Checks if a player is watching an auction.
     */
    suspend fun isWatching(playerUuid: UUID, auctionId: UUID): Boolean = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM watchlist WHERE player_uuid = ? AND auction_id = ?"),
            playerUuid.toString(),
            auctionId.toString()
        ) { rs ->
            rs.getInt("count") > 0
        } ?: false
    }
}
