package lol.mcplugs.minimessagechatplugin.paper.commands

import lol.mcplugs.minimessagechatplugin.paper.services.ChatInventoryPlaceholderService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Command for viewing inventory snapshots created by inventory placeholders
 */
@Command("chatplugin")
@CommandPermission("chatplugin.admin")
class InventoryViewCommand(
    private val chatInventoryPlaceholderService: ChatInventoryPlaceholderService
) {
    
    @Subcommand("viewinventory")
    @CommandPermission("chatplugin.viewinventory")
    fun viewInventory(player: Player, snapshotId: String) {
        if (!chatInventoryPlaceholderService.viewInventorySnapshot(player, snapshotId)) {
            player.sendMessage(
                Component.text("Failed to open inventory snapshot. It may have expired.")
                    .color(NamedTextColor.RED)
            )
        }
    }
}