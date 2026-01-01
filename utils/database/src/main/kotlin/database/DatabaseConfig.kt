package bruh.zchat.utils.database

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration for database connections.
 * Can be used with Configurate for file-based config or constructed programmatically.
 */
@ConfigSerializable
data class DatabaseConfig(
    @field:Comment("Database type: 'sqlite', 'mysql', or 'postgres'")
    val dialect: String = "sqlite",
    
    @field:Comment("MySQL/Postgres host address")
    val host: String = "localhost",
    
    @field:Comment("MySQL/Postgres port")
    val port: Int = 3306,
    
    @field:Comment("Database name (MySQL/Postgres)")
    val database: String = "plugin_data",
    
    @field:Comment("Database username (MySQL/Postgres)")
    val username: String = "",
    
    @field:Comment("Database password (MySQL/Postgres)")
    val password: String = "",
    
    @field:Comment("SQLite database file name (relative to plugin data folder)")
    val sqliteFile: String = "database.db",
    
    @field:Comment("Connection pool name (for logging/monitoring)")
    val poolName: String = "DBPool",
    
    @field:Comment("Maximum number of connections in the pool")
    val poolSize: Int = 8,
    
    @field:Comment("Connection timeout in milliseconds")
    val connectionTimeout: Long = 30_000,
    
    @field:Comment("Maximum connection lifetime in milliseconds")
    val maxLifetime: Long = 1_800_000,
    
    @field:Comment("Idle connection timeout in milliseconds")
    val idleTimeout: Long = 600_000,
    
    @field:Comment("Connection leak detection threshold in milliseconds (0 to disable)")
    val leakDetectionThreshold: Long = 30_000,
    
    @field:Comment("Additional JDBC connection options")
    val jdbcOptions: Map<String, String> = emptyMap()
) {
    /**
     * Resolves the database dialect enum from the string configuration.
     */
    fun resolveDialect(): DatabaseDialect = DatabaseDialect.fromString(dialect)
    
    /**
     * Builds the JDBC URL for the configured database.
     * @param dataFolderPath The plugin's data folder path (for SQLite relative paths)
     */
    fun buildJdbcUrl(dataFolderPath: String): String {
        return when (resolveDialect()) {
            DatabaseDialect.SQLITE -> {
                val path = if (sqliteFile.startsWith("/")) {
                    sqliteFile
                } else {
                    "$dataFolderPath/$sqliteFile"
                }
                "jdbc:sqlite:$path"
            }
            DatabaseDialect.MYSQL -> {
                val options = buildString {
                    append("useSSL=false")
                    append("&allowPublicKeyRetrieval=true")
                    append("&serverTimezone=UTC")
                    jdbcOptions.forEach { (key, value) ->
                        append("&$key=$value")
                    }
                }
                "jdbc:mysql://$host:$port/$database?$options"
            }
            DatabaseDialect.POSTGRES -> {
                val options = jdbcOptions.entries.joinToString("&") { "${it.key}=${it.value}" }
                val url = "jdbc:postgresql://$host:$port/$database"
                if (options.isNotEmpty()) "$url?$options" else url
            }
        }
    }
    
    /**
     * Gets the JDBC driver class name for the configured database.
     */
    fun getDriverClassName(): String {
        return when (resolveDialect()) {
            DatabaseDialect.SQLITE -> "org.sqlite.JDBC"
            DatabaseDialect.MYSQL -> "com.mysql.cj.jdbc.Driver"
            DatabaseDialect.POSTGRES -> "org.postgresql.Driver"
        }
    }
}
