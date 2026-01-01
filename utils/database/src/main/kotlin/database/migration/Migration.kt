package bruh.zchat.utils.database.migration

import bruh.zchat.utils.database.DialectQuery

/**
 * Represents a single database migration with a version number and SQL statements.
 *
 * @property version The version number of this migration (must be positive, sequential)
 * @property description Human-readable description of what this migration does
 * @property statements The SQL statements to execute for this migration
 */
data class Migration(
    val version: Int,
    val description: String,
    val statements: List<DialectQuery>
) {
    init {
        require(version > 0) { "Migration version must be positive, got: $version" }
        require(description.isNotBlank()) { "Migration description cannot be blank" }
    }
}
