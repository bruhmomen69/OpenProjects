package bruh.regionrestore.cmd

import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.cloner.VersionMode
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import org.slf4j.LoggerFactory
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("regionrestore", "rr", "arena")
class ClonerCommands(
    private val massClonerService: MassClonerService,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI
) {
    private val log = LoggerFactory.getLogger(ClonerCommands::class.java)

    @Subcommand("cloner status")
    @CommandPermission("regionrestore.cloner.status")
    suspend fun clonerStatus(actor: CommandSender, world: String? = null) {
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_STATUS_HEADER))
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_STATUS_SEPARATOR))

        val worldsToShow = if (world != null) {
            val targetWorld = Bukkit.getWorld(world)
            if (targetWorld == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                    unparsed("world", world)
                })
                return
            }
            listOf(targetWorld)
        } else {
            Bukkit.getWorlds().filter { w ->
                config.massCloner.worlds.any { it.name == w.name }
            }
        }

        for (targetWorld in worldsToShow) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == targetWorld.name }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_MANAGED) {
                    unparsed("world", targetWorld.name)
                })
                continue
            }

            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_HEADER) {
                unparsed("world", targetWorld.name)
            })

            for (pool in worldConfig.pools) {
                val instances = massClonerService.getInstancesForPool(targetWorld.name, pool.templateName)
                val activeCount = instances.size
                val targetCount = pool.count
                val status = if (activeCount == targetCount) "<green>✓" else "<yellow>⚠"

                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_STATUS) {
                    placeholder("status", status)
                    unparsed("name", pool.templateName)
                    unparsed("active", activeCount.toString())
                    unparsed("target", targetCount.toString())
                })
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_VERSION) {
                    unparsed(
                        "version", pool.versionMode.toString() +
                                (if (pool.versionMode == VersionMode.PINNED) " #${pool.pinnedVersionId}" else " (active)")
                    )
                })
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_SETTINGS) {
                    unparsed("separation", pool.separationChunks.toString())
                    unparsed("boot", pool.restoreOnBoot.toString())
                    unparsed("vacate", pool.restoreOnVacate.toString())
                })

                if (pool.restoreIntervalSeconds != null) {
                    actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_REPEAT) {
                        unparsed("interval", pool.restoreIntervalSeconds.toString())
                    })
                }
            }
        }
    }

    @Subcommand("cloner restore")
    @CommandPermission("regionrestore.cloner.restore")
    suspend fun clonerRestore(actor: CommandSender, world: String?, template: String?) {
        val worldName = world ?: (actor as? Player)?.world?.name
        if (worldName == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_SPECIFIED))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_RESTORE_STARTING))

        val worldsToRestore = if (template != null) {
            listOf(worldName to listOf(template))
        } else {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == worldName }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_MANAGED_ERROR) {
                    unparsed("world", worldName)
                })
                return
            }
            listOf(worldName to worldConfig.pools.map { it.templateName })
        }

        var restoredCount = 0

        for ((w, templates) in worldsToRestore) {
            for (templateName in templates) {
                val instances = massClonerService.getInstancesForPool(w, templateName)
                for (instance in instances) {
                    massClonerService.triggerInstanceRestore(instance)
                    restoredCount++
                }
            }
        }

        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_RESTORE_TRIGGERED) {
            unparsed("count", restoredCount.toString())
        })
    }

    @Subcommand("cloner regen")
    @CommandPermission("regionrestore.cloner.regen")
    suspend fun clonerRegen(
        actor: CommandSender,
        world: String? = null,
        force: Boolean = false
    ) {
        if (!force) {
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_CONFIRM))
            return
        }

        if (world != null) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == world }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_CONFIGURED) {
                    unparsed("world", world)
                })
                return
            }
        }

        val worldsToRegen = if (world != null) listOf(world) else emptyList()
        val result = massClonerService.regeneratePools(worldsToRegen)

        result.onFailure {
            log.error("Failed to persist state after regenerating pools", it)
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_SAVE_FAILED))
        }

        result.onSuccess { (removed, allocated) ->
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_COMPLETE))
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_REMOVED) {
                unparsed("count", removed.toString())
            })
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_ALLOCATED) {
                unparsed("count", allocated.toString())
            })
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_MANUAL_PRESERVED))
        }
    }
}
