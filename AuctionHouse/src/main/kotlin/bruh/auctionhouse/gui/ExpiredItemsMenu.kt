package bruh.auctionhouse.gui

import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Menu for retrieving expired/cancelled items.
 */
class ExpiredItemsMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<ExpiredItem>() {

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

        itemRenderer = { expiredItem, _ ->
            createExpiredItemDisplay(expiredItem)
        }

        asyncData<List<ExpiredItem>> {
            load { pctx.expiredItemRepository.getPlayerExpiredItems(pctx.player.uniqueId) }
            onLoaded { items -> dataSource = items }
        }
    }

    override fun populateItems() {
        items.clear()

        // Back button
        items[49] = MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.SwitchMenu(AuctionHouseMenu(pctx)) }
        }
    }

    private fun createExpiredItemDisplay(expiredItem: ExpiredItem): VItem {
        val material = XMaterial.matchXMaterial(expiredItem.itemStack.type.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(pctx.mm.deserialize("<gray>Source: <white>${expiredItem.reason}"))
        loreList.add(pctx.mm.deserialize("<gray>Available: <white>${formatExpiredTime(expiredItem.expiredAt)}"))
        if (expiredItem.itemStack.amount > 1) {
            loreList.add(pctx.mm.deserialize("<gray>Quantity: <white>${expiredItem.itemStack.amount}"))
        }
        loreList.add(Component.empty())
        loreList.add(pctx.mm.deserialize("<green>Click to claim item"))

        return VItem(material) {
            name = expiredItem.itemStack.itemMeta?.displayName()
                ?: Component.text(expiredItem.itemStack.type.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, controls ->
                val itemStack = expiredItem.itemStack
                val totalAmount = itemStack.amount
                val availableSpace = calculateAvailableSpace(itemStack)

                if (availableSpace <= 0) {
                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.INVENTORY_FULL))
                    return@onClick ClickResult.Deny
                }

                val giveAmount = minOf(availableSpace, totalAmount)
                val toGive = itemStack.clone().also { it.amount = giveAmount }
                val leftover = pctx.player.inventory.addItem(toGive)
                val actualGiven = giveAmount - leftover.values.sumOf { it.amount }
                val remainderAmount = totalAmount - actualGiven

                controls.runAsync(
                    action = {
                        pctx.expiredItemRepository.markAsClaimed(expiredItem.id)
                        if (remainderAmount > 0) {
                            storeOverflowAsNewExpiredItem(expiredItem, remainderAmount)
                        }
                    },
                    onSuccess = {
                        if (remainderAmount > 0) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.PARTIAL_RETRIEVAL_COUNT) {
                                unparsed("available", actualGiven.toString())
                                unparsed("total", totalAmount.toString())
                            })
                        } else {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ITEM_RETRIEVED))
                        }
                        controls.reloadData()
                    }
                )
                ClickResult.Deny
            }
        }
    }

    private fun calculateAvailableSpace(itemStack: ItemStack): Int {
        val maxStackSize = itemStack.maxStackSize
        var available = 0

        for (item in pctx.player.inventory.contents) {
            if (item == null || item.type.isAir) {
                available += maxStackSize
            } else if (item.type == itemStack.type && item.isSimilar(itemStack)) {
                val spaceInStack = maxStackSize - item.amount
                if (spaceInStack > 0) {
                    available += spaceInStack
                }
            }
        }

        return available
    }

    private suspend fun storeOverflowAsNewExpiredItem(original: ExpiredItem, remainingAmount: Int) {
        val overflowItem = original.itemStack.clone()
        overflowItem.amount = remainingAmount

        pctx.expiredItemRepository.create(
            ExpiredItem(
                id = UUID.randomUUID(),
                ownerUuid = original.ownerUuid,
                ownerName = original.ownerName,
                itemType = original.itemType,
                sourceId = original.sourceId,
                itemStack = overflowItem,
                reason = "${original.reason} (PARTIAL)",
                expiredAt = Instant.now()
            )
        )
    }

    private fun formatExpiredTime(instant: Instant): String {
        val duration = Duration.between(instant, Instant.now())
        return when {
            duration.toDays() > 0 -> "${duration.toDays()} days ago"
            duration.toHours() > 0 -> "${duration.toHours()} hours ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} minutes ago"
            else -> "Just now"
        }
    }
}
