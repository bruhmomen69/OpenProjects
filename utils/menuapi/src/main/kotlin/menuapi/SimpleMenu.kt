package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A simple menu with fixed slot positions.
 */
open class SimpleMenu : Menu {
    override var title: Component = Component.empty()
    var rows: Int = 3
    val items: MutableMap<Int, VItem> = mutableMapOf()
    override var background: VItem? = null

    /**
     * Called when the menu is opened.
     */
    var onOpen: ((Player, MenuControls<*>) -> Unit)? = null

    /**
     * Called when the menu is closed.
     */
    var onClose: ((Player, MenuControls<*>) -> Unit)? = null

    // ========================================================================
    // Async Data
    // ========================================================================

    internal val asyncDataConfigs: MutableList<AsyncDataConfig<*>> = mutableListOf()

    /**
     * Whether any async data loaders are currently loading.
     * Check this in [populateItems] to show loading placeholders.
     */
    var isAsyncLoading: Boolean = false
        internal set

    /**
     * Register an async data source that loads data off the main thread.
     * The framework starts all registered loaders after the menu is opened.
     *
     * ```kotlin
     * asyncData<List<Auction>> {
     *     load { auctionService.getActiveAuctions() }
     *     onLoaded { auctions -> dataSource = auctions }
     * }
     * ```
     */
    protected fun <T> asyncData(block: AsyncDataDsl<T>.() -> Unit) {
        asyncDataConfigs.add(AsyncDataDsl<T>().apply(block).build())
    }

    // ========================================================================
    // Observable State (menuState delegate)
    // ========================================================================

    internal var boundControls: MenuControlsImpl<*>? = null
    internal var isPopulating: Boolean = false
    private var refreshScheduled: Boolean = false

    /**
     * Kotlin property delegate for observable menu state.
     * When the property value changes and the menu is open, automatically
     * schedules a refresh (via [populateItems] + re-render).
     *
     * Changes are batched: multiple property changes within the same tick
     * result in a single refresh.
     *
     * During [populateItems] and init, changes do NOT trigger refresh.
     *
     * ```kotlin
     * private var filter by menuState(AuctionFilter.ALL)
     * private var sortBy by menuState(SortType.NEWEST)
     *
     * // In click handler:
     * onClick { _, _ ->
     *     filter = filter.next()  // auto-schedules refresh
     *     ClickResult.Deny
     * }
     * ```
     */
    protected fun <T> menuState(initial: T): ReadWriteProperty<Any?, T> {
        return MenuStateDelegate(initial, this)
    }

    internal fun scheduleRefresh() {
        if (isPopulating) return
        val ctrl = boundControls ?: return
        if (refreshScheduled) return
        refreshScheduled = true
        Bukkit.getScheduler().runTask(ctrl.holder.menuApi.plugin, Runnable {
            refreshScheduled = false
            if (ctrl.isOpen()) ctrl.refresh()
        })
    }

    // ========================================================================
    // Item Helpers
    // ========================================================================

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

/**
 * Property delegate that schedules a menu refresh when the value changes.
 */
private class MenuStateDelegate<T>(
    private var value: T,
    private val menu: SimpleMenu
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (this.value != value) {
            this.value = value
            menu.scheduleRefresh()
        }
    }
}
