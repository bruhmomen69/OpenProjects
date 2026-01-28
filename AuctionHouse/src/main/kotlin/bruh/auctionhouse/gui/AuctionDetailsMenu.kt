package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Menu for viewing auction details and placing bids or buying.
 */
class AuctionDetailsMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val auction: Auction
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)

            background = MenuUtils.backgroundItem()

            // Display the auction item
            item(13, createAuctionDisplayItem())

            // Bid button (if applicable)
            if (auction.canBid()) {
                item(29, createBidButton())
            }

            // Buy Now button (if applicable)
            if (auction.canBuyNow()) {
                item(33, createBuyNowButton())
            }

            // Cancel button (if owner)
            if (auction.sellerUuid == player.uniqueId || player.hasPermission("auctionhouse.admin.cancel")) {
                item(31, createCancelButton())
            }

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, economy, player).open()
                    ClickResult.CLOSE
                }
            }
            item(36, backItem)

            // Close button
            val closeItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(44, closeItem)
        }

        menuAPI.open(menu, player)
    }

    private fun createAuctionDisplayItem(): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))

            val loreList = mutableListOf<Component>()
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Seller: <white>${if (auction.isAnonymous) "Anonymous" else auction.sellerName}"))

            if (auction.auctionType == AuctionType.AUCTION || auction.auctionType == AuctionType.BOTH) {
                loreList.add(mm.deserialize("<yellow>Current Bid: <gold>${MenuUtils.formatPrice(auction.startPrice, plugin.economy)}"))
                loreList.add(mm.deserialize("<gray>Increment: <white>${MenuUtils.formatPrice(auction.minIncrement, plugin.economy)}"))
            }

            auction.buyNowPrice?.let {
                loreList.add(mm.deserialize("<green>Buy Now: <gold>${MenuUtils.formatPrice(it, plugin.economy)}"))
            }

            loreList.add(mm.deserialize("<gray>Time Left: <yellow>${MenuUtils.formatTimeRemaining(auction.endsAt)}"))
            loreList.add(mm.deserialize("<gray>Bids: <white>${auction.bidCount}"))
            loreList.add(mm.deserialize("<gray>Views: <white>${auction.viewCount}"))

            lore = loreList
        }
    }

    private fun createBidButton(): VItem {
        return VItem(XMaterial.GOLD_INGOT) {
            name = mm.deserialize("<green>Place Bid")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to place a bid"))
            loreList.add(mm.deserialize("<gray>Minimum increment: ${MenuUtils.formatPrice(auction.minIncrement, plugin.economy)}"))
            lore = loreList

            onClick { _, _ ->
                // Use runBlocking since we're in a non-suspend context
                runBlocking {
                    val result = menuAPI.promptDouble(
                        player,
                        "Enter Bid Amount",
                        null,
                        auction.startPrice,
                        Double.MAX_VALUE
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val bidResult = auctionService.placeBid(player, auction.id, result.value)
                            player.sendMessage(bidResult.message)
                        }
                        is AnvilInputResult.Cancelled -> {
                            // User cancelled, do nothing
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createBuyNowButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Buy Now")
            val loreList = mutableListOf<Component>()
            auction.buyNowPrice?.let { price ->
                loreList.add(mm.deserialize("<gray>Price: <gold>${MenuUtils.formatPrice(price, plugin.economy)}"))
            }
            loreList.add(mm.deserialize("<gray>Click to purchase instantly"))
            lore = loreList

            onClick { _, _ ->
                runBlocking {
                    val result = auctionService.buyNow(player, auction.id)
                    player.sendMessage(result.message)
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.RED_WOOL) {
            name = mm.deserialize("<red>Cancel Auction")
            lore = mutableListOf(mm.deserialize("<gray>Click to cancel this auction"))

            onClick { _, _ ->
                runBlocking {
                    val result = auctionService.cancelAuction(player, auction.id)
                    when (result) {
                        is bruh.auctionhouse.service.ServiceResult.Success -> {
                            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED))
                        }
                        is bruh.auctionhouse.service.ServiceResult.Failure -> {
                            player.sendMessage(result.message)
                        }
                    }
                }
                ClickResult.CLOSE
            }
        }
    }
}
