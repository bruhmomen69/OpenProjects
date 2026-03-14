package bruh.zchat.utils.itemapi

import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.DropResult
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Defines a tracked item type that can be registered with the ItemAPI.
 * Tracked items have their instances stored in a database and can have
 * custom click, drop, and use handlers.
 *
 * @property id Unique identifier for this item type (e.g., "magic_wand")
 */
class TrackedItem(
    val id: String
) {
    init {
        require(id.isNotBlank()) { "TrackedItem id cannot be blank" }
        require(id.matches(ID_PATTERN)) {
            "TrackedItem id must contain only lowercase letters, numbers, and underscores: '$id'"
        }
    }

    /** Base VItem template for building item stacks */
    var vItem: VItem = VItem(XMaterial.STONE)
        private set

    /** Slots where this item is allowed (null = any slot) */
    var allowedSlots: Set<Int>? = null

    /** Whether the item cannot be dropped */
    var soulbound: Boolean = false

    /** Whether to prevent moving to disallowed slots */
    var preventMoveToDisallowedSlots: Boolean = true

    /** Handler called when the item is clicked in any inventory */
    var clickHandler: ((ItemContext, ItemControls) -> ClickResult)? = null

    /** Handler called when the item is dropped (Q or Ctrl+Q) */
    var dropHandler: ((ItemContext, ItemControls) -> DropResult)? = null

    /** Handler called when the item is right-clicked in the world */
    var useHandler: ((ItemContext, ItemControls) -> ClickResult)? = null

    /**
     * Sets the material for the base VItem.
     */
    fun material(material: XMaterial) {
        vItem = VItem(material)
    }

    /**
     * Configures the base VItem using a builder DSL.
     */
    fun item(builder: VItem.() -> Unit) {
        vItem.apply(builder)
    }

    /**
     * Sets the item name.
     */
    fun name(name: Component) {
        vItem.name = name
    }

    /**
     * Sets the item name from a string.
     */
    fun name(name: String) {
        vItem.name(name)
    }

    /**
     * Sets the click handler.
     */
    fun onClick(handler: (ItemContext, ItemControls) -> ClickResult) {
        clickHandler = handler
    }

    /**
     * Sets a click handler that always denies.
     */
    fun onClickDeny(action: (ItemContext, ItemControls) -> Unit = { _, _ -> }) {
        clickHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Deny
        }
    }

    /**
     * Sets a click handler that always allows.
     */
    fun onClickAllow(action: (ItemContext, ItemControls) -> Unit = { _, _ -> }) {
        clickHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Allow
        }
    }

    /**
     * Sets the drop handler.
     */
    fun onDrop(handler: (ItemContext, ItemControls) -> DropResult) {
        dropHandler = handler
    }

    /**
     * Sets a drop handler that always denies.
     */
    fun onDropDeny(action: (ItemContext, ItemControls) -> Unit = { _, _ -> }) {
        dropHandler = { ctx, controls ->
            action(ctx, controls)
            DropResult.DENY
        }
    }

    /**
     * Sets a drop handler that always allows.
     */
    fun onDropAllow(action: (ItemContext, ItemControls) -> Unit = { _, _ -> }) {
        dropHandler = { ctx, controls ->
            action(ctx, controls)
            DropResult.ALLOW
        }
    }

    /**
     * Sets the use handler (right-click in world).
     */
    fun onUse(handler: (ItemContext, ItemControls) -> ClickResult) {
        useHandler = handler
    }

    /**
     * Sets a use handler that always denies default behavior.
     */
    fun onUseDeny(action: (ItemContext, ItemControls) -> Unit = { _, _ -> }) {
        useHandler = { ctx, controls ->
            action(ctx, controls)
            ClickResult.Deny
        }
    }

    /**
     * Restricts this item to specific slots.
     */
    fun allowSlots(vararg slots: Int) {
        allowedSlots = slots.toSet()
    }

    /**
     * Restricts this item to a single slot.
     */
    fun allowSlot(slot: Int) {
        allowedSlots = setOf(slot)
    }

    /**
     * Builds an ItemStack from the VItem template.
     * Note: This does not add PDC tracking - use ItemAPI.createItem() instead.
     */
    fun buildItemStack() = vItem.build()

    companion object {
        private val ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")

        /**
         * Creates a TrackedItem using a DSL builder.
         */
        inline operator fun invoke(id: String, builder: TrackedItem.() -> Unit): TrackedItem {
            return TrackedItem(id).apply(builder)
        }
    }
}
