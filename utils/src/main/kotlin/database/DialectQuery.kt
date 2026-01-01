package bruh.zchat.utils.database

/**
 * A dialect-aware SQL query that can provide different SQL strings for different database dialects.
 * 
 * Usage:
 * ```kotlin
 * // Same query for all dialects
 * val query = sql("SELECT * FROM players WHERE uuid = ?")
 * 
 * // Different per dialect
 * val insert = sql {
 *     mysql("INSERT IGNORE INTO players (uuid, name) VALUES (?, ?)")
 *     postgres("INSERT INTO players (uuid, name) VALUES (?, ?) ON CONFLICT DO NOTHING")
 *     sqlite("INSERT OR IGNORE INTO players (uuid, name) VALUES (?, ?)")
 * }
 * 
 * // Default with override
 * val autoIncrement = sql {
 *     default("BIGINT AUTO_INCREMENT PRIMARY KEY")
 *     sqlite("INTEGER PRIMARY KEY AUTOINCREMENT")
 * }
 * 
 * // Group dialects
 * val analyze = sql {
 *     mysqlAndPostgres("ANALYZE players")
 *     sqlite("ANALYZE")
 * }
 * ```
 */
class DialectQuery internal constructor(
    private val queries: Map<DatabaseDialect, String>,
    private val defaultQuery: String?
) {
    /**
     * Gets the SQL string for the specified dialect.
     * @param dialect The database dialect
     * @return The SQL string for that dialect
     * @throws DialectNotSupportedException if no query is defined for the dialect
     */
    fun forDialect(dialect: DatabaseDialect): String {
        return queries[dialect]
            ?: defaultQuery
            ?: throw DialectNotSupportedException(dialect)
    }
    
    /**
     * Checks if this query has a definition for the specified dialect.
     */
    fun supportsDialect(dialect: DatabaseDialect): Boolean {
        return queries.containsKey(dialect) || defaultQuery != null
    }
    
    /**
     * Returns all defined dialect-specific queries.
     */
    fun getAllQueries(): Map<DatabaseDialect, String> = queries.toMap()
    
    /**
     * Returns the default query if defined.
     */
    fun getDefault(): String? = defaultQuery
    
    companion object {
        /**
         * Creates a DialectQuery with the same SQL for all dialects.
         */
        operator fun invoke(sql: String): DialectQuery = DialectQuery(emptyMap(), sql)
        
        /**
         * Creates a DialectQuery using the builder DSL.
         */
        operator fun invoke(block: DialectQueryBuilder.() -> Unit): DialectQuery {
            return DialectQueryBuilder().apply(block).build()
        }
    }
}

/**
 * Builder for constructing dialect-aware queries.
 */
class DialectQueryBuilder {
    private val queries = mutableMapOf<DatabaseDialect, String>()
    private var defaultQuery: String? = null
    
    /**
     * Sets the SQL for MySQL dialect.
     */
    fun mysql(sql: String) {
        queries[DatabaseDialect.MYSQL] = sql.trimIndent()
    }
    
    /**
     * Sets the SQL for PostgreSQL dialect.
     */
    fun postgres(sql: String) {
        queries[DatabaseDialect.POSTGRES] = sql.trimIndent()
    }
    
    /**
     * Sets the SQL for SQLite dialect.
     */
    fun sqlite(sql: String) {
        queries[DatabaseDialect.SQLITE] = sql.trimIndent()
    }
    
    /**
     * Sets the default SQL for any dialect not explicitly defined.
     */
    fun default(sql: String) {
        defaultQuery = sql.trimIndent()
    }
    
    /**
     * Sets the same SQL for both MySQL and PostgreSQL.
     */
    fun mysqlAndPostgres(sql: String) {
        val trimmed = sql.trimIndent()
        queries[DatabaseDialect.MYSQL] = trimmed
        queries[DatabaseDialect.POSTGRES] = trimmed
    }
    
    /**
     * Sets the same SQL for both MySQL and SQLite.
     */
    fun mysqlAndSqlite(sql: String) {
        val trimmed = sql.trimIndent()
        queries[DatabaseDialect.MYSQL] = trimmed
        queries[DatabaseDialect.SQLITE] = trimmed
    }
    
    /**
     * Sets the same SQL for both PostgreSQL and SQLite.
     */
    fun postgresAndSqlite(sql: String) {
        val trimmed = sql.trimIndent()
        queries[DatabaseDialect.POSTGRES] = trimmed
        queries[DatabaseDialect.SQLITE] = trimmed
    }
    
    /**
     * Sets the SQL for all dialects explicitly.
     */
    fun all(sql: String) {
        val trimmed = sql.trimIndent()
        DatabaseDialect.entries.forEach { dialect ->
            queries[dialect] = trimmed
        }
    }
    
    internal fun build(): DialectQuery = DialectQuery(queries.toMap(), defaultQuery)
}

/**
 * Creates a dialect-aware query with the same SQL for all dialects.
 */
fun sql(sql: String): DialectQuery = DialectQuery(sql.trimIndent())

/**
 * Creates a dialect-aware query using the builder DSL.
 */
fun sql(block: DialectQueryBuilder.() -> Unit): DialectQuery = DialectQuery(block)
