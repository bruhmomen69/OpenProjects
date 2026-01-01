package bruh.zchat.utils.database

/**
 * Supported database dialects.
 */
enum class DatabaseDialect {
    SQLITE,
    MYSQL,
    POSTGRES;
    
    companion object {
        /**
         * Parses a dialect string to the corresponding enum value.
         * @param value The dialect string (case-insensitive)
         * @return The corresponding DatabaseDialect
         * @throws IllegalArgumentException if the dialect is not recognized
         */
        fun fromString(value: String): DatabaseDialect {
            return when (value.lowercase().trim()) {
                "sqlite" -> SQLITE
                "mysql", "mariadb" -> MYSQL
                "postgres", "postgresql" -> POSTGRES
                else -> throw IllegalArgumentException(
                    "Unknown database dialect: '$value'. Supported: sqlite, mysql, postgres"
                )
            }
        }
    }
}
