package bruh.zchat.paper.database

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.*
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class DatabaseType {
    MYSQL, SQLITE
}

data class DatabaseConfig(
    val type: DatabaseType = DatabaseType.SQLITE,
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "chatplugin",
    val username: String = "",
    val password: String = "",
    val sqliteFile: String = "database.db",
    val poolSize: Int = 8,
    val connectionTimeout: Long = 30000,
    val maxLifetime: Long = 1800000,
    val leakDetectionThreshold: Long = 30000,
    val autoMigrate: Boolean = true,
    val enableArchive: Boolean = true,
    val dataRetentionDays: Int = 30
)

class DatabaseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DatabaseService(private val config: DatabaseConfig, private val plugin: JavaPlugin) {
    private val logger = LoggerFactory.getLogger(DatabaseService::class.java)
    private val dataSource: HikariDataSource
    private val executor: ExecutorService
    private var flyway: Flyway? = null

    val databaseType: DatabaseType
        get() = config.type

    init {
        dataSource = createDataSource()
        executor = createExecutor()
        setupFlyway()
    }

    private fun createDataSource(): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "ZealousChat-Pool"

            when (config.type) {
                DatabaseType.MYSQL -> {
                    jdbcUrl =
                        "jdbc:mysql://${config.host}:${config.port}/${config.database}?useSSL=false&allowPublicKeyRetrieval=true"
                    username = config.username
                    password = config.password
                    driverClassName = "com.mysql.cj.jdbc.Driver"
                }

                DatabaseType.SQLITE -> {
                    jdbcUrl = "jdbc:sqlite:${plugin.dataFolder.absolutePath + File.separator + config.sqliteFile}"
                    driverClassName = "org.sqlite.JDBC"
                }
            }

            maximumPoolSize = config.poolSize
            connectionTimeout = config.connectionTimeout
            maxLifetime = config.maxLifetime
            idleTimeout = 600000 // 10 minutes
            leakDetectionThreshold = config.leakDetectionThreshold
        }
    }

    private fun createExecutor(): ExecutorService {
        return Executors.newFixedThreadPool(config.poolSize) {
            Thread(it, "ZealousChat-DB-Worker")
        }
    }

    private inline fun <T> withPluginClassLoader(action: () -> T): T {
        val thread = Thread.currentThread()
        val original = thread.contextClassLoader
        thread.contextClassLoader = plugin.javaClass.classLoader
        return try {
            action()
        } finally {
            thread.contextClassLoader = original
        }
    }

    private fun setupFlyway() {
        flyway = withPluginClassLoader {
            val flywayConfig: FluentConfiguration = Flyway.configure()
                .dataSource(dataSource)

            when (config.type) {
                DatabaseType.MYSQL -> {
                    flywayConfig
                        .locations("classpath:db/migration/mysql")
                        .load()
                }

                DatabaseType.SQLITE -> {
                    flywayConfig
                        .locations("classpath:db/migration/sqlite")
                        .load()
                }
            }
        }
    }

    suspend fun migrate(): MigrateResult {
        return withContext(Dispatchers.IO) {
            flyway?.migrate() ?: throw DatabaseException("Flyway not initialized")
        }
    }

    suspend fun executeUpdate(
        sql: String,
        vararg params: Any
    ): Int = withContext(Dispatchers.IO) {
        safeExecute("executeUpdate") {
            var connection: Connection? = null
            var statement: PreparedStatement? = null

            try {
                connection = dataSource.connection
                statement = connection.prepareStatement(sql)
                setParameters(statement, *params)
                statement.executeUpdate()
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    private fun setParameters(statement: PreparedStatement, vararg params: Any) {
        params.forEachIndexed { index, param ->
            when (param) {
                is String -> statement.setString(index + 1, param)
                is Int -> statement.setInt(index + 1, param)
                is Long -> statement.setLong(index + 1, param)
                is Double -> statement.setDouble(index + 1, param)
                is Boolean -> statement.setBoolean(index + 1, param)
                is UUID -> statement.setString(index + 1, param.toString())
                is java.util.Date -> statement.setTimestamp(index + 1, Timestamp(param.time))
                is Instant -> statement.setTimestamp(index + 1, Timestamp.from(param))
                else -> statement.setObject(index + 1, param)
            }
        }
    }

    suspend fun executeBatch(
        sql: String,
        paramSets: List<Array<Any>>
    ): IntArray = withContext(Dispatchers.IO) {
        safeExecute("executeBatch") {
            var connection: Connection? = null
            var statement: PreparedStatement? = null

            try {
                connection = dataSource.connection
                connection.autoCommit = false
                statement = connection.prepareStatement(sql)

                paramSets.forEach { params ->
                    setParameters(statement, *params)
                    statement.addBatch()
                }

                val result = statement.executeBatch()
                connection.commit()
                result
            } catch (e: Exception) {
                connection?.rollback()
                throw e
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    suspend fun <T> executeQuery(
        sql: String,
        vararg params: Any,
        mapper: (ResultSet) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        safeExecute("executeQuery") {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null

            try {
                connection = dataSource.connection
                statement = connection.prepareStatement(sql)
                setParameters(statement, *params)
                resultSet = statement.executeQuery()

                val results = mutableListOf<T>()
                while (resultSet.next()) {
                    results.add(mapper(resultSet))
                }
                results
            } finally {
                resultSet?.close()
                statement?.close()
                connection?.close()
            }
        }
    }

    suspend fun <T> executeQuerySingle(
        sql: String,
        vararg params: Any,
        mapper: (ResultSet) -> T
    ): T? = withContext(Dispatchers.IO) {
        val results = executeQuery(sql, *params, mapper = mapper)
        when (results.size) {
            0 -> null
            1 -> results.first()
            else -> throw DatabaseException("Expected single result, got ${results.size}")
        }
    }

    suspend fun <T> executeTransaction(
        operations: suspend (TransactionContext) -> T
    ): T = withContext(Dispatchers.IO) {
        safeExecute("executeTransaction") {
            var connection: Connection? = null

            try {
                connection = dataSource.connection
                connection.autoCommit = false

                val context = TransactionContext(connection)
                val result = operations(context)

                connection.commit()
                result
            } catch (e: Exception) {
                connection?.rollback()
                throw e
            } finally {
                connection?.close()
            }
        }
    }

    class TransactionContext(private val connection: Connection) {
        private fun setParameters(statement: PreparedStatement, vararg params: Any) {
            params.forEachIndexed { index, param ->
                when (param) {
                    is String -> statement.setString(index + 1, param)
                    is Int -> statement.setInt(index + 1, param)
                    is Long -> statement.setLong(index + 1, param)
                    is Double -> statement.setDouble(index + 1, param)
                    is Boolean -> statement.setBoolean(index + 1, param)
                    is UUID -> statement.setString(index + 1, param.toString())
                    is java.util.Date -> statement.setTimestamp(index + 1, Timestamp(param.time))
                    is Instant -> statement.setTimestamp(index + 1, Timestamp.from(param))
                    else -> statement.setObject(index + 1, param)
                }
            }
        }

        suspend fun executeUpdate(sql: String, vararg params: Any): Int = withContext(Dispatchers.IO) {
            var statement: PreparedStatement? = null
            return@withContext try {
                statement = connection.prepareStatement(sql)
                setParameters(statement, *params)
                statement.executeUpdate()
            } finally {
                statement?.close()
            }
        }

        suspend fun <T> executeQuery(
            sql: String,
            vararg params: Any,
            mapper: (ResultSet) -> T
        ): List<T> = withContext(Dispatchers.IO) {
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null
            return@withContext try {
                statement = connection.prepareStatement(sql)
                setParameters(statement, *params)
                resultSet = statement.executeQuery()
                val results = mutableListOf<T>()
                while (resultSet.next()) {
                    results.add(mapper(resultSet))
                }
                results
            } finally {
                resultSet?.close()
                statement?.close()
            }
        }

        suspend fun <T> executeQuerySingle(
            sql: String,
            vararg params: Any,
            mapper: (ResultSet) -> T
        ): T? = withContext(Dispatchers.IO) {
            val results = executeQuery(sql, *params, mapper = mapper)
            when (results.size) {
                0 -> null
                1 -> results.first()
                else -> throw DatabaseException("Expected single result, got ${results.size}")
            }
        }
    }


    private inline fun <T> safeExecute(
        operation: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: SQLException) {
            logger.error("Database operation failed: $operation", e)
            throw DatabaseException("Database operation failed: $operation", e)
        } catch (e: Exception) {
            logger.error("Unexpected error in database operation: $operation", e)
            throw DatabaseException("Unexpected error in database operation: $operation", e)
        }
    }

    fun close() {
        try {
            dataSource.close()
            executor.shutdown()
        } catch (e: Exception) {
            logger.error("Error closing database service", e)
        }
    }
}