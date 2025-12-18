package bruh.zchat.paper.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class StorageConfig(
    @field:Comment("Database configuration for player data storage")
    val database: DatabaseConfig = DatabaseConfig(),

    @field:Comment("Cross-server messaging configuration (requires MySQL)")
    val crossServerMessaging: CrossServerMessagingConfig = CrossServerMessagingConfig()
)

@ConfigSerializable
data class DatabaseConfig(
    @field:Comment("Database type: 'sqlite' or 'mysql'")
    val type: String = "sqlite",
    
    @field:Comment("MySQL connection settings (ignored for SQLite)")
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "chatplugin",
    val username: String = "",
    val password: String = "",
    
    @field:Comment("SQLite database file name")
    val sqliteFile: String = "database.db",
    
    @field:Comment("Connection pool size (default: 8)")
    val poolSize: Int = 8,
    
    @field:Comment("Connection timeout in milliseconds (default: 30000)")
    val connectionTimeout: Long = 30000,
    
    @field:Comment("Maximum connection lifetime in milliseconds (default: 1800000)")
    val maxLifetime: Long = 1800000,
    
    @field:Comment("Connection leak detection threshold in milliseconds (default: 30000)")
    val leakDetectionThreshold: Long = 30000,
    
    @field:Comment("Automatically migrate existing block data")
    val autoMigrate: Boolean = true,
    
    @field:Comment("Enable data archiving before deletion")
    val enableArchive: Boolean = true,
    
    @field:Comment("Data retention period in days (default: 30)")
    val dataRetentionDays: Int = 30,
    
    @field:Comment("Daily maintenance time (24-hour format, default: 02:00)")
    val maintenanceTime: String = "02:00",
    
    @field:Comment("Enable performance monitoring and logging")
    val enablePerformanceMonitoring: Boolean = false
)

@ConfigSerializable
data class CrossServerMessagingConfig(
    @field:Comment("Enable cross-server private messaging (requires MySQL database).")
    val enabled: Boolean = true,
    
    @field:Comment("Interval in milliseconds to poll for new messages.")
    val pollIntervalMillis: Long = 250,
    
    @field:Comment("Interval in seconds to update player presence heartbeat.")
    val heartbeatIntervalSeconds: Int = 10,
    
    @field:Comment("Time in seconds after which a player's presence is considered stale/offline.")
    val heartbeatTimeoutSeconds: Int = 25,
    
    @field:Comment("Time in seconds after which a claimed message is considered stuck and can be reclaimed.")
    val claimTimeoutSeconds: Int = 60,
    
    @field:Comment("Number of messages to claim in a single poll batch.")
    val pollBatchSize: Int = 50,
    
    @field:Comment("Data retention for message bus history in days.")
    val messageRetentionDays: Int = 7
)
