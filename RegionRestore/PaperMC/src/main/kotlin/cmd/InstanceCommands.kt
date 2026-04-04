package bruh.regionrestore.cmd

import bruh.regionrestore.cloner.InstanceConfig
import bruh.regionrestore.cloner.InstanceType
import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.cloner.RegionInstance
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("regionrestore", "rr", "arena")
class InstanceCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val massClonerService: MassClonerService,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI,
    private val plugin: JavaPlugin,
    private val menuAPI: MenuAPI
) {
    private val log = org.slf4j.LoggerFactory.getLogger(InstanceCommands::class.java)

    @Subcommand("instance create")
    @CommandPermission("regionrestore.instance.create")
    suspend fun createInstance(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        chunkX: Int,
        chunkZ: Int,
        world: String? = null,
        versionId: Int? = null,
        restoreOnBoot: Boolean = false,
        restoreOnVacate: Boolean = false,
        restoreIntervalSeconds: Int? = null
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = if (world != null) {
            Bukkit.getWorld(world)
        } else {
            player.world
        } ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world ?: "unknown")
            })
            return
        }

        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH_SIMPLE))
        }

        val instanceConfig = InstanceConfig(
            restoreOnBoot = restoreOnBoot,
            restoreOnVacate = restoreOnVacate,
            restoreIntervalSeconds = restoreIntervalSeconds,
            restoreAudienceScope = config.notifications.defaultAudienceScope,
            updateLight = null
        )

        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = versionId ?: templateVersion.versionId,
            originChunkX = chunkX,
            originChunkZ = chunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = instanceConfig
        )

        val result = massClonerService.addManualInstance(instance)
        result.onFailure {
            log.error("Failed to persist instance ${instance.instanceId}", it)
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
        }

        if (instanceConfig.restoreOnBoot || instanceConfig.restoreIntervalSeconds != null) {
            massClonerService.startInstanceTriggers(instance)
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_CREATED) {
            unparsed("id", instance.instanceId.toString())
            unparsed("name", templateName)
            unparsed("chunk_x", chunkX.toString())
            unparsed("chunk_z", chunkZ.toString())
        })
    }

    @Subcommand("instance list all")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listAllInstances(actor: CommandSender) {
        val instances = massClonerService.listInstances(null, null)

        if (instances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_HEADER) {
            unparsed("count", instances.size.toString())
        })
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_ITEM) {
                unparsed("type_mark", typeMark)
                unparsed("id", instance.instanceId.toString())
                unparsed("name", instance.templateName)
                unparsed("chunk_x", instance.originChunkX.toString())
                unparsed("chunk_z", instance.originChunkZ.toString())
                unparsed("world", instance.worldName)
            })
        }
    }

    @Subcommand("instance list world")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listInstancesByWorld(
        actor: CommandSender,
        world: String,
        @Optional type: String? = null
    ) {
        val instanceType = when (type?.uppercase()) {
            "POOLED" -> InstanceType.POOLED
            "MANUAL" -> InstanceType.MANUAL
            else -> null
        }

        val instances = massClonerService.listInstances(world, instanceType)

        if (instances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_HEADER) {
            unparsed("count", instances.size.toString())
        })
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_ITEM) {
                unparsed("type_mark", typeMark)
                unparsed("id", instance.instanceId.toString())
                unparsed("name", instance.templateName)
                unparsed("chunk_x", instance.originChunkX.toString())
                unparsed("chunk_z", instance.originChunkZ.toString())
                unparsed("world", instance.worldName)
            })
        }
    }

    @Subcommand("instance info")
    @CommandPermission("regionrestore.instance.info")
    suspend fun instanceInfo(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val cfg = instance.config
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_HEADER) {
            unparsed("id", instance.instanceId.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TEMPLATE) {
            unparsed("name", instance.templateName)
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_LOCATION) {
            unparsed("world", instance.worldName)
            unparsed("chunk_x", instance.originChunkX.toString())
            unparsed("chunk_z", instance.originChunkZ.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_SIZE) {
            unparsed("size_x", instance.sizeXChunks.toString())
            unparsed("size_z", instance.sizeZChunks.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TYPE) {
            unparsed("type", instance.instanceType.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_CONFIG))
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_BOOT_RESTORE) {
            unparsed("value", (cfg?.restoreOnBoot ?: false).toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_VACATE_RESTORE) {
            unparsed("value", (cfg?.restoreOnVacate ?: false).toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_REPEAT) {
            unparsed("value", (cfg?.restoreIntervalSeconds?.toString() ?: "none"))
        })
    }

    @Subcommand("instance delete")
    @CommandPermission("regionrestore.instance.delete")
    suspend fun deleteInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val confirmed = CommandHelpers.confirmInstanceDeletion(
            player = player,
            instance = instance,
            translations = translations,
            menuAPI = menuAPI,
            plugin = plugin
        )
        if (!confirmed) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_DELETE_CANCELLED))
            return
        }

        val result = massClonerService.removeInstance(id)

        result.onFailure {
            log.error("Failed to persist state after removing instance $id", it)
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
        }

        if (result.getOrNull() == true) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_DELETED) {
                unparsed("id", instanceId)
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
        }
    }

    @Subcommand("instance restore")
    @CommandPermission("regionrestore.instance.restore")
    suspend fun restoreInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.triggerInstanceRestore(instance)
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_RESTORE_TRIGGERED) {
            unparsed("id", instanceId)
        })
    }
}
