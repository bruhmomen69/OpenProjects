package bruh.regionrestore.timer

import bruh.regionrestore.config.EntityKillerConfig
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.bukkit.EntityEffect
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType

/**
 * Service responsible for killing entities within a region before restore.
 * Supports whitelist and blacklist modes for entity type filtering.
 *
 * Entity operations are distributed across chunk region dispatchers for optimal
 * performance in Folia environments. Each chunk's entities are processed on their
 * respective region thread concurrently.
 */
class EntityKiller(
    private val config: EntityKillerConfig,
    private val plugin: SuspendingJavaPlugin
) {
    /**
     * Kill all entities within the specified bounds that match the filter criteria.
     * Entities are grouped by chunk and processed concurrently on their respective
     * region dispatchers for optimal Folia performance.
     *
     * @param world The world to operate in
     * @param minBlockX Minimum block X coordinate
     * @param maxBlockX Maximum block X coordinate
     * @param minBlockZ Minimum block Z coordinate
     * @param maxBlockZ Maximum block Z coordinate
     * @return The number of entities killed
     */
    suspend fun killEntitiesInRegion(
        world: World,
        minBlockX: Int,
        maxBlockX: Int,
        minBlockZ: Int,
        maxBlockZ: Int
    ): Int {
        if (!config.enabled) {
            return 0
        }

        val entityTypes = config.entityTypes.mapNotNull { typeName ->
            try {
                EntityType.valueOf(typeName.uppercase())
            } catch (e: IllegalArgumentException) {
                plugin.slF4JLogger.warn("Unknown entity type in entityKiller config: $typeName")
                null
            }
        }

        if (entityTypes.isEmpty()) {
            plugin.slF4JLogger.warn("EntityKiller has no valid entity types configured, skipping")
            return 0
        }

        // Get all entities in the region (this must be done on the global region dispatcher)
        val entitiesInRegion = getEntitiesInRegion(world, minBlockX, maxBlockX, minBlockZ, maxBlockZ, entityTypes)

        if (entitiesInRegion.isEmpty()) {
            return 0
        }

        // Group entities by chunk for parallel processing
        val entitiesByChunk = entitiesInRegion.groupBy { entity ->
            Pair(entity.location.chunk.x, entity.location.chunk.z)
        }

        // Process each chunk's entities on its respective region dispatcher concurrently
        val totalKilled = coroutineScope {
            val jobs = entitiesByChunk.map { (chunkCoords, entities) ->
                val (chunkX, chunkZ) = chunkCoords
                async(plugin.regionDispatcher(world, chunkX, chunkZ)) {
                    var chunkKilled = 0
                    for (entity in entities) {
                        try {
                            killEntity(entity)
                            chunkKilled++
                        } catch (e: Exception) {
                            plugin.slF4JLogger.debug("Failed to kill entity ${entity.type} at ${entity.location}: ${e.message}")
                        }
                    }
                    chunkKilled
                }
            }
            jobs.awaitAll().sum()
        }

        if (totalKilled > 0) {
            plugin.slF4JLogger.info("EntityKiller: Killed $totalKilled entities in region (${entitiesByChunk.size} chunks)")
        }

        return totalKilled
    }

    /**
     * Get all entities within the specified bounds that match the filter criteria.
     * This is run on the global region dispatcher since world.entities is a global operation.
     */
    private suspend fun getEntitiesInRegion(
        world: World,
        minBlockX: Int,
        maxBlockX: Int,
        minBlockZ: Int,
        maxBlockZ: Int,
        entityTypes: List<EntityType>
    ): List<Entity> = withContext(plugin.globalRegionDispatcher) {
        world.entities.filter { entity ->
            isEntityInBounds(entity, minBlockX, maxBlockX, minBlockZ, maxBlockZ) &&
                shouldKillEntity(entity.type, entityTypes)
        }
    }

    /**
     * Kill a single entity, playing death effect and removing passengers.
     * Must be called on the region thread that owns the entity's chunk.
     */
    private fun killEntity(entity: Entity) {
        // Remove passengers first if any
        entity.passengers.forEach { passenger ->
            try {
                passenger.remove()
            } catch (e: Exception) {
                // Ignore errors removing passengers
            }
        }

        entity.remove()
    }

    /**
     * Check if an entity should be killed based on whitelist/blacklist mode.
     */
    private fun shouldKillEntity(entityType: EntityType, configuredTypes: List<EntityType>): Boolean {
        return if (config.whitelistMode) {
            entityType in configuredTypes
        } else {
            entityType !in configuredTypes
        }
    }

    /**
     * Check if an entity is within the specified bounds.
     * Uses entity location for the check.
     */
    private fun isEntityInBounds(
        entity: Entity,
        minBlockX: Int,
        maxBlockX: Int,
        minBlockZ: Int,
        maxBlockZ: Int
    ): Boolean {
        val loc = entity.location
        val x = loc.blockX
        val z = loc.blockZ
        return x in minBlockX..maxBlockX && z in minBlockZ..maxBlockZ
    }
}
