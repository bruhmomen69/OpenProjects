package bruh.auctionhouse.service

import bruh.auctionhouse.util.toBytes
import bruh.auctionhouse.util.toStoredUuidOrNull
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Normalizes expired-item UUID storage to compact binary columns.
 *
 * Older schema revisions used a mix of text and decimal UUID encodings across
 * `expired_items` and `consolidated_expired_items`, while the repositories already
 * expected compact UUID storage. This migration rewrites both tables into the
 * binary layout used by the current repositories and fresh-install schema.
 */
class ExpiredItemsUuidStorageMigration(
    private val database: Database,
    private val logger: Logger
) {
    private val migrationFlagKey = "expired_items_uuid_binary_storage_v1"

    suspend fun migrateIfNeeded() = withContext(Dispatchers.IO) {
        if (isMigrationCompleted()) {
            return@withContext
        }

        val needsExpiredItemsMigration = database.getConnection().use { connection ->
            tableNeedsBinaryUuidMigration(
                connection,
                "expired_items",
                listOf("id", "owner_uuid", "source_id", "consolidated_group_id")
            )
        }
        val needsConsolidatedMigration = database.getConnection().use { connection ->
            tableNeedsBinaryUuidMigration(
                connection,
                "consolidated_expired_items",
                listOf("id", "owner_uuid", "source_id")
            )
        }

        if (!needsExpiredItemsMigration && !needsConsolidatedMigration) {
            markMigrationCompleted()
            return@withContext
        }

        database.getConnection().use { connection ->
            val originalAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false

                if (needsExpiredItemsMigration) {
                    migrateExpiredItemsTable(connection)
                }
                if (needsConsolidatedMigration) {
                    migrateConsolidatedExpiredItemsTable(connection)
                }

                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }

        markMigrationCompleted()
        logger.info(
            "Expired-item UUID storage migration completed (expired_items: {}, consolidated_expired_items: {})",
            needsExpiredItemsMigration,
            needsConsolidatedMigration
        )
    }

    private suspend fun isMigrationCompleted(): Boolean = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT value FROM plugin_metadata WHERE key = ?"),
            migrationFlagKey
        ) { rs ->
            rs.getBoolean("value")
        } ?: false
    }

    private suspend fun markMigrationCompleted() = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("""
                    INSERT INTO plugin_metadata (key, value) VALUES (?, TRUE)
                    ON DUPLICATE KEY UPDATE value = TRUE
                """)
                postgres("""
                    INSERT INTO plugin_metadata (key, value) VALUES (?, 'TRUE')
                    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """)
                sqlite("""
                    INSERT OR REPLACE INTO plugin_metadata (key, value) VALUES (?, TRUE)
                """)
            },
            migrationFlagKey
        )
    }

    private fun tableNeedsBinaryUuidMigration(
        connection: Connection,
        tableName: String,
        uuidColumns: List<String>
    ): Boolean {
        connection.prepareStatement("SELECT * FROM $tableName LIMIT 1").use { statement ->
            statement.executeQuery().use { resultSet ->
                val metadata = resultSet.metaData
                for (column in uuidColumns) {
                    val typeName = findColumnTypeName(metadata = metadata, column = column)
                        ?: throw IllegalStateException("Missing expected column '$column' on table '$tableName'")
                    if (!isBinaryType(typeName)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun findColumnTypeName(
        metadata: java.sql.ResultSetMetaData,
        column: String
    ): String? {
        for (index in 1..metadata.columnCount) {
            if (metadata.getColumnLabel(index).equals(column, ignoreCase = true)) {
                return metadata.getColumnTypeName(index)
            }
        }
        return null
    }

    private fun isBinaryType(typeName: String): Boolean {
        val normalized = typeName.uppercase()
        return normalized.contains("BLOB") || normalized.contains("BINARY") || normalized == "BYTEA"
    }

    private fun migrateExpiredItemsTable(connection: Connection) {
        val tempTable = "expired_items_uuid_migration_new"
        executeStatements(connection, dropTableIfExistsSql(tempTable), createExpiredItemsTableSql(tempTable))

        connection.prepareStatement("SELECT * FROM expired_items").use { select ->
            select.executeQuery().use { resultSet ->
                connection.prepareStatement(insertExpiredItemsSql(tempTable)).use { insert ->
                    while (resultSet.next()) {
                        insert.setBytes(1, requireStoredUuid(resultSet.getObject("id"), "expired_items.id").toBytes())
                        insert.setBytes(2, requireStoredUuid(resultSet.getObject("owner_uuid"), "expired_items.owner_uuid").toBytes())
                        insert.setString(3, resultSet.getString("owner_name"))
                        insert.setString(4, resultSet.getString("item_type"))
                        insert.setBytes(5, requireStoredUuid(resultSet.getObject("source_id"), "expired_items.source_id").toBytes())
                        insert.setBytes(6, resultSet.getBytes("item_stack"))
                        insert.setString(7, resultSet.getString("reason"))
                        insert.setTimestamp(8, resultSet.getTimestamp("expired_at"))
                        insert.setBoolean(9, resultSet.getBoolean("claimed"))
                        insert.setTimestamp(10, resultSet.getTimestamp("claimed_at"))
                        insert.setUuidBytesOrNull(11, resultSet.getObject("consolidated_group_id"))
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
            }
        }

        executeStatements(
            connection,
            dropTableSql("expired_items"),
            renameTableSql(tempTable, "expired_items"),
            *expiredItemsIndexSql("expired_items").toTypedArray()
        )
    }

    private fun migrateConsolidatedExpiredItemsTable(connection: Connection) {
        val tempTable = "consolidated_expired_items_uuid_migration_new"
        executeStatements(connection, dropTableIfExistsSql(tempTable), createConsolidatedExpiredItemsTableSql(tempTable))

        connection.prepareStatement("SELECT * FROM consolidated_expired_items").use { select ->
            select.executeQuery().use { resultSet ->
                connection.prepareStatement(insertConsolidatedExpiredItemsSql(tempTable)).use { insert ->
                    while (resultSet.next()) {
                        insert.setBytes(1, requireStoredUuid(resultSet.getObject("id"), "consolidated_expired_items.id").toBytes())
                        insert.setBytes(2, requireStoredUuid(resultSet.getObject("owner_uuid"), "consolidated_expired_items.owner_uuid").toBytes())
                        insert.setString(3, resultSet.getString("owner_name"))
                        insert.setString(4, resultSet.getString("item_type"))
                        insert.setBytes(5, requireStoredUuid(resultSet.getObject("source_id"), "consolidated_expired_items.source_id").toBytes())
                        insert.setString(6, resultSet.getString("item_material"))
                        insert.setString(7, resultSet.getString("item_display_name"))
                        insert.setInt(8, resultSet.getInt("total_quantity"))
                        insert.setInt(9, resultSet.getInt("claimed_quantity"))
                        insert.setBytes(10, resultSet.getBytes("item_stack"))
                        insert.setString(11, resultSet.getString("reason"))
                        insert.setTimestamp(12, resultSet.getTimestamp("expired_at"))
                        insert.setTimestamp(13, resultSet.getTimestamp("last_updated_at"))
                        insert.setBoolean(14, resultSet.getBoolean("is_fully_claimed"))
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
            }
        }

        executeStatements(
            connection,
            dropTableSql("consolidated_expired_items"),
            renameTableSql(tempTable, "consolidated_expired_items"),
            *consolidatedExpiredItemsIndexSql("consolidated_expired_items").toTypedArray()
        )
    }

    private fun requireStoredUuid(value: Any?, column: String): java.util.UUID {
        return value.toStoredUuidOrNull()
            ?: throw IllegalStateException("Column '$column' does not contain a UUID value")
    }

    private fun PreparedStatement.setUuidBytesOrNull(index: Int, value: Any?) {
        val uuid = value.toStoredUuidOrNull()
        if (uuid == null) {
            setNull(index, Types.BINARY)
        } else {
            setBytes(index, uuid.toBytes())
        }
    }

    private fun executeStatements(connection: Connection, vararg statements: String) {
        for (statement in statements) {
            connection.createStatement().use { stmt ->
                stmt.execute(statement)
            }
        }
    }

    private fun dropTableIfExistsSql(tableName: String): String {
        return when (database.dialect) {
            DatabaseDialect.MYSQL,
            DatabaseDialect.POSTGRES,
            DatabaseDialect.SQLITE -> "DROP TABLE IF EXISTS $tableName"
        }
    }

    private fun dropTableSql(tableName: String): String {
        return "DROP TABLE $tableName"
    }

    private fun renameTableSql(from: String, to: String): String {
        return when (database.dialect) {
            DatabaseDialect.MYSQL -> "RENAME TABLE $from TO $to"
            DatabaseDialect.POSTGRES,
            DatabaseDialect.SQLITE -> "ALTER TABLE $from RENAME TO $to"
        }
    }

    private fun createExpiredItemsTableSql(tableName: String): String {
        return when (database.dialect) {
            DatabaseDialect.MYSQL -> """
                CREATE TABLE $tableName (
                    id BINARY(16) PRIMARY KEY,
                    owner_uuid BINARY(16) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    item_type VARCHAR(20) NOT NULL,
                    source_id BINARY(16) NOT NULL,
                    item_stack BLOB NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    claimed BOOLEAN NOT NULL DEFAULT FALSE,
                    claimed_at TIMESTAMP NULL,
                    consolidated_group_id BINARY(16) NULL
                )
            """.trimIndent()
            DatabaseDialect.POSTGRES -> """
                CREATE TABLE $tableName (
                    id BYTEA PRIMARY KEY,
                    owner_uuid BYTEA NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    item_type VARCHAR(20) NOT NULL,
                    source_id BYTEA NOT NULL,
                    item_stack BYTEA NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    claimed BOOLEAN NOT NULL DEFAULT FALSE,
                    claimed_at TIMESTAMP NULL,
                    consolidated_group_id BYTEA NULL
                )
            """.trimIndent()
            DatabaseDialect.SQLITE -> """
                CREATE TABLE $tableName (
                    id BLOB(16) PRIMARY KEY,
                    owner_uuid BLOB(16) NOT NULL,
                    owner_name TEXT NOT NULL,
                    item_type TEXT NOT NULL,
                    source_id BLOB(16) NOT NULL,
                    item_stack BLOB NOT NULL,
                    reason TEXT NOT NULL,
                    expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    claimed INTEGER NOT NULL DEFAULT 0,
                    claimed_at TIMESTAMP,
                    consolidated_group_id BLOB(16)
                )
            """.trimIndent()
        }
    }

    private fun createConsolidatedExpiredItemsTableSql(tableName: String): String {
        return when (database.dialect) {
            DatabaseDialect.MYSQL -> """
                CREATE TABLE $tableName (
                    id BINARY(16) PRIMARY KEY,
                    owner_uuid BINARY(16) NOT NULL,
                    owner_name VARCHAR(32) NOT NULL,
                    item_type VARCHAR(20) NOT NULL,
                    source_id BINARY(16) NOT NULL,
                    item_material VARCHAR(50) NOT NULL,
                    item_display_name TEXT,
                    total_quantity INT NOT NULL DEFAULT 0,
                    claimed_quantity INT NOT NULL DEFAULT 0,
                    item_stack BLOB NOT NULL,
                    reason VARCHAR(100) NOT NULL,
                    expired_at TIMESTAMP NOT NULL,
                    last_updated_at TIMESTAMP NOT NULL,
                    is_fully_claimed BOOLEAN DEFAULT FALSE
                )
            """.trimIndent()
            DatabaseDialect.POSTGRES -> """
                CREATE TABLE $tableName (
                    id BYTEA PRIMARY KEY,
                    owner_uuid BYTEA NOT NULL,
                    owner_name VARCHAR(32) NOT NULL,
                    item_type VARCHAR(20) NOT NULL,
                    source_id BYTEA NOT NULL,
                    item_material VARCHAR(50) NOT NULL,
                    item_display_name TEXT,
                    total_quantity INT NOT NULL DEFAULT 0,
                    claimed_quantity INT NOT NULL DEFAULT 0,
                    item_stack BYTEA NOT NULL,
                    reason VARCHAR(100) NOT NULL,
                    expired_at TIMESTAMP NOT NULL,
                    last_updated_at TIMESTAMP NOT NULL,
                    is_fully_claimed BOOLEAN DEFAULT FALSE
                )
            """.trimIndent()
            DatabaseDialect.SQLITE -> """
                CREATE TABLE $tableName (
                    id BLOB(16) PRIMARY KEY,
                    owner_uuid BLOB(16) NOT NULL,
                    owner_name TEXT NOT NULL,
                    item_type TEXT NOT NULL,
                    source_id BLOB(16) NOT NULL,
                    item_material TEXT NOT NULL,
                    item_display_name TEXT,
                    total_quantity INTEGER NOT NULL DEFAULT 0,
                    claimed_quantity INTEGER NOT NULL DEFAULT 0,
                    item_stack BLOB NOT NULL,
                    reason TEXT NOT NULL,
                    expired_at TIMESTAMP NOT NULL,
                    last_updated_at TIMESTAMP NOT NULL,
                    is_fully_claimed INTEGER DEFAULT 0
                )
            """.trimIndent()
        }
    }

    private fun insertExpiredItemsSql(tableName: String): String {
        return """
            INSERT INTO $tableName
            (id, owner_uuid, owner_name, item_type, source_id, item_stack, reason, expired_at, claimed, claimed_at, consolidated_group_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }

    private fun insertConsolidatedExpiredItemsSql(tableName: String): String {
        return """
            INSERT INTO $tableName
            (id, owner_uuid, owner_name, item_type, source_id, item_material, item_display_name, total_quantity, claimed_quantity, item_stack, reason, expired_at, last_updated_at, is_fully_claimed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }

    private fun expiredItemsIndexSql(tableName: String): List<String> {
        return listOf(
            "CREATE INDEX idx_expired_items_owner ON $tableName(owner_uuid, claimed)",
            "CREATE INDEX idx_expired_items_expired ON $tableName(expired_at)",
            "CREATE INDEX idx_consolidated_group ON $tableName(consolidated_group_id)"
        )
    }

    private fun consolidatedExpiredItemsIndexSql(tableName: String): List<String> {
        return listOf(
            "CREATE INDEX idx_owner_uuid ON $tableName(owner_uuid)",
            "CREATE INDEX idx_source_id ON $tableName(source_id)",
            "CREATE INDEX idx_fully_claimed ON $tableName(is_fully_claimed)"
        )
    }
}
