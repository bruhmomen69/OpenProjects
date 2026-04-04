package bruh.auctionhouse.hooks

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import com.github.shynixn.mccoroutine.folia.launch
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PlaceholderAPI expansion for AuctionHouse.
 * Provides placeholders for use in other plugins and scoreboards.
 */
class PlaceholderAPIHook(
    private val plugin: AuctionHousePlugin,
    private val auctionRepository: AuctionRepository,
    private val orderRepository: OrderRepository,
    private val consolidatedExpiredItemService: ConsolidatedExpiredItemService
) : PlaceholderExpansion() {
    private val playerSnapshots = ConcurrentHashMap<UUID, PlayerSnapshot>()
    private val refreshingPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val refreshingTotalAuctions = AtomicBoolean(false)

    @Volatile
    private var totalAuctionsSnapshot = CountSnapshot(0, 0L)

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
                    refreshPlayerSnapshotIfNeeded(it.uniqueId)
                    playerSnapshots[it.uniqueId]?.activeAuctions?.value?.toString() ?: "0"
                } ?: "0"
            }
            "active_orders" -> {
                player?.let {
                    refreshPlayerSnapshotIfNeeded(it.uniqueId)
                    playerSnapshots[it.uniqueId]?.activeOrders?.value?.toString() ?: "0"
                } ?: "0"
            }
            "total_auctions" -> {
                refreshTotalAuctionsIfNeeded()
                totalAuctionsSnapshot.value.toString()
            }
            "expired_items" -> {
                player?.let {
                    refreshPlayerSnapshotIfNeeded(it.uniqueId)
                    playerSnapshots[it.uniqueId]?.expiredItems?.value?.toString() ?: "0"
                } ?: "0"
            }
            else -> null
        }
    }

    private fun refreshPlayerSnapshotIfNeeded(playerId: UUID) {
        val current = playerSnapshots[playerId]
        if (current != null && !current.isStale()) return
        if (!refreshingPlayers.add(playerId)) return

        plugin.launch {
            try {
                playerSnapshots[playerId] = PlayerSnapshot(
                    activeAuctions = CountSnapshot(
                        auctionRepository.countPlayerAuctions(playerId, AuctionStatus.ACTIVE),
                        System.currentTimeMillis()
                    ),
                    activeOrders = CountSnapshot(
                        orderRepository.countPlayerOrders(playerId, OrderStatus.PENDING) +
                            orderRepository.countPlayerOrders(playerId, OrderStatus.PARTIAL),
                        System.currentTimeMillis()
                    ),
                    expiredItems = CountSnapshot(
                        consolidatedExpiredItemService.countPlayerConsolidatedItems(playerId),
                        System.currentTimeMillis()
                    )
                )
            } finally {
                refreshingPlayers.remove(playerId)
            }
        }
    }

    private fun refreshTotalAuctionsIfNeeded() {
        if (!totalAuctionsSnapshot.isStale() || !refreshingTotalAuctions.compareAndSet(false, true)) return

        plugin.launch {
            try {
                totalAuctionsSnapshot = CountSnapshot(
                    auctionRepository.getActiveAuctionsCount(),
                    System.currentTimeMillis()
                )
            } finally {
                refreshingTotalAuctions.set(false)
            }
        }
    }

    private data class CountSnapshot(val value: Int, val loadedAt: Long) {
        fun isStale(): Boolean = System.currentTimeMillis() - loadedAt > CACHE_TTL_MS
    }

    private data class PlayerSnapshot(
        val activeAuctions: CountSnapshot,
        val activeOrders: CountSnapshot,
        val expiredItems: CountSnapshot
    ) {
        fun isStale(): Boolean {
            return activeAuctions.isStale() || activeOrders.isStale() || expiredItems.isStale()
        }
    }

    private companion object {
        private const val CACHE_TTL_MS = 5_000L
    }
}
