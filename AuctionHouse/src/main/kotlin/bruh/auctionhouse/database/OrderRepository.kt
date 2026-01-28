package bruh.auctionhouse.database

import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.model.OrderType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Repository for order CRUD operations and queries.
 */
class OrderRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack?): ByteArray? = item?.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray?): ItemStack? = bytes?.let { ItemStack.deserializeBytes(it) }
    
    /**
     * Creates a new order.
     */
    suspend fun create(order: Order) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
            order.minFillQuantity
        )
    }
    
    /**
     * Gets an order by its ID.
     */
    suspend fun getById(id: UUID): Order? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM orders WHERE id = ?"),
            id.toString()
        ) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
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
        
        database.query(sql(sqlQuery), *params.toTypedArray()) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
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
        
        database.query(sql(sqlQuery), *params) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
    }
    
    /**
     * Gets all expired pending/partial orders (for cleanup tasks).
     */
    suspend fun getExpiredOrders(): List<Order> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM orders WHERE status IN ('PENDING', 'PARTIAL') AND expires_at < ?"),
            Instant.now()
        ) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
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
}
