package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDouble
import bruh.zchat.utils.menuapi.promptInt
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration

/**
 * Menu for creating new auctions.
 */
class AuctionCreateMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var auctionItem = player.inventory.itemInMainHand
    private var startPrice = 100.0
    private var binPrice: Double? = null
    private var duration = Duration.ofHours(config.auctions.defaultDuration.toLong())
    private var anonymous = false
    private var auctionType = AuctionType.BOTH

    fun open() {
        // Check if player is holding an item
        if (auctionItem.type.isAir) {
            player.sendMessage(mm.deserialize("<red>You must hold an item to sell!"))
            return
        }

        refreshMenu()
    }

    private fun refreshMenu() {
        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.CREATE_AUCTION_TITLE)

            background = MenuUtils.backgroundItem()

            // Item slot
            item(13, createItemDisplay())

            // Auction type selector
            item(29, createTypeButton())

            // Start price
            item(30, createStartPriceButton())

            // BIN price
            item(31, createBinPriceButton())

            // Duration
            item(32, createDurationButton())

            // Anonymous toggle
            item(33, createAnonymousButton())

            // Confirm button
            item(38, createConfirmButton())

            // Cancel button
            val cancelItem = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.CLOSE
                }
            }
            item(42, cancelItem)
        }

        menuAPI.open(menu, player)
    }

    private fun createItemDisplay(): VItem {
        val material = XMaterial.matchXMaterial(auctionItem.type.name).orElse(XMaterial.STONE)
        return VItem(material) {
            name = auctionItem.itemMeta?.displayName()
                ?: Component.text(auctionItem.type.name.replace("_", " "))
            hideAllFlags()
        }
    }

    private fun createTypeButton(): VItem {
        val (material, typeName) = when (auctionType) {
            AuctionType.AUCTION -> XMaterial.GOLD_INGOT to "Auction Only"
            AuctionType.BIN -> XMaterial.EMERALD to "BIN Only"
            AuctionType.BOTH -> XMaterial.DIAMOND to "Auction + BIN"
        }

        return VItem(material) {
            name = mm.deserialize("<yellow>Type: <white>$typeName")
            lore = mutableListOf(mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                auctionType = when (auctionType) {
                    AuctionType.AUCTION -> AuctionType.BIN
                    AuctionType.BIN -> AuctionType.BOTH
                    AuctionType.BOTH -> AuctionType.AUCTION
                }
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createStartPriceButton(): VItem {
        return VItem(XMaterial.GOLD_NUGGET) {
            name = mm.deserialize("<yellow>Start Price: <gold>${MenuUtils.formatPrice(startPrice, plugin.economy)}")
            lore = mutableListOf(mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptDouble(
                        player,
                        "Enter Start Price",
                        startPrice,
                        config.auctions.minStartPrice,
                        config.auctions.maxStartPrice
                    )
                    when (result) {
                        is AnvilInputResult.Success -> startPrice = result.value
                        is AnvilInputResult.Cancelled -> {}
                    }
                    refreshMenu()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createBinPriceButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            val loreList = mutableListOf<Component>()

            if (binPrice != null) {
                name = mm.deserialize("<green>BIN Price: <gold>${MenuUtils.formatPrice(binPrice!!, plugin.economy)}")
            } else {
                name = mm.deserialize("<gray>BIN Price: <red>Not Set")
            }
            loreList.add(mm.deserialize("<gray>Click to set"))
            loreList.add(mm.deserialize("<gray>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { ctx, _ ->
                if (ctx.isRightClick) {
                    binPrice = null
                    refreshMenu()
                    ClickResult.ALLOW
                } else {
                    runBlocking {
                        val result = menuAPI.promptDouble(
                            player,
                            "Enter BIN Price",
                            binPrice,
                            config.auctions.minStartPrice,
                            config.auctions.maxStartPrice
                        )
                        when (result) {
                            is AnvilInputResult.Success -> binPrice = result.value
                            is AnvilInputResult.Cancelled -> {}
                        }
                        refreshMenu()
                    }
                    ClickResult.CLOSE
                }
            }
        }
    }

    private fun createDurationButton(): VItem {
        return VItem(XMaterial.CLOCK) {
            name = mm.deserialize("<yellow>Duration: <white>${duration.toHours()}h")
            lore = mutableListOf(mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptInt(
                        player,
                        "Enter Duration (hours)",
                        duration.toHours().toInt(),
                        1,
                        config.auctions.maxDuration
                    )
                    when (result) {
                        is AnvilInputResult.Success -> duration = Duration.ofHours(result.value.toLong())
                        is AnvilInputResult.Cancelled -> {}
                    }
                    refreshMenu()
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createAnonymousButton(): VItem {
        val material = if (anonymous) XMaterial.LIME_DYE else XMaterial.GRAY_DYE
        return VItem(material) {
            name = mm.deserialize("<yellow>Anonymous: <white>${if (anonymous) "Yes" else "No"}")
            lore = mutableListOf(mm.deserialize("<gray>Click to toggle"))
            hideAllFlags()

            onClick { _, _ ->
                anonymous = !anonymous
                refreshMenu()
                ClickResult.ALLOW
            }
        }
    }

    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.LIME_WOOL) {
            name = mm.deserialize("<green>Confirm")
            val loreList = mutableListOf<Component>()

            // Calculate fee
            val listingFee = config.auctions.listingFee
            val fee = when (listingFee.type.uppercase()) {
                "PERCENTAGE" -> startPrice * (listingFee.amount / 100.0)
                else -> listingFee.amount
            }.coerceIn(listingFee.minFee, listingFee.maxFee)

            val totalFee = if (anonymous && config.auctions.display.allowAnonymous) {
                fee + config.auctions.display.anonymousFee
            } else fee

            loreList.add(mm.deserialize("<gray>Listing Fee: <gold>${MenuUtils.formatPrice(totalFee, plugin.economy)}"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<green>Click to create auction"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val actualBinPrice = if (auctionType == AuctionType.BOTH || auctionType == AuctionType.BIN) binPrice else null
                    val result = auctionService.createAuction(
                        player, auctionItem, auctionType, startPrice, actualBinPrice, duration, anonymous
                    )
                    player.sendMessage(result.message)

                    if (result.success) {
                        player.inventory.setItemInMainHand(null)
                    }
                }
                ClickResult.CLOSE
            }
        }
    }
}
