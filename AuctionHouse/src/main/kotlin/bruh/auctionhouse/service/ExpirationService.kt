package bruh.auctionhouse.service

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import kotlinx.coroutines.runBlocking
import org.bukkit.scheduler.BukkitTask
import org.slf4j.Logger

/**
 * Service that handles automatic expiration of auctions and orders.
 * Runs periodically to check for and process expired listings.
 */
class ExpirationService(
    private val plugin: AuctionHousePlugin,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val config: AuctionHouseConfig,
    private val logger: Logger
) {
    private var task: BukkitTask? = null

    /**
     * Starts the expiration checking task.
     * Runs asynchronously every minute (1200 ticks).
     */
    fun start() {
        task = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable {
                runBlocking { checkExpirations() }
            },
            1200L, // Initial delay: 1 minute
            1200L  // Period: 1 minute
        )
        logger.info("Expiration service started - checking every minute")
    }

    /**
     * Stops the expiration checking task.
     */
    fun stop() {
        task?.cancel()
        task = null
        logger.info("Expiration service stopped")
    }

    /**
     * Checks for and processes expired auctions and orders.
     * Called periodically by the scheduled task.
     */
    suspend fun checkExpirations() {
        try {
            // Process expired auctions
            auctionService.processExpiredAuctions()

            // Process expired orders
            orderService.processExpiredOrders()
        } catch (e: Exception) {
            logger.error("Error processing expirations", e)
        }
    }
}
