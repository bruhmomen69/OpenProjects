package bruh.zchat.utils.menuapi.configurable

import bruh.zchat.utils.menuapi.configurable.config.ItemConfig
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Represents an open instance of a configurable menu for a specific player.
 * Provides methods to interact with the menu and retrieve drag slot items.
 *
 * @param A The action enum type for this menu
 */
class ConfigurableMenuInstance<A : Enum<A>> internal constructor(
    private val holder: ConfigurableMenuHolder<A>
) {
    /**
     * The player viewing this menu instance.
     */
    val player: Player get() = holder.player

    /**
     * The menu definition.
     */
    val menu: ConfigurableMenu<A> get() = holder.menu

    /**
     * The underlying Bukkit inventory.
     */
    val inventory: Inventory get() = holder.inventory

    /**
     * Closes this menu for the player.
     */
    fun close() {
        player.closeInventory()
    }

    /**
     * Refreshes the menu, reloading all items from config.
     */
    fun refresh() {
        holder.menuApi.refreshMenu(holder)
    }

    /**
     * Gets the item from a drag slot by its enum value.
     *
     * @param S The slot enum type
     * @param slot The slot enum value
     * @return The ItemStack in the slot, or null if empty
     */
    fun <S : Enum<S>> getItem(slot: S): ItemStack? {
        val slotName = slot.name
        val position = holder.dragSlotPositions[slotName] ?: return null
        return inventory.getItem(position)?.takeIf { !it.type.isAir }
    }

    /**
     * Gets the item from a drag slot by its enum name.
     *
     * @param slotName The slot enum name as a string
     * @return The ItemStack in the slot, or null if empty
     */
    fun getItem(slotName: String): ItemStack? {
        val position = holder.dragSlotPositions[slotName] ?: return null
        return inventory.getItem(position)?.takeIf { !it.type.isAir }
    }

    /**
     * Gets all items from all drag slots.
     *
     * @return Map of slot enum names to their ItemStacks (null if empty)
     */
    fun getAllDragItems(): Map<String, ItemStack?> {
        return holder.dragSlotPositions.mapValues { (_, position) ->
            inventory.getItem(position)?.takeIf { !it.type.isAir }
        }
    }

    // ---------------------------------------------------------------------
    // Dynamic action registration
    // ---------------------------------------------------------------------

    /**
     * Registers or clears a click action for a specific inventory slot.
     *
     * This is primarily intended for menus that populate dynamic content
     * (e.g., player heads) at runtime using [setItemAt]. Since the
     * configurable menu click handler only processes slots that have an
     * associated [ItemConfig] with an `action`, this method wires those
     * dynamic slots into the click handling pipeline.
     *
     * - When [action] is non-null, a lightweight [ItemConfig] is stored in
     *   the underlying holder's `slotItems` map with `action = action.name`.
     * - When [action] is null, any existing entry for the slot is removed.
     */
    fun registerActionSlot(slot: Int, action: A?) {
        if (action == null) {
            holder.slotItems.remove(slot)
            return
        }

        val existing = holder.slotItems[slot]
        if (existing != null) {
            holder.slotItems[slot] = existing.copy(slot = slot, action = action.name)
        } else {
            holder.slotItems[slot] = ItemConfig(slot = slot, action = action.name)
        }
    }

    /**
     * Sets an item in a drag slot by its enum value.
     *
     * @param S The slot enum type
     * @param slot The slot enum value
     * @param item The ItemStack to set, or null to clear
     */
    fun <S : Enum<S>> setItem(slot: S, item: ItemStack?) {
        val slotName = slot.name
        val position = holder.dragSlotPositions[slotName] ?: return
        inventory.setItem(position, item)
    }

    /**
     * Sets an item at a specific inventory slot.
     *
     * @param slot The slot number
     * @param item The ItemStack to set
     */
    fun setItemAt(slot: Int, item: ItemStack?) {
        inventory.setItem(slot, item)
    }

    /**
     * Gets an item at a specific inventory slot.
     *
     * @param slot The slot number
     * @return The ItemStack in the slot
     */
    fun getItemAt(slot: Int): ItemStack? {
        return inventory.getItem(slot)
    }
}
