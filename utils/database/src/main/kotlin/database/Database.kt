package bruh.zchat.utils.database

import bruh.zchat.utils.configapi.TypedConfigLoader
import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.migration.MigrationReport
import bruh.zchat.utils.database.migration.MigrationRunner
import bruh.zchat.utils.database.migration.SchemaReport
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Main database class providing connection pooling, query execution, and migration support.
 * 
 * Usage:
 * ```kotlin
 * val database = plugin.createDatabase(config)
 *     .registerSchema(MySchema)
 * 
 * val report = database.initialize()
 * 
 * // Use database
 * val players = database.query(sql("SELECT * FROM players")) { rs -> ... }
 * 
 * // On disable
 * database.close()
 * ```
 */
class Database private constructor(
    val config: DatabaseConfig,
    private val dataFolderPath: String
) : Closeable {
    
    private val logger = LoggerFactory.getLogger(Database::class.java)
    
    val dialect: DatabaseDialect = config.resolveDialect()
    
    private lateinit var dataSource: HikariDataSource
    private val schemas = mutableMapOf<String, DatabaseSchema>()
    private var initialized = false
    
    private val migrationRunner = MigrationRunner(dialect)
    
    /**
     * Registers a schema for migration tracking.
     * Must be called before [initialize].
     * @param schema The schema to register
     * @return This database instance for chaining
     */
    fun registerSchema(schema: DatabaseSchema): Database {
        require(!initialized) { "Cannot register schemas after database is initialized" }
        require(!schemas.containsKey(schema.name)) { 
            "Schema '${schema.name}' is already registered" 
        }
        schemas[schema.name] = schema
        logger.debug("Registered schema '${schema.name}' with ${schema.migrations.size} migrations")
        return this
    }
    
    /**
     * Registers multiple schemas for migration tracking.
     * @param schemas The schemas to register
     * @return This database instance for chaining
     */
    fun registerSchemas(vararg schemas: DatabaseSchema): Database {
        schemas.forEach { registerSchema(it) }
        return this
    }
    
    /**
     * Initializes the database:
     * 1. Creates the HikariCP connection pool
     * 2. Creates the schema versions table if needed
     * 3. Runs pending migrations for all registered schemas
     * 
     * Must be called after registering schemas and before any queries.
     * @return Migration report with details of what was applied
     */
    suspend fun initialize(): MigrationReport = withContext(Dispatchers.IO) {
        require(!initialized) { "Database is already initialized" }
        
        val startTime = System.currentTimeMillis()
        
        // Create HikariCP pool
        dataSource = createDataSource()
        logger.info("Database connection pool created (${config.poolName}, dialect: $dialect)")
        
        // Run migrations
        val schemaReports = mutableMapOf<String, SchemaReport>()
        var totalApplied = 0
        var schemasUpdated = 0
        
        dataSource.connection.use { connection ->
            migrationRunner.ensureVersionTableExists(connection)
            
            for ((name, schema) in schemas) {
                val report = migrationRunner.runMigrations(connection, schema)
                schemaReports[name] = report
                totalApplied += report.migrationsApplied
                if (report.wasUpdated) schemasUpdated++
            }
        }
        
        initialized = true
        
        val executionTime = System.currentTimeMillis() - startTime
        MigrationReport(
            schemasUpdated = schemasUpdated,
            totalApplied = totalApplied,
            details = schemaReports,
            executionTimeMs = executionTime
        ).also { report ->
            if (report.hasChanges) {
                logger.info(report.toSummary())
            } else {
                logger.debug("All schemas up to date (checked in ${executionTime}ms)")
            }
        }
    }
    
    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            poolName = config.poolName
            jdbcUrl = config.buildJdbcUrl(dataFolderPath)
            
            if (dialect != DatabaseDialect.SQLITE) {
                username = config.username
                password = config.password
                driverClassName = config.getDriverClassName()
            } else {
                driverClassName = config.getDriverClassName()
            }
            
            maximumPoolSize = config.poolSize
            connectionTimeout = config.connectionTimeout
            maxLifetime = config.maxLifetime
            idleTimeout = config.idleTimeout
            
            if (config.leakDetectionThreshold > 0) {
                leakDetectionThreshold = config.leakDetectionThreshold
            }
            
            // SQLite-specific settings
            if (dialect == DatabaseDialect.SQLITE) {
                // SQLite doesn't support multiple connections well in WAL mode
                // Keep pool size reasonable
                maximumPoolSize = minOf(config.poolSize, 4)
                // Enable foreign keys for SQLite
                addDataSourceProperty("foreign_keys", "true")
            }
        }
        
        return HikariDataSource(hikariConfig)
    }
    
    private fun ensureInitialized() {
        require(initialized) { "Database not initialized. Call initialize() first." }
    }
    
    // ==================== Query Operations ====================
    
    /**
     * Executes a query and maps the results.
     * @param sql The dialect-aware SQL query
     * @param params Query parameters
     * @param mapper Function to map each ResultSet row to a result object
     * @return List of mapped results
     */
    suspend fun <T> query(
        sql: DialectQuery,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val sqlString = sql.forDialect(dialect)
        
        dataSource.connection.use { connection ->
            connection.prepareStatement(sqlString).use { stmt ->
                setParameters(stmt, params)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) {
                        results.add(mapper(rs))
                    }
                    results
                }
            }
        }
    }
    
    /**
     * Executes a query expecting a single result.
     * @param sql The dialect-aware SQL query
     * @param params Query parameters
     * @param mapper Function to map the ResultSet row to a result object
     * @return The mapped result, or null if no rows
     * @throws DatabaseQueryException if more than one row is returned
     */
    suspend fun <T> querySingle(
        sql: DialectQuery,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): T? = withContext(Dispatchers.IO) {
        val results = query(sql, *params, mapper = mapper)
        when (results.size) {
            0 -> null
            1 -> results.first()
            else -> throw DatabaseQueryException(
                "Expected single result, got ${results.size}",
                sql.forDialect(dialect)
            )
        }
    }
    
    /**
     * Executes an update/insert/delete statement.
     * @param sql The dialect-aware SQL query
     * @param params Query parameters
     * @return Number of rows affected
     */
    suspend fun execute(
        sql: DialectQuery,
        vararg params: Any?
    ): Int = withContext(Dispatchers.IO) {
        ensureInitialized()
        val sqlString = sql.forDialect(dialect)
        
        dataSource.connection.use { connection ->
            connection.prepareStatement(sqlString).use { stmt ->
                setParameters(stmt, params)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Executes an update/insert/delete with a simple SQL string.
     */
    suspend fun execute(
        sql: String,
        vararg params: Any?
    ): Int = execute(sql(sql), *params)

    /**
     * Executes an INSERT statement and returns the generated key.
     * Works cross-platform by using JDBC's RETURN_GENERATED_KEYS.
     * @param sql The dialect-aware SQL query
     * @param params Query parameters
     * @return The generated key, or null if no key was generated
     */
    suspend fun executeInsert(
        sql: DialectQuery,
        vararg params: Any?
    ): Long? = withContext(Dispatchers.IO) {
        ensureInitialized()
        val sqlString = sql.forDialect(dialect)

        dataSource.connection.use { connection ->
            connection.prepareStatement(sqlString, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                setParameters(stmt, params)
                stmt.executeUpdate()
                val generatedKeys = stmt.generatedKeys
                if (generatedKeys.next()) {
                    generatedKeys.getLong(1)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Executes an INSERT statement and returns the generated key.
     * @param sql The SQL query string
     * @param params Query parameters
     * @return The generated key, or null if no key was generated
     */
    suspend fun executeInsert(
        sql: String,
        vararg params: Any?
    ): Long? = executeInsert(sql(sql), *params)
    
    /**
     * Executes a batch of statements with multiple parameter sets.
     * @param sql The dialect-aware SQL query
     * @param paramSets List of parameter arrays, one per batch item
     * @return Array of update counts
     */
    suspend fun executeBatch(
        sql: DialectQuery,
        paramSets: List<Array<out Any?>>
    ): IntArray = withContext(Dispatchers.IO) {
        ensureInitialized()
        val sqlString = sql.forDialect(dialect)
        
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false
                connection.prepareStatement(sqlString).use { stmt ->
                    for (params in paramSets) {
                        setParameters(stmt, params)
                        stmt.addBatch()
                    }
                    val result = stmt.executeBatch()
                    connection.commit()
                    result
                }
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }
    
    /**
     * Executes operations within a transaction.
     * All operations in the block share the same connection and will be committed
     * together, or rolled back if an exception occurs.
     * @param block The operations to execute
     * @return The result of the block
     */
    suspend fun <T> transaction(
        block: suspend TransactionScope.() -> T
    ): T = withContext(Dispatchers.IO) {
        ensureInitialized()
        
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false
                val scope = TransactionScope(connection, dialect)
                val result = block(scope)
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Gets the current schema version for a registered schema.
     * @param schemaName The schema name
     * @return The current version, or 0 if not yet migrated
     */
    suspend fun getSchemaVersion(schemaName: String): Int = withContext(Dispatchers.IO) {
        ensureInitialized()
        dataSource.connection.use { connection ->
            migrationRunner.getCurrentVersion(connection, schemaName)
        }
    }
    
    /**
     * Gets a raw connection from the pool.
     * The caller is responsible for closing the connection.
     * Prefer using [query], [execute], or [transaction] instead.
     */
    fun getConnection(): Connection {
        ensureInitialized()
        return dataSource.connection
    }
    
    /**
     * Checks if the database is initialized.
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Closes the database connection pool.
     */
    override fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            logger.info("Database connection pool closed (${config.poolName})")
        }
    }
    
    private fun setParameters(stmt: PreparedStatement, params: Array<out Any?>) {
        params.forEachIndexed { index, param ->
            val paramIndex = index + 1
            when (param) {
                null -> stmt.setNull(paramIndex, java.sql.Types.NULL)
                is String -> stmt.setString(paramIndex, param)
                is Int -> stmt.setInt(paramIndex, param)
                is Long -> stmt.setLong(paramIndex, param)
                is Double -> stmt.setDouble(paramIndex, param)
                is Float -> stmt.setFloat(paramIndex, param)
                is Boolean -> stmt.setBoolean(paramIndex, param)
                is UUID -> stmt.setString(paramIndex, param.toString())
                is Instant -> stmt.setTimestamp(paramIndex, Timestamp.from(param))
                is java.util.Date -> stmt.setTimestamp(paramIndex, Timestamp(param.time))
                is ByteArray -> stmt.setBytes(paramIndex, param)
                else -> stmt.setObject(paramIndex, param)
            }
        }
    }
    
    companion object {
        /**
         * Creates a database instance with the given configuration.
         * @param config The database configuration
         * @param dataFolderPath The plugin's data folder path (for SQLite relative paths)
         * @return A new Database instance (not yet initialized)
         */
        fun create(config: DatabaseConfig, dataFolderPath: Path): Database {
            return Database(config, dataFolderPath.toString())
        }
        
        /**
         * Creates a database instance with the given configuration.
         * @param config The database configuration
         * @param dataFolderPath The plugin's data folder path as a string
         * @return A new Database instance (not yet initialized)
         */
        fun create(config: DatabaseConfig, dataFolderPath: String): Database {
            return Database(config, dataFolderPath)
        }
    }
}

// ==================== JavaPlugin Extension Functions ====================

/**
 * DSL builder for configuring a database instance.
 */
class DatabaseBuilder internal constructor() {
    private val schemas = mutableListOf<DatabaseSchema>()
    
    /**
     * Registers a schema for migration tracking.
     */
    fun schema(schema: DatabaseSchema) {
        schemas.add(schema)
    }
    
    /**
     * Registers multiple schemas for migration tracking.
     */
    fun schemas(vararg schemasToAdd: DatabaseSchema) {
        schemas.addAll(schemasToAdd)
    }
    
    internal fun applyTo(database: Database): Database {
        schemas.forEach { database.registerSchema(it) }
        return database
    }
}

/**
 * Creates a new database instance for this plugin using DSL syntax.
 * 
 * **Recommended usage:**
 * ```kotlin
 * val database = createDatabase(config) {
 *     schema(MySchema)
 *     schema(OtherSchema)
 * }
 * 
 * val report = database.initialize()
 * ```
 * 
 * @param config The database configuration (optional - loads from database.conf if null)
 * @param block DSL block for configuring schemas
 * @return A new Database instance (not yet initialized)
 */
suspend fun JavaPlugin.createDatabase(
    config: DatabaseConfig? = null,
    block: DatabaseBuilder.() -> Unit = {}
): Database {
    val resolvedConfig = config ?: loadDefaultDatabaseConfig()
    val database = Database.create(resolvedConfig, dataFolder.toPath())
    return DatabaseBuilder().apply(block).applyTo(database)
}

/**
 * Creates a new database instance with explicit configuration (no DSL).
 * 
 * ```kotlin
 * val database = createDatabase(config)
 *     .registerSchema(MySchema)
 * ```
 * 
 * @param config The database configuration
 * @return A new Database instance (not yet initialized)
 */
fun JavaPlugin.createDatabaseSync(config: DatabaseConfig): Database {
    return Database.create(config, dataFolder.toPath())
}

private suspend fun JavaPlugin.loadDefaultDatabaseConfig(): DatabaseConfig {
    val configPath = dataFolder.toPath().resolve("database.conf")
    val loader = TypedConfigLoader.create(
        configPath = configPath,
        defaultFactory = { DatabaseConfig() }
    )
    return loader.load()
}

/**
 * Creates a new database instance with configuration loaded from a file.
 * 
 * @param configPath Path to the configuration file (defaults to database.conf in data folder)
 * @param defaultConfig Default configuration if file doesn't exist
 * @param transform Optional transform to apply to loaded config
 * @param block DSL block for configuring schemas
 * @return A new Database instance (not yet initialized)
 */
suspend fun JavaPlugin.createDatabaseWithConfig(
    configPath: Path = dataFolder.toPath().resolve("database.conf"),
    defaultConfig: DatabaseConfig = DatabaseConfig(),
    transform: (DatabaseConfig) -> DatabaseConfig = { it },
    block: DatabaseBuilder.() -> Unit = {}
): Database {
    val loader = TypedConfigLoader.create(
        configPath = configPath,
        defaultFactory = { defaultConfig },
        transform = transform
    )
    val config = loader.load()
    val database = Database.create(config, dataFolder.toPath())
    return DatabaseBuilder().apply(block).applyTo(database)
}
