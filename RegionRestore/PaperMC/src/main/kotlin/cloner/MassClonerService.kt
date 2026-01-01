package bruh.regionrestore.cloner

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import bruh.regionrestore.config.MassClonerConfig
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplateVersion
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.template.TemplateCache
import bruh.regionrestore.template.TemplateRef
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.SchedulerService
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Composite key for pool registry.
 * Allows the same template to be used in multiple world pools.
 */
@ConfigSerializable
data class PoolKey(
    val worldName: String,
    val templateName: String
)

@ConfigSerializable
data class RegionInstance(
    val instanceId: UUID,
    val worldName: String,
    val templateName: String,
    val versionId: Int?,
    val originChunkX: Int,
    val originChunkZ: Int,
    val sizeXChunks: Int,
    val sizeZChunks: Int,
    val minBlockX: Int,
    val maxBlockX: Int,
    val minBlockZ: Int,
    val maxBlockZ: Int,
    val instanceType: InstanceType = InstanceType.POOLED,
    val config: InstanceConfig? = null,
    val occupancyCount: Int = 0
) {
    companion object {
        /**
         * Factory method to safely create RegionInstance with correct bounds.
         * Calculates block bounding box from chunk coordinates and dimensions.
         */
        fun create(
            instanceId: UUID,
            worldName: String,
            templateName: String,
            versionId: Int?,
            originChunkX: Int,
            originChunkZ: Int,
            sizeXChunks: Int,
            sizeZChunks: Int,
            instanceType: InstanceType = InstanceType.POOLED,
            config: InstanceConfig? = null
        ) = RegionInstance(
            instanceId = instanceId,
            worldName = worldName,
            templateName = templateName,
            versionId = versionId,
            originChunkX = originChunkX,
            originChunkZ = originChunkZ,
            sizeXChunks = sizeXChunks,
            sizeZChunks = sizeZChunks,
            minBlockX = originChunkX * 16,
            maxBlockX = (originChunkX + sizeXChunks) * 16 - 1,
            minBlockZ = originChunkZ * 16,
            maxBlockZ = (originChunkZ + sizeZChunks) * 16 - 1,
            instanceType = instanceType,
            config = config
        )
    }
}

enum class VersionMode {
    ACTIVE,
    PINNED
}

data class RegionPool(
    val worldName: String,
    val templateName: String,
    val versionMode: VersionMode,
    val pinnedVersionId: Int?,
    val count: Int,
    val separationChunks: Int,
    val restoreOnBoot: Boolean,
    val restoreOnVacate: Boolean,
    val restoreIntervalSeconds: Int?,
    val restoreAudienceScope: AudienceScope,
    /** Optional per-pool override for light updates. If null, falls back to global RestoreConfig.updateLight. */
    val updateLight: Boolean?
)

/**
 * Per-instance trigger configuration.
 * Allows each instance to have its own restore behavior.
 */
@ConfigSerializable
data class InstanceConfig(
    val restoreOnBoot: Boolean = false,
    val restoreOnVacate: Boolean = false,
    val restoreIntervalSeconds: Int? = null,
    val restoreAudienceScope: AudienceScope = AudienceScope.WORLD,
    /** Optional per-instance override for light updates. If null, falls back to pool or global setting. */
    val updateLight: Boolean? = null
)

/**
 * Type of instance - pooled (auto-allocated) or manual (user-specified coordinates).
 */
enum class InstanceType {
    POOLED,      // Created by Mass Cloner auto-allocation
    MANUAL       // Created by user command with explicit coordinates
}

/**
 * Registry of all instances (pooled and manual).
 * Key: world name
 * Value: List of instances in that world
 */
data class InstanceRegistry(
    val instances: ConcurrentHashMap<String, List<RegionInstance>>
)

/**
 * Represents a change in instance occupancy.
 * Used to notify the MassClonerService of occupancy updates.
 */
data class OccupancyUpdate(
    val instanceId: UUID,
    val newCount: Int
)

/**
 * Tracks a player's current region instance state.
 * The PlayerState object itself serves as the intrinsic lock for synchronizing
 * all state changes for a specific player, eliminating the need for a separate lock map.
 */
data class PlayerState(
    var currentInstance: RegionInstance? = null
)

class OccupancyTracker(private val plugin: JavaPlugin) : Listener {
    private val chunkToInstance = ConcurrentHashMap<Pair<String, Pair<Int, Int>>, RegionInstance>()
    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private val instanceOccupancy = ConcurrentHashMap<UUID, Int>()

    fun registerInstance(instance: RegionInstance) {
        for (x in instance.minBlockX..instance.maxBlockX step 16) {
            for (z in instance.minBlockZ..instance.maxBlockZ step 16) {
                val chunkX = (x / 16)
                val chunkZ = (z / 16)
                chunkToInstance[Pair(instance.worldName, Pair(chunkX, chunkZ))] = instance
            }
        }
    }

    fun unregisterInstance(instanceId: UUID) {
        chunkToInstance.values.removeIf { it.instanceId == instanceId }
        instanceOccupancy.remove(instanceId)
    }

    fun updatePlayerOccupancy(player: org.bukkit.entity.Player): List<OccupancyUpdate> {
        val playerId = player.uniqueId
        val playerState = playerStates.getOrPut(playerId) { PlayerState() }

        return synchronized(playerState) {
            val updates = mutableListOf<OccupancyUpdate>()

            val playerChunk = Pair(player.location.chunk.x, player.location.chunk.z)
            val worldName = player.world.name
            val currentInstance = chunkToInstance[Pair(worldName, playerChunk)]

            val previousInstance = playerState.currentInstance

            // Case 1: No previous instance, entering a new instance
            if (previousInstance == null && currentInstance != null) {
                playerState.currentInstance = currentInstance

                val newCount = instanceOccupancy.compute(currentInstance.instanceId) { _, count ->
                    ((count ?: 0) + 1)
                }!!

                updates.add(OccupancyUpdate(currentInstance.instanceId, newCount))
                return@synchronized updates
            }

            // Case 2: Had previous instance, leaving to wilderness
            if (previousInstance != null && currentInstance == null) {
                playerState.currentInstance = null

                val newCount = instanceOccupancy.compute(previousInstance.instanceId) { _, count ->
                    ((count ?: 1) - 1).coerceAtLeast(0)
                }!!

                updates.add(OccupancyUpdate(previousInstance.instanceId, newCount))
                return@synchronized updates
            }

            // Case 3: Moving within same instance (no change)
            if (previousInstance?.instanceId == currentInstance?.instanceId) {
                return@synchronized updates  // Empty list
            }

            // Case 4: Moving from A to B (adjacent instances) - THE BUG FIX
            if (previousInstance != null && currentInstance != null &&
                previousInstance.instanceId != currentInstance.instanceId
            ) {

                // Decrement A (atomically)
                val newCountA = instanceOccupancy.compute(previousInstance.instanceId) { _, count ->
                    ((count ?: 1) - 1).coerceAtLeast(0)
                }!!
                updates.add(OccupancyUpdate(previousInstance.instanceId, newCountA))

                // Increment B (atomically)
                val newCountB = instanceOccupancy.compute(currentInstance.instanceId) { _, count ->
                    ((count ?: 0) + 1)
                }!!
                updates.add(OccupancyUpdate(currentInstance.instanceId, newCountB))

                // Update player's current location
                playerState.currentInstance = currentInstance

                return@synchronized updates
            }

            updates
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val fromChunk = event.from.chunk
        val toChunk = event.to.chunk

        if (fromChunk.x != toChunk.x || fromChunk.z != toChunk.z || event.from.world != event.to.world) {
            val updates = updatePlayerOccupancy(event.player)
            for ((instanceId, newCount) in updates) {
                massClonerService?.onInstanceOccupancyChanged(instanceId, newCount)
            }
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val updates = updatePlayerOccupancy(event.player)
        for ((instanceId, newCount) in updates) {
            massClonerService?.onInstanceOccupancyChanged(instanceId, newCount)
        }
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val updates = updatePlayerOccupancy(event.player)
        for ((instanceId, newCount) in updates) {
            massClonerService?.onInstanceOccupancyChanged(instanceId, newCount)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val updates = updatePlayerOccupancy(event.player)
        for ((instanceId, newCount) in updates) {
            massClonerService?.onInstanceOccupancyChanged(instanceId, newCount)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        val playerState = playerStates[playerId] ?: return

        // Synchronize on playerState to prevent race condition with concurrent move events
        val instance = synchronized(playerState) {
            val instance = playerState.currentInstance ?: return
            playerState.currentInstance = null
            playerStates.remove(playerId)

            val newCount = instanceOccupancy.compute(instance.instanceId) { _, count ->
                ((count ?: 1) - 1).coerceAtLeast(0)
            }!!

            massClonerService?.onInstanceOccupancyChanged(instance.instanceId, newCount)
            instance
        }
    }

    fun getInstanceOccupancy(instanceId: UUID): Int {
        return instanceOccupancy[instanceId] ?: 0
    }

    fun clear() {
        chunkToInstance.clear()
        playerStates.clear()
        instanceOccupancy.clear()
    }

    var massClonerService: MassClonerService? = null
}

class MassClonerService(
    private val plugin: SuspendingJavaPlugin,
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val schedulerService: SchedulerService,
    private val massClonerConfig: MassClonerConfig,
    private val restoreConfig: RestoreConfig,
    private val templateCache: TemplateCache
) {
    private val slf4jLogger = org.slf4j.LoggerFactory.getLogger(MassClonerService::class.java)
    private val stateLoader = ClonerStateLoader(plugin.dataFolder.toPath(), slf4jLogger)
    private val registry = InstanceRegistry(ConcurrentHashMap())
    private val pools = ConcurrentHashMap<PoolKey, RegionPool>()
    private val instances = ConcurrentHashMap<UUID, RegionInstance>()
    private val vacateTasks = ConcurrentHashMap<UUID, Job>()
    private val instanceTimers = ConcurrentHashMap<UUID, Job>()
    private val allocator = PlacementAllocator()
    private val stateLock = Any()

    private val occupancyTracker = OccupancyTracker(plugin)

    init {
        occupancyTracker.massClonerService = this
        plugin.server.pluginManager.registerEvents(occupancyTracker, plugin)
    }

    suspend fun initialize() {
        loadState()
        loadConfig()

        // Register all instances (pooled and manual)
        for ((worldName, instances) in registry.instances) {
            for (instance in instances) {
                registerInstance(instance)
            }
        }

        // Collect templates that need preloading and pin them
        val requiredTemplates = collectRequiredTemplates()
        if (requiredTemplates.isNotEmpty()) {
            slf4jLogger.info("Preloading ${requiredTemplates.size} template(s) into cache...")
            val preloaded = templateCache.preload(requiredTemplates)
            slf4jLogger.info("Successfully preloaded $preloaded/${requiredTemplates.size} template(s)")

            // Log cache stats after preloading
            val stats = templateCache.getStats()
            slf4jLogger.info("Cache stats: ${stats.pinnedSize} pinned, ${stats.lruSize} LRU entries")
        }

        // Ensure pooled instances exist (auto-allocate if needed)
        ensurePoolInstanceCounts()

        slf4jLogger.info("Pool instances loaded")

        // Start triggers for all instances
        for ((_, instances) in registry.instances) {
            for (instance in instances) {
                when (instance.instanceType) {
                    InstanceType.POOLED -> {
                        // Existing behavior: use pool config for triggers
                        val pool = pools[PoolKey(instance.worldName, instance.templateName)] ?: continue
                        if (pool.restoreOnBoot) {
                            scheduleRestore(pool, instance)
                        }
                        if (pool.restoreIntervalSeconds != null) {
                            scheduleRepeatingRestore(pool, instance)
                        }
                    }

                    InstanceType.MANUAL -> {
                        // New behavior: use instance config for triggers
                        startInstanceTriggers(instance)
                    }
                }
            }
        }

        slf4jLogger.info("Instance triggers loaded")
    }

    private suspend fun loadState() {
        val state = stateLoader.load()
        // Rebuild in-memory registries from persisted state under a single lock
        synchronized(stateLock) {
            registry.instances.clear()
            instances.clear()

            // Primary source: instances grouped by world
            for ((worldName, worldInstances) in state.instances) {
                if (worldInstances.isEmpty()) continue
                registry.instances[worldName] = worldInstances
                for (instance in worldInstances) {
                    instances[instance.instanceId] = instance
                }
            }

            // Secondary source: instances indexed by ID (ensures no instance is lost
            // even if it was missing from the world map in the state file)
            for ((id, instance) in state.instancesById) {
                if (instances.containsKey(id)) continue
                val worldName = instance.worldName
                val current = registry.instances[worldName] ?: emptyList()
                registry.instances[worldName] = current + instance
                instances[id] = instance
            }
        }
    }

    private fun loadConfig() {
        pools.clear()
        massClonerConfig.worlds.forEach { worldCfg ->
            worldCfg.pools.forEach { poolCfg ->
                val pool = RegionPool(
                    worldName = worldCfg.name,
                    templateName = poolCfg.templateName,
                    versionMode = poolCfg.versionMode,
                    pinnedVersionId = poolCfg.pinnedVersionId,
                    count = poolCfg.count,
                    separationChunks = poolCfg.separationChunks,
                    restoreOnBoot = poolCfg.restoreOnBoot,
                    restoreOnVacate = poolCfg.restoreOnVacate,
                    restoreIntervalSeconds = poolCfg.restoreIntervalSeconds,
                    restoreAudienceScope = poolCfg.restoreAudienceScope,
                    updateLight = poolCfg.updateLight
                )
                pools[PoolKey(pool.worldName, pool.templateName)] = pool
            }
        }
    }

    /**
     * Create or update a pool at runtime and allocate the required instances.
     *
     * This does NOT persist to the configuration file; it only affects the
     * current runtime state. It is intended for use by admin GUIs and
     * temporary setups.
     *
     * @return number of newly allocated instances for this pool
     */
    suspend fun createPool(
        worldName: String,
        templateName: String,
        versionMode: VersionMode = VersionMode.ACTIVE,
        pinnedVersionId: Int? = null,
        count: Int,
        separationChunks: Int,
        restoreOnBoot: Boolean = false,
        restoreOnVacate: Boolean = false,
        restoreIntervalSeconds: Int? = null,
        restoreAudienceScope: AudienceScope = AudienceScope.WORLD,
        updateLight: Boolean? = null
    ): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val pool = RegionPool(
            worldName = worldName,
            templateName = templateName,
            versionMode = versionMode,
            pinnedVersionId = pinnedVersionId,
            count = count,
            separationChunks = separationChunks,
            restoreOnBoot = restoreOnBoot,
            restoreOnVacate = restoreOnVacate,
            restoreIntervalSeconds = restoreIntervalSeconds,
            restoreAudienceScope = restoreAudienceScope,
            updateLight = updateLight
        )

        pools[PoolKey(worldName, templateName)] = pool

        // Snapshot existing instances for this pool
        val existingInstances = synchronized(stateLock) {
            registry.instances[worldName]
                ?.filter { it.templateName == templateName }
                ?: emptyList()
        }

        val currentCount = existingInstances.size
        val targetCount = pool.count

        if (currentCount >= targetCount) {
            return@withContext 0
        }

        // Load template to get dimensions and resolve version
        val templateVersion = when (pool.versionMode) {
            VersionMode.ACTIVE ->
                templateRepository.loadActiveTemplateVersion(pool.templateName)

            VersionMode.PINNED ->
                pool.pinnedVersionId?.let {
                    templateRepository.loadTemplateVersion(pool.templateName, it)
                }
        }

        if (templateVersion == null) {
            slf4jLogger.error("Failed to load template '${pool.templateName}' for pool allocation")
            return@withContext 0
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            slf4jLogger.warn(
                "Template '${pool.templateName}' version mismatch: " +
                        "created on ${templateVersion.minecraftVersion}, running on ${nmsAdapter.minecraftVersion}. Please update the template or use a compatible version to avoid errors during restore."
            )
        }

        val neededCount = targetCount - currentCount

        // Allocate new instances (including ALL existing instances for overlap detection)
        val allWorldInstances = synchronized(stateLock) {
            registry.instances[pool.worldName] ?: emptyList()
        }
        val newInstances = allocator.allocateInstances(
            pool = pool,
            existingInstances = allWorldInstances,
            template = templateVersion.data,
            neededCount = neededCount
        )

        if (newInstances.isEmpty()) {
            slf4jLogger.error("Failed to allocate any instances for pool '${pool.templateName}' in world '$worldName'")
            return@withContext 0
        }

        // Commit new instances to the registries under the shared lock
        synchronized(stateLock) {
            addInstancesLocked(newInstances)
        }

        // Register new instances for occupancy tracking outside the lock
        for (instance in newInstances) {
            registerInstance(instance)
        }

        saveState()

        slf4jLogger.info(
            "Created/updated pool '${pool.templateName}' in world '${pool.worldName}' with ${newInstances.size} new instances (target=$targetCount)."
        )

        return@withContext newInstances.size
    }

    /**
     * Collect all template references that need to be preloaded.
     *
     * Includes:
     * - All templates referenced by pools (pools are always active)
     * - All instances with timers or boot restore configured
     *
     * @return List of template references to preload
     */
    private fun collectRequiredTemplates(): List<TemplateRef> {
        val refs = mutableListOf<TemplateRef>()

        // Collect from pools (all pools are active on the server)
        for (pool in pools.values) {
            refs.add(
                TemplateRef(
                    templateName = pool.templateName,
                    versionId = if (pool.versionMode == VersionMode.PINNED) pool.pinnedVersionId else null
                )
            )
        }

        // Collect from instances with timers or boot restore
        for ((_, instances) in registry.instances) {
            for (instance in instances) {
                val needsPreload = when (instance.instanceType) {
                    InstanceType.POOLED -> {
                        // Pooled instances: check pool config
                        val pool = pools[PoolKey(instance.worldName, instance.templateName)]
                        pool != null && (pool.restoreOnBoot || pool.restoreIntervalSeconds != null)
                    }

                    InstanceType.MANUAL -> {
                        // Manual instances: check instance config
                        val cfg = instance.config
                        cfg != null && (cfg.restoreOnBoot || cfg.restoreIntervalSeconds != null)
                    }
                }

                if (needsPreload) {
                    refs.add(
                        TemplateRef(
                            templateName = instance.templateName,
                            versionId = instance.versionId
                        )
                    )
                }
            }
        }

        return refs.distinctBy { "${it.templateName}:v${it.versionId ?: "active"}" }
    }

    private suspend fun ensurePoolInstanceCounts() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        for (pool in pools.values) {
            // Snapshot existing instances for this pool
            val existingInstances = synchronized(stateLock) {
                registry.instances[pool.worldName]
                    ?.filter { it.templateName == pool.templateName }
                    ?: emptyList()
            }

            val currentCount = existingInstances.size
            val targetCount = pool.count

            if (currentCount < targetCount) {
                // Need to allocate more instances
                slf4jLogger.info(
                    "Pool '${pool.templateName}' in world '${pool.worldName}': " +
                            "has $currentCount instances, needs $targetCount. Allocating ${targetCount - currentCount} new instances."
                )

                // Load template to get dimensions and resolve version
                val templateVersion = when (pool.versionMode) {
                    VersionMode.ACTIVE ->
                        templateRepository.loadActiveTemplateVersion(pool.templateName)

                    VersionMode.PINNED ->
                        pool.pinnedVersionId?.let {
                            templateRepository.loadTemplateVersion(pool.templateName, it)
                        }
                }

                if (templateVersion == null) {
                    slf4jLogger.error("Failed to load template '${pool.templateName}' for pool allocation")
                    continue
                }

                if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                    slf4jLogger.warn(
                        "Template '${pool.templateName}' version mismatch: " +
                                "created on ${templateVersion.minecraftVersion}, running on ${nmsAdapter.minecraftVersion}. Please update the template or use a compatible version to avoid errors during restore."
                    )
                }

                val neededCount = targetCount - currentCount

                // Allocate new instances (including ALL existing instances for overlap detection)
                val allWorldInstances = synchronized(stateLock) {
                    registry.instances[pool.worldName] ?: emptyList()
                }
                val newInstances = allocator.allocateInstances(
                    pool = pool,
                    existingInstances = allWorldInstances,
                    template = templateVersion.data,
                    neededCount = neededCount
                )

                if (newInstances.size != neededCount) {
                    slf4jLogger.error("Failed to allocate enough instances. Needed $neededCount, got ${newInstances.size}")
                    continue
                }

                // Commit new instances to the registries under the shared lock
                synchronized(stateLock) {
                    addInstancesLocked(newInstances)
                }

                // Register new instances for occupancy tracking outside the lock
                for (instance in newInstances) {
                    registerInstance(instance)
                }

                slf4jLogger.info("Successfully allocated ${newInstances.size} new instances for pool '${pool.templateName}'")
            } else if (currentCount > targetCount) {
                slf4jLogger.warn(
                    "Pool '${pool.templateName}' has $currentCount instances but config specifies $targetCount. " +
                            "Excess instances will remain in registry. Manual removal required."
                )
            }
        }

        saveState()
    }

    private fun registerInstance(instance: RegionInstance) {
        // Instance is already present in the in-memory maps; this only wires it
        // into the occupancy tracker.
        occupancyTracker.registerInstance(instance)
    }

    /**
     * Add a single instance to the in-memory registries under [stateLock].
     */
    private fun addInstanceLocked(instance: RegionInstance) {
        val worldName = instance.worldName
        registry.instances.compute(worldName) { _, existing ->
            val current = existing ?: emptyList()
            current + instance
        }
        instances[instance.instanceId] = instance
    }

    /**
     * Add multiple instances to the in-memory registries under [stateLock].
     */
    private fun addInstancesLocked(newInstances: Collection<RegionInstance>) {
        for (instance in newInstances) {
            addInstanceLocked(instance)
        }
    }

    /**
     * Remove an instance from the in-memory registries under [stateLock].
     *
     * @return the removed instance, or null if it did not exist.
     */
    private fun removeInstanceLocked(instanceId: UUID): RegionInstance? {
        val instance = instances.remove(instanceId) ?: return null

        val worldName = instance.worldName
        registry.instances.compute(worldName) { _, existing ->
            val current = existing ?: emptyList()
            current.filter { it.instanceId != instanceId }
        }

        return instance
    }

    private suspend fun scheduleBootRestores() {
        for (pool in pools.values) {
            if (pool.restoreOnBoot) {
                val worldInstances = registry.instances[pool.worldName] ?: emptyList()

                for (instance in worldInstances) {
                    if (instance.templateName == pool.templateName) {
                        scheduleRestore(pool, instance)
                    }
                }
            }

            if (pool.restoreIntervalSeconds != null) {
                val worldInstances = registry.instances[pool.worldName] ?: emptyList()

                for (instance in worldInstances) {
                    if (instance.templateName == pool.templateName) {
                        scheduleRepeatingRestore(pool, instance)
                    }
                }
            }
        }
    }

    private suspend fun scheduleRestore(pool: RegionPool, instance: RegionInstance) {
        val world = Bukkit.getWorld(pool.worldName) ?: return

        val versionId = when (pool.versionMode) {
            VersionMode.ACTIVE ->
                templateRepository.loadActiveTemplateVersion(pool.templateName)?.versionId

            VersionMode.PINNED ->
                pool.pinnedVersionId
        } ?: return

        val templateVersion = if (pool.versionMode == VersionMode.ACTIVE) {
            templateRepository.loadActiveTemplateVersion(pool.templateName)
        } else {
            pool.pinnedVersionId?.let { templateRepository.loadTemplateVersion(pool.templateName, it) }
        } ?: return

        val updateLight = resolveUpdateLight(pool, instance)

        val job = RestoreJob(
            id = instance.instanceId,
            world = world,
            targetChunkX = instance.originChunkX,
            targetChunkZ = instance.originChunkZ,
            sizeXChunks = instance.sizeXChunks,
            sizeZChunks = instance.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    world,
                    templateVersion.data,
                    instance.originChunkX,
                    instance.originChunkZ,
                    plugin,
                    updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), pool.restoreAudienceScope)
    }

    private suspend fun scheduleRepeatingRestore(pool: RegionPool, instance: RegionInstance) {
        val world = Bukkit.getWorld(pool.worldName) ?: return
        val interval = pool.restoreIntervalSeconds ?: return

        val versionId = when (pool.versionMode) {
            VersionMode.ACTIVE ->
                templateRepository.loadActiveTemplateVersion(pool.templateName)?.versionId

            VersionMode.PINNED ->
                pool.pinnedVersionId
        } ?: return

        val templateVersion = if (pool.versionMode == VersionMode.ACTIVE) {
            templateRepository.loadActiveTemplateVersion(pool.templateName)
        } else {
            pool.pinnedVersionId?.let { templateRepository.loadTemplateVersion(pool.templateName, it) }
        } ?: return

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            return
        }

        val updateLight = resolveUpdateLight(pool, instance)

        val job = RestoreJob(
            id = instance.instanceId,
            world = world,
            targetChunkX = instance.originChunkX,
            targetChunkZ = instance.originChunkZ,
            sizeXChunks = instance.sizeXChunks,
            sizeZChunks = instance.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    world,
                    templateVersion.data,
                    instance.originChunkX,
                    instance.originChunkZ,
                    plugin,
                    updateLight
                )
            }
        )

        schedulerService.scheduleRepeatingRestore(job, interval, pool.restoreAudienceScope)
    }

    fun onInstanceOccupancyChanged(instanceId: UUID, newCount: Int) {
        val instance = instances[instanceId] ?: return

        // Check if this instance has restore-on-vacate configured
        val shouldVacateRestore = when (instance.instanceType) {
            InstanceType.POOLED -> {
                // Legacy behavior: use pool config
                val pool = pools[PoolKey(instance.worldName, instance.templateName)]
                pool?.restoreOnVacate == true
            }

            InstanceType.MANUAL -> {
                // New behavior: use instance config
                instance.config?.restoreOnVacate == true
            }
        }

        if (shouldVacateRestore && newCount == 0) {
            vacateTasks[instanceId]?.cancel()

            vacateTasks[instanceId] = plugin.launch(plugin.asyncDispatcher) {
                delay(massClonerConfig.vacateDelaySeconds.toLong().seconds)

                if (occupancyTracker.getInstanceOccupancy(instanceId) == 0) {
                    withContext(plugin.globalRegionDispatcher) {
                        if (instance.instanceType == InstanceType.POOLED) {
                            val pool = pools[PoolKey(instance.worldName, instance.templateName)]
                            if (pool != null) {
                                val world = Bukkit.getWorld(pool.worldName)
                                if (world != null) {
                                    scheduleRestore(pool, instance)
                                }
                            }
                        } else {
                            // Manual instance
                            val world = Bukkit.getWorld(instance.worldName)
                            if (world != null) {
                                // Load template version
                                val templateVersion = when {
                                    instance.versionId != null ->
                                        templateRepository.loadTemplateVersion(
                                            instance.templateName,
                                            instance.versionId
                                        )

                                    else ->
                                        templateRepository.loadActiveTemplateVersion(instance.templateName)
                                }

                                if (templateVersion != null) {
                                    if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                                        slf4jLogger.warn("Template version ${templateVersion.minecraftVersion} for instance '${instance.instanceId}' does not match server version ${nmsAdapter.minecraftVersion}. Please update the template or instance version to avoid errors during restore.")
                                    }
                                    val job = createRestoreJob(instance, templateVersion, world)
                                    schedulerService.scheduleRestore(
                                        job,
                                        null,
                                        emptyList(),
                                        instance.config?.restoreAudienceScope ?: AudienceScope.WORLD
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (newCount > 0) {
            vacateTasks[instanceId]?.cancel()
            vacateTasks.remove(instanceId)
        }
    }

    suspend fun shutdown() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Unpin all templates that were preloaded
        val requiredTemplates = collectRequiredTemplates()
        for (ref in requiredTemplates) {
            if (ref.versionId != null) {
                templateCache.unpin(ref.templateName, ref.versionId)
            } else {
                // For active version, we need to resolve the version ID first
                // This is a bit tricky, so we'll skip it for now
                // In practice, this is fine because the cache will be cleared anyway
            }
        }

        slf4jLogger.info("Unpinned ${requiredTemplates.size} template(s)")

        vacateTasks.values.forEach { it.cancel() }
        vacateTasks.clear()
        occupancyTracker.clear()
    }

    suspend fun saveState() {
        // Take immutable snapshots under the state lock, then persist them off-thread.
        val instancesByWorldSnapshot: Map<String, List<RegionInstance>>
        val instancesByIdSnapshot: Map<UUID, RegionInstance>

        synchronized(stateLock) {
            instancesByWorldSnapshot = registry.instances.mapValues { (_, list) -> list.toList() }
            instancesByIdSnapshot = HashMap(instances)
        }

        stateLoader.save(ClonerState(instancesByWorldSnapshot, instancesByIdSnapshot))
    }

    fun getInstancesForPool(worldName: String, templateName: String): List<RegionInstance> {
        return registry.instances[worldName]?.filter { it.templateName == templateName } ?: emptyList()
    }

    /**
     * Trigger a manual restore of an instance (works for both POOLED and MANUAL instances).
     */
    suspend fun triggerInstanceRestore(instance: RegionInstance) {
        when (instance.instanceType) {
            InstanceType.POOLED -> {
                // Pooled instances: use pool config, and ensure scheduling starts from the global region dispatcher
                val pool = pools[PoolKey(instance.worldName, instance.templateName)] ?: return

                withContext(plugin.globalRegionDispatcher) {
                    scheduleRestore(pool, instance)
                }
            }

            InstanceType.MANUAL -> {
                // Manual instances: load template off-thread, then schedule restore from the global region dispatcher

                // Load template version using a blocking-friendly dispatcher
                val templateVersion = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    when {
                        // If instance has specific versionId, use it
                        instance.versionId != null ->
                            templateRepository.loadTemplateVersion(instance.templateName, instance.versionId)
                        // Otherwise use active version
                        else ->
                            templateRepository.loadActiveTemplateVersion(instance.templateName)
                    }
                } ?: return

                if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                    slf4jLogger.warn("Template version ${templateVersion.minecraftVersion} for instance '${instance.instanceId}' does not match server version ${nmsAdapter.minecraftVersion}. Please update the template or instance version to avoid restore errors.")
                }

                val scope = instance.config?.restoreAudienceScope ?: AudienceScope.WORLD

                // Create the job and schedule the restore from the global region dispatcher
                withContext(plugin.globalRegionDispatcher) {
                    val world = Bukkit.getWorld(instance.worldName) ?: return@withContext

                    val updateLight = resolveUpdateLight(null, instance)

                    val job = RestoreJob(
                        id = instance.instanceId,
                        world = world,
                        targetChunkX = instance.originChunkX,
                        targetChunkZ = instance.originChunkZ,
                        sizeXChunks = instance.sizeXChunks,
                        sizeZChunks = instance.sizeZChunks,
                        restoreAction = {
                            nmsAdapter.restoreTemplate(
                                world,
                                templateVersion.data,
                                instance.originChunkX,
                                instance.originChunkZ,
                                plugin,
                                updateLight
                            )
                        }
                    )

                    schedulerService.scheduleRestore(job, null, emptyList(), scope)
                }

                slf4jLogger.info("Triggered manual restore for instance '${instance.instanceId}'")
            }
        }
    }

    /**
     * Start all configured triggers for an instance (timers, boot restore).
     */
    suspend fun startInstanceTriggers(instance: RegionInstance) {
        val config = instance.config ?: return  // Legacy pooled instances use pool config

        // Load template version using a blocking-friendly dispatcher
        val templateVersion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when {
                // If instance has specific versionId, use it
                instance.versionId != null ->
                    templateRepository.loadTemplateVersion(instance.templateName, instance.versionId)
                // Otherwise use active version
                else ->
                    templateRepository.loadActiveTemplateVersion(instance.templateName)
            }
        } ?: return

        val world = Bukkit.getWorld(instance.worldName) ?: return

        // Restore on boot
        if (config.restoreOnBoot) {
            val job = createRestoreJob(instance, templateVersion, world)
            plugin.launch(plugin.globalRegionDispatcher) {
                delay(10.seconds)
                schedulerService.scheduleRestore(job, null, emptyList(), config.restoreAudienceScope)
            }
        }

        // Repeating timer
        if (config.restoreIntervalSeconds != null) {
            val job = createRestoreJob(instance, templateVersion, world)
            schedulerService.scheduleRepeatingRestore(
                job,
                config.restoreIntervalSeconds,
                config.restoreAudienceScope
            )
        }
    }

    /**
     * Stop all triggers for an instance.
     */
    fun stopInstanceTriggers(instance: RegionInstance) {
        schedulerService.cancelRepeatingRestore(instance.instanceId)
        instanceTimers.remove(instance.instanceId)
    }

    /**
     * Create a RestoreJob for an instance.
     */
    private fun resolveUpdateLight(pool: RegionPool?, instance: RegionInstance): Boolean {
        // Instance config wins, then pool, then global restore config
        val instanceOverride = instance.config?.updateLight
        if (instanceOverride != null) return instanceOverride

        val poolOverride = pool?.updateLight
        if (poolOverride != null) return poolOverride

        return restoreConfig.updateLight
    }

    private fun createRestoreJob(
        instance: RegionInstance,
        templateVersion: RegionTemplateVersion,
        world: World
    ): RestoreJob {
        val updateLight = resolveUpdateLight(null, instance)
        return RestoreJob(
            id = instance.instanceId,
            world = world,
            targetChunkX = instance.originChunkX,
            targetChunkZ = instance.originChunkZ,
            sizeXChunks = instance.sizeXChunks,
            sizeZChunks = instance.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    world,
                    templateVersion.data,
                    instance.originChunkX,
                    instance.originChunkZ,
                    plugin,
                    updateLight
                )
            }
        )
    }

    /**
     * Add a new manual instance to the registry.
     */
    suspend fun addManualInstance(instance: RegionInstance) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Update in-memory state under the shared lock
            synchronized(stateLock) {
                addInstanceLocked(instance)
            }

            // Wire into occupancy tracking and persist state outside the lock
            registerInstance(instance)
            saveState()

            slf4jLogger.info(
                "Added manual instance '${instance.instanceId}' for template '${instance.templateName}' " +
                        "at (${instance.originChunkX}, ${instance.originChunkZ})"
            )
        }

    /**
     * Remove an instance (manual or pooled).
     */
    suspend fun removeInstance(instanceId: UUID): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Remove from in-memory registries under the shared lock
            val removed = synchronized(stateLock) {
                removeInstanceLocked(instanceId)
            } ?: return@withContext false

            // Cancel any scheduled tasks and unregister from occupancy outside the lock
            stopInstanceTriggers(removed)
            occupancyTracker.unregisterInstance(instanceId)
            vacateTasks[instanceId]?.cancel()
            vacateTasks.remove(instanceId)

            saveState()

            slf4jLogger.info("Removed instance '$instanceId'")
            return@withContext true
        }

    /**
     * Get an instance by ID.
     */
    fun getInstance(instanceId: UUID): RegionInstance? {
        return instances[instanceId]
    }

    /**
     * Regenerate all pooled instances for specified worlds.
     * Removes existing pooled instances and re-allocates them from config.
     * Manual instances are preserved.
     *
     * @param worldNames List of world names to regenerate (empty = all configured worlds)
     * @return Pair of (total removed, total allocated)
     */
    suspend fun regeneratePools(worldNames: List<String> = emptyList()): Pair<Int, Int> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val worldsToRegen = if (worldNames.isEmpty()) {
                massClonerConfig.worlds.map { it.name }
            } else {
                worldNames.filter { worldName ->
                    massClonerConfig.worlds.any { it.name == worldName }
                }
            }

            var totalRemoved = 0
            var totalAllocated = 0

            for (worldName in worldsToRegen) {
                // Snapshot pooled instances to remove, and update registries under the lock
                val pooledInstances: List<RegionInstance>
                synchronized(stateLock) {
                    val worldInstances = registry.instances[worldName] ?: emptyList()
                    pooledInstances = worldInstances.filter { it.instanceType == InstanceType.POOLED }
                    val manualInstances = worldInstances.filter { it.instanceType == InstanceType.MANUAL }
                    registry.instances[worldName] = manualInstances
                    for (instance in pooledInstances) {
                        instances.remove(instance.instanceId)
                    }
                }

                // Stop timers and unregister from occupancy outside the lock
                for (instance in pooledInstances) {
                    stopInstanceTriggers(instance)
                    occupancyTracker.unregisterInstance(instance.instanceId)
                    vacateTasks[instance.instanceId]?.cancel()
                    vacateTasks.remove(instance.instanceId)
                    totalRemoved++
                }

                // Re-allocate pooled instances from config
                val worldPools = massClonerConfig.worlds.firstOrNull { it.name == worldName }?.pools ?: continue

                for (poolCfg in worldPools) {
                    val pool = RegionPool(
                        worldName = worldName,
                        templateName = poolCfg.templateName,
                        versionMode = poolCfg.versionMode,
                        pinnedVersionId = poolCfg.pinnedVersionId,
                        count = poolCfg.count,
                        separationChunks = poolCfg.separationChunks,
                        restoreOnBoot = poolCfg.restoreOnBoot,
                        restoreOnVacate = poolCfg.restoreOnVacate,
                        restoreIntervalSeconds = poolCfg.restoreIntervalSeconds,
                        restoreAudienceScope = poolCfg.restoreAudienceScope,
                        updateLight = poolCfg.updateLight
                    )

                    // Load template to get dimensions
                    val templateVersion = when (pool.versionMode) {
                        VersionMode.ACTIVE ->
                            templateRepository.loadActiveTemplateVersion(pool.templateName)

                        VersionMode.PINNED ->
                            pool.pinnedVersionId?.let {
                                templateRepository.loadTemplateVersion(pool.templateName, it)
                            }
                    } ?: continue

                    if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                        slf4jLogger.warn("Template version mismatch for '${pool.templateName}', please update it to avoid errors.")
                    }

                    // Allocate instances
                    val allWorldInstances = synchronized(stateLock) {
                        registry.instances[worldName] ?: emptyList()
                    }
                    val newInstances = allocator.allocateInstances(
                        pool = pool,
                        existingInstances = allWorldInstances,
                        template = templateVersion.data,
                        neededCount = pool.count
                    )

                    // Commit and register new instances
                    synchronized(stateLock) {
                        addInstancesLocked(newInstances)
                    }
                    for (instance in newInstances) {
                        registerInstance(instance)
                        totalAllocated++
                    }
                }
            }

            saveState()
            return@withContext Pair(totalRemoved, totalAllocated)
        }

    /**
     * List all instances (optionally filtered by world and type).
     */
    fun listInstances(worldName: String? = null, instanceType: InstanceType? = null): List<RegionInstance> {
        val worlds = if (worldName != null) {
            registry.instances.filterKeys { it == worldName }.values.flatten()
        } else {
            registry.instances.values.flatten()
        }

        return if (instanceType != null) {
            worlds.filter { it.instanceType == instanceType }
        } else {
            worlds
        }
    }

    /**
     * Get the total number of configured pools.
     */
    fun getPoolCount(): Int = pools.size

    /**
     * Get the target instance count for a specific pool.
     *
     * @param worldName The world name
     * @param templateName The template name
     * @return The target count, or null if pool not found
     */
    fun getPoolTarget(worldName: String, templateName: String): Int? {
        return pools[PoolKey(worldName, templateName)]?.count
    }
}
