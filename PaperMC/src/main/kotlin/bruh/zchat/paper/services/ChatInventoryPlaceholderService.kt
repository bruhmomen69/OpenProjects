package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.snapshots.FileInventorySnapshotStore
import bruh.zchat.paper.services.snapshots.InventorySnapshotSerializer
import bruh.zchat.paper.services.snapshots.InventorySnapshotStore
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Service for processing inventory placeholders in chat messages.
 * This handles user input placeholders like {inv}, [inv], [ender], [armor], [hand], [pos], [health]
 */
class ChatInventoryPlaceholderService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val snapshotStore: InventorySnapshotStore,
    private val serverInstanceId: String,
    private val placeholderAPIService: PlaceholderAPIService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatInventoryPlaceholderService::class.java)

    // Patterns for different placeholder formats
    private val inventoryPatterns = mapOf(
        "inv" to listOf(Pattern.compile("\\{inv\\}"), Pattern.compile("\\[inv\\]")),
        "ender" to listOf(Pattern.compile("\\[ender\\]")),
        "armor" to listOf(Pattern.compile("\\[armor\\]")),
        "hand" to listOf(Pattern.compile("\\[hand\\]")),
        "pos" to listOf(Pattern.compile("\\[pos\\]")),
        "health" to listOf(Pattern.compile("\\[health\\]"))
    )

    /**
     * Processes a raw chat message string and creates a Component with inventory placeholders
     * This creates a hybrid component with unparsed text and parsed inventory components
     */
    fun processRawMessage(player: Player, message: String): Component = runBlocking(plugin.entityDispatcher(player)) {
        processRawMessageInternal(player, message)
    }

    private suspend fun processRawMessageInternal(player: Player, message: String): Component {
        val config = configManager.config.inventoryPlaceholders

        // Check if inventory placeholders are enabled
        if (!config.enabled) {
            return Component.text(message)
        }

        // Check if player has permission
        if (!player.hasPermission(configManager.config.permissions.inventoryPlaceholderPermission)) {
            // Check if message contains placeholders and send error message
            if (containsInventoryPlaceholders(message)) {
                player.sendMessage(
                    messageFormattingService.formatMessage(
                        configManager.messages.inventoryPlaceholders.noPermission,
                        player,
                        processUrls = false,
                        processMentions = false
                    )
                )
            }
            return Component.text(message)
        }

        // Check if message contains any inventory placeholders
        if (!containsInventoryPlaceholders(message)) {
            return Component.text(message)
        }

        return try {
            createHybridComponent(player, message)
        } catch (e: Exception) {
            logger.warn("Failed to process inventory placeholders for player ${player.name}", e)
            Component.text(message)
        }
    }

    /**
     * Checks if a message contains any inventory placeholders
     */
    private fun containsInventoryPlaceholders(message: String): Boolean {
        return inventoryPatterns.values.flatten().any { pattern ->
            pattern.matcher(message).find()
        }
    }

    /**
     * Creates a hybrid component with unparsed text segments and parsed inventory components
     */
    private suspend fun createHybridComponent(player: Player, messageText: String): Component {
        val builder = Component.text()
        var currentIndex = 0
        val replacements = mutableListOf<PlaceholderReplacement>()
        val pendingSnapshots = mutableListOf<PendingSnapshot>()

        // Find all inventory placeholders and their positions
        for ((type, patterns) in inventoryPatterns) {
            for (pattern in patterns) {
                val matcher = pattern.matcher(messageText)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    val placeholder = matcher.group()

                    try {
                        val placeholderType = PlaceholderType.valueOf(type.uppercase())

                        // Check if this specific placeholder type is enabled
                        if (!isPlaceholderTypeEnabled(placeholderType)) {
                            val errorMsg = configManager.messages.inventoryPlaceholders.placeholderDisabled
                                .replace("{type}", placeholderType.displayName)
                            val errorComponent = Component.text("[$type: disabled]").color(NamedTextColor.RED)
                            replacements.add(PlaceholderReplacement(start, end, placeholder, errorComponent))
                            continue
                        }

                        val component = if (placeholderType in listOf(PlaceholderType.POS, PlaceholderType.HEALTH)) {
                            createNonInventoryComponent(player, placeholderType)
                        } else {
                            val pending = buildInventorySnapshot(player, placeholderType)
                            pendingSnapshots.add(pending)
                            createInventoryComponent(player, placeholderType, pending.snapshotId)
                        }

                        replacements.add(PlaceholderReplacement(start, end, placeholder, component))
                    } catch (e: Exception) {
                        logger.warn("Failed to create component for placeholder $placeholder", e)
                        val errorComponent = Component.text("[$type: error]").color(NamedTextColor.RED)
                        replacements.add(PlaceholderReplacement(start, end, placeholder, errorComponent))
                    }
                }
            }
        }

        // Sort replacements by position (ascending for building)
        val sortedReplacements = replacements.sortedBy { it.start }

        if (pendingSnapshots.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (pending in pendingSnapshots) {
                    val bytes = InventorySnapshotSerializer.serialize(pending.snapshot)
                    val saved = snapshotStore.save(
                        snapshotId = pending.snapshotId,
                        serverInstanceId = serverInstanceId,
                        createdAtEpochMs = pending.createdAtEpochMs,
                        expiresAtEpochMs = pending.expiresAtEpochMs,
                        data = bytes
                    )
                    if (!saved) {
                        throw IllegalStateException("Failed to save inventory snapshot")
                    }
                }
            }
        }

        // Build the hybrid component
        for (replacement in sortedReplacements) {
            // Add unparsed text before the placeholder
            if (replacement.start > currentIndex) {
                builder.append(Component.text(messageText.substring(currentIndex, replacement.start)))
            }

            // Add the parsed component
            builder.append(replacement.component)

            currentIndex = replacement.end
        }

        // Add remaining unparsed text after the last placeholder
        if (currentIndex < messageText.length) {
            builder.append(Component.text(messageText.substring(currentIndex)))
        }

        return builder.build()
    }

    private data class PendingSnapshot(
        val snapshotId: String,
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long,
        val snapshot: InventorySnapshot
    )

    /**
     * Checks if a specific placeholder type is enabled in config
     */
    private fun isPlaceholderTypeEnabled(type: PlaceholderType): Boolean {
        val config = configManager.config.inventoryPlaceholders
        return when (type) {
            PlaceholderType.INV -> config.enableInventoryPlaceholder
            PlaceholderType.ENDER -> config.enableEnderPlaceholder
            PlaceholderType.ARMOR -> config.enableArmorPlaceholder
            PlaceholderType.HAND -> config.enableHandPlaceholder
            PlaceholderType.POS -> config.enablePositionPlaceholder
            PlaceholderType.HEALTH -> config.enableHealthPlaceholder
        }
    }

    /**
     * Creates a non-inventory component (for pos, health)
     */
    private suspend fun createNonInventoryComponent(player: Player, type: PlaceholderType): Component {
        val displayComponent = getDisplayComponent(player, type)
        val hoverText = getHoverText(player, type)

        val clickAction = when (type) {
            PlaceholderType.POS -> {
                val config = configManager.config.inventoryPlaceholders.clickActions
                val command = replacePlaceholders(config.positionCommand, player)
                if (config.positionActionType == "suggest") {
                    ClickEvent.suggestCommand(command)
                } else {
                    ClickEvent.runCommand(command)
                }
            }

            PlaceholderType.HEALTH -> {
                val config = configManager.config.inventoryPlaceholders.clickActions
                val command = replacePlaceholders(config.healthCommand, player)
                if (config.healthActionType == "suggest") {
                    ClickEvent.suggestCommand(command)
                } else {
                    ClickEvent.runCommand(command)
                }
            }

            else -> null
        }

        val component = displayComponent.hoverEvent(HoverEvent.showText(hoverText))

        return if (clickAction != null) component.clickEvent(clickAction) else component
    }

    /**
     * Creates a clickable inventory component
     */
    private suspend fun createInventoryComponent(player: Player, type: PlaceholderType, snapshotId: String): Component {
        val displayComponent = getDisplayComponent(player, type)
        val hoverText = getHoverText(player, type)

        return displayComponent
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/chatplugin viewinventory $snapshotId"))
    }

    /**
     * Gets display text for any placeholder type
     */
    private suspend fun getDisplayComponent(player: Player, type: PlaceholderType): Component {
        val config = configManager.config.inventoryPlaceholders

        return when (type) {
            PlaceholderType.INV, PlaceholderType.ENDER, PlaceholderType.ARMOR, PlaceholderType.HAND -> {
                val itemCount = getItemCount(player, type)
                val placeholders = mapOf(
                    "type" to type.displayName,
                    "count" to itemCount.toString()
                )
                // Use MiniMessage formatting with proper placeholders
                messageFormattingService.formatMessage(
                    configManager.messages.inventoryPlaceholders.inventoryDisplayFormat,
                    player,
                    placeholders,
                    processUrls = false,
                    processMentions = false
                )
            }

            PlaceholderType.POS -> {
                val loc = player.location
                val placeholders = mapOf(
                    "x" to loc.blockX.toString(),
                    "y" to loc.blockY.toString(),
                    "z" to loc.blockZ.toString(),
                    "world" to (loc.world?.name ?: "Unknown")
                )
                messageFormattingService.formatMessage(
                    configManager.messages.inventoryPlaceholders.positionDisplayFormat,
                    player,
                    placeholders,
                    processUrls = false,
                    processMentions = false
                )
            }

            PlaceholderType.HEALTH -> {
                val health = player.health.roundToInt()
                val maxHealth = {
                    try {
                        player.getAttribute(Attribute.MAX_HEALTH)?.value?.roundToInt() ?: 20
                    } catch (e: NoSuchFieldError) {
                        maxHealthBridgeFn(player).roundToInt()
                    }
                }.invoke()
                val food = player.foodLevel
                val saturation = player.saturation.roundToInt()
                val placeholders = mapOf(
                    "health" to health.toString(),
                    "max_health" to maxHealth.toString(),
                    "food" to food.toString(),
                    "saturation" to saturation.toString()
                )
                messageFormattingService.formatMessage(
                    configManager.messages.inventoryPlaceholders.healthDisplayFormat,
                    player,
                    placeholders,
                    processUrls = false,
                    processMentions = false
                )
            }
        }
    }

    private fun maxHealthBridgeFn(player: Player): Double {
        return player.maxHealth
    }

    /**
     * Gets hover text for any placeholder type
     */
    private suspend fun getHoverText(player: Player, type: PlaceholderType): Component {
        val config = configManager.config.inventoryPlaceholders

        return when (type) {
            PlaceholderType.INV, PlaceholderType.ENDER, PlaceholderType.ARMOR, PlaceholderType.HAND -> {
                val preview = getItemPreview(player, type)
                val stringPlaceholders = mapOf(
                    "player" to player.name,
                    "type" to type.displayName
                )
                val componentPlaceholders = mapOf(
                    "preview" to preview
                )
                // Parse the hover text through MiniMessage with proper placeholders
                messageFormattingService.formatMessageComponent(
                    configManager.messages.inventoryPlaceholders.inventoryHoverFormat.replace("\\n", "\n"),
                    player,
                    stringPlaceholders,
                    componentPlaceholders,
                    processUrls = false,
                    processMentions = false,
                    allowColors = true,
                    allowFormatting = true
                )
            }

            PlaceholderType.POS -> {
                val loc = player.location
                val biome = try {
                    loc.world?.getBiome(loc)?.toString()?.replace("_", " ")?.lowercase()
                        ?.replaceFirstChar { it.uppercase() } ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
                val placeholders = mapOf(
                    "player" to player.name,
                    "x" to loc.blockX.toString(),
                    "y" to loc.blockY.toString(),
                    "z" to loc.blockZ.toString(),
                    "world" to (loc.world?.name ?: "Unknown"),
                    "biome" to biome
                )
                messageFormattingService.formatMessage(
                    configManager.messages.inventoryPlaceholders.positionHoverFormat.replace("\\n", "\n"),
                    player,
                    placeholders,
                    processUrls = false,
                    processMentions = false
                )
            }

            PlaceholderType.HEALTH -> {
                val health = player.health.roundToInt()
                val maxHealth = {
                    try {
                        player.getAttribute(Attribute.MAX_HEALTH)?.value?.roundToInt() ?: 20
                    } catch (e: NoSuchFieldError) {
                        maxHealthBridgeFn(player).roundToInt()
                    }
                }.invoke()
                val food = player.foodLevel
                val saturation = player.saturation.roundToInt()
                val effects = getEffectsText(player)
                val placeholders = mapOf(
                    "player" to player.name,
                    "health" to health.toString(),
                    "max_health" to maxHealth.toString(),
                    "food" to food.toString(),
                    "saturation" to saturation.toString(),
                    "effects" to effects
                )
                messageFormattingService.formatMessage(
                    configManager.messages.inventoryPlaceholders.healthHoverFormat.replace("\\n", "\n"),
                    player,
                    placeholders,
                    processUrls = false,
                    processMentions = false
                )
            }
        }
    }

    /**
     * Gets item count for inventory types
     */
    private suspend fun getItemCount(player: Player, type: PlaceholderType): Int {
        return when (type) {
            PlaceholderType.INV -> player.inventory.contents.count { it != null && it.type != Material.AIR }
            PlaceholderType.ENDER -> player.enderChest.contents.count { it != null && it.type != Material.AIR }
            PlaceholderType.ARMOR -> player.inventory.armorContents.count { it != null && it.type != Material.AIR }
            PlaceholderType.HAND -> {
                val mainHand = player.inventory.itemInMainHand
                val offHand = player.inventory.itemInOffHand
                var count = 0
                if (mainHand != null && mainHand.type != Material.AIR) count++
                if (offHand != null && offHand.type != Material.AIR) count++
                count
            }

            else -> 0
        }
    }

    /**
     * Gets item preview component for hover
     */
    private suspend fun getItemPreview(player: Player, type: PlaceholderType): Component {
        val config = configManager.config.inventoryPlaceholders
        val items = getItemsForType(player, type)
        val nonEmptyItems = items.filterNotNull().filter { it.type != Material.AIR }.take(config.maxPreviewItems)

        if (nonEmptyItems.isEmpty()) {
            return messageFormattingService.formatMessage(
                configManager.messages.inventoryPlaceholders.emptyInventoryText,
                player,
                processUrls = false,
                processMentions = false
            )
        }

        val builder = Component.text()
        var first = true

        for (item in nonEmptyItems) {
            if (!first) {
                builder.append(Component.newline())
            }
            first = false

            val itemName = item.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            val placeholders = mapOf(
                "amount" to item.amount.toString(),
                "item" to itemName
            )
            // Use MiniMessage formatting for item preview with safe placeholders
            val itemComponent = messageFormattingService.formatMessage(
                configManager.messages.inventoryPlaceholders.itemPreviewFormat,
                player,
                placeholders,
                processUrls = false,
                processMentions = false
            )
            builder.append(itemComponent)
        }

        if (items.size > config.maxPreviewItems) {
            builder.append(Component.newline())
            val placeholders = mapOf(
                "count" to (items.size - config.maxPreviewItems).toString()
            )
            val moreComponent = messageFormattingService.formatMessage(
                configManager.messages.inventoryPlaceholders.moreItemsText,
                player,
                placeholders,
                processUrls = false,
                processMentions = false
            )
            builder.append(moreComponent)
        }

        return builder.build()
    }

    /**
     * Gets effects text for health hover
     */
    private suspend fun getEffectsText(player: Player): String {
        val effects = player.activePotionEffects
        if (effects.isEmpty()) {
            return "No active effects"
        }

        return effects.joinToString("\\n") { effect ->
            val name = effect.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            val level = if (effect.amplifier > 0) " ${effect.amplifier + 1}" else ""
            val duration = "${effect.duration / 20}s"
            "$name$level ($duration)"
        }
    }

    /**
     * Gets items for the specified inventory type
     */
    private suspend fun getItemsForType(player: Player, type: PlaceholderType): Array<ItemStack?> {
        return when (type) {
            PlaceholderType.INV -> player.inventory.contents
            PlaceholderType.ENDER -> player.enderChest.contents
            PlaceholderType.ARMOR -> player.inventory.armorContents
            PlaceholderType.HAND -> arrayOf(player.inventory.itemInMainHand, player.inventory.itemInOffHand)
            else -> emptyArray()
        }
    }

    /**
     * Saves an inventory snapshot to disk and returns the snapshot ID
     */
    private suspend fun buildInventorySnapshot(player: Player, type: PlaceholderType): PendingSnapshot {
        val now = System.currentTimeMillis()
        val snapshotId = "${player.uniqueId}_${type.name.lowercase()}_$now"
        val snapshot = InventorySnapshot(
            playerId = player.uniqueId,
            playerName = player.name,
            type = type,
            items = cloneItems(getItemsForType(player, type)),
            timestamp = now
        )

        val retentionMillis = configManager.config.inventoryPlaceholders.snapshotRetentionMinutes * 60 * 1000L
        val createdAt = snapshot.timestamp
        val expiresAt = createdAt + retentionMillis

        return PendingSnapshot(
            snapshotId = snapshotId,
            createdAtEpochMs = createdAt,
            expiresAtEpochMs = expiresAt,
            snapshot = snapshot
        )
    }

    /**
     * Replaces placeholders in click command strings
     */
    private fun replacePlaceholders(command: String, player: Player): String {
        val loc = player.location
        var result = command
            .replace("{player}", player.name)
            .replace("{x}", loc.blockX.toString())
            .replace("{y}", loc.blockY.toString())
            .replace("{z}", loc.blockZ.toString())
            .replace("{world}", loc.world?.name ?: "Unknown")

        // Process PlaceholderAPI placeholders if available
        if (placeholderAPIService != null && placeholderAPIService.isEnabled()) {
            result = placeholderAPIService.processPlaceholderAPI(player, result)
        }

        return result
    }

    private fun cloneItems(items: Array<ItemStack?>): Array<ItemStack?> {
        return Array(items.size) { idx ->
            items[idx]?.clone()
        }
    }

    /**
     * Loads and displays an inventory snapshot to a player
     */
    suspend fun viewInventorySnapshot(viewer: Player, snapshotId: String): Boolean = withContext(Dispatchers.IO) {
        val stored = snapshotStore.load(snapshotId, serverInstanceId)
        val isFsBackend = snapshotStore is FileInventorySnapshotStore
        if (stored == null) {
            withContext(plugin.entityDispatcher(viewer)) {
                viewer.sendMessage(
                    messageFormattingService.formatMessage(
                        configManager.messages.inventoryPlaceholders.snapshotNotFound,
                        viewer,
                        processUrls = false,
                        processMentions = false
                    )
                )
            }
            return@withContext false
        }

        if (!isFsBackend && stored.expiresAtEpochMs <= System.currentTimeMillis()) {
            withContext(plugin.entityDispatcher(viewer)) {
                viewer.sendMessage(
                    messageFormattingService.formatMessage(
                        configManager.messages.inventoryPlaceholders.snapshotNotFound,
                        viewer,
                        processUrls = false,
                        processMentions = false
                    )
                )
            }
            return@withContext false
        }

        val snapshot = try {
            InventorySnapshotSerializer.deserialize(stored.data)
        } catch (e: Exception) {
            logger.error("Failed to deserialize inventory snapshot $snapshotId", e)
            withContext(plugin.entityDispatcher(viewer)) {
                viewer.sendMessage(
                    messageFormattingService.formatMessage(
                        configManager.messages.inventoryPlaceholders.viewFailed,
                        viewer,
                        processUrls = false,
                        processMentions = false
                    )
                )
            }
            return@withContext false
        }

        return@withContext try {
            withContext(plugin.entityDispatcher(viewer)) {
                val inventory = createReadOnlyInventory(snapshot)
                viewer.openInventory(inventory)
            }

            // Cleanup behavior: invoke store cleanup to match original FS post-view cleanup.
            // Run async so the inventory open is not delayed.
            plugin.launch(Dispatchers.IO) {
                snapshotStore.cleanupExpired(System.currentTimeMillis())
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to open inventory snapshot $snapshotId", e)
            withContext(plugin.entityDispatcher(viewer)) {
                viewer.sendMessage(
                    messageFormattingService.formatMessage(
                        configManager.messages.inventoryPlaceholders.viewFailed,
                        viewer,
                        processUrls = false,
                        processMentions = false
                    )
                )
            }
            false
        }
    }

    /**
     * Creates a read-only inventory from a snapshot
     */
    private fun createReadOnlyInventory(snapshot: InventorySnapshot): Inventory {
        val title = "${snapshot.playerName}'s ${snapshot.type.displayName}"
        val size = when (snapshot.type) {
            PlaceholderType.INV -> max(ceil((snapshot.items.size - 1) / 9.0) * 9, 45.0).roundToInt()  // 6 rows for main inventory
            PlaceholderType.ENDER -> max(ceil((snapshot.items.size - 1) / 9.0) * 9, 27.0).roundToInt() // 3-6 rows for ender chest
            PlaceholderType.ARMOR -> 9 // 1 row for armor
            PlaceholderType.HAND -> 9 // 1 row for hand items
            else -> 9
        }

        val inventory = Bukkit.createInventory(null, size, Component.text(title))

        // Add items to inventory
        for (i in snapshot.items.indices) {
            if (i < inventory.size && snapshot.items[i] != null) {
                inventory.setItem(i, snapshot.items[i])
            }
        }

        return inventory
    }

    /**
     * Represents different types of placeholders
     */
    enum class PlaceholderType(val displayName: String) {
        INV("Inventory"),
        ENDER("Ender Chest"),
        ARMOR("Armor"),
        HAND("Hand"),
        POS("Position"),
        HEALTH("Health")
    }

    /**
     * Data class for placeholder replacements
     */
    private data class PlaceholderReplacement(
        val start: Int,
        val end: Int,
        val placeholder: String,
        val component: Component
    )

    /**
     * Serializable snapshot of an inventory
     */
    data class InventorySnapshot(
        val playerId: UUID,
        val playerName: String,
        val type: PlaceholderType,
        val items: Array<ItemStack?>,
        val timestamp: Long
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as InventorySnapshot

            if (playerId != other.playerId) return false
            if (playerName != other.playerName) return false
            if (type != other.type) return false
            if (!items.contentEquals(other.items)) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = playerId.hashCode()
            result = 31 * result + playerName.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + items.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
}