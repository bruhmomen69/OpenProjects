package bruh.regionrestore.cloner

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.UUID

/**
 * Complete state for MassClonerService persistence.
 * Contains all instances organized by world.
 */
@ConfigSerializable
data class ClonerState(
    val instances: Map<String, List<RegionInstance>> = emptyMap(),
    val instancesById: Map<UUID, RegionInstance> = emptyMap()
) {
    companion object {
        /**
         * Create empty state.
         */
        fun empty() = ClonerState(emptyMap(), emptyMap())
    }
}
