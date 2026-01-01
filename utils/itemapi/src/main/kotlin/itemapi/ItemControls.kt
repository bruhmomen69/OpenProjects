package bruh.zchat.utils.itemapi

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Controls provided to tracked item handlers for accessing and modifying instance data.
 *
 * @property instance The tracked item instance with metadata
 * @property definition The tracked item definition
 * @property player The player interacting with the item
 */
class ItemControls internal constructor(
    private val api: ItemAPI,
    val instance: TrackedItemInstance,
    val definition: TrackedItem,
    val player: Player
) {
    /** The unique instance ID */
    val instanceId: UUID get() = instance.instanceId

    /** The item definition ID */
    val itemId: String get() = instance.itemId

    /** The owner UUID (nullable) */
    val ownerUuid: UUID? get() = instance.ownerUuid

    /**
     * Gets a string value from instance metadata.
     */
    fun getString(key: String): String? = instance.getString(key)

    /**
     * Sets a string value in instance metadata.
     */
    fun setString(key: String, value: String) {
        instance.setString(key, value)
    }

    /**
     * Gets an integer value from instance metadata.
     */
    fun getInt(key: String): Int? = instance.getInt(key)

    /**
     * Sets an integer value in instance metadata.
     */
    fun setInt(key: String, value: Int) {
        instance.setInt(key, value)
    }

    /**
     * Gets a long value from instance metadata.
     */
    fun getLong(key: String): Long? = instance.getLong(key)

    /**
     * Sets a long value in instance metadata.
     */
    fun setLong(key: String, value: Long) {
        instance.setLong(key, value)
    }

    /**
     * Gets a boolean value from instance metadata.
     */
    fun getBoolean(key: String): Boolean? = instance.getBoolean(key)

    /**
     * Sets a boolean value in instance metadata.
     */
    fun setBoolean(key: String, value: Boolean) {
        instance.setBoolean(key, value)
    }

    /**
     * Gets a double value from instance metadata.
     */
    fun getDouble(key: String): Double? = instance.getDouble(key)

    /**
     * Sets a double value in instance metadata.
     */
    fun setDouble(key: String, value: Double) {
        instance.setDouble(key, value)
    }

    /**
     * Gets a UUID value from instance metadata.
     */
    fun getUUID(key: String): UUID? = instance.getUUID(key)

    /**
     * Sets a UUID value in instance metadata.
     */
    fun setUUID(key: String, value: UUID) {
        instance.setUUID(key, value)
    }

    /**
     * Removes a metadata key.
     */
    fun remove(key: String): String? = instance.remove(key)

    /**
     * Checks if a metadata key exists.
     */
    fun hasKey(key: String): Boolean = instance.hasKey(key)

    /**
     * Saves the instance to the database if dirty.
     */
    suspend fun save() {
        if (instance.isDirty) {
            api.saveInstance(instance)
        }
    }

    /**
     * Forces a save regardless of dirty state.
     */
    suspend fun forceSave() {
        api.saveInstance(instance)
    }

    /**
     * Invalidates this instance in the cache, forcing a reload on next access.
     */
    fun invalidateCache() {
        api.invalidateCache(instance.instanceId)
    }

    /**
     * Updates the last interacted timestamp.
     */
    fun touch() {
        instance.touch()
    }
}
