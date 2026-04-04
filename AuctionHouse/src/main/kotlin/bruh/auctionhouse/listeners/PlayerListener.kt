package bruh.auctionhouse.listeners

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderFillRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.math.BigDecimal
import java.lang.Runnable

/**
 * Listener for player events, handling login notifications for auction/order events.
 */
class PlayerListener(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val auctionService: AuctionService,
    private val auctionRepository: AuctionRepository,
    private val bidRepository: BidRepository,
    private val orderRepository: OrderRepository,
    private val orderFillRepository: OrderFillRepository
) : Listener {

    private val mm = MiniMessage.miniMessage()

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
                withContext(Dispatchers.IO) {
                    // Check for outbid notifications
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
        }
    }

    /**
     * Checks if the player was outbid on any auctions while offline.
     */
    private suspend fun checkOutbidNotifications(playerId: java.util.UUID, player: org.bukkit.entity.Player) {
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
     * Checks if the player has any recently sold auctions while offline.
     * Uses limited query to avoid loading all sold auctions.
     */
    private suspend fun checkSoldNotifications(playerId: java.util.UUID, player: org.bukkit.entity.Player) {
        // Use optimized query with LIMIT instead of loading all and taking 5
        val recentSold = auctionRepository.getRecentSoldAuctions(playerId, 5)

        if (recentSold.isNotEmpty()) {
            player.sendMessage(
                translationAPI.getComponentSync(AuctionMessages.AUCTION_SOLD_LOGIN) {
                    unparsed("count", recentSold.size.toString())
                }
            )

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
        val filledOrders = orderRepository.getPlayerFilledOrders(playerId)

        if (filledOrders.isNotEmpty()) {
            player.sendMessage(
                translationAPI.getComponentSync(OrderMessages.ORDER_FILLED_LOGIN) {
                    unparsed("count", filledOrders.size.toString())
                }
            )

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

    private fun formatPrice(amount: Double): String {
        return plugin.economy.format(BigDecimal.valueOf(amount))
    }
}
