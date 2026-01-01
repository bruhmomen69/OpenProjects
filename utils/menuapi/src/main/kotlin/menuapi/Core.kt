package bruh.zchat.utils.menuapi

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Marker interface for items that can be placed in menu slots.
 */
sealed interface MenuSlottable

/**
 * Marker interface for all menu types.
 */
sealed interface Menu {
    val title: Component
    val background: VItem?
}

/**
 * Click context containing information about a click event.
 */
data class ClickContext(
    val player: Player,
    val slot: Int,
    val clickType: ClickType,
    val inventory: Inventory,
    val itemStack: ItemStack?,
    val isShiftClick: Boolean,
    val isRightClick: Boolean,
    val isLeftClick: Boolean,
    val hotbarButton: Int
) {
    val isMiddleClick: Boolean get() = clickType == ClickType.MIDDLE
    val isDoubleClick: Boolean get() = clickType == ClickType.DOUBLE_CLICK
    val isNumberKey: Boolean get() = clickType == ClickType.NUMBER_KEY
    val isDrop: Boolean get() = clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP
}

/**
 * Result of a click action - determines post-click behavior.
 */
enum class ClickResult {
    /** Allow the default behavior */
    ALLOW,
    /** Cancel the click entirely */
    DENY,
    /** Close the menu after the click */
    CLOSE,
    /** Refresh the menu after the click */
    REFRESH
}

/**
 * Context containing information about a drop event.
 */
data class DropContext(
    val player: Player,
    val slot: Int,
    val itemStack: ItemStack,
    val inventory: Inventory,
    val isControlDrop: Boolean
)

/**
 * Result of a drop action - determines post-drop behavior.
 */
enum class DropResult {
    /** Allow the drop */
    ALLOW,
    /** Cancel the drop entirely */
    DENY,
    /** Cancel the drop and close the menu */
    CLOSE
}

/**
 * Interface for controlling an open menu instance.
 */
interface MenuControls<out T : Menu> {
    /** The menu definition */
    val menu: T

    /** The player viewing this menu */
    val player: Player

    /** Get the underlying Bukkit inventory */
    fun bukkit(): Inventory

    /** Get the ItemStack at a specific slot */
    fun itemStackAt(slot: Int): ItemStack?

    /** Set an ItemStack at a specific slot */
    fun setItemAt(slot: Int, item: ItemStack?)

    /** Set a VItem at a specific slot */
    fun setVItemAt(slot: Int, item: VItem)

    /** Close this menu */
    fun close()

    /** Refresh the entire menu */
    fun refresh()

    /** Refresh a specific slot */
    fun refreshSlot(slot: Int)

    /** Get the current page (for paginated menus) */
    val currentPage: Int

    /** Navigate to a specific page (for paginated menus) */
    fun goToPage(page: Int)

    /** Navigate to the next page */
    fun nextPage()

    /** Navigate to the previous page */
    fun previousPage()

    /** Check if there is a next page */
    fun hasNextPage(): Boolean

    /** Check if there is a previous page */
    fun hasPreviousPage(): Boolean

    /** Get total number of pages */
    val totalPages: Int
}
