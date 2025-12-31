package bruh.zchat.utils.menuapi

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Core class for the Menu API. Manages all menu state and event handling.
 *
 * Usage:
 * ```kotlin
 * val menuApi = MenuAPI(plugin)
 *
 * val menu = menuApi.simple {
 *     title = Component.text("My Menu")
 *     rows = 3
 *
 *     item(13, XMaterial.DIAMOND) {
 *         name = Component.text("Click me!")
 *         onClickRun { ctx, _ ->
 *             ctx.player.sendMessage("You clicked!")
 *         }
 *     }
 * }
 *
 * menuApi.open(menu, player)
 * ```
 */
class MenuAPI(val plugin: JavaPlugin) : Closeable, AutoCloseable {
    private val listener = MenuListener()
    private val openMenus: MutableMap<UUID, MenuHolder<*>> = ConcurrentHashMap()
    private val scheduledTasks: MutableList<BukkitTask> = mutableListOf()

    /** NamespacedKey for marking menu items */
    val menuItemKey: NamespacedKey = NamespacedKey(plugin, "menu_item")

    init {
        Bukkit.getPluginManager().registerEvents(listener, plugin)
    }

    // ========================================================================
    // Menu Builders
    // ========================================================================

    /**
     * Create a simple menu with fixed slot positions.
     */
    inline fun simple(builder: SimpleMenu.() -> Unit): SimpleMenu {
        return SimpleMenu().apply(builder)
    }

    /**
     * Create a dynamic menu with auto-positioning.
     */
    inline fun dynamic(builder: DynamicMenu.() -> Unit): DynamicMenu {
        return DynamicMenu().apply(builder)
    }

    /**
     * Create an item menu with drag-and-drop support.
     */
    inline fun item(builder: ItemMenu.() -> Unit): ItemMenu {
        return ItemMenu().apply(builder)
    }

    /**
     * Create a paginated menu.
     */
    inline fun <T> paginated(builder: PaginatedMenu<T>.() -> Unit): PaginatedMenu<T> {
        return PaginatedMenu<T>().apply(builder)
    }

    /**
     * Create a confirmation menu.
     */
    inline fun confirm(builder: ConfirmationMenu.() -> Unit): ConfirmationMenu {
        return ConfirmationMenu().apply(builder)
    }

    // ========================================================================
    // Menu Opening
    // ========================================================================

    /**
     * Open a menu for a player.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Menu> open(menu: T, player: Player): MenuControls<T> {
        // Close any existing menu
        openMenus[player.uniqueId]?.let {
            player.closeInventory()
        }

        val holder = MenuHolder(this, menu, player)
        val inventory = createInventory(holder, menu)
        holder.setInventory(inventory)

        populateInventory(holder)

        openMenus[player.uniqueId] = holder
        player.openInventory(inventory)

        // Call onOpen callback
        when (menu) {
            is SimpleMenu -> menu.onOpen?.invoke(player, holder.controls as MenuControls<SimpleMenu>)
            is DynamicMenu -> menu.onOpen?.invoke(player, holder.controls as MenuControls<DynamicMenu>)
            is ItemMenu -> menu.onOpen?.invoke(player, holder.controls as MenuControls<ItemMenu>)
            is PaginatedMenu<*> -> (menu as PaginatedMenu<Any>).onOpen?.invoke(
                player, holder.controls as MenuControls<PaginatedMenu<Any>>
            )
            is ConfirmationMenu -> {} // No onOpen for confirmation menus
        }

        return holder.controls
    }

    /**
     * Close a menu for a specific player.
     */
    fun close(player: Player) {
        openMenus[player.uniqueId]?.let {
            player.closeInventory()
        }
    }

    /**
     * Close all menus for a specific menu instance.
     */
    fun closeAll(menu: Menu) {
        openMenus.values
            .filter { it.menu === menu }
            .forEach { it.player.closeInventory() }
    }

    /**
     * Get the controls for a player's currently open menu.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Menu> getControls(player: Player): MenuControls<T>? {
        return openMenus[player.uniqueId]?.controls as? MenuControls<T>
    }

    /**
     * Check if a player has a menu open.
     */
    fun hasMenuOpen(player: Player): Boolean {
        return openMenus.containsKey(player.uniqueId)
    }

    // ========================================================================
    // Internal Methods
    // ========================================================================

    private fun createInventory(holder: MenuHolder<*>, menu: Menu): Inventory {
        val title = when (menu) {
            is PaginatedMenu<*> -> menu.titleProvider?.invoke(holder.currentPage + 1, menu.pageCount) ?: menu.title
            else -> menu.title
        }

        val size = when (menu) {
            is SimpleMenu -> menu.size
            is DynamicMenu -> menu.rows * 9
            is ItemMenu -> menu.size
            is PaginatedMenu<*> -> menu.size
            is ConfirmationMenu -> menu.rows * 9
        }

        return Bukkit.createInventory(holder, size, title)
    }

    @Suppress("UNCHECKED_CAST")
    private fun populateInventory(holder: MenuHolder<*>) {
        val inventory = holder.inventory
        val menu = holder.menu
        holder.slotItems.clear()
        holder.dragSlots.clear()

        // First, fill with background if present
        menu.background?.let { bg ->
            val bgItem = bg.build()
            for (i in 0 until inventory.size) {
                inventory.setItem(i, bgItem)
            }
        }

        // Then populate based on menu type
        when (menu) {
            is SimpleMenu -> {
                menu.items.forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }
            }

            is DynamicMenu -> {
                menu.calculateSlots().forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }
            }

            is ItemMenu -> {
                menu.items.forEach { (slot, slottable) ->
                    when (slottable) {
                        is VItem -> {
                            holder.slotItems[slot] = slottable
                            inventory.setItem(slot, slottable.build())
                        }
                        is DragItem -> {
                            holder.dragSlots[slot] = slottable
                            slottable.defaultItem?.let { defaultItem ->
                                inventory.setItem(slot, defaultItem.build())
                            } ?: inventory.setItem(slot, null)
                        }
                    }
                }
            }

            is PaginatedMenu<*> -> {
                val paginatedMenu = menu as PaginatedMenu<Any>

                // Static items
                paginatedMenu.staticItems.forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }

                // Page items
                paginatedMenu.getPageItems(holder.currentPage).forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }

                // Navigation buttons
                if (holder.currentPage > 0) {
                    val prevItem = paginatedMenu.previousPageItem.copy().apply {
                        onClickDeny { _, controls -> controls.previousPage() }
                    }
                    holder.slotItems[paginatedMenu.previousPageSlot] = prevItem
                    inventory.setItem(paginatedMenu.previousPageSlot, prevItem.build())
                }

                if (holder.currentPage < paginatedMenu.pageCount - 1) {
                    val nextItem = paginatedMenu.nextPageItem.copy().apply {
                        onClickDeny { _, controls -> controls.nextPage() }
                    }
                    holder.slotItems[paginatedMenu.nextPageSlot] = nextItem
                    inventory.setItem(paginatedMenu.nextPageSlot, nextItem.build())
                }

                // Page indicator
                paginatedMenu.pageIndicatorRenderer?.let { renderer ->
                    val indicator = renderer(holder.currentPage + 1, paginatedMenu.pageCount)
                    holder.slotItems[paginatedMenu.pageIndicatorSlot] = indicator
                    inventory.setItem(paginatedMenu.pageIndicatorSlot, indicator.build())
                }
            }

            is ConfirmationMenu -> {
                // Confirm button
                val confirmItem = menu.confirmItem.copy().apply {
                    onClickClose { ctx, _ -> menu.onConfirm?.invoke(ctx.player) }
                }
                holder.slotItems[menu.confirmSlot] = confirmItem
                inventory.setItem(menu.confirmSlot, confirmItem.build())

                // Cancel button
                val cancelItem = menu.cancelItem.copy().apply {
                    onClickClose { ctx, _ -> menu.onCancel?.invoke(ctx.player) }
                }
                holder.slotItems[menu.cancelSlot] = cancelItem
                inventory.setItem(menu.cancelSlot, cancelItem.build())

                // Info item
                menu.infoItem?.let { info ->
                    holder.slotItems[menu.infoSlot] = info
                    inventory.setItem(menu.infoSlot, info.build())
                }
            }
        }
    }

    internal fun refreshMenu(holder: MenuHolder<*>) {
        val menu = holder.menu

        // Update title for paginated menus
        if (menu is PaginatedMenu<*>) {
            val newTitle = menu.titleProvider?.invoke(holder.currentPage + 1, menu.pageCount) ?: menu.title
            // Clear and repopulate
            holder.inventory.clear()
            populateInventory(holder)
        } else {
            holder.inventory.clear()
            populateInventory(holder)
        }
    }

    // ========================================================================
    // Scheduled Updates
    // ========================================================================

    /**
     * Schedule a repeating task to update menus.
     */
    fun scheduleUpdate(periodTicks: Long, update: (MenuControls<*>) -> Unit): BukkitTask {
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            openMenus.values.forEach { holder ->
                try {
                    update(holder.controls)
                } catch (e: Exception) {
                    plugin.slF4JLogger.warn("Error updating menu for ${holder.player.name}", e)
                }
            }
        }, periodTicks, periodTicks)
        scheduledTasks.add(task)
        return task
    }

    // ========================================================================
    // Cleanup
    // ========================================================================

    override fun close() {
        // Unregister listener
        HandlerList.unregisterAll(listener)

        // Cancel scheduled tasks
        scheduledTasks.forEach { it.cancel() }
        scheduledTasks.clear()

        // Close all open menus
        openMenus.values.toList().forEach { holder ->
            holder.player.closeInventory()
        }
        openMenus.clear()
    }

    // ========================================================================
    // Event Listener
    // ========================================================================

    private inner class MenuListener : Listener {

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryClick(event: InventoryClickEvent) {
            val holder = event.inventory.holder as? MenuHolder<*> ?: return
            if (holder.menuApi !== this@MenuAPI) return

            val player = event.whoClicked as? Player ?: return
            val slot = event.rawSlot

            // Clicked outside the menu inventory
            if (slot < 0 || slot >= event.inventory.size) {
                // For ItemMenu, we might want to handle shift-clicking from player inventory
                if (holder.menu is ItemMenu && event.isShiftClick) {
                    val dragSlots = holder.dragSlots
                    if (dragSlots.isNotEmpty()) {
                        // Find an empty drag slot
                        val emptySlot = dragSlots.entries.firstOrNull { (slotNum, _) ->
                            event.inventory.getItem(slotNum)?.type?.isAir != false
                        }

                        if (emptySlot != null) {
                            val dragItem = emptySlot.value
                            val item = event.currentItem ?: return

                            // Validate
                            if (dragItem.itemValidator?.invoke(item) == false) {
                                event.isCancelled = true
                                return
                            }

                            // Allow placement
                            if (dragItem.onItemPlace?.invoke(player, item, holder.controls) == false) {
                                event.isCancelled = true
                                return
                            }
                        } else {
                            event.isCancelled = true
                        }
                    } else {
                        event.isCancelled = true
                    }
                } else {
                    // Prevent shift-clicking items into menu
                    if (event.isShiftClick) {
                        event.isCancelled = true
                    }
                }
                return
            }

            // Check if this is a drag slot
            val dragItem = holder.dragSlots[slot]
            if (dragItem != null) {
                val currentItem = event.inventory.getItem(slot)
                val cursorItem = event.cursor

                when {
                    // Placing an item
                    cursorItem != null && !cursorItem.type.isAir -> {
                        if (dragItem.itemValidator?.invoke(cursorItem) == false) {
                            event.isCancelled = true
                            return
                        }
                        if (dragItem.onItemPlace?.invoke(player, cursorItem, holder.controls) == false) {
                            event.isCancelled = true
                            return
                        }
                        // Allow default behavior, notify change
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            dragItem.onSlotChange?.invoke(player, event.inventory.getItem(slot), holder.controls)
                        })
                    }
                    // Removing an item
                    currentItem != null && !currentItem.type.isAir -> {
                        if (dragItem.onItemRemove?.invoke(player, currentItem, holder.controls) == false) {
                            event.isCancelled = true
                            return
                        }
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            dragItem.onSlotChange?.invoke(player, event.inventory.getItem(slot), holder.controls)
                        })
                    }
                }
                return
            }

            // Regular item click
            val vItem = holder.slotItems[slot]

            // Cancel by default for non-moveable items
            if (vItem == null || !vItem.canBeMovedByPlayer) {
                event.isCancelled = true
            }

            val context = ClickContext(
                player = player,
                slot = slot,
                clickType = event.click,
                inventory = event.inventory,
                itemStack = event.currentItem,
                isShiftClick = event.isShiftClick,
                isRightClick = event.isRightClick,
                isLeftClick = event.isLeftClick,
                hotbarButton = event.hotbarButton
            )

            // Handle click callback
            vItem?.clickHandler?.let { handler ->
                val result = handler(context, holder.controls)

                when (result) {
                    ClickResult.ALLOW -> event.isCancelled = false
                    ClickResult.DENY -> event.isCancelled = true
                    ClickResult.CLOSE -> {
                        event.isCancelled = true
                        Bukkit.getScheduler().runTask(plugin, Runnable { player.closeInventory() })
                    }
                    ClickResult.REFRESH -> {
                        event.isCancelled = true
                        Bukkit.getScheduler().runTask(plugin, Runnable { refreshMenu(holder) })
                    }
                }
            }

            vItem?.clickListener?.let { listener ->
                listener(context, holder.controls)
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryDrag(event: InventoryDragEvent) {
            val holder = event.inventory.holder as? MenuHolder<*> ?: return
            if (holder.menuApi !== this@MenuAPI) return

            // Check if any dragged slots are in the menu
            val menuSlots = event.rawSlots.filter { it < event.inventory.size }

            if (menuSlots.isEmpty()) return

            // For ItemMenu, check if all slots are drag slots
            if (holder.menu is ItemMenu) {
                val allDragSlots = menuSlots.all { slot -> holder.dragSlots.containsKey(slot) }
                if (!allDragSlots) {
                    event.isCancelled = true
                    return
                }

                // Validate each slot
                val dragItems = event.newItems
                for (slot in menuSlots) {
                    val dragItem = holder.dragSlots[slot] ?: continue
                    val item = dragItems[slot] ?: continue
                    if (dragItem.itemValidator?.invoke(item) == false) {
                        event.isCancelled = true
                        return
                    }
                }
            } else {
                // Cancel dragging into regular menus
                event.isCancelled = true
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        @Suppress("UNCHECKED_CAST")
        fun onInventoryClose(event: InventoryCloseEvent) {
            val holder = event.inventory.holder as? MenuHolder<*> ?: return
            if (holder.menuApi !== this@MenuAPI) return

            val player = event.player as? Player ?: return
            openMenus.remove(player.uniqueId)

            // Call onClose callback
            when (val menu = holder.menu) {
                is SimpleMenu -> menu.onClose?.invoke(player, holder.controls as MenuControls<SimpleMenu>)
                is DynamicMenu -> menu.onClose?.invoke(player, holder.controls as MenuControls<DynamicMenu>)
                is ItemMenu -> menu.onClose?.invoke(player, holder.controls as MenuControls<ItemMenu>)
                is PaginatedMenu<*> -> (menu as PaginatedMenu<Any>).onClose?.invoke(
                    player, holder.controls as MenuControls<PaginatedMenu<Any>>
                )
                is ConfirmationMenu -> {}
            }
        }
    }
}