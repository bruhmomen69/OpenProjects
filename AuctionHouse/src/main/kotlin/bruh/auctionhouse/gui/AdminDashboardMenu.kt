package bruh.auctionhouse.gui

import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Admin dashboard menu for managing the auction house.
 */
class AdminDashboardMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    private var activeAuctions by menuState(0)

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_DASHBOARD_TITLE)
        background = MenuUtils.backgroundItem()

        asyncData<Int> {
            load { pctx.auctionRepository.getActiveAuctionsCount() }
            onLoaded { count -> activeAuctions = count }
        }
    }

    override fun populateItems() {
        items.clear()

        // Row 0: Title
        item(4, VItem(XMaterial.COMMAND_BLOCK) {
            name = pctx.mm.deserialize("<red><bold>Admin Dashboard")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Manage auctions and players"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Active Auctions: <white>$activeAuctions")
            )
            hideAllFlags()
        })

        // Row 1: Player management
        item(10, VItem(XMaterial.PLAYER_HEAD) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_VIEW_PLAYER)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>View a player's auctions"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to open")
            )
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminViewPlayerMenu(pctx))
            }
        })

        // Row 1: Search auctions
        item(12, VItem(XMaterial.COMPASS) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_SEARCH_AUCTIONS)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Search for auctions"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to search")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Search auctions by item name or seller").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.FEATURE_COMING_SOON) {
                                unparsed("feature", "Search for '${result.value}'")
                            })
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.Deny
            }
        })

        // Row 1: Active auctions
        item(14, VItem(XMaterial.CHEST) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_ACTIVE_AUCTIONS)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Browse all active auctions"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to open")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.FEATURE_COMING_SOON) {
                    unparsed("feature", "Active auctions browser")
                })
                ClickResult.Close
            }
        })

        // Row 2: Blacklist management
        item(28, VItem(XMaterial.BARRIER) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_BLACKLIST)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Manage blacklisted items"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Current: <white>${pctx.config.restrictions.blacklistedMaterials.size} items"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to manage")
            )
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminBlacklistMenu(pctx))
            }
        })

        // Row 2: Banned players
        item(30, VItem(XMaterial.WITHER_SKELETON_SKULL) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_BANNED_PLAYERS)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Manage banned players"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to manage")
            )
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminBannedPlayersMenu(pctx))
            }
        })

        // Row 2: Statistics
        item(32, VItem(XMaterial.BOOK) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_STATISTICS)
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>View server statistics"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to view")
            )
            hideAllFlags()

            onClick { _, controls ->
                controls.runAsync(
                    action = { pctx.auctionRepository.getActiveAuctionsCount() },
                    onSuccess = { count ->
                        pctx.player.sendMessage(pctx.mm.deserialize(
                            "<green>=== AuctionHouse Statistics ===</green>\n" +
                            "<gray>Active Auctions: <white>$count</white>\n" +
                            "<gray>Active Orders: <white>0</white>\n" +
                            "<gray>Language: <white>${pctx.config.language}</white>\n" +
                            "<gray>Database: <white>${pctx.config.database.type}</white>"
                        ))
                        pctx.player.closeInventory()
                    },
                    onError = { e ->
                        pctx.player.sendMessage(pctx.mm.deserialize("<red>Failed to load statistics: ${e.message}"))
                        pctx.player.closeInventory()
                    }
                )
                ClickResult.Deny
            }
        })

        // Row 3: Purge data
        item(37, VItem(XMaterial.TNT) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_PURGE_DATA)
            lore = mutableListOf(
                pctx.mm.deserialize("<red>Delete old records"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to purge")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.player.performCommand("ahadmin purge 30")
                ClickResult.Close
            }
        })

        // Row 3: Reload config
        item(41, VItem(XMaterial.REPEATER) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_RELOAD_CONFIG)
            lore = mutableListOf(
                pctx.mm.deserialize("<yellow>Reload configuration"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to reload")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.player.performCommand("ahadmin reload")
                ClickResult.Close
            }
        })

        // Row 5: Close button
        item(49, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }
}
