package bruh.zchat.utils.menuapi.configurable.config

import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration for a configurable menu.
 */
@ConfigSerializable
data class MenuConfig(
    @Comment("Menu title using MiniMessage format")
    val title: String = "Menu",

    @Comment("Number of rows in the menu (1-6)")
    val rows: Int = 3,

    @Comment("Background item to fill empty slots")
    val background: BackgroundConfig? = null,

    @Comment("Menu items mapped by their identifier")
    val items: Map<String, ItemConfig> = emptyMap(),

    @Comment("Drag slots for item menus, mapped by slot enum name")
    val dragSlots: Map<String, DragSlotConfig> = emptyMap()
) {
    companion object {
        private val miniMessage = MiniMessage.miniMessage()
    }

    /**
     * Parses the title as a MiniMessage component.
     */
    fun parsedTitle() = miniMessage.deserialize(title)

    /**
     * Gets the total inventory size.
     */
    val size: Int get() = rows.coerceIn(1, 6) * 9
}

/**
 * Configuration for a background filler item.
 */
@ConfigSerializable
data class BackgroundConfig(
    @Comment("Material name (XMaterial format)")
    val material: String = "BLACK_STAINED_GLASS_PANE",

    @Comment("Display name using MiniMessage format (empty string for no name)")
    val name: String? = "",

    @Comment("Lore lines using MiniMessage format")
    val lore: List<String> = emptyList(),

    @Comment("Custom model data value")
    val customModelData: Int? = null,

    @Comment("Whether to hide the tooltip entirely")
    val hideTooltip: Boolean = true
) {
    /**
     * Converts this to an ItemConfig for building.
     */
    fun toItemConfig(): ItemConfig = ItemConfig(
        slot = 0,
        material = material,
        name = name,
        lore = lore,
        customModelData = customModelData,
        hideTooltip = hideTooltip
    )
}
