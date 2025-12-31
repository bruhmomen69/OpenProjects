package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * A menu that automatically positions items for optimal spacing.
 * Items are distributed evenly across the available space with proper centering.
 */
class DynamicMenu : Menu {
    override var title: Component = Component.empty()
    val items: MutableList<VItem> = mutableListOf()
    override var background: VItem? = null

    var onOpen: ((Player, MenuControls<DynamicMenu>) -> Unit)? = null
    var onClose: ((Player, MenuControls<DynamicMenu>) -> Unit)? = null

    /**
     * Add an item using a builder.
     */
    inline fun item(material: XMaterial, builder: VItem.() -> Unit = {}) {
        items.add(VItem(material).apply(builder))
    }

    /**
     * Add an item.
     */
    fun item(item: VItem) {
        items.add(item)
    }

    /**
     * Add multiple items.
     */
    fun items(vararg itemList: VItem) {
        items.addAll(itemList)
    }

    /**
     * Calculate the optimal row count based on item count.
     * More rows for better spacing with more items.
     */
    val rows: Int
        get() = when {
            items.size <= 2 -> 3   // 2 items: 3 rows for good vertical centering
            items.size <= 4 -> 3   // 4 items: 3 rows
            items.size <= 6 -> 4   // 6 items: 4 rows for better spacing
            items.size <= 9 -> 5   // Up to 9 items: 5 rows
            items.size <= 14 -> 5  // Up to 14 items: 5 rows
            else -> 6              // More items: 6 rows
        }

    /**
     * Calculate optimal slot positions for the items with proper spacing.
     */
    fun calculateSlots(): Map<Int, VItem> {
        val result = mutableMapOf<Int, VItem>()
        val count = items.size
        val rowCount = rows

        if (count == 0) return result

        val slots = calculateSpacedSlots(count, rowCount)
        slots.forEachIndexed { index, slot ->
            if (index < items.size) {
                result[slot] = items[index]
            }
        }

        return result
    }

    /**
     * Calculate slot positions with even spacing between items.
     * Items are distributed to maximize spacing while staying centered.
     */
    private fun calculateSpacedSlots(count: Int, rows: Int): List<Int> {
        return when (count) {
            1 -> listOf(centerSlot(rows))
            2 -> twoItemLayout(rows)
            3 -> threeItemLayout(rows)
            4 -> fourItemLayout(rows)
            5 -> fiveItemLayout(rows)
            6 -> sixItemLayout(rows)
            7 -> sevenItemLayout(rows)
            else -> multiRowLayout(count, rows)
        }
    }

    /**
     * Get the center slot of the inventory.
     */
    private fun centerSlot(rows: Int): Int {
        val middleRow = rows / 2
        return middleRow * 9 + 4
    }

    /**
     * Layout for 2 items - evenly spaced on the center row.
     */
    private fun twoItemLayout(rows: Int): List<Int> {
        val middleRow = rows / 2
        // Place at columns 2 and 6 for even spacing (indices 2 and 6)
        return listOf(
            middleRow * 9 + 2,
            middleRow * 9 + 6
        )
    }

    /**
     * Layout for 3 items - evenly spaced on the center row.
     */
    private fun threeItemLayout(rows: Int): List<Int> {
        val middleRow = rows / 2
        // Place at columns 1, 4, 7 for even spacing
        return listOf(
            middleRow * 9 + 1,
            middleRow * 9 + 4,
            middleRow * 9 + 7
        )
    }

    /**
     * Layout for 4 items - 2x2 grid centered.
     */
    private fun fourItemLayout(rows: Int): List<Int> {
        val topRow = (rows / 2) - 1
        val bottomRow = rows / 2
        // Two columns with spacing
        return listOf(
            topRow * 9 + 2,
            topRow * 9 + 6,
            bottomRow * 9 + 2,
            bottomRow * 9 + 6
        )
    }

    /**
     * Layout for 5 items - 2 on top, 3 on bottom (or vice versa for balance).
     */
    private fun fiveItemLayout(rows: Int): List<Int> {
        val topRow = (rows / 2) - 1
        val bottomRow = rows / 2
        // 3 on top row, 2 on bottom row
        return listOf(
            topRow * 9 + 1,
            topRow * 9 + 4,
            topRow * 9 + 7,
            bottomRow * 9 + 2,
            bottomRow * 9 + 6
        )
    }

    /**
     * Layout for 6 items - 2 rows of 3, well spaced.
     */
    private fun sixItemLayout(rows: Int): List<Int> {
        val topRow = (rows / 2) - 1
        val bottomRow = rows / 2
        // 3 on each row with good spacing
        return listOf(
            topRow * 9 + 1,
            topRow * 9 + 4,
            topRow * 9 + 7,
            bottomRow * 9 + 1,
            bottomRow * 9 + 4,
            bottomRow * 9 + 7
        )
    }

    /**
     * Layout for 7 items - 3 on top, 4 on bottom (or balanced).
     */
    private fun sevenItemLayout(rows: Int): List<Int> {
        val topRow = (rows / 2) - 1
        val bottomRow = rows / 2
        return listOf(
            topRow * 9 + 1,
            topRow * 9 + 4,
            topRow * 9 + 7,
            bottomRow * 9 + 1,
            bottomRow * 9 + 3,
            bottomRow * 9 + 5,
            bottomRow * 9 + 7
        )
    }

    /**
     * Layout for more than 7 items - distribute across multiple rows.
     */
    private fun multiRowLayout(count: Int, rows: Int): List<Int> {
        val slots = mutableListOf<Int>()
        val maxItemsPerRow = 7

        // Calculate how many rows we need
        val rowsNeeded = (count + maxItemsPerRow - 1) / maxItemsPerRow

        // Calculate starting row to center vertically
        val startRow = (rows - rowsNeeded) / 2

        var remaining = count
        for (rowOffset in 0 until rowsNeeded) {
            val rowIndex = startRow + rowOffset
            val itemsInThisRow = minOf(maxItemsPerRow, remaining)

            // Calculate positions for this row with even spacing
            val rowSlots = calculateRowSlots(rowIndex, itemsInThisRow)
            slots.addAll(rowSlots)

            remaining -= itemsInThisRow
        }

        return slots
    }

    /**
     * Calculate evenly spaced slots for a single row.
     */
    private fun calculateRowSlots(rowIndex: Int, itemCount: Int): List<Int> {
        if (itemCount <= 0) return emptyList()
        if (itemCount == 1) return listOf(rowIndex * 9 + 4)

        val slots = mutableListOf<Int>()

        // For even spacing: calculate the gap between items
        // We want items to be evenly distributed across columns 1-7 (leaving borders)
        val usableWidth = 7 // columns 1 through 7
        val totalGaps = itemCount + 1 // gaps on both sides and between items
        val gapSize = usableWidth.toFloat() / totalGaps

        for (i in 0 until itemCount) {
            // Position = 1 + gapSize * (i + 1) - 0.5 * gapSize (centered in each "cell")
            // Simplified: position = 1 + gapSize * (i + 0.5)
            val col = (1 + gapSize * (i + 1)).toInt().coerceIn(1, 7)
            slots.add(rowIndex * 9 + col)
        }

        return slots
    }

    companion object {
        inline operator fun invoke(builder: DynamicMenu.() -> Unit): DynamicMenu {
            return DynamicMenu().apply(builder)
        }
    }
}
