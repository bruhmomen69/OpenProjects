package bruh.zchat.utils.menuapi.configurable

import bruh.zchat.utils.menuapi.ClickContext
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.configurable.config.MenuConfig
import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

/**
 * Base class for configurable menus with an action enum.
 * Subclasses override the [actionHandlers] map to define click behavior.
 *
 * Example usage:
 * ```kotlin
 * enum class ShopActions { BUY, SELL, CLOSE }
 *
 * class ShopMenu(menuApi: ConfigurableMenuAPI) : ConfigurableMenu<ShopActions>(
 *     menuApi = menuApi,
 *     configName = "shop",
 *     actionClass = ShopActions::class
 * ) {
 *     override val actionHandlers = mapOf(
 *         ShopActions.BUY to { ctx, instance ->
 *             ctx.player.sendMessage("Buying!")
 *             ClickResult.DENY
 *         },
 *         ShopActions.CLOSE to { _, instance ->
 *             instance.close()
 *             ClickResult.CLOSE
 *         }
 *     )
 * }
 * ```
 *
 * @param A The action enum type
 * @param menuApi The ConfigurableMenuAPI instance
 * @param configName The config file name (without .conf extension)
 * @param actionClass The KClass of the action enum
 */
abstract class ConfigurableMenu<A : Enum<A>>(
    protected val menuApi: ConfigurableMenuAPI,
    protected val configName: String,
    protected val actionClass: KClass<A>
) {
    private val configLoader = menuApi.createConfigLoader(configName)
    private var _config: MenuConfig = MenuConfig()

    /**
     * The current menu configuration.
     */
    val config: MenuConfig get() = _config

    /**
     * Map of action enum values to their click handlers.
     * Override this to define behavior for each action.
     */
    protected abstract val actionHandlers: Map<A, (ClickContext, ConfigurableMenuInstance<A>) -> ClickResult>

    /**
     * Optional: Override to add code-based slot validators.
     * Map of slot enum names to validator functions.
     */
    protected open val slotValidators: Map<String, (ItemStack) -> Boolean> = emptyMap()

    /**
     * Optional: Override to handle menu close events.
     */
    protected open val onClose: ((Player, ConfigurableMenuInstance<A>) -> Unit)? = null

    /**
     * Loads or reloads the menu configuration from disk.
     */
    suspend fun loadConfig() {
        _config = configLoader.load()
    }

    /**
     * Reloads the menu configuration.
     * Changes take effect on the next menu open.
     */
    fun reload() {
        runBlocking { loadConfig() }
    }

    /**
     * Opens this menu for a player.
     *
     * @param player The player to open the menu for
     * @return The menu instance for this player
     */
    fun open(player: Player): ConfigurableMenuInstance<A> {
        return menuApi.openMenu(this, player)
    }

    /**
     * Handles an action by name.
     * Called internally by the API when a configured action is triggered.
     */
    internal fun handleAction(
        actionName: String,
        context: ClickContext,
        instance: ConfigurableMenuInstance<A>
    ): ClickResult {
        val action = try {
            java.lang.Enum.valueOf(actionClass.java, actionName)
        } catch (e: IllegalArgumentException) {
            // Action name doesn't match any enum value
            return ClickResult.DENY
        }

        val handler = actionHandlers[action] ?: return ClickResult.DENY
        return handler(context, instance)
    }

    /**
     * Gets a slot validator by name.
     */
    internal fun getSlotValidator(slotName: String): ((ItemStack) -> Boolean)? {
        return slotValidators[slotName]
    }

    /**
     * Handles menu close.
     */
    internal fun handleClose(player: Player, instance: ConfigurableMenuInstance<A>) {
        onClose?.invoke(player, instance)
    }
}
