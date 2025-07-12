package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.config.ConfigManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern
import kotlin.math.roundToInt

/**
 * Service for processing inventory placeholders in chat messages.
 * This handles user input placeholders like {inv}, [inv], [ender], [armor], [hand], [pos], [health]
 */
class ChatInventoryPlaceholderService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val logger = LoggerFactory.getLogger(ChatInventoryPlaceholderService::class.java)
    private val inventoryDataDir: Path = plugin.dataFolder.toPath().resolve("inventory_snapshots")

    // Patterns for different placeholder formats
    private val inventoryPatterns = mapOf(
        "inv" to listOf(Pattern.compile("\\{inv\\}"), Pattern.compile("\\[inv\\]")),
        "ender" to listOf(Pattern.compile("\\[ender\\]")),
        "armor" to listOf(Pattern.compile("\\[armor\\]")),
        "hand" to listOf(Pattern.compile("\\[hand\\]")),
        "pos" to listOf(Pattern.compile("\\[pos\\]")),
        "health" to listOf(Pattern.compile("\\[health\\]"))
    )
    
    init {
        // Create inventory data directory if it doesn't exist
        try {
            Files.createDirectories(inventoryDataDir)
        } catch (e: Exception) {
            logger.error("Failed to create inventory snapshots directory", e)
        }
    }
    
    /**
     * Processes a raw chat message string and creates a Component with inventory placeholders
     * This creates a hybrid component with unparsed text and parsed inventory components
     */
    fun processRawMessage(player: Player, message: String): Component {
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
                        configManager.config.messages.inventoryPlaceholders.noPermission,
                        player
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
    private fun createHybridComponent(player: Player, messageText: String): Component {
        val builder = Component.text()
        var currentIndex = 0
        val replacements = mutableListOf<PlaceholderReplacement>()
        
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
                            val errorMsg = configManager.config.messages.inventoryPlaceholders.placeholderDisabled
                                .replace("{type}", placeholderType.displayName)
                            val errorComponent = Component.text("[$type: disabled]").color(NamedTextColor.RED)
                            replacements.add(PlaceholderReplacement(start, end, placeholder, errorComponent))
                            continue
                        }
                        
                        val component = if (placeholderType in listOf(PlaceholderType.POS, PlaceholderType.HEALTH)) {
                            createNonInventoryComponent(player, placeholderType)
                        } else {
                            val snapshotId = saveInventorySnapshot(player, placeholderType)
                            createInventoryComponent(player, placeholderType, snapshotId)
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
    private fun createNonInventoryComponent(player: Player, type: PlaceholderType): Component {
        val displayComponent = getDisplayComponent(player, type)
        val hoverText = getHoverText(player, type)
        
        val clickAction = when (type) {
            PlaceholderType.POS -> ClickEvent.suggestCommand("/tp ${player.location.blockX} ${player.location.blockY} ${player.location.blockZ}")
            PlaceholderType.HEALTH -> ClickEvent.suggestCommand("/effect give ${player.name} ")
            else -> null
        }
        
        val component = displayComponent.hoverEvent(HoverEvent.showText(hoverText))
        
        return if (clickAction != null) component.clickEvent(clickAction) else component
    }
    
    /**
     * Creates a clickable inventory component
     */
    private fun createInventoryComponent(player: Player, type: PlaceholderType, snapshotId: String): Component {
        val displayComponent = getDisplayComponent(player, type)
        val hoverText = getHoverText(player, type)
        
        return displayComponent
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/chatplugin viewinventory $snapshotId"))
    }
    
    /**
     * Gets display text for any placeholder type
     */
    private fun getDisplayComponent(player: Player, type: PlaceholderType): Component {
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
                    config.inventoryDisplayFormat,
                    player,
                    placeholders
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
                    config.positionDisplayFormat,
                    player,
                    placeholders
                )
            }
            PlaceholderType.HEALTH -> {
                val health = player.health.roundToInt()
                val maxHealth = player.maxHealth.roundToInt()
                val food = player.foodLevel
                val saturation = player.saturation.roundToInt()
                val placeholders = mapOf(
                    "health" to health.toString(),
                    "max_health" to maxHealth.toString(),
                    "food" to food.toString(),
                    "saturation" to saturation.toString()
                )
                messageFormattingService.formatMessage(
                    config.healthDisplayFormat,
                    player,
                    placeholders
                )
            }
        }
    }
    
    /**
     * Gets hover text for any placeholder type
     */
    private fun getHoverText(player: Player, type: PlaceholderType): Component {
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
                    config.inventoryHoverFormat.replace("\\n", "\n"),
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
                    loc.world?.getBiome(loc)?.toString()?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
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
                    config.positionHoverFormat.replace("\\n", "\n"),
                    player,
                    placeholders
                )
            }
            PlaceholderType.HEALTH -> {
                val health = player.health.roundToInt()
                val maxHealth = player.maxHealth.roundToInt()
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
                    config.healthHoverFormat.replace("\\n", "\n"),
                    player,
                    placeholders
                )
            }
        }
    }
    
    /**
     * Gets item count for inventory types
     */
    private fun getItemCount(player: Player, type: PlaceholderType): Int {
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
    private fun getItemPreview(player: Player, type: PlaceholderType): Component {
        val config = configManager.config.inventoryPlaceholders
        val items = getItemsForType(player, type)
        val nonEmptyItems = items.filterNotNull().filter { it.type != Material.AIR }.take(config.maxPreviewItems)
        
        if (nonEmptyItems.isEmpty()) {
            return messageFormattingService.formatMessage(config.emptyInventoryText, player)
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
                config.itemPreviewFormat,
                player,
                placeholders
            )
            builder.append(itemComponent)
        }
        
        if (items.size > config.maxPreviewItems) {
            builder.append(Component.newline())
            val placeholders = mapOf(
                "count" to (items.size - config.maxPreviewItems).toString()
            )
            val moreComponent = messageFormattingService.formatMessage(
                config.moreItemsText,
                player,
                placeholders
            )
            builder.append(moreComponent)
        }
        
        return builder.build()
    }
    
    /**
     * Gets effects text for health hover
     */
    private fun getEffectsText(player: Player): String {
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
    private fun getItemsForType(player: Player, type: PlaceholderType): Array<ItemStack?> {
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
    private fun saveInventorySnapshot(player: Player, type: PlaceholderType): String {
        val snapshotId = "${player.uniqueId}_${type.name.lowercase()}_${System.currentTimeMillis()}"
        val snapshotFile = inventoryDataDir.resolve("$snapshotId.dat")
        
        try {
            FileOutputStream(snapshotFile.toFile()).use { fos ->
                BukkitObjectOutputStream(fos).use { oos ->
                    val snapshot = InventorySnapshot(
                        playerId = player.uniqueId,
                        playerName = player.name,
                        type = type,
                        items = getItemsForType(player, type),
                        timestamp = System.currentTimeMillis()
                    )
                    oos.writeObject(snapshot)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save inventory snapshot for player ${player.name}", e)
            throw e
        }
        
        return snapshotId
    }
    
    /**
     * Loads and displays an inventory snapshot to a player
     */
    fun viewInventorySnapshot(viewer: Player, snapshotId: String): Boolean {
        val snapshotFile = inventoryDataDir.resolve("$snapshotId.dat")
        
        if (!Files.exists(snapshotFile)) {
            viewer.sendMessage(
                messageFormattingService.formatMessage(
                    configManager.config.messages.inventoryPlaceholders.snapshotNotFound,
                    viewer
                )
            )
            return false
        }
        
        try {
            FileInputStream(snapshotFile.toFile()).use { fis ->
                BukkitObjectInputStream(fis).use { ois ->
                    val snapshot = ois.readObject() as InventorySnapshot
                    
                    // Create a read-only inventory view
                    val inventory = createReadOnlyInventory(snapshot)
                    viewer.openInventory(inventory)
                    
                    // Clean up old snapshots
                    cleanupOldSnapshots()
                    
                    return true
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load inventory snapshot $snapshotId", e)
            viewer.sendMessage(
                messageFormattingService.formatMessage(
                    configManager.config.messages.inventoryPlaceholders.viewFailed,
                    viewer
                )
            )
            return false
        }
    }
    
    /**
     * Creates a read-only inventory from a snapshot
     */
    private fun createReadOnlyInventory(snapshot: InventorySnapshot): Inventory {
        val title = "${snapshot.playerName}'s ${snapshot.type.displayName}"
        val size = when (snapshot.type) {
            PlaceholderType.INV -> 54 // 6 rows for main inventory
            PlaceholderType.ENDER -> 27 // 3 rows for ender chest
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
     * Cleans up old inventory snapshots
     */
    private fun cleanupOldSnapshots() {
        try {
            val config = configManager.config.inventoryPlaceholders
            val cutoffTime = System.currentTimeMillis() - (config.snapshotRetentionMinutes * 60 * 1000L)
            
            Files.list(inventoryDataDir).use { stream ->
                stream.filter { it.toString().endsWith(".dat") }
                    .filter { Files.getLastModifiedTime(it).toMillis() < cutoffTime }
                    .forEach { 
                        try {
                            Files.delete(it)
                        } catch (e: Exception) {
                            logger.debug("Failed to delete old snapshot file: ${it.fileName}", e)
                        }
                    }
            }
        } catch (e: Exception) {
            logger.debug("Failed to cleanup old snapshots", e)
        }
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