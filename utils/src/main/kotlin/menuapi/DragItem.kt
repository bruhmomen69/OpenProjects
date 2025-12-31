package bruh.zchat.utils.menuapi

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Represents a slot that accepts items dragged by the player.
 *
 * @param defaultItem Optional item to display when the slot is empty
 */
class DragItem(
    var defaultItem: VItem? = null
) : MenuSlottable {
    /**
     * Called when an item is placed into this slot.
     */
    var onItemPlace: ((Player, ItemStack, MenuControls<*>) -> Boolean)? = null

    /**
     * Called when an item is removed from this slot.
     */
    var onItemRemove: ((Player, ItemStack?, MenuControls<*>) -> Boolean)? = null

    /**
     * Called when the slot content changes.
     */
    var onSlotChange: ((Player, ItemStack?, MenuControls<*>) -> Unit)? = null

    /**
     * Validator for items being placed - return true to allow.
     */
    var itemValidator: ((ItemStack) -> Boolean)? = null

    /**
     * Set the item placement handler.
     */
    fun onPlace(handler: (Player, ItemStack, MenuControls<*>) -> Boolean) {
        onItemPlace = handler
    }

    /**
     * Set the item removal handler.
     */
    fun onRemove(handler: (Player, ItemStack?, MenuControls<*>) -> Boolean) {
        onItemRemove = handler
    }

    /**
     * Set a simple validator that just checks the item.
     */
    fun validate(validator: (ItemStack) -> Boolean) {
        itemValidator = validator
    }

    companion object {
        inline operator fun invoke(builder: DragItem.() -> Unit): DragItem {
            return DragItem().apply(builder)
        }
    }
}
