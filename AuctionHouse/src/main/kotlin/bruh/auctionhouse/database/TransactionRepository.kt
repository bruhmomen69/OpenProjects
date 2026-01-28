package bruh.auctionhouse.database

import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for transaction logging operations.
 */
class TransactionRepository(private val database: Database) {
    
    /**
     * Creates a new transaction record and returns the generated ID.
     */
    suspend fun create(transaction: Transaction): Long = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO transactions (transaction_type, from_uuid, from_name, to_uuid, to_name, amount, tax_amount, item_material, item_quantity, reference_id, timestamp, server_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO transactions (transaction_type, from_uuid, from_name, to_uuid, to_name, amount, tax_amount, item_material, item_quantity, reference_id, timestamp, server_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            transaction.transactionType.name,
            transaction.fromUuid?.toString(),
            transaction.fromName,
            transaction.toUuid?.toString(),
            transaction.toName,
            transaction.amount,
            transaction.taxAmount,
            transaction.itemMaterial,
            transaction.itemQuantity,
            transaction.referenceId?.toString(),
            transaction.timestamp,
            transaction.serverId
        )
        
        database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    /**
     * Gets transactions by reference ID (auction/order ID).
     */
    suspend fun getByReferenceId(referenceId: UUID): List<Transaction> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM transactions WHERE reference_id = ? ORDER BY timestamp DESC"),
            referenceId.toString()
        ) { rs ->
            Transaction(
                id = rs.getLong("id"),
                transactionType = TransactionType.valueOf(rs.getString("transaction_type")),
                fromUuid = rs.getString("from_uuid")?.let { UUID.fromString(it) },
                fromName = rs.getString("from_name"),
                toUuid = rs.getString("to_uuid")?.let { UUID.fromString(it) },
                toName = rs.getString("to_name"),
                amount = rs.getDouble("amount"),
                taxAmount = rs.getDouble("tax_amount"),
                itemMaterial = rs.getString("item_material"),
                itemQuantity = rs.getInt("item_quantity").takeIf { it > 0 },
                referenceId = rs.getString("reference_id")?.let { UUID.fromString(it) },
                timestamp = rs.getTimestamp("timestamp").toInstant(),
                serverId = rs.getString("server_id")
            )
        }
    }
    
    /**
     * Gets transactions for a specific player (either as sender or receiver).
     */
    suspend fun getPlayerTransactions(playerUuid: UUID, limit: Int = 50): List<Transaction> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM transactions WHERE from_uuid = ? OR to_uuid = ? ORDER BY timestamp DESC LIMIT ?"),
            playerUuid.toString(),
            playerUuid.toString(),
            limit
        ) { rs ->
            Transaction(
                id = rs.getLong("id"),
                transactionType = TransactionType.valueOf(rs.getString("transaction_type")),
                fromUuid = rs.getString("from_uuid")?.let { UUID.fromString(it) },
                fromName = rs.getString("from_name"),
                toUuid = rs.getString("to_uuid")?.let { UUID.fromString(it) },
                toName = rs.getString("to_name"),
                amount = rs.getDouble("amount"),
                taxAmount = rs.getDouble("tax_amount"),
                itemMaterial = rs.getString("item_material"),
                itemQuantity = rs.getInt("item_quantity").takeIf { it > 0 },
                referenceId = rs.getString("reference_id")?.let { UUID.fromString(it) },
                timestamp = rs.getTimestamp("timestamp").toInstant(),
                serverId = rs.getString("server_id")
            )
        }
    }
}
