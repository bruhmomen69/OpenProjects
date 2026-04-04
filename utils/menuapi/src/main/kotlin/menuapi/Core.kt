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
    var title: Component
    val background: VItem?

    /**
     * Called by the framework before initial render and before each refresh.
     * Override to build items from current state.
     *
     * For SimpleMenu: populate the [SimpleMenu.items] map (call items.clear() first).
     * For PaginatedMenu: populate chrome items in [SimpleMenu.items] (inherited).
     *   Content items come from [PaginatedMenu.dataSource] + [PaginatedMenu.itemRenderer].
     *
     * Default: no-op. Items set directly in init/builders are preserved
     * (see [BuilderSimpleMenu] / [BuilderPaginatedMenu]).
     */
    fun populateItems() {}
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
sealed interface ClickResult {
    /** Allow the default behavior */
    data object Allow : ClickResult

    /** Cancel the click entirely */
    data object Deny : ClickResult

    /** Close the menu after the click */
    data object Close : ClickResult

    /** Refresh the menu after the click */
    data object Refresh : ClickResult

    /**
     * Switch to another menu.
     *
     * This is used for menu-to-menu transitions to avoid close/open sequencing bugs.
     */
    data class SwitchMenu(val menu: Menu) : ClickResult
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

    /** Unique generation for this open menu instance */
    val generation: Long

    /** Get the underlying Bukkit inventory */
    fun bukkit(): Inventory

    /** Check whether this controls object still represents the player's active open menu */
    fun isOpen(): Boolean

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

    /**
     * Re-execute all async data loaders registered on the menu.
     * Use when a filter/sort changes and data needs to reload.
     * Automatically shows loading state and refreshes when data arrives.
     */
    fun reloadData()

    /**
     * Run an async action from a click handler.
     * Runs the action off the main thread, then calls [onSuccess] on the
     * main thread (only if the menu is still open).
     *
     * @param processingSlot Slot to show a processing indicator (null = don't show)
     * @param action Suspending function to run off-thread
     * @param onSuccess Called on main thread with result
     * @param onError Called on main thread on failure (defaults to SLF4J warning)
     */
    fun <R> runAsync(
        processingSlot: Int? = null,
        action: suspend () -> R,
        onSuccess: (R) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    )
}
