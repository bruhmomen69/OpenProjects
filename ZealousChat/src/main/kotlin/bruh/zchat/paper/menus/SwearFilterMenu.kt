package bruh.zchat.paper.menus

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import bruh.zchat.paper.config.SwearFilterConfig
import bruh.zchat.paper.services.ChatInputService
import bruh.zchat.paper.swearfilter.InfractionManager
import bruh.zchat.utils.menuapi.ClickContext
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenu
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenuAPI
import bruh.zchat.utils.menuapi.configurable.ConfigurableMenuInstance
import bruh.zchat.paper.PaperMC
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Actions available in the swear filter management menu.
 */
enum class SwearFilterActions {
    CLOSE,
    BACK_TO_MAIN,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    
    // Main view actions
    TOGGLE_FILTER_ENABLED,
    VIEW_GROUP_DETAILS,
    VIEW_PLAYER_INFRACTIONS,
    CREATE_GROUP,
    
    // Group editing actions
    EDIT_GROUP_NAME,
    EDIT_GROUP_TYPE,
    EDIT_GROUP_DISTANCE,
    MANAGE_FILTERS,
    MANAGE_PUNISHMENTS,
    DELETE_GROUP,
    BACK_TO_GROUPS,
    
    // Filter management actions
    ADD_FILTER,
    REMOVE_FILTER,
    
    // Punishment management actions
    ADD_PUNISHMENT,
    EDIT_PUNISHMENT,
    REMOVE_PUNISHMENT,
    
    // Type selection actions
    SELECT_TYPE_REGEX,
    SELECT_TYPE_SMART,
    SELECT_TYPE_LEVENSHTEIN,
    SELECT_TYPE_DICE,
    
    // Player infractions
    CLEAR_PLAYER_INFRACTIONS
}

/**
 * Configurable menu for managing swear filter settings and viewing infractions.
 * Allows full configuration of all filter group attributes from the GUI.
 */
class SwearFilterMenu(
    menuApi: ConfigurableMenuAPI,
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val infractionManager: InfractionManager,
    private val chatInputService: ChatInputService
) : ConfigurableMenu<SwearFilterActions>(
    menuApi = menuApi,
    configName = "swearfilter",
    actionClass = SwearFilterActions::class
) {
    private val miniMessage = MiniMessage.miniMessage()
    
    // Track current view state per player
    private val playerStates = mutableMapOf<UUID, PlayerState>()

    private data class PlayerState(
        var view: SwearFilterView = SwearFilterView.MAIN,
        var page: Int = 0,
        var selectedGroupIndex: Int = -1,
        var selectedPunishmentCount: Int = -1,
        var editingGroups: MutableList<FilterGroup> = mutableListOf()
    )
    
    private enum class SwearFilterView {
        MAIN,
        GROUP_EDIT,
        TYPE_SELECT,
        FILTER_LIST,
        PUNISHMENT_LIST,
        PUNISHMENT_EDIT,
        PLAYER_INFRACTIONS
    }

    override val actionHandlers: Map<SwearFilterActions, (ClickContext, ConfigurableMenuInstance<SwearFilterActions>) -> ClickResult> = mapOf(
        SwearFilterActions.CLOSE to { _, instance ->
            instance.close()
            ClickResult.CLOSE
        },
        SwearFilterActions.BACK_TO_MAIN to { ctx, instance ->
            getState(ctx.player).apply {
                view = SwearFilterView.MAIN
                page = 0
                selectedGroupIndex = -1
            }
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.BACK_TO_GROUPS to { ctx, instance ->
            getState(ctx.player).apply {
                view = SwearFilterView.GROUP_EDIT
            }
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.PREVIOUS_PAGE to { ctx, instance ->
            val state = getState(ctx.player)
            if (state.page > 0) {
                state.page--
                refreshDisplay(instance)
            }
            ClickResult.DENY
        },
        SwearFilterActions.NEXT_PAGE to { ctx, instance ->
            handleNextPage(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.TOGGLE_FILTER_ENABLED to { ctx, instance ->
            handleToggleFilterEnabled(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.VIEW_GROUP_DETAILS to { ctx, instance ->
            handleViewGroupDetails(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.VIEW_PLAYER_INFRACTIONS to { ctx, instance ->
            getState(ctx.player).apply {
                view = SwearFilterView.PLAYER_INFRACTIONS
                page = 0
            }
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.CREATE_GROUP to { ctx, instance ->
            handleCreateGroup(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.EDIT_GROUP_NAME to { ctx, instance ->
            handleEditGroupName(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.EDIT_GROUP_TYPE to { ctx, instance ->
            getState(ctx.player).view = SwearFilterView.TYPE_SELECT
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.EDIT_GROUP_DISTANCE to { ctx, instance ->
            handleEditGroupDistance(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.MANAGE_FILTERS to { ctx, instance ->
            getState(ctx.player).apply {
                view = SwearFilterView.FILTER_LIST
                page = 0
            }
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.MANAGE_PUNISHMENTS to { ctx, instance ->
            getState(ctx.player).apply {
                view = SwearFilterView.PUNISHMENT_LIST
                page = 0
            }
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.DELETE_GROUP to { ctx, instance ->
            handleDeleteGroup(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.ADD_FILTER to { ctx, instance ->
            handleAddFilter(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.REMOVE_FILTER to { ctx, instance ->
            handleRemoveFilter(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.ADD_PUNISHMENT to { ctx, instance ->
            handleAddPunishment(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.EDIT_PUNISHMENT to { ctx, instance ->
            handleEditPunishment(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.REMOVE_PUNISHMENT to { ctx, instance ->
            handleRemovePunishment(ctx, instance)
            ClickResult.DENY
        },
        SwearFilterActions.SELECT_TYPE_REGEX to { ctx, instance ->
            setGroupType(ctx.player, "regex")
            getState(ctx.player).view = SwearFilterView.GROUP_EDIT
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.SELECT_TYPE_SMART to { ctx, instance ->
            setGroupType(ctx.player, "smart")
            getState(ctx.player).view = SwearFilterView.GROUP_EDIT
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.SELECT_TYPE_LEVENSHTEIN to { ctx, instance ->
            setGroupType(ctx.player, "levenshtein")
            getState(ctx.player).view = SwearFilterView.GROUP_EDIT
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.SELECT_TYPE_DICE to { ctx, instance ->
            setGroupType(ctx.player, "dice-sorensen")
            getState(ctx.player).view = SwearFilterView.GROUP_EDIT
            refreshDisplay(instance)
            ClickResult.DENY
        },
        SwearFilterActions.CLEAR_PLAYER_INFRACTIONS to { ctx, instance ->
            handleClearInfractions(ctx, instance)
            ClickResult.DENY
        }
    )

    override val onClose: ((Player, ConfigurableMenuInstance<SwearFilterActions>) -> Unit) = { player, _ ->
        chatInputService.cancelInput(player)
        playerStates.remove(player.uniqueId)
    }

    private fun getState(player: Player): PlayerState {
        return playerStates.getOrPut(player.uniqueId) {
            PlayerState(editingGroups = configManager.config.swearFilter.filterGroups.toMutableList())
        }
    }

    /**
     * Opens the swear filter management menu for an admin.
     */
    fun openForAdmin(player: Player) {
        if (!player.hasPermission(configManager.config.gui.swearFilterGuiPermission)) {
            player.sendMessage(miniMessage.deserialize("<red>You don't have permission to access this menu.</red>"))
            return
        }
        
        playerStates[player.uniqueId] = PlayerState(
            editingGroups = configManager.config.swearFilter.filterGroups.toMutableList()
        )
        val instance = open(player)
        refreshDisplay(instance)
    }

    private fun refreshDisplay(instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val player = instance.player
        val state = getState(player)
        
        // Clear content area and any dynamic actions
        val contentSlots = (config.rows - 1) * 9
        for (slot in 0 until contentSlots) {
            instance.setItemAt(slot, null)
            instance.registerActionSlot(slot, null)
        }
        
        when (state.view) {
            SwearFilterView.MAIN -> displayMainView(instance, state)
            SwearFilterView.GROUP_EDIT -> displayGroupEditView(instance, state)
            SwearFilterView.TYPE_SELECT -> displayTypeSelectView(instance, state)
            SwearFilterView.FILTER_LIST -> displayFilterListView(instance, state)
            SwearFilterView.PUNISHMENT_LIST -> displayPunishmentListView(instance, state)
            SwearFilterView.PUNISHMENT_EDIT -> displayPunishmentEditView(instance, state)
            SwearFilterView.PLAYER_INFRACTIONS -> displayPlayerInfractionsView(instance, state)
        }
    }

    private fun displayMainView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val filterConfig = configManager.config.swearFilter
        val size = instance.inventory.size
        
        // Toggle enabled status at slot 4
        val statusItem = ItemStack(if (filterConfig.enabled) Material.LIME_DYE else Material.GRAY_DYE)
        statusItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize(
                if (filterConfig.enabled) "<green><bold>Swear Filter: ENABLED</bold></green>"
                else "<red><bold>Swear Filter: DISABLED</bold></red>"
            ))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Filter groups: ${state.editingGroups.size}</gray>"),
                miniMessage.deserialize("<gray>Blocked message: ${if (filterConfig.enableBlockedMessage) "Yes" else "No"}</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to toggle</yellow>")
            ))
        }
        if (4 in 0 until size) {
            instance.setItemAt(4, statusItem)
            instance.registerActionSlot(4, SwearFilterActions.TOGGLE_FILTER_ENABLED)
        }

        // Display filter groups starting at slot 10
        state.editingGroups.forEachIndexed { index, group ->
            if (index >= 21) return@forEachIndexed // Max 21 groups displayed
            
            val groupItem = createGroupItem(group)
            val slot = 10 + index + (index / 7) * 2
            if (slot in 0 until size) {
                instance.setItemAt(slot, groupItem)
                instance.registerActionSlot(slot, SwearFilterActions.VIEW_GROUP_DETAILS)
            }
        }

        // Create new group button at slot 8
        val createItem = ItemStack(Material.EMERALD)
        createItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<green><bold>Create New Group</bold></green>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to create a new filter group</gray>")
            ))
        }
        if (8 in 0 until size) {
            instance.setItemAt(8, createItem)
            instance.registerActionSlot(8, SwearFilterActions.CREATE_GROUP)
        }

        // View player infractions button at slot 40 or last row
        val infractionsSlot = if (size > 40) 40 else size - 1
        val infractionsItem = ItemStack(Material.BOOK)
        infractionsItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow><bold>View Player Infractions</bold></yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to view online players' infractions</gray>")
            ))
        }
        if (infractionsSlot in 0 until size) {
            instance.setItemAt(infractionsSlot, infractionsItem)
            instance.registerActionSlot(infractionsSlot, SwearFilterActions.VIEW_PLAYER_INFRACTIONS)
        }
    }

    private fun createGroupItem(group: FilterGroup): ItemStack {
        val material = when (group.type.lowercase()) {
            "regex" -> Material.COMPARATOR
            "smart", "mixed", "auto" -> Material.REDSTONE
            "levenshtein" -> Material.REPEATER
            "dice-sorensen", "dice" -> Material.HOPPER
            else -> Material.BARRIER
        }
        
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>${group.name}</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Type: <white>${group.type}</white></gray>"),
                miniMessage.deserialize("<gray>Distance: <white>${group.distance}</white></gray>"),
                miniMessage.deserialize("<gray>Filters: <white>${group.filters.size}</white></gray>"),
                miniMessage.deserialize("<gray>Punishments: <white>${group.punishments.size}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to edit</yellow>")
            ))
        }
        return item
    }

    private fun displayGroupEditView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        val size = instance.inventory.size
        
        // Back button at slot 0
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back to Groups</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.BACK_TO_MAIN)

        // Group name header at slot 4
        val headerItem = ItemStack(Material.NAME_TAG)
        headerItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>Editing: ${group.name}</bold></gold>"))
        }
        instance.setItemAt(4, headerItem)

        // Edit name at slot 10
        val nameItem = ItemStack(Material.PAPER)
        nameItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Edit Name</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Current: <white>${group.name}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to change</yellow>")
            ))
        }
        if (10 in 0 until size) {
            instance.setItemAt(10, nameItem)
            instance.registerActionSlot(10, SwearFilterActions.EDIT_GROUP_NAME)
        }

        // Edit type at slot 12
        val typeItem = ItemStack(getMaterialForType(group.type))
        typeItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Edit Type</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Current: <white>${group.type}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>regex - Exact pattern matching</dark_gray>"),
                miniMessage.deserialize("<dark_gray>smart - Fuzzy matching (recommended)</dark_gray>"),
                miniMessage.deserialize("<dark_gray>levenshtein - Edit distance matching</dark_gray>"),
                miniMessage.deserialize("<dark_gray>dice-sorensen - Similarity coefficient</dark_gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to change</yellow>")
            ))
        }
        if (12 in 0 until size) {
            instance.setItemAt(12, typeItem)
            instance.registerActionSlot(12, SwearFilterActions.EDIT_GROUP_TYPE)
        }

        // Edit distance at slot 14
        val distanceItem = ItemStack(Material.CLOCK)
        distanceItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Edit Distance/Threshold</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Current: <white>${group.distance}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>For levenshtein/smart: max edit distance</dark_gray>"),
                miniMessage.deserialize("<dark_gray>For dice-sorensen: similarity % (e.g., 75)</dark_gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Left-click: +1 | Right-click: -1</yellow>"),
                miniMessage.deserialize("<yellow>Shift+click for +/-5</yellow>")
            ))
        }
        if (14 in 0 until size) {
            instance.setItemAt(14, distanceItem)
            instance.registerActionSlot(14, SwearFilterActions.EDIT_GROUP_DISTANCE)
        }

        // Manage filters at slot 16
        val filtersItem = ItemStack(Material.WRITABLE_BOOK)
        filtersItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Manage Filters</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Filters: <white>${group.filters.size}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to add/remove filter patterns</yellow>")
            ))
        }
        if (16 in 0 until size) {
            instance.setItemAt(16, filtersItem)
            instance.registerActionSlot(16, SwearFilterActions.MANAGE_FILTERS)
        }

        // Manage punishments at slot 28
        val punishItem = ItemStack(Material.IRON_SWORD)
        punishItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow>Manage Punishments</yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Punishment levels: <white>${group.punishments.size}</white></gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<yellow>Click to add/edit/remove punishments</yellow>")
            ))
        }
        if (28 in 0 until size) {
            instance.setItemAt(28, punishItem)
            instance.registerActionSlot(28, SwearFilterActions.MANAGE_PUNISHMENTS)
        }

        // Delete group at slot 35
        val deleteItem = ItemStack(Material.BARRIER)
        deleteItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<red><bold>Delete Group</bold></red>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Permanently delete this filter group</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<red>This cannot be undone!</red>")
            ))
        }
        if (35 in 0 until size) {
            instance.setItemAt(35, deleteItem)
            instance.registerActionSlot(35, SwearFilterActions.DELETE_GROUP)
        }
    }

    private fun displayTypeSelectView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        // Back button
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.BACK_TO_GROUPS)

        // Title
        val titleItem = ItemStack(Material.OAK_SIGN)
        titleItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>Select Filter Type</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Current: <white>${group.type}</white></gray>")
            ))
        }
        instance.setItemAt(4, titleItem)

        // Regex type at slot 10
        val regexItem = ItemStack(Material.COMPARATOR)
        regexItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow><bold>Regex</bold></yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Pattern-based matching</gray>"),
                miniMessage.deserialize("<gray>Use regular expressions</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>Best for: exact patterns, URLs, IPs</dark_gray>")
            ))
            if (group.type.lowercase() == "regex") {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        instance.setItemAt(10, regexItem)
        instance.registerActionSlot(10, SwearFilterActions.SELECT_TYPE_REGEX)

        // Smart type at slot 12
        val smartItem = ItemStack(Material.REDSTONE)
        smartItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<green><bold>Smart (Recommended)</bold></green>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Combines Levenshtein + Dice-Sorensen</gray>"),
                miniMessage.deserialize("<gray>Automatically scales for word length</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>Best for: general swear word filtering</dark_gray>")
            ))
            if (group.type.lowercase() in listOf("smart", "mixed", "auto")) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        instance.setItemAt(12, smartItem)
        instance.registerActionSlot(12, SwearFilterActions.SELECT_TYPE_SMART)

        // Levenshtein type at slot 14
        val levenshteinItem = ItemStack(Material.REPEATER)
        levenshteinItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow><bold>Levenshtein</bold></yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Edit distance matching</gray>"),
                miniMessage.deserialize("<gray>Counts character changes needed</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>Distance setting = max allowed edits</dark_gray>")
            ))
            if (group.type.lowercase() == "levenshtein") {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        instance.setItemAt(14, levenshteinItem)
        instance.registerActionSlot(14, SwearFilterActions.SELECT_TYPE_LEVENSHTEIN)

        // Dice-Sorensen type at slot 16
        val diceItem = ItemStack(Material.HOPPER)
        diceItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<yellow><bold>Dice-Sorensen</bold></yellow>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Similarity coefficient matching</gray>"),
                miniMessage.deserialize("<gray>Compares character bigrams</gray>"),
                miniMessage.deserialize(""),
                miniMessage.deserialize("<dark_gray>Distance setting = min similarity % (e.g., 75)</dark_gray>")
            ))
            if (group.type.lowercase() in listOf("dice-sorensen", "dice")) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        instance.setItemAt(16, diceItem)
        instance.registerActionSlot(16, SwearFilterActions.SELECT_TYPE_DICE)
    }

    private fun displayFilterListView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        val size = instance.inventory.size
        val itemsPerPage = (config.rows - 2) * 9 // Leave space for nav and add button
        
        // Back button
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.BACK_TO_GROUPS)

        // Title
        val titleItem = ItemStack(Material.WRITABLE_BOOK)
        titleItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>Filter Patterns</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Group: ${group.name}</gray>"),
                miniMessage.deserialize("<gray>Total filters: ${group.filters.size}</gray>")
            ))
        }
        instance.setItemAt(4, titleItem)

        // Add filter button
        val addItem = ItemStack(Material.EMERALD)
        addItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<green><bold>Add Filter</bold></green>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to add a new filter pattern</gray>")
            ))
        }
        instance.setItemAt(8, addItem)
        instance.registerActionSlot(8, SwearFilterActions.ADD_FILTER)

        // Display filters
        val startIndex = state.page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, group.filters.size)
        
        for (i in startIndex until endIndex) {
            val filter = group.filters[i]
            val slot = 9 + (i - startIndex)
            
            if (slot in 0 until size - 9) {
                val filterItem = ItemStack(Material.PAPER)
                filterItem.editMeta { meta ->
                    val displayText = if (filter.length > 30) filter.take(27) + "..." else filter
                    meta.displayName(miniMessage.deserialize("<yellow>$displayText</yellow>"))
                    meta.lore(listOf(
                        miniMessage.deserialize("<gray>Index: ${i + 1}</gray>"),
                        if (filter.length > 30) miniMessage.deserialize("<dark_gray>$filter</dark_gray>") else null,
                        miniMessage.deserialize(""),
                        miniMessage.deserialize("<red>Click to remove</red>")
                    ).filterNotNull())
                }
                instance.setItemAt(slot, filterItem)
                instance.registerActionSlot(slot, SwearFilterActions.REMOVE_FILTER)
            }
        }

        // Pagination
        displayPagination(instance, state, group.filters.size, itemsPerPage)
    }

    private fun displayPunishmentListView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        val size = instance.inventory.size
        
        // Back button
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.BACK_TO_GROUPS)

        // Title
        val titleItem = ItemStack(Material.IRON_SWORD)
        titleItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>Punishments</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Group: ${group.name}</gray>"),
                miniMessage.deserialize("<gray>Punishment levels: ${group.punishments.size}</gray>")
            ))
        }
        instance.setItemAt(4, titleItem)

        // Add punishment button
        val addItem = ItemStack(Material.EMERALD)
        addItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<green><bold>Add Punishment Level</bold></green>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to add a punishment for a specific infraction count</gray>")
            ))
        }
        instance.setItemAt(8, addItem)
        instance.registerActionSlot(8, SwearFilterActions.ADD_PUNISHMENT)

        // Display punishments sorted by infraction count
        val sortedPunishments = group.punishments.entries.sortedBy { it.key }
        sortedPunishments.forEachIndexed { index, (count, commands) ->
            if (index >= 21) return@forEachIndexed
            
            val slot = 10 + index + (index / 7) * 2
            if (slot in 0 until size) {
                val punishItem = ItemStack(Material.DIAMOND_SWORD)
                punishItem.editMeta { meta ->
                    meta.displayName(miniMessage.deserialize("<yellow>Infraction #$count</yellow>"))
                    val loreLines = mutableListOf(
                        miniMessage.deserialize("<gray>Commands: ${commands.size}</gray>"),
                        miniMessage.deserialize("")
                    )
                    commands.take(3).forEach { cmd ->
                        val displayCmd = if (cmd.length > 35) cmd.take(32) + "..." else cmd
                        loreLines.add(miniMessage.deserialize("<dark_gray>- $displayCmd</dark_gray>"))
                    }
                    if (commands.size > 3) {
                        loreLines.add(miniMessage.deserialize("<dark_gray>... and ${commands.size - 3} more</dark_gray>"))
                    }
                    loreLines.add(miniMessage.deserialize(""))
                    loreLines.add(miniMessage.deserialize("<yellow>Left-click to edit</yellow>"))
                    loreLines.add(miniMessage.deserialize("<red>Right-click to delete</red>"))
                    meta.lore(loreLines)
                }
                instance.setItemAt(slot, punishItem)
                instance.registerActionSlot(slot, SwearFilterActions.EDIT_PUNISHMENT)
            }
        }
    }

    private fun displayPunishmentEditView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        val commands = group.punishments[state.selectedPunishmentCount] ?: return
        val size = instance.inventory.size
        val itemsPerPage = (config.rows - 2) * 9
        
        // Back button
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back to Punishments</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.MANAGE_PUNISHMENTS)

        // Title
        val titleItem = ItemStack(Material.DIAMOND_SWORD)
        titleItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gold><bold>Infraction #${state.selectedPunishmentCount}</bold></gold>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Commands: ${commands.size}</gray>")
            ))
        }
        instance.setItemAt(4, titleItem)

        // Add command button
        val addItem = ItemStack(Material.EMERALD)
        addItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<green><bold>Add Command</bold></green>"))
            meta.lore(listOf(
                miniMessage.deserialize("<gray>Click to add a command to this punishment level</gray>"),
                miniMessage.deserialize("<gray>Use {player} as a placeholder for the player name</gray>")
            ))
        }
        instance.setItemAt(8, addItem)
        instance.registerActionSlot(8, SwearFilterActions.ADD_PUNISHMENT)

        // Display commands
        val startIndex = state.page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, commands.size)
        
        for (i in startIndex until endIndex) {
            val command = commands[i]
            val slot = 9 + (i - startIndex)
            
            if (slot in 0 until size - 9) {
                val cmdItem = ItemStack(Material.COMMAND_BLOCK)
                cmdItem.editMeta { meta ->
                    val displayCmd = if (command.length > 30) command.take(27) + "..." else command
                    meta.displayName(miniMessage.deserialize("<yellow>$displayCmd</yellow>"))
                    meta.lore(listOf(
                        if (command.length > 30) miniMessage.deserialize("<dark_gray>$command</dark_gray>") else null,
                        miniMessage.deserialize(""),
                        miniMessage.deserialize("<red>Click to remove</red>")
                    ).filterNotNull())
                }
                instance.setItemAt(slot, cmdItem)
                instance.registerActionSlot(slot, SwearFilterActions.REMOVE_PUNISHMENT)
            }
        }

        // Pagination
        displayPagination(instance, state, commands.size, itemsPerPage)
    }

    private fun displayPlayerInfractionsView(instance: ConfigurableMenuInstance<SwearFilterActions>, state: PlayerState) {
        val itemsPerPage = (config.rows - 1) * 9 - 2
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        
        val startIndex = state.page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, onlinePlayers.size)
        val pageItems = if (startIndex < onlinePlayers.size) {
            onlinePlayers.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Back button at slot 0
        val backItem = ItemStack(Material.ARROW)
        backItem.editMeta { meta ->
            meta.displayName(miniMessage.deserialize("<gray>Back to Main</gray>"))
        }
        instance.setItemAt(0, backItem)
        instance.registerActionSlot(0, SwearFilterActions.BACK_TO_MAIN)

        // Display player heads with infractions
        pageItems.forEachIndexed { index, onlinePlayer ->
            plugin.launch(Dispatchers.IO) {
                val infractions = infractionManager.getPlayerInfractions(onlinePlayer.uniqueId)
                
                plugin.launch(plugin.globalRegionDispatcher) {
                    val playerItem = ItemStack(Material.PLAYER_HEAD)
                    playerItem.editMeta { meta ->
                        if (meta is org.bukkit.inventory.meta.SkullMeta) {
                            meta.setOwningPlayer(onlinePlayer)
                        }
                        meta.displayName(miniMessage.deserialize("<yellow>${onlinePlayer.name}</yellow>"))
                        
                        val loreLines = mutableListOf<net.kyori.adventure.text.Component>()
                        if (infractions.isEmpty()) {
                            loreLines.add(miniMessage.deserialize("<green>No infractions</green>"))
                        } else {
                            loreLines.add(miniMessage.deserialize("<red>Infractions:</red>"))
                            infractions.forEach { (group, count) ->
                                loreLines.add(miniMessage.deserialize("<gray>- $group: <white>$count</white></gray>"))
                            }
                        }
                        loreLines.add(miniMessage.deserialize(""))
                        loreLines.add(miniMessage.deserialize("<yellow>Click to clear infractions</yellow>"))
                        meta.lore(loreLines)
                    }
                    val slot = 9 + index
                    instance.setItemAt(slot, playerItem)
                    instance.registerActionSlot(slot, SwearFilterActions.CLEAR_PLAYER_INFRACTIONS)
                }
            }
        }

        // Pagination
        displayPagination(instance, state, onlinePlayers.size, itemsPerPage)
    }

    private fun displayPagination(
        instance: ConfigurableMenuInstance<SwearFilterActions>,
        state: PlayerState,
        totalItems: Int,
        itemsPerPage: Int
    ) {
        val size = instance.inventory.size
        val maxPage = if (totalItems > 0) (totalItems - 1) / itemsPerPage else 0
        
        // Previous page button
        if (state.page > 0) {
            val prevItem = ItemStack(Material.ARROW)
            prevItem.editMeta { meta ->
                meta.displayName(miniMessage.deserialize("<gray>Previous Page</gray>"))
                meta.lore(listOf(
                    miniMessage.deserialize("<gray>Page ${state.page + 1}/${maxPage + 1}</gray>")
                ))
            }
            val prevSlot = size - 9
            if (prevSlot in 0 until size) {
                instance.setItemAt(prevSlot, prevItem)
                instance.registerActionSlot(prevSlot, SwearFilterActions.PREVIOUS_PAGE)
            }
        }
        
        // Next page button
        if (state.page < maxPage) {
            val nextItem = ItemStack(Material.ARROW)
            nextItem.editMeta { meta ->
                meta.displayName(miniMessage.deserialize("<gray>Next Page</gray>"))
                meta.lore(listOf(
                    miniMessage.deserialize("<gray>Page ${state.page + 1}/${maxPage + 1}</gray>")
                ))
            }
            val nextSlot = size - 1
            if (nextSlot in 0 until size) {
                instance.setItemAt(nextSlot, nextItem)
                instance.registerActionSlot(nextSlot, SwearFilterActions.NEXT_PAGE)
            }
        }
    }

    // --- Action Handlers ---

    private fun handleNextPage(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val itemsPerPage = (config.rows - 2) * 9
        
        val totalItems = when (state.view) {
            SwearFilterView.FILTER_LIST -> {
                state.editingGroups.getOrNull(state.selectedGroupIndex)?.filters?.size ?: 0
            }
            SwearFilterView.PUNISHMENT_EDIT -> {
                state.editingGroups.getOrNull(state.selectedGroupIndex)
                    ?.punishments?.get(state.selectedPunishmentCount)?.size ?: 0
            }
            SwearFilterView.PLAYER_INFRACTIONS -> Bukkit.getOnlinePlayers().size
            else -> 0
        }
        
        val maxPage = if (totalItems > 0) (totalItems - 1) / itemsPerPage else 0
        if (state.page < maxPage) {
            state.page++
            refreshDisplay(instance)
        }
    }

    private fun handleToggleFilterEnabled(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val currentConfig = configManager.config
        val newSwearFilterConfig = currentConfig.swearFilter.copy(
            enabled = !currentConfig.swearFilter.enabled
        )
        val newConfig = currentConfig.copy(swearFilter = newSwearFilterConfig)
        
        if (configManager.updateConfig(newConfig)) {
            ctx.player.sendMessage(miniMessage.deserialize(
                if (newSwearFilterConfig.enabled) "<green>Swear filter enabled!</green>"
                else "<yellow>Swear filter disabled!</yellow>"
            ))
        } else {
            ctx.player.sendMessage(miniMessage.deserialize("<red>Failed to save config!</red>"))
        }
        refreshDisplay(instance)
    }

    private fun handleViewGroupDetails(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val slot = ctx.slot
        
        // Calculate which group was clicked based on slot
        var groupIndex = 0
        for (i in state.editingGroups.indices) {
            val expectedSlot = 10 + i + (i / 7) * 2
            if (expectedSlot == slot) {
                groupIndex = i
                break
            }
        }
        
        if (groupIndex < state.editingGroups.size) {
            state.selectedGroupIndex = groupIndex
            state.view = SwearFilterView.GROUP_EDIT
            refreshDisplay(instance)
        }
    }

    private fun handleCreateGroup(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter a name for the new filter group (or 'cancel' to cancel):</yellow>"))
        instance.close()
        
        chatInputService.requestInput(ctx.player, { name ->
            val state = getState(ctx.player)
            val newGroup = FilterGroup(
                name = name,
                type = "smart",
                distance = 2,
                filters = emptyList(),
                punishments = emptyMap()
            )
            state.editingGroups.add(newGroup)
            state.selectedGroupIndex = state.editingGroups.size - 1
            state.view = SwearFilterView.GROUP_EDIT
            
            saveGroupsToConfig(ctx.player)
            
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<green>Created group: $name</green>"))
                val newInstance = open(ctx.player)
                refreshDisplay(newInstance)
            }
        }, {
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<gray>Group creation cancelled.</gray>"))
                openForAdmin(ctx.player)
            }
        })
    }

    private fun handleEditGroupName(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter new name for '${group.name}' (or 'cancel' to cancel):</yellow>"))
        instance.close()
        
        chatInputService.requestInput(ctx.player, { newName ->
            state.editingGroups[state.selectedGroupIndex] = group.copy(name = newName)
            saveGroupsToConfig(ctx.player)
            
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<green>Renamed to: $newName</green>"))
                val newInstance = open(ctx.player)
                refreshDisplay(newInstance)
            }
        }, {
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<gray>Rename cancelled.</gray>"))
                val newInstance = open(ctx.player)
                refreshDisplay(newInstance)
            }
        })
    }

    private fun handleEditGroupDistance(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        val change = when {
            ctx.isShiftClick && ctx.isRightClick -> -5
            ctx.isShiftClick && ctx.isLeftClick -> 5
            ctx.isRightClick -> -1
            else -> 1
        }
        
        val newDistance = (group.distance + change).coerceIn(0, 100)
        state.editingGroups[state.selectedGroupIndex] = group.copy(distance = newDistance)
        saveGroupsToConfig(ctx.player)
        
        ctx.player.sendMessage(miniMessage.deserialize("<gray>Distance set to: <white>$newDistance</white></gray>"))
        refreshDisplay(instance)
    }

    private fun handleDeleteGroup(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        // Require shift-click to confirm deletion
        if (!ctx.isShiftClick) {
            ctx.player.sendMessage(miniMessage.deserialize("<red>Shift-click to confirm deletion of '${group.name}'</red>"))
            return
        }
        
        state.editingGroups.removeAt(state.selectedGroupIndex)
        state.selectedGroupIndex = -1
        state.view = SwearFilterView.MAIN
        
        saveGroupsToConfig(ctx.player)
        
        ctx.player.sendMessage(miniMessage.deserialize("<red>Deleted group: ${group.name}</red>"))
        refreshDisplay(instance)
    }

    private fun handleAddFilter(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter the filter pattern to add (or 'cancel' to cancel):</yellow>"))
        if (group.type.lowercase() == "regex") {
            ctx.player.sendMessage(miniMessage.deserialize("<gray>Tip: Use regex patterns like (?i)\\bword\\b</gray>"))
        }
        instance.close()
        
        chatInputService.requestInput(ctx.player, { pattern ->
            val newFilters = group.filters + pattern
            state.editingGroups[state.selectedGroupIndex] = group.copy(filters = newFilters)
            saveGroupsToConfig(ctx.player)
            
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<green>Added filter: $pattern</green>"))
                val newInstance = open(ctx.player)
                refreshDisplay(newInstance)
            }
        }, {
            plugin.launch(plugin.globalRegionDispatcher) {
                ctx.player.sendMessage(miniMessage.deserialize("<gray>Filter add cancelled.</gray>"))
                val newInstance = open(ctx.player)
                refreshDisplay(newInstance)
            }
        })
    }

    private fun handleRemoveFilter(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        val slot = ctx.slot
        val itemsPerPage = (config.rows - 2) * 9
        val filterIndex = state.page * itemsPerPage + (slot - 9)
        
        if (filterIndex < 0 || filterIndex >= group.filters.size) return
        
        val removedFilter = group.filters[filterIndex]
        val newFilters = group.filters.toMutableList().apply { removeAt(filterIndex) }
        state.editingGroups[state.selectedGroupIndex] = group.copy(filters = newFilters)
        saveGroupsToConfig(ctx.player)
        
        ctx.player.sendMessage(miniMessage.deserialize("<red>Removed filter: $removedFilter</red>"))
        refreshDisplay(instance)
    }

    private fun handleAddPunishment(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        if (state.view == SwearFilterView.PUNISHMENT_EDIT) {
            // Adding a command to existing punishment level
            ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter the command to add (use {player} for player name, or 'cancel'):</yellow>"))
            instance.close()
            
            chatInputService.requestInput(ctx.player, { command ->
                val currentCommands = group.punishments[state.selectedPunishmentCount] ?: emptyList()
                val newPunishments = group.punishments.toMutableMap()
                newPunishments[state.selectedPunishmentCount] = currentCommands + command
                state.editingGroups[state.selectedGroupIndex] = group.copy(punishments = newPunishments)
                saveGroupsToConfig(ctx.player)
                
                plugin.launch(plugin.globalRegionDispatcher) {
                    ctx.player.sendMessage(miniMessage.deserialize("<green>Added command: $command</green>"))
                    val newInstance = open(ctx.player)
                    refreshDisplay(newInstance)
                }
            }, {
                plugin.launch(plugin.globalRegionDispatcher) {
                    ctx.player.sendMessage(miniMessage.deserialize("<gray>Command add cancelled.</gray>"))
                    val newInstance = open(ctx.player)
                    refreshDisplay(newInstance)
                }
            })
        } else {
            // Creating a new punishment level
            ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter the infraction count for this punishment (e.g., 1, 3, 5, or 'cancel'):</yellow>"))
            instance.close()
            
            chatInputService.requestInput(ctx.player, { countStr ->
                val count = countStr.toIntOrNull()
                if (count == null || count < 1) {
                    plugin.launch(plugin.globalRegionDispatcher) {
                        ctx.player.sendMessage(miniMessage.deserialize("<red>Invalid number. Please enter a positive integer.</red>"))
                        val newInstance = open(ctx.player)
                        refreshDisplay(newInstance)
                    }
                    return@requestInput
                }
                
                if (group.punishments.containsKey(count)) {
                    state.selectedPunishmentCount = count
                    state.view = SwearFilterView.PUNISHMENT_EDIT
                    plugin.launch(plugin.globalRegionDispatcher) {
                        ctx.player.sendMessage(miniMessage.deserialize("<yellow>Editing existing punishment level #$count</yellow>"))
                        val newInstance = open(ctx.player)
                        refreshDisplay(newInstance)
                    }
                } else {
                    ctx.player.sendMessage(miniMessage.deserialize("<yellow>Enter the command for infraction #$count (use {player}, or 'cancel'):</yellow>"))
                    
                    chatInputService.requestInput(ctx.player, { command ->
                        val newPunishments = group.punishments.toMutableMap()
                        newPunishments[count] = listOf(command)
                        state.editingGroups[state.selectedGroupIndex] = group.copy(punishments = newPunishments)
                        state.selectedPunishmentCount = count
                        state.view = SwearFilterView.PUNISHMENT_EDIT
                        saveGroupsToConfig(ctx.player)
                        
                        plugin.launch(plugin.globalRegionDispatcher) {
                            ctx.player.sendMessage(miniMessage.deserialize("<green>Created punishment level #$count</green>"))
                            val newInstance = open(ctx.player)
                            refreshDisplay(newInstance)
                        }
                    }, {
                        plugin.launch(plugin.globalRegionDispatcher) {
                            ctx.player.sendMessage(miniMessage.deserialize("<gray>Punishment creation cancelled.</gray>"))
                            val newInstance = open(ctx.player)
                            refreshDisplay(newInstance)
                        }
                    })
                }
            }, {
                plugin.launch(plugin.globalRegionDispatcher) {
                    ctx.player.sendMessage(miniMessage.deserialize("<gray>Punishment creation cancelled.</gray>"))
                    val newInstance = open(ctx.player)
                    refreshDisplay(newInstance)
                }
            })
        }
    }

    private fun handleEditPunishment(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        
        val slot = ctx.slot
        val sortedPunishments = group.punishments.entries.sortedBy { it.key }
        
        // Calculate which punishment was clicked
        var punishmentIndex = 0
        for (i in sortedPunishments.indices) {
            val expectedSlot = 10 + i + (i / 7) * 2
            if (expectedSlot == slot) {
                punishmentIndex = i
                break
            }
        }
        
        if (punishmentIndex >= sortedPunishments.size) return
        
        val (count, _) = sortedPunishments[punishmentIndex]
        
        if (ctx.isRightClick) {
            // Delete entire punishment level
            if (!ctx.isShiftClick) {
                ctx.player.sendMessage(miniMessage.deserialize("<red>Shift+right-click to delete punishment level #$count</red>"))
                return
            }
            
            val newPunishments = group.punishments.toMutableMap()
            newPunishments.remove(count)
            state.editingGroups[state.selectedGroupIndex] = group.copy(punishments = newPunishments)
            saveGroupsToConfig(ctx.player)
            
            ctx.player.sendMessage(miniMessage.deserialize("<red>Deleted punishment level #$count</red>"))
            refreshDisplay(instance)
        } else {
            // Edit this punishment level
            state.selectedPunishmentCount = count
            state.view = SwearFilterView.PUNISHMENT_EDIT
            state.page = 0
            refreshDisplay(instance)
        }
    }

    private fun handleRemovePunishment(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        val commands = group.punishments[state.selectedPunishmentCount] ?: return
        
        val slot = ctx.slot
        val itemsPerPage = (config.rows - 2) * 9
        val cmdIndex = state.page * itemsPerPage + (slot - 9)
        
        if (cmdIndex < 0 || cmdIndex >= commands.size) return
        
        val removedCmd = commands[cmdIndex]
        val newCommands = commands.toMutableList().apply { removeAt(cmdIndex) }
        
        val newPunishments = group.punishments.toMutableMap()
        if (newCommands.isEmpty()) {
            newPunishments.remove(state.selectedPunishmentCount)
            state.view = SwearFilterView.PUNISHMENT_LIST
        } else {
            newPunishments[state.selectedPunishmentCount] = newCommands
        }
        state.editingGroups[state.selectedGroupIndex] = group.copy(punishments = newPunishments)
        saveGroupsToConfig(ctx.player)
        
        ctx.player.sendMessage(miniMessage.deserialize("<red>Removed command: $removedCmd</red>"))
        refreshDisplay(instance)
    }

    private fun handleClearInfractions(ctx: ClickContext, instance: ConfigurableMenuInstance<SwearFilterActions>) {
        val state = getState(ctx.player)
        val slot = ctx.slot
        if (slot < 9) return
        
        val itemsPerPage = (config.rows - 1) * 9 - 2
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        
        val index = state.page * itemsPerPage + (slot - 9)
        if (index >= onlinePlayers.size) return
        
        val targetPlayer = onlinePlayers[index]
        
        plugin.launch(Dispatchers.IO) {
            val success = infractionManager.resetAllInfractions(targetPlayer.uniqueId)
            
            plugin.launch(plugin.globalRegionDispatcher) {
                if (success) {
                    ctx.player.sendMessage(miniMessage.deserialize(
                        "<green>Cleared all infractions for <yellow>${targetPlayer.name}</yellow></green>"
                    ))
                    refreshDisplay(instance)
                } else {
                    ctx.player.sendMessage(miniMessage.deserialize(
                        "<red>Failed to clear infractions for ${targetPlayer.name}</red>"
                    ))
                }
            }
        }
    }

    // --- Helper Methods ---

    private fun setGroupType(player: Player, type: String) {
        val state = getState(player)
        val group = state.editingGroups.getOrNull(state.selectedGroupIndex) ?: return
        state.editingGroups[state.selectedGroupIndex] = group.copy(type = type)
        saveGroupsToConfig(player)
        player.sendMessage(miniMessage.deserialize("<gray>Type set to: <white>$type</white></gray>"))
    }

    private fun getMaterialForType(type: String): Material {
        return when (type.lowercase()) {
            "regex" -> Material.COMPARATOR
            "smart", "mixed", "auto" -> Material.REDSTONE
            "levenshtein" -> Material.REPEATER
            "dice-sorensen", "dice" -> Material.HOPPER
            else -> Material.BARRIER
        }
    }

    private fun saveGroupsToConfig(player: Player) {
        val state = getState(player)
        val currentConfig = configManager.config
        val newSwearFilterConfig = currentConfig.swearFilter.copy(
            filterGroups = state.editingGroups.toList()
        )
        val newConfig = currentConfig.copy(swearFilter = newSwearFilterConfig)
        
        if (!configManager.updateConfig(newConfig)) {
            player.sendMessage(miniMessage.deserialize("<red>Warning: Failed to save changes to config file!</red>"))
        }
    }
}
