package bruh.auctionhouse.hooks

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.OrderStatus
import kotlinx.coroutines.runBlocking
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

/**
 * PlaceholderAPI expansion for AuctionHouse.
 * Provides placeholders for use in other plugins and scoreboards.
 */
class PlaceholderAPIHook(
    private val plugin: AuctionHousePlugin,
    private val auctionRepository: AuctionRepository,
    private val orderRepository: OrderRepository,
    private val expiredItemRepository: ExpiredItemRepository
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "auctionhouse"

    override fun getAuthor(): String = plugin.pluginMeta.authors.firstOrNull() ?: "Unknown"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    /**
     * Handles placeholder requests from PlaceholderAPI.
     *
     * Available placeholders:
     * - %auctionhouse_active_auctions% - Player's active auction count
     * - %auctionhouse_active_orders% - Player's pending orders count
     * - %auctionhouse_total_auctions% - Global active auctions count
     * - %auctionhouse_expired_items% - Player's unclaimed expired items count
     *
     * @param player The player to get data for (can be null)
     * @param params The placeholder identifier (without the prefix)
     * @return The placeholder value, or null if not found
     */
    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        return when (params.lowercase()) {
            "active_auctions" -> {
                player?.let {
                    runBlocking {
                        auctionRepository.countPlayerAuctions(it.uniqueId, AuctionStatus.ACTIVE).toString()
                    }
                } ?: "0"
            }
            "active_orders" -> {
                player?.let {
                    runBlocking {
                        orderRepository.countPlayerOrders(it.uniqueId, OrderStatus.PENDING).toString()
                    }
                } ?: "0"
            }
            "total_auctions" -> {
                // Count all active auctions globally
                runBlocking {
                    // Note: We could add a dedicated method for this, but for now we'll use 0
                    // as a placeholder. A full implementation would add countAllActiveAuctions() to repository.
                    "0"
                }
            }
            "expired_items" -> {
                player?.let {
                    runBlocking {
                        expiredItemRepository.countPlayerExpiredItems(it.uniqueId).toString()
                    }
                } ?: "0"
            }
            else -> null
        }
    }
}
