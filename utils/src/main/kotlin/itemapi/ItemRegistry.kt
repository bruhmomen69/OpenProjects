package bruh.zchat.utils.itemapi

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for TrackedItem definitions.
 * Thread-safe storage for item type definitions that can be looked up by ID.
 */
class ItemRegistry {
    private val items: MutableMap<String, TrackedItem> = ConcurrentHashMap()

    /**
     * Registers a tracked item definition.
     *
     * @param item The TrackedItem to register
     * @throws IllegalArgumentException if an item with this ID is already registered
     */
    fun register(item: TrackedItem) {
        require(!items.containsKey(item.id)) {
            "TrackedItem with id '${item.id}' is already registered"
        }
        items[item.id] = item
    }

    /**
     * Registers a tracked item using a DSL builder.
     *
     * @param id The unique identifier for the item
     * @param builder DSL builder for configuring the item
     * @return The registered TrackedItem
     */
    inline fun register(id: String, builder: TrackedItem.() -> Unit): TrackedItem {
        val item = TrackedItem(id).apply(builder)
        register(item)
        return item
    }

    /**
     * Gets a registered TrackedItem by ID.
     *
     * @param id The item ID to look up
     * @return The TrackedItem, or null if not found
     */
    fun get(id: String): TrackedItem? = items[id]

    /**
     * Gets a registered TrackedItem by ID, throwing if not found.
     *
     * @param id The item ID to look up
     * @return The TrackedItem
     * @throws IllegalArgumentException if the item is not registered
     */
    fun getOrThrow(id: String): TrackedItem {
        return items[id] ?: throw IllegalArgumentException("TrackedItem '$id' is not registered")
    }

    /**
     * Checks if an item ID is registered.
     *
     * @param id The item ID to check
     * @return true if registered
     */
    fun isRegistered(id: String): Boolean = items.containsKey(id)

    /**
     * Gets all registered item IDs.
     */
    fun getRegisteredIds(): Set<String> = items.keys.toSet()

    /**
     * Gets all registered TrackedItems.
     */
    fun getAll(): Collection<TrackedItem> = items.values.toList()

    /**
     * Unregisters a TrackedItem by ID.
     *
     * @param id The item ID to unregister
     * @return The removed TrackedItem, or null if not found
     */
    fun unregister(id: String): TrackedItem? = items.remove(id)

    /**
     * Clears all registered items.
     */
    fun clear() {
        items.clear()
    }

    /**
     * Gets the number of registered items.
     */
    val size: Int get() = items.size
}
