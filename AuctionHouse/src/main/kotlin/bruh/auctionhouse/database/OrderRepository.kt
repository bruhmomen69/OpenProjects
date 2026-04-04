package bruh.auctionhouse.database

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.model.OrderType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Repository for order CRUD operations and queries.
 */
class OrderRepository(private val database: Database) {

    private fun serializeItem(item: ItemStack?): ByteArray? = item?.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray?): ItemStack? = bytes?.let { ItemStack.deserializeBytes(it) }

    /**
     * Safely converts a string to Material, returning AIR if the material is not found.
     */
    private fun safeMaterialValueOf(name: String): Material {
        return try {
            Material.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Material.AIR
        }
    }

    private fun safeOrderTypeValueOf(name: String): OrderType {
        return try {
            OrderType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            OrderType.BUY_ORDER
        }
    }

    private fun safeOrderStatusValueOf(name: String): OrderStatus {
        return try {
            OrderStatus.valueOf(name)
        } catch (e: IllegalArgumentException) {
            OrderStatus.CANCELLED
        }
    }

    /**
     * Sanitizes a short ID to prevent LIKE pattern injection.
     */
    private fun sanitizeShortId(input: String): String {
        return input.filter { it.isLetterOrDigit() }
    }

    /**
     * Maps a ResultSet row to an Order object.
     */
    private fun mapOrder(rs: ResultSet): Order = Order(
        id = UUID.fromString(rs.getString("id")),
        creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
        creatorName = rs.getString("creator_name"),
        orderType = safeOrderTypeValueOf(rs.getString("order_type")),
        itemMaterial = safeMaterialValueOf(rs.getString("item_material")),
        itemDisplayName = rs.getString("item_display_name"),
        itemLoreHash = rs.getString("item_lore_hash"),
        itemNbtHash = rs.getString("item_nbt_hash"),
        itemStack = deserializeItem(rs.getBytes("item_stack")),
        quantityRequested = rs.getInt("quantity_requested"),
        quantityFilled = rs.getInt("quantity_filled"),
        pricePerUnit = rs.getDouble("price_per_unit"),
        totalPrice = rs.getDouble("total_price"),
        status = safeOrderStatusValueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        expiresAt = rs.getTimestamp("expires_at").toInstant(),
        filledAt = rs.getTimestamp("filled_at")?.toInstant(),
        allowPartial = rs.getBoolean("allow_partial"),
        minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 },
        version = rs.getInt("version")
    )

    /**
     * Creates a new order.
     */
    suspend fun create(order: Order) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                postgres("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            order.id.toString(),
            order.creatorUuid.toString(),
            order.creatorName,
            order.orderType.name,
            order.itemMaterial.name,
            order.itemDisplayName,
            order.itemLoreHash,
            order.itemNbtHash,
            serializeItem(order.itemStack),
            order.quantityRequested,
            order.quantityFilled,
            order.pricePerUnit,
            order.totalPrice,
            order.status.name,
            order.createdAt,
            order.expiresAt,
            order.filledAt,
            order.allowPartial,
            order.minFillQuantity,
            order.version
        )
    }

    /**
     * Gets an order by its ID.
     */
    suspend fun getById(id: UUID): Order? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM orders WHERE id = ?"),
            id.toString()
        ) { rs -> mapOrder(rs) }
    }

    /**
     * Gets an order by its ID within a transaction scope.
     */
    suspend fun getById(scope: TransactionScope, id: UUID): Order? {
        return scope.querySingle(
            sql("SELECT * FROM orders WHERE id = ?"),
            id.toString()
        ) { rs -> mapOrder(rs) }
    }

    /**
     * Updates the fill status and quantity of an order with optimistic locking.
     * Returns the number of affected rows (1 = success, 0 = version mismatch or invalid status).
     *
     * CRITICAL: The WHERE clause includes both version check AND status guard:
     *   - version = ?: Optimistic locking - prevents concurrent fill conflicts
     *   - status IN ('PENDING', 'PARTIAL'): State machine guard - prevents re-updating FILLED/EXPIRED/CANCELLED orders
     *
     * The status guard is defensive: callers already check isActive() before calling,
     * but this protects against edge cases (version rollbacks, data corruption).
     * Without this guard, a FILLED order could be re-updated if somehow the version matched.
     *
     * Valid order state transitions: PENDING -> PARTIAL -> FILLED
     * See OrderStatus enum and Order.isActive() for the complete state machine.
     */
    suspend fun updateFillStatusWithVersion(
        scope: TransactionScope,
        id: UUID,
        quantityFilled: Int,
        status: OrderStatus,
        expectedVersion: Int
    ): Int {
        val filledAt = if (status == OrderStatus.FILLED) Instant.now() else null
        return scope.execute(
            sql("UPDATE orders SET quantity_filled = ?, status = ?, filled_at = ?, version = version + 1 WHERE id = ? AND version = ? AND status IN ('PENDING', 'PARTIAL')"),
            quantityFilled,
            status.name,
            filledAt,
            id.toString(),
            expectedVersion
        )
    }

    /**
     * Batch-loads orders by a list of IDs.
     * Used to avoid N+1 queries (e.g. in WatchlistMenu).
     */
    suspend fun getByIds(ids: List<UUID>): List<Order> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return database.query(
            sql("SELECT * FROM orders WHERE id IN ($placeholders)"),
            *ids.map { it.toString() }.toTypedArray()
        ) { rs -> mapOrder(rs) }
    }

    /**
     * Updates the fill status and quantity of an order.
     */
    suspend fun updateFillStatus(id: UUID, quantityFilled: Int, status: OrderStatus) = withContext(Dispatchers.IO) {
        val filledAt = if (status == OrderStatus.FILLED) Instant.now() else null

        database.execute(
            sql("UPDATE orders SET quantity_filled = ?, status = ?, filled_at = ? WHERE id = ?"),
            quantityFilled,
            status.name,
            filledAt,
            id.toString()
        )
    }

    /**
     * Updates just the status of an order.
     */
    suspend fun updateStatus(id: UUID, status: OrderStatus) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE orders SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }

    /**
     * Updates the status of an order within a transaction scope.
     */
    suspend fun updateStatus(scope: TransactionScope, id: UUID, status: OrderStatus): Int {
        return scope.execute(
            sql("UPDATE orders SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }

    suspend fun cancelWithVersion(scope: TransactionScope, id: UUID, expectedVersion: Int): Int {
        return scope.execute(
            sql("UPDATE orders SET status = 'CANCELLED', version = version + 1 WHERE id = ? AND version = ? AND status IN ('PENDING', 'PARTIAL')"),
            id.toString(),
            expectedVersion
        )
    }

    /**
     * Gets active orders with filtering and sorting.
     */
    suspend fun getActiveOrders(
        filter: OrderFilter,
        sort: OrderSort,
        page: Int,
        pageSize: Int
    ): List<Order> = withContext(Dispatchers.IO) {
        val offset = page * pageSize

        var sqlQuery = "SELECT * FROM orders WHERE status IN ('PENDING', 'PARTIAL')"
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

        filter.orderType?.let {
            sqlQuery += " AND order_type = ?"
            params.add(it.name)
        }

        sqlQuery += when (sort) {
            OrderSort.NEWEST -> " ORDER BY created_at DESC"
            OrderSort.PRICE_LOW -> " ORDER BY price_per_unit ASC"
            OrderSort.PRICE_HIGH -> " ORDER BY price_per_unit DESC"
            OrderSort.MOST_FILLED -> " ORDER BY quantity_filled DESC"
        }

        sqlQuery += " LIMIT ? OFFSET ?"
        params.add(pageSize)
        params.add(offset)

        database.query(sql(sqlQuery), *params.toTypedArray()) { rs -> mapOrder(rs) }
    }

    /**
     * Gets orders for a specific player.
     */
    suspend fun getPlayerOrders(creatorUuid: UUID, status: OrderStatus?): List<Order> = withContext(Dispatchers.IO) {
        val sqlQuery = if (status != null) {
            "SELECT * FROM orders WHERE creator_uuid = ? AND status = ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM orders WHERE creator_uuid = ? ORDER BY created_at DESC"
        }

        val params = if (status != null) {
            arrayOf(creatorUuid.toString(), status.name)
        } else {
            arrayOf(creatorUuid.toString())
        }

        database.query(sql(sqlQuery), *params) { rs -> mapOrder(rs) }
    }

    /**
     * Gets all expired pending/partial orders (for cleanup tasks) with pagination.
     */
    suspend fun getExpiredOrders(limit: Int = 100): List<Order> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM orders WHERE status IN ('PENDING', 'PARTIAL') AND expires_at < ? LIMIT ?"),
            Instant.now(),
            limit
        ) { rs -> mapOrder(rs) }
    }

    /**
     * Counts orders for a specific player with a specific status.
     */
    suspend fun countPlayerOrders(creatorUuid: UUID, status: OrderStatus): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM orders WHERE creator_uuid = ? AND status = ?"),
            creatorUuid.toString(),
            status.name
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Counts total active orders matching filter criteria.
     * Note: Includes both PENDING and PARTIAL statuses to match getActiveOrders().
     */
    suspend fun countActiveOrders(filter: OrderFilter): Int = withContext(Dispatchers.IO) {
        var sqlQuery = "SELECT COUNT(*) as count FROM orders WHERE status IN ('PENDING', 'PARTIAL')"
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

        filter.orderType?.let {
            sqlQuery += " AND order_type = ?"
            params.add(it.name)
        }

        database.querySingle(sql(sqlQuery), *params.toTypedArray()) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    suspend fun getPlayerFilledOrders(playerId: UUID): List<Order> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM orders WHERE creator_uuid = ? AND status = 'FILLED' ORDER BY filled_at DESC LIMIT 10"),
            playerId.toString()
        ) { rs -> mapOrder(rs) }
    }

    suspend fun findByShortId(shortId: String): Order? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeShortId(shortId)
        if (sanitized.isEmpty()) return@withContext null
        database.querySingle(
            sql("SELECT * FROM orders WHERE id LIKE ?"),
            "$sanitized%"
        ) { rs -> mapOrder(rs) }
    }

    suspend fun findBestBuyOrderForMaterial(material: Material): Order? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM orders WHERE order_type = 'BUY_ORDER' AND item_material = ? AND status IN ('PENDING', 'PARTIAL') AND expires_at > ? ORDER BY price_per_unit DESC LIMIT 1"),
            material.name,
            Instant.now()
        ) { rs -> mapOrder(rs) }
    }

    suspend fun updatePrice(id: UUID, pricePerUnit: Double, totalPrice: Double) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE orders SET price_per_unit = ?, total_price = ? WHERE id = ?"),
            pricePerUnit,
            totalPrice,
            id.toString()
        )
    }
}
