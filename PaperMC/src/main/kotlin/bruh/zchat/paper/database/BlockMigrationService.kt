package bruh.zchat.paper.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.ConfigurationLoader
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class MigrationResult(
    val migratedCount: Int,
    val message: String,
    val success: Boolean = migratedCount >= 0
)

class BlockMigrationService(
    private val databaseService: DatabaseService,
    private val dataFolder: Path,
    private val retentionDays: Int = 30
) {
    private val logger = LoggerFactory.getLogger(BlockMigrationService::class.java)
    
    suspend fun migrateBlockData(): MigrationResult = withContext(Dispatchers.IO) {
        val blocksFile = dataFolder.resolve("blocks.conf")
        
        if (!Files.exists(blocksFile)) {
            logger.info("No existing block data found at ${blocksFile.fileName}")
            return@withContext MigrationResult(0, "No existing block data found")
        }
        
        try {
            logger.info("Starting migration of block data from ${blocksFile.fileName}")
            
            val loader = HoconConfigurationLoader.builder()
                .path(blocksFile)
                .build()
            
            val node = loader.load()
            val blocksNode = node.node("blocks")
            
            var migratedCount = 0
            databaseService.executeTransaction { tx ->
                for (entry in blocksNode.childrenMap()) {
                    try {
                        val playerUUID = UUID.fromString(entry.key.toString())
                        val blockedList = entry.value.getList(String::class.java)
                            ?.map { UUID.fromString(it) }
                            ?: emptyList()
                        
                        for (blockedUUID in blockedList) {
                            try {
                                // Check if player exists, create if not
                                ensurePlayerExists(tx, playerUUID)
                                ensurePlayerExists(tx, blockedUUID)
                                
                                // Insert block record
                                val insertSql = when (databaseService.databaseType) {
                                    DatabaseType.MYSQL -> """INSERT IGNORE INTO player_blocks
                                    (blocker_uuid, blocked_uuid, blocked_by_username)
                                    VALUES (?, ?, (SELECT username FROM players WHERE uuid = ?))"""
                                    DatabaseType.SQLITE -> """INSERT OR IGNORE INTO player_blocks
                                    (blocker_uuid, blocked_uuid, blocked_by_username)
                                    VALUES (?, ?, (SELECT username FROM players WHERE uuid = ?))"""
                                }

                                val insertResult = tx.executeUpdate(
                                    insertSql,
                                    playerUUID, blockedUUID, playerUUID
                                )
                                
                                if (insertResult > 0) {
                                    migratedCount++
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to migrate block from $playerUUID to $blockedUUID: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse player UUID ${entry.key}: ${e.message}")
                    }
                }
            }
            
            // Backup and remove old file
            val backupFile = dataFolder.resolve("blocks.conf.backup")
            try {
                Files.move(blocksFile, backupFile)
                logger.info("Backed up original blocks file to ${backupFile.fileName}")
            } catch (e: Exception) {
                logger.warn("Failed to backup original blocks file: ${e.message}")
            }
            
            val message = "Successfully migrated $migratedCount blocks from ${blocksFile.fileName}"
            logger.info(message)
            MigrationResult(migratedCount, message)
            
        } catch (e: Exception) {
            val message = "Migration failed: ${e.message}"
            logger.error(message, e)
            MigrationResult(0, message)
        }
    }
    
    private suspend fun ensurePlayerExists(tx: DatabaseService.TransactionContext, uuid: UUID) {
        val exists = tx.executeQuerySingle(
            "SELECT COUNT(*) as count FROM players WHERE uuid = ?",
            uuid
        ) { rs -> rs.getInt("count") } ?: 0
        
        if (exists == 0) {
            tx.executeUpdate(
                "INSERT INTO players (uuid, username, is_online) VALUES (?, ?, FALSE)",
                uuid, "Unknown"
            )
        }
    }
    
    suspend fun cleanupOldData(): MigrationResult = withContext(Dispatchers.IO) {
        try {
            logger.info("Starting cleanup of old block data ($retentionDays+ days)")
            
            val cutoffDate = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
            
            val deletedCount = databaseService.executeUpdate(
                "DELETE FROM player_blocks WHERE blocked_at < ?",
                cutoffDate
            )
            
            val message = "Cleaned up $deletedCount old block records"
            logger.info(message)
            MigrationResult(deletedCount, message, true)
        } catch (e: Exception) {
            val message = "Data cleanup failed: ${e.message}"
            logger.error(message, e)
            MigrationResult(0, message, false)
        }
    }
}