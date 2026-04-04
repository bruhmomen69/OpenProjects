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
 * Menu for creating bulk auctions.
 */
class BulkListMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    private var auctionItem = pctx.player.inventory.itemInMainHand
    private var quantity by menuState(1)
    private var startPrice by menuState(100.0)
    private var binPrice by menuState<Double?>(null)
    private var duration by menuState(Duration.ofHours(pctx.config.auctions.defaultDuration.toLong()))
    private var anonymous by menuState(false)
    private var auctionType = AuctionType.BOTH

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_TITLE)
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        val maxQuantity = pctx.config.auctions.bulkListing.maxBulkListings
        val feePerItem = calculateFeePerItem()
        val totalFee = feePerItem * quantity

        // Row 0: Title
        item(4, VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow><bold>Bulk Listing")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Create multiple auctions at once"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Max: ${maxQuantity} auctions")
            )
            hideAllFlags()
        })

        // Row 1: Item Display
        item(10, createItemDisplay())

        // Row 1: Quantity Display
        item(13, createQuantityDisplay())

        // Row 1: Per-Item Settings
        item(16, createPerItemSettings())

        // Row 2: Quantity Controls
        item(19, VItem(XMaterial.RED_CONCRETE) {
            name = pctx.mm.deserialize("<red>-64")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Decrease quantity by 64"))
            hideAllFlags()

            onClick { _, _ ->
                quantity = (quantity - 64).coerceAtLeast(1)
                ClickResult.Deny
            }
        })

        item(20, VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>-1")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Decrease quantity by 1"))
            hideAllFlags()

            onClick { _, _ ->
                quantity = (quantity - 1).coerceAtLeast(1)
                ClickResult.Deny
            }
        })

        item(22, VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow>Quantity: <gold>$quantity")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to set"),
                pctx.mm.deserialize("<gray>Max: ${maxQuantity}")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptIntAsync(
                    pctx.player,
                    "Enter Quantity",
                    quantity,
                    1,
                    maxQuantity
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> quantity = result.value
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@BulkListMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        })

        item(24, VItem(XMaterial.LIME_WOOL) {
            name = pctx.mm.deserialize("<green>+1")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Increase quantity by 1"))
            hideAllFlags()

            onClick { _, _ ->
                quantity = (quantity + 1).coerceAtMost(maxQuantity)
                ClickResult.Deny
            }
        })

        item(25, VItem(XMaterial.LIME_CONCRETE) {
            name = pctx.mm.deserialize("<green>+64")
            lore = mutableListOf(pctx.mm.deserialize("<gray>Increase quantity by 64"))
            hideAllFlags()

            onClick { _, _ ->
                quantity = (quantity + 64).coerceAtMost(maxQuantity)
                ClickResult.Deny
            }
        })

        // Row 3: Pricing and Settings
        item(28, createStartPriceButton())
        item(30, createBinPriceButton())
        item(32, createDurationButton())
        item(34, createAnonymousButton())

        // Row 4: Info & Action
        item(39, VItem(XMaterial.GOLD_INGOT) {
            name = pctx.mm.deserialize("<yellow>Fee Preview")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Fee per auction: <gold>${MenuUtils.formatPrice(feePerItem, pctx.economy)}"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gold>Total Fees: <gold>${MenuUtils.formatPrice(totalFee, pctx.economy)}"))
            loreList.add(Component.empty())
            if (pctx.config.auctions.bulkListing.feeDiscountPercent > 0) {
                loreList.add(pctx.mm.deserialize("<green>Bulk Discount: ${pctx.config.auctions.bulkListing.feeDiscountPercent}%"))
            }
            lore = loreList
            hideAllFlags()
        })

        // Row 4: Create Action
        item(40, VItem(XMaterial.LIME_WOOL) {
            name = pctx.mm.deserialize("<green><bold>Create All Auctions")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Creates $quantity auctions"))
            loreList.add(pctx.mm.deserialize("<gray>Total cost: <gold>${MenuUtils.formatPrice(totalFee, pctx.economy)}"))
            loreList.add(Component.empty())

            if (quantity > 10) {
                loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_WARNING) {
                    unparsed("count", quantity.toString())
                })
                loreList.add(Component.empty())
            }

            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_CONFIRM) {
                unparsed("count", quantity.toString())
            })
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                val itemInHand = pctx.player.inventory.itemInMainHand
                if (itemInHand.amount < quantity) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.BULK_LISTING_NO_ITEMS))
                    return@onClick ClickResult.Deny
                }

                if (quantity > 10) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_CONFIRMATION) {
                        unparsed("count", quantity.toString())
                    })
                }

                val actualBinPrice = if (auctionType == AuctionType.BOTH || auctionType == AuctionType.BIN) binPrice else null
                controls.runAsync(
                    action = {
                        pctx.auctionService.createBulkAuctions(
                            pctx.player,
                            auctionItem,
                            quantity,
                            auctionType,
                            startPrice,
                            actualBinPrice,
                            duration,
                            anonymous
                        )
                    },
                    onSuccess = { result ->
                        pctx.player.sendMessage(result.message)
                        controls.close()
                    }
                )
                ClickResult.Deny
            }
        })

        // Back button
        item(45, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        })

        // Close button
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.Close
            }
        })
    }

    private fun createItemDisplay(): VItem {
        val material = XMaterial.matchXMaterial(auctionItem.type.name).orElse(XMaterial.STONE)
        return VItem(material) {
            name = auctionItem.itemMeta?.displayName()
                ?: Component.text(auctionItem.type.name.replace("_", " "))
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Item being listed"),
                pctx.mm.deserialize("<gray>Amount in hand: <white>${auctionItem.amount}")
            )
            hideAllFlags()
        }
    }

    private fun createQuantityDisplay(): VItem {
        return VItem(XMaterial.PAPER) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_QUANTITY) {
                unparsed("quantity", quantity.toString())
            }
            val stacks = (quantity + 63) / 64
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_STACKS) {
                unparsed("stacks", stacks.toString())
            })
            loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.BULK_LISTING_TOTAL_ITEMS) {
                unparsed("total", quantity.toString())
            })
            lore = loreList
            hideAllFlags()
        }
    }

    private fun createPerItemSettings(): VItem {
        return VItem(XMaterial.BOOK) {
            name = pctx.mm.deserialize("<yellow>Per-Item Settings")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Each auction will contain:"))
            loreList.add(pctx.mm.deserialize("<white>• 1 item"))
            loreList.add(Component.empty())
            loreList.add(pctx.mm.deserialize("<gray>Same settings for all auctions"))
            lore = loreList
            hideAllFlags()
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
                        pctx.menuAPI.open(this@BulkListMenu, pctx.player)
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
                    return@onClick ClickResult.Deny
                }

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
                        pctx.menuAPI.open(this@BulkListMenu, pctx.player)
                    })
                }
                ClickResult.Deny
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
                        pctx.menuAPI.open(this@BulkListMenu, pctx.player)
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

    private fun calculateFeePerItem(): Double {
        val listingFee = pctx.config.auctions.listingFee
        val fee = when (listingFee.type.uppercase()) {
            "PERCENTAGE" -> startPrice * (listingFee.amount / 100.0)
            else -> listingFee.amount
        }.coerceIn(listingFee.minFee, listingFee.maxFee)

        val totalFee = if (anonymous && pctx.config.auctions.display.allowAnonymous) {
            fee + pctx.config.auctions.display.anonymousFee
        } else fee

        val discount = pctx.config.auctions.bulkListing.feeDiscountPercent / 100.0
        return totalFee * (1.0 - discount)
    }
}
