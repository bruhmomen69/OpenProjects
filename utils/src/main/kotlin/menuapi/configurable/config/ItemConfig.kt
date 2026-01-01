package bruh.zchat.utils.menuapi.configurable.config

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.SkullMeta
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import java.util.*

/**
 * Configuration for a menu item with all VItem-equivalent properties.
 * All name/lore fields support MiniMessage formatting.
 */
@ConfigSerializable
data class ItemConfig(
    @Comment("Slot position in the menu (0-indexed)")
    val slot: Int = 0,

    @Comment("Material name (XMaterial format, e.g., DIAMOND, PLAYER_HEAD)")
    val material: String = "STONE",

    @Comment("Display name using MiniMessage format")
    val name: String? = null,

    @Comment("Lore lines using MiniMessage format")
    val lore: List<String> = emptyList(),

    @Comment("Stack amount")
    val amount: Int = 1,

    @Comment("Custom model data value")
    val customModelData: Int? = null,

    @Comment("Item model key (namespace:key format)")
    val itemModel: String? = null,

    @Comment("Item flags to apply (e.g., HIDE_ENCHANTS, HIDE_ATTRIBUTES)")
    val flags: List<String> = emptyList(),

    @Comment("Whether the item is unbreakable")
    val unbreakable: Boolean = false,

    @Comment("Whether to hide the tooltip entirely")
    val hideTooltip: Boolean = false,

    @Comment("Whether to show enchantment glint without enchantments")
    val enchantGlint: Boolean = false,

    @Comment("Whether this item can be used as a glider")
    val isGlider: Boolean = false,

    @Comment("Damage value for damageable items")
    val damage: Int? = null,

    @Comment("Maximum stack size override")
    val maxStackSize: Int? = null,

    @Comment("Enchantments map (enchantment name -> level)")
    val enchantments: Map<String, Int> = emptyMap(),

    @Comment("Skull owner UUID for player heads")
    val skullOwner: String? = null,

    @Comment("Skull owner name for player heads (legacy)")
    val skullOwnerName: String? = null,

    @Comment("Action name from the menu's action enum (null = display only)")
    val action: String? = null
) {
    companion object {
        private val miniMessage = MiniMessage.miniMessage()
    }

    /**
     * Builds this config into a Bukkit ItemStack.
     */
    fun buildItemStack(): ItemStack {
        val xMaterial = XMaterial.matchXMaterial(material).orElse(XMaterial.STONE)
        val bukkitMaterial = xMaterial.parseMaterial() ?: org.bukkit.Material.STONE
        val itemStack = ItemStack(bukkitMaterial, amount)

        itemStack.editMeta { meta ->
            // Display name
            name?.let { meta.displayName(miniMessage.deserialize(it)) }

            // Lore
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { miniMessage.deserialize(it) })
            }

            // Custom model data
            customModelData?.let { meta.setCustomModelData(it) }

            // Item model
            itemModel?.let { keyStr ->
                val parts = keyStr.split(":", limit = 2)
                if (parts.size == 2) {
                    meta.setItemModel(NamespacedKey(parts[0], parts[1]))
                }
            }

            // Item flags
            flags.forEach { flagName ->
                try {
                    val flag = ItemFlag.valueOf(flagName.uppercase())
                    meta.addItemFlags(flag)
                } catch (_: IllegalArgumentException) {
                    // Ignore invalid flag names
                }
            }

            // Unbreakable
            if (unbreakable) {
                meta.isUnbreakable = true
            }

            // Hide tooltip
            if (hideTooltip) {
                meta.isHideTooltip = true
            }

            // Enchant glint override
            if (enchantGlint) {
                meta.setEnchantmentGlintOverride(true)
            }

            // Glider
            if (isGlider) {
                meta.setGlider(true)
            }

            // Max stack size
            maxStackSize?.let { meta.setMaxStackSize(it) }

            // Damage (for damageable items)
            if (meta is Damageable && damage != null) {
                meta.damage = damage
            }

            // Skull owner (for player heads)
            if (meta is SkullMeta) {
                skullOwner?.let { uuidStr ->
                    try {
                        val uuid = UUID.fromString(uuidStr)
                        org.bukkit.Bukkit.getOfflinePlayer(uuid).let { player ->
                            meta.setOwningPlayer(player)
                        }
                    } catch (_: IllegalArgumentException) {
                        // Ignore invalid UUID
                    }
                }
                skullOwnerName?.let { name ->
                    @Suppress("DEPRECATION")
                    meta.owner = name
                }
            }

            // Enchantments
            enchantments.forEach { (enchantName, level) ->
                val enchantment = Enchantment.getByName(enchantName.uppercase())
                    ?: org.bukkit.Registry.ENCHANTMENT.get(
                        NamespacedKey.minecraft(enchantName.lowercase())
                    )
                enchantment?.let { meta.addEnchant(it, level, true) }
            }
        }

        return itemStack
    }
}
