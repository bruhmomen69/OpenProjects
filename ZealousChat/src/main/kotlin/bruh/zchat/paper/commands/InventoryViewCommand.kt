package bruh.zchat.paper.commands
 
 import bruh.zchat.paper.services.ChatInventoryPlaceholderService
 import com.github.shynixn.mccoroutine.folia.entityDispatcher
 import com.github.shynixn.mccoroutine.folia.launch
 import org.bukkit.entity.Player
 import org.bukkit.plugin.Plugin
 import revxrsal.commands.annotation.Command
 import revxrsal.commands.annotation.Subcommand
 import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Command for viewing inventory snapshots created by inventory placeholders
 */
@Command("chatplugin", "zealouschat", "zchat")
@CommandPermission("zchat.admin")
class InventoryViewCommand(
    private val plugin: Plugin,
    private val chatInventoryPlaceholderService: ChatInventoryPlaceholderService
) {
    
    @Subcommand("viewinventory")
    @CommandPermission("zchat.viewinventory")
    fun viewInventory(player: Player, snapshotId: String) {
        plugin.launch(plugin.entityDispatcher(player)) {
            chatInventoryPlaceholderService.viewInventorySnapshot(player, snapshotId)
        }
    }
}