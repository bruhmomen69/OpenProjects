# Phase 1: Foundation - Detailed Implementation Plan

This phase establishes the module structure, main plugin class, configuration system, translation system, and economy provider.

---

## Step 1: Create Module Structure

### File: `settings.gradle.kts` (Modify existing)
Add to the end of the file:
```kotlin
// AuctionHouse
include(":AuctionHouse")
```

### File: `AuctionHouse/build.gradle.kts` (Create)
```kotlin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "bruh.zchat.auctionhouse"
version = "1.0.0"

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinStdlib)
    
    // Kyori Adventure
    compileOnly(libs.bundles.adventure)
    
    // Configurate
    compileOnly(libs.configurateHocon)
    
    // Lamp
    compileOnly(libs.bundles.lamp)
    
    // Logging
    compileOnly(libs.bundles.slf4j)
    
    // Vault
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    
    // Database
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    compileOnly(libs.hikaricp)
    compileOnly(libs.caffeine)
    
    // Coroutines
    implementation(libs.bundles.mccoroutine)
    implementation(libs.kotlinxCoroutinesCore)
    
    // Project utils
    implementation(project(":utils"))
    implementation(libs.kotlinxSerializationJson)
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    
    relocate("com.github.shynixn", "bruh.zchat.auctionhouse.dependencies.com.github.shynixn")
    
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
```

### File: `AuctionHouse/README.md` (Create)
```markdown
# AuctionHouse

A GUI-based Auction House plugin with dual-mode auctions (Auction + BIN) and an Order system for bulk item requests.

## Features

- **Auction System**: Create auctions with bidding, Buy-It-Now (BIN), or both
- **Order System**: Request specific items in bulk quantities
- **Vault Integration**: Full economy support with configurable fees
- **Flexible Configuration**: Extensive customization for admins

## Commands

- `/ah` - Open the auction house
- `/ah sell <price> [binPrice]` - Quick sell held item
- `/ah bid <auctionId> <amount>` - Place a bid
- `/ah buy <auctionId>` - Buy via BIN
- `/order` - Browse orders
- `/order buy <material> <quantity> <price>` - Create buy order
- `/ahadmin` - Admin commands

## Configuration

See `config.conf` for all configuration options.

## Dependencies

- Vault (required)
- Paper 1.21+ (required)
```

---

## Step 2: Create Plugin Resources

### File: `AuctionHouse/src/main/resources/paper-plugin.yml` (Create)
```yaml
name: AuctionHouse
version: ${version}
main: bruh.zchat.auctionhouse.AuctionHousePlugin
api-version: '1.21'
load: POSTWORLD
folia-supported: true
dependencies:
  server:
    Vault:
      load: BEFORE
      required: true
      join-classpath: true
```

### File: `AuctionHouse/src/main/resources/plugin.yml` (Create - fallback)
```yaml
name: AuctionHouse
version: ${version}
main: bruh.zchat.auctionhouse.AuctionHousePlugin
api-version: '1.21'
depend: [Vault]
```

---

## Step 3: Create Configuration Classes

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/config/AuctionHouseConfig.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class AuctionHouseConfig(
    @Comment("Language for translations. Configure translations in the 'translations' folder.")
    val language: String = "en",
    
    val database: DatabaseConfig = DatabaseConfig(),
    val auctions: AuctionSettings = AuctionSettings(),
    val orders: OrderSettings = OrderSettings(),
    val gui: GuiSettings = GuiSettings(),
    val economy: EconomySettings = EconomySettings(),
    val restrictions: RestrictionsConfig = RestrictionsConfig(),
    val notifications: NotificationSettings = NotificationSettings()
)

@ConfigSerializable
data class DatabaseConfig(
    @Comment("Options: SQLITE, MYSQL, POSTGRESQL")
    val type: String = "SQLITE",
    val sqliteFile: String = "auctionhouse.db",
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "auctionhouse",
    val username: String = "root",
    val password: String = "",
    val poolSize: Int = 10
)

@ConfigSerializable
data class AuctionSettings(
    @Comment("Duration options in hours shown to players")
    val durationOptions: List<Int> = listOf(1, 6, 12, 24, 48, 72),
    val defaultDuration: Int = 24,
    val maxDuration: Int = 168,
    
    val minStartPrice: Double = 1.0,
    val maxStartPrice: Double = 1000000000.0,
    val minIncrement: Double = 1.0,
    val defaultIncrement: Double = 5.0,
    
    val listingFee: FeeConfig = FeeConfig("PERCENTAGE", 1.0, 10.0, 10000.0),
    val saleFee: FeeConfig = FeeConfig("PERCENTAGE", 5.0, 0.0, 100000.0),
    
    val maxConcurrentAuctions: Int = 10,
    val expiredStorageDays: Int = 30,
    
    @Comment("Allow auctions with both bidding AND BIN")
    val allowCombined: Boolean = true,
    val minBinMultiplier: Double = 1.5,
    
    val antiSnipe: AntiSnipeConfig = AntiSnipeConfig(),
    val display: AuctionDisplayConfig = AuctionDisplayConfig()
)

@ConfigSerializable
data class FeeConfig(
    val type: String = "PERCENTAGE",
    val amount: Double = 0.0,
    val minFee: Double = 0.0,
    val maxFee: Double = 0.0
)

@ConfigSerializable
data class AntiSnipeConfig(
    val enabled: Boolean = true,
    val thresholdMinutes: Int = 5,
    val extensionMinutes: Int = 5,
    val maxExtensions: Int = 3
)

@ConfigSerializable
data class AuctionDisplayConfig(
    val showSeller: Boolean = true,
    val allowAnonymous: Boolean = true,
    val anonymousFee: Double = 1000.0,
    val showBidHistory: Boolean = true,
    val maxBidHistory: Int = 5
)

@ConfigSerializable
data class OrderSettings(
    val enabled: Boolean = true,
    val durationOptions: List<Int> = listOf(24, 48, 72, 168),
    val defaultDuration: Int = 72,
    val maxDuration: Int = 336,
    
    val minQuantity: Int = 1,
    val maxQuantity: Int = 10000,
    val minPricePerUnit: Double = 0.01,
    val maxPricePerUnit: Double = 10000000.0,
    
    val listingFee: FeeConfig = FeeConfig("FLAT", 100.0, 100.0, 100.0),
    val fillFee: FeeConfig = FeeConfig("PERCENTAGE", 2.5, 0.0, 100000.0),
    
    val partialFills: PartialFillConfig = PartialFillConfig(),
    val maxConcurrentOrders: Int = 5,
    val expiredStorageDays: Int = 30,
    
    val matching: OrderMatchingConfig = OrderMatchingConfig()
)

@ConfigSerializable
data class PartialFillConfig(
    val enabled: Boolean = true,
    val defaultAllowPartial: Boolean = true,
    val minPartialQuantity: Int = 1
)

@ConfigSerializable
data class OrderMatchingConfig(
    val requireExactNbt: Boolean = false,
    val requireExactName: Boolean = false,
    val ignoreCustomNames: Boolean = true
)

@ConfigSerializable
data class GuiSettings(
    val auctionMenuRows: Int = 6,
    val itemsPerPage: Int = 28,
    val updateInterval: Int = 30,
    
    val confirm: ConfirmConfig = ConfirmConfig(),
    val defaultSort: String = "ENDING_SOON",
    val defaultFilter: String = "ALL"
)

@ConfigSerializable
data class ConfirmConfig(
    val expensiveThreshold: Double = 10000.0,
    val skipConfirmForCheap: Boolean = true
)

@ConfigSerializable
data class EconomySettings(
    val currencySymbol: String = "$",
    val decimalPlaces: Int = 2,
    val compactFormatting: Boolean = true
)

@ConfigSerializable
data class RestrictionsConfig(
    val blacklistedMaterials: List<String> = listOf("BEDROCK", "BARRIER", "COMMAND_BLOCK"),
    val nbtBlacklist: List<String> = emptyList(),
    val disabledWorlds: List<String> = emptyList(),
    val blockCreative: Boolean = true
)

@ConfigSerializable
data class NotificationSettings(
    val alertOnLogin: Boolean = true,
    val alertOutbid: Boolean = true,
    val alertSold: Boolean = true,
    val alertOrderFilled: Boolean = true,
    
    val sounds: SoundConfig = SoundConfig()
)

@ConfigSerializable
data class SoundConfig(
    val outbid: String = "ENTITY_VILLAGER_NO",
    val sold: String = "ENTITY_PLAYER_LEVELUP",
    val won: String = "ENTITY_PLAYER_LEVELUP",
    val expired: String = "BLOCK_ANVIL_LAND"
)
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/config/AuctionHouseConfigLoader.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path

class AuctionHouseConfigLoader(
    private val dataFolder: Path,
    private val logger: Logger
) {
    private val configPath: Path = dataFolder.resolve("config.conf")

    private val loader: ConfigurationLoader<CommentedConfigurationNode> =
        HoconConfigurationLoader.builder()
            .path(configPath)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

    suspend fun load(): AuctionHouseConfig = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = loader.load()

        var config: AuctionHouseConfig? = rootNode.get()
        if (config == null) config = AuctionHouseConfig()

        rootNode.set(config)
        loader.save(rootNode)

        config
    }

    suspend fun reload(): AuctionHouseConfig = load()
    
    suspend fun save(config: AuctionHouseConfig) = withContext(Dispatchers.IO) {
        val rootNode = loader.createNode()
        rootNode.set(config)
        loader.save(rootNode)
    }
}
```

---

## Step 4: Create Translation Messages

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/translations/AuctionMessages.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

enum class AuctionMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // General errors
    PLAYER_ONLY("player_only", "<red>This command can only be run by a player."),
    NO_PERMISSION("no_permission", "<red>You don't have permission to do that."),
    CONFIG_RELOADED("config_reloaded", "<green>Configuration reloaded."),
    
    // Auction creation
    AUCTION_CREATED("auction_created", "<green>Your auction has been created!"),
    AUCTION_CREATED_FEE("auction_created_fee", "<yellow>Listing fee: <gold>{fee}</gold> charged."),
    AUCTION_INVALID_ITEM("auction_invalid_item", "<red>You cannot auction this item."),
    AUCTION_BLACKLISTED("auction_blacklisted", "<red>This item type is blacklisted."),
    AUCTION_PRICE_TOO_LOW("auction_price_too_low", "<red>Price must be at least <gold>{min}</gold>."),
    AUCTION_PRICE_TOO_HIGH("auction_price_too_high", "<red>Price cannot exceed <gold>{max}</gold>."),
    AUCTION_MAX_REACHED("auction_max_reached", "<red>You can only have {max} active auctions."),
    
    // Bidding
    BID_PLACED("bid_placed", "<green>Bid placed! You are now the highest bidder."),
    BID_TOO_LOW("bid_too_low", "<red>Your bid must be at least <gold>{min}</gold>."),
    BID_OUTBID("bid_outbid", "<red>You have been outbid on <gold>{item}</gold>! New bid: <gold>{amount}</gold>."),
    BID_NO_BALANCE("bid_no_balance", "<red>You don't have enough money for this bid."),
    BID_CANNOT_ON_BIN("bid_cannot_on_bin", "<red>This is a BIN-only auction. Use /ah buy."),
    
    // BIN
    BIN_PURCHASED("bin_purchased", "<green>Purchased <gold>{item}</gold> for <gold>{price}</gold>!"),
    BIN_ALREADY_SOLD("bin_already_sold", "<red>This item has already been sold."),
    BIN_NO_BALANCE("bin_no_balance", "<red>You don't have enough money to buy this item."),
    
    // Auction end
    AUCTION_SOLD("auction_sold", "<green>Your <gold>{item}</gold> sold for <gold>{price}</gold>!"),
    AUCTION_WON("auction_won", "<green>You won <gold>{item}</gold> for <gold>{price}</gold>!"),
    AUCTION_EXPIRED("auction_expired", "<yellow>Your auction for <gold>{item}</gold> has expired."),
    AUCTION_CANCELLED("auction_cancelled", "<yellow>Your auction has been cancelled."),
    AUCTION_NOT_FOUND("auction_not_found", "<red>Auction not found."),
    AUCTION_NOT_OWNER("auction_not_owner", "<red>You don't own this auction."),
    AUCTION_ALREADY_ENDED("auction_already_ended", "<red>This auction has already ended."),
    
    // Expired items
    EXPIRED_RETRIEVED("expired_retrieved", "<green>Retrieved <gold>{item}</gold>."),
    EXPIRED_INVENTORY_FULL("expired_inventory_full", "<red>Your inventory is full!"),
    EXPIRED_NONE("expired_none", "<gray>You have no expired items to retrieve."),
    
    // Admin
    ADMIN_PURGED("admin_purged", "<green>Purged {count} old records."),
    ADMIN_TOGGLE_ON("admin_toggle_on", "<green>Auction House enabled."),
    ADMIN_TOGGLE_OFF("admin_toggle_off", "<red>Auction House disabled."),
    ADMIN_GIVEN("admin_given", "<green>Gave auction item to {player}."),
    ADMIN_REFUNDED("admin_refunded", "<green>Refunded auction to {player}.");
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/translations/OrderMessages.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

enum class OrderMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // Order creation
    ORDER_CREATED("order_created", "<green>Order created successfully!"),
    ORDER_CANCELLED("order_cancelled", "<yellow>Order cancelled."),
    ORDER_NOT_FOUND("order_not_found", "<red>Order not found."),
    ORDER_NOT_OWNER("order_not_owner", "<red>You don't own this order."),
    ORDER_INVALID_QUANTITY("order_invalid_quantity", "<red>Quantity must be between {min} and {max}."),
    ORDER_INVALID_PRICE("order_invalid_price", "<red>Price per unit must be between {min} and {max}."),
    ORDER_MAX_REACHED("order_max_reached", "<red>You can only have {max} active orders."),
    
    // Order filling
    ORDER_FILLED("order_filled", "<green>Your order has been completely filled!"),
    ORDER_PARTIAL_FILL("order_partial_fill", "<yellow>Your order was partially filled ({filled}/{total})."),
    ORDER_FULFILLED("order_fulfilled", "<green>You fulfilled an order and received <gold>{amount}</gold>!"),
    ORDER_NOT_ENOUGH_ITEMS("order_not_enough_items", "<red>You don't have enough items to fulfill this order."),
    ORDER_MIN_FILL_NOT_MET("order_min_fill_not_met", "<red>You must fulfill at least {min} items."),
    ORDER_WRONG_ITEM("order_wrong_item", "<red>The items don't match the order requirements."),
    
    // Order status
    ORDER_EXPIRED("order_expired", "<yellow>Your order has expired."),
    ORDER_ALREADY_FILLED("order_already_filled", "<red>This order has already been filled."),
    ORDER_SYSTEM_DISABLED("order_system_disabled", "<red>The order system is currently disabled.");
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/translations/GuiMessages.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.translations

import bruh.zchat.utils.translations.MessageKey

enum class GuiMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // Titles
    MAIN_TITLE("main_title", "Auction House"),
    MY_AUCTIONS_TITLE("my_auctions_title", "My Auctions"),
    MY_ORDERS_TITLE("my_orders_title", "My Orders"),
    ORDERS_TITLE("orders_title", "Order Browser"),
    CREATE_AUCTION_TITLE("create_auction_title", "Create Auction"),
    CREATE_ORDER_TITLE("create_order_title", "Create Order"),
    EXPIRED_ITEMS_TITLE("expired_items_title", "Expired Items"),
    
    // Navigation
    PREVIOUS_PAGE("previous_page", "<gray>← Previous Page"),
    NEXT_PAGE("next_page", "<gray>Next Page →"),
    BACK("back", "<gray>Back"),
    CLOSE("close", "<red>Close"),
    
    // Sorting
    SORT_TITLE("sort_title", "Sort Options"),
    SORT_ENDING_SOON("sort_ending_soon", "Ending Soon"),
    SORT_NEWEST("sort_newest", "Newest First"),
    SORT_PRICE_LOW("sort_price_low", "Price: Low to High"),
    SORT_PRICE_HIGH("sort_price_high", "Price: High to Low"),
    SORT_MOST_BIDS("sort_most_bids", "Most Bids"),
    
    // Filters
    FILTER_ALL("filter_all", "All Auctions"),
    FILTER_AUCTION("filter_auction", "Auction Only"),
    FILTER_BIN("filter_bin", "Buy It Now Only"),
    FILTER_BOTH("filter_both", "Auction + BIN"),
    
    // Buttons
    BUTTON_CREATE_AUCTION("button_create_auction", "<green>Create Auction"),
    BUTTON_CREATE_ORDER("button_create_order", "<green>Create Order"),
    BUTTON_REFRESH("button_refresh", "<yellow>Refresh"),
    BUTTON_SEARCH("button_search", "<yellow>Search"),
    
    // Item lore - Auction
    LORE_SELLER("lore_seller", "<gray>Seller: <white>{seller}"),
    LORE_PRICE_BIN("lore_price_bin", "<green>Buy Now: <gold>{price}"),
    LORE_PRICE_AUCTION("lore_price_auction", "<yellow>Current Bid: <gold>{bid}"),
    LORE_PRICE_START("lore_price_start", "<gray>Starting: <white>{price}"),
    LORE_BIDS("lore_bids", "<gray>Bids: <white>{count}"),
    LORE_TIME_LEFT("lore_time_left", "<gray>Ends in: <yellow>{time}"),
    LORE_EXPIRED("lore_expired", "<red>Expired"),
    LORE_SOLD("lore_sold", "<green>SOLD"),
    LORE_CLICK_TO_BID("lore_click_to_bid", "<yellow>Click to place bid"),
    LORE_CLICK_TO_BUY("lore_click_to_buy", "<green>Click to buy now"),
    LORE_SHIFT_FOR_DETAILS("lore_shift_for_details", "<gray>Shift-click for details"),
    
    // Item lore - Order
    LORE_ORDER_TYPE("lore_order_type", "<gray>Type: <white>{type}"),
    LORE_ORDER_QUANTITY("lore_order_quantity", "<gray>Quantity: <white>{filled}/{total}"),
    LORE_ORDER_PRICE("lore_order_price", "<gray>Price/Unit: <gold>{price}"),
    LORE_ORDER_TOTAL("lore_order_total", "<gray>Total: <gold>{total}"),
    LORE_ORDER_ALLOW_PARTIAL("lore_order_allow_partial", "<gray>Partial fills: <green>Allowed"),
    LORE_ORDER_NO_PARTIAL("lore_order_no_partial", "<gray>Partial fills: <red>Not allowed"),
    LORE_CLICK_TO_FULFILL("lore_click_to_fulfill", "<green>Click to fulfill order"),
    
    // Create auction
    CREATE_PLACE_ITEM("create_place_item", "<yellow>Place item here"),
    CREATE_SET_START_PRICE("create_set_start_price", "<yellow>Set start price"),
    CREATE_SET_BIN_PRICE("create_set_bin_price", "<yellow>Set BIN price (optional)"),
    CREATE_SET_DURATION("create_set_duration", "<yellow>Set duration"),
    CREATE_ANONYMOUS("create_anonymous", "<yellow>Anonymous: {status}"),
    CREATE_CONFIRM("create_confirm", "<green>Confirm"),
    CREATE_CANCEL("create_cancel", "<red>Cancel");
}
```

---

## Step 5: Create Economy Provider

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/economy/EconomyProvider.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.economy

import net.kyori.adventure.text.Component
import java.util.UUID

interface EconomyProvider {
    fun getBalance(player: UUID): Double
    fun hasBalance(player: UUID, amount: Double): Boolean
    fun withdraw(player: UUID, amount: Double): Boolean
    fun deposit(player: UUID, amount: Double): Boolean
    fun format(amount: Double): Component
    fun formatRaw(amount: Double): String
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/economy/VaultEconomyProvider.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.economy

import net.kyori.adventure.text.Component
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class VaultEconomyProvider(
    private val plugin: JavaPlugin,
    private val currencySymbol: String,
    private val compactFormatting: Boolean
) : EconomyProvider {
    private lateinit var economy: Economy
    
    fun initialize(): Boolean {
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        return if (rsp != null) {
            economy = rsp.provider
            true
        } else {
            false
        }
    }
    
    override fun getBalance(player: UUID): Double {
        return economy.getBalance(Bukkit.getOfflinePlayer(player))
    }
    
    override fun hasBalance(player: UUID, amount: Double): Boolean {
        return economy.has(Bukkit.getOfflinePlayer(player), amount)
    }
    
    override fun withdraw(player: UUID, amount: Double): Boolean {
        return economy.withdrawPlayer(Bukkit.getOfflinePlayer(player), amount).transactionSuccess()
    }
    
    override fun deposit(player: UUID, amount: Double): Boolean {
        return economy.depositPlayer(Bukkit.getOfflinePlayer(player), amount).transactionSuccess()
    }
    
    override fun format(amount: Double): Component {
        return Component.text(formatRaw(amount))
    }
    
    override fun formatRaw(amount: Double): String {
        val formatted = if (compactFormatting && amount >= 1000) {
            compactFormat(amount)
        } else {
            String.format("%,.2f", amount)
        }
        return "$currencySymbol$formatted"
    }
    
    private fun compactFormat(amount: Double): String {
        return when {
            amount >= 1_000_000_000 -> String.format("%.2fB", amount / 1_000_000_000)
            amount >= 1_000_000 -> String.format("%.2fM", amount / 1_000_000)
            amount >= 1_000 -> String.format("%.2fK", amount / 1_000)
            else -> String.format("%.2f", amount)
        }
    }
}
```

---

## Step 6: Create Main Plugin Class

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/AuctionHousePlugin.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse

import bruh.zchat.auctionhouse.config.AuctionHouseConfig
import bruh.zchat.auctionhouse.config.AuctionHouseConfigLoader
import bruh.zchat.auctionhouse.economy.EconomyProvider
import bruh.zchat.auctionhouse.economy.VaultEconomyProvider
import bruh.zchat.auctionhouse.translations.AuctionMessages
import bruh.zchat.auctionhouse.translations.GuiMessages
import bruh.zchat.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AuctionHousePlugin : SuspendingJavaPlugin() {
    companion object {
        lateinit var instance: AuctionHousePlugin
            private set
    }
    
    private val logger = LoggerFactory.getLogger(AuctionHousePlugin::class.java)
    
    lateinit var config: AuctionHouseConfig
        private set
    private lateinit var configLoader: AuctionHouseConfigLoader
    lateinit var translationAPI: TranslationAPI
        private set
    lateinit var economy: EconomyProvider
        private set
    lateinit var menuAPI: MenuAPI
        private set
    
    var isEnabledFlag = true
        private set
    
    override suspend fun onEnableAsync() {
        instance = this
        
        logger.info("Enabling AuctionHouse v${description.version}...")
        
        // Load configuration
        configLoader = AuctionHouseConfigLoader(dataFolder.toPath(), logger)
        config = configLoader.load()
        
        // Initialize translations
        initializeTranslations()
        
        // Initialize economy
        if (!initializeEconomy()) {
            logger.severe("Vault economy not found! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }
        
        // Initialize MenuAPI
        menuAPI = MenuAPI(this)
        
        // Log success
        logger.info("AuctionHouse enabled successfully!")
        logger.info("Language: ${config.language}")
        logger.info("Currency: ${config.economy.currencySymbol}")
    }
    
    override suspend fun onDisableAsync() {
        logger.info("Disabling AuctionHouse...")
        
        // Close MenuAPI
        if (::menuAPI.isInitialized) {
            menuAPI.close()
        }
        
        // Save configuration
        if (::configLoader.isInitialized && ::config.isInitialized) {
            launch {
                configLoader.save(config)
            }
        }
        
        logger.info("AuctionHouse disabled.")
    }
    
    private suspend fun initializeTranslations() {
        val translationsDir = dataFolder.toPath().resolve("translations")
        translationAPI = TranslationAPI(translationsDir)
        
        // Register all message enums
        translationAPI.register("auction", AuctionMessages::class)
        translationAPI.register("order", OrderMessages::class)
        translationAPI.register("gui", GuiMessages::class)
        
        // Load translations
        translationAPI.load()
        
        // Switch to configured language if not default
        if (config.language != "en") {
            translationAPI.switchLanguage(config.language)
        }
    }
    
    private fun initializeEconomy(): Boolean {
        economy = VaultEconomyProvider(
            this,
            config.economy.currencySymbol,
            config.economy.compactFormatting
        )
        return (economy as VaultEconomyProvider).initialize()
    }
    
    suspend fun reloadConfig() {
        config = configLoader.reload()
        
        // Reload translations
        if (config.language != translationAPI.getCurrentLocale()) {
            translationAPI.switchLanguage(config.language)
        }
    }
    
    fun toggleEnabled(): Boolean {
        enabled = !enabled
        return enabled
    }
}
```

---

## Phase 1 Completion Checklist

After completing Phase 1, you should have:

- [ ] `settings.gradle.kts` updated with `:AuctionHouse` module
- [ ] `AuctionHouse/build.gradle.kts` with all dependencies
- [ ] `AuctionHouse/README.md` documentation
- [ ] `paper-plugin.yml` and `plugin.yml` resource files
- [ ] `AuctionHouseConfig.kt` with all configuration data classes
- [ ] `AuctionHouseConfigLoader.kt` for config loading
- [ ] `AuctionMessages.kt`, `OrderMessages.kt`, `GuiMessages.kt` enums
- [ ] `EconomyProvider.kt` interface
- [ ] `VaultEconomyProvider.kt` implementation
- [ ] `AuctionHousePlugin.kt` main class with lifecycle management

## Build Verification

Run these commands to verify Phase 1:
```bash
./gradlew :AuctionHouse:build
./gradlew :AuctionHouse:shadowJar
```

The plugin should compile successfully and be loadable by Paper (though it won't have any functionality yet).
