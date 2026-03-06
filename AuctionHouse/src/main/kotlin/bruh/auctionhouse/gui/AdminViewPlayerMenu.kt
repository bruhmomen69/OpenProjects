package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
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
) {
    private val mm = MiniMessage.miniMessage()
    private var targetPlayer: Player? = null
    private var targetPlayerUuid: UUID? = null
    private var currentFilter: AuctionStatus? = null

    fun open() {
        val menu = menuAPI.simple {
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
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter player name")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val offlinePlayer = Bukkit.getOfflinePlayer(result.value)
                                if (offlinePlayer.uniqueId != null) {
                                    targetPlayerUuid = offlinePlayer.uniqueId
                                    openAuctionsMenu(offlinePlayer.name ?: result.value)
                                } else {
                                    player.sendMessage(mm.deserialize("<red>Player not found: ${result.value}"))
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    ClickResult.CLOSE
                }
            })

            item(40, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AdminDashboardMenu(menuAPI, auctionRepository, null, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            })

            item(44, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            })
        }

        menuAPI.open(menu, player)
    }

    private fun openAuctionsMenu(playerName: String) {
        val auctions = auctionRepository?.let { repo ->
            runBlocking {
                targetPlayerUuid?.let { uuid ->
                    repo.getPlayerAuctions(uuid, currentFilter)
                }
            }
        } ?: emptyList()

        val menu = menuAPI.simple {
            rows = 6
            title = mm.deserialize("<red>$playerName's Auctions")

            background = MenuUtils.backgroundItem()

            // Filter buttons
            item(10, createFilterButton(AuctionStatus.ACTIVE, XMaterial.LIME_WOOL, "Active"))
            item(12, createFilterButton(AuctionStatus.SOLD, XMaterial.GOLD_INGOT, "Sold"))
            item(14, createFilterButton(AuctionStatus.EXPIRED, XMaterial.GRAY_WOOL, "Expired"))
            item(16, createFilterButton(null, XMaterial.COMPASS, "All"))

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
                        item(slot, createAuctionItem(auction))
                    }
                }
            }

            // Back button
            item(49, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    open()
                    ClickResult.CLOSE
                }
            })

            // Close button
            item(53, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            })
        }

        menuAPI.open(menu, player)
    }

    private fun createFilterButton(status: AuctionStatus?, material: XMaterial, label: String): VItem {
        val isSelected = currentFilter == status
        return VItem(material) {
            name = mm.deserialize(
                if (isSelected) "<green><bold>$label" else "<gray>$label"
            )
            lore = mutableListOf(mm.deserialize("<gray>Click to filter"))
            hideAllFlags()

            onClick { _, _ ->
                currentFilter = status
                targetPlayerUuid?.let { uuid ->
                    val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
                    openAuctionsMenu(offlinePlayer.name ?: "Unknown")
                }
                ClickResult.ALLOW
            }
        }
    }

    private fun createAuctionItem(auction: Auction): VItem {
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
                openAuctionsMenu(auction.sellerName)
                ClickResult.CLOSE
            }
        }
    }
}
