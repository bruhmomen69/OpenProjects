package bruh.zchat.utils.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Scope for executing queries within a transaction.
 * All operations share the same connection and participate in the transaction.
 */
class TransactionScope internal constructor(
    private val connection: Connection,
    val dialect: DatabaseDialect
) {
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
        val sqlString = sql.forDialect(dialect)
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
        val sqlString = sql.forDialect(dialect)
        connection.prepareStatement(sqlString).use { stmt ->
            setParameters(stmt, params)
            stmt.executeUpdate()
        }
    }
    
    /**
     * Executes an update/insert/delete with a simple SQL string (same for all dialects).
     */
    suspend fun execute(
        sql: String,
        vararg params: Any?
    ): Int = execute(sql(sql), *params)
    
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
        val sqlString = sql.forDialect(dialect)
        connection.prepareStatement(sqlString).use { stmt ->
            for (params in paramSets) {
                setParameters(stmt, params)
                stmt.addBatch()
            }
            stmt.executeBatch()
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
}
