package bruh.zchat.utils.database

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Extension functions for ResultSet to simplify common data type conversions.
 */

/**
 * Gets a UUID from a string column.
 * @param columnLabel The column name
 * @return The UUID, or null if the column value is null
 */
fun ResultSet.getUUID(columnLabel: String): UUID? {
    val value = getString(columnLabel) ?: return null
    return try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Gets a UUID from a string column, throwing if null or invalid.
 * @param columnLabel The column name
 * @return The UUID
 * @throws IllegalStateException if the column is null or contains an invalid UUID
 */
fun ResultSet.getUUIDOrThrow(columnLabel: String): UUID {
    val value = getString(columnLabel)
        ?: throw IllegalStateException("Column '$columnLabel' is null")
    return try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("Column '$columnLabel' contains invalid UUID: $value", e)
    }
}

/**
 * Gets an Instant from a timestamp column.
 * @param columnLabel The column name
 * @return The Instant, or null if the column value is null
 */
fun ResultSet.getInstant(columnLabel: String): Instant? {
    val timestamp = getTimestamp(columnLabel) ?: return null
    return timestamp.toInstant()
}

/**
 * Gets an Instant from a timestamp column, throwing if null.
 * @param columnLabel The column name
 * @return The Instant
 * @throws IllegalStateException if the column is null
 */
fun ResultSet.getInstantOrThrow(columnLabel: String): Instant {
    val timestamp = getTimestamp(columnLabel)
        ?: throw IllegalStateException("Column '$columnLabel' is null")
    return timestamp.toInstant()
}

/**
 * Gets an Instant from an epoch milliseconds column (BIGINT).
 * @param columnLabel The column name
 * @return The Instant, or null if the column value is null or 0
 */
fun ResultSet.getInstantFromEpochMs(columnLabel: String): Instant? {
    val epochMs = getLong(columnLabel)
    if (wasNull() || epochMs == 0L) return null
    return Instant.ofEpochMilli(epochMs)
}

/**
 * Gets a nullable String, returning null for SQL NULL.
 * @param columnLabel The column name
 * @return The string value or null
 */
fun ResultSet.getStringOrNull(columnLabel: String): String? {
    val value = getString(columnLabel)
    return if (wasNull()) null else value
}

/**
 * Gets a nullable Int, returning null for SQL NULL.
 * @param columnLabel The column name
 * @return The int value or null
 */
fun ResultSet.getIntOrNull(columnLabel: String): Int? {
    val value = getInt(columnLabel)
    return if (wasNull()) null else value
}

/**
 * Gets a nullable Long, returning null for SQL NULL.
 * @param columnLabel The column name
 * @return The long value or null
 */
fun ResultSet.getLongOrNull(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}

/**
 * Gets a nullable Boolean, returning null for SQL NULL.
 * @param columnLabel The column name
 * @return The boolean value or null
 */
fun ResultSet.getBooleanOrNull(columnLabel: String): Boolean? {
    val value = getBoolean(columnLabel)
    return if (wasNull()) null else value
}

/**
 * Gets a nullable Double, returning null for SQL NULL.
 * @param columnLabel The column name
 * @return The double value or null
 */
fun ResultSet.getDoubleOrNull(columnLabel: String): Double? {
    val value = getDouble(columnLabel)
    return if (wasNull()) null else value
}

/**
 * Gets bytes from a BLOB column.
 * @param columnLabel The column name
 * @return The byte array, or null if the column value is null
 */
fun ResultSet.getBytesOrNull(columnLabel: String): ByteArray? {
    return getBytes(columnLabel)
}
