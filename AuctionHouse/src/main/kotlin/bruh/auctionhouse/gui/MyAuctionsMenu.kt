package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Menu for viewing and managing player's own auctions.
 */
class MyAuctionsMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val auctions = runBlocking {
            auctionService.getPlayerAuctions(player.uniqueId, null)
        }

        val menu = menuAPI.paginated<Auction> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = auctions

            itemRenderer = { auction, _ ->
                createMyAuctionItem(auction)
            }

            background = MenuUtils.backgroundItem()

            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            }
            staticItems[49] = backItem
        }

        menuAPI.open(menu, player)
    }

    private fun createMyAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(mm.deserialize("<gray>Status: ${getStatusColor(auction.status)}${auction.status}"))

        if (auction.status == AuctionStatus.ACTIVE) {
            loreList.add(mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<red>Click to cancel"))
        } else if (auction.status == AuctionStatus.SOLD) {
            loreList.add(mm.deserialize("<gray>Sold for: <gold>${MenuUtils.formatPrice(auction.finalPrice ?: 0.0, plugin.economy)}"))
            auction.soldToName?.let {
                loreList.add(mm.deserialize("<gray>To: <white>$it"))
            }
        }

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                if (auction.status == AuctionStatus.ACTIVE) {
                    runBlocking {
                        val result = auctionService.cancelAuction(player, auction.id)
                        when (result) {
                            is bruh.auctionhouse.service.ServiceResult.Success -> {
                                player.sendMessage(translationAPI.getComponentSync(
                                    AuctionMessages.AUCTION_CANCELLED
                                ))
                            }
                            is bruh.auctionhouse.service.ServiceResult.Failure -> {
                                player.sendMessage(result.message)
                            }
                        }
                    }
                    // Refresh menu
                    open()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun getStatusColor(status: AuctionStatus): String {
        return when (status) {
            AuctionStatus.ACTIVE -> "<green>"
            AuctionStatus.SOLD -> "<gold>"
            AuctionStatus.EXPIRED -> "<red>"
            AuctionStatus.CANCELLED -> "<gray>"
        }
    }
}
