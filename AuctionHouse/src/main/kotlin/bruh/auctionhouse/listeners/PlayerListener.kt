package bruh.auctionhouse.listeners

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.auctionhouse.util.PlayerStateManager
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.math.BigDecimal
import java.util.UUID

/**
 * Listener for player events, handling login notifications for auction/order events.
 */
class PlayerListener(
    private val plugin: AuctionHousePlugin,
    config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val orderRepository: OrderRepository
) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val config: AuctionHouseConfig
        get() = plugin.config

    /**
     * Handles player login and delivers queued notifications.
     * Uses coroutine launch instead of runBlocking to avoid thread blocking.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerId = player.uniqueId

        if (!config.notifications.alertOnLogin) return

        // Use coroutine scope instead of runBlocking to avoid blocking threads
        plugin.launch {
            try {
                val notifications = withContext(Dispatchers.IO) {
                    buildLoginNotifications(playerId)
                }

                withContext(plugin.entityDispatcher(player)) {
                    notifications.outbidCount?.let { outbidCount ->
                        player.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.BID_OUTBID_LOGIN) {
                                unparsed("count", outbidCount.toString())
                            }
                        )
                    }

                    if (notifications.recentSold.isNotEmpty()) {
                        player.sendMessage(
                            translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD_LOGIN) {
                                unparsed("count", notifications.recentSold.size.toString())
                            }
                        )

                        notifications.recentSold.forEach { auction ->
                            player.sendMessage(
                                mm.deserialize(
                                    "<green>Sold: <white>${auction.itemDisplayName ?: auction.itemMaterial} " +
                                        "<gold>for ${formatPrice(auction.finalPrice ?: 0.0)}"
                                )
                            )
                        }
                    }

                    if (notifications.filledOrders.isNotEmpty()) {
                        player.sendMessage(
                            translationAPI.getComponentSync(OrderMessages.ORDER_FILLED_LOGIN) {
                                unparsed("count", notifications.filledOrders.size.toString())
                            }
                        )

                        notifications.filledOrders.take(5).forEach { order ->
                            player.sendMessage(
                                mm.deserialize(
                                    "<green>Order Filled: <white>${order.itemMaterial.name} " +
                                        "<yellow>x${order.quantityFilled} <gold>for ${formatPrice(order.totalPrice)}"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.slF4JLogger.error("Error processing login notifications for ${player.name}", e)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        PlayerStateManager.clearState(event.player.uniqueId)
    }

    private suspend fun buildLoginNotifications(playerId: UUID): LoginNotifications {
        val outbidCount = if (config.notifications.alertOutbid) {
            bidRepository.getOutbidBidsForPlayer(playerId).takeIf { it > 0 }
        } else {
            null
        }

        val recentSold = if (config.notifications.alertSold) {
            auctionRepository.getRecentSoldAuctions(playerId, 5)
        } else {
            emptyList()
        }

        val filledOrders = if (config.notifications.alertOrderFilled) {
            orderRepository.getPlayerFilledOrders(playerId)
        } else {
            emptyList()
        }

        return LoginNotifications(outbidCount, recentSold, filledOrders)
    }

    private fun formatPrice(amount: Double): String {
        return plugin.economy.format(BigDecimal.valueOf(amount))
    }

    private data class LoginNotifications(
        val outbidCount: Int?,
        val recentSold: List<bruh.auctionhouse.model.Auction>,
        val filledOrders: List<bruh.auctionhouse.model.Order>
    )
}
