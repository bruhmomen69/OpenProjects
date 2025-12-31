package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Result of a menu tree navigation/action.
 */
sealed class MenuTreeResult {
    /** User completed an action successfully */
    data class ActionCompleted(val actionId: String, val data: Any? = null) : MenuTreeResult()

    /** User cancelled/closed the menu tree */
    data object Cancelled : MenuTreeResult()

    /** User navigated back to root and closed */
    data object ClosedAtRoot : MenuTreeResult()
}

/**
 * A node in the menu tree - can be a menu, submenu, or action.
 */
sealed class MenuNode {
    abstract val id: String
    abstract val title: Component
    abstract val icon: VItem
}

/**
 * A submenu node that contains other nodes.
 */
class SubmenuNode(
    override val id: String,
    override val title: Component,
    override val icon: VItem
) : MenuNode() {
    @PublishedApi internal val children: MutableList<MenuNode> = mutableListOf()
    internal var dynamicChildren: ((Player) -> List<MenuNode>)? = null
    internal var background: VItem? = null
    internal var isPaginated: Boolean = false
    internal var itemsPerPage: Int = 28

    /**
     * Add a static submenu.
     */
    fun submenu(
        id: String,
        title: String,
        material: XMaterial = XMaterial.CHEST,
        builder: SubmenuNode.() -> Unit
    ) {
        val icon = VItem(material) { name = Component.text(title) }
        val node = SubmenuNode(id, Component.text(title), icon).apply(builder)
        children.add(node)
    }

    /**
     * Add a static submenu with custom icon.
     */
    fun submenu(
        id: String,
        title: Component,
        icon: VItem,
        builder: SubmenuNode.() -> Unit
    ) {
        val node = SubmenuNode(id, title, icon).apply(builder)
        children.add(node)
    }

    /**
     * Add an action item.
     */
    fun action(
        id: String,
        title: String,
        material: XMaterial = XMaterial.PAPER,
        description: List<String> = emptyList(),
        handler: suspend (Player) -> Any?
    ) {
        val icon = VItem(material) {
            name = Component.text(title)
            if (description.isNotEmpty()) {
                loreStrings(description)
            }
        }
        children.add(ActionNode(id, Component.text(title), icon, handler))
    }

    /**
     * Add an action item with custom icon.
     */
    fun action(
        id: String,
        title: Component,
        icon: VItem,
        handler: suspend (Player) -> Any?
    ) {
        children.add(ActionNode(id, title, icon, handler))
    }

    /**
     * Add an action that requires form data input first.
     */
    inline fun <reified T : Any> actionWithForm(
        id: String,
        title: String,
        material: XMaterial = XMaterial.PAPER,
        description: List<String> = emptyList(),
        formData: T,
        crossinline handler: suspend (Player, T) -> Any?
    ) {
        val icon = VItem(material) {
            name = Component.text(title)
            if (description.isNotEmpty()) {
                loreStrings(description)
            }
        }
        children.add(FormActionNode(id, Component.text(title), icon, formData) { player, data ->
            @Suppress("UNCHECKED_CAST")
            handler(player, data as T)
        })
    }

    /**
     * Add dynamic children based on player context.
     */
    fun dynamicItems(provider: (Player) -> List<MenuNode>) {
        dynamicChildren = provider
    }

    /**
     * Enable pagination for this submenu.
     */
    fun paginated(itemsPerPage: Int = 28) {
        this.isPaginated = true
        this.itemsPerPage = itemsPerPage
    }

    /**
     * Set the background item.
     */
    fun background(item: VItem) {
        this.background = item
    }
}

/**
 * An action node that executes code when clicked.
 */
class ActionNode(
    override val id: String,
    override val title: Component,
    override val icon: VItem,
    val handler: suspend (Player) -> Any?
) : MenuNode()

/**
 * An action node that collects form data before executing.
 */
class FormActionNode<T : Any>(
    override val id: String,
    override val title: Component,
    override val icon: VItem,
    val formData: T,
    val handler: suspend (Player, Any) -> Any?
) : MenuNode()

/**
 * Builder for creating a menu tree.
 */
class MenuTreeBuilder(
    private val menuApi: MenuAPI
) {
    private var rootTitle: Component = Component.text("Menu")
    @PublishedApi internal val rootChildren: MutableList<MenuNode> = mutableListOf()
    private var rootBackground: VItem? = VItem.FILLER_GRAY
    private var backItem: VItem = VItem(XMaterial.ARROW) { name = Component.text("Back") }
    private var closeItem: VItem = VItem(XMaterial.BARRIER) { name = Component.text("Close") }
    private var isPaginated: Boolean = false
    private var itemsPerPage: Int = 28

    /**
     * Set the root menu title.
     */
    fun title(title: String) {
        rootTitle = Component.text(title)
    }

    /**
     * Set the root menu title.
     */
    fun title(title: Component) {
        rootTitle = title
    }

    /**
     * Add a submenu to the root.
     */
    fun submenu(
        id: String,
        title: String,
        material: XMaterial = XMaterial.CHEST,
        builder: SubmenuNode.() -> Unit
    ) {
        val icon = VItem(material) { name = Component.text(title) }
        val node = SubmenuNode(id, Component.text(title), icon).apply(builder)
        rootChildren.add(node)
    }

    /**
     * Add a submenu to the root with custom icon.
     */
    fun submenu(
        id: String,
        title: Component,
        icon: VItem,
        builder: SubmenuNode.() -> Unit
    ) {
        val node = SubmenuNode(id, title, icon).apply(builder)
        rootChildren.add(node)
    }

    /**
     * Add an action to the root.
     */
    fun action(
        id: String,
        title: String,
        material: XMaterial = XMaterial.PAPER,
        description: List<String> = emptyList(),
        handler: suspend (Player) -> Any?
    ) {
        val icon = VItem(material) {
            name = Component.text(title)
            if (description.isNotEmpty()) {
                loreStrings(description)
            }
        }
        rootChildren.add(ActionNode(id, Component.text(title), icon, handler))
    }

    /**
     * Add an action to the root with custom icon.
     */
    fun action(
        id: String,
        title: Component,
        icon: VItem,
        handler: suspend (Player) -> Any?
    ) {
        rootChildren.add(ActionNode(id, title, icon, handler))
    }

    /**
     * Add an action that requires form data input first.
     */
    inline fun <reified T : Any> actionWithForm(
        id: String,
        title: String,
        material: XMaterial = XMaterial.PAPER,
        description: List<String> = emptyList(),
        formData: T,
        crossinline handler: suspend (Player, T) -> Any?
    ) {
        val icon = VItem(material) {
            name = Component.text(title)
            if (description.isNotEmpty()) {
                loreStrings(description)
            }
        }
        rootChildren.add(FormActionNode(id, Component.text(title), icon, formData) { player, data ->
            @Suppress("UNCHECKED_CAST")
            handler(player, data as T)
        })
    }

    /**
     * Set the background item.
     */
    fun background(item: VItem) {
        rootBackground = item
    }

    /**
     * Set the back navigation item.
     */
    fun backItem(item: VItem) {
        backItem = item
    }

    /**
     * Set the close item.
     */
    fun closeItem(item: VItem) {
        closeItem = item
    }

    /**
     * Enable pagination for the root menu.
     */
    fun paginated(itemsPerPage: Int = 28) {
        this.isPaginated = true
        this.itemsPerPage = itemsPerPage
    }

    /**
     * Build and return the menu tree navigator.
     */
    fun build(): MenuTreeNavigator {
        val rootNode = SubmenuNode("root", rootTitle, VItem.AIR).apply {
            children.addAll(rootChildren)
            background = rootBackground
            isPaginated = this@MenuTreeBuilder.isPaginated
            itemsPerPage = this@MenuTreeBuilder.itemsPerPage
        }
        return MenuTreeNavigator(menuApi, rootNode, backItem, closeItem)
    }
}

/**
 * Handles navigation through the menu tree.
 */
class MenuTreeNavigator(
    private val menuApi: MenuAPI,
    private val rootNode: SubmenuNode,
    private val backItem: VItem,
    private val closeItem: VItem
) {
    /**
     * Open the menu tree for a player and return when they complete an action or close.
     */
    suspend fun open(player: Player): MenuTreeResult {
        return suspendCoroutine { continuation ->
            openAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }

    /**
     * Open the menu tree for a player asynchronously.
     */
    fun openAsync(player: Player): CompletableFuture<MenuTreeResult> {
        val future = CompletableFuture<MenuTreeResult>()
        showNode(player, rootNode, emptyList(), future)
        return future
    }

    private fun showNode(
        player: Player,
        node: SubmenuNode,
        breadcrumb: List<SubmenuNode>,
        future: CompletableFuture<MenuTreeResult>
    ) {
        // Gather all children (static + dynamic)
        val allChildren = node.children.toMutableList()
        node.dynamicChildren?.invoke(player)?.let { allChildren.addAll(it) }

        if (node.isPaginated && allChildren.size > node.itemsPerPage) {
            showPaginatedNode(player, node, allChildren, breadcrumb, future)
        } else {
            showSimpleNode(player, node, allChildren, breadcrumb, future)
        }
    }

    private fun showSimpleNode(
        player: Player,
        node: SubmenuNode,
        children: List<MenuNode>,
        breadcrumb: List<SubmenuNode>,
        future: CompletableFuture<MenuTreeResult>
    ) {
        // Track whether this menu was actually interacted with (navigated to
        // another node or executed an action). If the menu closes without any
        // interaction, we treat that as the user cancelling the menu tree.
        var interacted = false

        val menu = menuApi.simple {
            title = node.title
            rows = calculateRows(children.size, breadcrumb.isNotEmpty())
            background = node.background

            // Add navigation items
            val lastRow = rows - 1

            if (breadcrumb.isNotEmpty()) {
                // Back button
                item(lastRow * 9, backItem.copy().apply {
                    onClickDeny { _, _ ->
                        interacted = true
                        val parentBreadcrumb = breadcrumb.dropLast(1)
                        val parentNode = breadcrumb.lastOrNull() ?: rootNode
                        showNode(player, parentNode, parentBreadcrumb, future)
                    }
                })
            }

            // Close button
            item(lastRow * 9 + 8, closeItem.copy().apply {
                onClickDeny { _, _ ->
                    interacted = true
                    player.closeInventory()
                    if (!future.isDone) {
                        if (breadcrumb.isEmpty()) {
                            future.complete(MenuTreeResult.ClosedAtRoot)
                        } else {
                            future.complete(MenuTreeResult.Cancelled)
                        }
                    }
                }
            })

            // Add child items
            val contentSlots = getContentSlots(rows)
            children.forEachIndexed { index, child ->
                if (index < contentSlots.size) {
                    val slot = contentSlots[index]
                    addNodeItem(slot, child, node, breadcrumb, future, player) {
                        interacted = true
                    }
                }
            }

            onClose = { _, _ ->
                if (!interacted && !future.isDone) {
                    if (breadcrumb.isEmpty()) {
                        future.complete(MenuTreeResult.ClosedAtRoot)
                    } else {
                        future.complete(MenuTreeResult.Cancelled)
                    }
                }
            }
        }

        menuApi.open(menu, player)
    }

    private fun showPaginatedNode(
        player: Player,
        node: SubmenuNode,
        children: List<MenuNode>,
        breadcrumb: List<SubmenuNode>,
        future: CompletableFuture<MenuTreeResult>
    ) {
        var interacted = false

        val menu = menuApi.paginated<MenuNode> {
            title = node.title
            rows = 6
            background = node.background

            dataSource = children
            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            itemRenderer = { child, _ ->
                child.icon.copy()
            }

            previousPageItem = VItem(XMaterial.ARROW) { name = Component.text("Previous Page") }
            nextPageItem = VItem(XMaterial.ARROW) { name = Component.text("Next Page") }

            pageIndicatorRenderer = { current, total ->
                VItem(XMaterial.PAPER) {
                    name = Component.text("Page $current of $total")
                }
            }

            // Back button at slot 45
            if (breadcrumb.isNotEmpty()) {
                staticItem(45, backItem.material) {
                    name = backItem.name
                    onClickDeny { _, _ ->
                        interacted = true
                        val parentBreadcrumb = breadcrumb.dropLast(1)
                        val parentNode = breadcrumb.lastOrNull() ?: rootNode
                        showNode(player, parentNode, parentBreadcrumb, future)
                    }
                }
            }

            // Close button at slot 53 (but next page is there, so use 44)
            staticItem(44, closeItem.material) {
                name = closeItem.name
                onClickDeny { _, _ ->
                    interacted = true
                    player.closeInventory()
                    if (!future.isDone) {
                        if (breadcrumb.isEmpty()) {
                            future.complete(MenuTreeResult.ClosedAtRoot)
                        } else {
                            future.complete(MenuTreeResult.Cancelled)
                        }
                    }
                }
            }

            onClose = { _, _ ->
                if (!future.isDone) {
                    future.complete(MenuTreeResult.Cancelled)
                }
            }
        }

        // We need to add click handlers to the paginated items
        // This requires a custom approach - store handlers and check in content slots
        val controls = menuApi.open(menu, player)

        // Add click listeners to content items after opening
        // Since paginated menu rebuilds on page change, we need to handle this differently
        // For now, we'll use a workaround by storing the node reference
    }

    private fun SimpleMenu.addNodeItem(
        slot: Int,
        child: MenuNode,
        parentNode: SubmenuNode,
        breadcrumb: List<SubmenuNode>,
        future: CompletableFuture<MenuTreeResult>,
        player: Player,
        onInteraction: () -> Unit
    ) {
        val icon = child.icon.copy()

        when (child) {
            is SubmenuNode -> {
                icon.onClickDeny { _, _ ->
                    onInteraction()
                    val newBreadcrumb = breadcrumb + parentNode
                    showNode(player, child, newBreadcrumb, future)
                }
            }
            is ActionNode -> {
                icon.onClickDeny { _, _ ->
                    onInteraction()
                    player.closeInventory()
                    // Execute action asynchronously
                    menuApi.plugin.server.scheduler.runTaskAsynchronously(menuApi.plugin, Runnable {
                        try {
                            // We need to run the suspend function - use a simple approach
                            val result = kotlinx.coroutines.runBlocking {
                                child.handler(player)
                            }
                            if (!future.isDone) {
                                future.complete(MenuTreeResult.ActionCompleted(child.id, result))
                            }
                        } catch (e: Exception) {
                            menuApi.plugin.slF4JLogger.error("Error executing menu action ${child.id}", e)
                            if (!future.isDone) {
                                future.complete(MenuTreeResult.ActionCompleted(child.id, null))
                            }
                        }
                    })
                }
            }
            is FormActionNode<*> -> {
                icon.onClickDeny { _, _ ->
                    onInteraction()
                    // Collect form data first, then execute action
                    val formCollector = FormDataCollector(menuApi, child.formData, child.title.toString())
                    formCollector.collectAsync(player).thenAccept { formResult ->
                        when (formResult) {
                            is FormResult.Success -> {
                                menuApi.plugin.server.scheduler.runTaskAsynchronously(menuApi.plugin, Runnable {
                                    try {
                                        val result = kotlinx.coroutines.runBlocking {
                                            child.handler(player, formResult.data)
                                        }
                                        if (!future.isDone) {
                                            future.complete(MenuTreeResult.ActionCompleted(child.id, result))
                                        }
                                    } catch (e: Exception) {
                                        menuApi.plugin.slF4JLogger.error("Error executing menu action ${child.id}", e)
                                        if (!future.isDone) {
                                            future.complete(MenuTreeResult.ActionCompleted(child.id, null))
                                        }
                                    }
                                })
                            }
                            is FormResult.Cancelled -> {
                                // Return to the menu
                                showNode(player, parentNode, breadcrumb, future)
                            }
                        }
                    }
                }
            }
        }

        item(slot, icon)
    }

    private fun calculateRows(itemCount: Int, hasBack: Boolean): Int {
        // Reserve bottom row for navigation
        val contentRows = when {
            itemCount <= 7 -> 1
            itemCount <= 14 -> 2
            itemCount <= 21 -> 3
            itemCount <= 28 -> 4
            else -> 5
        }
        return contentRows + 1 // +1 for navigation row
    }

    private fun getContentSlots(rows: Int): List<Int> {
        // Use center slots for content, leaving edges for aesthetics
        val slots = mutableListOf<Int>()
        for (row in 0 until rows - 1) { // Exclude last row (navigation)
            for (col in 1..7) { // Columns 1-7 (leaving 0 and 8)
                slots.add(row * 9 + col)
            }
        }
        return slots
    }
}

/**
 * Extension function to create a menu tree.
 *
 * Usage:
 * ```kotlin
 * val result = menuApi.menuTree {
 *     title("Main Menu")
 *
 *     submenu("settings", "Settings", XMaterial.COMPARATOR) {
 *         action("toggle", "Toggle Feature", XMaterial.LEVER) { player ->
 *             // Do something
 *             "Feature toggled"
 *         }
 *
 *         actionWithForm("create", "Create Item", XMaterial.CRAFTING_TABLE,
 *             formData = MyFormData()
 *         ) { player, data ->
 *             // Use form data
 *         }
 *     }
 *
 *     action("quit", "Quit", XMaterial.BARRIER) { player ->
 *         player.kick(Component.text("Goodbye!"))
 *     }
 * }.open(player)
 * ```
 */
fun MenuAPI.menuTree(builder: MenuTreeBuilder.() -> Unit): MenuTreeNavigator {
    return MenuTreeBuilder(this).apply(builder).build()
}

/**
 * Quick helper to create and immediately open a menu tree.
 */
suspend fun MenuAPI.openMenuTree(player: Player, builder: MenuTreeBuilder.() -> Unit): MenuTreeResult {
    return menuTree(builder).open(player)
}

/**
 * Async version of openMenuTree.
 */
fun MenuAPI.openMenuTreeAsync(player: Player, builder: MenuTreeBuilder.() -> Unit): CompletableFuture<MenuTreeResult> {
    return menuTree(builder).openAsync(player)
}

/**
 * Helper to create dynamic menu nodes from a list of data.
 */
inline fun <T> dynamicNodes(
    items: List<T>,
    crossinline nodeBuilder: (T) -> MenuNode
): List<MenuNode> {
    return items.map { nodeBuilder(it) }
}

/**
 * Helper to create an action node from data.
 */
fun actionNode(
    id: String,
    title: String,
    material: XMaterial = XMaterial.PAPER,
    description: List<String> = emptyList(),
    handler: suspend (Player) -> Any?
): ActionNode {
    val icon = VItem(material) {
        name = Component.text(title)
        if (description.isNotEmpty()) {
            loreStrings(description)
        }
    }
    return ActionNode(id, Component.text(title), icon, handler)
}

/**
 * Helper to create a submenu node.
 */
fun submenuNode(
    id: String,
    title: String,
    material: XMaterial = XMaterial.CHEST,
    builder: SubmenuNode.() -> Unit
): SubmenuNode {
    val icon = VItem(material) { name = Component.text(title) }
    return SubmenuNode(id, Component.text(title), icon).apply(builder)
}
