package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.config.EssentiallyStatelessConfigLoader
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Main plugin command for reload and admin functions.
 */
@Command("essentiallystateless", "es", "stateless")
class EssentiallyStatelessMainCommand(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI,
    private val configLoader: EssentiallyStatelessConfigLoader
) {

    @Subcommand("reload")
    @CommandPermission("essentiallystateless.reload")
    suspend fun reload(actor: BukkitCommandActor) {
        // Reload config
        val newConfig = configLoader.reload()
        
        // Update plugin's config reference using reflection since it's private set
        val configField = plugin::class.java.getDeclaredField("config")
        configField.isAccessible = true
        configField.set(plugin, newConfig)
        
        // Reload translations
        translations.switchLanguage(newConfig.language)
        translations.reload()
        
        actor.sender().sendMessage(translations.getComponent(CommandMessages.CONFIG_RELOADED))
    }

    @Subcommand("version")
    fun version(actor: BukkitCommandActor) {
        val version = plugin.pluginMeta.version
        actor.sender().sendMessage(net.kyori.adventure.text.Component.text("EssentiallyStateless v$version"))
    }
}
