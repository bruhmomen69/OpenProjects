package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
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
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI,
    private val menuAPI: MenuAPI
) {

    @Command("anvil")
    @CommandPermission("essentiallystateless.anvil")
    fun anvil(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openAnvil(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.ANVIL_OPENED))
    }

    @Command("workbench", "craft", "wb")
    @CommandPermission("essentiallystateless.workbench")
    fun workbench(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openWorkbench(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.WORKBENCH_OPENED))
    }

    @Command("grindstone")
    @CommandPermission("essentiallystateless.grindstone")
    fun grindstone(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openGrindstone(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GRINDSTONE_OPENED))
    }

    @Command("cartographytable", "cartography")
    @CommandPermission("essentiallystateless.cartography")
    fun cartographytable(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openCartographyTable(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.CARTOGRAPHY_OPENED))
    }

    @Command("loom")
    @CommandPermission("essentiallystateless.loom")
    fun loom(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openLoom(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.LOOM_OPENED))
    }

    @Command("smithingtable", "smithing")
    @CommandPermission("essentiallystateless.smithing")
    fun smithingtable(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openSmithingTable(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SMITHING_OPENED))
    }

    @Command("stonecutter")
    @CommandPermission("essentiallystateless.stonecutter")
    fun stonecutter(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openStonecutter(null, true)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.STONECUTTER_OPENED))
    }

    @Command("enderchest", "ec", "echest")
    @CommandPermission("essentiallystateless.enderchest")
    fun enderchest(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val target = if (targetName != null) {
            if (!player.hasPermission("essentiallystateless.enderchest.others")) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.NO_PERMISSION))
                return
            }
            val t = Bukkit.getPlayer(targetName)
            if (t == null) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            t
        } else {
            player
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openInventory(target.enderChest)
            }
        }

        if (target == player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.ENDERCHEST_OPENED))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.ENDERCHEST_OPENED_OTHER) {
                unparsed("player", target.name)
            })
        }
    }

    @Command("disposal", "trash")
    @CommandPermission("essentiallystateless.disposal")
    fun disposal(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                val inventory = Bukkit.createInventory(null, InventoryType.CHEST, Component.text("Disposal"))
                player.openInventory(inventory)
            }
        }
        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.DISPOSAL_OPENED))
    }

    @Command("invsee")
    @CommandPermission("essentiallystateless.invsee")
    fun invsee(actor: BukkitCommandActor, @SuggestOnlinePlayer targetName: String) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.openInventory(target.inventory)
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.INVSEE_OPENED) {
            unparsed("player", target.name)
        })
    }

    @Command("clearinventory", "ci", "clear", "clean")
    @CommandPermission("essentiallystateless.clearinventory")
    fun clearinventory(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = if (targetName != null) {
            if (!actor.sender().hasPermission("essentiallystateless.clearinventory.others")) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.NO_PERMISSION))
                return
            }
            val t = Bukkit.getPlayer(targetName)
            if (t == null) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }
            t
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.inventory.clear()
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.CLEAR_INVENTORY_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.CLEAR_INVENTORY_OTHER) {
                unparsed("player", target.name)
            })
        }
    }
}
