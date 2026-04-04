package bruh.regionrestore.selection

import bruh.regionrestore.translations.CommandMessages
import bruh.zchat.utils.itemapi.ItemAPI
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * Service for managing the region selection wand.
 * Uses ItemAPI to create a tracked item that players can use to select region corners.
 */
class SelectionWandService(
    private val plugin: JavaPlugin,
    private val itemAPI: ItemAPI,
    private val selectionService: SelectionService,
    private val translations: TranslationAPI
) : Closeable, Listener {

    private val logger = LoggerFactory.getLogger(SelectionWandService::class.java)

    companion object {
        const val WAND_ITEM_ID = "regionrestore_selection_wand"
    }

    /** Track which corner the player is setting next (alternates between pos1 and pos2) */
    private val nextCorner = mutableMapOf<java.util.UUID, Int>()

    init {
        registerWandItem()
        plugin.server.pluginManager.registerEvents(this, plugin)
        logger.info("Selection wand service initialized")
    }

    /**
     * Registers the selection wand as a tracked item with ItemAPI.
     */
    private fun registerWandItem() {
        itemAPI.register(WAND_ITEM_ID) {
            material(XMaterial.GOLDEN_AXE)

            item {
                name = Component.text("Region Selection Wand", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                lore = mutableListOf<Component>(
                    Component.text("Right-click: Set next corner", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click: View selection", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Use /rr wand to get this item", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                )
                glow()
            }

            // Right-click to set corners
            onUseDeny { ctx, _ ->
                val player = ctx.player
                val location = player.getTargetBlockExact(5)?.location ?: player.location

                // Determine which corner to set (alternate between pos1 and pos2)
                val corner = nextCorner.getOrDefault(player.uniqueId, 1)

                if (corner == 1) {
                    val partial = selectionService.setPos1(player.uniqueId, location)
                    nextCorner[player.uniqueId] = 2

                    // Check if world changed
                    if (partial.pos2 == null && selectionService.getPartialSelection(player.uniqueId)?.pos2 != null) {
                        player.sendMessage(runBlocking {
                            translations.getComponent(CommandMessages.WAND_WORLD_CHANGED)
                        })
                    }

                    player.sendMessage(runBlocking {
                        translations.getComponent(CommandMessages.WAND_POS1_SET) {
                            unparsed("x", location.blockX.toString())
                            unparsed("y", location.blockY.toString())
                            unparsed("z", location.blockZ.toString())
                        }
                    })
                } else {
                    val partial = selectionService.setPos2(player.uniqueId, location)
                    nextCorner[player.uniqueId] = 1

                    // Check if world changed
                    if (partial.pos1 == null) {
                        player.sendMessage(runBlocking {
                            translations.getComponent(CommandMessages.WAND_WORLD_CHANGED)
                        })
                    }

                    player.sendMessage(runBlocking {
                        translations.getComponent(CommandMessages.WAND_POS2_SET) {
                            unparsed("x", location.blockX.toString())
                            unparsed("y", location.blockY.toString())
                            unparsed("z", location.blockZ.toString())
                        }
                    })

                    // If selection is complete, show info
                    selectionService.getSelection(player.uniqueId)?.let { selection ->
                        player.sendMessage(runBlocking {
                            translations.getComponent(CommandMessages.WAND_SELECTION_COMPLETE) {
                                unparsed("width", selection.width.toString())
                                unparsed("length", selection.length.toString())
                                unparsed("chunk_width", selection.chunkWidth.toString())
                                unparsed("chunk_length", selection.chunkLength.toString())
                            }
                        })
                    }
                }

            }
        }
    }

    /**
     * Gives the selection wand to a player.
     *
     * @param player The player to give the wand to
     * @return true if the wand was given, false if player already has one
     */
    suspend fun giveWand(player: Player): Boolean {
        val wand = itemAPI.createItem(WAND_ITEM_ID, player) ?: run {
            logger.warn("Failed to create selection wand item")
            return false
        }

        player.inventory.addItem(wand)
        nextCorner[player.uniqueId] = 1
        return true
    }

    /**
     * Checks if a player already has a selection wand in their inventory.
     */
    fun playerHasWand(player: Player): Boolean {
        return player.inventory.contents.any { item ->
            item != null && itemAPI.getItemId(item) == WAND_ITEM_ID
        }
    }

    /**
     * Shows the current selection info to a player.
     */
    fun showSelectionInfo(player: Player) {
        val selection = selectionService.getSelection(player.uniqueId)

        if (selection == null) {
            val partial = selectionService.getPartialSelection(player.uniqueId)
            if (partial == null) {
                player.sendMessage(runBlocking {
                    translations.getComponent(CommandMessages.SELECTION_NONE)
                })
            } else {
                player.sendMessage(runBlocking {
                    translations.getComponent(CommandMessages.SELECTION_INCOMPLETE)
                })
            }
            return
        }

        player.sendMessage(runBlocking {
            translations.getComponent(CommandMessages.SELECTION_INFO) {
                unparsed("world", selection.world.name)
                unparsed("min_x", selection.minX.toString())
                unparsed("min_z", selection.minZ.toString())
                unparsed("max_x", selection.maxX.toString())
                unparsed("max_z", selection.maxZ.toString())
                unparsed("chunk_width", selection.chunkWidth.toString())
                unparsed("chunk_length", selection.chunkLength.toString())
            }
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        selectionService.clearSelection(playerId)
        nextCorner.remove(playerId)
    }

    override fun close() {
        nextCorner.clear()
        logger.info("Selection wand service closed")
    }
}
