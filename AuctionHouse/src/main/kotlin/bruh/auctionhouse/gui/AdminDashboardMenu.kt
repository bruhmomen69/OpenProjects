package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Admin dashboard menu for managing the auction house.
 */
class AdminDashboardMenu(
    private val menuAPI: MenuAPI,
    private val auctionRepository: AuctionRepository?,
    private val transactionRepository: TransactionRepository?,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private val playerBanRepository = plugin.playerBanRepository

    fun open() {
        val activeAuctions = auctionRepository?.let { runBlocking { it.getActiveAuctionsCount() } } ?: 0

        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.ADMIN_DASHBOARD_TITLE)

            background = MenuUtils.backgroundItem()

            // Row 0: Title
            item(4, VItem(XMaterial.COMMAND_BLOCK) {
                name = mm.deserialize("<red><bold>Admin Dashboard")
                lore = mutableListOf(
                    mm.deserialize("<gray>Manage auctions and players"),
                    Component.empty(),
                    mm.deserialize("<gray>Active Auctions: <white>$activeAuctions")
                )
                hideAllFlags()
            })

            // Row 1: Player management
            item(10, VItem(XMaterial.PLAYER_HEAD) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_VIEW_PLAYER)
                lore = mutableListOf(
                    mm.deserialize("<gray>View a player's auctions"),
                    Component.empty(),
                    mm.deserialize("<green>Click to open")
                )
                hideAllFlags()

                onClick { _, _ ->
                    AdminViewPlayerMenu(menuAPI, auctionRepository, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            })

            // Row 1: Search auctions
            item(12, VItem(XMaterial.COMPASS) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_SEARCH_AUCTIONS)
                lore = mutableListOf(
                    mm.deserialize("<gray>Search for auctions"),
                    Component.empty(),
                    mm.deserialize("<green>Click to search")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptText(player, "Search auctions by item name or seller")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                // In full implementation, this would open search results
                                player.sendMessage(mm.deserialize("<yellow>Search for '${result.value}' - feature coming soon"))
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    ClickResult.CLOSE
                }
            })

            // Row 1: Active auctions
            item(14, VItem(XMaterial.CHEST) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_ACTIVE_AUCTIONS)
                lore = mutableListOf(
                    mm.deserialize("<gray>Browse all active auctions"),
                    Component.empty(),
                    mm.deserialize("<green>Click to open")
                )
                hideAllFlags()

                onClick { _, _ ->
                    player.sendMessage(mm.deserialize("<yellow>Active auctions browser - feature coming soon"))
                    ClickResult.CLOSE
                }
            })

            // Row 2: Blacklist management
            item(28, VItem(XMaterial.BARRIER) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_BLACKLIST)
                lore = mutableListOf(
                    mm.deserialize("<gray>Manage blacklisted items"),
                    Component.empty(),
                    mm.deserialize("<gray>Current: <white>${config.restrictions.blacklistedMaterials.size} items"),
                    Component.empty(),
                    mm.deserialize("<green>Click to manage")
                )
                hideAllFlags()

                onClick { _, _ ->
                    AdminBlacklistMenu(menuAPI, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            })

            // Row 2: Banned players
            item(30, VItem(XMaterial.WITHER_SKELETON_SKULL) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_BANNED_PLAYERS)
                lore = mutableListOf(
                    mm.deserialize("<gray>Manage banned players"),
                    Component.empty(),
                    mm.deserialize("<green>Click to manage")
                )
                hideAllFlags()

                onClick { _, _ ->
                    AdminBannedPlayersMenu(menuAPI, config, translationAPI, plugin, player, playerBanRepository).open()
                    ClickResult.CLOSE
                }
            })

            // Row 2: Statistics
            item(32, VItem(XMaterial.BOOK) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_STATISTICS)
                lore = mutableListOf(
                    mm.deserialize("<gray>View server statistics"),
                    Component.empty(),
                    mm.deserialize("<green>Click to view")
                )
                hideAllFlags()

                onClick { _, _ ->
                    showStatistics()
                    ClickResult.CLOSE
                }
            })

            // Row 3: Purge data
            item(37, VItem(XMaterial.TNT) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_PURGE_DATA)
                lore = mutableListOf(
                    mm.deserialize("<red>Delete old records"),
                    Component.empty(),
                    mm.deserialize("<green>Click to purge")
                )
                hideAllFlags()

                onClick { _, _ ->
                    player.performCommand("ahadmin purge 30")
                    ClickResult.CLOSE
                }
            })

            // Row 3: Reload config
            item(41, VItem(XMaterial.REPEATER) {
                name = translationAPI.getComponentSync(GuiMessages.ADMIN_RELOAD_CONFIG)
                lore = mutableListOf(
                    mm.deserialize("<yellow>Reload configuration"),
                    Component.empty(),
                    mm.deserialize("<green>Click to reload")
                )
                hideAllFlags()

                onClick { _, _ ->
                    player.performCommand("ahadmin reload")
                    ClickResult.CLOSE
                }
            })

            // Row 5: Close button
            item(49, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            })
        }

        menuAPI.open(menu, player)
    }

    private fun showStatistics() {
        val activeAuctions = auctionRepository?.let { runBlocking { it.getActiveAuctionsCount() } } ?: 0

        player.sendMessage(mm.deserialize(
            "<green>=== AuctionHouse Statistics ===</green>\n" +
            "<gray>Active Auctions: <white>$activeAuctions</white>\n" +
            "<gray>Active Orders: <white>0</white>\n" +
            "<gray>Language: <white>${config.language}</white>\n" +
            "<gray>Database: <white>${config.database.type}</white>"
        ))
    }
}
