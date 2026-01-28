package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Menu for retrieving expired/cancelled items.
 */
class ExpiredItemsMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val expiredItemRepository: ExpiredItemRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val expiredItems = runBlocking {
            expiredItemRepository.getPlayerExpiredItems(player.uniqueId)
        }

        if (expiredItems.isEmpty()) {
            player.sendMessage(mm.deserialize("<gray>You have no expired items to claim."))
            return
        }

        val menu = menuAPI.paginated<ExpiredItem> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.EXPIRED_ITEMS_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = expiredItems

            itemRenderer = { expiredItem, _ ->
                createExpiredItemDisplay(expiredItem)
            }

            background = MenuUtils.backgroundItem()

            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }

            // Back button
            val backItem = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AuctionHouseMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            }
            staticItems[49] = backItem
        }

        menuAPI.open(menu, player)
    }

    private fun createExpiredItemDisplay(expiredItem: ExpiredItem): VItem {
        val material = XMaterial.matchXMaterial(expiredItem.itemStack.type.name).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()
        loreList.add(mm.deserialize("<gray>Reason: <white>${expiredItem.reason}"))
        loreList.add(mm.deserialize("<gray>Expired: <white>${formatExpiredTime(expiredItem.expiredAt)}"))
        if (expiredItem.itemStack.amount > 1) {
            loreList.add(mm.deserialize("<gray>Quantity: <white>${expiredItem.itemStack.amount}"))
        }
        loreList.add(Component.empty())
        loreList.add(mm.deserialize("<green>Click to retrieve item"))

        return VItem(material) {
            name = expiredItem.itemStack.itemMeta?.displayName()
                ?: Component.text(expiredItem.itemStack.type.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    // Give item to player with partial retrieval support
                    giveItemToPlayer(expiredItem)
                }
                // Refresh menu
                open()
                ClickResult.CLOSE
            }
        }
    }

    private suspend fun giveItemToPlayer(expiredItem: ExpiredItem) {
        val itemStack = expiredItem.itemStack
        val maxStackSize = itemStack.maxStackSize
        val totalAmount = itemStack.amount

        // Calculate how much inventory space is available for this item type
        val availableSpace = calculateAvailableSpace(itemStack)

        if (availableSpace <= 0) {
            // No space at all - keep in expired items
            player.sendMessage(mm.deserialize("<red>Your inventory is full! Clear some space and try again."))
            return
        }

        if (availableSpace >= totalAmount) {
            // Full retrieval - give all items and mark as claimed
            val remaining = player.inventory.addItem(itemStack.clone())
            if (remaining.isEmpty()) {
                expiredItemRepository.markAsClaimed(expiredItem.id)
                player.sendMessage(mm.deserialize("<green>Item retrieved successfully!"))
            } else {
                // This shouldn't happen if we calculated correctly, but handle it anyway
                val remainingAmount = remaining.values.sumOf { it.amount }
                storeOverflowAsNewExpiredItem(expiredItem, remainingAmount)
                expiredItemRepository.markAsClaimed(expiredItem.id)
                player.sendMessage(mm.deserialize("<yellow>Partial retrieval! Some items couldn't fit and remain in expired items."))
            }
        } else {
            // Partial retrieval - give what fits, store remainder as new expired item
            val toGive = itemStack.clone()
            toGive.amount = availableSpace

            val remainder = itemStack.clone()
            remainder.amount = totalAmount - availableSpace

            player.inventory.addItem(toGive)

            // Store overflow as a new expired item entry
            storeOverflowAsNewExpiredItem(expiredItem, remainder.amount)

            // Mark original as claimed
            expiredItemRepository.markAsClaimed(expiredItem.id)

            player.sendMessage(mm.deserialize("<yellow>Partial retrieval! Retrieved $availableSpace/${totalAmount} items. The rest remain in expired items."))
        }
    }

    private fun calculateAvailableSpace(itemStack: ItemStack): Int {
        val maxStackSize = itemStack.maxStackSize
        val type = itemStack.type
        val meta = itemStack.itemMeta
        var available = 0

        // Check existing slots with same item type that aren't full
        for (item in player.inventory.contents) {
            if (item == null || item.type.isAir) {
                // Empty slot - can hold a full stack
                available += maxStackSize
            } else if (item.type == type && item.isSimilar(itemStack)) {
                // Same item type with same metadata - can stack
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

        expiredItemRepository.create(
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

    private fun formatExpiredTime(instant: java.time.Instant): String {
        val duration = java.time.Duration.between(instant, java.time.Instant.now())
        return when {
            duration.toDays() > 0 -> "${duration.toDays()} days ago"
            duration.toHours() > 0 -> "${duration.toHours()} hours ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} minutes ago"
            else -> "Just now"
        }
    }
}
