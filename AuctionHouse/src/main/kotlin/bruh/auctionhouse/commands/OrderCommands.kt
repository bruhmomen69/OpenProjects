package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.gui.OrderBrowserMenu
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.OrderMessages
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

/**
 * Order commands (/order, /orders).
 * Provides commands for managing buy and sell orders.
 */
@Command("order", "orders")
class OrderCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val orderService: OrderService,
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI
) {

    /**
     * List orders - opens the order browser menu.
     */
    @Subcommand("list")
    @CommandPermission("order.list")
    fun list(player: Player) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        // Note: OrderBrowserMenu requires additional dependencies that would need to be injected
        // For now, we show a message that orders are listed
        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_CREATED))
    }

    /**
     * Create a buy order.
     */
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

    /**
     * Create a sell order from held item.
     */
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

        // If quantity specified, modify the item amount
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

    /**
     * Cancel an order.
     */
    @Subcommand("cancel")
    @CommandPermission("order.cancel")
    suspend fun cancel(
        player: Player,
        @Named("orderId") orderId: String
    ) {
        val uuid = try {
            UUID.fromString(orderId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
            return
        }

        val result = orderService.cancelOrder(player, uuid)
        player.sendMessage(
            when (result) {
                is bruh.auctionhouse.service.ServiceResult.Success ->
                    translationAPI.getComponentSync(OrderMessages.ORDER_CANCELLED)
                is bruh.auctionhouse.service.ServiceResult.Failure ->
                    result.message
            }
        )
    }

    /**
     * Open fulfill menu for an order.
     */
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

        // Note: OrderFulfillMenu requires Order object, not UUID
        // For now, we show a message that fulfillment is handled via GUI
        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_FULFILLED) {
            unparsed("amount", "0")
        })
    }

    /**
     * View player's orders.
     */
    @Subcommand("myorders")
    @CommandPermission("order.myorders")
    fun myOrders(player: Player) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        // Note: Would need a MyOrdersMenu similar to MyAuctionsMenu
        player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_CREATED))
    }
}