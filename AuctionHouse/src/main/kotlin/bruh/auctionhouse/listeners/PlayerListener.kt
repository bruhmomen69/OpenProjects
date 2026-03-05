package bruh.auctionhouse.listeners

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderFillRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.translations.TranslationAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.math.BigDecimal
import java.time.Duration
import java.lang.Runnable

/**
 * Listener for player events, handling login notifications for auction/order events.
 */
class PlayerListener(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val auctionService: AuctionService,
    private val bidRepository: BidRepository,
    private val orderRepository: OrderRepository,
    private val orderFillRepository: OrderFillRepository
) : Listener {

    private val mm = MiniMessage.miniMessage()

    /**
     * Handles player login and delivers queued notifications.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerId = player.uniqueId

        if (!config.notifications.alertOnLogin) return

        // Run asynchronously to avoid blocking the join
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                kotlinx.coroutines.runBlocking {
                    // Check for outbid notifications (bids that were outbid while player was offline)
                    if (config.notifications.alertOutbid) {
                        checkOutbidNotifications(playerId, player)
                    }

                    // Check for sold auction notifications
                    if (config.notifications.alertSold) {
                        checkSoldNotifications(playerId, player)
                    }

                    // Check for order filled notifications
                    if (config.notifications.alertOrderFilled) {
                        checkOrderFilledNotifications(playerId, player)
                    }
                }
            } catch (e: Exception) {
                plugin.slF4JLogger.error("Error processing login notifications for ${player.name}", e)
            }
        })
    }

    /**
     * Checks if the player was outbid on any auctions while offline.
     */
    private suspend fun checkOutbidNotifications(playerId: java.util.UUID, player: org.bukkit.entity.Player) {
        // Get all bids by this player that are now outbid
        // This is a simplified check - in production you'd want a dedicated notification table
        val outbidCount = bidRepository.getOutbidBidsForPlayer(playerId)

        if (outbidCount > 0) {
            player.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.BID_OUTBID_LOGIN) {
                    unparsed("count", outbidCount.toString())
                }
            )
        }
    }

    /**
     * Checks if the player has any sold auctions while offline.
     */
    private suspend fun checkSoldNotifications(playerId: java.util.UUID, player: org.bukkit.entity.Player) {
        // Get player's sold auctions
        val soldAuctions = auctionService.getPlayerAuctions(playerId, bruh.auctionhouse.model.AuctionStatus.SOLD)

        // Filter to only show recently sold (could be improved with timestamp tracking)
        val recentSold = soldAuctions.take(5) // Limit to 5 to avoid spam

        if (recentSold.isNotEmpty()) {
            player.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD_LOGIN) {
                    unparsed("count", recentSold.size.toString())
                }
            )

            // Show details for each
            recentSold.forEach { auction ->
                player.sendMessage(
                    mm.deserialize(
                        "<green>Sold: <white>${auction.itemDisplayName ?: auction.itemMaterial} " +
                        "<gold>for ${formatPrice(auction.finalPrice ?: 0.0)}"
                    )
                )
            }
        }
    }

    /**
     * Checks if any of the player's orders were filled while offline.
     */
    private suspend fun checkOrderFilledNotifications(playerId: java.util.UUID, player: org.bukkit.entity.Player) {
        // Get player's orders that were filled
        val filledOrders = orderRepository.getPlayerFilledOrders(playerId)

        if (filledOrders.isNotEmpty()) {
            player.sendMessage(
                translationAPI.getComponentSync(OrderMessages.ORDER_FILLED_LOGIN) {
                    unparsed("count", filledOrders.size.toString())
                }
            )

            // Show details for each
            filledOrders.take(5).forEach { order ->
                player.sendMessage(
                    mm.deserialize(
                        "<green>Order Filled: <white>${order.itemMaterial.name} " +
                        "<yellow>x${order.quantityFilled} <gold>for ${formatPrice(order.totalPrice)}"
                    )
                )
            }
        }
    }

    /**
     * Formats a price using the economy provider.
     */
    private fun formatPrice(amount: Double): String {
        return plugin.economy.format(BigDecimal.valueOf(amount))
    }
}
