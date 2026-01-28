# AuctionHouse Module - Implementation Plan

## Overview
A comprehensive GUI-based Auction House plugin with dual-mode auctions (Auction + BIN), an Order system for bulk item requests, and extensive customization. Built following the existing codebase patterns (RegionRestore/EssentiallyStateless style) using Kotlin, Configurate, Lamp, and the project's utility modules.

---

## Module Structure

```
AuctionHouse/
├── build.gradle.kts              # Module build configuration
├── README.md                     # Module documentation
├── src/main/kotlin/
│   └── bruh/zchat/auctionhouse/
│       ├── AuctionHousePlugin.kt           # Main plugin class
│       ├── config/
│       │   ├── AuctionHouseConfig.kt       # Configuration classes
│       │   └── AuctionHouseConfigLoader.kt # Config loading
│       ├── translations/
│       │   ├── AuctionMessages.kt          # Auction-related messages
│       │   ├── OrderMessages.kt            # Order-related messages
│       │   └── GuiMessages.kt              # GUI-related messages
│       ├── commands/
│       │   ├── AuctionHouseCommands.kt     # Main /ah command
│       │   ├── AuctionAdminCommands.kt     # Admin commands
│       │   └── OrderCommands.kt            # Order system commands
│       ├── database/
│       │   ├── AuctionHouseSchema.kt       # Database migrations
│       │   ├── AuctionRepository.kt        # Auction data access
│       │   ├── OrderRepository.kt          # Order data access
│       │   └── TransactionRepository.kt    # Transaction logging
│       ├── economy/
│       │   ├── EconomyProvider.kt          # Economy interface
│       │   └── VaultEconomyProvider.kt     # Vault implementation
│       ├── model/
│       │   ├── Auction.kt                  # Auction data class
│       │   ├── AuctionType.kt              # Enum: AUCTION, BIN, BOTH
│       │   ├── AuctionStatus.kt            # Enum: ACTIVE, SOLD, EXPIRED, CANCELLED
│       │   ├── Order.kt                    # Order data class
│       │   ├── OrderStatus.kt              # Enum: PENDING, FILLED, EXPIRED, CANCELLED
│       │   ├── OrderType.kt                # Enum: BUY_ORDER, SELL_ORDER
│       │   ├── Transaction.kt              # Transaction record
│       │   └── Bid.kt                      # Bid data class
│       ├── service/
│       │   ├── AuctionService.kt           # Auction business logic
│       │   ├── OrderService.kt             # Order business logic
│       │   ├── ExpirationService.kt        # Handles auction/order expiration
│       │   ├── NotificationService.kt      # Player notifications
│       │   └── SearchService.kt            # Auction/order search/filtering
│       └── gui/
│           ├── AuctionHouseMenu.kt         # Main auction browser
│           ├── AuctionCreateMenu.kt        # Create auction flow
│           ├── AuctionDetailsMenu.kt       # View auction details
│           ├── OrderBrowserMenu.kt         # Browse orders
│           ├── OrderCreateMenu.kt          # Create order flow
│           ├── OrderFulfillMenu.kt         # Fulfill order flow
│           ├── MyAuctionsMenu.kt           # View own auctions
│           ├── MyOrdersMenu.kt             # View own orders
│           ├── ExpiredItemsMenu.kt         # Retrieve expired items
│           ├── SortOptionsMenu.kt          # Sort/filter options
│           └── components/
│               ├── AuctionItemRenderer.kt  # Render auction items
│               ├── OrderItemRenderer.kt    # Render order items
│               └── NavigationComponents.kt # Shared nav buttons
└── src/main/resources/
    ├── paper-plugin.yml
    ├── plugin.yml
    └── defaults/
        └── .gitkeep                      # Defaults loaded from code
```

---

## Dependencies

### Build.gradle.kts
```kotlin
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
```

### Paper-Plugin.yml
```yaml
name: AuctionHouse
version: ${version}
main: bruh.zchat.auctionhouse.AuctionHousePlugin
api-version: 1.21
load: POSTWORLD
folia-supported: true
dependencies:
  server:
    Vault:
      load: BEFORE
      required: true
      join-classpath: true
```

---

## Configuration System (Following RegionRestore Pattern)

### Config Structure
Uses `@ConfigSerializable` data classes with `@Comment` annotations, loaded via `HoconConfigurationLoader`.

### AuctionHouseConfig.kt
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
    val type: String,  // PERCENTAGE or FLAT
    val amount: Double,
    val minFee: Double,
    val maxFee: Double
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

### AuctionHouseConfigLoader.kt (Following EssentiallyStateless Pattern)
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
}
```

---

## Translation System (Following RegionRestore/EssentiallyStateless Pattern)

Uses `utils/translations` with `MessageKey` interface. The TranslationAPI auto-generates translation files from registered enums.

### AuctionMessages.kt
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
    ADMIN_RELOADED("admin_reloaded", "<green>Configuration reloaded."),
    ADMIN_TOGGLE_ON("admin_toggle_on", "<green>Auction House enabled."),
    ADMIN_TOGGLE_OFF("admin_toggle_off", "<red>Auction House disabled."),
    ADMIN_GIVEN("admin_given", "<green>Gave auction item to {player}."),
    ADMIN_REFUNDED("admin_refunded", "<green>Refunded auction to {player}.");
}
```

### OrderMessages.kt
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

### GuiMessages.kt
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

### Translation Initialization (In Plugin Class)
```kotlin
// In AuctionHousePlugin.kt
private lateinit var translationAPI: TranslationAPI

private suspend fun initializeTranslations() {
    val translationsDir = dataFolder.toPath().resolve("translations")
    translationAPI = TranslationAPI(translationsDir)
    
    // Register all message enums
    translationAPI.register("auction", AuctionMessages::class)
    translationAPI.register("order", OrderMessages::class)
    translationAPI.register("gui", GuiMessages::class)
    
    // Load translations
    translationAPI.load()
    
    // Switch to configured language
    val config = configManager.config
    if (config.language != "en") {
        translationAPI.switchLanguage(config.language)
    }
}
```

---

## Database Schema

### Tables

#### auctions
```sql
CREATE TABLE IF NOT EXISTS auctions (
    id VARCHAR(36) PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    seller_name VARCHAR(16) NOT NULL,
    item_stack BLOB NOT NULL,           -- Bukkit ItemStack serialized to bytes
    item_material VARCHAR(64) NOT NULL, -- For indexing/search
    item_display_name TEXT,             -- For display/filtering
    
    auction_type VARCHAR(20) NOT NULL,  -- AUCTION, BIN, BOTH
    start_price DECIMAL(19, 4) NOT NULL,
    buy_now_price DECIMAL(19, 4),
    reserve_price DECIMAL(19, 4),
    min_increment DECIMAL(19, 4) NOT NULL DEFAULT 1.0,
    
    status VARCHAR(20) NOT NULL,        -- ACTIVE, SOLD, EXPIRED, CANCELLED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at TIMESTAMP NOT NULL,
    sold_at TIMESTAMP,
    sold_to_uuid VARCHAR(36),
    sold_to_name VARCHAR(16),
    final_price DECIMAL(19, 4),
    
    view_count INT NOT NULL DEFAULT 0,
    bid_count INT NOT NULL DEFAULT 0,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_auctions_status ON auctions(status);
CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_uuid, status);
CREATE INDEX IF NOT EXISTS idx_auctions_ends_at ON auctions(ends_at, status);
CREATE INDEX IF NOT EXISTS idx_auctions_material ON auctions(item_material, status);
```

#### auction_bids
```sql
CREATE TABLE IF NOT EXISTS auction_bids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_id VARCHAR(36) NOT NULL,
    bidder_uuid VARCHAR(36) NOT NULL,
    bidder_name VARCHAR(16) NOT NULL,
    bid_amount DECIMAL(19, 4) NOT NULL,
    bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_outbid BOOLEAN NOT NULL DEFAULT FALSE,
    
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bids_auction ON auction_bids(auction_id, bid_amount DESC);
CREATE INDEX IF NOT EXISTS idx_bids_bidder ON auction_bids(bidder_uuid);
```

#### orders
```sql
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    creator_uuid VARCHAR(36) NOT NULL,
    creator_name VARCHAR(16) NOT NULL,
    order_type VARCHAR(20) NOT NULL,    -- BUY_ORDER, SELL_ORDER
    
    item_material VARCHAR(64) NOT NULL,
    item_display_name VARCHAR(255),
    item_lore_hash VARCHAR(64),
    item_nbt_hash VARCHAR(64),
    item_stack BLOB,                    -- For sell orders
    
    quantity_requested INT NOT NULL,
    quantity_filled INT NOT NULL DEFAULT 0,
    price_per_unit DECIMAL(19, 4) NOT NULL,
    total_price DECIMAL(19, 4) NOT NULL,
    
    status VARCHAR(20) NOT NULL,        -- PENDING, PARTIAL, FILLED, EXPIRED, CANCELLED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    filled_at TIMESTAMP,
    
    allow_partial BOOLEAN NOT NULL DEFAULT TRUE,
    min_fill_quantity INT
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_creator ON orders(creator_uuid, status);
CREATE INDEX IF NOT EXISTS idx_orders_item ON orders(item_material, status, order_type);
CREATE INDEX IF NOT EXISTS idx_orders_expires ON orders(expires_at, status);
```

#### order_fills
```sql
CREATE TABLE IF NOT EXISTS order_fills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    filler_uuid VARCHAR(36) NOT NULL,
    filler_name VARCHAR(16) NOT NULL,
    quantity INT NOT NULL,
    price_per_unit DECIMAL(19, 4) NOT NULL,
    total_price DECIMAL(19, 4) NOT NULL,
    filled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
```

#### expired_items
```sql
CREATE TABLE IF NOT EXISTS expired_items (
    id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    owner_name VARCHAR(16) NOT NULL,
    item_type VARCHAR(20) NOT NULL,     -- AUCTION_ITEM, ORDER_ITEM
    source_id VARCHAR(36) NOT NULL,
    item_stack BLOB NOT NULL,
    reason VARCHAR(50) NOT NULL,
    expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_expired_owner ON expired_items(owner_uuid, claimed);
```

#### transactions
```sql
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_type VARCHAR(30) NOT NULL,
    from_uuid VARCHAR(36),
    from_name VARCHAR(16),
    to_uuid VARCHAR(36),
    to_name VARCHAR(16),
    amount DECIMAL(19, 4) NOT NULL,
    tax_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    item_material VARCHAR(64),
    item_quantity INT,
    reference_id VARCHAR(36),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    server_id VARCHAR(64)
);
```

### Schema Migration (Following ZealousChat Pattern)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.sql

object AuctionHouseSchema : DatabaseSchema("auctionhouse") {
    
    override val migrations = listOf(
        migration(1, "Initial schema") {
            // auctions table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(16) NOT NULL,
                        item_stack BLOB NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_display_name TEXT,
                        auction_type VARCHAR(20) NOT NULL,
                        start_price DECIMAL(19, 4) NOT NULL,
                        buy_now_price DECIMAL(19, 4),
                        reserve_price DECIMAL(19, 4),
                        min_increment DECIMAL(19, 4) NOT NULL DEFAULT 1.0,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ends_at TIMESTAMP NOT NULL,
                        sold_at TIMESTAMP,
                        sold_to_uuid VARCHAR(36),
                        sold_to_name VARCHAR(16),
                        final_price DECIMAL(19, 4),
                        view_count INT NOT NULL DEFAULT 0,
                        bid_count INT NOT NULL DEFAULT 0,
                        is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
                        INDEX idx_status (status),
                        INDEX idx_seller (seller_uuid, status),
                        INDEX idx_ends_at (ends_at, status),
                        INDEX idx_material (item_material, status)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        id TEXT PRIMARY KEY,
                        seller_uuid TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_stack BLOB NOT NULL,
                        item_material TEXT NOT NULL,
                        item_display_name TEXT,
                        auction_type TEXT NOT NULL,
                        start_price REAL NOT NULL,
                        buy_now_price REAL,
                        reserve_price REAL,
                        min_increment REAL NOT NULL DEFAULT 1.0,
                        status TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ends_at TIMESTAMP NOT NULL,
                        sold_at TIMESTAMP,
                        sold_to_uuid TEXT,
                        sold_to_name TEXT,
                        final_price REAL,
                        view_count INTEGER NOT NULL DEFAULT 0,
                        bid_count INTEGER NOT NULL DEFAULT 0,
                        is_anonymous INTEGER NOT NULL DEFAULT 0
                    )
                """)
            })
            
            // Additional tables...
        }
    )
}
```

---

## Item Serialization (IMPORTANT - NOT Using ItemAPI)

The `ItemAPI` from utils is for **tracked items** with Persistent Data Container (PDC) instance tracking. For auction items, use Bukkit's built-in serialization:

```kotlin
object ItemSerialization {
    fun serialize(itemStack: ItemStack): ByteArray {
        return itemStack.serializeAsBytes()
    }
    
    fun deserialize(bytes: ByteArray): ItemStack {
        return ItemStack.deserializeBytes(bytes)
    }
}
```

---

## GUI Design Using MenuAPI

### Main Auction Browser (Using PaginatedMenu)
```kotlin
package bruh.zchat.auctionhouse.gui

import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class AuctionHouseMenu(
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {
    fun open(player: Player, page: Int = 0) {
        val menu = PaginatedMenu<Auction> {
            rows = 6
            title = MiniMessage.miniMessage().deserialize(
                translationAPI.getString(GuiMessages.MAIN_TITLE)
            )
            
            // Content slots for auction items
            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
            
            // Load auctions
            dataSource = runBlocking { auctionService.getActiveAuctions() }
            
            itemRenderer = { auction, index ->
                createAuctionItem(auction)
            }
            
            // Navigation items
            staticItems[45] = createPreviousPageItem()
            staticItems[53] = createNextPageItem()
            staticItems[49] = createPageIndicator()
            
            // Filter buttons
            staticItems[46] = createFilterItem()
            staticItems[47] = createSortItem()
            staticItems[48] = createSearchItem()
            
            // My auctions/orders buttons
            staticItems[50] = createMyAuctionsItem()
            staticItems[51] = createMyOrdersItem()
            staticItems[52] = createCreateAuctionItem()
        }
        
        menu.open(player)
    }
    
    private fun createAuctionItem(auction: Auction): VItem {
        return VItem(XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)) {
            name = auction.itemDisplayName?.let { 
                MiniMessage.miniMessage().deserialize(it)
            } ?: Component.text(auction.itemMaterial)
            
            lore = buildList {
                // Add lore lines based on auction type and status
                add(translationAPI.getComponentSync(GuiMessages.LORE_SELLER) {
                    unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
                })
                
                if (auction.auctionType == AuctionType.BIN || auction.auctionType == AuctionType.BOTH) {
                    add(translationAPI.getComponentSync(GuiMessages.LORE_PRICE_BIN) {
                        parsed("price", economy.format(auction.buyNowPrice!!).toString())
                    })
                }
                
                // ... more lore
            }
            
            clickHandler = { ctx, controls ->
                handleAuctionClick(ctx.player, auction)
                ClickResult.CLOSE
            }
        }
    }
}
```

---

## Command Structure (Using Lamp)

```kotlin
package bruh.zchat.auctionhouse.commands

import bruh.zchat.auctionhouse.service.AuctionService
import bruh.zchat.utils.translations.TranslationAPI
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("ah", "auctionhouse")
class AuctionHouseCommands(
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {
    @Subcommand
    suspend fun openMenu(player: Player) {
        // Open main auction browser
        AuctionHouseMenu(auctionService, translationAPI).open(player)
    }
    
    @Subcommand("sell")
    @CommandPermission("auctionhouse.sell")
    suspend fun quickSell(
        player: Player,
        price: Double,
        @Optional binPrice: Double?
    ) {
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) {
            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_INVALID_ITEM))
            return
        }
        
        // Create auction logic
    }
    
    @Subcommand("bid")
    @CommandPermission("auctionhouse.bid")
    suspend fun bid(
        player: Player,
        auctionId: String,
        amount: Double
    ) {
        // Bid logic
    }
    
    @Subcommand("buy")
    @CommandPermission("auctionhouse.buy")
    suspend fun buy(
        player: Player,
        auctionId: String
    ) {
        // BIN purchase logic
    }
    
    @Subcommand("expired")
    fun expired(player: Player) {
        ExpiredItemsMenu(auctionService, translationAPI).open(player)
    }
    
    @Subcommand("reload")
    @CommandPermission("auctionhouse.admin.reload")
    suspend fun reload(actor: BukkitCommandActor) {
        // Reload config and translations
        actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_RELOADED))
    }
}
```

---

## Economy Integration

```kotlin
package bruh.zchat.auctionhouse.economy

import net.kyori.adventure.text.Component
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

interface EconomyProvider {
    fun getBalance(player: UUID): Double
    fun hasBalance(player: UUID, amount: Double): Boolean
    fun withdraw(player: UUID, amount: Double): Boolean
    fun deposit(player: UUID, amount: Double): Boolean
    fun format(amount: Double): Component
}

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
        } else false
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
        val formatted = if (compactFormatting && amount >= 1000) {
            compactFormat(amount)
        } else {
            String.format("%,.2f", amount)
        }
        return Component.text("$currencySymbol$formatted")
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

## Key API Usage Summary

| Feature | API | Usage Pattern |
|---------|-----|---------------|
| Config Loading | `HoconConfigurationLoader` | Direct loader with `@ConfigSerializable` data classes |
| Translations | `TranslationAPI` from utils | `MessageKey` enum implementing interface, `register()` then `load()` |
| Database | `Database` from utils | `DatabaseSchema` with migrations, `registerSchema()` then `initialize()` |
| GUI Menus | `PaginatedMenu`, `SimpleMenu` from utils | DSL builder pattern with `VItem` for items |
| Item Storage | Bukkit Serialization | `ItemStack.serializeAsBytes()` / `deserializeBytes()` |
| Commands | Lamp | `@Command`, `@Subcommand` annotations with constructor injection |
| Economy | Vault API | Service provider pattern with `Economy` interface |

---

## Implementation Phases

### Phase 1: Foundation
1. Create module structure and build files
2. Create main plugin class with lifecycle management
3. Create configuration classes and loader
4. Create message enums
5. Implement Vault economy provider

### Phase 2: Database Layer
1. Create database schema with all migrations
2. Create model classes
3. Implement repositories (Auction, Order, Transaction)

### Phase 3: Service Layer
1. Implement AuctionService
2. Implement OrderService
3. Implement ExpirationService
4. Implement NotificationService

### Phase 4: GUI Layer
1. Create base menu utilities
2. Implement AuctionHouseMenu (main browser)
3. Implement AuctionCreateMenu
4. Implement OrderBrowserMenu
5. Implement OrderCreateMenu
6. Implement ExpiredItemsMenu

### Phase 5: Commands
1. Implement AuctionHouseCommands
2. Implement OrderCommands
3. Implement AuctionAdminCommands

### Phase 6: Polish
1. Add PlaceholderAPI support
2. Add metrics
3. Create documentation
4. Testing and bug fixes
