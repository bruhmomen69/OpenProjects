package bruh.auctionhouse.database

import bruh.auctionhouse.model.ClaimResult
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.auctionhouse.util.getStoredUuid
import bruh.auctionhouse.util.getStoredUuidOrNull
import bruh.auctionhouse.util.safeValueOf
import bruh.auctionhouse.util.toBytes
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Repository for consolidated expired item operations.
 * Handles grouping of multiple expired item stacks from the same source.
 */
class ConsolidatedExpiredItemRepository(private val database: Database) {

    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)

    /**
     * Gets or creates a consolidated group for an order/auction.
     * If group exists for the source, adds quantity to it.
     * If group doesn't exist, creates new consolidated entry.
     *
     * @param quantity The total quantity to add to the group
     * @return The consolidated item (created or updated)
     */
    suspend fun addItemToGroup(
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        itemStack: ItemStack,
        reason: String,
        quantity: Int
    ): ConsolidatedExpiredItem = withContext(Dispatchers.IO) {
        // First, try to find an existing consolidated group for this source
        val existing = getBySourceId(sourceId)

        if (existing != null) {
            // Update existing group
            val newQuantity = existing.totalQuantity + quantity
            val now = Instant.now()

            database.execute(
                sql("""
                    UPDATE consolidated_expired_items
                    SET total_quantity = ?, last_updated_at = ?
                    WHERE id = ?
                """),
                newQuantity,
                now,
                existing.id.toBytes()
            )

            existing.copy(
                totalQuantity = newQuantity,
                lastUpdatedAt = now
            )
        } else {
            // Create new consolidated group.
            // Use INSERT OR IGNORE to handle TOCTOU race: another thread may have
            // inserted the same sourceId between our SELECT above and this INSERT.
            // Always re-read from DB afterward to return the actual persisted entry.
            val newItem = ConsolidatedExpiredItem(
                id = sourceId, // Use sourceId as the consolidated ID for simplicity
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                itemType = itemType,
                sourceId = sourceId,
                itemMaterial = itemStack.type,
                itemDisplayName = itemStack.itemMeta?.displayName?.toString(),
                totalQuantity = quantity,
                claimedQuantity = 0,
                itemStack = itemStack.clone().apply { amount = 1 }, // Store as single item template
                reason = reason,
                expiredAt = Instant.now(),
                lastUpdatedAt = Instant.now(),
                isFullyClaimed = false
            )

            database.execute(
                sql {
                    mysql("""
                        INSERT IGNORE INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    sqlite("""
                        INSERT OR IGNORE INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    postgres("""
                        INSERT INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                    """)
                },
                newItem.id.toBytes(),
                newItem.ownerUuid.toBytes(),
                newItem.ownerName,
                newItem.itemType.name,
                newItem.sourceId.toBytes(),
                newItem.itemMaterial.name,
                newItem.itemDisplayName,
                newItem.totalQuantity,
                newItem.claimedQuantity,
                serializeItem(newItem.itemStack),
                newItem.reason,
                newItem.expiredAt,
                newItem.lastUpdatedAt,
                newItem.isFullyClaimed
            )

            // Re-read to handle TOCTOU: if another thread inserted first,
            // we return their committed entry (not our stale newItem).
            getBySourceId(sourceId) ?: newItem
        }
    }

    /**
     * Gets a consolidated item by its source ID within a transaction scope.
     */
    suspend fun getBySourceId(scope: TransactionScope, sourceId: UUID): ConsolidatedExpiredItem? {
        return scope.querySingle(
            sql("SELECT * FROM consolidated_expired_items WHERE source_id = ?"),
            sourceId.toBytes()
        ) { rs ->
            ConsolidatedExpiredItem(
                id = rs.getStoredUuid("id"),
                ownerUuid = rs.getStoredUuid("owner_uuid"),
                ownerName = rs.getString("owner_name"),
                itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
                sourceId = rs.getStoredUuid("source_id"),
                itemMaterial = safeValueOf<Material>(rs.getString("item_material"), Material.AIR),
                itemDisplayName = rs.getString("item_display_name"),
                totalQuantity = rs.getInt("total_quantity"),
                claimedQuantity = rs.getInt("claimed_quantity"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                lastUpdatedAt = rs.getTimestamp("last_updated_at").toInstant(),
                isFullyClaimed = rs.getBoolean("is_fully_claimed")
            )
        }
    }

    /**
     * Adds an item to a consolidated group within a transaction scope.
     * This allows atomic storage alongside other database operations.
     */
    suspend fun addItemToGroup(
        scope: TransactionScope,
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        itemStack: ItemStack,
        reason: String,
        quantity: Int
    ): ConsolidatedExpiredItem {
        val existing = getBySourceId(scope, sourceId)

        if (existing != null) {
            val newQuantity = existing.totalQuantity + quantity
            val now = Instant.now()

            scope.execute(
                sql("""
                    UPDATE consolidated_expired_items
                    SET total_quantity = ?, last_updated_at = ?
                    WHERE id = ?
                """),
                newQuantity,
                now,
                existing.id.toBytes()
            )

            return existing.copy(
                totalQuantity = newQuantity,
                lastUpdatedAt = now
            )
        } else {
            val newItem = ConsolidatedExpiredItem(
                id = sourceId,
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                itemType = itemType,
                sourceId = sourceId,
                itemMaterial = itemStack.type,
                itemDisplayName = itemStack.itemMeta?.displayName?.toString(),
                totalQuantity = quantity,
                claimedQuantity = 0,
                itemStack = itemStack.clone().apply { amount = 1 },
                reason = reason,
                expiredAt = Instant.now(),
                lastUpdatedAt = Instant.now(),
                isFullyClaimed = false
            )

            scope.execute(
                sql("""
                    INSERT INTO consolidated_expired_items
                    (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """),
                newItem.id.toBytes(),
                newItem.ownerUuid.toBytes(),
                newItem.ownerName,
                newItem.itemType.name,
                newItem.sourceId.toBytes(),
                newItem.itemMaterial.name,
                newItem.itemDisplayName,
                newItem.totalQuantity,
                newItem.claimedQuantity,
                serializeItem(newItem.itemStack),
                newItem.reason,
                newItem.expiredAt,
                newItem.lastUpdatedAt,
                newItem.isFullyClaimed
            )

            return newItem
        }
    }

    /**
     * Gets all unclaimed consolidated groups for a player.
     */
    suspend fun getPlayerConsolidatedItems(ownerUuid: UUID): List<ConsolidatedExpiredItem> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM consolidated_expired_items WHERE owner_uuid = ? AND is_fully_claimed = FALSE ORDER BY expired_at DESC"),
            ownerUuid.toBytes()
        ) { rs ->
            ConsolidatedExpiredItem(
                id = rs.getStoredUuid("id"),
                ownerUuid = rs.getStoredUuid("owner_uuid"),
                ownerName = rs.getString("owner_name"),
                itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
                sourceId = rs.getStoredUuid("source_id"),
                itemMaterial = safeValueOf<Material>(rs.getString("item_material"), Material.AIR),
                itemDisplayName = rs.getString("item_display_name"),
                totalQuantity = rs.getInt("total_quantity"),
                claimedQuantity = rs.getInt("claimed_quantity"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                lastUpdatedAt = rs.getTimestamp("last_updated_at").toInstant(),
                isFullyClaimed = rs.getBoolean("is_fully_claimed")
            )
        }
    }

    /**
     * Gets a single consolidated item by ID.
     */
    suspend fun getById(id: UUID): ConsolidatedExpiredItem? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM consolidated_expired_items WHERE id = ?"),
            id.toBytes()
        ) { rs ->
            ConsolidatedExpiredItem(
                id = rs.getStoredUuid("id"),
                ownerUuid = rs.getStoredUuid("owner_uuid"),
                ownerName = rs.getString("owner_name"),
                itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
                sourceId = rs.getStoredUuid("source_id"),
                itemMaterial = safeValueOf<Material>(rs.getString("item_material"), Material.AIR),
                itemDisplayName = rs.getString("item_display_name"),
                totalQuantity = rs.getInt("total_quantity"),
                claimedQuantity = rs.getInt("claimed_quantity"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                lastUpdatedAt = rs.getTimestamp("last_updated_at").toInstant(),
                isFullyClaimed = rs.getBoolean("is_fully_claimed")
            )
        }
    }

    /**
     * Gets a consolidated item by its source ID.
     */
    suspend fun getBySourceId(sourceId: UUID): ConsolidatedExpiredItem? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM consolidated_expired_items WHERE source_id = ?"),
            sourceId.toBytes()
        ) { rs ->
            ConsolidatedExpiredItem(
                id = rs.getStoredUuid("id"),
                ownerUuid = rs.getStoredUuid("owner_uuid"),
                ownerName = rs.getString("owner_name"),
                itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
                sourceId = rs.getStoredUuid("source_id"),
                itemMaterial = safeValueOf<Material>(rs.getString("item_material"), Material.AIR),
                itemDisplayName = rs.getString("item_display_name"),
                totalQuantity = rs.getInt("total_quantity"),
                claimedQuantity = rs.getInt("claimed_quantity"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                lastUpdatedAt = rs.getTimestamp("last_updated_at").toInstant(),
                isFullyClaimed = rs.getBoolean("is_fully_claimed")
            )
        }
    }

    /**
     * Claims a specific quantity from a consolidated group using an atomic SQL update.
     * Uses a WHERE condition to prevent over-claiming if two players claim simultaneously.
     * Returns a ClaimResult with the actual quantity claimed.
     */
    suspend fun claimQuantity(
        groupId: UUID,
        quantityToClaim: Int
    ): ClaimResult = withContext(Dispatchers.IO) {
        // First, check how much is available (best-effort pre-check)
        val consolidated = getById(groupId)
            ?: return@withContext ClaimResult(false, 0, "Consolidated item not found")

        val available = consolidated.remainingQuantity()
        if (available <= 0) {
            return@withContext ClaimResult(false, 0, "No items remaining to claim")
        }

        val toClaim = minOf(quantityToClaim, available)
        val now = Instant.now()

        // Atomic update: only succeeds if the new claimed quantity doesn't exceed total
        // The WHERE condition (claimed_quantity + ?) <= total_quantity prevents over-claiming
        val rowsAffected = database.execute(
            sql("""
                UPDATE consolidated_expired_items
                SET claimed_quantity = claimed_quantity + ?, last_updated_at = ?, is_fully_claimed = (claimed_quantity + ?) >= total_quantity
                WHERE id = ? AND (claimed_quantity + ?) <= total_quantity
            """),
            toClaim,
            now,
            toClaim,
            groupId.toBytes(),
            toClaim
        )

        if (rowsAffected == 0) {
            // Another player claimed items concurrently, try to re-check available
            val rechecked = getById(groupId)
            val recheckedAvailable = rechecked?.remainingQuantity() ?: 0
            if (recheckedAvailable <= 0) {
                return@withContext ClaimResult(false, 0, "No items remaining to claim")
            }
            // Retry with the remaining quantity
            val retryClaim = minOf(quantityToClaim, recheckedAvailable)
            val retryRows = database.execute(
                sql("""
                    UPDATE consolidated_expired_items
                    SET claimed_quantity = claimed_quantity + ?, last_updated_at = ?, is_fully_claimed = (claimed_quantity + ?) >= total_quantity
                    WHERE id = ? AND (claimed_quantity + ?) <= total_quantity
                """),
                retryClaim,
                Instant.now(),
                retryClaim,
                groupId.toBytes(),
                retryClaim
            )
            if (retryRows == 0) {
                // Even after retry, atomic update failed. Check final state for a helpful message.
                val finalCheck = getById(groupId)
                val finalAvailable = finalCheck?.remainingQuantity() ?: 0
                return@withContext ClaimResult(
                    false, 0,
                    if (finalAvailable > 0) {
                        "Only $finalAvailable items remaining — try claiming fewer"
                    } else {
                        "No items remaining to claim"
                    }
                )
            }
            return@withContext ClaimResult(
                success = true,
                claimedQuantity = retryClaim,
                message = "Successfully claimed $retryClaim items"
            )
        }

        ClaimResult(
            success = true,
            claimedQuantity = toClaim,
            message = "Successfully claimed $toClaim items"
        )
    }

    /**
     * Marks a group as fully claimed.
     */
    suspend fun markFullyClaimed(groupId: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("""
                UPDATE consolidated_expired_items
                SET is_fully_claimed = TRUE, claimed_quantity = total_quantity, last_updated_at = ?
                WHERE id = ?
            """),
            Instant.now(),
            groupId.toBytes()
        )
    }

    /**
     * Counts unclaimed consolidated items for a player.
     */
    suspend fun countPlayerConsolidatedItems(ownerUuid: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM consolidated_expired_items WHERE owner_uuid = ? AND is_fully_claimed = FALSE"),
            ownerUuid.toBytes()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Gets consolidated items with their individual expired items using a JOIN query.
     * This is more efficient than separate queries when you need both consolidated and individual item data.
     * @return Map of ConsolidatedExpiredItem to list of its individual ExpiredItems
     */
    suspend fun getPlayerConsolidatedItemsWithJoin(ownerUuid: UUID): Map<ConsolidatedExpiredItem, List<bruh.auctionhouse.model.ExpiredItem>> = withContext(Dispatchers.IO) {
        database.query(
            sql("""
                SELECT 
                    c.id as c_id, c.owner_uuid as c_owner_uuid, c.owner_name, c.item_type, 
                    c.source_id as c_source_id, c.item_material, c.item_display_name, 
                    c.total_quantity, c.claimed_quantity, c.item_stack as c_item_stack,
                    c.reason as c_reason, c.expired_at as c_expired_at, c.last_updated_at, c.is_fully_claimed,
                    e.id as e_id, e.owner_uuid as e_owner_uuid, e.item_type as e_item_type,
                    e.source_id as e_source_id, e.consolidated_group_id, e.item_stack as e_item_stack,
                    e.reason as e_reason, e.expired_at as e_expired_at, e.claimed, e.claimed_at
                FROM consolidated_expired_items c
                LEFT JOIN expired_items e ON c.id = e.consolidated_group_id AND e.claimed = FALSE
                WHERE c.owner_uuid = ? AND c.is_fully_claimed = FALSE
                ORDER BY c.expired_at DESC, e.expired_at DESC
            """),
            ownerUuid.toBytes()
        ) { rs ->
            val consolidated = ConsolidatedExpiredItem(
                id = rs.getStoredUuid("c_id"),
                ownerUuid = rs.getStoredUuid("c_owner_uuid"),
                ownerName = rs.getString("owner_name"),
                itemType = safeValueOf<ExpiredItemType>(rs.getString("item_type"), ExpiredItemType.AUCTION_ITEM),
                sourceId = rs.getStoredUuid("c_source_id"),
                itemMaterial = safeValueOf<Material>(rs.getString("item_material"), Material.AIR),
                itemDisplayName = rs.getString("item_display_name"),
                totalQuantity = rs.getInt("total_quantity"),
                claimedQuantity = rs.getInt("claimed_quantity"),
                itemStack = deserializeItem(rs.getBytes("c_item_stack")),
                reason = rs.getString("c_reason"),
                expiredAt = rs.getTimestamp("c_expired_at").toInstant(),
                lastUpdatedAt = rs.getTimestamp("last_updated_at").toInstant(),
                isFullyClaimed = rs.getBoolean("is_fully_claimed")
            )
            
            // Check if there's an expired item in this row
            val eIdObj = rs.getStoredUuidOrNull("e_id")
            val expiredItem = if (eIdObj != null) {
                bruh.auctionhouse.model.ExpiredItem(
                    id = eIdObj,
                    ownerUuid = rs.getStoredUuid("e_owner_uuid"),
                    ownerName = rs.getString("owner_name"),
                    itemType = ExpiredItemType.valueOf(rs.getString("e_item_type")),
                    sourceId = rs.getStoredUuid("e_source_id"),
                    consolidatedGroupId = rs.getStoredUuidOrNull("consolidated_group_id"),
                    itemStack = deserializeItem(rs.getBytes("e_item_stack")),
                    reason = rs.getString("e_reason"),
                    expiredAt = rs.getTimestamp("e_expired_at").toInstant(),
                    claimed = rs.getBoolean("claimed"),
                    claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
                )
            } else null
            
            Pair(consolidated, expiredItem)
        }.groupBy({ it.first }, { it.second?.let { listOf(it) } ?: emptyList() })
            .mapValues { (_, value) -> value.flatten() }
    }
}
