package bruh.zchat.utils.itemapi

import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.DropResult
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.event.inventory.ClickType
import java.util.concurrent.CompletableFuture
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Main API class for tracked items.
 * Handles registration, creation, caching, and event handling for tracked items.
 *
 * Usage:
 * ```kotlin
 * val itemApi = ItemAPI(plugin, DatabaseItemDataStore(database))
 *
 * itemApi.register("magic_wand") {
 *     material(XMaterial.STICK)
 *     item {
 *         name = Component.text("Magic Wand")
 *         glow()
 *     }
 *     onClick { ctx, controls ->
 *         ctx.player.sendMessage("Wand activated!")
 *         ClickResult.DENY
 *     }
 *     soulbound = true
 * }
 *
 * val item = itemApi.createItem("magic_wand", player)
 * player.inventory.addItem(item)
 * ```
 *
 * @param plugin The owning plugin
 * @param dataStore The data store for persisting item instances
 */
class ItemAPI(
    val plugin: JavaPlugin,
    private val dataStore: ItemDataStore
) : Closeable {

    private val logger = LoggerFactory.getLogger(ItemAPI::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** PDC key for the item definition ID */
    val itemIdKey: NamespacedKey = NamespacedKey(plugin, "tracked_item_id")

    /** PDC key for the unique instance ID */
    val instanceIdKey: NamespacedKey = NamespacedKey(plugin, "tracked_instance_id")

    /** Registry for TrackedItem definitions */
    val registry = ItemRegistry()

    /** Async cache for loaded instances */
    private val instanceCache: AsyncCache<UUID, TrackedItemInstance> = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(5000)
        .buildAsync()

    /** Set of instance IDs currently being loaded */
    private val pendingLoads: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** Event listener */
    private val listener = ItemListener()

    init {
        Bukkit.getPluginManager().registerEvents(listener, plugin)
        logger.debug("ItemAPI initialized")
    }

    /**
     * Registers a TrackedItem definition.
     *
     * @param item The TrackedItem to register
     * @return The registered TrackedItem
     */
    fun register(item: TrackedItem): TrackedItem {
        registry.register(item)
        logger.debug("Registered tracked item: ${item.id}")
        return item
    }

    /**
     * Registers a TrackedItem using a DSL builder.
     *
     * @param id The unique identifier for the item
     * @param builder DSL builder for configuring the item
     * @return The registered TrackedItem
     */
    inline fun register(id: String, builder: TrackedItem.() -> Unit): TrackedItem {
        val item = TrackedItem(id).apply(builder)
        return register(item)
    }

    /**
     * Creates a new tracked item instance and returns the ItemStack.
     *
     * @param itemId The item definition ID
     * @param owner The owner player (optional)
     * @param metadata Initial metadata to set (optional)
     * @return The created ItemStack, or null if the item ID is not registered
     */
    suspend fun createItem(
        itemId: String,
        owner: Player? = null,
        metadata: Map<String, String> = emptyMap()
    ): ItemStack? {
        val definition = registry.get(itemId) ?: return null

        val instanceId = UUID.randomUUID()
        val now = Instant.now()

        val instance = TrackedItemInstance(
            instanceId = instanceId,
            itemId = itemId,
            ownerUuid = owner?.uniqueId,
            createdAt = now,
            lastInteractedAt = now,
            metadata = metadata.toMutableMap(),
            isDirty = true
        )

        // Save to database
        dataStore.save(instance)

        // Add to cache
        instanceCache.put(instanceId, CompletableFuture.completedFuture(instance))

        // Build the ItemStack with PDC tracking
        val itemStack = definition.buildItemStack()
        itemStack.editMeta { meta ->
            meta.persistentDataContainer.set(itemIdKey, PersistentDataType.STRING, itemId)
            meta.persistentDataContainer.set(instanceIdKey, PersistentDataType.STRING, instanceId.toString())
        }

        logger.debug("Created tracked item instance: $instanceId for item $itemId")
        return itemStack
    }

    /**
     * Checks if an ItemStack is a tracked item.
     */
    fun isTrackedItem(itemStack: ItemStack?): Boolean {
        if (itemStack == null || itemStack.type.isAir) return false
        val meta = itemStack.itemMeta ?: return false
        return meta.persistentDataContainer.has(instanceIdKey, PersistentDataType.STRING)
    }

    /**
     * Gets the item definition ID from an ItemStack.
     */
    fun getItemId(itemStack: ItemStack?): String? {
        if (itemStack == null || itemStack.type.isAir) return null
        val meta = itemStack.itemMeta ?: return null
        return meta.persistentDataContainer.get(itemIdKey, PersistentDataType.STRING)
    }

    /**
     * Gets the instance ID from an ItemStack.
     */
    fun getInstanceId(itemStack: ItemStack?): UUID? {
        if (itemStack == null || itemStack.type.isAir) return null
        val meta = itemStack.itemMeta ?: return null
        val idString = meta.persistentDataContainer.get(instanceIdKey, PersistentDataType.STRING) ?: return null
        return try {
            UUID.fromString(idString)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Gets a TrackedItemInstance from an ItemStack.
     * Returns null if not a tracked item or if still loading.
     */
    suspend fun getInstance(itemStack: ItemStack?): TrackedItemInstance? {
        val instanceId = getInstanceId(itemStack) ?: return null
        return getInstanceById(instanceId)
    }

    /**
     * Gets a TrackedItemInstance by its instance ID.
     */
    suspend fun getInstanceById(instanceId: UUID): TrackedItemInstance? {
        // Check cache first
        val cached = instanceCache.getIfPresent(instanceId)
        if (cached != null) {
            return cached.await()
        }

        // Load from database
        return loadInstance(instanceId)
    }

    /**
     * Loads an instance from the database and caches it.
     */
    private suspend fun loadInstance(instanceId: UUID): TrackedItemInstance? {
        val future = instanceCache.get(instanceId) { id ->
            runBlocking { dataStore.load(id) }
        }
        return future.await()
    }

    /**
     * Starts loading an instance asynchronously.
     */
    private fun startLoadAsync(instanceId: UUID) {
        if (pendingLoads.contains(instanceId)) return
        pendingLoads.add(instanceId)

        scope.launch {
            try {
                loadInstance(instanceId)
            } finally {
                pendingLoads.remove(instanceId)
            }
        }
    }

    /**
     * Checks if an instance is currently loading.
     */
    fun isLoading(instanceId: UUID): Boolean {
        if (pendingLoads.contains(instanceId)) return true

        val future = instanceCache.getIfPresent(instanceId) ?: return false
        return !future.isDone
    }

    /**
     * Saves an instance to the database.
     */
    suspend fun saveInstance(instance: TrackedItemInstance) {
        dataStore.save(instance)
        instance.isDirty = false
    }

    /**
     * Invalidates a cached instance.
     */
    fun invalidateCache(instanceId: UUID) {
        instanceCache.synchronous().invalidate(instanceId)
    }

    /**
     * Deletes an instance from the database and cache.
     */
    suspend fun deleteInstance(instanceId: UUID): Boolean {
        invalidateCache(instanceId)
        return dataStore.delete(instanceId)
    }

    override fun close() {
        HandlerList.unregisterAll(listener)
        instanceCache.synchronous().invalidateAll()
        dataStore.close()
        logger.debug("ItemAPI closed")
    }

    /**
     * Internal event listener for tracked item interactions.
     */
    private inner class ItemListener : Listener {

        private val lastDropControl: MutableMap<UUID, Boolean> = ConcurrentHashMap()

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryClick(event: InventoryClickEvent) {
            val player = event.whoClicked as? Player ?: return
            val item = event.currentItem ?: return

            val instanceId = getInstanceId(item) ?: return
            val itemId = getItemId(item) ?: return
            val definition = registry.get(itemId) ?: return

            // Block if loading
            if (isLoading(instanceId)) {
                event.isCancelled = true
                return
            }

            // Track whether this was a DROP or CONTROL_DROP for later use in onPlayerDropItem
            if (event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP) {
                lastDropControl[player.uniqueId] = (event.click == ClickType.CONTROL_DROP)
            } else {
                lastDropControl.remove(player.uniqueId)
            }

            // Try to get from cache
            val future = instanceCache.getIfPresent(instanceId)
            if (future == null) {
                // Start loading, block interaction
                event.isCancelled = true
                startLoadAsync(instanceId)
                return
            }

            if (!future.isDone) {
                event.isCancelled = true
                return
            }

            val instance = try {
                future.get()
            } catch (e: Exception) {
                logger.warn("Failed to get tracked item instance: $instanceId", e)
                event.isCancelled = true
                return
            } ?: return

            // Check slot restriction for moves
            if (isSlotMove(event) && definition.allowedSlots != null) {
                val targetSlot = getTargetSlot(event)
                if (targetSlot != -1 && targetSlot !in definition.allowedSlots!!) {
                    event.isCancelled = true
                    return
                }
            }

            // Call click handler
            if (definition.clickHandler != null) {
                val context = ItemContext(
                    player = player,
                    itemStack = item,
                    slot = event.slot,
                    action = ItemAction.CLICK,
                    clickType = event.click,
                    isShiftClick = event.isShiftClick,
                    isRightClick = event.isRightClick,
                    isLeftClick = event.isLeftClick
                )

                instance.touch()
                val controls = ItemControls(this@ItemAPI, instance, definition, player)
                val result = definition.clickHandler!!.invoke(context, controls)

                when (result) {
                    ClickResult.ALLOW -> {}
                    ClickResult.DENY -> event.isCancelled = true
                    ClickResult.CLOSE -> {
                        event.isCancelled = true
                        Bukkit.getScheduler().runTask(plugin, Runnable { player.closeInventory() })
                    }
                    ClickResult.REFRESH -> event.isCancelled = true
                }

                // Save if dirty
                if (instance.isDirty) {
                    scope.launch { saveInstance(instance) }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onPlayerDropItem(event: PlayerDropItemEvent) {
            val player = event.player
            val item = event.itemDrop.itemStack

            val instanceId = getInstanceId(item) ?: return
            val itemId = getItemId(item) ?: return
            val definition = registry.get(itemId) ?: return

            // Block if loading
            if (isLoading(instanceId)) {
                event.isCancelled = true
                return
            }

            val future = instanceCache.getIfPresent(instanceId)
            if (future == null) {
                event.isCancelled = true
                startLoadAsync(instanceId)
                return
            }

            if (!future.isDone) {
                event.isCancelled = true
                return
            }

            val instance = try {
                future.get()
            } catch (e: Exception) {
                logger.warn("Failed to get tracked item instance: $instanceId", e)
                event.isCancelled = true
                return
            } ?: return

            // Soulbound items cannot be dropped
            if (definition.soulbound) {
                event.isCancelled = true
                return
            }

            // Call drop handler
            if (definition.dropHandler != null) {
                val isControlDrop = lastDropControl.remove(player.uniqueId) ?: false

                val context = ItemContext(
                    player = player,
                    itemStack = item,
                    slot = player.inventory.heldItemSlot,
                    action = ItemAction.DROP,
                    isControlDrop = isControlDrop
                )

                instance.touch()
                val controls = ItemControls(this@ItemAPI, instance, definition, player)
                val result = definition.dropHandler!!.invoke(context, controls)

                when (result) {
                    DropResult.ALLOW -> {}
                    DropResult.DENY -> event.isCancelled = true
                    DropResult.CLOSE -> {
                        event.isCancelled = true
                        player.closeInventory()
                    }
                }

                if (instance.isDirty) {
                    scope.launch { saveInstance(instance) }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onPlayerInteract(event: PlayerInteractEvent) {
            if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

            val player = event.player
            val item = event.item ?: return

            val instanceId = getInstanceId(item) ?: return
            val itemId = getItemId(item) ?: return
            val definition = registry.get(itemId) ?: return

            // Block if loading
            if (isLoading(instanceId)) {
                event.isCancelled = true
                return
            }

            val future = instanceCache.getIfPresent(instanceId)
            if (future == null) {
                event.isCancelled = true
                startLoadAsync(instanceId)
                return
            }

            if (!future.isDone) {
                event.isCancelled = true
                return
            }

            val instance = try {
                future.get()
            } catch (e: Exception) {
                logger.warn("Failed to get tracked item instance: $instanceId", e)
                event.isCancelled = true
                return
            } ?: return

            // Call use handler
            if (definition.useHandler != null) {
                val context = ItemContext(
                    player = player,
                    itemStack = item,
                    slot = player.inventory.heldItemSlot,
                    action = ItemAction.USE,
                    isRightClick = true
                )

                instance.touch()
                val controls = ItemControls(this@ItemAPI, instance, definition, player)
                val result = definition.useHandler!!.invoke(context, controls)

                when (result) {
                    ClickResult.ALLOW -> {}
                    ClickResult.DENY -> event.isCancelled = true
                    ClickResult.CLOSE -> {
                        event.isCancelled = true
                        player.closeInventory()
                    }
                    ClickResult.REFRESH -> event.isCancelled = true
                }

                if (instance.isDirty) {
                    scope.launch { saveInstance(instance) }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        fun onInventoryDrag(event: InventoryDragEvent) {
            val player = event.whoClicked as? Player ?: return

            // Check all dragged items
            for ((slot, item) in event.newItems) {
                val instanceId = getInstanceId(item) ?: continue
                val itemId = getItemId(item) ?: continue
                val definition = registry.get(itemId) ?: continue

                // Block if loading
                if (isLoading(instanceId)) {
                    event.isCancelled = true
                    return
                }

                val future = instanceCache.getIfPresent(instanceId)
                if (future == null || !future.isDone) {
                    event.isCancelled = true
                    if (future == null) startLoadAsync(instanceId)
                    return
                }

                // Check slot restriction
                if (definition.allowedSlots != null && slot !in definition.allowedSlots!!) {
                    event.isCancelled = true
                    return
                }
            }
        }

        private fun isSlotMove(event: InventoryClickEvent): Boolean {
            return event.isShiftClick || event.click.isKeyboardClick || event.hotbarButton >= 0
        }

        private fun getTargetSlot(event: InventoryClickEvent): Int {
            return when {
                event.hotbarButton >= 0 -> event.hotbarButton
                event.isShiftClick -> -1 // Can't determine target for shift-click
                else -> event.rawSlot
            }
        }
    }
}
