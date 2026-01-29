package bruh.auctionhouse.database

import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.auctionhouse.util.toBigInteger
import bruh.auctionhouse.util.toUuid
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.math.BigInteger
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
            expiredItem.id.toBigInteger(),
            expiredItem.ownerUuid.toBigInteger(),
            expiredItem.ownerName,
            expiredItem.itemType.name,
            expiredItem.sourceId.toBigInteger(),
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
            id.toBigInteger()
        ) { rs ->
            ExpiredItem(
                id = rs.getObject("id", BigInteger::class.java).toUuid(),
                ownerUuid = rs.getObject("owner_uuid", BigInteger::class.java).toUuid(),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = rs.getObject("source_id", BigInteger::class.java).toUuid(),
                consolidatedGroupId = rs.getObject("consolidated_group_id", BigInteger::class.java)?.toUuid(),
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
            ownerUuid.toBigInteger()
        ) { rs ->
            ExpiredItem(
                id = rs.getObject("id", BigInteger::class.java).toUuid(),
                ownerUuid = rs.getObject("owner_uuid", BigInteger::class.java).toUuid(),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = rs.getObject("source_id", BigInteger::class.java).toUuid(),
                consolidatedGroupId = rs.getObject("consolidated_group_id", BigInteger::class.java)?.toUuid(),
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
            id.toBigInteger()
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
            ownerUuid.toBigInteger()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Gets all expired items that belong to a consolidated group.
     */
    suspend fun getItemsByGroup(consolidatedGroupId: UUID): List<ExpiredItem> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM expired_items WHERE consolidated_group_id = ? AND claimed = FALSE ORDER BY expired_at DESC"),
            consolidatedGroupId.toBigInteger()
        ) { rs ->
            ExpiredItem(
                id = rs.getObject("id", BigInteger::class.java).toUuid(),
                ownerUuid = rs.getObject("owner_uuid", BigInteger::class.java).toUuid(),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = rs.getObject("source_id", BigInteger::class.java).toUuid(),
                consolidatedGroupId = rs.getObject("consolidated_group_id", BigInteger::class.java)?.toUuid(),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                claimed = rs.getBoolean("claimed"),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }

    /**
     * Marks a specific quantity of items from a consolidated group as claimed.
     * This updates individual expired items until the total quantity is reached.
     * @return The actual quantity marked as claimed
     */
    suspend fun markItemsAsClaimedByGroup(consolidatedGroupId: UUID, quantity: Int): Int = withContext(Dispatchers.IO) {
        val items = getItemsByGroup(consolidatedGroupId)
        var remainingToMark = quantity
        var totalMarked = 0

        for (item in items) {
            if (remainingToMark <= 0) break

            if (item.itemStack.amount <= remainingToMark) {
                // Mark this entire item as claimed
                markAsClaimed(item.id)
                totalMarked += item.itemStack.amount
                remainingToMark -= item.itemStack.amount
            } else {
                // This is a partial claim - we need to split the item stack
                // Mark the original as claimed and create a new entry for the remainder
                val originalAmount = item.itemStack.amount
                val claimedAmount = remainingToMark

                // Update the original entry to be partially claimed (we'll mark it fully claimed for simplicity)
                markAsClaimed(item.id)

                // Create a new expired item with the remaining amount
                val remainderItem = item.copy(
                    id = UUID.randomUUID(),
                    itemStack = item.itemStack.clone().apply { amount = originalAmount - claimedAmount },
                    consolidatedGroupId = item.consolidatedGroupId
                )
                create(remainderItem)

                totalMarked += claimedAmount
                remainingToMark = 0
            }
        }

        totalMarked
    }
}
