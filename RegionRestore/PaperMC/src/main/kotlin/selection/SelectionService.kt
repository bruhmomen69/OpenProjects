package bruh.regionrestore.selection

import org.bukkit.Location
import org.bukkit.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a player's region selection with two corner positions.
 */
data class Selection(
    val world: World,
    val pos1: Location,
    val pos2: Location
) {
    /** Minimum X coordinate (block). */
    val minX: Int get() = minOf(pos1.blockX, pos2.blockX)

    /** Maximum X coordinate (block). */
    val maxX: Int get() = maxOf(pos1.blockX, pos2.blockX)

    /** Minimum Z coordinate (block). */
    val minZ: Int get() = minOf(pos1.blockZ, pos2.blockZ)

    /** Maximum Z coordinate (block). */
    val maxZ: Int get() = maxOf(pos1.blockZ, pos2.blockZ)

    /** Minimum chunk X coordinate. */
    val minChunkX: Int get() = minX shr 4

    /** Maximum chunk X coordinate. */
    val maxChunkX: Int get() = maxX shr 4

    /** Minimum chunk Z coordinate. */
    val minChunkZ: Int get() = minZ shr 4

    /** Maximum chunk Z coordinate. */
    val maxChunkZ: Int get() = maxZ shr 4

    /** Width in blocks. */
    val width: Int get() = maxX - minX + 1

    /** Length in blocks. */
    val length: Int get() = maxZ - minZ + 1

    /** Width in chunks. */
    val chunkWidth: Int get() = maxChunkX - minChunkX + 1

    /** Length in chunks. */
    val chunkLength: Int get() = maxChunkZ - minChunkZ + 1
}

/**
 * Represents a partial selection where only one position is set.
 */
data class PartialSelection(
    val world: World,
    val pos1: Location? = null,
    val pos2: Location? = null
) {
    /**
     * Returns true if both positions are set and valid (same world).
     */
    fun isComplete(): Boolean = pos1 != null && pos2 != null

    /**
     * Converts to a complete Selection if both positions are set.
     */
    fun toSelection(): Selection? {
        if (!isComplete()) return null
        return Selection(world, pos1!!, pos2!!)
    }
}

/**
 * Service for managing player region selections.
 * Thread-safe for concurrent access.
 */
class SelectionService {
    private val selections = ConcurrentHashMap<UUID, PartialSelection>()

    /**
     * Sets position 1 for a player's selection.
     *
     * @param playerUuid The player's UUID
     * @param location The location to set as position 1
     * @return The updated partial selection
     */
    fun setPos1(playerUuid: UUID, location: Location): PartialSelection {
        val world = location.world
        val existing = selections[playerUuid]

        val newSelection = if (existing == null || existing.world != world) {
            // New selection or world changed - reset pos2
            PartialSelection(world, pos1 = location, pos2 = null)
        } else {
            // Same world - keep pos2
            existing.copy(pos1 = location)
        }

        selections[playerUuid] = newSelection
        return newSelection
    }

    /**
     * Sets position 2 for a player's selection.
     *
     * @param playerUuid The player's UUID
     * @param location The location to set as position 2
     * @return The updated partial selection
     */
    fun setPos2(playerUuid: UUID, location: Location): PartialSelection {
        val world = location.world
        val existing = selections[playerUuid]

        val newSelection = if (existing == null || existing.world != world) {
            // New selection or world changed - reset pos1
            PartialSelection(world, pos1 = null, pos2 = location)
        } else {
            // Same world - keep pos1
            existing.copy(pos2 = location)
        }

        selections[playerUuid] = newSelection
        return newSelection
    }

    /**
     * Gets the current partial selection for a player.
     *
     * @param playerUuid The player's UUID
     * @return The partial selection, or null if none exists
     */
    fun getPartialSelection(playerUuid: UUID): PartialSelection? = selections[playerUuid]

    /**
     * Gets the complete selection for a player if both positions are set.
     *
     * @param playerUuid The player's UUID
     * @return The complete selection, or null if incomplete
     */
    fun getSelection(playerUuid: UUID): Selection? = selections[playerUuid]?.toSelection()

    /**
     * Clears the selection for a player.
     *
     * @param playerUuid The player's UUID
     */
    fun clearSelection(playerUuid: UUID) {
        selections.remove(playerUuid)
    }

    /**
     * Checks if a player has a complete selection.
     *
     * @param playerUuid The player's UUID
     * @return true if both positions are set
     */
    fun hasCompleteSelection(playerUuid: UUID): Boolean = 
        selections[playerUuid]?.isComplete() == true
}
