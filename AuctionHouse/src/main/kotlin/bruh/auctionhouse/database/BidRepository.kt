package bruh.auctionhouse.database

import bruh.auctionhouse.model.Bid
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.util.UUID

/**
 * Repository for bid CRUD operations and bid history queries.
 */
class BidRepository(private val database: Database) {

    /**
     * Maps a ResultSet row to a Bid object.
     */
    private fun mapBid(rs: ResultSet): Bid = Bid(
        id = rs.getLong("id"),
        auctionId = UUID.fromString(rs.getString("auction_id")),
        bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
        bidderName = rs.getString("bidder_name"),
        bidAmount = rs.getDouble("bid_amount"),
        bidTime = rs.getTimestamp("bid_time").toInstant(),
        isOutbid = rs.getBoolean("is_outbid")
    )

    /**
     * Creates a new bid and returns the generated ID.
     */
    suspend fun create(bid: Bid): Long = withContext(Dispatchers.IO) {
        database.executeInsert(
            sql {
                mysql("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
                postgres("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
            },
            bid.auctionId.toString(),
            bid.bidderUuid.toString(),
            bid.bidderName,
            bid.bidAmount,
            bid.bidTime,
            bid.isOutbid
        ) ?: 0L
    }

    /**
     * Creates a new bid within a transaction scope.
     */
    suspend fun create(scope: TransactionScope, bid: Bid): Int {
        return scope.execute(
            sql("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)"),
            bid.auctionId.toString(),
            bid.bidderUuid.toString(),
            bid.bidderName,
            bid.bidAmount,
            bid.bidTime,
            bid.isOutbid
        )
    }

    /**
     * Gets all bids for a specific auction, ordered by bid amount (highest first).
     */
    suspend fun getBidsForAuction(auctionId: UUID): List<Bid> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_amount DESC"),
            auctionId.toString()
        ) { rs -> mapBid(rs) }
    }

    /**
     * Gets the highest (current winning) bid for an auction.
     */
    suspend fun getHighestBid(auctionId: UUID): Bid? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? AND is_outbid = FALSE ORDER BY bid_amount DESC LIMIT 1"),
            auctionId.toString()
        ) { rs -> mapBid(rs) }
    }

    /**
     * Gets the highest bid within a transaction scope.
     */
    suspend fun getHighestBid(scope: TransactionScope, auctionId: UUID): Bid? {
        return scope.querySingle(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? AND is_outbid = FALSE ORDER BY bid_amount DESC LIMIT 1"),
            auctionId.toString()
        ) { rs -> mapBid(rs) }
    }

    /**
     * Marks a bid as outbid.
     * Only marks if currently not outbid (idempotent, safe for concurrent calls).
     */
    suspend fun markAsOutbid(bidId: Long): Int = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auction_bids SET is_outbid = TRUE WHERE id = ? AND is_outbid = FALSE"),
            bidId
        )
    }

    /**
     * Marks a bid as outbid within a transaction scope.
     * Only marks if currently not outbid (idempotent, safe for concurrent calls).
     * Returns the number of rows affected (0 if already outbid, 1 if successfully marked).
     */
    suspend fun markAsOutbid(scope: TransactionScope, bidId: Long): Int {
        return scope.execute(
            sql("UPDATE auction_bids SET is_outbid = TRUE WHERE id = ? AND is_outbid = FALSE"),
            bidId
        )
    }

    /**
     * Gets the bid history for an auction with a limit.
     */
    suspend fun getBidHistory(auctionId: UUID, limit: Int): List<Bid> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_time DESC LIMIT ?"),
            auctionId.toString(),
            limit
        ) { rs -> mapBid(rs) }
    }

    /**
     * Gets count of outbid bids for a player (for login notifications).
     */
    suspend fun getOutbidBidsForPlayer(playerId: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM auction_bids WHERE bidder_uuid = ? AND is_outbid = TRUE"),
            playerId.toString()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Gets the highest active bid for a player on an auction.
     */
    suspend fun getPlayerActiveBid(playerId: UUID, auctionId: UUID): Bid? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? AND bidder_uuid = ? AND is_outbid = FALSE ORDER BY bid_amount DESC LIMIT 1"),
            auctionId.toString(),
            playerId.toString()
        ) { rs -> mapBid(rs) }
    }

    /**
     * Deletes a bid atomically and returns its amount for refund.
     * Uses a transaction to ensure SELECT + DELETE are atomic.
     */
    suspend fun deleteBid(bidId: Long): Double? = withContext(Dispatchers.IO) {
        database.transaction {
            val amount = querySingle(
                sql("SELECT bid_amount FROM auction_bids WHERE id = ?"),
                bidId
            ) { rs ->
                rs.getDouble("bid_amount")
            }

            if (amount != null) {
                execute(
                    sql("DELETE FROM auction_bids WHERE id = ?"),
                    bidId
                )
            }

            amount
        }
    }
}
