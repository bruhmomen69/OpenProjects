package bruh.zchat.paper.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class DatabaseMaintenanceService(
    private val dbPlayerQueries: DBPlayerQueries,
    private val config: DatabaseConfig
) {
    private val logger = LoggerFactory.getLogger(DatabaseMaintenanceService::class.java)

    /**
     * Performs data retention for infractions and message bus records.
     * Player blocks are intentionally preserved and are neither archived nor deleted.
     */
    suspend fun performDataRetention(): MaintenanceResult = withContext(Dispatchers.IO) {
        val cutoffDate = Instant.now().minus(config.dataRetentionDays.toLong(), ChronoUnit.DAYS)
        var totalDeleted = 0

        try {
            logger.info("Starting data retention cleanup for records older than $cutoffDate")

            // Archive old data before deletion (if archive enabled)
            if (config.enableArchive) {
                val archivedInfractions = dbPlayerQueries.archiveOldInfractions(cutoffDate)
                logger.info("Archived $archivedInfractions infraction records")
            }

            // Clean old player infractions
            val infractionsDeleted = cleanupOldInfractions(cutoffDate)
            totalDeleted += infractionsDeleted
            logger.info("Deleted $infractionsDeleted old infraction records")

            // Clean old messages from bus
            val messagesDeleted = cleanupOldMessages(cutoffDate)
            totalDeleted += messagesDeleted
            logger.info("Deleted $messagesDeleted old message bus records")

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
    
    
    private suspend fun cleanupOldInfractions(cutoffDate: Instant): Int {
        return dbPlayerQueries.cleanupOldInfractions(cutoffDate)
    }

    private suspend fun cleanupOldMessages(cutoffDate: Instant): Int {
        return dbPlayerQueries.deleteOldMessages(cutoffDate)
    }
    
    private suspend fun optimizeDatabase() {
        try {
            logger.info("Optimizing database")
            dbPlayerQueries.vacuumDatabase()
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