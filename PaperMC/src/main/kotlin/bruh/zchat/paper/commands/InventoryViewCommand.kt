package bruh.zchat.paper.commands

import bruh.zchat.paper.services.ChatInventoryPlaceholderService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Command for viewing inventory snapshots created by inventory placeholders
 */
@Command("chatplugin", "zealouschat", "zchat")
@CommandPermission("zchat.admin")
class InventoryViewCommand(
    private val chatInventoryPlaceholderService: ChatInventoryPlaceholderService
) {
    
    @Subcommand("viewinventory")
    @CommandPermission("zchat.viewinventory")
    fun viewInventory(player: Player, snapshotId: String) {
        if (!chatInventoryPlaceholderService.viewInventorySnapshot(player, snapshotId)) {
            player.sendMessage(
                Component.text("Failed to open inventory snapshot. It may have expired.")
                    .color(NamedTextColor.RED)
            )
        }
    }
}