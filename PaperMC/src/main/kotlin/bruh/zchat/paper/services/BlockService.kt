package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.database.DBPlayerQueries
import bruh.zchat.paper.database.DatabaseService
import bruh.zchat.paper.database.PlayerDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*

class BlockService(
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService,
    private val socialSpyService: SocialSpyService,
    private val dbPlayerQueries: DBPlayerQueries,
    private val playerDataManager: PlayerDataManager
) {
    private val logger = LoggerFactory.getLogger(BlockService::class.java)
    
    suspend fun blockPlayer(blockerUuid: UUID, blockedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        val config = configManager.config.blocks
        
        if (!config.enableBlockSystem) {
            return@withContext false
        }
        
        val blockerUsername = playerDataManager.getUsernameByUuid(blockerUuid) ?: "Unknown"
        val success = dbPlayerQueries.createBlockWithChecks(
            blockerUuid = blockerUuid,
            blockedUuid = blockedUuid,
            blockerUsername = blockerUsername,
            maxBlocksPerPlayer = config.maxBlocksPerPlayer
        )

        if (success) {
            // Update player cache
            val playerData = playerDataManager.getPlayerData(blockerUuid)
            if (playerData != null) {
                val updatedBlocked = playerData.blockedPlayers.toMutableSet()
                updatedBlocked.add(blockedUuid)
                playerDataManager.updatePlayerBlockedPlayers(blockerUuid, updatedBlocked)
            }
        }

        success
    }
    
    suspend fun unblockPlayer(blockerUuid: UUID, blockedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        val config = configManager.config.blocks
        
        if (!config.enableBlockSystem) {
            return@withContext false
        }
        
        try {
            val success = dbPlayerQueries.deleteBlock(blockerUuid, blockedUuid)
            
            if (success) {
                // Update player cache
                val playerData = playerDataManager.getPlayerData(blockerUuid)
                if (playerData != null) {
                    val updatedBlocked = playerData.blockedPlayers.toMutableSet()
                    updatedBlocked.remove(blockedUuid)
                    playerDataManager.updatePlayerBlockedPlayers(blockerUuid, updatedBlocked)
                }
            }
            
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun getBlockedPlayers(playerUuid: UUID): Set<UUID> {
        // Check cache first
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            return playerData.blockedPlayers
        }
        
        // Fallback to database for offline players
        return dbPlayerQueries.getPlayerBlockedPlayers(playerUuid)
    }
    
    suspend fun isBlocked(recipientUuid: UUID, senderUuid: UUID): Boolean {
        // Check cache first
        val recipientData = playerDataManager.getPlayerData(recipientUuid)
        if (recipientData != null) {
            return senderUuid in recipientData.blockedPlayers
        }
        
        // Fallback to database for offline players
        return dbPlayerQueries.checkBlockExists(recipientUuid, senderUuid)
    }
    
    fun getBlockedPlayersSync(player: Player): List<String> {
        val blockedSet = runBlocking { getBlockedPlayers(player.uniqueId) }
        
        return blockedSet.mapNotNull { uuid ->
            val blockedPlayer = Bukkit.getOfflinePlayer(uuid)
            blockedPlayer.name
        }.sorted()
    }
    
    fun isBlockedSync(recipient: Player, sender: Player): Boolean {
        return runBlocking { isBlocked(recipient.uniqueId, sender.uniqueId) }
    }
    
    fun showBlockList(player: Player) {
        val config = configManager.config.blocks
        
        if (!config.enableBlockSystem) {
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.BLOCKS_SYSTEM_DISABLED, player))
            return
        }
        
        val blockedList = getBlockedPlayersSync(player)
        
        if (blockedList.isEmpty()) {
            player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.BLOCKS_BLOCK_LIST_EMPTY, player))
        } else {
            val listString = blockedList.joinToString(", ")
            player.sendMessage(messageFormattingService.getConfigMessage(
                MessageKey.BLOCKS_BLOCK_LIST,
                player,
                mapOf("list" to listString)
            ))
        }
    }
    
    suspend fun clearBlocks(playerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = dbPlayerQueries.databaseService.executeUpdate(
                "DELETE FROM player_blocks WHERE blocker_uuid = ?",
                playerUuid
            )
            
            // Update player cache
            val playerData = playerDataManager.getPlayerData(playerUuid)
            if (playerData != null) {
                playerDataManager.updatePlayerBlockedPlayers(playerUuid, emptySet())
            }
            
            affectedRows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun getBlockStats(): Map<String, Int> {
        val onlinePlayers = playerDataManager.getOnlinePlayerData()
        val totalBlocks = onlinePlayers.values.sumOf { it.blockedPlayers.size }
        return mapOf(
            "total_blocking_players" to onlinePlayers.size,
            "total_blocks" to totalBlocks,
            "average_blocks_per_player" to if (onlinePlayers.isNotEmpty()) totalBlocks / onlinePlayers.size else 0
        )
    }
    
    // Admin methods for database management
    suspend fun forceBlock(adminPlayerUuid: UUID, playerName: String, targetName: String): Boolean = withContext(Dispatchers.IO) {
        // Resolve the player who should be doing the blocking
        val player = Bukkit.getPlayer(playerName) ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(playerName) }
        
        // Resolve the target to be blocked
        val target = Bukkit.getPlayer(targetName) ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(targetName) }

        
        // Use the player's UUID as the blocker, not the admin's UUID
        val success = blockPlayer(player.uniqueId, target.uniqueId)
        
        if (success) {
            val adminLabel = if (adminPlayerUuid == UUID(0L, 0L)) "CONSOLE" else adminPlayerUuid.toString()
            logger.info("Admin $adminLabel forced ${player.name} to block ${target.name}")
        }
        
        return@withContext success
    }
    
    suspend fun forceUnblock(adminPlayerUuid: UUID, playerName: String, targetName: String): Boolean {
        // Resolve the player who should be doing the unblocking
        val player = Bukkit.getPlayer(playerName) ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(playerName) }
        
        // Resolve the target to be unblocked
        val target = Bukkit.getPlayer(targetName) ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(targetName) }
        
        // Use the player's UUID as the blocker, not the admin's UUID
        val success = unblockPlayer(player.uniqueId, target.uniqueId)
        
        if (success) {
            val adminLabel = if (adminPlayerUuid == UUID(0L, 0L)) "CONSOLE" else adminPlayerUuid.toString()
            logger.info("Admin $adminLabel forced ${player.name} to unblock ${target.name}")
        }
        
        return success
    }
    
    suspend fun clearBlocksByName(playerName: String): Boolean {
        // Resolve the player (support both online and offline players)
        val player = Bukkit.getPlayer(playerName) ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(playerName) }
        
        val success = clearBlocks(player.uniqueId)
        
        if (success) {
            logger.info("Cleared all blocks for player ${player.name} ($playerName)")
        }
        
        return success
    }
    
    suspend fun clearAllBlocks(): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = dbPlayerQueries.databaseService.executeUpdate(
                "DELETE FROM player_blocks"
            )
            
            // Update all online player caches
            val onlinePlayers = playerDataManager.getOnlinePlayerData()
            onlinePlayers.forEach { (uuid, _) ->
                playerDataManager.updatePlayerBlockedPlayers(uuid, emptySet())
            }
            
            affectedRows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}