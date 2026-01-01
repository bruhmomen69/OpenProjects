package bruh.zchat.utils.menuapi.configurable

import bruh.zchat.utils.menuapi.configurable.config.ItemConfig
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * InventoryHolder implementation for configurable menus.
 * Tracks menu state including items and drag slots.
 *
 * @param A The action enum type for this menu
 */
internal class ConfigurableMenuHolder<A : Enum<A>>(
    val menuApi: ConfigurableMenuAPI,
    val menu: ConfigurableMenu<A>,
    val player: Player
) : InventoryHolder {
    private lateinit var _inventory: Inventory

    /**
     * Maps slot numbers to their configured items (for click handling).
     */
    val slotItems: MutableMap<Int, ItemConfig> = mutableMapOf()

    /**
     * Maps slot numbers to their drag slot enum names.
     */
    val dragSlotsByPosition: MutableMap<Int, String> = mutableMapOf()

    /**
     * Maps drag slot enum names to their slot positions.
     */
    val dragSlotPositions: MutableMap<String, Int> = mutableMapOf()

    /**
     * The menu instance exposed to action handlers.
     */
    val instance: ConfigurableMenuInstance<A> by lazy {
        ConfigurableMenuInstance(this)
    }

    override fun getInventory(): Inventory = _inventory

    fun setInventory(inventory: Inventory) {
        _inventory = inventory
    }
}
