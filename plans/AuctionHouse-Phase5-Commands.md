# Phase 5: Commands - Detailed Implementation Plan

This phase creates all command classes using the Lamp command framework.

---

## Step 1: Create Main Auction Commands

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/commands/AuctionHouseCommands.kt` (Create)
```kotlin
package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.gui.AuctionCreateMenu
import bruh.auctionhouse.gui.AuctionHouseMenu
import bruh.auctionhouse.gui.ExpiredItemsMenu
import bruh.auctionhouse.gui.MyAuctionsMenu
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Named
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.time.Duration
import java.util.UUID

@Command("ah", "auctionhouse")
class AuctionHouseCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI
) {
    @Subcommand
    fun openMenu(player: Player) {
        if (!plugin.isEnabledFlag) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        AuctionHouseMenu(menuAPI, auctionService, translationAPI, player).open()
    }
    
    @Subcommand("sell")
    @CommandPermission("auctionhouse.sell")
    fun quickSell(
        player: Player,
        @Named("price") price: Double,
        @Optional @Named("bin") binPrice: Double?
    ) {
        if (!plugin.isEnabledFlag) {
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
        
        plugin.launch {
            val result = auctionService.createAuction(
                player, item, type, price, actualBinPrice,
                Duration.ofHours(config.auctions.defaultDuration.toLong()), false
            )
            player.sendMessage(result.message)
            
            if (result.success) {
                player.inventory.setItemInMainHand(null)
            }
        }
    }
    
    @Subcommand("bid")
    @CommandPermission("auctionhouse.bid")
    fun bid(
        player: Player,
        @Named("auction") auctionId: String,
        @Named("amount") amount: Double
    ) {
        if (!plugin.isEnabledFlag) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }
        
        plugin.launch {
            val result = auctionService.placeBid(player, uuid, amount)
            player.sendMessage(result.message)
        }
    }
    
    @Subcommand("buy")
    @CommandPermission("auctionhouse.buy")
    fun buy(
        player: Player,
        @Named("auction") auctionId: String
    ) {
        if (!plugin.isEnabledFlag) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
            return
        }
        
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }
        
        plugin.launch {
            val result = auctionService.buyNow(player, uuid)
            player.sendMessage(result.message)
        }
    }
    
    @Subcommand("cancel")
    @CommandPermission("auctionhouse.cancel")
    fun cancel(
        player: Player,
        @Named("auction") auctionId: String
    ) {
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }
        
        plugin.launch {
            val result = auctionService.cancelAuction(player, uuid)
            player.sendMessage(
                when (result) {
                    is bruh.auctionhouse.service.ServiceResult.Success ->
                        translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED)
                    is bruh.auctionhouse.service.ServiceResult.Failure ->
                        result.message
                }
            )
        }
    }
    
    @Subcommand("expired")
    fun expired(player: Player) {
        // Open expired items menu
        ExpiredItemsMenu(menuAPI, auctionService, translationAPI, player).open()
    }
    
    @Subcommand("list")
    @CommandPermission("auctionhouse.list")
    fun list(player: Player) {
        MyAuctionsMenu(menuAPI, auctionService, translationAPI, player).open()
    }
    
    @Subcommand("reload")
    @CommandPermission("auctionhouse.admin.reload")
    fun reload(actor: BukkitCommandActor) {
        plugin.launch {
            plugin.reloadConfig()
            actor.reply(translationAPI.getComponentSync(AuctionMessages.CONFIG_RELOADED))
        }
    }
}
```

---

## Step 2: Create Order Commands

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/commands/OrderCommands.kt` (Create)
```kotlin
package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.service.OrderService
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.launch
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
    private val translationAPI: TranslationAPI,
    private val menuAPI: MenuAPI
) {
    @Subcommand
    fun list(player: Player) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        // Open order browser menu
        OrderBrowserMenu(orderService, translationAPI, player).open()
    }
    
    @Subcommand("buy")
    @CommandPermission("auctionhouse.orders.create")
    fun createBuyOrder(
        player: Player,
        @Named("material") material: Material,
        @Named("quantity") quantity: Int,
        @Named("price") pricePerUnit: Double
    ) {
        if (!config.orders.enabled) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED))
            return
        }
        
        plugin.launch {
            val result = orderService.createBuyOrder(
                player, material, null, quantity, pricePerUnit,
                config.orders.partialFills.defaultAllowPartial, null,
                Duration.ofHours(config.orders.defaultDuration.toLong())
            )
            player.sendMessage(result.message)
        }
    }
    
    @Subcommand("sell")
    @CommandPermission("auctionhouse.orders.create")
    fun createSellOrder(
        player: Player,
        @Named("price") pricePerUnit: Double
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
        
        plugin.launch {
            val result = orderService.createSellOrder(
                player, item, pricePerUnit,
                Duration.ofHours(config.orders.defaultDuration.toLong())
            )
            
            if (result.success) {
                player.inventory.setItemInMainHand(null)
            }
            player.sendMessage(result.message)
        }
    }
    
    @Subcommand("cancel")
    @CommandPermission("auctionhouse.orders.cancel")
    fun cancel(
        player: Player,
        @Named("order") orderId: String
    ) {
        val uuid = try {
            UUID.fromString(orderId)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND))
            return
        }
        
        plugin.launch {
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
    }
    
    @Subcommand("fulfill")
    @CommandPermission("auctionhouse.orders.fulfill")
    fun fulfill(
        player: Player,
        @Named("order") orderId: String
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
        
        // Open fulfill menu
        OrderFulfillMenu(orderService, translationAPI, player, uuid).open()
    }
}
```

---

## Step 3: Create Admin Commands

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/commands/AuctionAdminCommands.kt` (Create)
```kotlin
package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("ahadmin", "auctionhouseadmin")
@CommandPermission("auctionhouse.admin")
class AuctionAdminCommands(
    private val plugin: AuctionHousePlugin,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {
    @Subcommand("toggle")
    fun toggle(actor: BukkitCommandActor) {
        val newState = plugin.toggleEnabled()
        actor.reply(
            if (newState) {
                translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_ON)
            } else {
                translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF)
            }
        )
    }
    
    @Subcommand("status")
    fun status(actor: BukkitCommandActor) {
        actor.reply(
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<green>AuctionHouse Status:</green>\n" +
                "<gray>Enabled: <white>${plugin.isEnabledFlag}</white>\n" +
                "<gray>Version: <white>${plugin.description.version}</white>"
            )
        )
    }
}
```

---

## Step 4: Update Main Plugin Class with Commands

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/AuctionHousePlugin.kt` (Update)
Add to the `onEnableAsync()` method after economy initialization:

```kotlin
// Initialize database
val dbConfig = bruh.zchat.utils.database.DatabaseConfig(
    dialect = bruh.zchat.utils.database.DatabaseDialect.valueOf(config.database.type),
    sqliteFile = config.database.sqliteFile,
    host = config.database.host,
    port = config.database.port,
    database = config.database.database,
    username = config.database.username,
    password = config.database.password,
    poolSize = config.database.poolSize
)

val database = createDatabase(dbConfig) {
    schema(AuctionHouseSchema)
}

val migrationReport = database.initialize()
logger.info("Database initialized: ${migrationReport.totalApplied} migrations applied")

// Create repositories
val auctionRepository = AuctionRepository(database)
val bidRepository = BidRepository(database)
val orderRepository = OrderRepository(database)
val orderFillRepository = OrderFillRepository(database)
val expiredItemRepository = ExpiredItemRepository(database)
val transactionRepository = TransactionRepository(database)

// Create services
val auctionService = AuctionService(
    this, config, auctionRepository, bidRepository, expiredItemRepository,
    transactionRepository, economy, translationAPI, config.serverId
)

val orderService = OrderService(
    this, config, orderRepository, orderFillRepository, expiredItemRepository,
    transactionRepository, economy, translationAPI, config.serverId
)

// Initialize MenuAPI
menuAPI = MenuAPI(this)

// Register commands
val lamp = revxrsal.commands.bukkit.BukkitLamp.builder(this).build()
lamp.register(AuctionHouseCommands(this, config, auctionService, translationAPI, menuAPI))
lamp.register(OrderCommands(this, config, orderService, translationAPI, menuAPI))
lamp.register(AuctionAdminCommands(this, auctionService, translationAPI))
```

Add to the imports:
```kotlin
import bruh.auctionhouse.commands.*
import bruh.auctionhouse.database.*
import bruh.auctionhouse.service.*
import bruh.zchat.utils.database.createDatabase
import bruh.zchat.utils.menuapi.MenuAPI
```

---

## Phase 5 Completion Checklist

After completing Phase 5, you should have:

- [ ] `AuctionHouseCommands.kt` - Main /ah command
- [ ] `OrderCommands.kt` - /order commands
- [ ] `AuctionAdminCommands.kt` - Admin commands
- [ ] Updated `AuctionHousePlugin.kt` with command registration
- [ ] Updated `AuctionHousePlugin.kt` with database initialization
- [ ] Updated `AuctionHousePlugin.kt` with service initialization

## Build Verification

```bash
./gradlew :AuctionHouse:build
./gradlew :AuctionHouse:shadowJar
```

The plugin should now be fully functional with all commands working.
