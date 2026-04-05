package bruh.regionrestore.cmd

import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.selection.SelectionService
import bruh.regionrestore.selection.SelectionWandService
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("regionrestore", "rr", "arena")
class SelectionCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val config: RegionRestoreConfig,
    private val translations: TranslationAPI,
    private val selectionService: SelectionService,
    private val selectionWandService: SelectionWandService,
    private val plugin: JavaPlugin
) {
    private val log = LoggerFactory.getLogger(SelectionCommands::class.java)

    @Subcommand("selection wand")
    @CommandPermission("regionrestore.wand")
    suspend fun giveWand(actor: BukkitCommandActor) {
        val player = actor.requirePlayer()

        if (selectionWandService.giveWand(player)) {
            player.sendMessage(translations.getComponent(CommandMessages.WAND_GIVEN))
        } else {
            player.sendMessage(translations.getComponent(CommandMessages.WAND_ALREADY_HAVE))
        }
    }

    @Subcommand("selection")
    @CommandPermission("regionrestore.selection")
    suspend fun showSelection(actor: BukkitCommandActor) {
        val player = actor.requirePlayer()
        selectionWandService.showSelectionInfo(player)
    }

    @Subcommand("selection clear")
    @CommandPermission("regionrestore.selection")
    suspend fun clearSelection(actor: BukkitCommandActor) {
        val player = actor.requirePlayer()
        selectionService.clearSelection(player.uniqueId)
        player.sendMessage(translations.getComponent(CommandMessages.SELECTION_CLEARED))
    }

    @Subcommand("selection create")
    @CommandPermission("regionrestore.template.create")
    suspend fun createFromSelection(actor: BukkitCommandActor, name: String) {
        val player = actor.requirePlayer()

        val selection = selectionService.getSelection(player.uniqueId)
        if (selection == null) {
            val partial = selectionService.getPartialSelection(player.uniqueId)
            if (partial == null) {
                player.sendMessage(translations.getComponent(CommandMessages.SELECTION_NONE))
            } else {
                player.sendMessage(translations.getComponent(CommandMessages.SELECTION_INCOMPLETE))
            }
            return
        }

        player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_FROM_SELECTION) {
            unparsed("name", name)
        })

        val template = if (nmsAdapter.supportsAsync) {
            withContext(plugin.asyncDispatcher) {
                nmsAdapter.serializeArea(
                    selection.world,
                    selection.minChunkX,
                    selection.minChunkZ,
                    selection.maxChunkX,
                    selection.maxChunkZ
                )
            }
        } else {
            withContext(plugin.globalRegionDispatcher) {
                nmsAdapter.serializeArea(
                    selection.world,
                    selection.minChunkX,
                    selection.minChunkZ,
                    selection.maxChunkX,
                    selection.maxChunkZ
                )
            }
        }

        withContext(plugin.asyncDispatcher) {
            val descriptionFormat = config.templates.defaultDescriptionFormat
            val description = descriptionFormat.replace("<player>", player.name)
            templateRepository.saveTemplate(name, description, template, nmsAdapter.minecraftVersion)

            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_CREATED) {
                unparsed("name", name)
            })
        }
    }
}
