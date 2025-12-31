package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for player state management (heal, feed, fly, speed, god, etc.).
 */
class PlayerCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {

    @Command("heal")
    @CommandPermission("essentiallystateless.heal")
    fun heal(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.heal.others") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                val maxHealth = target.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                target.health = maxHealth
                target.fireTicks = 0
                target.foodLevel = 20
                target.saturation = 20f
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.HEAL_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.HEAL_OTHER) {
                unparsed("player", target.name)
            })
            target.sendMessage(translations.getComponentSync(CommandMessages.HEAL_BY_OTHER) {
                unparsed("healer", actor.name())
            })
        }
    }

    @Command("feed", "eat")
    @CommandPermission("essentiallystateless.feed")
    fun feed(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.feed.others") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.foodLevel = plugin.config.defaultFeedAmount
                target.saturation = 20f
                target.exhaustion = 0f
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FEED_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FEED_OTHER) {
                unparsed("player", target.name)
            })
            target.sendMessage(translations.getComponentSync(CommandMessages.FEED_BY_OTHER) {
                unparsed("feeder", actor.name())
            })
        }
    }

    @Command("fly")
    @CommandPermission("essentiallystateless.fly")
    fun fly(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.fly.others") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.allowFlight = !target.allowFlight
                if (!target.allowFlight) {
                    target.isFlying = false
                }
            }
        }

        if (target.allowFlight) {
            if (target == actor.sender()) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FLY_ENABLED))
            } else {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FLY_ENABLED_OTHER) {
                    unparsed("player", target.name)
                })
            }
        } else {
            if (target == actor.sender()) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FLY_DISABLED))
            } else {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.FLY_DISABLED_OTHER) {
                    unparsed("player", target.name)
                })
            }
        }
    }

    @Command("speed")
    @CommandPermission("essentiallystateless.speed")
    fun speed(
        actor: BukkitCommandActor,
        speed: Float,
        @Optional type: String?,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.speed.others") ?: return

        val isFlying = type?.lowercase() == "fly" || (type == null && target.isFlying)
        val maxSpeed = if (isFlying) plugin.config.maxFlySpeed else plugin.config.maxWalkSpeed
        val speedType = if (isFlying) "fly" else "walk"

        if (speed < 0 || speed > maxSpeed) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPEED_INVALID) {
                unparsed("max", maxSpeed.toString())
            })
            return
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                if (isFlying) {
                    target.flySpeed = speed
                } else {
                    target.walkSpeed = speed
                }
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPEED_SET) {
                unparsed("type", speedType)
                unparsed("speed", speed.toString())
            })
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPEED_SET_OTHER) {
                unparsed("player", target.name)
                unparsed("type", speedType)
                unparsed("speed", speed.toString())
            })
        }
    }

    @Command("flyspeed", "fspeed")
    @CommandPermission("essentiallystateless.speed")
    fun flyspeed(
        actor: BukkitCommandActor,
        speed: Float,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        speed(actor, speed, "fly", targetName)
    }

    @Command("walkspeed", "wspeed")
    @CommandPermission("essentiallystateless.speed")
    fun walkspeed(
        actor: BukkitCommandActor,
        speed: Float,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        speed(actor, speed, "walk", targetName)
    }

    @Command("god")
    @CommandPermission("essentiallystateless.god")
    fun god(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.god.others") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.isInvulnerable = !target.isInvulnerable
            }
        }

        if (target.isInvulnerable) {
            if (target == actor.sender()) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GOD_ENABLED))
            } else {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GOD_ENABLED_OTHER) {
                    unparsed("player", target.name)
                })
            }
        } else {
            if (target == actor.sender()) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GOD_DISABLED))
            } else {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.GOD_DISABLED_OTHER) {
                    unparsed("player", target.name)
                })
            }
        }
    }

    @Command("ext", "extinguish")
    @CommandPermission("essentiallystateless.ext")
    fun extinguish(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.ext") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.fireTicks = 0
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXT_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXT_OTHER) {
                unparsed("player", target.name)
            })
        }
    }

    @Command("rest")
    @CommandPermission("essentiallystateless.rest")
    fun rest(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.rest") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0)
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.REST_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.REST_OTHER) {
                unparsed("player", target.name)
            })
        }
    }

    @Command("exp", "xp")
    @CommandPermission("essentiallystateless.exp")
    fun exp(
        actor: BukkitCommandActor,
        @Optional action: String?,
        @Optional amount: Int?,
        @Optional @SuggestOnlinePlayer targetName: String?
    ) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.exp") ?: return

        if (action == null || action.equals("query", ignoreCase = true) || action.equals("show", ignoreCase = true)) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXP_QUERY) {
                unparsed("player", target.name)
                unparsed("levels", target.level.toString())
                unparsed("total", target.totalExperience.toString())
            })
            return
        }

        val amt = amount ?: action.toIntOrNull() ?: 0

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                when (action.lowercase()) {
                    "set" -> {
                        target.level = amt
                        target.exp = 0f
                    }
                    "give", "add" -> {
                        target.giveExp(amt)
                    }
                    "take", "remove" -> {
                        target.giveExp(-amt)
                    }
                    else -> {
                        // If action is a number, treat it as "give"
                        val parsedAmount = action.toIntOrNull()
                        if (parsedAmount != null) {
                            target.giveExp(parsedAmount)
                        }
                    }
                }
            }
        }

        when (action.lowercase()) {
            "set" -> actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXP_SET) {
                unparsed("player", target.name)
                unparsed("amount", amt.toString())
            })
            "give", "add" -> actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXP_GIVE) {
                unparsed("player", target.name)
                unparsed("amount", amt.toString())
            })
            "take", "remove" -> actor.sender().sendMessage(translations.getComponentSync(CommandMessages.EXP_TAKE) {
                unparsed("player", target.name)
                unparsed("amount", amt.toString())
            })
        }
    }

    @Command("kill")
    @CommandPermission("essentiallystateless.kill")
    fun kill(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        val target = resolveTarget(actor, targetName, "essentiallystateless.kill") ?: return

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.health = 0.0
            }
        }

        if (target == actor.sender()) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.KILL_SELF))
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.KILL_OTHER) {
                unparsed("player", target.name)
            })
        }
    }

    @Command("burn")
    @CommandPermission("essentiallystateless.burn")
    fun burn(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        @Optional @Default("5") seconds: Int
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        plugin.launch {
            withContext(plugin.entityDispatcher(target)) {
                target.fireTicks = seconds * 20
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BURN_SET) {
            unparsed("player", target.name)
            unparsed("seconds", seconds.toString())
        })
    }

    private fun resolveTarget(actor: BukkitCommandActor, targetName: String?, otherPermission: String): Player? {
        return if (targetName != null) {
            val player = Bukkit.getPlayer(targetName)
            if (player == null) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return null
            }
            if (!actor.sender().hasPermission(otherPermission)) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.NO_PERMISSION))
                return null
            }
            player
        } else if (actor.sender() is Player) {
            actor.sender() as Player
        } else {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            null
        }
    }
}
