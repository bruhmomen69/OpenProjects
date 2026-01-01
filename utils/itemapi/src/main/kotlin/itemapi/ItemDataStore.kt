package bruh.zchat.utils.itemapi

import java.io.Closeable
import java.time.Instant
import java.util.*

/**
 * Interface for persisting TrackedItemInstance data.
 * Implementations can use different storage backends (database, file, etc.).
 */
interface ItemDataStore : Closeable {
    /**
     * Loads a TrackedItemInstance by its unique instance ID.
     *
     * @param instanceId The unique instance ID
     * @return The loaded instance, or null if not found
     */
    suspend fun load(instanceId: UUID): TrackedItemInstance?

    /**
     * Saves a TrackedItemInstance and its metadata.
     * If the instance already exists, it will be updated.
     *
     * @param instance The instance to save
     */
    suspend fun save(instance: TrackedItemInstance)

    /**
     * Deletes a TrackedItemInstance and its metadata.
     *
     * @param instanceId The instance ID to delete
     * @return true if the instance was deleted, false if it didn't exist
     */
    suspend fun delete(instanceId: UUID): Boolean

    /**
     * Deletes all TrackedItemInstances owned by a player.
     *
     * @param ownerUuid The owner's UUID
     * @return The number of instances deleted
     */
    suspend fun deleteByOwner(ownerUuid: UUID): Int

    /**
     * Finds all TrackedItemInstances owned by a player.
     *
     * @param ownerUuid The owner's UUID
     * @return List of instances owned by the player
     */
    suspend fun findByOwner(ownerUuid: UUID): List<TrackedItemInstance>

    /**
     * Finds all TrackedItemInstances of a specific item type.
     *
     * @param itemId The item definition ID
     * @return List of instances of that item type
     */
    suspend fun findByItemId(itemId: String): List<TrackedItemInstance>

    /**
     * Updates only the lastInteractedAt timestamp for an instance.
     * This is an optimization to avoid full saves for touch operations.
     *
     * @param instanceId The instance ID
     * @param timestamp The new timestamp
     */
    suspend fun updateLastInteracted(instanceId: UUID, timestamp: Instant)

    /**
     * Counts the total number of tracked item instances.
     *
     * @return The total count
     */
    suspend fun count(): Long

    /**
     * Counts the number of instances of a specific item type.
     *
     * @param itemId The item definition ID
     * @return The count
     */
    suspend fun countByItemId(itemId: String): Long

    /**
     * Closes any resources held by the store.
     */
    override fun close()
}