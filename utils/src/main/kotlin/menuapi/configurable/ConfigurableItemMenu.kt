package bruh.zchat.utils.menuapi.configurable

import bruh.zchat.utils.menuapi.ClickContext
import bruh.zchat.utils.menuapi.ClickResult
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

/**
 * A configurable menu with named drag slots for player item placement.
 * Extends [ConfigurableMenu] with slot enum support for type-safe slot access.
 *
 * Example usage:
 * ```kotlin
 * enum class TradeActions { CONFIRM, CANCEL }
 * enum class TradeSlots { MY_ITEM, THEIR_ITEM }
 *
 * class TradeMenu(menuApi: ConfigurableMenuAPI) : ConfigurableItemMenu<TradeActions, TradeSlots>(
 *     menuApi = menuApi,
 *     configName = "trade",
 *     actionClass = TradeActions::class,
 *     slotClass = TradeSlots::class
 * ) {
 *     override val actionHandlers = mapOf(
 *         TradeActions.CONFIRM to { ctx, instance ->
 *             val myItem = instance.getItem(TradeSlots.MY_ITEM)
 *             val theirItem = instance.getItem(TradeSlots.THEIR_ITEM)
 *             // Process trade...
 *             ClickResult.CLOSE
 *         },
 *         TradeActions.CANCEL to { _, instance ->
 *             instance.close()
 *             ClickResult.CLOSE
 *         }
 *     )
 *
 *     // Optional: Add code-based validators
 *     override val additionalSlotValidators = mapOf(
 *         TradeSlots.MY_ITEM to { item -> item.amount <= 32 }
 *     )
 * }
 * ```
 *
 * Config file structure for drag slots:
 * ```hocon
 * drag-slots {
 *     MY_ITEM {
 *         slot = 20
 *         default-item {
 *             material = "LIME_STAINED_GLASS_PANE"
 *             name = "<green>Place your item here</green>"
 *         }
 *         validator {
 *             allowed-materials = ["DIAMOND", "EMERALD"]
 *             max-amount = 64
 *         }
 *     }
 * }
 * ```
 *
 * @param A The action enum type
 * @param S The slot enum type
 * @param menuApi The ConfigurableMenuAPI instance
 * @param configName The config file name (without .conf extension)
 * @param actionClass The KClass of the action enum
 * @param slotClass The KClass of the slot enum
 */
abstract class ConfigurableItemMenu<A : Enum<A>, S : Enum<S>>(
    menuApi: ConfigurableMenuAPI,
    configName: String,
    actionClass: KClass<A>,
    protected val slotClass: KClass<S>
) : ConfigurableMenu<A>(menuApi, configName, actionClass) {

    /**
     * Optional: Override to add code-based slot validators.
     * These are checked IN ADDITION to config validators.
     * Map of slot enum values to validator functions.
     */
    protected open val additionalSlotValidators: Map<S, (ItemStack) -> Boolean> = emptyMap()

    /**
     * Combines config-based and code-based slot validators.
     */
    override val slotValidators: Map<String, (ItemStack) -> Boolean>
        get() = additionalSlotValidators.mapKeys { (slot, _) -> slot.name }

    /**
     * Gets an item from a drag slot in a menu instance.
     *
     * @param instance The menu instance
     * @param slot The slot enum value
     * @return The ItemStack in the slot, or null if empty
     */
    fun getSlotItem(instance: ConfigurableMenuInstance<A>, slot: S): ItemStack? {
        return instance.getItem(slot)
    }

    /**
     * Gets all items from all configured drag slots.
     *
     * @param instance The menu instance
     * @return Map of slot enum values to their ItemStacks
     */
    fun getAllSlotItems(instance: ConfigurableMenuInstance<A>): Map<S, ItemStack?> {
        val result = mutableMapOf<S, ItemStack?>()
        slotClass.java.enumConstants?.forEach { slot ->
            result[slot] = instance.getItem(slot)
        }
        return result
    }

    /**
     * Checks if a slot has an item.
     *
     * @param instance The menu instance
     * @param slot The slot enum value
     * @return true if the slot contains an item
     */
    fun hasItem(instance: ConfigurableMenuInstance<A>, slot: S): Boolean {
        return instance.getItem(slot) != null
    }

    /**
     * Sets an item in a drag slot.
     *
     * @param instance The menu instance
     * @param slot The slot enum value
     * @param item The item to place, or null to clear
     */
    fun setSlotItem(instance: ConfigurableMenuInstance<A>, slot: S, item: ItemStack?) {
        instance.setItem(slot, item)
    }
}
