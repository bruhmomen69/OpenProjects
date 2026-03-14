package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.BulkListingResult
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
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
 * Menu for creating bulk auctions.
 */
class BulkListMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) : bruh.zchat.utils.menuapi.SimpleMenu() {
    private val mm = MiniMessage.miniMessage()
    private var auctionItem = player.inventory.itemInMainHand
    private var quantity = 1
    private var startPrice = 100.0
    private var binPrice: Double? = null
    private var duration = Duration.ofHours(config.auctions.defaultDuration.toLong())
    private var anonymous = false
    private var auctionType = AuctionType.BOTH

    fun createMenuOrNull(): Menu? {
        // Check if player is holding an item
        if (auctionItem.type.isAir) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.MUST_HOLD_ITEM))
            return null
        }

        // Check if bulk listing is enabled
        if (!config.auctions.bulkListing.enabled) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.BULK_LISTING_DISABLED))
            return null
        }

        return createMenu()
    }

    fun createMenu(): Menu {
        val maxQuantity = config.auctions.bulkListing.maxBulkListings
        val totalItems = quantity
        val feePerItem = calculateFeePerItem()
        val totalFee = feePerItem * quantity

        return this.apply {
            items.clear()
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.BULK_LISTING_TITLE)

            background = MenuUtils.backgroundItem()

            // Row 0: Title
            item(4, VItem(XMaterial.PAPER) {
                name = mm.deserialize("<yellow><bold>Bulk Listing")
                lore = mutableListOf(
                    mm.deserialize("<gray>Create multiple auctions at once"),
                    Component.empty(),
                    mm.deserialize("<gray>Max: ${maxQuantity} auctions")
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
                name = mm.deserialize("<red>-64")
                lore = mutableListOf(mm.deserialize("<gray>Decrease quantity by 64"))
                hideAllFlags()

                onClick { _, _ ->
                    quantity = (quantity - 64).coerceAtLeast(1)
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            item(20, VItem(XMaterial.RED_WOOL) {
                name = mm.deserialize("<red>-1")
                lore = mutableListOf(mm.deserialize("<gray>Decrease quantity by 1"))
                hideAllFlags()

                onClick { _, _ ->
                    quantity = (quantity - 1).coerceAtLeast(1)
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            item(22, VItem(XMaterial.PAPER) {
                name = mm.deserialize("<yellow>Quantity: <gold>$quantity")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to set"),
                    mm.deserialize("<gray>Max: ${maxQuantity}")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptInt(
                            player,
                            "Enter Quantity",
                            quantity,
                            1,
                            maxQuantity
                        )
                        when (result) {
                            is AnvilInputResult.Success -> quantity = result.value
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            item(24, VItem(XMaterial.LIME_WOOL) {
                name = mm.deserialize("<green>+1")
                lore = mutableListOf(mm.deserialize("<gray>Increase quantity by 1"))
                hideAllFlags()

                onClick { _, _ ->
                    quantity = (quantity + 1).coerceAtMost(maxQuantity)
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            item(25, VItem(XMaterial.LIME_CONCRETE) {
                name = mm.deserialize("<green>+64")
                lore = mutableListOf(mm.deserialize("<gray>Increase quantity by 64"))
                hideAllFlags()

                onClick { _, _ ->
                    quantity = (quantity + 64).coerceAtMost(maxQuantity)
                    ClickResult.SwitchMenu(createMenu())
                }
            })

            // Row 3: Pricing and Settings
            item(10, createStartPriceButton())

            item(14, createBinPriceButton())

            item(16, createDurationButton())

            item(19, createAnonymousButton())

            // Row 4: Fee Preview
            item(22, VItem(XMaterial.GOLD_INGOT) {
                name = mm.deserialize("<yellow>Fee Preview")
                val loreList = mutableListOf<Component>()
                loreList.add(mm.deserialize("<gray>Fee per auction: <gold>${MenuUtils.formatPrice(feePerItem, plugin.economy)}"))
                loreList.add(Component.empty())
                loreList.add(mm.deserialize("<gold>Total Fees: <gold>${MenuUtils.formatPrice(totalFee, plugin.economy)}"))
                loreList.add(Component.empty())
                if (config.auctions.bulkListing.feeDiscountPercent > 0) {
                    loreList.add(mm.deserialize("<green>Bulk Discount: ${config.auctions.bulkListing.feeDiscountPercent}%"))
                }
                lore = loreList
                hideAllFlags()
            })

            // Row 5: Action Buttons
            item(38, VItem(XMaterial.LIME_WOOL) {
                name = mm.deserialize("<green><bold>Create All Auctions")
                val loreList = mutableListOf<Component>()
                loreList.add(mm.deserialize("<gray>Creates $quantity auctions"))
                loreList.add(mm.deserialize("<gray>Total cost: <gold>${MenuUtils.formatPrice(totalFee, plugin.economy)}"))
                loreList.add(Component.empty())

                if (quantity > 10) {
                    loreList.add(translationAPI.getComponentSync(GuiMessages.BULK_LISTING_WARNING) {
                        unparsed("count", quantity.toString())
                    })
                    loreList.add(Component.empty())
                }

                loreList.add(translationAPI.getComponentSync(GuiMessages.BULK_LISTING_CONFIRM) {
                    unparsed("count", quantity.toString())
                })
                lore = loreList
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        // Check if player has enough items
                        val itemInHand = player.inventory.itemInMainHand
                        if (itemInHand.amount < quantity) {
                            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.BULK_LISTING_NO_ITEMS))
                            return@runBlocking
                        }

                        // Confirm if creating many auctions
                        if (quantity > 10) {
                            player.sendMessage(translationAPI.getComponentSync(GuiMessages.BULK_LISTING_CONFIRMATION) {
                                unparsed("count", quantity.toString())
                            })
                            // In a full implementation, we'd add a confirmation menu here
                        }

                        val actualBinPrice = if (auctionType == AuctionType.BOTH || auctionType == AuctionType.BIN) binPrice else null
                        val result = auctionService.createBulkAuctions(
                            player,
                            auctionItem,
                            quantity,
                            auctionType,
                            startPrice,
                            actualBinPrice,
                            duration,
                            anonymous
                        )

                        player.sendMessage(result.message)
                    }
                    ClickResult.Close
                }
            })

            // Back button - just close for now, can navigate to main menu
            item(45, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.SwitchMenu(
                        AuctionHouseMenu(
                            menuAPI,
                            auctionService,
                            plugin.orderService,
                            plugin.auctionRepository,
                            plugin.bidRepository,
                            plugin.orderRepository,
                            plugin.watchlistRepository,
                            config,
                            translationAPI,
                            plugin,
                            plugin.economy,
                            player
                        ).createMenu()
                    )
                }
            })

            // Close button
            item(53, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.Close
                }
            })
        }
    }

    private fun createItemDisplay(): VItem {
        val material = XMaterial.matchXMaterial(auctionItem.type.name).orElse(XMaterial.STONE)
        return VItem(material) {
            name = auctionItem.itemMeta?.displayName()
                ?: Component.text(auctionItem.type.name.replace("_", " "))
            lore = mutableListOf(
                mm.deserialize("<gray>Item being listed"),
                mm.deserialize("<gray>Amount in hand: <white>${auctionItem.amount}")
            )
            hideAllFlags()
        }
    }

    private fun createQuantityDisplay(): VItem {
        return VItem(XMaterial.PAPER) {
            name = translationAPI.getComponentSync(GuiMessages.BULK_LISTING_QUANTITY) {
                unparsed("quantity", quantity.toString())
            }
            val stacks = (quantity + 63) / 64
            val loreList = mutableListOf<Component>()
            loreList.add(translationAPI.getComponentSync(GuiMessages.BULK_LISTING_STACKS) {
                unparsed("stacks", stacks.toString())
            })
            loreList.add(translationAPI.getComponentSync(GuiMessages.BULK_LISTING_TOTAL_ITEMS) {
                unparsed("total", quantity.toString())
            })
            lore = loreList
            hideAllFlags()
        }
    }

    private fun createPerItemSettings(): VItem {
        return VItem(XMaterial.BOOK) {
            name = mm.deserialize("<yellow>Per-Item Settings")
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Each auction will contain:"))
            loreList.add(mm.deserialize("<white>• 1 item"))
            loreList.add(Component.empty())
            loreList.add(mm.deserialize("<gray>Same settings for all auctions"))
            lore = loreList
            hideAllFlags()
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
                }
                ClickResult.SwitchMenu(createMenu())
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
                    ClickResult.SwitchMenu(createMenu())
                } else {
                    runBlocking {
                        val minBinPrice = if (auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH) {
                            startPrice.coerceAtLeast(config.auctions.minStartPrice)
                        } else {
                            config.auctions.minStartPrice
                        }

                        val result = menuAPI.promptDouble(
                            player,
                            "Enter BIN Price",
                            binPrice,
                            minBinPrice,
                            config.auctions.maxStartPrice
                        )
                        when (result) {
                            is AnvilInputResult.Success -> {
                                if ((auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH) && result.value <= startPrice) {
                                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.BIN_PRICE_MUST_BE_GREATER))
                                } else {
                                    binPrice = result.value
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    ClickResult.SwitchMenu(createMenu())
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
                }
                ClickResult.SwitchMenu(createMenu())
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
                ClickResult.SwitchMenu(createMenu())
            }
        }
    }

    private fun calculateFeePerItem(): Double {
        val listingFee = config.auctions.listingFee
        val fee = when (listingFee.type.uppercase()) {
            "PERCENTAGE" -> startPrice * (listingFee.amount / 100.0)
            else -> listingFee.amount
        }.coerceIn(listingFee.minFee, listingFee.maxFee)

        val totalFee = if (anonymous && config.auctions.display.allowAnonymous) {
            fee + config.auctions.display.anonymousFee
        } else fee

        // Apply bulk discount
        val discount = config.auctions.bulkListing.feeDiscountPercent / 100.0
        return totalFee * (1.0 - discount)
    }
}
