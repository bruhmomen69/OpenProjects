package bruh.auctionhouse.database

import bruh.auctionhouse.model.OrderFill
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for order fill CRUD operations.
 */
class OrderFillRepository(private val database: Database) {
    
    /**
     * Creates a new order fill record and returns the generated ID.
     */
    suspend fun create(fill: OrderFill): Long = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO order_fills (order_id, filler_uuid, filler_name, quantity, price_per_unit, total_price, filled_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO order_fills (order_id, filler_uuid, filler_name, quantity, price_per_unit, total_price, filled_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
            },
            fill.orderId.toString(),
            fill.fillerUuid.toString(),
            fill.fillerName,
            fill.quantity,
            fill.pricePerUnit,
            fill.totalPrice,
            fill.filledAt
        )
        
        database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    /**
     * Gets all fills for a specific order.
     */
    suspend fun getFillsForOrder(orderId: UUID): List<OrderFill> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM order_fills WHERE order_id = ? ORDER BY filled_at DESC"),
            orderId.toString()
        ) { rs ->
            OrderFill(
                id = rs.getLong("id"),
                orderId = UUID.fromString(rs.getString("order_id")),
                fillerUuid = UUID.fromString(rs.getString("filler_uuid")),
                fillerName = rs.getString("filler_name"),
                quantity = rs.getInt("quantity"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                filledAt = rs.getTimestamp("filled_at").toInstant()
            )
        }
    }
}
