package bruh.auctionhouse.gui

import bruh.auctionhouse.model.ConsolidatedExpiredItem
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Menu for retrieving expired/cancelled items from consolidated groups.
 * This replaces the original ExpiredItemsMenu with a consolidated view.
 */
class ConsolidatedExpiredItemsMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<ConsolidatedExpiredItem>() {

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.EXPIRED_ITEMS_TITLE)
        background = MenuUtils.backgroundItem()
        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        loadingPlaceholder = MenuUtils.loadingAuctionItem()
        emptyPlaceholder = VItem(XMaterial.BARRIER) {
            name = pctx.mm.deserialize("<red>No Claimable Items")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>You have no expired or"),
                pctx.mm.deserialize("<gray>cancelled items to claim.")
            )
        }

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }

        itemRenderer = { consolidatedItem, _ ->
            createConsolidatedItemDisplay(consolidatedItem)
        }

        asyncData<List<ConsolidatedExpiredItem>> {
            load { pctx.consolidatedExpiredItemService.getPlayerConsolidatedItems(pctx.player.uniqueId) }
            onLoaded { items -> dataSource = items }
        }
    }

    override fun populateItems() {
        items.clear()

        // Back button
        items[49] = MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.SwitchMenu(AuctionHouseMenu(pctx)) }
        }

        // Claim All button
        items[45] = createClaimAllButton()
    }

    private fun createClaimAllButton(): VItem {
        val totalItems = dataSource.sumOf { it.remainingQuantity() }

        return VItem(XMaterial.CHEST) {
            name = pctx.mm.deserialize("<green>Claim All Items")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Total items: <white>$totalItems"),
                Component.empty(),
                pctx.mm.deserialize("<yellow>Click to claim all items"),
                pctx.mm.deserialize("<gray>Items that don't fit will remain.")
            )
            hideAllFlags()

            onClick { _, controls ->
                val itemsToClaim = dataSource.toList()
                if (itemsToClaim.isEmpty() || itemsToClaim.all { it.remainingQuantity() <= 0 }) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.NO_CLAIMABLE_ITEMS))
                    return@onClick ClickResult.Deny
                }

                controls.runAsync(
                    action = {
                        var totalClaimed = 0
                        var totalRemaining = 0
                        for (item in itemsToClaim) {
                            if (item.remainingQuantity() <= 0) continue
                            val result = pctx.consolidatedExpiredItemService.claimItems(pctx.player, item, item.remainingQuantity())
                            if (result.success) {
                                totalClaimed += result.claimedQuantity
                                if (result.claimedQuantity < item.remainingQuantity()) {
                                    totalRemaining += (item.remainingQuantity() - result.claimedQuantity)
                                }
                            } else {
                                totalRemaining += item.remainingQuantity()
                            }
                        }
                        totalClaimed to totalRemaining
                    },
                    onSuccess = { (totalClaimed, totalRemaining) ->
                        if (totalClaimed > 0 && totalRemaining > 0) {
                            pctx.player.sendMessage(pctx.mm.deserialize("<green>Claimed <white>$totalClaimed <green>items. <yellow>$totalRemaining <yellow>items remaining (inventory full)."))
                        } else if (totalClaimed > 0) {
                            pctx.player.sendMessage(pctx.mm.deserialize("<green>Claimed <white>$totalClaimed <green>items."))
                        } else if (totalRemaining > 0) {
                            pctx.player.sendMessage(pctx.mm.deserialize("<red>Inventory full! <yellow>$totalRemaining <yellow>items remaining."))
                        }
                        controls.reloadData()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun createConsolidatedItemDisplay(item: ConsolidatedExpiredItem): VItem {
        val material = XMaterial.matchXMaterial(item.itemMaterial.name).orElse(XMaterial.STONE)

        return VItem(material) {
            name = item.itemDisplayName?.let { pctx.mm.deserialize(it) }
                ?: Component.text(item.itemMaterial.name.replace("_", " "))
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Available: <green>${item.remainingQuantity()}"),
                pctx.mm.deserialize("<gray>Total: <white>${item.totalQuantity}"),
                pctx.mm.deserialize("<gray>Source: <white>${item.itemType}"),
                pctx.mm.deserialize("<gray>Reason: <white>${item.reason}"),
                Component.empty(),
                pctx.mm.deserialize("<yellow>Left-click to claim items"),
                pctx.mm.deserialize("<gray>(Shift-click to claim max amount)")
            )
            hideAllFlags()

            onClick { clickType, _ ->
                val quantity = if (clickType.isShiftClick) item.remainingQuantity()
                else minOf(64, item.remainingQuantity())
                ClickResult.SwitchMenu(ClaimQuantityMenu(pctx, item, quantity))
            }
        }
    }
}
