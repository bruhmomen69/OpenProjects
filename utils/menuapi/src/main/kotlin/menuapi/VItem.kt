package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.meta.components.EquippableComponent
import org.bukkit.inventory.meta.components.FoodComponent
import org.bukkit.inventory.meta.components.UseCooldownComponent
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * A virtual item representation that can be converted to a Bukkit ItemStack.
 * Supports a fluent DSL for easy item creation.
 *
 * @param material The XMaterial type for this item
 */
class VItem(
    var material: XMaterial
) : MenuSlottable {
    // Display properties
    var name: Component? = null
    var lore: MutableList<Component> = mutableListOf()
    var amount: Int = 1
    var customModelData: Int? = null
    var itemModel: NamespacedKey? = null

    // Behavior
    var canBeMovedByPlayer: Boolean = false
    var clickHandler: ((ClickContext, MenuControls<*>) -> ClickResult)? = null
    var clickListener: ((ClickContext, MenuControls<*>) -> Unit)? = null
    var dropHandler: ((DropContext, MenuControls<*>) -> DropResult)? = null
    var dropListener: ((DropContext, MenuControls<*>) -> Unit)? = null

    // Item flags and properties
    var flags: MutableSet<ItemFlag> = mutableSetOf()
    var unbreakable: Boolean = false
    var hideTooltip: Boolean = false
    var enchantGlint: Boolean = false
    var isGlider: Boolean = false
    var damage: Int? = null
    var maxStackSize: Int? = null

    // Components
    var itemCooldown: UseCooldownComponent? = null
    var equippable: EquippableComponent? = null
    var food: FoodComponent? = null

    // Attribute modifiers
    var attributeModifiers: MutableMap<Attribute, MutableList<AttributeModifier>> = mutableMapOf()

    // Enchantments
    var enchantments: MutableMap<Enchantment, Int> = mutableMapOf()

    // Skull owner (for player heads)
    var skullOwner: UUID? = null
    var skullOwnerName: String? = null

    // Persistent data
    private val persistentData: MutableMap<NamespacedKey, PersistentDataEntry<*>> = mutableMapOf()

    /**
     * Set the display name using a string (will be converted to Component).
     */
    fun name(text: String) {
        name = Component.text(text)
    }

    /**
     * Add a single line of lore.
     */
    fun lore(line: Component) {
        lore.add(line)
    }

    /**
     * Add a single line of lore from a string.
     */
    fun lore(text: String) {
        lore.add(Component.text(text))
    }

    /**
     * Set multiple lines of lore.
     */
    fun lore(lines: List<Component>) {
        lore.clear()
        lore.addAll(lines)
    }

    /**
     * Set multiple lines of lore from strings.
     */
    fun loreStrings(lines: List<String>) {
        lore.clear()
        lore.addAll(lines.map { Component.text(it) })
    }

    /**
     * Add an item flag.
     */
    fun flag(flag: ItemFlag) {
        flags.add(flag)
    }

    /**
     * Add multiple item flags.
     */
    fun flags(vararg itemFlags: ItemFlag) {
        flags.addAll(itemFlags)
    }

    /**
     * Hide all item flags.
     */
    fun hideAllFlags() {
        flags.addAll(ItemFlag.entries)
    }

    /**
     * Add an enchantment.
     */
    fun enchant(enchantment: Enchantment, level: Int = 1) {
        enchantments[enchantment] = level
    }

    /**
     * Add a glowing effect without visible enchantment.
     */
    fun glow() {
        enchantGlint = true
    }

    /**
     * Add an attribute modifier.
     */
    fun attribute(attribute: Attribute, modifier: AttributeModifier) {
        attributeModifiers.getOrPut(attribute) { mutableListOf() }.add(modifier)
    }

    /**
     * Store persistent data on the item.
     */
    fun <T, Z : Any> persistentData(key: NamespacedKey, type: PersistentDataType<T, Z>, value: Z) {
        persistentData[key] = PersistentDataEntry(type, value)
    }

    /**
     * Set the click handler.
     */
    fun onClick(handler: (ClickContext, MenuControls<*>) -> ClickResult) {
        clickHandler = handler
    }

    /**
     * Simple click handler that just runs an action, then denies.
     */
    fun onClickDeny(action: (ClickContext, MenuControls<*>) -> Unit) {
        clickHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Deny
        }
    }

    /**
     * Adds a listener separate to the click handler.
     */
    fun clickListener(action: (ClickContext, MenuControls<*>) -> Unit) {
        clickListener = action
    }

    /**
     * Adds a listener separate to the click handler.
     */
    fun listener(action: (ClickContext, MenuControls<*>) -> Unit) {
        clickListener = action
    }

    /**
     * Click handler that closes the menu after the action.
     */
    fun onClickClose(action: (ClickContext, MenuControls<*>) -> Unit = { _, _ -> }) {
        clickHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Close
        }
    }

    /**
     * Click handler that refreshes the menu after the action.
     */
    fun onClickRefresh(action: (ClickContext, MenuControls<*>) -> Unit) {
        clickHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Refresh
        }
    }

    /**
     * Click handler that switches to another menu.
     * Use for actual menu-to-menu transitions (not state changes within the same menu).
     */
    fun onClickSwitch(menuFactory: (ClickContext, MenuControls<*>) -> Menu) {
        clickHandler = { ctx, controls -> ClickResult.SwitchMenu(menuFactory(ctx, controls)) }
    }

    /**
     * Set the drop handler.
     */
    fun onDrop(handler: (DropContext, MenuControls<*>) -> DropResult) {
        dropHandler = handler
    }

    /**
     * Drop handler that denies the drop after running an action.
     */
    fun onDropDeny(action: (DropContext, MenuControls<*>) -> Unit) {
        dropHandler = { ctx, controls ->
            action(ctx, controls)
            DropResult.DENY
        }
    }

    /**
     * Drop handler that allows the drop after running an action.
     */
    fun onDropAllow(action: (DropContext, MenuControls<*>) -> Unit) {
        dropHandler = { ctx, controls ->
            action(ctx, controls)
            DropResult.ALLOW
        }
    }

    /**
     * Drop handler that closes the menu after the action.
     */
    fun onDropClose(action: (DropContext, MenuControls<*>) -> Unit) {
        dropHandler = { ctx, controls ->
            action(ctx, controls)
            DropResult.CLOSE
        }
    }

    /**
     * Adds a listener separate to the drop handler.
     */
    fun dropListener(action: (DropContext, MenuControls<*>) -> Unit) {
        dropListener = action
    }

    /**
     * Build this VItem into a Bukkit ItemStack.
     */
    fun build(): ItemStack {
        val bukkitMaterial = material.parseMaterial() ?: Material.STONE
        val itemStack = ItemStack(bukkitMaterial, amount)

        itemStack.editMeta { meta ->
            // Display name
            name?.let { meta.displayName(it) }

            // Lore
            if (lore.isNotEmpty()) {
                meta.lore(lore)
            }

            // Custom model data
            customModelData?.let { meta.setCustomModelData(it) }

            // Item model
            itemModel?.let { meta.setItemModel(it) }

            // Item flags
            if (flags.isNotEmpty()) {
                meta.addItemFlags(*flags.toTypedArray())
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
                meta.damage = damage!!
            }

            // Skull owner (for player heads)
            if (meta is SkullMeta) {
                skullOwner?.let { uuid ->
                    Bukkit.getOfflinePlayer(uuid).let { player ->
                        meta.setOwningPlayer(player)
                    }
                }
                skullOwnerName?.let { name ->
                    @Suppress("DEPRECATION")
                    meta.owner = name
                }
            }

            // Components
            itemCooldown?.let { meta.setUseCooldown(it) }
            equippable?.let { meta.setEquippable(it) }
            food?.let { meta.setFood(it) }

            // Attribute modifiers
            attributeModifiers.forEach { (attr, modifiers) ->
                modifiers.forEach { modifier ->
                    meta.addAttributeModifier(attr, modifier)
                }
            }

            // Enchantments
            enchantments.forEach { (enchant, level) ->
                meta.addEnchant(enchant, level, true)
            }
        }

        // Persistent data
        if (persistentData.isNotEmpty()) {
            itemStack.editPersistentDataContainer { pdc ->
                persistentData.forEach { (key, entry) ->
                    @Suppress("UNCHECKED_CAST")
                    (entry as PersistentDataEntry<Any>).apply {
                        pdc.set(key, type as PersistentDataType<Any, Any>, value)
                    }
                }
            }
        }

        return itemStack
    }

    /**
     * Create a copy of this VItem.
     */
    fun copy(): VItem = VItem(material).also { copy ->
        copy.name = name
        copy.lore = lore.toMutableList()
        copy.amount = amount
        copy.customModelData = customModelData
        copy.itemModel = itemModel
        copy.canBeMovedByPlayer = canBeMovedByPlayer
        copy.clickHandler = clickHandler
        copy.clickListener = clickListener
        copy.dropHandler = dropHandler
        copy.dropListener = dropListener
        copy.flags = flags.toMutableSet()
        copy.unbreakable = unbreakable
        copy.hideTooltip = hideTooltip
        copy.enchantGlint = enchantGlint
        copy.isGlider = isGlider
        copy.damage = damage
        copy.maxStackSize = maxStackSize
        copy.itemCooldown = itemCooldown
        copy.equippable = equippable
        copy.food = food
        copy.attributeModifiers = attributeModifiers.mapValues { it.value.toMutableList() }.toMutableMap()
        copy.enchantments = enchantments.toMutableMap()
        copy.skullOwner = skullOwner
        copy.skullOwnerName = skullOwnerName
    }

    companion object {
        /**
         * Create a VItem using a DSL builder.
         */
        inline operator fun invoke(material: XMaterial, builder: VItem.() -> Unit): VItem {
            return VItem(material).apply(builder)
        }

        /**
         * Create a VItem from a Bukkit Material.
         */
        fun of(material: Material): VItem {
            return VItem(XMaterial.matchXMaterial(material))
        }

        /**
         * Create a VItem from a Bukkit Material with a builder.
         */
        inline fun of(material: Material, builder: VItem.() -> Unit): VItem {
            return VItem(XMaterial.matchXMaterial(material)).apply(builder)
        }

        /**
         * Create a player head VItem.
         */
        fun playerHead(player: Player, builder: VItem.() -> Unit = {}): VItem {
            return VItem(XMaterial.PLAYER_HEAD) {
                skullOwner = player.uniqueId
                builder()
            }
        }

        /**
         * Create a player head VItem by UUID.
         */
        fun playerHead(uuid: UUID, builder: VItem.() -> Unit = {}): VItem {
            return VItem(XMaterial.PLAYER_HEAD) {
                skullOwner = uuid
                builder()
            }
        }

        /**
         * Common filler items.
         */
        val FILLER_BLACK = VItem(XMaterial.BLACK_STAINED_GLASS_PANE) {
            name = Component.empty()
            hideAllFlags()
        }

        val FILLER_GRAY = VItem(XMaterial.GRAY_STAINED_GLASS_PANE) {
            name = Component.empty()
            hideAllFlags()
        }

        val FILLER_WHITE = VItem(XMaterial.WHITE_STAINED_GLASS_PANE) {
            name = Component.empty()
            hideAllFlags()
        }

        val AIR = VItem(XMaterial.AIR)
    }

    private data class PersistentDataEntry<Z : Any>(
        val type: PersistentDataType<*, Z>,
        val value: Z
    )
}
