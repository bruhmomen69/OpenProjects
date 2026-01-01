package bruh.zchat.utils.database.migration

import java.time.Instant

/**
 * Report of a single schema's migration execution.
 */
data class SchemaReport(
    val schemaName: String,
    val previousVersion: Int,
    val currentVersion: Int,
    val migrationsApplied: Int,
    val appliedMigrations: List<AppliedMigration> = emptyList()
) {
    val wasUpdated: Boolean get() = migrationsApplied > 0
}

/**
 * Details of a single applied migration.
 */
data class AppliedMigration(
    val version: Int,
    val description: String,
    val appliedAt: Instant,
    val executionTimeMs: Long
)

/**
 * Complete report of all migration executions.
 */
data class MigrationReport(
    val schemasUpdated: Int,
    val totalApplied: Int,
    val details: Map<String, SchemaReport>,
    val executionTimeMs: Long
) {
    /**
     * Returns true if any migrations were applied.
     */
    val hasChanges: Boolean get() = totalApplied > 0
    
    /**
     * Gets the report for a specific schema.
     */
    fun forSchema(name: String): SchemaReport? = details[name]
    
    /**
     * Returns a summary string suitable for logging.
     */
    fun toSummary(): String = buildString {
        append("Migration completed in ${executionTimeMs}ms: ")
        append("$totalApplied migrations applied across $schemasUpdated schemas")
        if (details.isNotEmpty()) {
            append(" [")
            append(details.values.filter { it.wasUpdated }.joinToString(", ") { 
                "${it.schemaName}: v${it.previousVersion} -> v${it.currentVersion}" 
            })
            append("]")
        }
    }
}
