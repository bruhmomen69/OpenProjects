package bruh.zchat.utils.database.migration

import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.MigrationException
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

/**
 * Internal class responsible for executing migrations against a database.
 */
internal class MigrationRunner(
    private val dialect: DatabaseDialect
) {
    private val logger = LoggerFactory.getLogger(MigrationRunner::class.java)
    
    /**
     * Ensures the schema versions table exists.
     */
    fun ensureVersionTableExists(connection: Connection) {
        val sql = when (dialect) {
            DatabaseDialect.MYSQL -> """
                CREATE TABLE IF NOT EXISTS _schema_versions (
                    schema_name VARCHAR(64) PRIMARY KEY,
                    version INT NOT NULL,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    description VARCHAR(255)
                )
            """.trimIndent()
            DatabaseDialect.POSTGRES -> """
                CREATE TABLE IF NOT EXISTS _schema_versions (
                    schema_name VARCHAR(64) PRIMARY KEY,
                    version INT NOT NULL,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    description VARCHAR(255)
                )
            """.trimIndent()
            DatabaseDialect.SQLITE -> """
                CREATE TABLE IF NOT EXISTS _schema_versions (
                    schema_name TEXT PRIMARY KEY,
                    version INTEGER NOT NULL,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    description TEXT
                )
            """.trimIndent()
        }
        
        connection.createStatement().use { stmt ->
            stmt.execute(sql)
        }
    }
    
    /**
     * Gets the current version for a schema.
     */
    fun getCurrentVersion(connection: Connection, schemaName: String): Int {
        val sql = "SELECT version FROM _schema_versions WHERE schema_name = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt("version") else 0
            }
        }
    }
    
    /**
     * Updates the version for a schema.
     */
    private fun updateVersion(connection: Connection, schemaName: String, version: Int, description: String) {
        val upsertSql = when (dialect) {
            DatabaseDialect.MYSQL -> """
                INSERT INTO _schema_versions (schema_name, version, applied_at, description)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE version = VALUES(version), applied_at = VALUES(applied_at), description = VALUES(description)
            """.trimIndent()
            DatabaseDialect.POSTGRES -> """
                INSERT INTO _schema_versions (schema_name, version, applied_at, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (schema_name) DO UPDATE SET version = EXCLUDED.version, applied_at = EXCLUDED.applied_at, description = EXCLUDED.description
            """.trimIndent()
            DatabaseDialect.SQLITE -> """
                INSERT OR REPLACE INTO _schema_versions (schema_name, version, applied_at, description)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
        }
        
        connection.prepareStatement(upsertSql).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.setInt(2, version)
            stmt.setTimestamp(3, Timestamp.from(Instant.now()))
            stmt.setString(4, description)
            stmt.executeUpdate()
        }
    }
    
    /**
     * Runs all pending migrations for a schema.
     * @return SchemaReport with details of what was applied
     */
    fun runMigrations(connection: Connection, schema: DatabaseSchema): SchemaReport {
        schema.validate()
        
        val previousVersion = getCurrentVersion(connection, schema.name)
        val pendingMigrations = schema.getMigrationsFrom(previousVersion)
        
        if (pendingMigrations.isEmpty()) {
            logger.debug("Schema '${schema.name}' is up to date at version $previousVersion")
            return SchemaReport(
                schemaName = schema.name,
                previousVersion = previousVersion,
                currentVersion = previousVersion,
                migrationsApplied = 0
            )
        }
        
        logger.info("Running ${pendingMigrations.size} migrations for schema '${schema.name}' " +
                   "(v$previousVersion -> v${schema.getLatestVersion()})")
        
        val appliedMigrations = mutableListOf<AppliedMigration>()
        var currentVersion = previousVersion
        
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            
            for (migration in pendingMigrations) {
                val startTime = System.currentTimeMillis()
                
                try {
                    logger.debug("Applying migration v${migration.version}: ${migration.description}")
                    
                    for (statement in migration.statements) {
                        val sql = statement.forDialect(dialect)
                        // Split by semicolons for multi-statement support, but be careful with strings
                        val statements = splitStatements(sql)
                        for (singleSql in statements) {
                            if (singleSql.isNotBlank()) {
                                connection.createStatement().use { stmt ->
                                    stmt.execute(singleSql.trim())
                                }
                            }
                        }
                    }
                    
                    currentVersion = migration.version
                    updateVersion(connection, schema.name, currentVersion, migration.description)
                    connection.commit()
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    appliedMigrations.add(AppliedMigration(
                        version = migration.version,
                        description = migration.description,
                        appliedAt = Instant.now(),
                        executionTimeMs = executionTime
                    ))
                    
                    logger.debug("Applied migration v${migration.version} for '${schema.name}' in ${executionTime}ms")
                    
                } catch (e: Exception) {
                    connection.rollback()
                    throw MigrationException(
                        message = "Failed to apply migration v${migration.version} (${migration.description}): ${e.message}",
                        schemaName = schema.name,
                        version = migration.version,
                        cause = e
                    )
                }
            }
            
        } finally {
            connection.autoCommit = originalAutoCommit
        }
        
        return SchemaReport(
            schemaName = schema.name,
            previousVersion = previousVersion,
            currentVersion = currentVersion,
            migrationsApplied = appliedMigrations.size,
            appliedMigrations = appliedMigrations
        )
    }
    
    /**
     * Splits SQL by semicolons, being careful about strings and comments.
     * This is a simple implementation - for complex cases, use single statements per execute().
     */
    private fun splitStatements(sql: String): List<String> {
        // Simple split - works for most cases
        // For complex SQL with semicolons in strings, use separate execute() calls
        return sql.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
