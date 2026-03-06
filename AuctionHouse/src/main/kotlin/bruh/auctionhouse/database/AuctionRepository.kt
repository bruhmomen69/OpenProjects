package bruh.auctionhouse.database

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.AuctionType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Repository for auction CRUD operations and queries.
 */
class AuctionRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)
    
    /**
     * Creates a new auction listing.
     */
    suspend fun create(auction: Auction) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            auction.id.toString(),
            auction.sellerUuid.toString(),
            auction.sellerName,
            serializeItem(auction.itemStack),
            auction.itemMaterial,
            auction.itemDisplayName,
            auction.auctionType.name,
            auction.startPrice,
            auction.buyNowPrice,
            auction.reservePrice,
            auction.minIncrement,
            auction.status.name,
            auction.createdAt,
            auction.endsAt,
            auction.soldAt,
            auction.soldToUuid?.toString(),
            auction.soldToName,
            auction.finalPrice,
            auction.viewCount,
            auction.bidCount,
            auction.isAnonymous,
            auction.extensionCount,
            auction.manualExtensionCount
        )
    }

    /**
     * Gets an auction by its ID.
     */
    suspend fun getById(id: UUID): Auction? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql {
                mysql("SELECT * FROM auctions WHERE id = ?")
                sqlite("SELECT * FROM auctions WHERE id = ?")
            },
            id.toString()
        ) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                extensionCount = rs.getInt("extension_count"),
                manualExtensionCount = rs.getInt("manual_extension_count")
            )
        }
    }
    
    /**
     * Gets active auctions with filtering and sorting.
     */
    suspend fun getActiveAuctions(
        filter: AuctionFilter,
        sort: AuctionSort,
        page: Int,
        pageSize: Int
    ): List<Auction> = withContext(Dispatchers.IO) {
        val offset = page * pageSize

        var sqlQuery = "SELECT * FROM auctions WHERE status = 'ACTIVE'"
        val params = mutableListOf<Any>()

        filter.searchQuery?.let {
            sqlQuery += " AND (item_display_name LIKE ? OR item_material LIKE ?)"
            params.add("%$it%")
            params.add("%$it%")
        }

        filter.material?.let {
            sqlQuery += " AND item_material = ?"
            params.add(it)
        }

        filter.auctionType?.let {
            sqlQuery += " AND auction_type = ?"
            params.add(it.name)
        }

        filter.minPrice?.let {
            sqlQuery += " AND start_price >= ?"
            params.add(it)
        }

        filter.maxPrice?.let {
            sqlQuery += " AND start_price <= ?"
            params.add(it)
        }

        filter.sellerName?.let {
            sqlQuery += " AND seller_name = ?"
            params.add(it)
        }

        filter.endingWithin?.let { duration ->
            val endTime = java.time.Instant.now().plus(duration)
            sqlQuery += " AND ends_at <= ?"
            params.add(endTime)
        }

        sqlQuery += when (sort) {
            AuctionSort.ENDING_SOON -> " ORDER BY ends_at ASC"
            AuctionSort.NEWEST -> " ORDER BY created_at DESC"
            AuctionSort.PRICE_LOW -> " ORDER BY start_price ASC"
            AuctionSort.PRICE_HIGH -> " ORDER BY start_price DESC"
            AuctionSort.MOST_BIDS -> " ORDER BY bid_count DESC"
            AuctionSort.RECENTLY_UPDATED -> " ORDER BY created_at DESC, bid_count DESC"
        }

        sqlQuery += " LIMIT ? OFFSET ?"
        params.add(pageSize)
        params.add(offset)

        database.query(sql(sqlQuery), *params.toTypedArray()) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                extensionCount = rs.getInt("extension_count"),
                manualExtensionCount = rs.getInt("manual_extension_count")
            )
        }
    }

    /**
     * Updates the status of an auction.
     */
    suspend fun updateStatus(id: UUID, status: AuctionStatus) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }

    /**
     * Marks an auction as sold.
     */
    suspend fun markAsSold(id: UUID, buyerUuid: UUID, buyerName: String, finalPrice: Double) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
                sqlite("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
            },
            Instant.now(),
            buyerUuid.toString(),
            buyerName,
            finalPrice,
            id.toString()
        )
    }

    /**
     * Gets auctions for a specific player.
     */
    suspend fun getPlayerAuctions(sellerUuid: UUID, status: AuctionStatus?): List<Auction> = withContext(Dispatchers.IO) {
        val sqlQuery = if (status != null) {
            "SELECT * FROM auctions WHERE seller_uuid = ? AND status = ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM auctions WHERE seller_uuid = ? ORDER BY created_at DESC"
        }

        val params = if (status != null) {
            arrayOf(sellerUuid.toString(), status.name)
        } else {
            arrayOf(sellerUuid.toString())
        }

        database.query(sql(sqlQuery), *params) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                extensionCount = rs.getInt("extension_count"),
                manualExtensionCount = rs.getInt("manual_extension_count")
            )
        }
    }
    
    /**
     * Gets all expired active auctions (for cleanup tasks).
     */
    suspend fun getExpiredAuctions(): List<Auction> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auctions WHERE status = 'ACTIVE' AND ends_at < ?"),
            Instant.now()
        ) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                extensionCount = rs.getInt("extension_count"),
                manualExtensionCount = rs.getInt("manual_extension_count")
            )
        }
    }
    
    /**
     * Increments the view count for an auction.
     */
    suspend fun incrementViewCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET view_count = view_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    suspend fun findByShortId(shortId: String): Auction? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM auctions WHERE id LIKE ?"),
            "$shortId%"
        ) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                extensionCount = rs.getInt("extension_count"),
                manualExtensionCount = rs.getInt("manual_extension_count")
            )
        }
    }
    
    suspend fun incrementBidCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET bid_count = bid_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Decrements the bid count for an auction (for bid withdrawal).
     */
    suspend fun decrementBidCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET bid_count = GREATEST(0, bid_count - 1) WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Counts auctions for a specific player with a specific status.
     */
    suspend fun countPlayerAuctions(sellerUuid: UUID, status: AuctionStatus): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM auctions WHERE seller_uuid = ? AND status = ?"),
            sellerUuid.toString(),
            status.name
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Updates the end time of an auction (for anti-snipe).
     */
    suspend fun updateEndTime(id: UUID, newEndTime: Instant) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET ends_at = ? WHERE id = ?"),
            newEndTime,
            id.toString()
        )
    }

    /**
     * Gets the extension count for an auction (for anti-snipe limit).
     */
    suspend fun getExtensionCount(id: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT extension_count FROM auctions WHERE id = ?"),
            id.toString()
        ) { rs ->
            rs.getInt("extension_count")
        } ?: 0
    }

    /**
     * Increments the extension count for an auction.
     */
    suspend fun incrementExtensionCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET extension_count = extension_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Increments the manual extension count for an auction.
     */
    suspend fun incrementManualExtensionCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET manual_extension_count = manual_extension_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Gets the manual extension count for an auction.
     */
    suspend fun getManualExtensionCount(id: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT manual_extension_count FROM auctions WHERE id = ?"),
            id.toString()
        ) { rs ->
            rs.getInt("manual_extension_count")
        } ?: 0
    }

    /**
     * Counts total active auctions matching filter criteria.
     */
    suspend fun countActiveAuctions(filter: AuctionFilter): Int = withContext(Dispatchers.IO) {
        var sqlQuery = "SELECT COUNT(*) as count FROM auctions WHERE status = 'ACTIVE'"
        val params = mutableListOf<Any>()

        filter.searchQuery?.let {
            sqlQuery += " AND (item_display_name LIKE ? OR item_material LIKE ?)"
            params.add("%$it%")
            params.add("%$it%")
        }

        filter.material?.let {
            sqlQuery += " AND item_material = ?"
            params.add(it)
        }

        filter.auctionType?.let {
            sqlQuery += " AND auction_type = ?"
            params.add(it.name)
        }

        filter.minPrice?.let {
            sqlQuery += " AND start_price >= ?"
            params.add(it)
        }

        filter.maxPrice?.let {
            sqlQuery += " AND start_price <= ?"
            params.add(it)
        }

        filter.sellerName?.let {
            sqlQuery += " AND seller_name = ?"
            params.add(it)
        }

        filter.endingWithin?.let { duration ->
            val endTime = java.time.Instant.now().plus(duration)
            sqlQuery += " AND ends_at <= ?"
            params.add(endTime)
        }

        database.querySingle(sql(sqlQuery), *params.toTypedArray()) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Gets total count of active auctions.
     */
    suspend fun getActiveAuctionsCount(): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM auctions WHERE status = 'ACTIVE'")
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Deletes an auction by ID.
     */
    suspend fun delete(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM auctions WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Updates the start price and/or buy-now price of an auction.
     * Only allowed if no bids have been placed.
     */
    suspend fun updatePrices(id: UUID, startPrice: Double?, buyNowPrice: Double?) = withContext(Dispatchers.IO) {
        val params = mutableListOf<Any>()
        val updates = mutableListOf<String>()

        startPrice?.let {
            updates.add("start_price = ?")
            params.add(it)
        }

        buyNowPrice?.let {
            updates.add("buy_now_price = ?")
            params.add(it)
        }

        if (updates.isNotEmpty()) {
            params.add(id.toString())
            database.execute(
                sql("UPDATE auctions SET ${updates.joinToString(", ")} WHERE id = ?"),
                *params.toTypedArray()
            )
        }
    }
}
