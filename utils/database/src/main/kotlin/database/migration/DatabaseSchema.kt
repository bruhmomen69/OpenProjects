package bruh.zchat.utils.database.migration

/**
 * Base class for defining a database schema with versioned migrations.
 * 
 * Each schema has a unique name and a list of migrations that will be tracked
 * independently in the `_schema_versions` table. This allows multiple modules
 * to maintain their own schema versions in the same database.
 * 
 * Example:
 * ```kotlin
 * object MyPluginSchema : DatabaseSchema("myplugin") {
 *     override val migrations = listOf(
 *         migration(1, "Create initial tables") {
 *             execute(sql {
 *                 mysql("CREATE TABLE ...")
 *                 sqlite("CREATE TABLE ...")
 *             })
 *         },
 *         migration(2, "Add index") {
 *             execute(sql("CREATE INDEX ..."))
 *         }
 *     )
 * }
 * ```
 * 
 * @property name Unique identifier for this schema (used in _schema_versions table)
 */
abstract class DatabaseSchema(val name: String) {
    
    init {
        require(name.isNotBlank()) { "Schema name cannot be blank" }
        require(name.matches(SCHEMA_NAME_PATTERN)) { 
            "Schema name must contain only lowercase letters, numbers, and underscores: '$name'" 
        }
    }
    
    /**
     * The list of migrations for this schema, in version order.
     * Migrations must have sequential version numbers starting from 1.
     */
    abstract val migrations: List<Migration>
    
    /**
     * DSL helper for creating a migration.
     */
    protected fun migration(
        version: Int,
        description: String,
        block: MigrationBuilder.() -> Unit
    ): Migration {
        return MigrationBuilder().apply(block).build(version, description)
    }
    
    /**
     * Returns the latest version number in this schema.
     */
    fun getLatestVersion(): Int = migrations.maxOfOrNull { it.version } ?: 0
    
    /**
     * Validates that migrations are correctly ordered and sequential.
     * @throws IllegalStateException if migrations are invalid
     */
    fun validate() {
        if (migrations.isEmpty()) return
        
        val versions = migrations.map { it.version }.sorted()
        val expected = (1..versions.size).toList()
        
        require(versions == expected) {
            "Migrations for schema '$name' must be sequential starting from 1. " +
            "Expected versions: $expected, got: $versions"
        }
        
        // Check for duplicate versions
        val duplicates = migrations.groupBy { it.version }.filter { it.value.size > 1 }
        require(duplicates.isEmpty()) {
            "Duplicate migration versions in schema '$name': ${duplicates.keys}"
        }
    }
    
    /**
     * Gets migrations that need to be applied given the current version.
     */
    fun getMigrationsFrom(currentVersion: Int): List<Migration> {
        return migrations
            .filter { it.version > currentVersion }
            .sortedBy { it.version }
    }
    
    companion object {
        private val SCHEMA_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*$")
    }
}
