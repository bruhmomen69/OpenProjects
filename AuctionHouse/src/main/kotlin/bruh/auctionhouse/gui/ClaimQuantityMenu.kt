package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.OrderService
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
 * Menu for selecting quantity of items to claim from a consolidated expired item group.
 */
class ClaimQuantityMenu(
    private val menuAPI: MenuAPI,
    private val consolidatedService: ConsolidatedExpiredItemService,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val watchlistRepository: WatchlistRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player,
    private val consolidatedItem: ConsolidatedExpiredItem,
    private val initialQuantity: Int
) {
    private val mm = MiniMessage.miniMessage()
    private var quantity: Int = initialQuantity.coerceIn(1, consolidatedItem.remainingQuantity())

    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = mm.deserialize("<gold>Claim Items</gold>")

            // Item display showing available/total
            item(13, createItemDisplay())

            // Quantity controls (bottom row)
            item(29, createDecreaseButton(1))      // -1
            item(30, createDecreaseButton(10))     // -10
            item(31, createQuantityDisplay())       // Shows current quantity
            item(32, createIncreaseButton(10))     // +10
            item(33, createIncreaseButton(1))      // +1

            // Quick select buttons
            item(38, createQuickSelectButton(64))   // Full stack
            item(39, createQuickSelectButton(576))  // 9 stacks (inventory)
            item(40, createMaxButton())              // Max available

            // Confirm/Cancel
            item(42, createConfirmButton())
            item(43, createCancelButton())
        }

        menuAPI.open(menu, player)
    }

    private fun createItemDisplay(): VItem {
        val material = XMaterial.matchXMaterial(consolidatedItem.itemMaterial.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf(
            mm.deserialize("<gray>Available: <green>${consolidatedItem.remainingQuantity()}"),
            mm.deserialize("<gray>Total: <white>${consolidatedItem.totalQuantity}"),
            mm.deserialize("<gray>Type: <white>${consolidatedItem.itemType}"),
            Component.empty(),
            mm.deserialize("<yellow>Use the buttons below to select how many items to claim")
        )

        return VItem(material) {
            name = consolidatedItem.itemDisplayName?.let { mm.deserialize(it) }
                ?: Component.text(consolidatedItem.itemMaterial.name.replace("_", " "))
            lore = loreList
            hideAllFlags()
        }
    }

    private fun createDecreaseButton(amount: Int): VItem {
        val material = when {
            amount == 1 -> XMaterial.LIME_DYE
            else -> XMaterial.MELON_SLICE
        }

        return VItem(material) {
            name = mm.deserialize("<red>-$amount")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to decrease by $amount")
            )

            onClick { _, _ ->
                quantity = (quantity - amount).coerceAtLeast(1)
                open() // Refresh menu
                ClickResult.CLOSE
            }
        }
    }

    private fun createIncreaseButton(amount: Int): VItem {
        val material = when {
            amount == 1 -> XMaterial.LIME_DYE
            else -> XMaterial.MELON_SLICE
        }

        return VItem(material) {
            name = mm.deserialize("<green>+$amount")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to increase by $amount")
            )

            onClick { _, _ ->
                quantity = (quantity + amount).coerceAtMost(consolidatedItem.remainingQuantity())
                open() // Refresh menu
                ClickResult.CLOSE
            }
        }
    }

    private fun createQuantityDisplay(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = mm.deserialize("<yellow>Quantity: <white>$quantity")
            lore = mutableListOf(
                mm.deserialize("<gray>Out of <white>${consolidatedItem.remainingQuantity()} <gray>available")
            )
        }
    }

    private fun createQuickSelectButton(amount: Int): VItem {
        val displayAmount = if (amount >= consolidatedItem.remainingQuantity()) {
            "All (${consolidatedItem.remainingQuantity()})"
        } else {
            amount.toString()
        }

        return VItem(XMaterial.GOLD_BLOCK) {
            name = mm.deserialize("<yellow>$displayAmount")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to set quantity to $displayAmount")
            )

            onClick { _, _ ->
                quantity = amount.coerceAtMost(consolidatedItem.remainingQuantity()).coerceAtLeast(1)
                open() // Refresh menu
                ClickResult.CLOSE
            }
        }
    }

    private fun createMaxButton(): VItem {
        return VItem(XMaterial.DIAMOND_BLOCK) {
            name = mm.deserialize("<green>MAX (${consolidatedItem.remainingQuantity()})")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to claim all available items")
            )

            onClick { _, _ ->
                quantity = consolidatedItem.remainingQuantity()
                open() // Refresh menu
                ClickResult.CLOSE
            }
        }
    }

    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = mm.deserialize("<green>Confirm Claim")
            lore = mutableListOf(
                mm.deserialize("<gray>Claim: <green>$quantity <gray>items"),
                mm.deserialize("<gray>Remaining: <yellow>${consolidatedItem.remainingQuantity() - quantity}"),
                Component.empty(),
                mm.deserialize("<green>Click to confirm")
            )

            onClick { _, _ ->
                runBlocking {
                    val result = consolidatedService.claimItems(player, consolidatedItem, quantity)
                    player.sendMessage(mm.deserialize(
                        if (result.success) "<green>${result.message}"
                        else "<red>${result.message}"
                    ))

                    // Return to consolidated expired items menu
                    if (result.success) {
                        ConsolidatedExpiredItemsMenu(
                            menuAPI, consolidatedService, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config,
                            translationAPI, plugin, economy, player
                        ).open()
                    }
                }
                ClickResult.CLOSE
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.REDSTONE_BLOCK) {
            name = mm.deserialize("<red>Cancel")
            lore = mutableListOf(
                mm.deserialize("<gray>Return to expired items menu")
            )

            onClick { _, _ ->
                // Return to consolidated expired items menu
                ConsolidatedExpiredItemsMenu(
                    menuAPI, consolidatedService, auctionService, orderService, auctionRepository, bidRepository, watchlistRepository, config,
                    translationAPI, plugin, economy, player
                ).open()
                ClickResult.CLOSE
            }
        }
    }
}
