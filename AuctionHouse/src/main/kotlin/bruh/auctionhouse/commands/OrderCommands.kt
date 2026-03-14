package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.BidRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.WatchlistRepository
import bruh.auctionhouse.gui.MyOrdersMenu
import bruh.auctionhouse.gui.OrderBrowserMenu
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.OrderMessages
import bruh.auctionhouse.economy.EconomyProvider
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.Material
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Named
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.time.Duration
import java.util.UUID

@Command("order", "orders")
class OrderCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val orderService: OrderService,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI,
    private val economy: EconomyProvider
) {
    private val auctionRepository = AuctionRepository(plugin.database)
    private val bidRepository = BidRepository(plugin.database)
    private val orderRepository = OrderRepository(plugin.database)
    private val watchlistRepository = WatchlistRepository(plugin.database)

    @Subcommand("list")
    @CommandPermission("order.list")
    fun list(player: Player) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        OrderBrowserMenu(
            menuAPI,
            auctionService,
            orderService,
            auctionRepository,
            bidRepository,
            orderRepository,
            watchlistRepository,
            config,
            translationAPI,
            plugin,
            economy,
            player
        ).createMenuOrNull()?.let { menuAPI.open(it, player) }
    }

    @Subcommand("buy")
    @CommandPermission("order.buy")
    suspend fun createBuyOrder(
        player: Player,
        @Named("material") material: Material,
        @Named("quantity") quantity: Int,
        @Named("pricePerUnit") pricePerUnit: Double,
        @Optional @Named("duration") durationHours: Int?
    ) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }

        val duration = Duration.ofHours((durationHours ?: config.orders.defaultDuration).toLong())

        val result = orderService.createBuyOrder(
            player, material, null, quantity, pricePerUnit,
            config.orders.partialFills.defaultAllowPartial, null,
            duration
        )
        player.sendMessage(result.message)
    }

    @Subcommand("sell")
    @CommandPermission("order.sell")
    suspend fun createSellOrder(
        player: Player,
        @Named("pricePerUnit") pricePerUnit: Double,
        @Optional @Named("quantity") quantity: Int?,
        @Optional @Named("duration") durationHours: Int?
    ) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type.isAir) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_WRONG_ITEM))
            return
        }

        val itemToSell = if (quantity != null && quantity > 0 && quantity <= item.amount) {
            item.clone().apply { amount = quantity }
        } else {
            item.clone()
        }

        val duration = Duration.ofHours((durationHours ?: config.orders.defaultDuration).toLong())

        val result = orderService.createSellOrder(
            player, itemToSell, pricePerUnit, duration
        )

        if (result.success) {
            player.inventory.removeItem(itemToSell)
        }
        player.sendMessage(result.message)
    }

    @Subcommand("cancel")
    @CommandPermission("order.cancel")
    suspend fun cancel(
        player: Player,
        @Named("orderId") orderId: String
    ) {
        val order = run {
            val byFullUuid = try {
                orderService.getOrder(UUID.fromString(orderId))
            } catch (e: IllegalArgumentException) {
                null
            }
            
            if (byFullUuid != null) {
                byFullUuid
            } else {
                orderService.findOrderByShortId(orderId)
            }
        }
        
        if (order == null) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
            return
        }

        val result = orderService.cancelOrder(player, order.id)
        player.sendMessage(
            when (result) {
                is bruh.auctionhouse.service.ServiceResult.Success ->
                    translationAPI.getComponentSync(OrderMessages.ORDER_CANCELLED)
                is bruh.auctionhouse.service.ServiceResult.Failure ->
                    result.message
            }
        )
    }

    @Subcommand("fulfill")
    @CommandPermission("order.fulfill")
    fun fulfill(
        player: Player,
        @Named("orderId") orderId: String
    ) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }

        val uuid = try {
            UUID.fromString(orderId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
            return
        }

        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_FULFILLED) {
            unparsed("amount", "0")
        })
    }

    @Subcommand("myorders")
    @CommandPermission("order.myorders")
    fun myOrders(player: Player) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        val menu = MyOrdersMenu(
            menuAPI,
            auctionService,
            orderService,
            auctionRepository,
            bidRepository,
            orderRepository,
            watchlistRepository,
            config,
            translationAPI,
            plugin,
            economy,
            player
        ).createMenu()
        menuAPI.open(menu, player)
    }
}
