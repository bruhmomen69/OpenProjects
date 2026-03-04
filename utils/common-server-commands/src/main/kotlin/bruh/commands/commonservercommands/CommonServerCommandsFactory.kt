package bruh.commands.commonservercommands

import bruh.commands.commonservercommands.commands.*

/**
 * Factory for creating all common server commands.
 *
 * This class provides a convenient way to instantiate all the command classes
 * with the proper dependencies (plugin and translations).
 */
class CommonServerCommandsFactory(private val plugin: CommandPlugin) {

    /**
     * Creates all command instances for registration with Lamp.
     *
     * @return List of all command objects ready to be registered
     */
    fun createAllCommands(): List<Any> {
        return listOf(
            GameModeCommands(plugin, plugin.translations),
            TimeWeatherCommands(plugin, plugin.translations),
            PlayerCommands(plugin, plugin.translations),
            TeleportCommands(plugin, plugin.translations),
            InventoryCommands(plugin, plugin.translations, plugin.menuAPI ?: error("MenuAPI must be initialized to use InventoryCommands")),
            ItemCommands(plugin, plugin.translations),
            WorldCommands(plugin, plugin.translations),
            AdminCommands(plugin, plugin.translations),
            InfoCommands(plugin, plugin.translations),
            FunCommands(plugin, plugin.translations)
        )
    }

    /**
     * Creates individual command instances by name.
     */
    fun createGameModeCommands() = GameModeCommands(plugin, plugin.translations)
    fun createTimeWeatherCommands() = TimeWeatherCommands(plugin, plugin.translations)
    fun createPlayerCommands() = PlayerCommands(plugin, plugin.translations)
    fun createTeleportCommands() = TeleportCommands(plugin, plugin.translations)
    fun createInventoryCommands() = InventoryCommands(plugin, plugin.translations, plugin.menuAPI ?: error("MenuAPI must be initialized to use InventoryCommands"))
    fun createItemCommands() = ItemCommands(plugin, plugin.translations)
    fun createWorldCommands() = WorldCommands(plugin, plugin.translations)
    fun createAdminCommands() = AdminCommands(plugin, plugin.translations)
    fun createInfoCommands() = InfoCommands(plugin, plugin.translations)
    fun createFunCommands() = FunCommands(plugin, plugin.translations)
}
