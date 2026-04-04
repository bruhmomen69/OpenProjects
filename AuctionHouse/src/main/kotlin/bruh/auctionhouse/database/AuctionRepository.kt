package bruh.auctionhouse.database

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.AuctionType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Repository for auction CRUD operations and queries.
 */
class AuctionRepository(private val database: Database) {

    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)

    /**
     * Maps a ResultSet row to an Auction object.
     * Extracted to eliminate duplicated deserialization logic.
     */
    private fun mapAuction(rs: ResultSet): Auction = Auction(
        id = UUID.fromString(rs.getString("id")),
        sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
        sellerName = rs.getString("seller_name"),
        itemStack = deserializeItem(rs.getBytes("item_stack")),
        itemMaterial = rs.getString("item_material"),
        itemDisplayName = rs.getString("item_display_name"),
        auctionType = safeAuctionTypeValueOf(rs.getString("auction_type")),
        startPrice = rs.getDouble("start_price"),
        buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
        reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
        minIncrement = rs.getDouble("min_increment"),
        status = safeAuctionStatusValueOf(rs.getString("status")),
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
        manualExtensionCount = rs.getInt("manual_extension_count"),
        version = rs.getInt("version")
    )

    private fun safeAuctionTypeValueOf(name: String): AuctionType {
        return try {
            AuctionType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AuctionType.AUCTION
        }
    }

    private fun safeAuctionStatusValueOf(name: String): AuctionStatus {
        return try {
            AuctionStatus.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AuctionStatus.EXPIRED
        }
    }

    /**
     * Sanitizes a short ID to prevent LIKE pattern injection.
     * Only allows alphanumeric characters.
     */
    private fun sanitizeShortId(input: String): String {
        return input.filter { it.isLetterOrDigit() }
    }

    /**
     * Creates a new auction listing.
     */
    suspend fun create(auction: Auction) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO auctions (id, seller_uuid, seller_name, item_stack, item_material, item_display_name, auction_type, start_price, buy_now_price, reserve_price, min_increment, status, created_at, ends_at, sold_at, sold_to_uuid, sold_to_name, final_price, view_count, bid_count, is_anonymous, extension_count, manual_extension_count, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auctions (id, seller_uuid, seller_name, item_stack, item_material, item_display_name, auction_type, start_price, buy_now_price, reserve_price, min_increment, status, created_at, ends_at, sold_at, sold_to_uuid, sold_to_name, final_price, view_count, bid_count, is_anonymous, extension_count, manual_extension_count, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                postgres("INSERT INTO auctions (id, seller_uuid, seller_name, item_stack, item_material, item_display_name, auction_type, start_price, buy_now_price, reserve_price, min_increment, status, created_at, ends_at, sold_at, sold_to_uuid, sold_to_name, final_price, view_count, bid_count, is_anonymous, extension_count, manual_extension_count, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
            auction.manualExtensionCount,
            auction.version
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
                postgres("SELECT * FROM auctions WHERE id = ?")
            },
            id.toString()
        ) { rs -> mapAuction(rs) }
    }

    /**
     * Gets an auction by its ID within a transaction scope.
     */
    suspend fun getById(scope: TransactionScope, id: UUID): Auction? {
        return scope.querySingle(
            sql("SELECT * FROM auctions WHERE id = ?"),
            id.toString()
        ) { rs -> mapAuction(rs) }
    }

    /**
     * Marks an auction as sold within a transaction scope with optimistic locking.
     * Returns the number of affected rows (1 = success, 0 = version mismatch or already sold).
     *
     * CRITICAL: This SQL has TWO guards in the WHERE clause:
     *   1. status = 'ACTIVE': Only sells if auction is still active (protects against
     *      concurrent sell from processExpiredAuction() or another buyNow())
     *   2. version = ?: Optimistic lock - only succeeds if version hasn't changed
     *      since the caller read it. placeBid() increments version on each bid,
     *      so this fails if a bid was placed after the caller's read.
     *
     * Also increments version on success (version = version + 1) so subsequent
     * attempts with the old version will fail.
     *
     * @see incrementVersion for how version is maintained on bid placement
     */
    suspend fun markAsSoldWithVersion(
        scope: TransactionScope,
        id: UUID,
        buyerUuid: UUID,
        buyerName: String,
        finalPrice: Double,
        expectedVersion: Int
    ): Int {
        return scope.execute(
            sql("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ?, version = version + 1 WHERE id = ? AND status = 'ACTIVE' AND version = ?"),
            Instant.now(),
            buyerUuid.toString(),
            buyerName,
            finalPrice,
            id.toString(),
            expectedVersion
        )
    }

    /**
     * Batch-loads auctions by a list of IDs.
     * Used to avoid N+1 queries (e.g. in WatchlistMenu).
     */
    suspend fun getByIds(ids: List<UUID>): List<Auction> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return database.query(
            sql("SELECT * FROM auctions WHERE id IN ($placeholders)"),
            *ids.map { it.toString() }.toTypedArray()
        ) { rs -> mapAuction(rs) }
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

        database.query(sql(sqlQuery), *params.toTypedArray()) { rs -> mapAuction(rs) }
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
     * Updates the status of an auction within a transaction scope.
     */
    suspend fun updateStatus(scope: TransactionScope, id: UUID, status: AuctionStatus): Int {
        return scope.execute(
            sql("UPDATE auctions SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }

    suspend fun cancelWithVersion(scope: TransactionScope, id: UUID, expectedVersion: Int): Int {
        return scope.execute(
            sql("UPDATE auctions SET status = 'CANCELLED', version = version + 1 WHERE id = ? AND status = 'ACTIVE' AND version = ?"),
            id.toString(),
            expectedVersion
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
                postgres("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
            },
            Instant.now(),
            buyerUuid.toString(),
            buyerName,
            finalPrice,
            id.toString()
        )
    }

    /**
     * Marks an auction as sold within a transaction scope.
     */
    suspend fun markAsSold(scope: TransactionScope, id: UUID, buyerUuid: UUID, buyerName: String, finalPrice: Double): Int {
        return scope.execute(
            sql {
                mysql("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
                sqlite("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
                postgres("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
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

        database.query(sql(sqlQuery), *params) { rs -> mapAuction(rs) }
    }

    /**
     * Gets all expired active auctions (for cleanup tasks) with pagination.
     */
    suspend fun getExpiredAuctions(limit: Int = 100): List<Auction> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auctions WHERE status = 'ACTIVE' AND ends_at < ? LIMIT ?"),
            Instant.now(),
            limit
        ) { rs -> mapAuction(rs) }
    }

    /**
     * Gets recent sold auctions for a player with a limit.
     */
    suspend fun getRecentSoldAuctions(playerId: UUID, limit: Int): List<Auction> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM auctions WHERE seller_uuid = ? AND status = 'SOLD' ORDER BY sold_at DESC LIMIT ?"),
            playerId.toString(),
            limit
        ) { rs -> mapAuction(rs) }
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
        val sanitized = sanitizeShortId(shortId)
        if (sanitized.isEmpty()) return@withContext null
        database.querySingle(
            sql("SELECT * FROM auctions WHERE id LIKE ?"),
            "$sanitized%"
        ) { rs -> mapAuction(rs) }
    }

    suspend fun incrementBidCount(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE auctions SET bid_count = bid_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Increments the bid count within a transaction scope.
     */
    suspend fun incrementBidCount(scope: TransactionScope, id: UUID): Int {
        return scope.execute(
            sql("UPDATE auctions SET bid_count = bid_count + 1 WHERE id = ?"),
            id.toString()
        )
    }

    /**
     * Increments the version for optimistic locking.
     *
     * CRITICAL FOR CONCURRENCY: This MUST be called whenever auction state changes,
     * particularly when bids are placed. Without this:
     *   - processExpiredAuction() could sell to a bidder who was outbid
     *   - buyNow() could sell despite a concurrent bid
     *
     * The version is checked by markAsSoldWithVersion() - if the version doesn't match,
     * the UPDATE returns 0 rows and the auction isn't sold.
     *
     * Called from:
     *   - placeBid(): After creating bid and incrementing bid count
     *
     * @see markAsSoldWithVersion for how version checking prevents double-sells
     */
    suspend fun incrementVersion(scope: TransactionScope, id: UUID): Int {
        return scope.execute(
            sql("UPDATE auctions SET version = version + 1 WHERE id = ?"),
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
     * Updates the end time within a transaction scope.
     */
    suspend fun updateEndTime(scope: TransactionScope, id: UUID, newEndTime: Instant): Int {
        return scope.execute(
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
     * Gets the extension count within a transaction scope.
     */
    suspend fun getExtensionCount(scope: TransactionScope, id: UUID): Int {
        return scope.querySingle(
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
     * Increments the extension count within a transaction scope.
     */
    suspend fun incrementExtensionCount(scope: TransactionScope, id: UUID): Int {
        return scope.execute(
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
