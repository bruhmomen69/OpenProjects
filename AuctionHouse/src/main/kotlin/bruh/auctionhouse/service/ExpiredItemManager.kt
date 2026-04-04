package bruh.auctionhouse.service

import bruh.auctionhouse.database.ConsolidatedExpiredItemRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.zchat.utils.database.TransactionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Service for managing expired items with consolidation support.
 * This service provides a unified interface for creating expired items,
 * automatically consolidating items from the same source.
 */
class ExpiredItemManager(
    private val expiredItemRepository: ExpiredItemRepository,
    private val consolidatedRepository: ConsolidatedExpiredItemRepository
) {

    /**
     * Stores items as a consolidated group instead of individual entries.
     * This automatically groups items from the same source (order/auction) together.
     *
     * @param ownerUuid UUID of the player who owns these items
     * @param ownerName Name of the owner
     * @param itemType Type of source (AUCTION_ITEM or ORDER_ITEM)
     * @param sourceId UUID of the original auction or order
     * @param items List of item stacks to store
     * @param reason Reason for expiration (e.g., "EXPIRED", "CANCELLED")
     */
    suspend fun storeExpiredItems(
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        items: List<ItemStack>,
        reason: String
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext

        // Create or update consolidated group
        // Use the first item as template
        val templateItem = items.first().clone().apply { amount = 1 }
        val totalQuantity = items.sumOf { it.amount }

        // Add items to consolidated group (this creates or updates the group)
        val consolidated = consolidatedRepository.addItemToGroup(
            ownerUuid = ownerUuid,
            ownerName = ownerName,
            itemType = itemType,
            sourceId = sourceId,
            itemStack = templateItem,
            reason = reason,
            quantity = totalQuantity
        )

        // Store individual items for inventory management using batch insert
        val now = Instant.now()
        val expiredItems = items.map { itemStack ->
            ExpiredItem(
                id = UUID.randomUUID(),
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                itemType = itemType,
                sourceId = sourceId,
                consolidatedGroupId = consolidated.id,
                itemStack = itemStack,
                reason = reason,
                expiredAt = now
            )
        }
        expiredItemRepository.createBatch(expiredItems)
    }

    /**
     * Stores a single item stack as an expired item.
     * This is a convenience method that wraps storeExpiredItems with a single item.
     */
    suspend fun storeExpiredItem(
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        item: ItemStack,
        reason: String
    ) {
        storeExpiredItems(
            ownerUuid = ownerUuid,
            ownerName = ownerName,
            itemType = itemType,
            sourceId = sourceId,
            items = listOf(item),
            reason = reason
        )
    }

    /**
     * Stores a single item stack as an expired item within a transaction scope.
     * This allows atomic storage alongside other database operations.
     * Both consolidated group and individual item are created atomically.
     */
    suspend fun storeExpiredItemWithinTransaction(
        scope: TransactionScope,
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        item: ItemStack,
        reason: String
    ) {
        val templateItem = item.clone().apply { amount = 1 }
        val totalQuantity = item.amount

        val consolidated = consolidatedRepository.addItemToGroup(
            scope = scope,
            ownerUuid = ownerUuid,
            ownerName = ownerName,
            itemType = itemType,
            sourceId = sourceId,
            itemStack = templateItem,
            reason = reason,
            quantity = totalQuantity
        )

        val expiredItem = ExpiredItem(
            id = UUID.randomUUID(),
            ownerUuid = ownerUuid,
            ownerName = ownerName,
            itemType = itemType,
            sourceId = sourceId,
            consolidatedGroupId = consolidated.id,
            itemStack = item,
            reason = reason,
            expiredAt = Instant.now()
        )
        expiredItemRepository.create(scope, expiredItem)
    }
}
