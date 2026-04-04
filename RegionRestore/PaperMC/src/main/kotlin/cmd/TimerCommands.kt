package bruh.regionrestore.cmd

import bruh.regionrestore.cloner.InstanceConfig
import bruh.regionrestore.cloner.InstanceType
import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.cloner.RegionInstance
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("regionrestore", "rr", "arena")
class TimerCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val massClonerService: MassClonerService,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI
) {
    private val log = org.slf4j.LoggerFactory.getLogger(TimerCommands::class.java)

    @Subcommand("timer set instance")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByInstanceId(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val updatedConfig = InstanceConfig(
            restoreOnBoot = instance.config?.restoreOnBoot ?: false,
            restoreOnVacate = instance.config?.restoreOnVacate ?: false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null
        )

        val updatedInstance = instance.copy(
            config = updatedConfig
        )

        massClonerService.stopInstanceTriggers(instance)

        val removeResult = massClonerService.removeInstance(instance.instanceId)
        if (removeResult.isFailure) {
            log.error("Failed to persist state after removing instance ${instance.instanceId} for timer update", removeResult.exceptionOrNull())
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            return
        }
        
        val addResult = massClonerService.addManualInstance(updatedInstance)
        if (addResult.isFailure) {
            log.error("Failed to persist state after adding updated instance ${updatedInstance.instanceId} for timer update", addResult.exceptionOrNull())
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            return
        }

        massClonerService.startInstanceTriggers(updatedInstance)

        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET) {
            unparsed("id", instanceId)
            unparsed("interval", intervalSeconds.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET_HELP) {
            unparsed("id", instanceId)
        })
    }

    @Subcommand("timer set template")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByTemplateName(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player
        val targetWorld = player?.world ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", "unknown")
            })
            return
        }

        val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)
        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH_SIMPLE))
        }

        val targetChunkX = templateVersion.data.minChunkX
        val targetChunkZ = templateVersion.data.minChunkZ

        val instanceConfig = InstanceConfig(
            restoreOnBoot = false,
            restoreOnVacate = false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null
        )

        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = templateVersion.versionId,
            originChunkX = targetChunkX,
            originChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = instanceConfig
        )

        massClonerService.addManualInstance(instance)
            .onFailure {
                log.error("Failed to persist timer instance ${instance.instanceId} for template '$templateName'", it)
                actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            }
        massClonerService.startInstanceTriggers(instance)

        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET_TEMPLATE) {
            unparsed("name", templateName)
            unparsed("interval", intervalSeconds.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_INSTANCE_ID_HELP) {
            unparsed("id", instance.instanceId.toString())
        })
    }

    @Subcommand("timer cancel id")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerById(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.removeInstance(id)
            .onFailure {
                log.error("Failed to persist state after cancelling timer for instance $id", it)
                actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            }
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_CANCELLED) {
            unparsed("id", instanceId)
        })
    }

    @Subcommand("timer cancel template-all")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerByTemplate(
        actor: CommandSender,
        @SuggestTemplateName templateName: String
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val worldName = player.world.name
        val instances = massClonerService.listInstances(worldName, null)
        val templateInstances = instances.filter { it.templateName == templateName }

        if (templateInstances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TIMER_NO_INSTANCES) {
                unparsed("name", templateName)
            })
            return
        }

        var cancelledCount = 0
        var failedCount = 0
        for (instance in templateInstances) {
            if (instance.config?.restoreIntervalSeconds != null) {
                val result = massClonerService.removeInstance(instance.instanceId)
                if (result.isSuccess) {
                    cancelledCount++
                } else {
                    log.error("Failed to persist state after cancelling timer for instance ${instance.instanceId}", result.exceptionOrNull())
                    failedCount++
                }
            }
        }

        if (failedCount > 0) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
        }
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_CANCELLED_TEMPLATE) {
            unparsed("count", cancelledCount.toString())
            unparsed("name", templateName)
        })
    }
}
