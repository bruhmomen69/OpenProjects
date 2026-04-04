package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * A menu that supports both fixed items and drag-and-drop slots.
 */
open class ItemMenu : Menu {
    override var title: Component = Component.empty()
    var rows: Int = 3
    val items: MutableMap<Int, MenuSlottable> = mutableMapOf()
    override var background: VItem? = null

    var onOpen: ((Player, MenuControls<ItemMenu>) -> Unit)? = null
    var onClose: ((Player, MenuControls<ItemMenu>) -> Unit)? = null

    /**
     * Add a display item at a slot.
     */
    inline fun item(slot: Int, material: XMaterial, builder: VItem.() -> Unit = {}) {
        items[slot] = VItem(material).apply(builder)
    }

    /**
     * Add a display item at a slot.
     */
    fun item(slot: Int, item: VItem) {
        items[slot] = item
    }

    /**
     * Add a drag-and-drop slot.
     */
    inline fun dragSlot(slot: Int, builder: DragItem.() -> Unit = {}) {
        items[slot] = DragItem().apply(builder)
    }

    /**
     * Add a drag-and-drop slot.
     */
    fun dragSlot(slot: Int, dragItem: DragItem) {
        items[slot] = dragItem
    }

    /**
     * Add drag-and-drop slots at multiple positions.
     */
    fun dragSlots(vararg slots: Int, builder: DragItem.() -> Unit = {}) {
        slots.forEach { slot ->
            items[slot] = DragItem().apply(builder)
        }
    }

    /**
     * Get all items currently in drag slots.
     */
    fun getDragSlotItems(inventory: Inventory): Map<Int, org.bukkit.inventory.ItemStack?> {
        return items.filterValues { it is DragItem }
            .keys
            .associateWith { inventory.getItem(it) }
    }

    val size: Int get() = rows * 9

    companion object {
        inline operator fun invoke(builder: ItemMenu.() -> Unit): ItemMenu {
            return ItemMenu().apply(builder)
        }
    }
}
