package bruh.zchat.utils.database.migration

import bruh.zchat.utils.database.DialectQuery
import bruh.zchat.utils.database.sql

/**
 * DSL builder for defining migration statements.
 */
class MigrationBuilder {
    private val statements = mutableListOf<DialectQuery>()
    
    /**
     * Adds a dialect-aware SQL statement to the migration.
     */
    fun execute(sql: DialectQuery) {
        statements.add(sql)
    }
    
    /**
     * Adds a simple SQL statement (same for all dialects) to the migration.
     */
    fun execute(sql: String) {
        statements.add(sql(sql))
    }
    
    /**
     * Adds multiple SQL statements that are the same for all dialects.
     */
    fun executeAll(vararg sqls: String) {
        sqls.forEach { execute(it) }
    }
    
    /**
     * Adds multiple dialect-aware SQL statements.
     */
    fun executeAll(vararg sqls: DialectQuery) {
        sqls.forEach { execute(it) }
    }
    
    internal fun build(version: Int, description: String): Migration {
        require(statements.isNotEmpty()) { "Migration version $version has no statements" }
        return Migration(version, description, statements.toList())
    }
}
