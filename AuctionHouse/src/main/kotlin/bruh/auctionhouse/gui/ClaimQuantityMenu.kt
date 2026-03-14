package bruh.auctionhouse.gui

import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Menu for selecting quantity of items to claim from a consolidated expired item group.
 */
class ClaimQuantityMenu(
    private val pctx: PlayerMenuContext,
    private val consolidatedItem: ConsolidatedExpiredItem,
    private val initialQuantity: Int
) : SimpleMenu() {

    private var quantity by menuState(initialQuantity.coerceIn(1, consolidatedItem.remainingQuantity()))

    init {
        rows = 5
        title = pctx.mm.deserialize("<gold>Claim Items</gold>")
    }

    override fun populateItems() {
        items.clear()

        // Item display showing available/total
        item(13, createItemDisplay())

        // Quantity controls (bottom row)
        item(29, createDecreaseButton(1))
        item(30, createDecreaseButton(10))
        item(31, createQuantityDisplay())
        item(32, createIncreaseButton(10))
        item(33, createIncreaseButton(1))

        // Quick select buttons
        item(38, createQuickSelectButton(64))
        item(39, createQuickSelectButton(576))
        item(40, createMaxButton())

        // Confirm/Cancel
        item(42, createConfirmButton())
        item(43, createCancelButton())
    }

    private fun createItemDisplay(): VItem {
        val material = XMaterial.matchXMaterial(consolidatedItem.itemMaterial.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf(
            pctx.mm.deserialize("<gray>Available: <green>${consolidatedItem.remainingQuantity()}"),
            pctx.mm.deserialize("<gray>Total: <white>${consolidatedItem.totalQuantity}"),
            pctx.mm.deserialize("<gray>Type: <white>${consolidatedItem.itemType}"),
            Component.empty(),
            pctx.mm.deserialize("<yellow>Use the buttons below to select how many items to claim")
        )

        return VItem(material) {
            name = consolidatedItem.itemDisplayName?.let { pctx.mm.deserialize(it) }
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
            name = pctx.mm.deserialize("<red>-$amount")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to decrease by $amount")
            )

            onClick { _, _ ->
                quantity = (quantity - amount).coerceAtLeast(1)
                ClickResult.Deny
            }
        }
    }

    private fun createIncreaseButton(amount: Int): VItem {
        val material = when {
            amount == 1 -> XMaterial.LIME_DYE
            else -> XMaterial.MELON_SLICE
        }

        return VItem(material) {
            name = pctx.mm.deserialize("<green>+$amount")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to increase by $amount")
            )

            onClick { _, _ ->
                quantity = (quantity + amount).coerceAtMost(consolidatedItem.remainingQuantity())
                ClickResult.Deny
            }
        }
    }

    private fun createQuantityDisplay(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = pctx.mm.deserialize("<yellow>Quantity: <white>$quantity")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Out of <white>${consolidatedItem.remainingQuantity()} <gray>available")
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
            name = pctx.mm.deserialize("<yellow>$displayAmount")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to set quantity to $displayAmount")
            )

            onClick { _, _ ->
                quantity = amount.coerceAtMost(consolidatedItem.remainingQuantity()).coerceAtLeast(1)
                ClickResult.Deny
            }
        }
    }

    private fun createMaxButton(): VItem {
        return VItem(XMaterial.DIAMOND_BLOCK) {
            name = pctx.mm.deserialize("<green>MAX (${consolidatedItem.remainingQuantity()})")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to claim all available items")
            )

            onClick { _, _ ->
                quantity = consolidatedItem.remainingQuantity()
                ClickResult.Deny
            }
        }
    }

    private fun createConfirmButton(): VItem {
        return VItem(XMaterial.EMERALD_BLOCK) {
            name = pctx.mm.deserialize("<green>Confirm Claim")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Claim: <green>$quantity <gray>items"),
                pctx.mm.deserialize("<gray>Remaining: <yellow>${consolidatedItem.remainingQuantity() - quantity}"),
                Component.empty(),
                pctx.mm.deserialize("<green>Click to confirm")
            )

            onClick { _, controls ->
                controls.runAsync(
                    action = { pctx.consolidatedExpiredItemService.claimItems(pctx.player, consolidatedItem, quantity) },
                    onSuccess = { result ->
                        pctx.player.sendMessage(pctx.mm.deserialize(
                            if (result.success) "<green>${result.message}"
                            else "<red>${result.message}"
                        ))
                        if (result.success) {
                            pctx.menuAPI.open(ConsolidatedExpiredItemsMenu(pctx), pctx.player)
                        } else {
                            controls.close()
                        }
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createCancelButton(): VItem {
        return VItem(XMaterial.REDSTONE_BLOCK) {
            name = pctx.mm.deserialize("<red>Cancel")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Return to expired items menu")
            )

            onClick { _, _ ->
                ClickResult.SwitchMenu(ConsolidatedExpiredItemsMenu(pctx))
            }
        }
    }
}
