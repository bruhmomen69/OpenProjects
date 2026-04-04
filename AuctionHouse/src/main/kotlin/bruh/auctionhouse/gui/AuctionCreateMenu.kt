package bruh.auctionhouse.gui

import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptDoubleAsync
import bruh.zchat.utils.menuapi.promptIntAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.time.Duration

/**
 * Menu for creating new auctions.
 * Caller is responsible for verifying the player is holding a valid item before opening.
 */
class AuctionCreateMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {
    private var auctionItem = pctx.player.inventory.itemInMainHand
    private var startPrice = 100.0
    private var binPrice: Double? = null
    private var duration = Duration.ofHours(pctx.config.auctions.defaultDuration.toLong())
    private var anonymous by menuState(false)
    private var auctionType by menuState(AuctionType.BOTH)

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.CREATE_AUCTION_TITLE)
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        item(13, createItemDisplay())
        item(29, createTypeButton())
        item(30, createStartPriceButton())
        item(31, createBinPriceButton())
        item(32, createDurationButton())
        item(33, createAnonymousButton())

        // Row 5: Navigation
        item(45, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.SwitchMenu(AuctionHouseMenu(pctx)) }
        })
        item(49, createConfirmButton())
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
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
            name = pctx.mm.deserialize("<yellow>Type: <white>$typeName")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                auctionType = when (auctionType) {
                    AuctionType.AUCTION -> AuctionType.BIN
                    AuctionType.BIN -> AuctionType.BOTH
                    AuctionType.BOTH -> AuctionType.AUCTION
                }
                ClickResult.Deny
            }
        }
    }

    private fun createStartPriceButton(): VItem {
        return VItem(XMaterial.GOLD_NUGGET) {
            name = pctx.mm.deserialize("<yellow>Start Price: <gold>${MenuUtils.formatPrice(startPrice, pctx.economy)}")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptDoubleAsync(
                    pctx.player,
                    "Enter Start Price",
                    startPrice,
                    pctx.config.auctions.minStartPrice,
                    pctx.config.auctions.maxStartPrice
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> startPrice = result.value
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@AuctionCreateMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createBinPriceButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            val loreList = mutableListOf<Component>()

            if (binPrice != null) {
                name = pctx.mm.deserialize("<green>BIN Price: <gold>${MenuUtils.formatPrice(binPrice!!, pctx.economy)}")
            } else {
                name = pctx.mm.deserialize("<gray>BIN Price: <red>Not Set")
            }
            loreList.add(pctx.mm.deserialize("<gray>Click to set"))
            loreList.add(pctx.mm.deserialize("<gray>Right-click to clear"))
            lore = loreList
            hideAllFlags()

            onClick { ctx, _ ->
                if (ctx.isRightClick) {
                    binPrice = null
                    ClickResult.Refresh
                } else {
                    val minBinPrice = if (auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH) {
                        startPrice.coerceAtLeast(pctx.config.auctions.minStartPrice)
                    } else {
                        pctx.config.auctions.minStartPrice
                    }

                    pctx.menuAPI.promptDoubleAsync(
                        pctx.player,
                        "Enter BIN Price",
                        binPrice,
                        minBinPrice,
                        pctx.config.auctions.maxStartPrice
                    ).thenAccept { result ->
                        when (result) {
                            is AnvilInputResult.Success -> {
                                if ((auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH) && result.value <= startPrice) {
                                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.BIN_PRICE_MUST_BE_GREATER))
                                } else {
                                    binPrice = result.value
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                            pctx.menuAPI.open(this@AuctionCreateMenu, pctx.player)
                        })
                    }
                    ClickResult.Deny
                }
            }
        }
    }

    private fun createDurationButton(): VItem {
        return VItem(XMaterial.CLOCK) {
            name = pctx.mm.deserialize("<yellow>Duration: <white>${duration.toHours()}h")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to change"))
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptIntAsync(
                    pctx.player,
                    "Enter Duration (hours)",
                    duration.toHours().toInt(),
                    1,
                    pctx.config.auctions.maxDuration
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> duration = Duration.ofHours(result.value.toLong())
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@AuctionCreateMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createAnonymousButton(): VItem {
        val material = if (anonymous) XMaterial.LIME_DYE else XMaterial.GRAY_DYE
        return VItem(material) {
            name = pctx.mm.deserialize("<yellow>Anonymous: <white>${if (anonymous) "Yes" else "No"}")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Click to toggle"))
            hideAllFlags()

            onClick { _, _ ->
                anonymous = !anonymous
                ClickResult.Deny
            }
        }
    }

    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.LIME_WOOL) {
            name = pctx.mm.deserialize("<green>Confirm")
            val loreList = mutableListOf<Component>()

            val listingFee = pctx.config.auctions.listingFee
            val fee = when (listingFee.type.uppercase()) {
                "PERCENTAGE" -> startPrice * (listingFee.amount / 100.0)
                else -> listingFee.amount
            }.coerceIn(listingFee.minFee, listingFee.maxFee)

            val totalFee = if (anonymous && pctx.config.auctions.display.allowAnonymous) {
                fee + pctx.config.auctions.display.anonymousFee
            } else fee

            loreList.add(pctx.mm.deserialize("<gray>Listing Fee: <gold>${MenuUtils.formatPrice(totalFee, pctx.economy)}"))

            val binPriceTotal = binPrice ?: 0.0
            val totalValue = startPrice.coerceAtLeast(binPriceTotal)
            if (MenuUtils.isExpensiveAction(totalValue, pctx.config.gui.confirm.expensiveThreshold)) {
                loreList.add(Component.empty())
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.EXPENSIVE_TRANSACTION_WARNING))
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.EXPENSIVE_TRANSACTION_THRESHOLD) {
                    unparsed("threshold", MenuUtils.formatPrice(pctx.config.gui.confirm.expensiveThreshold, pctx.economy))
                })
            }

            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<green>Click to create auction"))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                val actualBinPrice = if (auctionType == AuctionType.BOTH || auctionType == AuctionType.BIN) binPrice else null

                val totalValue = startPrice.coerceAtLeast(binPrice ?: 0.0)
                if (MenuUtils.isExpensiveAction(totalValue, pctx.config.gui.confirm.expensiveThreshold)) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.CONFIRM_EXPENSIVE_AUCTION))
                }

                controls.runAsync(
                    action = {
                        pctx.auctionService.createAuction(
                            pctx.player, auctionItem, auctionType, startPrice, actualBinPrice, duration, anonymous
                        )
                    },
                    onSuccess = { result ->
                        pctx.player.sendMessage(result.message)
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        }
    }
}
