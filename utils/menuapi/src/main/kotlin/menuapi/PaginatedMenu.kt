package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * A paginated menu for displaying large collections of items.
 *
 * Extends [SimpleMenu] — chrome/static items go in [items] (via [item]).
 * Content items come from [dataSource] + [itemRenderer] and are rendered
 * into [contentSlots], overlaying anything in those slots from [items].
 *
 * All [SimpleMenu] features are inherited: [populateItems], [asyncData],
 * [menuState], [onOpen], [onClose], etc.
 */
open class PaginatedMenu<T> : SimpleMenu() {
    init {
        rows = 6
    }

    /**
     * Title provider - receives current page number (1-based) and total pages.
     * If set, overrides [title] and is recalculated on each refresh.
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
     * Content from [dataSource] is rendered into these slots, overlaying
     * any items placed here via [item].
     */
    var contentSlots: List<Int> = (10..16) + (19..25) + (28..34) + (37..43)

    /**
     * Shown in ALL content slots while [isAsyncLoading] is true and [dataSource] is empty.
     */
    var loadingPlaceholder: VItem? = null

    /**
     * Shown centered in content slots when [dataSource] is empty after loading completes.
     */
    var emptyPlaceholder: VItem? = null

    // Navigation
    var autoNavigation: Boolean = true
    var previousPageSlot: Int = 45
    var previousPageItem: VItem = VItem(XMaterial.ARROW) {
        name = Component.text("Previous Page")
    }
    var nextPageSlot: Int = 53
    var nextPageItem: VItem = VItem(XMaterial.ARROW) {
        name = Component.text("Next Page")
    }
    var pageIndicatorSlot: Int = 49
    var pageIndicatorRenderer: ((Int, Int) -> VItem)? = null

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

    companion object {
        inline operator fun <T> invoke(builder: PaginatedMenu<T>.() -> Unit): PaginatedMenu<T> {
            return PaginatedMenu<T>().apply(builder)
        }
    }
}
