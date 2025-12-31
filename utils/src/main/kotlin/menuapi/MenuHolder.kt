package bruh.zchat.utils.menuapi

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * InventoryHolder implementation that tracks menu state.
 */
internal class MenuHolder<T : Menu>(
    val menuApi: MenuAPI,
    val menu: T,
    val player: Player
) : InventoryHolder {
    private lateinit var _inventory: Inventory
    var currentPage: Int = 0
        internal set

    // Track VItem positions for click handling
    val slotItems: MutableMap<Int, VItem> = mutableMapOf()

    // Track drag slots for ItemMenu
    val dragSlots: MutableMap<Int, DragItem> = mutableMapOf()

    // Controls instance
    val controls: MenuControlsImpl<T> by lazy { MenuControlsImpl(this) }

    override fun getInventory(): Inventory = _inventory

    fun setInventory(inventory: Inventory) {
        _inventory = inventory
    }
}

/**
 * Implementation of MenuControls.
 */
internal class MenuControlsImpl<T : Menu>(
    private val holder: MenuHolder<T>
) : MenuControls<T> {

    override val menu: T get() = holder.menu
    override val player: Player get() = holder.player

    override fun bukkit(): Inventory = holder.inventory

    override fun itemStackAt(slot: Int): ItemStack? = holder.inventory.getItem(slot)

    override fun setItemAt(slot: Int, item: ItemStack?) {
        holder.inventory.setItem(slot, item)
    }

    override fun setVItemAt(slot: Int, item: VItem) {
        holder.slotItems[slot] = item
        holder.inventory.setItem(slot, item.build())
    }

    override fun close() {
        player.closeInventory()
    }

    override fun refresh() {
        holder.menuApi.refreshMenu(holder)
    }

    override fun refreshSlot(slot: Int) {
        holder.slotItems[slot]?.let { item ->
            holder.inventory.setItem(slot, item.build())
        }
    }

    override val currentPage: Int get() = holder.currentPage

    override fun goToPage(page: Int) {
        if (menu !is PaginatedMenu<*>) return
        @Suppress("UNCHECKED_CAST")
        val paginatedMenu = menu as PaginatedMenu<Any>
        val newPage = page.coerceIn(0, paginatedMenu.pageCount - 1)
        if (newPage != holder.currentPage) {
            holder.currentPage = newPage
            refresh()
        }
    }

    override fun nextPage() {
        goToPage(currentPage + 1)
    }

    override fun previousPage() {
        goToPage(currentPage - 1)
    }

    override fun hasNextPage(): Boolean {
        val paginatedMenu = menu as? PaginatedMenu<*> ?: return false
        return currentPage < paginatedMenu.pageCount - 1
    }

    override fun hasPreviousPage(): Boolean {
        return currentPage > 0
    }

    override val totalPages: Int
        get() = (menu as? PaginatedMenu<*>)?.pageCount ?: 1
}
