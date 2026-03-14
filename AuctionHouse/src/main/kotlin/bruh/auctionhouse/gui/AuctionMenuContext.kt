package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.*
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.OrderService
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Shared dependency context for all AuctionHouse menus.
 * Created once in [AuctionHousePlugin.onEnableAsync] and passed to commands.
 */
class AuctionMenuContext(
    val menuAPI: MenuAPI,
    val auctionService: AuctionService,
    val orderService: OrderService,
    val consolidatedExpiredItemService: ConsolidatedExpiredItemService,
    val auctionRepository: AuctionRepository,
    val bidRepository: BidRepository,
    val orderRepository: OrderRepository,
    val orderFillRepository: OrderFillRepository,
    val watchlistRepository: WatchlistRepository,
    val expiredItemRepository: ExpiredItemRepository,
    val consolidatedExpiredItemRepository: ConsolidatedExpiredItemRepository,
    val transactionRepository: TransactionRepository,
    val playerBanRepository: PlayerBanRepository,
    val config: AuctionHouseConfig,
    val translationAPI: TranslationAPI,
    val plugin: AuctionHousePlugin,
    val economy: EconomyProvider
) {
    val mm: MiniMessage = MiniMessage.miniMessage()

    fun forPlayer(player: Player) = PlayerMenuContext(this, player)
}

/**
 * Per-player menu context — wraps [AuctionMenuContext] with a specific player.
 * Passed to individual menu constructors.
 */
class PlayerMenuContext(
    val ctx: AuctionMenuContext,
    val player: Player
) {
    // Convenience delegates
    val menuAPI get() = ctx.menuAPI
    val auctionService get() = ctx.auctionService
    val orderService get() = ctx.orderService
    val consolidatedExpiredItemService get() = ctx.consolidatedExpiredItemService
    val auctionRepository get() = ctx.auctionRepository
    val bidRepository get() = ctx.bidRepository
    val orderRepository get() = ctx.orderRepository
    val orderFillRepository get() = ctx.orderFillRepository
    val watchlistRepository get() = ctx.watchlistRepository
    val expiredItemRepository get() = ctx.expiredItemRepository
    val consolidatedExpiredItemRepository get() = ctx.consolidatedExpiredItemRepository
    val transactionRepository get() = ctx.transactionRepository
    val playerBanRepository get() = ctx.playerBanRepository
    val config get() = ctx.config
    val translationAPI get() = ctx.translationAPI
    val plugin get() = ctx.plugin
    val economy get() = ctx.economy
    val mm get() = ctx.mm
}
