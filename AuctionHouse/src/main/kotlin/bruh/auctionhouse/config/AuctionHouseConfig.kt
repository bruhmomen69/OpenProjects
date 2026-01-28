package bruh.auctionhouse.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Main configuration for AuctionHouse plugin.
 */
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
    val compactFormatting: Boolean = true,
    @Comment("DEV ONLY: Use mock economy when Vault is not available. " +
        "Enable ONLY for development/testing.")
    val useMockEconomy: Boolean = false
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