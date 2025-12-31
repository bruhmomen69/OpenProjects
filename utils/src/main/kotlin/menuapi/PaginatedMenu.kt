package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * A paginated menu for displaying large collections of items.
 */
class PaginatedMenu<T> : Menu {
    override var title: Component = Component.empty()
    var rows: Int = 6
    override var background: VItem? = null

    /**
     * Title provider - receives current page number and total pages.
     */
    var titleProvider: ((Int, Int) -> Component)? = null

    /**
     * The data source for pagination.
     */
    var dataSource: List<T> = emptyList()

    /**
     * Converts a data item to a VItem.
     */
    var itemRenderer: ((T, Int) -> VItem)? = null

    /**
     * Slots where paginated items will be placed.
     */
    var contentSlots: List<Int> = (10..16) + (19..25) + (28..34) + (37..43)

    /**
     * Static items (navigation, decorations).
     */
    val staticItems: MutableMap<Int, VItem> = mutableMapOf()

    /**
     * Previous page button slot and item.
     */
    var previousPageSlot: Int = 45
    var previousPageItem: VItem = VItem(XMaterial.ARROW) {
        name = Component.text("Previous Page")
    }

    /**
     * Next page button slot and item.
     */
    var nextPageSlot: Int = 53
    var nextPageItem: VItem = VItem(XMaterial.ARROW) {
        name = Component.text("Next Page")
    }

    /**
     * Page indicator slot and item renderer.
     */
    var pageIndicatorSlot: Int = 49
    var pageIndicatorRenderer: ((Int, Int) -> VItem)? = null

    var onOpen: ((Player, MenuControls<PaginatedMenu<T>>) -> Unit)? = null
    var onClose: ((Player, MenuControls<PaginatedMenu<T>>) -> Unit)? = null

    /**
     * Add a static item.
     */
    inline fun staticItem(slot: Int, material: XMaterial, builder: VItem.() -> Unit = {}) {
        staticItems[slot] = VItem(material).apply(builder)
    }

    /**
     * Set the data source and item renderer.
     */
    fun <R : T> data(source: List<R>, renderer: (R, Int) -> VItem) {
        @Suppress("UNCHECKED_CAST")
        dataSource = source as List<T>
        @Suppress("UNCHECKED_CAST")
        itemRenderer = renderer as (T, Int) -> VItem
    }

    /**
     * Use a common content slot layout.
     */
    fun useStandardLayout() {
        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
    }

    /**
     * Get the number of items per page.
     */
    val itemsPerPage: Int get() = contentSlots.size

    /**
     * Get the total number of pages.
     */
    val pageCount: Int
        get() = if (dataSource.isEmpty()) 1 else (dataSource.size + itemsPerPage - 1) / itemsPerPage

    /**
     * Get items for a specific page.
     */
    fun getPageItems(page: Int): Map<Int, VItem> {
        val result = mutableMapOf<Int, VItem>()
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, dataSource.size)
        val renderer = itemRenderer ?: return result

        contentSlots.forEachIndexed { slotIndex, slot ->
            val dataIndex = startIndex + slotIndex
            if (dataIndex < endIndex) {
                result[slot] = renderer(dataSource[dataIndex], dataIndex)
            }
        }

        return result
    }

    val size: Int get() = rows * 9

    companion object {
        inline operator fun <T> invoke(builder: PaginatedMenu<T>.() -> Unit): PaginatedMenu<T> {
            return PaginatedMenu<T>().apply(builder)
        }
    }
}
