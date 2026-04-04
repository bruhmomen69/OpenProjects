package bruh.zchat.utils.menuapi

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.Closeable
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val generationCounter = AtomicLong(0)

    init {
        Bukkit.getPluginManager().registerEvents(listener, plugin)
    }

    // ========================================================================
    // Menu Builders
    // ========================================================================

    /**
     * Create a simple menu with fixed slot positions.
     * Items set in [builder] are captured and replayed via [Menu.populateItems].
     */
    inline fun simple(builder: SimpleMenu.() -> Unit): SimpleMenu {
        val menu = BuilderSimpleMenu()
        menu.apply(builder)
        menu.builderItems.putAll(menu.items)
        return menu
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
     * Chrome items set in [builder] (via [SimpleMenu.item]) are captured
     * and replayed via [Menu.populateItems].
     */
    inline fun <T> paginated(builder: PaginatedMenu<T>.() -> Unit): PaginatedMenu<T> {
        val menu = BuilderPaginatedMenu<T>()
        menu.apply(builder)
        menu.builderItems.putAll(menu.items)
        return menu
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

        val holder = MenuHolder(this, menu, player, generationCounter.incrementAndGet())

        // Bind controls for menuState delegates
        if (menu is SimpleMenu) {
            menu.boundControls = holder.controls
            // Pre-set loading flag so first populateItems() sees it
            if (menu.asyncDataConfigs.isNotEmpty()) {
                menu.isAsyncLoading = true
            }
        }

        // Build items from state before inventory creation
        menu.populateItems()

        val inventory = createInventory(holder, menu)
        holder.setInventory(inventory)

        populateInventory(holder)

        openMenus[player.uniqueId] = holder
        player.openInventory(inventory)

        // Start async data loaders after inventory is visible
        startAsyncLoaders(holder)

        // Call onOpen callback
        when {
            menu is SimpleMenu -> menu.onOpen?.invoke(player, holder.controls)
            menu is DynamicMenu -> menu.onOpen?.invoke(player, holder.controls as MenuControls<DynamicMenu>)
            menu is ItemMenu -> menu.onOpen?.invoke(player, holder.controls as MenuControls<ItemMenu>)
            menu is ConfirmationMenu -> {} // No onOpen for confirmation menus
        }

        return holder.controls
    }

    internal fun isHolderOpen(holder: MenuHolder<*>): Boolean {
        val currentHolder = openMenus[holder.player.uniqueId] ?: return false
        if (currentHolder !== holder) return false
        return holder.player.openInventory.topInventory.holder === holder
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
            is SimpleMenu -> menu.size // PaginatedMenu inherits from SimpleMenu
            is DynamicMenu -> menu.rows * 9
            is ItemMenu -> menu.size
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

        // Then populate based on menu type.
        // PaginatedMenu extends SimpleMenu, so check it BEFORE SimpleMenu.
        when {
            menu is PaginatedMenu<*> -> {
                val paginatedMenu = menu as PaginatedMenu<Any>

                // Chrome items (inherited from SimpleMenu, built by populateItems())
                paginatedMenu.items.forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }

                // Content slots: data items OR placeholders
                val pageItems = paginatedMenu.getPageItems(holder.currentPage)
                if (pageItems.isEmpty()) {
                    if (paginatedMenu.isAsyncLoading && paginatedMenu.loadingPlaceholder != null) {
                        // Fill all content slots with loading placeholder
                        for (slot in paginatedMenu.contentSlots) {
                            holder.slotItems[slot] = paginatedMenu.loadingPlaceholder!!
                            inventory.setItem(slot, paginatedMenu.loadingPlaceholder!!.build())
                        }
                    } else if (!paginatedMenu.isAsyncLoading && paginatedMenu.emptyPlaceholder != null) {
                        // Show empty placeholder centered in content area
                        val centerSlot = paginatedMenu.contentSlots[paginatedMenu.contentSlots.size / 2]
                        holder.slotItems[centerSlot] = paginatedMenu.emptyPlaceholder!!
                        inventory.setItem(centerSlot, paginatedMenu.emptyPlaceholder!!.build())
                    }
                } else {
                    // Render data items into content slots (overlays chrome items in those slots)
                    pageItems.forEach { (slot, vItem) ->
                        holder.slotItems[slot] = vItem
                        inventory.setItem(slot, vItem.build())
                    }
                }

                // Auto-navigation buttons
                if (paginatedMenu.autoNavigation) {
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

                    paginatedMenu.pageIndicatorRenderer?.let { renderer ->
                        val indicator = renderer(holder.currentPage + 1, paginatedMenu.pageCount)
                        holder.slotItems[paginatedMenu.pageIndicatorSlot] = indicator
                        inventory.setItem(paginatedMenu.pageIndicatorSlot, indicator.build())
                    }
                }
            }

            menu is SimpleMenu -> {
                menu.items.forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }
            }

            menu is DynamicMenu -> {
                menu.calculateSlots().forEach { (slot, vItem) ->
                    holder.slotItems[slot] = vItem
                    inventory.setItem(slot, vItem.build())
                }
            }

            menu is ItemMenu -> {
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

            menu is ConfirmationMenu -> {
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

        // Rebuild items from state via populateItems()
        if (menu is SimpleMenu) {
            menu.isPopulating = true
            menu.populateItems()
            menu.isPopulating = false
        } else {
            menu.populateItems()
        }

        holder.inventory.clear()
        populateInventory(holder)
    }

    @Suppress("UNCHECKED_CAST")
    private fun startAsyncLoaders(holder: MenuHolder<*>) {
        val menu = holder.menu
        if (menu !is SimpleMenu) return // PaginatedMenu is a SimpleMenu, caught here too
        val configs = menu.asyncDataConfigs
        if (configs.isEmpty()) return

        // isAsyncLoading was already set in open() before populateItems()

        for (config in configs) {
            val typedConfig = config as AsyncDataConfig<Any?>
            val handle = bindAsyncData(
                controls = holder.controls,
                source = AsyncMenuDataSource { _ ->
                    CompletableFuture.supplyAsync {
                        kotlinx.coroutines.runBlocking { typedConfig.loader() }
                    }
                },
                policy = AsyncDataPolicy(staleAfter = config.staleAfter, eagerLoadOnBind = false),
                onData = { data, controls ->
                    typedConfig.onLoaded(data)
                    checkAsyncLoadingComplete(holder)
                    controls.refresh()
                },
                onStateChange = { state, controls ->
                    if (state is AsyncMenuState.Error) {
                        typedConfig.onError?.invoke(state.cause)
                            ?: plugin.slF4JLogger.warn(
                                "Async load failed for ${holder.player.name}", state.cause
                            )
                        checkAsyncLoadingComplete(holder)
                        controls.refresh()
                    }
                }
            )
            holder.asyncHandles.add(handle)
        }

        // Start all loaders after all handles are registered (avoids race conditions)
        for (handle in holder.asyncHandles) {
            handle.refresh()
        }
    }

    private fun checkAsyncLoadingComplete(holder: MenuHolder<*>) {
        val menu = holder.menu as? SimpleMenu ?: return
        val allDone = holder.asyncHandles.all {
            it.state is AsyncMenuState.Ready || it.state is AsyncMenuState.Error
        }
        if (allDone) {
            menu.isAsyncLoading = false
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

    /** Tracks drop source info for correlating InventoryClickEvent with PlayerDropItemEvent */
    private data class DropSource(val slot: Int, val isControlDrop: Boolean)

    private inner class MenuListener : Listener {

        private val lastDropSources: MutableMap<UUID, DropSource> = ConcurrentHashMap()

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

            // Track drop source slot and whether it was a control-drop (Ctrl+Q)
            // for any drop within the menu inventory (top inventory).
            if (context.isDrop && slot in 0 until event.inventory.size) {
                lastDropSources[player.uniqueId] = DropSource(slot, context.clickType == ClickType.CONTROL_DROP)
            } else if (!context.isDrop) {
                lastDropSources.remove(player.uniqueId)
            }

            // Handle click callback
            vItem?.clickHandler?.let { handler ->
                val result = handler(context, holder.controls)

                when (result) {
                    ClickResult.Allow -> event.isCancelled = false
                    ClickResult.Deny -> event.isCancelled = true
                    ClickResult.Close -> {
                        event.isCancelled = true
                        val sourceHolder = holder
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (openMenus[player.uniqueId] === sourceHolder) {
                                player.closeInventory()
                            }
                        })
                    }
                    ClickResult.Refresh -> {
                        event.isCancelled = true
                        val sourceHolder = holder
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (openMenus[player.uniqueId] === sourceHolder) {
                                refreshMenu(sourceHolder)
                            }
                        })
                    }
                    is ClickResult.SwitchMenu -> {
                        event.isCancelled = true
                        if (Bukkit.isPrimaryThread()) {
                            open(result.menu, player)
                        } else {
                            Bukkit.getScheduler().runTask(plugin, Runnable { open(result.menu, player) })
                        }
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
            if (openMenus[player.uniqueId] === holder) {
                openMenus.remove(player.uniqueId)
            }

            // Call onClose callback
            // PaginatedMenu extends SimpleMenu — its onClose is inherited, so
            // the SimpleMenu branch handles both.
            when (val menu = holder.menu) {
                is SimpleMenu -> menu.onClose?.invoke(player, holder.controls)
                is DynamicMenu -> menu.onClose?.invoke(player, holder.controls as MenuControls<DynamicMenu>)
                is ItemMenu -> menu.onClose?.invoke(player, holder.controls as MenuControls<ItemMenu>)
                is ConfirmationMenu -> {}
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onPlayerDropItem(event: PlayerDropItemEvent) {
            val player = event.player
            val holder = openMenus[player.uniqueId] ?: return
            if (holder.menuApi !== this@MenuAPI) return

            val inventory = holder.inventory
            val droppedItem = event.itemDrop.itemStack

            // Use the last recorded drop source from the click event
            val dropSource = lastDropSources.remove(player.uniqueId) ?: return
            val sourceSlot = dropSource.slot
            val sourceVItem = holder.slotItems[sourceSlot] ?: return

            // No drop handler or not a tracked item
            if (sourceVItem.dropHandler == null && sourceVItem.dropListener == null) return

            val isControlDrop = dropSource.isControlDrop

            val context = DropContext(
                player = player,
                slot = sourceSlot,
                itemStack = droppedItem,
                inventory = inventory,
                isControlDrop = isControlDrop
            )

            // Handle drop callback
            sourceVItem.dropHandler?.let { handler ->
                val result = handler(context, holder.controls)

                when (result) {
                    DropResult.ALLOW -> {}
                    DropResult.DENY -> event.isCancelled = true
                    DropResult.CLOSE -> {
                        event.isCancelled = true
                        Bukkit.getScheduler().runTask(plugin, Runnable { player.closeInventory() })
                    }
                }
            }

            sourceVItem.dropListener?.let { listener ->
                listener(context, holder.controls)
            }
        }
    }
}
