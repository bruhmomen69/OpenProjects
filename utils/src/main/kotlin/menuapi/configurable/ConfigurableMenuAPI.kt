package bruh.zchat.utils.menuapi.configurable

import bruh.zchat.utils.configapi.TypedConfigLoader
import bruh.zchat.utils.menuapi.ClickContext
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.configurable.config.MenuConfig
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
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
import java.io.Closeable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Main API for configurable menus.
 * Manages menu registration, config loading, and event handling.
 *
 * Usage:
 * ```kotlin
 * val menuApi = ConfigurableMenuAPI(plugin)
 *
 * // Register menus
 * menuApi.register(MyShopMenu(menuApi))
 *
 * // On disable
 * menuApi.close()
 * ```
 *
 * @param plugin The JavaPlugin instance
 * @param menusDirectory The directory for menu config files (defaults to plugin/menus/)
 */
class ConfigurableMenuAPI(
    val plugin: JavaPlugin,
    val menusDirectory: Path = plugin.dataFolder.toPath().resolve("menus")
) : Closeable, AutoCloseable {
    private val listener = MenuListener()
    private val openMenus: MutableMap<UUID, ConfigurableMenuHolder<*>> = ConcurrentHashMap()
    private val registeredMenus: MutableList<ConfigurableMenu<*>> = mutableListOf()

    init {
        Bukkit.getPluginManager().registerEvents(listener, plugin)
    }

    /**
     * Registers a configurable menu, loading its config file.
     *
     * @param menu The menu to register
     * @return The registered menu (for chaining)
     */
    fun <M : ConfigurableMenu<*>> register(menu: M): M {
        registeredMenus.add(menu)
        runBlocking { menu.loadConfig() }
        return menu
    }

    /**
     * Reloads all registered menu configurations.
     */
    fun reloadAll() {
        runBlocking {
            registeredMenus.forEach { it.loadConfig() }
        }
    }

    /**
     * Gets the config path for a menu by name.
     */
    fun getConfigPath(configName: String): Path {
        return menusDirectory.resolve("$configName.conf")
    }

    /**
     * Creates a config loader for a menu.
     */
    internal fun createConfigLoader(configName: String): TypedConfigLoader<MenuConfig> {
        return TypedConfigLoader.create(
            configPath = getConfigPath(configName),
            defaultFactory = { MenuConfig() }
        )
    }

    /**
     * Opens a menu for a player.
     */
    internal fun <A : Enum<A>> openMenu(
        menu: ConfigurableMenu<A>,
        player: Player
    ): ConfigurableMenuInstance<A> {
        // Close any existing menu
        openMenus[player.uniqueId]?.let {
            player.closeInventory()
        }

        val holder = ConfigurableMenuHolder(this, menu, player)
        val config = menu.config

        val inventory = Bukkit.createInventory(
            holder,
            config.size,
            config.parsedTitle()
        )
        holder.setInventory(inventory)

        populateInventory(holder, config)

        openMenus[player.uniqueId] = holder
        player.openInventory(inventory)

        return holder.instance
    }

    /**
     * Populates an inventory with items from config.
     */
    private fun <A : Enum<A>> populateInventory(
        holder: ConfigurableMenuHolder<A>,
        config: MenuConfig
    ) {
        val inventory = holder.inventory
        holder.slotItems.clear()
        holder.dragSlotsByPosition.clear()
        holder.dragSlotPositions.clear()

        // Fill with background
        config.background?.let { bg ->
            val bgItem = bg.toItemConfig().buildItemStack()
            for (i in 0 until inventory.size) {
                inventory.setItem(i, bgItem)
            }
        }

        // Place configured items
        config.items.forEach { (_, itemConfig) ->
            val slot = itemConfig.slot
            if (slot in 0 until inventory.size) {
                holder.slotItems[slot] = itemConfig
                inventory.setItem(slot, itemConfig.buildItemStack())
            }
        }

        // Place drag slots
        config.dragSlots.forEach { (slotName, dragSlotConfig) ->
            val slot = dragSlotConfig.slot
            if (slot in 0 until inventory.size) {
                holder.dragSlotsByPosition[slot] = slotName
                holder.dragSlotPositions[slotName] = slot

                // Place default item if configured
                dragSlotConfig.defaultItem?.let { defaultItem ->
                    inventory.setItem(slot, defaultItem.buildItemStack())
                } ?: inventory.setItem(slot, null)
            }
        }
    }

    /**
     * Refreshes a menu holder.
     */
    internal fun <A : Enum<A>> refreshMenu(holder: ConfigurableMenuHolder<A>) {
        holder.inventory.clear()
        populateInventory(holder, holder.menu.config)
    }

    /**
     * Closes all menus and unregisters listeners.
     */
    override fun close() {
        HandlerList.unregisterAll(listener)

        openMenus.values.toList().forEach { holder ->
            holder.player.closeInventory()
        }
        openMenus.clear()
        registeredMenus.clear()
    }

    /**
     * Event listener for configurable menus.
     */
    private inner class MenuListener : Listener {

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryClick(event: InventoryClickEvent) {
            val holder = event.inventory.holder as? ConfigurableMenuHolder<*> ?: return
            if (holder.menuApi !== this@ConfigurableMenuAPI) return

            val player = event.whoClicked as? Player ?: return
            val slot = event.rawSlot

            // Clicked outside the menu inventory
            if (slot < 0 || slot >= event.inventory.size) {
                // Handle shift-clicking from player inventory into drag slots
                if (event.isShiftClick) {
                    val dragSlots = holder.dragSlotsByPosition
                    if (dragSlots.isNotEmpty()) {
                        // Find an empty drag slot
                        val emptySlot = dragSlots.entries.firstOrNull { (slotNum, _) ->
                            event.inventory.getItem(slotNum)?.type?.isAir != false
                        }

                        if (emptySlot != null) {
                            val item = event.currentItem ?: return
                            val slotName = emptySlot.value
                            
                            // Validate using config validator
                            if (!validateDragSlotItem(holder, slotName, item)) {
                                event.isCancelled = true
                                return
                            }
                        } else {
                            event.isCancelled = true
                        }
                    } else {
                        event.isCancelled = true
                    }
                }
                return
            }

            // Check if this is a drag slot
            val dragSlotName = holder.dragSlotsByPosition[slot]
            if (dragSlotName != null) {
                val currentItem = event.inventory.getItem(slot)
                val cursorItem = event.cursor

                // Placing an item
                if (cursorItem != null && !cursorItem.type.isAir) {
                    if (!validateDragSlotItem(holder, dragSlotName, cursorItem)) {
                        event.isCancelled = true
                        return
                    }
                }
                // Allow drag slot interactions
                return
            }

            // Regular item click - cancel by default
            event.isCancelled = true

            val itemConfig = holder.slotItems[slot] ?: return

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

            // Handle action if present
            val actionName = itemConfig.action
            if (actionName != null) {
                @Suppress("UNCHECKED_CAST")
                val result = (holder.menu as ConfigurableMenu<Any>).handleAction(
                    actionName,
                    context,
                    holder.instance as ConfigurableMenuInstance<Any>
                )

                when (result) {
                    ClickResult.ALLOW -> event.isCancelled = false
                    ClickResult.DENY -> {} // Already cancelled
                    ClickResult.CLOSE -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable { player.closeInventory() })
                    }
                    ClickResult.REFRESH -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable { refreshMenu(holder) })
                    }
                }
            }
        }

        private fun validateDragSlotItem(
            holder: ConfigurableMenuHolder<*>,
            slotName: String,
            item: org.bukkit.inventory.ItemStack
        ): Boolean {
            val config = holder.menu.config
            val dragSlotConfig = config.dragSlots[slotName] ?: return true

            // Check config validator
            dragSlotConfig.validator?.let { validator ->
                if (!validator.validate(item)) return false
            }

            // Check code validators
            @Suppress("UNCHECKED_CAST")
            val menu = holder.menu as ConfigurableMenu<Any>
            val additionalValidator = menu.getSlotValidator(slotName)
            if (additionalValidator != null && !additionalValidator(item)) {
                return false
            }

            return true
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryDrag(event: InventoryDragEvent) {
            val holder = event.inventory.holder as? ConfigurableMenuHolder<*> ?: return
            if (holder.menuApi !== this@ConfigurableMenuAPI) return

            // Check if any dragged slots are in the menu
            val menuSlots = event.rawSlots.filter { it < event.inventory.size }

            if (menuSlots.isEmpty()) return

            // Only allow dragging into drag slots
            val allDragSlots = menuSlots.all { slot ->
                holder.dragSlotsByPosition.containsKey(slot)
            }

            if (!allDragSlots) {
                event.isCancelled = true
                return
            }

            // Validate each drag slot
            for (slot in menuSlots) {
                val slotName = holder.dragSlotsByPosition[slot] ?: continue
                val item = event.newItems[slot] ?: continue
                if (!validateDragSlotItem(holder, slotName, item)) {
                    event.isCancelled = true
                    return
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onInventoryClose(event: InventoryCloseEvent) {
            val holder = event.inventory.holder as? ConfigurableMenuHolder<*> ?: return
            if (holder.menuApi !== this@ConfigurableMenuAPI) return

            val player = event.player as? Player ?: return
            openMenus.remove(player.uniqueId)

            // Call onClose if defined
            @Suppress("UNCHECKED_CAST")
            (holder.menu as ConfigurableMenu<Any>).handleClose(
                player,
                holder.instance as ConfigurableMenuInstance<Any>
            )
        }
    }
}
