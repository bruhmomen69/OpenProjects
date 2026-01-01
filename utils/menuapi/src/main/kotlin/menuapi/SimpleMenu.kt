package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * A simple menu with fixed slot positions.
 */
class SimpleMenu : Menu {
    override var title: Component = Component.empty()
    var rows: Int = 3
    val items: MutableMap<Int, VItem> = mutableMapOf()
    override var background: VItem? = null

    /**
     * Called when the menu is opened.
     */
    var onOpen: ((Player, MenuControls<SimpleMenu>) -> Unit)? = null

    /**
     * Called when the menu is closed.
     */
    var onClose: ((Player, MenuControls<SimpleMenu>) -> Unit)? = null

    /**
     * Add an item at a specific slot using a builder.
     */
    inline fun item(slot: Int, material: XMaterial, builder: VItem.() -> Unit = {}) {
        items[slot] = VItem(material).apply(builder)
    }

    /**
     * Add an item at a specific slot.
     */
    fun item(slot: Int, item: VItem) {
        items[slot] = item
    }

    /**
     * Add an item at row and column (0-indexed).
     */
    inline fun item(row: Int, col: Int, material: XMaterial, builder: VItem.() -> Unit = {}) {
        items[row * 9 + col] = VItem(material).apply(builder)
    }

    /**
     * Add an item at row and column.
     */
    fun item(row: Int, col: Int, item: VItem) {
        items[row * 9 + col] = item
    }

    /**
     * Fill a range of slots with an item.
     */
    fun fill(slots: IntRange, item: VItem) {
        slots.forEach { items[it] = item.copy() }
    }

    /**
     * Fill the border of the menu with an item.
     */
    fun border(item: VItem) {
        val size = rows * 9
        // Top row
        for (i in 0 until 9) items[i] = item.copy()
        // Bottom row
        for (i in size - 9 until size) items[i] = item.copy()
        // Left and right columns
        for (row in 1 until rows - 1) {
            items[row * 9] = item.copy()
            items[row * 9 + 8] = item.copy()
        }
    }

    /**
     * Fill all empty slots with an item.
     */
    fun fillEmpty(item: VItem) {
        background = item
    }

    /**
     * Create a pattern-based layout.
     */
    fun pattern(vararg pattern: String, items: Map<Char, VItem>) {
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { col, char ->
                if (char != ' ' && char != '.') {
                    items[char]?.let { this.items[row * 9 + col] = it.copy() }
                }
            }
        }
    }

    /**
     * Get the total size of the inventory.
     */
    val size: Int get() = rows * 9

    companion object {
        inline operator fun invoke(builder: SimpleMenu.() -> Unit): SimpleMenu {
            return SimpleMenu().apply(builder)
        }
    }
}
