package lol.mcplugs.minimessagechatplugin.paper.services

import lol.mcplugs.minimessagechatplugin.paper.utils.ChatInventoryHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Smoker
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * Service for processing inventory placeholders in chat messages.
 * This handles user input placeholders like {inv}, [inv], [ender], [armor], [hand]
 */
class ChatInventoryPlaceholderService(private val plugin: JavaPlugin) {
    private val logger = LoggerFactory.getLogger(ChatInventoryPlaceholderService::class.java)
    private val inventoryDataDir: Path = plugin.dataFolder.toPath().resolve("inventory_snapshots")
    // Use legacy because it includes some basic formatting vs no formatting.
    private val plainTextSerializer = LegacyComponentSerializer.legacySection()
    
    // Patterns for different placeholder formats
    private val inventoryPatterns = mapOf(
        "inv" to listOf(Pattern.compile("\\{inv\\}"), Pattern.compile("\\[inv\\]")),
        "ender" to listOf(Pattern.compile("\\[ender\\]")),
        "armor" to listOf(Pattern.compile("\\[armor\\]")),
        "hand" to listOf(Pattern.compile("\\[hand\\]"))
    )
    
    init {
        // Create inventory data directory if it doesn't exist
        try {
            Files.createDirectories(inventoryDataDir)

            cleanupOldSnapshots()
        } catch (e: Exception) {
            logger.error("Failed to create and clean up inventory snapshots directory", e)
        }
    }
    
    /**
     * Processes a raw chat message string and creates a Component with inventory placeholders
     * This creates a hybrid component with unparsed text and parsed inventory components
     */
    fun processRawMessage(player: Player, message: String): Component {
        // Check if message contains any inventory placeholders
        if (!containsInventoryPlaceholders(message)) {
            // Return unparsed text component for normal processing
            return Component.text(message)
        }
        
        return try {
            createHybridComponent(player, message)
        } catch (e: Exception) {
            logger.warn("Failed to process inventory placeholders for player ${player.name}", e)
            Component.text(message) // Return original message as unparsed text if processing fails
        }
    }
    
    /**
     * Processes a chat message and replaces inventory placeholders with clickable components
     * @deprecated Use processRawMessage instead to preserve chat formatting
     */
    @Deprecated("Use processRawMessage instead to preserve chat formatting")
    fun processMessage(player: Player, message: Component): Component {
        val messageText = plainTextSerializer.serialize(message)
        
        // Check if message contains any inventory placeholders
        if (!containsInventoryPlaceholders(messageText)) {
            return message
        }
        
        return try {
            processInventoryPlaceholders(player, messageText)
        } catch (e: Exception) {
            logger.warn("Failed to process inventory placeholders for player ${player.name}", e)
            message // Return original message if processing fails
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
                        val snapshotId = saveInventorySnapshot(player, InventoryType.valueOf(type.uppercase()))
                        val component = createInventoryComponent(player, InventoryType.valueOf(type.uppercase()), snapshotId)
                        
                        replacements.add(PlaceholderReplacement(start, end, placeholder, component))
                    } catch (e: Exception) {
                        logger.warn("Failed to create inventory component for placeholder $placeholder", e)
                        // Add error component instead
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
            
            // Add the parsed inventory component
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
     * Processes inventory placeholders in a message and returns a MiniMessage string
     * @deprecated Use createHybridComponent for better unparsed text handling
     */
    @Deprecated("Use createHybridComponent for better unparsed text handling")
    private fun processInventoryPlaceholdersToMiniMessage(player: Player, messageText: String): String {
        var processedText = messageText
        
        // Find all inventory placeholders and replace them with MiniMessage format
        for ((type, patterns) in inventoryPatterns) {
            for (pattern in patterns) {
                val matcher = pattern.matcher(processedText)
                while (matcher.find()) {
                    val placeholder = matcher.group()
                    
                    try {
                        val snapshotId = saveInventorySnapshot(player, InventoryType.valueOf(type.uppercase()))
                        val miniMessageReplacement = createInventoryMiniMessage(player, InventoryType.valueOf(type.uppercase()), snapshotId)
                        
                        processedText = processedText.replace(placeholder, miniMessageReplacement)
                    } catch (e: Exception) {
                        logger.warn("Failed to create inventory placeholder for $placeholder", e)
                        // Replace with error text
                        processedText = processedText.replace(placeholder, "<red>[$type: error]</red>")
                    }
                }
            }
        }
        
        return processedText
    }
    
    /**
     * Processes inventory placeholders in a message and returns a Component with clickable elements
     * @deprecated Use processInventoryPlaceholdersToMiniMessage for better chat format integration
     */
    @Deprecated("Use processInventoryPlaceholdersToMiniMessage for better chat format integration")
    private fun processInventoryPlaceholders(player: Player, messageText: String): Component {
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
                        val snapshotId = saveInventorySnapshot(player, InventoryType.valueOf(type.uppercase()))
                        val replacement = createInventoryComponent(player, InventoryType.valueOf(type.uppercase()), snapshotId)
                        
                        replacements.add(PlaceholderReplacement(start, end, placeholder, replacement))
                    } catch (e: Exception) {
                        logger.warn("Failed to create inventory component for placeholder $placeholder", e)
                        // Add error component instead
                        val errorComponent = Component.text("[$type: error]").color(NamedTextColor.RED)
                        replacements.add(PlaceholderReplacement(start, end, placeholder, errorComponent))
                    }
                }
            }
        }
        
        // Sort replacements by position (descending) to avoid index shifting issues
        replacements.sortByDescending { it.start }
        
        // Build the final component
        return buildComponentWithReplacements(messageText, replacements)
    }
    
    /**
     * Builds a Component with placeholder replacements
     */
    private fun buildComponentWithReplacements(originalText: String, replacements: List<PlaceholderReplacement>): Component {
        if (replacements.isEmpty()) {
            return Component.text(originalText)
        }
        
        val builder = Component.text()
        var currentIndex = 0
        
        // Sort replacements by start position (ascending for building)
        val sortedReplacements = replacements.sortedBy { it.start }
        
        for (replacement in sortedReplacements) {
            // Add text before the placeholder
            if (replacement.start > currentIndex) {
                builder.append(Component.text(originalText.substring(currentIndex, replacement.start)))
            }
            
            // Add the replacement component
            builder.append(replacement.component)
            
            currentIndex = replacement.end
        }
        
        // Add remaining text after the last placeholder
        if (currentIndex < originalText.length) {
            builder.append(Component.text(originalText.substring(currentIndex)))
        }
        
        return builder.build()
    }
    
    /**
     * Creates a MiniMessage string for an inventory placeholder
     */
    private fun createInventoryMiniMessage(player: Player, type: InventoryType, snapshotId: String): String {
        val displayText = getInventoryDisplayText(player, type)
        val hoverText = getInventoryHoverTextPlain(player, type)
        
        // Create MiniMessage format with hover and click
        // Escape any existing MiniMessage tags in the display text and hover text
        val escapedDisplayText = displayText.replace("<", "\\<").replace(">", "\\>")
        val escapedHoverText = hoverText.replace("<", "\\<").replace(">", "\\>").replace("'", "\\'")
        
        return "<yellow><hover:show_text:'$escapedHoverText'><click:run_command:'/chatplugin viewinventory $snapshotId'>$escapedDisplayText</click></hover></yellow>"
    }
    
    /**
     * Creates a clickable inventory component
     * @deprecated Use createInventoryMiniMessage for better chat format integration
     */
    @Deprecated("Use createInventoryMiniMessage for better chat format integration")
    private fun createInventoryComponent(player: Player, type: InventoryType, snapshotId: String): Component {
        val displayText = getInventoryDisplayText(player, type)
        val hoverText = getInventoryHoverText(player, type)
        
        return Component.text(displayText)
            .color(NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/chatplugin viewinventory $snapshotId"))
    }
    
    /**
     * Saves an inventory snapshot to disk and returns the snapshot ID
     */
    private fun saveInventorySnapshot(player: Player, type: InventoryType): String {
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
     * Gets items for the specified inventory type
     */
    private fun getItemsForType(player: Player, type: InventoryType): Array<ItemStack?> {
        return when (type) {
            InventoryType.INV -> player.inventory.contents
            InventoryType.ENDER -> player.enderChest.contents
            InventoryType.ARMOR -> player.inventory.armorContents
            InventoryType.HAND -> arrayOf(player.inventory.itemInMainHand, player.inventory.itemInOffHand)
        }
    }
    
    /**
     * Gets display text for inventory placeholder
     */
    private fun getInventoryDisplayText(player: Player, type: InventoryType): String {
        val itemCount = when (type) {
            InventoryType.INV -> player.inventory.contents.count { it != null && it.type != Material.AIR }
            InventoryType.ENDER -> player.enderChest.contents.count { it != null && it.type != Material.AIR }
            InventoryType.ARMOR -> player.inventory.armorContents.count { it != null && it.type != Material.AIR }
            InventoryType.HAND -> {
                val mainHand = player.inventory.itemInMainHand
                val offHand = player.inventory.itemInOffHand
                var count = 0
                if (mainHand != null && mainHand.type != Material.AIR) count++
                if (offHand != null && offHand.type != Material.AIR) count++
                count
            }
        }
        
        return "[${type.displayName}: $itemCount ${if (itemCount == 1) "item" else "items"}]"
    }
    
    /**
     * Gets hover text for inventory placeholder as plain text for MiniMessage
     */
    private fun getInventoryHoverTextPlain(player: Player, type: InventoryType): String {
        val items = getItemsForType(player, type)
        val builder = StringBuilder()
        
        builder.append("${player.name}'s ${type.displayName}\\n")
        builder.append("Click to view\\n")
        builder.append("\\n")
        
        // Show first few items as preview
        val nonEmptyItems = items.filterNotNull().filter { it.type != Material.AIR }.take(5)
        if (nonEmptyItems.isNotEmpty()) {
            builder.append("Preview:\\n")
            for (item in nonEmptyItems) {
                val itemName = item.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                builder.append("• ${item.amount}x $itemName\\n")
            }
            if (items.size > 5) {
                builder.append("... and ${items.size - 5} more")
            }
        } else {
            builder.append("Empty")
        }
        
        return builder.toString().trimEnd()
    }
    
    /**
     * Gets hover text for inventory placeholder
     * @deprecated Use getInventoryHoverTextPlain for MiniMessage format
     */
    @Deprecated("Use getInventoryHoverTextPlain for MiniMessage format")
    private fun getInventoryHoverText(player: Player, type: InventoryType): Component {
        val items = getItemsForType(player, type)
        val builder = Component.text()
            .append(Component.text("${player.name}'s ${type.displayName}").color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Click to view").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.newline())
        
        // Show first few items as preview
        val nonEmptyItems = items.filterNotNull().filter { it.type != Material.AIR }.take(5)
        if (nonEmptyItems.isNotEmpty()) {
            builder.append(Component.text("Preview:").color(NamedTextColor.YELLOW))
            for (item in nonEmptyItems) {
                builder.append(Component.newline())
                    .append(Component.text("• ").color(NamedTextColor.GRAY))
                    .append(Component.text("${item.amount}x ${item.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}").color(NamedTextColor.WHITE))
            }
            if (items.size > 5) {
                builder.append(Component.newline())
                    .append(Component.text("... and ${items.size - 5} more").color(NamedTextColor.GRAY))
            }
        } else {
            builder.append(Component.text("Empty").color(NamedTextColor.GRAY))
        }
        
        return builder.build()
    }
    
    /**
     * Loads and displays an inventory snapshot to a player
     */
    fun viewInventorySnapshot(viewer: Player, snapshotId: String): Boolean {
        val snapshotFile = inventoryDataDir.resolve("$snapshotId.dat")
        
        if (!Files.exists(snapshotFile)) {
            viewer.sendMessage(Component.text("Inventory snapshot not found or expired.").color(NamedTextColor.RED))
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
            viewer.sendMessage(Component.text("Failed to load inventory snapshot.").color(NamedTextColor.RED))
            return false
        }
    }
    
    /**
     * Creates a read-only inventory from a snapshot
     */
    private fun createReadOnlyInventory(snapshot: InventorySnapshot): Inventory {
        val title = "${snapshot.playerName}'s ${snapshot.type.displayName}"
        val size = when (snapshot.type) {
            InventoryType.INV -> 45 // 6 rows for main inventory
            InventoryType.ENDER -> 27 // 3 rows for ender chest
            InventoryType.ARMOR -> 9 // 1 row for armor
            InventoryType.HAND -> 9 // 1 row for hand items
        }
        
        val inventory = AtomicReference<Inventory>(null)

        inventory.set(Bukkit.createInventory(object : ChatInventoryHolder {
            override fun getInventory(): Inventory {
                return inventory.get()
            }
        }, size, Component.text(title)))

        val inv = inventory.get()

        // Add items to inventory
        for (i in snapshot.items.indices) {
            if (i < inv.size && snapshot.items[i] != null) {
                inv.setItem(i, snapshot.items[i])
            }
        }
        
        return inv
    }
    
    /**
     * Cleans up old inventory snapshots (older than 1 hour)
     */
    private fun cleanupOldSnapshots() {
        try {
            val cutoffTime = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
            
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
     * Represents different types of inventories
     */
    enum class InventoryType(val displayName: String) {
        INV("Inventory"),
        ENDER("Ender Chest"),
        ARMOR("Armor"),
        HAND("Hand")
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
        val type: InventoryType,
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