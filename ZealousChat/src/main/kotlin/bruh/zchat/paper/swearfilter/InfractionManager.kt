package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.database.DBPlayerQueries
import bruh.zchat.paper.database.PlayerDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import java.util.*

class InfractionManager(
    private val dbPlayerQueries: DBPlayerQueries,
    private val playerDataManager: PlayerDataManager
) {
    
    suspend fun getInfractions(playerUuid: UUID, groupName: String): Int {
        // Check cache first
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            return playerData.infractions[groupName] ?: 0
        }
        
        // Fallback to database for offline players
        return dbPlayerQueries.getPlayerInfractionCount(playerUuid, groupName)
    }
    
    suspend fun addInfraction(playerUuid: UUID, groupName: String): Int {
        val newCount = dbPlayerQueries.addInfractionTransactional(playerUuid, groupName)
        
        // Update player cache
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            val updatedInfractions = playerData.infractions.toMutableMap()
            updatedInfractions[groupName] = newCount
            playerDataManager.updatePlayerInfractions(playerUuid, updatedInfractions)
        }
        
        return newCount
    }
    
    suspend fun getPlayerInfractions(playerUuid: UUID): Map<String, Int> {
        // Check cache first
        val playerData = playerDataManager.getPlayerData(playerUuid)
        if (playerData != null) {
            return playerData.infractions
        }
        
        // Fallback to database for offline players
        return dbPlayerQueries.getPlayerInfractions(playerUuid)
    }
    
    suspend fun resetInfractions(playerUuid: UUID, groupName: String): Boolean {
        val success = dbPlayerQueries.deleteInfraction(playerUuid, groupName)
        
        if (success) {
            // Update player cache
            val playerData = playerDataManager.getPlayerData(playerUuid)
            if (playerData != null) {
                val updatedInfractions = playerData.infractions.toMutableMap()
                updatedInfractions.remove(groupName)
                playerDataManager.updatePlayerInfractions(playerUuid, updatedInfractions)
            }
        }
        
        return success
    }
    
    suspend fun resetAllInfractions(playerUuid: UUID): Boolean {
        val success = dbPlayerQueries.deleteAllInfractions(playerUuid)
        
        if (success) {
            // Update player cache
            val playerData = playerDataManager.getPlayerData(playerUuid)
            if (playerData != null) {
                playerDataManager.updatePlayerInfractions(playerUuid, emptyMap())
            }
        }
        
        return success
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
