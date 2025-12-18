package bruh.zchat.paper.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class DatabaseMaintenanceService(
    private val databaseService: DatabaseService,
    private val config: DatabaseConfig
) {
    private val logger = LoggerFactory.getLogger(DatabaseMaintenanceService::class.java)
    
    suspend fun performDataRetention(): MaintenanceResult = withContext(Dispatchers.IO) {
        val cutoffDate = Instant.now().minus(config.dataRetentionDays.toLong(), ChronoUnit.DAYS)
        var totalDeleted = 0
        
        try {
            logger.info("Starting data retention cleanup for records older than $cutoffDate")
            
            // Archive old data before deletion (if archive enabled)
            if (config.enableArchive) {
                archiveOldData(cutoffDate)
            }
            
            // Clean old player infractions
            val infractionsDeleted = cleanupOldInfractions(cutoffDate)
            totalDeleted += infractionsDeleted
            logger.info("Deleted $infractionsDeleted old infraction records")
            
            // Clean old player blocks  
            val blocksDeleted = cleanupOldBlocks(cutoffDate)
            totalDeleted += blocksDeleted
            logger.info("Deleted $blocksDeleted old block records")
            
            // Optimize database
            optimizeDatabase()
            
            return@withContext MaintenanceResult(
                success = true,
                recordsDeleted = totalDeleted,
                message = "Data retention completed: $totalDeleted records deleted"
            )
        } catch (e: Exception) {
            logger.error("Data retention failed", e)
            return@withContext MaintenanceResult(
                success = false,
                recordsDeleted = 0,
                message = "Data retention failed: ${e.message}"
            )
        }
    }
    
    private suspend fun archiveOldData(cutoffDate: Instant) {
        try {
            logger.info("Archiving data older than $cutoffDate")
            
            // Archive old infractions
            val archivedInfractions = databaseService.executeUpdate(
                """INSERT INTO player_infractions_archive 
                (player_uuid, group_name, count, last_updated, created_at, archived_at)
                SELECT player_uuid, group_name, count, last_updated, created_at, CURRENT_TIMESTAMP
                FROM player_infractions 
                WHERE last_updated < ?""",
                cutoffDate
            )
            logger.info("Archived $archivedInfractions infraction records")
            
            // Archive old blocks
            val archivedBlocks = databaseService.executeUpdate(
                """INSERT INTO player_blocks_archive 
                (blocker_uuid, blocked_uuid, blocked_at, blocked_by_username, archived_at)
                SELECT blocker_uuid, blocked_uuid, blocked_at, blocked_by_username, CURRENT_TIMESTAMP
                FROM player_blocks 
                WHERE blocked_at < ?""",
                cutoffDate
            )
            logger.info("Archived $archivedBlocks block records")
            
        } catch (e: Exception) {
            logger.error("Failed to archive old data", e)
            throw e
        }
    }
    
    private suspend fun cleanupOldInfractions(cutoffDate: Instant): Int {
        return databaseService.executeUpdate(
            "DELETE FROM player_infractions WHERE last_updated < ?",
            cutoffDate
        )
    }
    
    private suspend fun cleanupOldBlocks(cutoffDate: Instant): Int {
        return databaseService.executeUpdate(
            "DELETE FROM player_blocks WHERE blocked_at < ?",
            cutoffDate
        )
    }
    
    private suspend fun optimizeDatabase() {
        try {
            logger.info("Optimizing database")
            
            when (config.type) {
                DatabaseType.SQLITE -> {
                    databaseService.executeUpdate("VACUUM")
                    databaseService.executeUpdate("ANALYZE")
                }
                DatabaseType.MYSQL -> {
                    databaseService.executeUpdate("OPTIMIZE TABLE player_infractions")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_blocks")
                    databaseService.executeUpdate("OPTIMIZE TABLE players")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_infractions_archive")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_blocks_archive")
                }
            }
            
            logger.info("Database optimization completed")
        } catch (e: Exception) {
            logger.error("Database optimization failed", e)
            // Don't fail the entire retention process for optimization errors
        }
    }
}

data class MaintenanceResult(
    val success: Boolean,
    val recordsDeleted: Int,
    val message: String
)