package bruh.auctionhouse.gui

import org.bukkit.Material
import java.time.Duration

/**
 * Represents the state of an order being created.
 * Handles the split between stacks (64 items each) and individual items.
 *
 * @property selectedMaterial The material being ordered
 * @property stacks Number of full stacks (64 items each)
 * @property items Individual items beyond stacks (0-63)
 * @property pricePerUnit Price per single item
 * @property duration Order duration
 * @property allowPartial Whether partial fills are allowed
 * @property minFillQuantity Minimum quantity for partial fills
 */
data class OrderCreateState(
    val selectedMaterial: Material? = null,
    val stacks: Int = 0,
    val items: Int = 0,
    val pricePerUnit: Double = 1.0,
    val duration: Duration = Duration.ofHours(72),
    val allowPartial: Boolean = true,
    val minFillQuantity: Int? = null
) {
    /**
     * Total quantity in items.
     */
    val totalQuantity: Int get() = (stacks * 64) + items

    /**
     * Total value of the order.
     */
    val totalValue: Double get() = totalQuantity * pricePerUnit

    /**
     * Check if the state is valid for order creation.
     */
    fun isValid(): Boolean = selectedMaterial != null && totalQuantity > 0 && pricePerUnit > 0

    /**
     * Creates a copy with updated stacks, auto-adjusting items if needed.
     */
    fun withStacks(newStacks: Int): OrderCreateState {
        return copy(stacks = newStacks.coerceAtLeast(0))
    }

    /**
     * Creates a copy with updated items, auto-carrying to/from stacks.
     */
    fun withItems(newItems: Int): OrderCreateState {
        val normalizedItems = when {
            newItems >= 64 -> {
                val extraStacks = newItems / 64
                copy(items = newItems % 64, stacks = stacks + extraStacks)
            }
            newItems < 0 -> {
                val borrowedStacks = ((-newItems + 63) / 64).coerceAtMost(stacks)
                copy(
                    items = (borrowedStacks * 64) + newItems,
                    stacks = stacks - borrowedStacks
                )
            }
            else -> copy(items = newItems)
        }
        return normalizedItems
    }

    /**
     * Creates a copy with quantity updated by delta (in items).
     */
    fun withQuantityDelta(delta: Int, isShift: Boolean = false): OrderCreateState {
        val multiplier = if (isShift) 10 else 1
        val stackDelta = delta * multiplier / 64
        val itemDelta = delta * multiplier % 64

        val newStacks = stacks + stackDelta
        val newItems = items + itemDelta

        return when {
            newItems >= 64 -> copy(stacks = newStacks + 1, items = newItems - 64)
            newItems < 0 && newStacks > 0 -> copy(stacks = newStacks - 1, items = 64 + newItems)
            newItems < 0 -> copy(stacks = 0, items = 0.coerceAtLeast(newItems))
            else -> copy(stacks = newStacks.coerceAtLeast(0), items = newItems)
        }
    }

    companion object {
        const val ITEMS_PER_STACK = 64
        const val MAX_STACKS = 156 // 10,000 / 64
        const val MAX_QUANTITY = 10000
    }
}
