package bruh.auctionhouse.service

import bruh.auctionhouse.database.ConsolidatedExpiredItemRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.model.ClaimResult
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Service for claiming items from consolidated expired item groups.
 * Handles inventory space calculation and partial claiming.
 */
class ConsolidatedExpiredItemService(
    private val consolidatedRepository: ConsolidatedExpiredItemRepository,
    private val expiredItemRepository: ExpiredItemRepository
) {

    /**
     * Claims a specific quantity from a consolidated group.
     * Gives items to the player and updates the consolidated record.
     *
     * @param player The player claiming the items
     * @param consolidatedItem The consolidated item to claim from
     * @param requestedQuantity The quantity to claim
     * @return ClaimResult with success status and actual quantity claimed
     */
    suspend fun claimItems(
        player: Player,
        consolidatedItem: ConsolidatedExpiredItem,
        requestedQuantity: Int
    ): ClaimResult = withContext(Dispatchers.IO) {
        val remaining = consolidatedItem.remainingQuantity()
        if (remaining <= 0) {
            return@withContext ClaimResult(
                success = false,
                claimedQuantity = 0,
                message = "No items remaining to claim"
            )
        }

        val toClaim = requestedQuantity.coerceIn(1, remaining)

        // Calculate how many items can actually fit in inventory
        val availableSpace = calculateAvailableSpace(player, consolidatedItem.itemStack)
        val actualClaim = minOf(toClaim, availableSpace)

        if (actualClaim <= 0) {
            return@withContext ClaimResult(
                success = false,
                claimedQuantity = 0,
                message = "Inventory full"
            )
        }

        // Get individual item stacks from repository
        val itemStacks = expiredItemRepository.getItemsByGroup(consolidatedItem.id)

        // Give items to player, tracking how many were actually given
        var given = 0
        for (item in itemStacks) {
            if (given >= actualClaim) break

            val toGive = minOf(item.itemStack.amount, actualClaim - given)
            val giveStack = item.itemStack.clone().apply { amount = toGive }

            val remainder = player.inventory.addItem(giveStack)
            if (remainder.isEmpty()) {
                given += toGive
                // Mark individual item as claimed (will be fully marked by repository method)
            } else {
                // Couldn't fit - stop here
                break
            }
        }

        if (given <= 0) {
            return@withContext ClaimResult(
                success = false,
                claimedQuantity = 0,
                message = "Failed to add items to inventory"
            )
        }

        // Mark items as claimed in repository
        val markedQuantity = expiredItemRepository.markItemsAsClaimedByGroup(consolidatedItem.id, given)

        // Update consolidated record
        val claimResult = consolidatedRepository.claimQuantity(consolidatedItem.id, markedQuantity)

        if (markedQuantity >= remaining) {
            // All items claimed, mark as fully claimed
            consolidatedRepository.markFullyClaimed(consolidatedItem.id)
        }

        ClaimResult(
            success = true,
            claimedQuantity = markedQuantity,
            message = "Successfully claimed $markedQuantity items"
        )
    }

    /**
     * Calculates how many of a specific item can fit in the player's inventory.
     *
     * @param player The player to check
     * @param template The template item stack
     * @return The number of items that can fit
     */
    private fun calculateAvailableSpace(player: Player, template: ItemStack): Int {
        val maxStackSize = template.type.maxStackSize

        // Count empty slots and partial stack space
        var availableSpace = 0
        val inventory = player.inventory

        // Check each slot in the main inventory (slots 0-35, excluding armor and offhand)
        for (i in 0..35) {
            val item = inventory.getItem(i)
            if (item == null || item.type.isAir) {
                // Empty slot - can fit a full stack
                availableSpace += maxStackSize
            } else if (item.isSimilar(template)) {
                // Partial stack - can fit remaining space
                availableSpace += (maxStackSize - item.amount)
            }
        }

        return availableSpace
    }

    /**
     * Gets all consolidated expired items for a player.
     */
    suspend fun getPlayerConsolidatedItems(playerUuid: java.util.UUID): List<ConsolidatedExpiredItem> {
        return consolidatedRepository.getPlayerConsolidatedItems(playerUuid)
    }

    /**
     * Gets a single consolidated expired item by ID.
     */
    suspend fun getConsolidatedItem(id: java.util.UUID): ConsolidatedExpiredItem? {
        return consolidatedRepository.getById(id)
    }

    /**
     * Counts consolidated expired items for a player.
     */
    suspend fun countPlayerConsolidatedItems(playerUuid: java.util.UUID): Int {
        return consolidatedRepository.countPlayerConsolidatedItems(playerUuid)
    }
}
