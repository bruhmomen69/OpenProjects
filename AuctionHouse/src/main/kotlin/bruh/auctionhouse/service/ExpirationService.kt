package bruh.auctionhouse.service

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import kotlinx.coroutines.runBlocking
import org.bukkit.scheduler.BukkitTask
import org.slf4j.Logger
import java.time.Duration
import kotlin.math.minOf

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
    private var cleanupTask: BukkitTask? = null

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

        // Start cleanup task for old expired items (runs daily)
        cleanupTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable {
                runBlocking { cleanupOldExpiredItems() }
            },
            72000L, // Initial delay: 1 hour (in ticks)
            14400L // Period: 4 hours (in ticks)
        )

        logger.info("Expiration service started - checking every minute, cleanup every 4 hours")
    }

    /**
     * Stops the expiration checking task.
     */
    fun stop() {
        task?.cancel()
        task = null
        cleanupTask?.cancel()
        cleanupTask = null
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

    /**
     * Cleans up expired items older than the configured storage days.
     */
    suspend fun cleanupOldExpiredItems() {
        try {
            val auctionDays = config.auctions.expiredStorageDays
            val orderDays = config.orders.expiredStorageDays

            // Delete old expired items
            val deletedCount = plugin.getExpiredItemRepository().deleteOldItems(minOf(auctionDays, orderDays))

            if (deletedCount > 0) {
                logger.info("Cleaned up {} expired items older than {} days", deletedCount, minOf(auctionDays, orderDays))
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up old expired items", e)
        }
    }
}
