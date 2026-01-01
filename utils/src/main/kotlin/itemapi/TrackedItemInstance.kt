package bruh.zchat.utils.itemapi

import java.time.Instant
import java.util.UUID

/**
 * Represents a runtime instance of a tracked item loaded from the database.
 * Each physical item in the world has a unique instance with its own metadata.
 *
 * @property instanceId Unique identifier for this item instance (stored in PDC)
 * @property itemId The definition ID (e.g., "magic_wand") linking to a TrackedItem
 * @property ownerUuid The UUID of the player who owns this item (nullable)
 * @property createdAt When this instance was created
 * @property lastInteractedAt When this instance was last interacted with
 * @property metadata Extensible key-value metadata storage
 * @property isDirty Whether this instance has unsaved changes
 */
data class TrackedItemInstance(
    val instanceId: UUID,
    val itemId: String,
    val ownerUuid: UUID?,
    val createdAt: Instant,
    var lastInteractedAt: Instant,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    var isDirty: Boolean = false
) {
    /**
     * Gets a string value from metadata.
     */
    fun getString(key: String): String? = metadata[key]

    /**
     * Sets a string value in metadata and marks as dirty.
     */
    fun setString(key: String, value: String) {
        metadata[key] = value
        isDirty = true
    }

    /**
     * Gets an integer value from metadata.
     */
    fun getInt(key: String): Int? = metadata[key]?.toIntOrNull()

    /**
     * Sets an integer value in metadata and marks as dirty.
     */
    fun setInt(key: String, value: Int) {
        metadata[key] = value.toString()
        isDirty = true
    }

    /**
     * Gets a long value from metadata.
     */
    fun getLong(key: String): Long? = metadata[key]?.toLongOrNull()

    /**
     * Sets a long value in metadata and marks as dirty.
     */
    fun setLong(key: String, value: Long) {
        metadata[key] = value.toString()
        isDirty = true
    }

    /**
     * Gets a boolean value from metadata.
     */
    fun getBoolean(key: String): Boolean? = metadata[key]?.toBooleanStrictOrNull()

    /**
     * Sets a boolean value in metadata and marks as dirty.
     */
    fun setBoolean(key: String, value: Boolean) {
        metadata[key] = value.toString()
        isDirty = true
    }

    /**
     * Gets a double value from metadata.
     */
    fun getDouble(key: String): Double? = metadata[key]?.toDoubleOrNull()

    /**
     * Sets a double value in metadata and marks as dirty.
     */
    fun setDouble(key: String, value: Double) {
        metadata[key] = value.toString()
        isDirty = true
    }

    /**
     * Gets a UUID value from metadata.
     */
    fun getUUID(key: String): UUID? = metadata[key]?.let {
        try {
            UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Sets a UUID value in metadata and marks as dirty.
     */
    fun setUUID(key: String, value: UUID) {
        metadata[key] = value.toString()
        isDirty = true
    }

    /**
     * Removes a metadata key and marks as dirty.
     */
    fun remove(key: String): String? {
        val removed = metadata.remove(key)
        if (removed != null) isDirty = true
        return removed
    }

    /**
     * Checks if a metadata key exists.
     */
    fun hasKey(key: String): Boolean = metadata.containsKey(key)

    /**
     * Clears all metadata and marks as dirty.
     */
    fun clearMetadata() {
        if (metadata.isNotEmpty()) {
            metadata.clear()
            isDirty = true
        }
    }

    /**
     * Updates the last interacted timestamp and marks as dirty.
     */
    fun touch() {
        lastInteractedAt = Instant.now()
        isDirty = true
    }
}
