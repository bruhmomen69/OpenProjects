package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
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
import java.util.UUID

/**
 * Menu for viewing a specific player's auctions.
 */
class AdminViewPlayerMenu(
    private val menuAPI: MenuAPI,
    private val auctionRepository: AuctionRepository?,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) : bruh.zchat.utils.menuapi.SimpleMenu() {
    private val mm = MiniMessage.miniMessage()

    fun createMenu(): Menu {
        return this.apply {
            items.clear()
            rows = 5
            title = mm.deserialize("<red>Select Player")

            background = MenuUtils.backgroundItem()

            item(13, VItem(XMaterial.PLAYER_HEAD) {
                name = mm.deserialize("<yellow>Enter Player Name")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to enter a player name"),
                    Component.empty(),
                    mm.deserialize("<gray>View their auctions")
                )
                hideAllFlags()

                onClick { _, _ ->
                    var nextMenu: Menu? = null
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter player name")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val offlinePlayer = Bukkit.getOfflinePlayer(result.value)
                                if (offlinePlayer.uniqueId != null) {
                                    PlayerStateManager.setAdminTarget(player.uniqueId, offlinePlayer.uniqueId, offlinePlayer.name ?: result.value)
                                    nextMenu = createAuctionsMenu(offlinePlayer.name ?: result.value)
                                } else {
                                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND) {
                                        unparsed("player", result.value)
                                    })
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    nextMenu?.let { ClickResult.SwitchMenu(it) } ?: ClickResult.Close
                }
            })

            item(40, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(
                        AdminDashboardMenu(menuAPI, auctionRepository, null, config, translationAPI, plugin, player).createMenu()
                    )
                }
            })

            item(44, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.Close }
            })
        }
    }

    private fun createAuctionsMenu(playerName: String): Menu {
        val adminTarget = PlayerStateManager.getAdminTarget(player.uniqueId)
        val currentFilter = PlayerStateManager.getAdminAuctionStatusFilter(player.uniqueId)

        val auctions = auctionRepository?.let { repo ->
            runBlocking {
                adminTarget?.let { (uuid, _) ->
                    repo.getPlayerAuctions(uuid, currentFilter)
                }
            }
        } ?: emptyList()

        return bruh.zchat.utils.menuapi.SimpleMenu().apply {
            rows = 6
            title = mm.deserialize("<red>$playerName's Auctions")

            background = MenuUtils.backgroundItem()

            // Filter buttons
            item(10, createFilterButton(playerName, AuctionStatus.ACTIVE, XMaterial.LIME_WOOL, "Active"))
            item(12, createFilterButton(playerName, AuctionStatus.SOLD, XMaterial.GOLD_INGOT, "Sold"))
            item(14, createFilterButton(playerName, AuctionStatus.EXPIRED, XMaterial.GRAY_WOOL, "Expired"))
            item(16, createFilterButton(playerName, null, XMaterial.COMPASS, "All"))

            // Auction display area
            if (auctions.isEmpty()) {
                item(22, VItem(XMaterial.BARRIER) {
                    name = mm.deserialize("<red>No Auctions Found")
                    lore = mutableListOf(mm.deserialize("<gray>This player has no auctions"))
                    hideAllFlags()
                })
            } else {
                auctions.take(14).forEachIndexed { index, auction ->
                    val slot = 19 + index
                    if (slot < 53) {
                        item(slot, createAuctionItem(playerName, auction))
                    }
                }
            }

            // Back button
            item(49, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            // Close button
            item(53, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.Close }
            })
        }
    }

    private fun createFilterButton(playerName: String, status: AuctionStatus?, material: XMaterial, label: String): VItem {
        val currentFilter = PlayerStateManager.getAdminAuctionStatusFilter(player.uniqueId)
        val isSelected = currentFilter == status
        return VItem(material) {
            name = mm.deserialize(
                if (isSelected) "<green><bold>$label" else "<gray>$label"
            )
            lore = mutableListOf(mm.deserialize("<gray>Click to filter"))
            hideAllFlags()

            onClick { _, _ ->
                PlayerStateManager.setAdminAuctionStatusFilter(player.uniqueId, status)
                ClickResult.SwitchMenu(createAuctionsMenu(playerName))
            }
        }
    }

    private fun createAuctionItem(playerName: String, auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        return VItem(material) {
            name = mm.deserialize("<yellow>${auction.itemMaterial}")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Price: <gold>${auction.startPrice}"))
            loreList.add(mm.deserialize("<gray>Status: <white>${auction.status}"))
            loreList.add(mm.deserialize("<gray>Seller: <white>${auction.sellerName}"))
            if (auction.soldToName != null) {
                loreList.add(mm.deserialize("<gray>Buyer: <white>${auction.soldToName}"))
            }
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<red>Click to cancel"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                player.performCommand("ahadmin cancel ${auction.id}")
                ClickResult.SwitchMenu(createAuctionsMenu(playerName))
            }
        }
    }
}
