package bruh.auctionhouse.database

import bruh.auctionhouse.model.Bid
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for bid CRUD operations and bid history queries.
 */
class BidRepository(private val database: Database) {
    
    /**
     * Creates a new bid and returns the generated ID.
     */
    suspend fun create(bid: Bid): Long = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
            },
            bid.auctionId.toString(),
            bid.bidderUuid.toString(),
            bid.bidderName,
            bid.bidAmount,
            bid.bidTime,
            bid.isOutbid
        )
        
        // Get the generated ID
        database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    /**
     * Gets all bids for a specific auction, ordered by bid amount (highest first).
     */
    suspend fun getBidsForAuction(auctionId: UUID): List<Bid> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_amount DESC"),
            auctionId.toString()
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
    
    /**
     * Gets the highest (current winning) bid for an auction.
     */
    suspend fun getHighestBid(auctionId: UUID): Bid? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? AND is_outbid = FALSE ORDER BY bid_amount DESC LIMIT 1"),
            auctionId.toString()
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
    
    /**
     * Marks a bid as outbid.
     */
    suspend fun markAsOutbid(bidId: Long) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auction_bids SET is_outbid = TRUE WHERE id = ?"),
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
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
}
