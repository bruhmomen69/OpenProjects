package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.ConfigurationNode
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path
import java.nio.file.Files

/**
 * Service for managing player block lists for private messages
 */
class BlockService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val socialSpyService: SocialSpyService,
    private val dataFolder: Path
) {
    private val logger = LoggerFactory.getLogger(BlockService::class.java)
    
    // Track blocked players: player UUID -> set of blocked player UUIDs
    private val blockedPlayers = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    
    private val blocksFile = dataFolder.resolve("blocks.conf")
    private val blocksLoader = HoconConfigurationLoader.builder()
        .path(blocksFile)
        .prettyPrinting(true)
        .build()
    
    /**
     * Block a player from sending private messages to the blocker
     */
    fun blockPlayer(player: Player, targetName: String): Boolean {
        val config = configManager.config.blocks
        
        // Check if block system is enabled
        if (!config.enableBlockSystem) {
            player.sendMessage(messageFormattingService.getConfigMessage("system.feature_disabled", player))
            return false
        }
        
        // Find target player
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "commands.player_not_found",
                player,
                mapOf("player" to targetName)
            ))
            return false
        }
        
        // Check if trying to block self
        if (target.uniqueId == player.uniqueId && !config.blockSelf) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "private_messages.self_message",
                player
            ))
            return false
        }
        
        // Get or create block list for player
        val blockList = blockedPlayers.getOrPut(player.uniqueId) { ConcurrentHashMap.newKeySet() }
        
        // Check if already blocked
        if (blockList.contains(target.uniqueId)) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "blocks.already_blocked",
                player,
                mapOf("player" to target.name)
            ))
            return false
        }
        
        // Check max blocks limit
        if (blockList.size >= config.maxBlocksPerPlayer) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "system.genericError", // Could add specific message for max blocks
                player
            ))
            return false
        }
        
        // Add to block list
        blockList.add(target.uniqueId)
        
        // Send success message
        player.sendMessage(messageFormattingService.getConfigMessage(
            "blocks.blocked",
            player,
            mapOf("player" to target.name)
        ))
        
        // Log if enabled
        if (config.logBlocks) {
            logger.info("${player.name} blocked ${target.name}")
        }
        
        // Notify social spy if enabled
        socialSpyService.broadcastBlockAction(player, target, "blocked")
        
        return true
    }
    
    /**
     * Unblock a player
     */
    fun unblockPlayer(player: Player, targetName: String): Boolean {
        val config = configManager.config.blocks
        
        // Check if block system is enabled
        if (!config.enableBlockSystem) {
            player.sendMessage(messageFormattingService.getConfigMessage("system.feature_disabled", player))
            return false
        }
        
        // Find target player (check offline players too)
        val target = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (target?.uniqueId == null) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "commands.player_not_found",
                player,
                mapOf("player" to targetName)
            ))
            return false
        }
        
        // Get block list for player
        val blockList = blockedPlayers[player.uniqueId]
        if (blockList == null || !blockList.contains(target.uniqueId)) {
            player.sendMessage(messageFormattingService.getConfigMessage(
                "blocks.not_blocked",
                player,
                mapOf("player" to targetName)
            ))
            return false
        }
        
        // Remove from block list
        blockList.remove(target.uniqueId)
        
        // Clean up empty block list
        if (blockList.isEmpty()) {
            blockedPlayers.remove(player.uniqueId)
        }
        
        // Send success message
        player.sendMessage(messageFormattingService.getConfigMessage(
            "blocks.unblocked",
            player,
            mapOf("player" to targetName)
        ))
        
        // Log if enabled
        if (config.logBlocks) {
            logger.info("${player.name} unblocked $targetName")
        }
        
        // Notify social spy if enabled
        if (target is Player) {
            socialSpyService.broadcastBlockAction(player, target, "unblocked")
        }
        
        return true
    }
    
    /**
     * Get list of blocked players for a player
     */
    fun getBlockedPlayers(player: Player): List<String> {
        val blockList = blockedPlayers[player.uniqueId] ?: return emptyList()
        
        return blockList.mapNotNull { uuid ->
            val blockedPlayer = Bukkit.getOfflinePlayer(uuid)
            blockedPlayer.name
        }.sorted()
    }
    
    /**
     * Check if a sender is blocked by the recipient
     */
    fun isBlocked(recipient: Player, sender: Player): Boolean {
        val blockList = blockedPlayers[recipient.uniqueId] ?: return false
        return blockList.contains(sender.uniqueId)
    }
    
    /**
     * Display block list to player
     */
    fun showBlockList(player: Player) {
        val config = configManager.config.blocks
        
        if (!config.enableBlockSystem) {
            player.sendMessage(messageFormattingService.getConfigMessage("system.feature_disabled", player))
            return
        }
        
        val blockedList = getBlockedPlayers(player)
        
        if (blockedList.isEmpty()) {
            player.sendMessage(messageFormattingService.getConfigMessage("blocks.block_list_empty", player))
        } else {
            val listString = blockedList.joinToString(", ")
            player.sendMessage(messageFormattingService.getConfigMessage(
                "blocks.block_list",
                player,
                mapOf("list" to listString)
            ))
        }
    }
    
    /**
     * Force block a player (admin command)
     */
    fun forceBlock(admin: Player, playerName: String, targetName: String): Boolean {
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            admin.sendMessage(messageFormattingService.getConfigMessage(
                "commands.player_not_found",
                admin,
                mapOf("player" to playerName)
            ))
            return false
        }
        
        return blockPlayer(targetPlayer, targetName)
    }
    
    /**
     * Force unblock a player (admin command)
     */
    fun forceUnblock(admin: Player, playerName: String, targetName: String): Boolean {
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            admin.sendMessage(messageFormattingService.getConfigMessage(
                "commands.player_not_found",
                admin,
                mapOf("player" to playerName)
            ))
            return false
        }
        
        return unblockPlayer(targetPlayer, targetName)
    }
    
    /**
     * Clear all blocks for a player (admin command)
     */
    fun clearBlocks(player: Player) {
        val blockList = blockedPlayers.remove(player.uniqueId)
        val count = blockList?.size ?: 0
        
        logger.info("Cleared $count blocks for ${player.name}")
    }
    
    /**
     * Clear all blocks for a player by name (admin command)
     */
    fun clearBlocksByName(admin: Player, playerName: String): Boolean {
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            admin.sendMessage(messageFormattingService.getConfigMessage(
                "commands.player_not_found",
                admin,
                mapOf("player" to playerName)
            ))
            return false
        }
        
        clearBlocks(targetPlayer)
        admin.sendMessage(messageFormattingService.getConfigMessage(
            "commands.feature_enabled", // Generic success message
            admin,
            mapOf("feature" to "Block list cleared for ${targetPlayer.name}")
        ))
        
        return true
    }
    
    /**
     * Get statistics about blocks
     */
    fun getBlockStats(): Map<String, Int> {
        val totalBlocks = blockedPlayers.values.sumOf { it.size }
        return mapOf(
            "total_blocking_players" to blockedPlayers.size,
            "total_blocks" to totalBlocks,
            "average_blocks_per_player" to if (blockedPlayers.isNotEmpty()) totalBlocks / blockedPlayers.size else 0
        )
    }
    
    /**
     * Handle player quit - clean up if not persistent
     */
    fun handlePlayerQuit(player: Player) {
        val config = configManager.config.blocks
        
        if (!config.persistBlockLists) {
            blockedPlayers.remove(player.uniqueId)
            logger.debug("Removed non-persistent block list for ${player.name}")
        }
    }
    
    /**
     * Clear all block data (admin command)
     */
    fun clearAllBlocks() {
        val totalBlocks = blockedPlayers.values.sumOf { it.size }
        val totalPlayers = blockedPlayers.size
        
        blockedPlayers.clear()
        
        logger.info("Cleared all block data: $totalBlocks blocks from $totalPlayers players")
    }
    
    /**
     * Load persisted block lists
     */
    fun loadBlocks() {
        val config = configManager.config.blocks
        if (!config.persistBlockLists) return
        
        try {
            if (!Files.exists(blocksFile)) return
            
            val node: ConfigurationNode = blocksLoader.load()
            val blocksNode = node.node("blocks")
            
            for (entry in blocksNode.childrenMap()) {
                val playerUUID = UUID.fromString(entry.key.toString())
                val blockedList = entry.value.getList(String::class.java)
                    ?.map { UUID.fromString(it) }
                    ?.toMutableSet() ?: mutableSetOf()
                blockedPlayers[playerUUID] = blockedList
            }
            
            logger.info("Loaded block lists for ${blockedPlayers.size} players")
        } catch (e: Exception) {
            logger.error("Failed to load block lists", e)
        }
    }
    
    /**
     * Save block lists to file
     */
    fun saveBlocks() {
        val config = configManager.config.blocks
        if (!config.persistBlockLists) return
        
        try {
            val node = blocksLoader.createNode()
            val blocksNode = node.node("blocks")
            
            for ((playerUUID, blockedSet) in blockedPlayers) {
                blocksNode.node(playerUUID.toString()).setList(String::class.java, blockedSet.map { it.toString() })
            }
            
            blocksLoader.save(node)
            logger.info("Saved block lists for ${blockedPlayers.size} players")
        } catch (e: Exception) {
            logger.error("Failed to save block lists", e)
        }
    }
}