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
import org.bukkit.entity.Player

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
        loreList.add(Component.empty())
        loreList.add(mm.deserialize("<green>Click to retrieve item"))

        return VItem(material) {
            name = expiredItem.itemStack.itemMeta?.displayName()
                ?: Component.text(expiredItem.itemStack.type.name.replace("_", " "))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    // Give item to player
                    giveItemToPlayer(expiredItem)
                    // Mark as claimed
                    expiredItemRepository.markAsClaimed(expiredItem.id)
                }
                // Refresh menu
                open()
                ClickResult.CLOSE
            }
        }
    }

    private fun giveItemToPlayer(expiredItem: ExpiredItem) {
        val remaining = player.inventory.addItem(expiredItem.itemStack)
        if (remaining.isNotEmpty()) {
            // Inventory full, drop at player location
            remaining.values.forEach { item ->
                player.world.dropItemNaturally(player.location, item)
            }
            player.sendMessage(mm.deserialize("<yellow>Your inventory was full. Some items were dropped at your location."))
        } else {
            player.sendMessage(mm.deserialize("<green>Item retrieved successfully!"))
        }
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
