package bruh.zchat.utils.menuapi.configurable.config

import com.cryptomorin.xseries.XMaterial
import org.bukkit.inventory.ItemStack
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration for a drag slot where players can place items.
 */
@ConfigSerializable
data class DragSlotConfig(
    @Comment("Slot position in the menu (0-indexed)")
    val slot: Int = 0,

    @Comment("Default item to display when the slot is empty")
    val defaultItem: ItemConfig? = null,

    @Comment("Validator configuration for items placed in this slot")
    val validator: DragSlotValidatorConfig? = null
)

/**
 * Configuration for validating items placed in a drag slot.
 * All conditions are optional and combined with AND logic.
 */
@ConfigSerializable
data class DragSlotValidatorConfig(
    @Comment("List of allowed materials (if set, only these materials are accepted)")
    val allowedMaterials: List<String>? = null,

    @Comment("List of blocked materials (if set, these materials are rejected)")
    val blockedMaterials: List<String>? = null,

    @Comment("Minimum stack amount required")
    val minAmount: Int? = null,

    @Comment("Maximum stack amount allowed")
    val maxAmount: Int? = null,

    @Comment("Whether the item must have a custom display name")
    val requireName: Boolean = false,

    @Comment("Whether the item must have lore")
    val requireLore: Boolean = false
) {
    /**
     * Validates an ItemStack against this configuration.
     *
     * @param item The item to validate
     * @return true if the item passes all validation checks
     */
    fun validate(item: ItemStack): Boolean {
        val materialName = item.type.name

        // Check allowed materials
        if (allowedMaterials != null) {
            val allowed = allowedMaterials.any { allowed ->
                materialName.equals(allowed, ignoreCase = true) ||
                    XMaterial.matchXMaterial(allowed).map { it.parseMaterial()?.name }
                        .orElse(null) == materialName
            }
            if (!allowed) return false
        }

        // Check blocked materials
        if (blockedMaterials != null) {
            val blocked = blockedMaterials.any { blockedMat ->
                materialName.equals(blockedMat, ignoreCase = true) ||
                    XMaterial.matchXMaterial(blockedMat).map { it.parseMaterial()?.name }
                        .orElse(null) == materialName
            }
            if (blocked) return false
        }

        // Check amount constraints
        minAmount?.let { min ->
            if (item.amount < min) return false
        }

        maxAmount?.let { max ->
            if (item.amount > max) return false
        }

        // Check name requirement
        if (requireName) {
            if (!item.hasItemMeta() || !item.itemMeta.hasDisplayName()) {
                return false
            }
        }

        // Check lore requirement
        if (requireLore) {
            if (!item.hasItemMeta() || !item.itemMeta.hasLore()) {
                return false
            }
        }

        return true
    }
}
