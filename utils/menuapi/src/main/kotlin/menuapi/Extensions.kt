package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import org.bukkit.Material

/**
 * Create a VItem quickly.
 */
fun vItem(material: XMaterial, builder: VItem.() -> Unit = {}): VItem {
    return VItem(material).apply(builder)
}

/**
 * Create a VItem from a Material.
 */
fun vItem(material: Material, builder: VItem.() -> Unit = {}): VItem {
    return VItem.of(material, builder)
}

/**
 * Slot position helper - convert row/col to slot index.
 */
fun slot(row: Int, col: Int): Int = row * 9 + col

/**
 * Range of slots in a row.
 */
fun row(rowIndex: Int): IntRange = (rowIndex * 9) until ((rowIndex + 1) * 9)

/**
 * Range of slots in a column.
 */
fun column(colIndex: Int, rows: Int = 6): List<Int> = (0 until rows).map { it * 9 + colIndex }
