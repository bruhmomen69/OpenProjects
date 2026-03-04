package bruh.commands.commonservercommands.commands

import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.entityDispatcher
import bruh.commands.commonservercommands.translations.CommandMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for inventory management and virtual containers.
 */
class InventoryCommands(
    private val plugin: CommandPlugin,
    private val translations: TranslationAPI,
    private val menuAPI: MenuAPI
) {

    @Command("anvil")
    @CommandPermission("essentiallystateless.anvil")
    suspend fun anvil(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openAnvil(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.ANVIL_OPENED))
    }

    @Command("workbench", "craft", "wb")
    @CommandPermission("essentiallystateless.workbench")
    suspend fun workbench(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openWorkbench(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.WORKBENCH_OPENED))
    }

    @Command("grindstone")
    @CommandPermission("essentiallystateless.grindstone")
    suspend fun grindstone(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openGrindstone(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.GRINDSTONE_OPENED))
    }

    @Command("cartographytable", "cartography")
    @CommandPermission("essentiallystateless.cartography")
    suspend fun cartographytable(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openCartographyTable(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.CARTOGRAPHY_OPENED))
    }

    @Command("loom")
    @CommandPermission("essentiallystateless.loom")
    suspend fun loom(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openLoom(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.LOOM_OPENED))
    }

    @Command("smithingtable", "smithing")
    @CommandPermission("essentiallystateless.smithing")
    suspend fun smithingtable(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openSmithingTable(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.SMITHING_OPENED))
    }

    @Command("stonecutter")
    @CommandPermission("essentiallystateless.stonecutter")
    suspend fun stonecutter(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            player.openStonecutter(null, true)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.STONECUTTER_OPENED))
    }

    @Command("enderchest", "ec", "echest")
    @CommandPermission("essentiallystateless.enderchest")
    suspend fun enderchest(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val target = if (targetName != null) {
            if (!player.hasPermission("essentiallystateless.enderchest.others")) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.NO_PERMISSION))
                return
            }
            val t = Bukkit.getPlayer(targetName)
            if (t == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            t
        } else {
            player
        }

        withContext(plugin.entityDispatcher(player)) {
            player.openInventory(target.enderChest)
        }

        if (target == player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ENDERCHEST_OPENED))
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ENDERCHEST_OPENED_OTHER) {
                unparsed("player", target.name)
            })
        }
    }

    @Command("disposal", "trash")
    @CommandPermission("essentiallystateless.disposal")
    suspend fun disposal(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            val inventory = Bukkit.createInventory(null, InventoryType.CHEST, Component.text("Disposal"))
            player.openInventory(inventory)
        }
        actor.sender().sendMessage(translations.getComponent(CommandMessages.DISPOSAL_OPENED))
    }

    @Command("invsee")
    @CommandPermission("essentiallystateless.invsee")
    suspend fun invsee(actor: BukkitCommandActor, @SuggestOnlinePlayer targetName: String) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            player.openInventory(target.inventory)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.INVSEE_OPENED) {
            unparsed("player", target.name)
        })
    }

    @Command("clearinventory", "ci", "clear", "clean")
    @CommandPermission("essentiallystateless.clearinventory")
    suspend fun clearinventory(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = if (targetName != null) {
            if (!actor.sender().hasPermission("essentiallystateless.clearinventory.others")) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.NO_PERMISSION))
                return
            }
            val t = Bukkit.getPlayer(targetName)
            if (t == null) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            t
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        withContext(plugin.entityDispatcher(target)) {
            target.inventory.clear()
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.CLEAR_INVENTORY_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.CLEAR_INVENTORY_OTHER) {
                unparsed("player", target.name)
            })
        }
    }
}
