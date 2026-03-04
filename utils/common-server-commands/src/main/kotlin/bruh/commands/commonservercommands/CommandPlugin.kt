package bruh.commands.commonservercommands

import bruh.commands.commonservercommands.config.CommonServerCommandsConfig
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity

/**
 * Interface that plugins must implement to use the common server commands.
 *
 * This interface provides the minimal contract needed by all server commands:
 * - Configuration access
 * - Translation system support
 * - Optional MenuAPI for inventory commands
 * - MCCoroutine entity/region dispatcher for multi-threaded command execution
 */
interface CommandPlugin {
    /**
     * The plugin's configuration object.
     */
    val config: CommonServerCommandsConfig

    /**
     * The plugin's translation API instance.
     */
    val translations: TranslationAPI

    /**
     * Optional MenuAPI instance for inventory commands.
     * Required if using InventoryCommands.
     */
    val menuAPI: MenuAPI?

    /**
     * The underlying SuspendingJavaPlugin for dispatcher access.
     * This is used to get entity and region dispatchers for MCCoroutine.
     */
    fun getUnderlyingPlugin(): SuspendingJavaPlugin
}

/**
 * Gets the entity dispatcher for the given entity.
 * Used with withContext() for safe entity access in async code.
 */
fun CommandPlugin.entityDispatcher(entity: Entity) = getUnderlyingPlugin().entityDispatcher(entity)

/**
 * Gets the region dispatcher for the given location.
 * Used with withContext() for safe region access in async code.
 */
fun CommandPlugin.regionDispatcher(location: Location) = getUnderlyingPlugin().regionDispatcher(location)

/**
 * Gets the region dispatcher for the given world and chunk coordinates.
 * Used with withContext() for safe region access in async code.
 */
fun CommandPlugin.regionDispatcher(world: World, chunkX: Int, chunkZ: Int) = getUnderlyingPlugin().regionDispatcher(world, chunkX, chunkZ)
