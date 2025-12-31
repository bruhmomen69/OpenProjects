package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.TreeType
import org.bukkit.block.CreatureSpawner
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for world manipulation (lightning, entities, trees, spawners, etc.).
 */
class WorldCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {

    @Command("lightning", "shock", "smite", "strike", "thor")
    @CommandPermission("essentiallystateless.lightning")
    fun lightning(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer targetName: String?) {
        if (targetName != null) {
            val target = Bukkit.getPlayer(targetName)
            if (target == null) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_NOT_FOUND) {
                    unparsed("player", targetName)
                })
                return
            }

            plugin.launch {
                withContext(plugin.regionDispatcher(target.location)) {
                    target.world.strikeLightning(target.location)
                }
            }

            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.LIGHTNING_STRIKE_PLAYER) {
                unparsed("player", target.name)
            })
        } else {
            if (actor.sender() !is Player) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
                return
            }
            val player = actor.sender() as Player
            val targetBlock = player.getTargetBlockExact(256)
            val location = targetBlock?.location ?: player.location

            plugin.launch {
                withContext(plugin.regionDispatcher(location)) {
                    location.world?.strikeLightning(location)
                }
            }

            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.LIGHTNING_STRIKE))
        }
    }

    @Command("remove", "butcher", "killall", "mobkill")
    @CommandPermission("essentiallystateless.remove")
    fun remove(
        actor: BukkitCommandActor,
        @Optional @SuggestEntityType entityType: String?,
        @Optional @Default("100") radius: Int
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val type = if (entityType != null && !entityType.equals("all", ignoreCase = true)) {
            try {
                EntityType.valueOf(entityType.uppercase())
            } catch (e: IllegalArgumentException) {
                actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNMOB_INVALID) {
                    unparsed("type", entityType)
                })
                return
            }
        } else {
            null
        }

        plugin.launch {
            withContext(plugin.regionDispatcher(player.location)) {
                var count = 0
                val maxRemove = plugin.config.maxRemoveEntities
                
                for (entity in player.world.entities) {
                    if (entity is Player) continue
                    if (count >= maxRemove) break
                    
                    if (entity.location.distance(player.location) <= radius) {
                        if (type == null || entity.type == type) {
                            entity.remove()
                            count++
                        }
                    }
                }

                if (count > 0) {
                    player.sendMessage(translations.getComponentSync(CommandMessages.REMOVE_SUCCESS) {
                        unparsed("count", count.toString())
                    })
                } else {
                    player.sendMessage(translations.getComponentSync(CommandMessages.REMOVE_NONE))
                }
            }
        }
    }

    @Command("spawnmob", "mob")
    @CommandPermission("essentiallystateless.spawnmob")
    fun spawnmob(
        actor: BukkitCommandActor,
        @SuggestEntityType entityType: String,
        @Optional @Default("1") amount: Int
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val type = try {
            EntityType.valueOf(entityType.uppercase())
        } catch (e: IllegalArgumentException) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNMOB_INVALID) {
                unparsed("type", entityType)
            })
            return
        }

        if (!type.isSpawnable || !type.isAlive) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNMOB_INVALID) {
                unparsed("type", entityType)
            })
            return
        }

        val spawnAmount = amount.coerceIn(1, plugin.config.maxSpawnMobs)

        plugin.launch {
            withContext(plugin.regionDispatcher(player.location)) {
                repeat(spawnAmount) {
                    player.world.spawnEntity(player.location, type)
                }
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNMOB_SUCCESS) {
            unparsed("count", spawnAmount.toString())
            unparsed("type", type.name.lowercase())
        })
    }

    @Command("tree")
    @CommandPermission("essentiallystateless.tree")
    fun tree(actor: BukkitCommandActor, @Optional treeType: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val type = if (treeType != null) {
            try {
                TreeType.valueOf(treeType.uppercase())
            } catch (e: IllegalArgumentException) {
                TreeType.TREE
            }
        } else {
            TreeType.TREE
        }

        val targetBlock = player.getTargetBlockExact(256)
        val location = targetBlock?.location?.add(0.0, 1.0, 0.0) ?: player.location

        plugin.launch {
            withContext(plugin.regionDispatcher(location)) {
                val success = location.world?.generateTree(location, type) ?: false
                if (success) {
                    player.sendMessage(translations.getComponentSync(CommandMessages.TREE_SUCCESS))
                } else {
                    player.sendMessage(translations.getComponentSync(CommandMessages.TREE_FAILED))
                }
            }
        }
    }

    @Command("bigtree")
    @CommandPermission("essentiallystateless.tree")
    fun bigtree(actor: BukkitCommandActor, @Optional treeType: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val type = when (treeType?.lowercase()) {
            "oak" -> TreeType.BIG_TREE
            "redwood", "sequoia" -> TreeType.TALL_REDWOOD
            "jungle" -> TreeType.JUNGLE
            "spruce" -> TreeType.MEGA_REDWOOD
            else -> TreeType.BIG_TREE
        }

        val targetBlock = player.getTargetBlockExact(256)
        val location = targetBlock?.location?.add(0.0, 1.0, 0.0) ?: player.location

        plugin.launch {
            withContext(plugin.regionDispatcher(location)) {
                val success = location.world?.generateTree(location, type) ?: false
                if (success) {
                    player.sendMessage(translations.getComponentSync(CommandMessages.TREE_SUCCESS))
                } else {
                    player.sendMessage(translations.getComponentSync(CommandMessages.TREE_FAILED))
                }
            }
        }
    }

    @Command("break")
    @CommandPermission("essentiallystateless.break")
    fun breakBlock(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val targetBlock = player.getTargetBlockExact(256)
        if (targetBlock == null || targetBlock.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BREAK_FAILED))
            return
        }

        plugin.launch {
            withContext(plugin.regionDispatcher(targetBlock.location)) {
                targetBlock.type = Material.AIR
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.BREAK_SUCCESS))
    }

    @Command("spawner")
    @CommandPermission("essentiallystateless.spawner")
    fun spawner(actor: BukkitCommandActor, @SuggestEntityType entityType: String) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val type = try {
            EntityType.valueOf(entityType.uppercase())
        } catch (e: IllegalArgumentException) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNMOB_INVALID) {
                unparsed("type", entityType)
            })
            return
        }

        val targetBlock = player.getTargetBlockExact(256)
        if (targetBlock == null || targetBlock.type != Material.SPAWNER) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNER_FAILED))
            return
        }

        plugin.launch {
            withContext(plugin.regionDispatcher(targetBlock.location)) {
                val spawner = targetBlock.state as CreatureSpawner
                spawner.spawnedType = type
                spawner.update()
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.SPAWNER_SET) {
            unparsed("type", type.name.lowercase())
        })
    }

    @Command("world")
    @CommandPermission("essentiallystateless.world")
    fun world(actor: BukkitCommandActor, @SuggestWorld worldName: String) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            actor.sender().sendMessage(translations.getComponentSync(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", worldName)
            })
            return
        }

        val spawnLocation = world.spawnLocation

        plugin.launch {
            withContext(plugin.entityDispatcher(player)) {
                player.teleportAsync(spawnLocation)
            }
        }

        actor.sender().sendMessage(translations.getComponentSync(CommandMessages.WORLD_TP_SUCCESS) {
            unparsed("world", world.name)
        })
    }
}
