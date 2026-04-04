package bruh.auctionhouse.service

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import com.github.shynixn.mccoroutine.folia.launch
import org.bukkit.scheduler.BukkitTask
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service that handles automatic expiration of auctions and orders.
 * Runs periodically to check for and process expired listings.
 */
class ExpirationService(
    private val plugin: AuctionHousePlugin,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    config: AuctionHouseConfig,
    private val logger: Logger
) {
    private var task: BukkitTask? = null
    private var cleanupTask: BukkitTask? = null
    private val expirationRunInProgress = AtomicBoolean(false)
    private val cleanupRunInProgress = AtomicBoolean(false)
    private val config: AuctionHouseConfig
        get() = plugin.config

    /**
     * Starts the expiration checking task.
     * Runs asynchronously every minute (1200 ticks).
     */
    fun start() {
        task = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable {
                if (!expirationRunInProgress.compareAndSet(false, true)) {
                    logger.warn("Skipping expiration pass because a previous run is still in progress")
                    return@Runnable
                }

                plugin.launch {
                    try {
                        checkExpirations()
                    } finally {
                        expirationRunInProgress.set(false)
                    }
                }
            },
            1200L, // Initial delay: 1 minute
            1200L  // Period: 1 minute
        )

        // Start cleanup task for old expired items (runs daily)
        cleanupTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable {
                if (!cleanupRunInProgress.compareAndSet(false, true)) {
                    logger.warn("Skipping expired-item cleanup because a previous run is still in progress")
                    return@Runnable
                }

                plugin.launch {
                    try {
                        cleanupOldExpiredItems()
                    } finally {
                        cleanupRunInProgress.set(false)
                    }
                }
            },
            72000L, // Initial delay: 1 hour (in ticks)
            288000L // Period: 4 hours (in ticks)
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

            val deletedAuctionItems = plugin.expiredItemRepository.deleteOldItems(
                auctionDays,
                bruh.auctionhouse.model.ExpiredItemType.AUCTION_ITEM
            )
            val deletedOrderItems = plugin.expiredItemRepository.deleteOldItems(
                orderDays,
                bruh.auctionhouse.model.ExpiredItemType.ORDER_ITEM
            )
            val deletedCount = deletedAuctionItems + deletedOrderItems

            if (deletedCount > 0) {
                logger.info(
                    "Cleaned up {} expired items (auction retention: {} days, order retention: {} days)",
                    deletedCount,
                    auctionDays,
                    orderDays
                )
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up old expired items", e)
        }
    }
}
