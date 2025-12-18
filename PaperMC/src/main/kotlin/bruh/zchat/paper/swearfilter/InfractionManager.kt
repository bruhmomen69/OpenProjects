package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.database.PlayerDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*

class InfractionManager(
    private val databaseService: bruh.zchat.paper.database.DatabaseService,
    private val playerDataManager: PlayerDataManager
) {
    private val logger = LoggerFactory.getLogger(InfractionManager::class.java)
    
    suspend fun getInfractions(playerUuid: UUID, groupName: String): Int {
        // Check cache first
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            return playerData.infractions[groupName] ?: 0
        }
        
        // Fallback to database for offline players
        return withContext(Dispatchers.IO) {
            try {
                val count = databaseService.executeQuerySingle(
                    "SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                    playerUuid, groupName
                ) { rs -> rs.getInt("count") }
                
                count ?: 0
            } catch (e: Exception) {
                logger.error("Failed to get infractions for player $playerUuid, group $groupName", e)
                0
            }
        }
    }
    
    suspend fun addInfraction(playerUuid: UUID, groupName: String): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeTransaction { tx ->
                // Check if infraction exists
                val existingCount = tx.executeQuerySingle(
                    "SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                    playerUuid, groupName
                ) { rs -> rs.getInt("count") }
                
                if (existingCount != null) {
                    // Update existing infraction
                    val newCount = existingCount + 1
                    tx.executeUpdate(
                        "UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?",
                        newCount, playerUuid, groupName
                    )
                    newCount
                } else {
                    // Insert new infraction
                    tx.executeUpdate(
                        "INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)",
                        playerUuid, groupName
                    )
                    1
                }
            }.also { newCount ->
                // Update player cache
                val playerData = playerDataManager.getPlayerData(playerUuid)
                if (playerData != null) {
                    val updatedInfractions = playerData.infractions.toMutableMap()
                    updatedInfractions[groupName] = newCount
                    playerDataManager.updatePlayerInfractions(playerUuid, updatedInfractions)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to add infraction for player $playerUuid, group $groupName", e)
            0
        }
    }
    
    suspend fun getPlayerInfractions(playerUuid: UUID): Map<String, Int> {
        // Check cache first
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            return playerData.infractions
        }
        
        // Fallback to database for offline players
        return withContext(Dispatchers.IO) {
            try {
                databaseService.executeQuery(
                    "SELECT group_name, count FROM player_infractions WHERE player_uuid = ? ORDER BY group_name",
                    playerUuid
                ) { rs ->
                    rs.getString("group_name") to rs.getInt("count")
                }.toMap()
            } catch (e: Exception) {
                logger.error("Failed to get all infractions for player $playerUuid", e)
                emptyMap()
            }
        }
    }
    
    suspend fun resetInfractions(playerUuid: UUID, groupName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = databaseService.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                playerUuid, groupName
            )
            
            if (affectedRows > 0) {
                // Update player cache
                val playerData = playerDataManager.getPlayerData(playerUuid)
                if (playerData != null) {
                    val updatedInfractions = playerData.infractions.toMutableMap()
                    updatedInfractions.remove(groupName)
                    playerDataManager.updatePlayerInfractions(playerUuid, updatedInfractions)
                }
            }
            
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to reset infractions for player $playerUuid, group $groupName", e)
            false
        }
    }
    
    suspend fun resetAllInfractions(playerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = databaseService.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ?",
                playerUuid
            )
            
            if (affectedRows > 0) {
                // Update player cache
                val playerData = playerDataManager.getPlayerData(playerUuid)
                if (playerData != null) {
                    playerDataManager.updatePlayerInfractions(playerUuid, emptyMap())
                }
            }
            
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to reset all infractions for player $playerUuid", e)
            false
        }
    }
    
    // Legacy sync methods for compatibility
    fun getInfractionsSync(player: Player, groupName: String): Int {
        return runBlocking { 
            getInfractions(player.uniqueId, groupName) 
        }
    }
    
    fun addInfractionSync(player: Player, groupName: String): Int {
        return runBlocking { 
            addInfraction(player.uniqueId, groupName) 
        }
    }
}
