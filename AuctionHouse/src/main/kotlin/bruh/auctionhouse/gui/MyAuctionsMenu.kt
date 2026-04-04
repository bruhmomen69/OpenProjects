package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.service.ServiceResult
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Menu for viewing and managing player's own auctions.
 */
class MyAuctionsMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<Auction>() {

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
        background = MenuUtils.backgroundItem()
        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }

        itemRenderer = { auction, _ -> createMyAuctionItem(auction) }

        asyncData<List<Auction>> {
            load { pctx.auctionService.getPlayerAuctions(pctx.player.uniqueId, null) }
            onLoaded { auctions -> dataSource = auctions }
        }
    }

    override fun populateItems() {
        items.clear()

        // Back button
        items[49] = MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        }
    }

    private fun createMyAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(pctx.mm.deserialize("<gray>ID: <white>${auction.shortId}"))
        loreList.add(Component.empty())
        loreList.add(pctx.mm.deserialize("<gray>Status: ${getStatusColor(auction.status)}${auction.status}"))

        if (auction.status == AuctionStatus.ACTIVE) {
            loreList.add(pctx.mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<red>Click to cancel"))
        } else if (auction.status == AuctionStatus.SOLD) {
            loreList.add(pctx.mm.deserialize("<gray>Sold for: <gold>${MenuUtils.formatPrice(auction.finalPrice ?: 0.0, pctx.economy)}"))
            auction.soldToName?.let {
                loreList.add(pctx.mm.deserialize("<gray>To: <white>$it"))
            }
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to view details"))
        } else if (auction.status == AuctionStatus.EXPIRED) {
            loreList.add(pctx.mm.deserialize("<gray>Expired at: <white>${auction.endsAt}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<yellow>Click to view details"))
        } else if (auction.status == AuctionStatus.CANCELLED) {
            loreList.add(pctx.mm.deserialize("<gray>Cancelled"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Click to view details"))
        }

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                pctx.mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                when (auction.status) {
                    AuctionStatus.ACTIVE -> {
                        controls.runAsync(
                            action = { pctx.auctionService.cancelAuction(pctx.player, auction.id) },
                            onSuccess = { result ->
                                when (result) {
                                    is ServiceResult.Success -> {
                                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(
                                            AuctionMessages.AUCTION_CANCELLED
                                        ))
                                    }
                                    is ServiceResult.Failure -> {
                                        pctx.player.sendMessage(result.message)
                                    }
                                }
                                // Reload auctions after cancel
                                controls.runAsync(
                                    action = { pctx.auctionService.getPlayerAuctions(pctx.player.uniqueId, null) },
                                    onSuccess = { auctions -> dataSource = auctions }
                                )
                            }
                        )
                        ClickResult.Deny
                    }
                    AuctionStatus.SOLD, AuctionStatus.EXPIRED, AuctionStatus.CANCELLED -> {
                        ClickResult.SwitchMenu(createAuctionDetailsMenu(auction))
                    }
                }
            }
        }
    }

    private fun createAuctionDetailsMenu(auction: Auction): SimpleMenu {
        return SimpleMenu().apply {
            rows = 5
            title = pctx.mm.deserialize("<yellow>Auction Details")

            background = MenuUtils.backgroundItem()

            // Display the auction item
            item(13, VItem(XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)) {
                name = auction.itemDisplayName?.let { pctx.mm.deserialize(it) } ?: Component.text(auction.itemMaterial.replace("_", " "))
                val lore = mutableListOf<Component>()
                lore.add(Component.empty())
                lore.add(pctx.mm.deserialize("<gray>Status: ${getStatusColor(auction.status)}${auction.status}"))

                if (auction.status == AuctionStatus.SOLD) {
                    lore.add(pctx.mm.deserialize("<gray>Sold for: <gold>${MenuUtils.formatPrice(auction.finalPrice ?: 0.0, pctx.economy)}"))
                    auction.soldToName?.let { lore.add(pctx.mm.deserialize("<gray>Buyer: <white>$it")) }
                    auction.soldAt?.let { lore.add(pctx.mm.deserialize("<gray>Sold at: <white>$it")) }
                } else if (auction.status == AuctionStatus.EXPIRED) {
                    lore.add(pctx.mm.deserialize("<gray>Expired at: <white>${auction.endsAt}"))
                } else if (auction.status == AuctionStatus.CANCELLED) {
                    lore.add(pctx.mm.deserialize("<gray>Cancelled"))
                }

                lore.add(pctx.mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
                lore.add(pctx.mm.deserialize("<gray>Views: <white>${auction.viewCount}"))
            })

            // Back button
            item(49, MenuUtils.backButton(pctx.translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(MyAuctionsMenu(pctx))
                }
            })
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
