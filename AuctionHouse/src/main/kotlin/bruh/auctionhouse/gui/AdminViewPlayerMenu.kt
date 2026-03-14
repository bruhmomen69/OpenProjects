package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

/**
 * Menu for viewing a specific player's auctions.
 */
class AdminViewPlayerMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    init {
        rows = 5
        title = pctx.mm.deserialize("<red>Select Player")
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        item(13, VItem(XMaterial.PLAYER_HEAD) {
            name = pctx.mm.deserialize("<yellow>Enter Player Name")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to enter a player name"),
                Component.empty(),
                pctx.mm.deserialize("<gray>View their auctions")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Enter player name").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val offlinePlayer = Bukkit.getOfflinePlayer(result.value)
                            if (offlinePlayer.uniqueId != null) {
                                PlayerStateManager.setAdminTarget(pctx.player.uniqueId, offlinePlayer.uniqueId, offlinePlayer.name ?: result.value)
                                pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                    pctx.menuAPI.open(
                                        AdminViewPlayerAuctionsMenu(pctx, offlinePlayer.name ?: result.value),
                                        pctx.player
                                    )
                                })
                            } else {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND) {
                                    unparsed("player", result.value)
                                })
                                pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                    pctx.menuAPI.open(this@AdminViewPlayerMenu, pctx.player)
                                })
                            }
                        }
                        is AnvilInputResult.Cancelled -> {
                            pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                                pctx.menuAPI.open(this@AdminViewPlayerMenu, pctx.player)
                            })
                        }
                    }
                }
                ClickResult.Deny
            }
        })

        item(40, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminDashboardMenu(pctx))
            }
        })

        item(44, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }
}

/**
 * Menu displaying a specific player's auctions with filter support.
 */
class AdminViewPlayerAuctionsMenu(
    private val pctx: PlayerMenuContext,
    private val playerName: String
) : SimpleMenu() {

    private var auctions by menuState<List<Auction>>(emptyList())
    private var currentFilter by menuState<AuctionStatus?>(
        PlayerStateManager.getAdminAuctionStatusFilter(pctx.player.uniqueId)
    )

    init {
        rows = 6
        title = pctx.mm.deserialize("<red>$playerName's Auctions")
        background = MenuUtils.backgroundItem()

        loadAuctions()
    }

    private fun loadAuctions() {
        val adminTarget = PlayerStateManager.getAdminTarget(pctx.player.uniqueId)
        asyncData<List<Auction>> {
            load {
                adminTarget?.let { (uuid, _) ->
                    pctx.auctionRepository.getPlayerAuctions(uuid, currentFilter)
                } ?: emptyList()
            }
            onLoaded { data -> auctions = data }
        }
    }

    override fun populateItems() {
        items.clear()

        // Filter buttons
        item(10, createFilterButton(AuctionStatus.ACTIVE, XMaterial.LIME_WOOL, "Active"))
        item(12, createFilterButton(AuctionStatus.SOLD, XMaterial.GOLD_INGOT, "Sold"))
        item(14, createFilterButton(AuctionStatus.EXPIRED, XMaterial.GRAY_WOOL, "Expired"))
        item(16, createFilterButton(null, XMaterial.COMPASS, "All"))

        // Auction display area
        if (auctions.isEmpty()) {
            item(22, VItem(XMaterial.BARRIER) {
                name = pctx.mm.deserialize("<red>No Auctions Found")
                lore = mutableListOf(pctx.mm.deserialize("<gray>This player has no auctions"))
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
        item(49, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminViewPlayerMenu(pctx))
            }
        })

        // Close button
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }

    private fun createFilterButton(status: AuctionStatus?, material: XMaterial, label: String): VItem {
        val isSelected = currentFilter == status
        return VItem(material) {
            name = pctx.mm.deserialize(
                if (isSelected) "<green><bold>$label" else "<gray>$label"
            )
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to filter"))
            hideAllFlags()

            onClick { _, controls ->
                PlayerStateManager.setAdminAuctionStatusFilter(pctx.player.uniqueId, status)
                currentFilter = status
                controls.runAsync(
                    action = {
                        val adminTarget = PlayerStateManager.getAdminTarget(pctx.player.uniqueId)
                        adminTarget?.let { (uuid, _) ->
                            pctx.auctionRepository.getPlayerAuctions(uuid, status)
                        } ?: emptyList()
                    },
                    onSuccess = { data -> auctions = data }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)
        return VItem(material) {
            name = pctx.mm.deserialize("<yellow>${auction.itemMaterial}")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Price: <gold>${auction.startPrice}"))
            loreList.add(pctx.mm.deserialize("<gray>Status: <white>${auction.status}"))
            loreList.add(pctx.mm.deserialize("<gray>Seller: <white>${auction.sellerName}"))
            if (auction.soldToName != null) {
                loreList.add(pctx.mm.deserialize("<gray>Buyer: <white>${auction.soldToName}"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<red>Click to cancel"))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                pctx.player.performCommand("ahadmin cancel ${auction.id}")
                controls.runAsync(
                    action = {
                        val adminTarget = PlayerStateManager.getAdminTarget(pctx.player.uniqueId)
                        adminTarget?.let { (uuid, _) ->
                            pctx.auctionRepository.getPlayerAuctions(uuid, currentFilter)
                        } ?: emptyList()
                    },
                    onSuccess = { data -> auctions = data }
                )
                ClickResult.Deny
            }
        }
    }
}
