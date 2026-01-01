package bruh.zchat.utils.itemapi

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * Represents the type of action performed on a tracked item.
 */
enum class ItemAction {
    /** Player clicked on the item in an inventory */
    CLICK,
    /** Player dropped the item (Q or Ctrl+Q) */
    DROP,
    /** Player right-clicked to use the item in the world */
    USE,
    /** Player attempted to move the item to a different slot */
    MOVE
}

/**
 * Context provided to tracked item handlers containing information about the interaction.
 *
 * @property player The player who interacted with the item
 * @property itemStack The ItemStack being interacted with
 * @property slot The slot the item is in (-1 if not applicable)
 * @property action The type of action performed
 * @property clickType The click type (if applicable)
 * @property isShiftClick Whether shift was held during click
 * @property isRightClick Whether this was a right-click
 * @property isLeftClick Whether this was a left-click
 * @property isControlDrop Whether Ctrl was held during drop (drop entire stack)
 * @property targetSlot The target slot for move actions (-1 if not applicable)
 */
data class ItemContext(
    val player: Player,
    val itemStack: ItemStack,
    val slot: Int,
    val action: ItemAction,
    val clickType: ClickType? = null,
    val isShiftClick: Boolean = false,
    val isRightClick: Boolean = false,
    val isLeftClick: Boolean = false,
    val isControlDrop: Boolean = false,
    val targetSlot: Int = -1
) {
    /** Whether this was a middle-click */
    val isMiddleClick: Boolean get() = clickType == ClickType.MIDDLE

    /** Whether this was a double-click */
    val isDoubleClick: Boolean get() = clickType == ClickType.DOUBLE_CLICK

    /** Whether a number key was pressed */
    val isNumberKey: Boolean get() = clickType == ClickType.NUMBER_KEY

    /** Whether this was any kind of drop action */
    val isDrop: Boolean get() = action == ItemAction.DROP
}
