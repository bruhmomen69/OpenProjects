package bruh.auctionhouse.service

import bruh.auctionhouse.database.ConsolidatedExpiredItemRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
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

        // Store individual items for inventory management
        items.forEach { itemStack ->
            val expiredItem = ExpiredItem(
                id = UUID.randomUUID(),
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                itemType = itemType,
                sourceId = sourceId,
                consolidatedGroupId = consolidated.id, // Link to group
                itemStack = itemStack,
                reason = reason,
                expiredAt = java.time.Instant.now()
            )
            expiredItemRepository.create(expiredItem)
        }
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
}
