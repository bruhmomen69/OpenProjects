package bruh.regionrestore.cmd

import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.SchedulerService
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import org.slf4j.LoggerFactory
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.floor

@Command("regionrestore", "rr", "arena")
class RestoreCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val schedulerService: SchedulerService,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI
) {
    private val log = LoggerFactory.getLogger(RestoreCommands::class.java)

    @Subcommand("restore version")
    @CommandPermission("regionrestore.restore")
    suspend fun restore(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        @SuggestVersionId @Optional @Default("active") versionOrActive: String = "active",
        @Optional scope: AudienceScope? = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val scope = scope ?: config.notifications.defaultAudienceScope

        val templateVersion = if (versionOrActive.equals("active", true)) {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionOrActive)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionOrActive)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(job, null, emptyList(), scope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
        })
    }

    @Subcommand("restore in")
    @CommandPermission("regionrestore.restorein")
    suspend fun restoreIn(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        seconds: Int,
        @SuggestVersionId versionOrActive: String = "active",
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val templateVersion = if (versionOrActive == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionOrActive)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionOrActive)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(
            job,
            countdownSeconds = seconds,
            announcePoints = config.restore.defaultAnnounceTimes,
            audienceScope = scope
        )

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_SCHEDULED) {
            unparsed("name", name)
            unparsed("seconds", seconds.toString())
        })
    }

    @Subcommand("restore template", "template restore")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreTemplate(
        actor: CommandSender,
        @SuggestTemplateName name: String
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val templateVersion = templateRepository.loadActiveTemplateVersion(name)

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
        })
    }

    @Subcommand("restore at")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreAt(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        world: String,
        x: Int,
        z: Int,
        @SuggestVersionId @Optional versionOrActive: String? = null
    ) {
        val targetWorld = Bukkit.getWorld(world) ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world)
            })
            return
        }

        val versionToUse = versionOrActive ?: "active"

        val templateVersion = if (versionToUse == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionToUse.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionToUse)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionToUse)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val targetChunkX = floor(x / 16.0).toInt()
        val targetChunkZ = floor(z / 16.0).toInt()

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = targetChunkX,
            targetChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING_AT) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
            unparsed("chunk_x", targetChunkX.toString())
            unparsed("chunk_z", targetChunkZ.toString())
        })
    }
}
