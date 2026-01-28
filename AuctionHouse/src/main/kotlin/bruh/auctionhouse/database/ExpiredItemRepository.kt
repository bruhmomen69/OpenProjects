package bruh.auctionhouse.database

import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Repository for expired item CRUD operations.
 */
class ExpiredItemRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)
    
    /**
     * Creates a new expired item entry.
     */
    suspend fun create(expiredItem: ExpiredItem) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            expiredItem.id.toString(),
            expiredItem.ownerUuid.toString(),
            expiredItem.ownerName,
            expiredItem.itemType.name,
            expiredItem.sourceId.toString(),
            serializeItem(expiredItem.itemStack),
            expiredItem.reason,
            expiredItem.expiredAt,
            expiredItem.claimed,
            expiredItem.claimedAt
        )
    }
    
    /**
     * Gets an expired item by its ID.
     */
    suspend fun getById(id: UUID): ExpiredItem? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM expired_items WHERE id = ?"),
            id.toString()
        ) { rs ->
            ExpiredItem(
                id = UUID.fromString(rs.getString("id")),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = UUID.fromString(rs.getString("source_id")),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                claimed = rs.getBoolean("claimed"),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }
    
    /**
     * Gets unclaimed expired items for a specific player.
     */
    suspend fun getPlayerExpiredItems(ownerUuid: UUID): List<ExpiredItem> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM expired_items WHERE owner_uuid = ? AND claimed = FALSE ORDER BY expired_at DESC"),
            ownerUuid.toString()
        ) { rs ->
            ExpiredItem(
                id = UUID.fromString(rs.getString("id")),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = UUID.fromString(rs.getString("source_id")),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                claimed = rs.getBoolean("claimed"),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }
    
    /**
     * Marks an expired item as claimed.
     */
    suspend fun markAsClaimed(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE expired_items SET claimed = TRUE, claimed_at = ? WHERE id = ?"),
            Instant.now(),
            id.toString()
        )
    }
    
    /**
     * Deletes expired items older than the specified number of days.
     * @return Number of rows deleted
     */
    suspend fun deleteOldItems(days: Int): Int = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM expired_items WHERE expired_at < ?"),
            Instant.now().minusSeconds(days * 86400L)
        )
    }
    
    /**
     * Counts unclaimed expired items for a specific player.
     */
    suspend fun countPlayerExpiredItems(ownerUuid: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM expired_items WHERE owner_uuid = ? AND claimed = FALSE"),
            ownerUuid.toString()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }
}
