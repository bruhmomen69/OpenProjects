package bruh.auctionhouse.service

import bruh.auctionhouse.database.ConsolidatedExpiredItemRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.slf4j.Logger
import java.util.UUID
import bruh.auctionhouse.util.getStoredUuid
import bruh.auctionhouse.util.getStoredUuidOrNull
import bruh.auctionhouse.util.toBytes
import kotlin.math.min

/**
 * Service to migrate existing expired items to the consolidated format.
 * This should be run once on plugin startup after the database schema migration.
 */
class ConsolidatedExpiredItemsMigration(
    private val database: Database,
    private val expiredItemRepository: ExpiredItemRepository,
    private val consolidatedRepository: ConsolidatedExpiredItemRepository,
    private val logger: Logger
) {
    private val migrationFlagKey = "consolidated_expired_items_migration_completed"

    /**
     * Checks if the migration has already been completed.
     */
    suspend fun isMigrationCompleted(): Boolean = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT value FROM plugin_metadata WHERE key = ?"),
            migrationFlagKey
        ) { rs ->
            rs.getBoolean("value")
        } ?: false
    }

    /**
     * Marks the migration as completed.
     */
    private suspend fun markMigrationCompleted() = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("""
                    INSERT INTO plugin_metadata (key, value) VALUES (?, TRUE)
                    ON DUPLICATE KEY UPDATE value = TRUE
                """)
                postgres("""
                    INSERT INTO plugin_metadata (key, value) VALUES (?, 'TRUE')
                    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """)
                sqlite("""
                    INSERT OR REPLACE INTO plugin_metadata (key, value) VALUES (?, TRUE)
                """)
            },
            migrationFlagKey
        )
    }

    /**
     * Migrates all existing unclaimed expired items to the consolidated format.
     * Groups items by source_id and creates consolidated entries for each group.
     */
    suspend fun migrate() = withContext(Dispatchers.IO) {
        // Check if migration already completed
        if (isMigrationCompleted()) {
            return@withContext
        }

        // Get all unclaimed expired items
        val allItems = database.query(
            sql("SELECT * FROM expired_items WHERE claimed = FALSE ORDER BY expired_at DESC")
        ) { rs ->
            mapFromResultSet(rs)
        }

        if (allItems.isEmpty()) {
            // No items to migrate, just mark as completed
            markMigrationCompleted()
            return@withContext
        }

        // Group items by source_id
        val groupedItems = allItems.groupBy { it.sourceId }

        var migratedCount = 0
        var createdGroups = 0

        groupedItems.forEach { (sourceId, items) ->
            // Skip if already has a consolidated group ID
            if (items.all { it.consolidatedGroupId != null }) {
                return@forEach
            }

            // Get representative item for template
            val firstItem = items.first()
            val totalQuantity = items.sumOf { it.amount }

            // Create consolidated entry
            val consolidated = ConsolidatedExpiredItem(
                id = sourceId, // Use sourceId as consolidated ID for simplicity
                ownerUuid = firstItem.ownerUuid,
                ownerName = firstItem.ownerName,
                itemType = firstItem.itemType,
                sourceId = sourceId,
                itemMaterial = firstItem.itemStack.type,
                itemDisplayName = firstItem.itemStack.itemMeta?.displayName?.toString(),
                totalQuantity = totalQuantity,
                claimedQuantity = 0,
                itemStack = firstItem.itemStack.clone().apply { amount = 1 }, // Template
                reason = firstItem.reason,
                expiredAt = items.minOfOrNull { it.expiredAt } ?: firstItem.expiredAt,
                lastUpdatedAt = java.time.Instant.now(),
                isFullyClaimed = false
            )

            // Insert consolidated entry
            database.execute(
                sql {
                    mysql("""
                        INSERT INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    postgres("""
                        INSERT INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    sqlite("""
                        INSERT INTO consolidated_expired_items
                        (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                },
                consolidated.id.toBytes(),
                consolidated.ownerUuid.toBytes(),
                consolidated.ownerName,
                consolidated.itemType.name,
                consolidated.sourceId.toBytes(),
                consolidated.itemMaterial.name,
                consolidated.itemDisplayName,
                consolidated.totalQuantity,
                consolidated.claimedQuantity,
                firstItem.itemStack.serializeAsBytes(), // itemStack
                consolidated.reason,
                consolidated.expiredAt,
                consolidated.lastUpdatedAt,
                consolidated.isFullyClaimed
            )

            createdGroups++

            // Update existing items with consolidated_group_id
            items.forEach { item ->
                database.execute(
                    sql("UPDATE expired_items SET consolidated_group_id = ? WHERE id = ?"),
                    consolidated.id.toBytes(),
                    item.id.toBytes()
                )
                migratedCount++
            }
        }

        markMigrationCompleted()

        // Log migration results
        logger.info("[AuctionHouse] Consolidated expired items migration completed:")
        logger.info("  - Migrated $migratedCount expired items")
        logger.info("  - Created $createdGroups consolidated groups")
    }

    /**
     * Helper method to map a ResultSet to an expired item-like structure.
     * This is a simplified version for migration purposes.
     */
    private data class MigrationItem(
        val id: UUID,
        val ownerUuid: UUID,
        val ownerName: String,
        val itemType: ExpiredItemType,
        val sourceId: UUID,
        val consolidatedGroupId: UUID?,
        val itemStack: org.bukkit.inventory.ItemStack,
        val amount: Int,
        val reason: String,
        val expiredAt: java.time.Instant
    )

    private fun mapFromResultSet(rs: java.sql.ResultSet): MigrationItem {
        val itemStack = org.bukkit.inventory.ItemStack.deserializeBytes(rs.getBytes("item_stack"))
        return MigrationItem(
            id = rs.getStoredUuid("id"),
            ownerUuid = rs.getStoredUuid("owner_uuid"),
            ownerName = rs.getString("owner_name"),
            itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
            sourceId = rs.getStoredUuid("source_id"),
            consolidatedGroupId = rs.getStoredUuidOrNull("consolidated_group_id"),
            itemStack = itemStack,
            amount = itemStack.amount,
            reason = rs.getString("reason"),
            expiredAt = rs.getTimestamp("expired_at").toInstant()
        )
    }
}
