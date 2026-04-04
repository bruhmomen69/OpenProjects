# Consolidated Expired Items System Design

## Overview
This design introduces a consolidated view of expired items where multiple item stacks from the same order are grouped together, allowing users to claim specific quantities rather than individual stacks.

## 1. Data Model Changes

### New Model: ConsolidatedExpiredItem
```kotlin
data class ConsolidatedExpiredItem(
    val id: UUID,                    // Group ID (same as source order ID for order items)
    val ownerUuid: UUID,
    val ownerName: String,
    val itemType: ExpiredItemType,   // AUCTION_ITEM or ORDER_ITEM
    val sourceId: UUID,              // Original auction/order ID
    val itemMaterial: Material,      // For grouping/filtering
    val itemDisplayName: String?,    // For display purposes
    val totalQuantity: Int,          // Total items available
    val claimedQuantity: Int,        // Items already claimed
    val itemStack: ItemStack,        // Template item (amount = 1)
    val reason: String,
    val expiredAt: Instant,
    val lastUpdatedAt: Instant,
    val isFullyClaimed: Boolean = false
) {
    fun remainingQuantity(): Int = totalQuantity - claimedQuantity
    fun isEmpty(): Boolean = remainingQuantity() <= 0
}
```

### Existing Model: ExpiredItem (Modified)
The existing `ExpiredItem` becomes the backing storage for individual item stacks:
```kotlin
data class ExpiredItem(
    val id: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val itemType: ExpiredItemType,
    val sourceId: UUID,
    val consolidatedGroupId: UUID?,  // NEW: Links to consolidated group
    val itemStack: ItemStack,
    val reason: String,
    val expiredAt: Instant,
    val claimed: Boolean = false,
    val claimedAt: Instant? = null
)
```

## 2. Database Schema Changes

### New Table: consolidated_expired_items
```sql
CREATE TABLE consolidated_expired_items (
    id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    owner_name VARCHAR(32) NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    item_material VARCHAR(50) NOT NULL,
    item_display_name TEXT,
    total_quantity INT NOT NULL DEFAULT 0,
    claimed_quantity INT NOT NULL DEFAULT 0,
    item_stack BLOB NOT NULL,        -- Single item template
    reason VARCHAR(100) NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    last_updated_at TIMESTAMP NOT NULL,
    is_fully_claimed BOOLEAN DEFAULT FALSE,
    INDEX idx_owner_uuid (owner_uuid),
    INDEX idx_source_id (source_id),
    INDEX idx_fully_claimed (is_fully_claimed)
);
```

### Modified Table: expired_items
```sql
ALTER TABLE expired_items ADD COLUMN consolidated_group_id VARCHAR(36) NULL;
ALTER TABLE expired_items ADD INDEX idx_consolidated_group (consolidated_group_id);
```

## 3. Repository Layer Changes

### New Repository: ConsolidatedExpiredItemRepository
```kotlin
class ConsolidatedExpiredItemRepository(private val database: Database) {
    
    /**
     * Gets or creates a consolidated group for an order/auction.
     * If group exists, adds quantity to it.
     * If group doesn't exist, creates new consolidated entry.
     */
    suspend fun addItemToGroup(
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        itemStack: ItemStack,
        reason: String
    ): ConsolidatedExpiredItem
    
    /**
     * Gets all unclaimed consolidated groups for a player.
     */
    suspend fun getPlayerConsolidatedItems(ownerUuid: UUID): List<ConsolidatedExpiredItem>
    
    /**
     * Claims a specific quantity from a consolidated group.
     * Returns the actual quantity claimed (may be less than requested).
     */
    suspend fun claimQuantity(
        groupId: UUID,
        quantityToClaim: Int
    ): ClaimResult
    
    /**
     * Gets a single consolidated item by ID.
     */
    suspend fun getById(id: UUID): ConsolidatedExpiredItem?
    
    /**
     * Marks a group as fully claimed when quantity reaches 0.
     */
    suspend fun markFullyClaimed(groupId: UUID)
}
```

### Modified Repository: ExpiredItemRepository
Add method to get items by consolidated group for inventory distribution:
```kotlin
suspend fun getItemsByGroup(consolidatedGroupId: UUID): List<ExpiredItem>
suspend fun markItemsAsClaimedByGroup(consolidatedGroupId: UUID, quantity: Int)
```

## 4. GUI Workflow

### ExpiredItemsMenu Redesign

```kotlin
class ConsolidatedExpiredItemsMenu(
    private val consolidatedRepository: ConsolidatedExpiredItemRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    // ... other deps
) {
    fun open() {
        val consolidatedItems = runBlocking {
            consolidatedRepository.getPlayerConsolidatedItems(player.uniqueId)
        }
        
        // Paginated menu showing one entry per order/auction
        val menu = menuAPI.paginated<ConsolidatedExpiredItem> {
            // ... setup
            
            itemRenderer = { consolidatedItem, _ ->
                createConsolidatedItemDisplay(consolidatedItem)
            }
        }
    }
    
    private fun createConsolidatedItemDisplay(item: ConsolidatedExpiredItem): VItem {
        return VItem(material) {
            name = item.itemDisplayName ?: item.itemMaterial.name
            lore = listOf(
                "<gray>Available: <green>${item.remainingQuantity()}",
                "<gray>Total: <white>${item.totalQuantity}",
                "<gray>Type: <white>${item.itemType}",
                "",
                "<yellow>Click to claim items",
                "<gray>(Shift-click for max amount)"
            )
            
            onClick { clickType, _ ->
                if (clickType.isShiftClick) {
                    // Try to claim max available
                    openClaimMenu(item, item.remainingQuantity())
                } else {
                    // Open quantity selector
                    openClaimMenu(item, minOf(64, item.remainingQuantity()))
                }
                ClickResult.CLOSE
            }
        }
    }
}
```

### New: ClaimQuantityMenu
A new menu for selecting how many items to claim:

```kotlin
class ClaimQuantityMenu(
    private val menuAPI: MenuAPI,
    private val consolidatedRepository: ConsolidatedExpiredItemRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    private val player: Player,
    private val consolidatedItem: ConsolidatedExpiredItem,
    private val initialQuantity: Int
) {
    private var quantity: Int = initialQuantity.coerceIn(1, consolidatedItem.remainingQuantity())
    
    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = mm.deserialize("<gold>Claim Items</gold>")
            
            // Item display showing available/total
            item(13, createItemDisplay())
            
            // Quantity controls
            item(29, createDecreaseButton(1))      // -1
            item(30, createDecreaseButton(10))     // -10
            item(31, createQuantityDisplay())       // Shows current quantity
            item(32, createIncreaseButton(10))     // +10
            item(33, createIncreaseButton(1))      // +1
            
            // Quick select buttons
            item(38, createQuickSelectButton(64))   // Full stack
            item(39, createQuickSelectButton(576))  // 9 stacks (inventory)
            item(40, createMaxButton())              // Max available
            
            // Confirm/Cancel
            item(42, createConfirmButton())
            item(43, createCancelButton())
        }
        
        menuAPI.open(menu, player)
    }
    
    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Claim $quantity Items")
            lore = listOf(
                "<gray>You will receive: <green>$quantity",
                "<gray>Remaining after claim: <yellow>${consolidatedItem.remainingQuantity() - quantity}"
            )
            
            onClick { _, _ ->
                runBlocking {
                    val result = performClaim(quantity)
                    player.sendMessage(result.message)
                }
                // Return to main expired items menu
                ConsolidatedExpiredItemsMenu(...).open()
                ClickResult.CLOSE
            }
        }
    }
}
```

## 5. Service Layer Changes

### ExpirationService Changes
Modify how items are stored when orders/auctions expire:

```kotlin
class ExpirationService(
    private val consolidatedRepository: ConsolidatedExpiredItemRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    // ... other deps
) {
    /**
     * Stores items as consolidated group instead of individual entries.
     */
    suspend fun storeExpiredItems(
        ownerUuid: UUID,
        ownerName: String,
        itemType: ExpiredItemType,
        sourceId: UUID,
        items: List<ItemStack>,
        reason: String
    ) {
        // Create consolidated group (or update existing)
        val consolidated = consolidatedRepository.addItemToGroup(
            ownerUuid, ownerName, itemType, sourceId,
            items.first().clone().apply { amount = 1 },  // Template item
            reason
        )
        
        // Store individual items for inventory management
        items.forEach { itemStack ->
            expiredItemRepository.create(
                ExpiredItem(
                    id = UUID.randomUUID(),
                    ownerUuid = ownerUuid,
                    ownerName = ownerName,
                    itemType = itemType,
                    sourceId = sourceId,
                    consolidatedGroupId = consolidated.id,  // Link to group
                    itemStack = itemStack,
                    reason = reason,
                    expiredAt = Instant.now()
                )
            )
        }
    }
}
```

### Claim Logic
```kotlin
suspend fun claimItems(
    player: Player,
    consolidatedItem: ConsolidatedExpiredItem,
    requestedQuantity: Int
): ClaimResult {
    val remaining = consolidatedItem.remainingQuantity()
    val toClaim = minOf(requestedQuantity, remaining)
    
    // Calculate how many items can actually fit in inventory
    val availableSpace = calculateAvailableSpace(consolidatedItem.itemStack)
    val actualClaim = minOf(toClaim, availableSpace)
    
    if (actualClaim <= 0) {
        return ClaimResult(false, 0, "Inventory full")
    }
    
    // Get individual item stacks from repository
    val itemStacks = expiredItemRepository.getItemsByGroup(consolidatedItem.id)
    
    // Give items to player, tracking how many were actually given
    var given = 0
    for (item in itemStacks) {
        if (given >= actualClaim) break
        
        val toGive = minOf(item.amount, actualClaim - given)
        val giveStack = item.clone().apply { amount = toGive }
        
        val remainder = player.inventory.addItem(giveStack)
        if (remainder.isEmpty()) {
            given += toGive
            // Mark individual item as claimed or partially claimed
            expiredItemRepository.markItemPartiallyClaimed(item.id, toGive)
        } else {
            // Couldn't fit - stop here
            break
        }
    }
    
    // Update consolidated record
    consolidatedRepository.claimQuantity(consolidatedItem.id, given)
    
    return ClaimResult(true, given, "Claimed $given items")
}
```

## 6. Migration Strategy

### Migration Script (Run on plugin startup)
```kotlin
class ConsolidatedExpiredItemsMigration(
    private val database: Database,
    private val expiredItemRepository: ExpiredItemRepository,
    private val consolidatedRepository: ConsolidatedExpiredItemRepository
) {
    suspend fun migrate() {
        // Check if migration already completed
        if (isMigrationCompleted()) return
        
        // Group all unclaimed expired items by source_id
        val unclaimedItems = database.query(
            "SELECT * FROM expired_items WHERE claimed = FALSE"
        ) { rs ->
            // Map to ExpiredItem objects
        }
        
        // Group by source_id
        val grouped = unclaimedItems.groupBy { it.sourceId }
        
        grouped.forEach { (sourceId, items) ->
            // Get representative item for template
            val template = items.first().itemStack.clone().apply { amount = 1 }
            val totalQuantity = items.sumOf { it.amount }
            
            // Create consolidated entry
            val consolidated = ConsolidatedExpiredItem(
                id = sourceId,  // Use sourceId as consolidated ID for simplicity
                ownerUuid = items.first().ownerUuid,
                ownerName = items.first().ownerName,
                itemType = items.first().itemType,
                sourceId = sourceId,
                itemMaterial = template.type,
                itemDisplayName = template.itemMeta?.displayName()?.toString(),
                totalQuantity = totalQuantity,
                claimedQuantity = 0,
                itemStack = template,
                reason = items.first().reason,
                expiredAt = items.minOf { it.expiredAt },
                lastUpdatedAt = Instant.now()
            )
            
            consolidatedRepository.create(consolidated)
            
            // Update existing items with consolidated_group_id
            items.forEach { item ->
                database.execute(
                    "UPDATE expired_items SET consolidated_group_id = ? WHERE id = ?",
                    consolidated.id, item.id
                )
            }
        }
        
        markMigrationCompleted()
    }
}
```

## 7. Implementation Phases

### Phase 1: Database & Models
- Create `consolidated_expired_items` table
- Add `consolidated_group_id` to `expired_items`
- Create `ConsolidatedExpiredItem` data class
- Modify `ExpiredItem` to include group ID

### Phase 2: Repository Layer
- Create `ConsolidatedExpiredItemRepository`
- Add methods to `ExpiredItemRepository`
- Write migration script

### Phase 3: Service Layer
- Update `ExpirationService` to use consolidated storage
- Create claim logic in service layer

### Phase 4: GUI
- Create `ClaimQuantityMenu`
- Redesign `ExpiredItemsMenu` to use consolidated view

### Phase 5: Testing & Cleanup
- Test with large quantities (100+ stacks)
- Verify partial claiming works correctly
- Clean up old migration code

## Benefits

1. **Better UX**: One entry per order instead of potentially hundreds
2. **Flexible Claiming**: Users can claim exactly what they need
3. **Inventory Management**: Smart partial claiming based on available space
4. **Scalable**: Handles bulk orders gracefully
5. **Backward Compatible**: Migration preserves existing data
