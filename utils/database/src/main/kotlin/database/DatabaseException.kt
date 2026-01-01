package bruh.zchat.utils.database

/**
 * Base exception for database operations.
 */
open class DatabaseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when a database connection fails.
 */
class DatabaseConnectionException(
    message: String,
    cause: Throwable? = null
) : DatabaseException(message, cause)

/**
 * Exception thrown when a query fails.
 */
class DatabaseQueryException(
    message: String,
    val sql: String? = null,
    cause: Throwable? = null
) : DatabaseException(message, cause)

/**
 * Exception thrown when a migration fails.
 */
class MigrationException(
    message: String,
    val schemaName: String? = null,
    val version: Int? = null,
    cause: Throwable? = null
) : DatabaseException(message, cause)

/**
 * Exception thrown when a dialect-specific query is not defined.
 */
class DialectNotSupportedException(
    val dialect: DatabaseDialect,
    message: String = "No query defined for dialect: $dialect"
) : DatabaseException(message)
