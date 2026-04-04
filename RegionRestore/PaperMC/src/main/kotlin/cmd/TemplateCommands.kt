package bruh.regionrestore.cmd

import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.floor

@Command("regionrestore", "rr", "arena")
class TemplateCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI
) {
    private val log = org.slf4j.LoggerFactory.getLogger(TemplateCommands::class.java)

    @Subcommand("template create")
    @CommandPermission("regionrestore.template.create")
    suspend fun createTemplate(
        actor: CommandSender,
        name: String,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        world: String? = null
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

        val minChunkX = floor(minX / 16.0).toInt()
        val maxChunkX = floor(maxX / 16.0).toInt()
        val minChunkZ = floor(minZ / 16.0).toInt()
        val maxChunkZ = floor(maxZ / 16.0).toInt()

        val template = nmsAdapter.serializeArea(targetWorld, minChunkX, minChunkZ, maxChunkX, maxChunkZ)

        val descriptionFormat = config.templates.defaultDescriptionFormat
        val description = descriptionFormat.replace("<player>", player.name)
        templateRepository.saveTemplate(name, description, template, nmsAdapter.minecraftVersion)

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_CREATED) {
            unparsed("name", name)
        })
    }

    @Subcommand("template list")
    @CommandPermission("regionrestore.template.list")
    suspend fun listTemplates(actor: CommandSender) {
        val templates = templateRepository.listTemplates()
        if (templates.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_HEADER))
        templates.forEach { templateName ->
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_ITEM) {
                unparsed("name", templateName)
            })
        }
    }

    @Subcommand("template info")
    @CommandPermission("regionrestore.template.info")
    suspend fun templateInfo(actor: CommandSender, @SuggestTemplateName name: String) {
        val versions = templateRepository.getTemplateVersions(name)
        if (versions == null || versions.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
            return
        }

        val activeVersionId = templateRepository.loadActiveTemplateVersion(name)?.versionId ?: 0

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_HEADER) {
            unparsed("name", name)
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_VERSIONS) {
            unparsed("count", versions.size.toString())
        })
        versions.forEach { version ->
            val activeMark = if (version.versionId == activeVersionId) translations.getString(CommandMessages.TEMPLATE_INFO_VERSION_ACTIVE_MARK) else ""
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_VERSION_LINE) {
                unparsed("version", version.versionId.toString())
                unparsed("active_mark", activeMark)
                unparsed("created", java.time.Instant.ofEpochMilli(version.createdAt).toString())
                unparsed("description", version.description)
            })
        }
    }

    @Subcommand("template setactive")
    @CommandPermission("regionrestore.template.setactive")
    suspend fun setActiveVersion(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        @SuggestVersionNumber versionId: Int
    ) {
        val success = templateRepository.setActiveVersion(name, versionId)
        if (success) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_ACTIVE_SET) {
                unparsed("name", name)
                unparsed("version", versionId.toString())
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_ACTIVE_SET_FAILED))
        }
    }

    @Subcommand("template delete")
    @CommandPermission("regionrestore.template.delete")
    suspend fun deleteTemplate(actor: CommandSender, @SuggestTemplateName name: String) {
        val deleted = templateRepository.deleteTemplate(name)
        if (deleted) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_DELETED) {
                unparsed("name", name)
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
        }
    }
}
