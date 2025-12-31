package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for changing game modes.
 */
class GameModeCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {

    @Command("gamemode", "gm")
    @CommandPermission("essentiallystateless.gamemode")
    fun gamemode(
        actor: BukkitCommandActor,
        @SuggestGameMode mode: String,
        @Optional @SuggestOnlinePlayer target: String?
    ) {
        val gameMode = parseGameMode(mode)
        if (gameMode == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.INVALID_GAMEMODE) {
                unparsed("mode", mode)
            })
            return
        }

        val targetPlayer = if (target != null) {
            val player = Bukkit.getPlayer(target)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", target)
                })
                return
            }
            if (!actor.sender().hasPermission("essentiallystateless.gamemode.others")) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.NO_PERMISSION))
                return
            }
            player
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
                return
            }
            actor.sender() as Player
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(targetPlayer)) {
                targetPlayer.gameMode = gameMode
            }
        }

        if (targetPlayer == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GAMEMODE_SET_SELF) {
                unparsed("mode", gameMode.name.lowercase())
            })
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GAMEMODE_SET_OTHER) {
                unparsed("player", targetPlayer.name)
                unparsed("mode", gameMode.name.lowercase())
            })
            targetPlayer.sendMessage(translations.getComponentSync(CommandMessages.GAMEMODE_SET_BY_OTHER) {
                unparsed("mode", gameMode.name.lowercase())
                unparsed("setter", actor.name())
            })
        }
    }

    @Command("gmc", "creative")
    @CommandPermission("essentiallystateless.gamemode")
    fun creative(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer target: String?) {
        gamemode(actor, "creative", target)
    }

    @Command("gms", "survival")
    @CommandPermission("essentiallystateless.gamemode")
    fun survival(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer target: String?) {
        gamemode(actor, "survival", target)
    }

    @Command("gma", "adventure")
    @CommandPermission("essentiallystateless.gamemode")
    fun adventure(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer target: String?) {
        gamemode(actor, "adventure", target)
    }

    @Command("gmsp", "spectator")
    @CommandPermission("essentiallystateless.gamemode")
    fun spectator(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer target: String?) {
        gamemode(actor, "spectator", target)
    }

    private fun parseGameMode(mode: String): GameMode? {
        return when (mode.lowercase()) {
            "0", "s", "survival" -> GameMode.SURVIVAL
            "1", "c", "creative" -> GameMode.CREATIVE
            "2", "a", "adventure" -> GameMode.ADVENTURE
            "3", "sp", "spectator" -> GameMode.SPECTATOR
            else -> try {
                GameMode.valueOf(mode.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
