package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.gui.AuctionCreateMenu
import bruh.auctionhouse.gui.AuctionHouseMenu
import bruh.auctionhouse.gui.ConsolidatedExpiredItemsMenu
import bruh.auctionhouse.gui.MyAuctionsMenu
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.ConsolidatedExpiredItemService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Named
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.time.Duration
import java.util.UUID

/**
 * Main AuctionHouse commands (/ah, /auctionhouse).
 * Provides access to the auction house system with subcommands for selling,
 * bidding, buying, and managing auctions.
 */
@Command("ah", "auctionhouse")
class AuctionHouseCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val auctionService: AuctionService,
    private val orderService: OrderService,
    private val consolidatedExpiredItemService: ConsolidatedExpiredItemService,
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI,
    private val economy: EconomyProvider
) {
    private val auctionRepository = AuctionRepository(plugin.database)
    private val bidRepository = BidRepository(plugin.database)
    private val orderRepository = OrderRepository(plugin.database)
    private val watchlistRepository = WatchlistRepository(plugin.database)

    /**
     * Default command - opens the main auction house menu.
     */
    @Subcommand
    fun openMenu(player: Player) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        AuctionHouseMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
    }

    /**
     * Quick sell command - creates an auction from the held item.
     */
    @Subcommand("sell")
    @CommandPermission("auctionhouse.sell")
    suspend fun quickSell(
        player: Player,
        @Named("price") price: Double,
        @Optional @Named("binPrice") binPrice: Double?,
        @Optional @Named("duration") durationHours: Int?
    ) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type.isAir) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_INVALID_ITEM))
            return
        }

        val type = when {
            binPrice != null && price > 0 -> AuctionType.BOTH
            binPrice != null -> AuctionType.BIN
            else -> AuctionType.AUCTION
        }

        val actualBinPrice = if (type == AuctionType.BOTH || type == AuctionType.BIN) binPrice else null
        val duration = Duration.ofHours((durationHours ?: config.auctions.defaultDuration).toLong())

        val result = auctionService.createAuction(
            player, item, type, price, actualBinPrice, duration, false
        )
        player.sendMessage(result.message)

        if (result.success) {
            player.inventory.setItemInMainHand(null)
        }
    }

    @Subcommand("bid")
    @CommandPermission("auctionhouse.bid")
    suspend fun bid(
        player: Player,
        @Named("auctionId") auctionId: String,
        @Named("amount") amount: Double
    ) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }

        val auction = try {
            auctionService.getAuction(UUID.fromString(auctionId))
        } catch (e: IllegalArgumentException) {
            auctionService.findAuctionByShortId(auctionId)
        }

        if (auction == null) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val result = auctionService.placeBid(player, auction.id, amount)
        player.sendMessage(result.message)
    }

    @Subcommand("buy")
    @CommandPermission("auctionhouse.buy")
    suspend fun buy(
        player: Player,
        @Named("auctionId") auctionId: String
    ) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }

        val auction = try {
            auctionService.getAuction(UUID.fromString(auctionId))
        } catch (e: IllegalArgumentException) {
            auctionService.findAuctionByShortId(auctionId)
        }

        if (auction == null) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val result = auctionService.buyNow(player, auction.id)
        player.sendMessage(result.message)
    }

    @Subcommand("cancel")
    @CommandPermission("auctionhouse.cancel")
    suspend fun cancel(
        player: Player,
        @Named("auctionId") auctionId: String
    ) {
        val auction = try {
            auctionService.getAuction(UUID.fromString(auctionId))
        } catch (e: IllegalArgumentException) {
            auctionService.findAuctionByShortId(auctionId)
        }

        if (auction == null) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val result = auctionService.cancelAuction(player, auction.id)
        player.sendMessage(
            when (result) {
                is bruh.auctionhouse.service.ServiceResult.Success ->
                    translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED)
                is bruh.auctionhouse.service.ServiceResult.Failure ->
                    result.message
            }
        )
    }

    /**
     * Open claimable items menu (formerly expired items).
     */
    @Subcommand("expired")
    @CommandPermission("auctionhouse.expired")
    @Command("ah", "auctionhouse", "claimable", "claim")
    fun claimable(player: Player) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        ConsolidatedExpiredItemsMenu(
            menuAPI, consolidatedExpiredItemService, auctionService, orderService, auctionRepository, bidRepository, orderRepository,
            watchlistRepository, config, translationAPI, plugin, economy, player
        ).open()
    }

    /**
     * Open my auctions menu.
     */
    @Subcommand("myauctions")
    @CommandPermission("auctionhouse.myauctions")
    fun myAuctions(player: Player) {
        MyAuctionsMenu(menuAPI, auctionService, orderService, auctionRepository, bidRepository, orderRepository, watchlistRepository, config, translationAPI, plugin, economy, player).open()
    }

    /**
     * Open create auction menu.
     */
    @Subcommand("create")
    @CommandPermission("auctionhouse.sell")
    fun create(player: Player) {
        if (!plugin.isReady) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        AuctionCreateMenu(menuAPI, auctionService, config, translationAPI, plugin, player).open()
    }

    /**
     * Reload configuration.
     */
    @Subcommand("reload")
    @CommandPermission("auctionhouse.admin.reload")
    suspend fun reload(player: Player) {
        plugin.reloadPluginConfig()
        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.CONFIG_RELOADED))
    }
}