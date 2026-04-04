package bruh.auctionhouse.database

import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.auctionhouse.util.safeValueOf
import bruh.auctionhouse.util.toBigInteger
import bruh.auctionhouse.util.toUuid
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.math.BigInteger
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Repository for expired item CRUD operations.
 */
class ExpiredItemRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)

    private fun mapExpiredItem(rs: ResultSet): ExpiredItem = ExpiredItem(
        id = rs.getObject("id", BigInteger::class.java).toUuid(),
        ownerUuid = rs.getObject("owner_uuid", BigInteger::class.java).toUuid(),
        ownerName = rs.getString("owner_name"),
        itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
        sourceId = rs.getObject("source_id", BigInteger::class.java).toUuid(),
        consolidatedGroupId = rs.getObject("consolidated_group_id", BigInteger::class.java)?.toUuid(),
        itemStack = deserializeItem(rs.getBytes("item_stack")),
        reason = rs.getString("reason"),
        expiredAt = rs.getTimestamp("expired_at").toInstant(),
        claimed = rs.getBoolean("claimed"),
        claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
    )
    
    /**
     * Creates a new expired item entry.
     */
    suspend fun create(expiredItem: ExpiredItem) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                postgres("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
            expiredItem.claimedAt,
            expiredItem.consolidatedGroupId?.toBigInteger()
        )
    }

    /**
     * Creates a new expired item entry within a transaction scope.
     * This allows atomic storage alongside other database operations.
     */
    suspend fun create(scope: TransactionScope, expiredItem: ExpiredItem): Int {
        return scope.execute(
            sql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
            expiredItem.id.toBigInteger(),
            expiredItem.ownerUuid.toBigInteger(),
            expiredItem.ownerName,
            expiredItem.itemType.name,
            expiredItem.sourceId.toBigInteger(),
            serializeItem(expiredItem.itemStack),
            expiredItem.reason,
            expiredItem.expiredAt,
            expiredItem.claimed,
            expiredItem.claimedAt,
            expiredItem.consolidatedGroupId?.toBigInteger()
        )
    }

    /**
     * Creates multiple expired item entries in a single batch operation.
     */
    suspend fun createBatch(items: List<ExpiredItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val paramSets = items.map { item ->
            arrayOf(
                item.id.toBigInteger(),
                item.ownerUuid.toBigInteger(),
                item.ownerName,
                item.itemType.name,
                item.sourceId.toBigInteger(),
                serializeItem(item.itemStack),
                item.reason,
                item.expiredAt,
                item.claimed,
                item.claimedAt,
                item.consolidatedGroupId?.toBigInteger()
            )
        }
        database.executeBatch(
            sql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
            paramSets
        )
    }
    
    /**
     * Gets an expired item by its ID.
     */
    suspend fun getById(id: UUID): ExpiredItem? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM expired_items WHERE id = ?"),
            id.toBigInteger()
        ) { rs -> mapExpiredItem(rs) }
    }
    
    /**
     * Gets unclaimed expired items for a specific player.
     */
    suspend fun getPlayerExpiredItems(ownerUuid: UUID): List<ExpiredItem> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM expired_items WHERE owner_uuid = ? AND claimed = FALSE ORDER BY expired_at DESC"),
            ownerUuid.toBigInteger()
        ) { rs -> mapExpiredItem(rs) }
    }
    
    /**
     * Marks an expired item as claimed.
     */
    suspend fun markAsClaimed(id: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE expired_items SET claimed = TRUE, claimed_at = ? WHERE id = ? AND claimed = FALSE"),
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
        ) { rs -> mapExpiredItem(rs) }
    }

    private suspend fun getItemsByGroup(scope: TransactionScope, consolidatedGroupId: UUID): List<ExpiredItem> {
        return scope.query(
            sql("SELECT * FROM expired_items WHERE consolidated_group_id = ? AND claimed = FALSE ORDER BY expired_at DESC"),
            consolidatedGroupId.toBigInteger()
        ) { rs -> mapExpiredItem(rs) }
    }

    /**
     * Marks a specific quantity of items from a consolidated group as claimed.
     * This updates individual expired items until the total quantity is reached.
     * @return The actual quantity marked as claimed
     */
    suspend fun markItemsAsClaimedByGroup(consolidatedGroupId: UUID, quantity: Int): Int = withContext(Dispatchers.IO) {
        database.transaction {
            markItemsAsClaimedByGroup(this, consolidatedGroupId, quantity)
        }
    }

    private suspend fun markItemsAsClaimedByGroup(
        scope: TransactionScope,
        consolidatedGroupId: UUID,
        quantity: Int
    ): Int {
        val items = getItemsByGroup(scope, consolidatedGroupId)
        var remainingToMark = quantity
        var totalMarked = 0

        for (item in items) {
            if (remainingToMark <= 0) break

            if (item.itemStack.amount <= remainingToMark) {
                // Mark entire item as claimed (only if still unclaimed)
                val affected = scope.execute(
                    sql("UPDATE expired_items SET claimed = TRUE, claimed_at = ? WHERE id = ? AND claimed = FALSE"),
                    Instant.now(),
                    item.id.toBigInteger()
                )
                if (affected > 0) {
                    totalMarked += item.itemStack.amount
                    remainingToMark -= item.itemStack.amount
                }
            } else {
                // Partial claim: need to split item atomically
                val originalAmount = item.itemStack.amount
                val claimedAmount = remainingToMark

                // Mark original as claimed (only if still unclaimed)
                val affected = scope.execute(
                    sql("UPDATE expired_items SET claimed = TRUE, claimed_at = ? WHERE id = ? AND claimed = FALSE"),
                    Instant.now(),
                    item.id.toBigInteger()
                )
                if (affected > 0) {
                    // Create remainder entry
                    val remainderItem = item.copy(
                        id = UUID.randomUUID(),
                        itemStack = item.itemStack.clone().apply { amount = originalAmount - claimedAmount },
                        consolidatedGroupId = item.consolidatedGroupId
                    )
                    // Insert remainder (should not conflict because UUID is random)
                    scope.execute(
                        sql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                        remainderItem.id.toBigInteger(),
                        remainderItem.ownerUuid.toBigInteger(),
                        remainderItem.ownerName,
                        remainderItem.itemType.name,
                        remainderItem.sourceId.toBigInteger(),
                        serializeItem(remainderItem.itemStack),
                        remainderItem.reason,
                        remainderItem.expiredAt,
                        remainderItem.claimed,
                        remainderItem.claimedAt,
                        remainderItem.consolidatedGroupId?.toBigInteger()
                    )
                    totalMarked += claimedAmount
                    remainingToMark = 0
                }
            }
        }
        return totalMarked
    }
}
