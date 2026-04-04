package bruh.auctionhouse.database

import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.model.WatchlistEntry
import bruh.auctionhouse.util.safeValueOf
import bruh.auctionhouse.util.safeValueOfOrNull
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
     * Uses INSERT OR IGNORE to prevent TOCTOU race conditions.
     */
    suspend fun add(playerUuid: UUID, auctionId: UUID): WatchlistEntry? = withContext(Dispatchers.IO) {
        val entry = WatchlistEntry(
            playerUuid = playerUuid,
            auctionId = auctionId
        )

        database.execute(
            sql {
                mysql("INSERT IGNORE INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, ?, NULL, NULL, ?, ?, ?)")
                sqlite("INSERT OR IGNORE INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, ?, NULL, NULL, ?, ?, ?)")
                postgres("INSERT INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, ?, NULL, NULL, ?, ?, ?) ON CONFLICT (player_uuid, auction_id) DO NOTHING")
            },
            entry.playerUuid.toString(),
            entry.auctionId.toString(),
            entry.addedAt,
            entry.lastNotifiedAt,
            entry.hasNewActivity
        )

        // Get the entry (may have been inserted by us or already existed)
        getByAuction(playerUuid, auctionId)
    }

    /**
     * Adds an order to a player's watchlist.
     * Uses INSERT OR IGNORE to prevent TOCTOU race conditions.
     */
    suspend fun addOrder(playerUuid: UUID, orderId: UUID, orderType: OrderType): WatchlistEntry? = withContext(Dispatchers.IO) {
        val entry = WatchlistEntry(
            playerUuid = playerUuid,
            orderId = orderId,
            orderType = orderType
        )

        database.execute(
            sql {
                mysql("INSERT IGNORE INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, NULL, ?, ?, ?, ?, ?)")
                sqlite("INSERT OR IGNORE INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, NULL, ?, ?, ?, ?, ?)")
                postgres("INSERT INTO watchlist (player_uuid, auction_id, order_id, order_type, added_at, last_notified_at, has_new_activity) VALUES (?, NULL, ?, ?, ?, ?, ?) ON CONFLICT (player_uuid, order_id) DO NOTHING")
            },
            entry.playerUuid.toString(),
            entry.orderId.toString(),
            entry.orderType!!.name,
            entry.addedAt,
            entry.lastNotifiedAt,
            entry.hasNewActivity
        )

        // Get the entry (may have been inserted by us or already existed)
        getByOrder(playerUuid, orderId)
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
     * Removes an order from a player's watchlist.
     */
    suspend fun removeOrder(playerUuid: UUID, orderId: UUID): Boolean = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM watchlist WHERE player_uuid = ? AND order_id = ?"),
            playerUuid.toString(),
            orderId.toString()
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
                orderId = rs.getString("order_id")?.let { UUID.fromString(it) },
                orderType = rs.getString("order_type")?.let { safeValueOfOrNull<OrderType>(it) },
                addedAt = rs.getTimestamp("added_at").toInstant(),
                lastNotifiedAt = rs.getTimestamp("last_notified_at")?.toInstant(),
                hasNewActivity = rs.getBoolean("has_new_activity")
            )
        }
    }

    /**
     * Gets a watchlist entry by order ID.
     */
    suspend fun getByOrder(playerUuid: UUID, orderId: UUID): WatchlistEntry? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM watchlist WHERE player_uuid = ? AND order_id = ?"),
            playerUuid.toString(),
            orderId.toString()
        ) { rs ->
            WatchlistEntry(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                auctionId = rs.getString("auction_id")?.let { UUID.fromString(it) },
                orderId = UUID.fromString(rs.getString("order_id")),
                orderType = rs.getString("order_type")?.let { safeValueOfOrNull<OrderType>(it) },
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
                auctionId = rs.getString("auction_id")?.let { UUID.fromString(it) },
                orderId = rs.getString("order_id")?.let { UUID.fromString(it) },
                orderType = rs.getString("order_type")?.let { safeValueOfOrNull<OrderType>(it) },
                addedAt = rs.getTimestamp("added_at").toInstant(),
                lastNotifiedAt = rs.getTimestamp("last_notified_at")?.toInstant(),
                hasNewActivity = rs.getBoolean("has_new_activity")
            )
        }
    }

    /**
     * Gets auction watchlist entries for a player.
     */
    suspend fun getPlayerAuctionWatchlist(playerUuid: UUID): List<WatchlistEntry> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM watchlist WHERE player_uuid = ? AND auction_id IS NOT NULL ORDER BY added_at DESC"),
            playerUuid.toString()
        ) { rs ->
            WatchlistEntry(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                orderId = null,
                orderType = null,
                addedAt = rs.getTimestamp("added_at").toInstant(),
                lastNotifiedAt = rs.getTimestamp("last_notified_at")?.toInstant(),
                hasNewActivity = rs.getBoolean("has_new_activity")
            )
        }
    }

    /**
     * Gets order watchlist entries for a player.
     */
    suspend fun getPlayerOrderWatchlist(playerUuid: UUID): List<WatchlistEntry> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM watchlist WHERE player_uuid = ? AND order_id IS NOT NULL ORDER BY added_at DESC"),
            playerUuid.toString()
        ) { rs ->
            WatchlistEntry(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                auctionId = null,
                orderId = UUID.fromString(rs.getString("order_id")),
                orderType = safeValueOf<OrderType>(rs.getString("order_type"), OrderType.BUY_ORDER),
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
     * Removes all watchlist entries for expired/cancelled orders.
     */
    suspend fun removeExpiredOrderEntries(orderIds: List<UUID>) = withContext(Dispatchers.IO) {
        if (orderIds.isEmpty()) return@withContext
        
        val placeholders = orderIds.joinToString(",") { "?" }
        database.execute(
            sql("DELETE FROM watchlist WHERE order_id IN ($placeholders)"),
            *orderIds.map { it.toString() }.toTypedArray()
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

    /**
     * Checks if a player is watching an order.
     */
    suspend fun isWatchingOrder(playerUuid: UUID, orderId: UUID): Boolean = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM watchlist WHERE player_uuid = ? AND order_id = ?"),
            playerUuid.toString(),
            orderId.toString()
        ) { rs ->
            rs.getInt("count") > 0
        } ?: false
    }
}
