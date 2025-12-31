package bruh.regionrestore.cloner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import bruh.regionrestore.nms.RegionTemplate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Allocates non-overlapping instance placements in chunk coordinates.
 *
 * Per PLAN.md §11.2:
 * - Work in chunk coordinates
 * - Reserve rectangles in a simple allocator grid
 * - Respect template dimensions, separationChunks, and base offsets
 * - Be deterministic when possible
 * - Use stored cloner-state.yml first, only allocate new positions when missing
 */
class PlacementAllocator {
    private val logger = LoggerFactory.getLogger(PlacementAllocator::class.java)

    /**
     * Allocate new instance positions for a pool.
     *
     * @param pool The region pool requiring instances
     * @param existingInstances All existing instances in the world (to avoid overlaps)
     * @param template The template to get dimensions from
     * @param neededCount Number of new instances to allocate
     * @return List of new RegionInstance objects with allocated positions
     */
    suspend fun allocateInstances(
        pool: RegionPool,
        existingInstances: List<RegionInstance>,
        template: RegionTemplate,
        neededCount: Int
    ): List<RegionInstance> = withContext(Dispatchers.IO) {
        val newInstances = mutableListOf<RegionInstance>()
        val templateWidth = template.sizeXChunks
        val templateDepth = template.sizeZChunks
        val separation = pool.separationChunks
        val worldName = pool.worldName

        logger.info("Allocating $neededCount instances for template '${pool.templateName}' " +
                   "(${templateWidth}x${templateDepth} chunks) in world '$worldName' " +
                   "with $separation chunk separation")

        // Scan positions starting from (0,0) for deterministic placement
        var candidateX = 0
        var candidateZ = 0
        var allocated = 0

        while (allocated < neededCount) {
            // Calculate bounds including separation buffer
            val minX = candidateX
            val minZ = candidateZ
            val maxX = candidateX + templateWidth - 1
            val maxZ = candidateZ + templateDepth - 1

            // Check if this placement would overlap with existing instances
            if (!wouldOverlap(minX, minZ, maxX, maxZ, existingInstances, separation)) {
                // Found a valid placement
                val newInstance = RegionInstance.create(
                    instanceId = java.util.UUID.randomUUID(),
                    worldName = worldName,
                    templateName = pool.templateName,
                    versionId = null, // Will be resolved during restore
                    originChunkX = candidateX,
                    originChunkZ = candidateZ,
                    sizeXChunks = templateWidth,
                    sizeZChunks = templateDepth
                )

                newInstances.add(newInstance)
                allocated++

                logger.info("Allocated instance ${newInstance.instanceId} at chunk ($candidateX, $candidateZ)")
            }

            // Move to next candidate position
            // Advance by template width + separation to reduce search space
            candidateX += templateWidth + separation

            // Reset to next row if we've gone too far (arbitrary limit)
            if (candidateX > 1000) {
                candidateX = 0
                candidateZ += templateDepth + separation
            }
        }

        logger.info("Successfully allocated ${newInstances.size} instances")
        newInstances
    }

    /**
     * Check if a placement would overlap with existing instances.
     * Accounts for template size + separationChunks buffer on all sides.
     *
     * @param minX Candidate minimum chunk X
     * @param minZ Candidate minimum chunk Z
     * @param maxX Candidate maximum chunk X
     * @param maxZ Candidate maximum chunk Z
     * @param existingInstances Existing instances to check against
     * @param separationChunks Required buffer between instances
     * @return true if placement would overlap, false otherwise
     */
    private fun wouldOverlap(
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        existingInstances: List<RegionInstance>,
        separationChunks: Int
    ): Boolean {
        for (existing in existingInstances) {
            // Calculate existing instance bounds including separation
            val existingMinX = existing.originChunkX - separationChunks
            val existingMaxX = existing.originChunkX + existing.sizeXChunks - 1 + separationChunks
            val existingMinZ = existing.originChunkZ - separationChunks
            val existingMaxZ = existing.originChunkZ + existing.sizeZChunks - 1 + separationChunks

            // Check for rectangle overlap
            val overlapsX = minX <= existingMaxX && maxX >= existingMinX
            val overlapsZ = minZ <= existingMaxZ && maxZ >= existingMinZ

            if (overlapsX && overlapsZ) {
                return true
            }
        }

        return false
    }
}
