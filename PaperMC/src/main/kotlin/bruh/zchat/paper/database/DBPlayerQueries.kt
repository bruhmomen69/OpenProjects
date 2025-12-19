package bruh.zchat.paper.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.time.Instant
import java.util.*

class DBPlayerQueries(val databaseService: DatabaseService) {
    private val logger = LoggerFactory.getLogger(DBPlayerQueries::class.java)
    
    // Expose database type for other classes
    val databaseType: DatabaseType get() = databaseService.databaseType
    
    // Expose executeUpdate method for other classes
    suspend fun executeUpdate(sql: String, vararg params: Any): Int = withContext(Dispatchers.IO) {
        databaseService.executeUpdate(sql, *params)
    }

    // Player data queries
    suspend fun getPlayerData(uuid: UUID): PlayerDataQueryResult? = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuerySingle(
                "SELECT uuid, username, first_seen, last_seen, chat_disabled, messages_disabled FROM players WHERE uuid = ?",
                uuid
            ) { rs ->
                PlayerDataQueryResult(
                    uuid = UUID.fromString(rs.getString("uuid")),
                    username = rs.getString("username"),
                    firstSeen = rs.getTimestamp("first_seen").toInstant(),
                    lastSeen = rs.getTimestamp("last_seen").toInstant(),
                    chatDisabled = rs.getBoolean("chat_disabled"),
                    messagesDisabled = rs.getBoolean("messages_disabled")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get player data for $uuid", e)
            null
        }
    }

    suspend fun insertOrUpdatePlayer(
        uuid: UUID,
        username: String,
        now: Instant,
        serverInstanceId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val insertSql = when (databaseService.databaseType) {
                DatabaseType.MYSQL -> """INSERT IGNORE INTO players
                (uuid, username, first_seen, last_seen, online_server_id, online_last_heartbeat)
                VALUES (?, ?, ?, ?, ?, ?)"""
                DatabaseType.SQLITE -> """INSERT OR IGNORE INTO players
                (uuid, username, first_seen, last_seen, online_server_id, online_last_heartbeat)
                VALUES (?, ?, ?, ?, ?, ?)"""
            }

            databaseService.executeUpdate(
                insertSql,
                uuid, username, now, now, serverInstanceId, now
            )

            // Update existing player - update server ID and heartbeat
            databaseService.executeUpdate(
                "UPDATE players SET username = ?, last_seen = ?, online_server_id = ?, online_last_heartbeat = ? WHERE uuid = ?",
                username, now, serverInstanceId, now, uuid
            )

            true
        } catch (e: Exception) {
            logger.error("Failed to insert/update player $uuid", e)
            false
        }
    }

    suspend fun getPlayerInfractions(playerUuid: UUID): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuery(
                "SELECT group_name, count FROM player_infractions WHERE player_uuid = ? ORDER BY group_name",
                playerUuid
            ) { rs ->
                rs.getString("group_name") to rs.getInt("count")
            }.toMap()
        } catch (e: Exception) {
            logger.error("Failed to get infractions for player $playerUuid", e)
            emptyMap()
        }
    }

    suspend fun getPlayerInfractionCount(playerUuid: UUID, groupName: String): Int = withContext(Dispatchers.IO) {
        try {
            val count = databaseService.executeQuerySingle(
                "SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                playerUuid, groupName
            ) { rs -> rs.getInt("count") }
            
            count ?: 0
        } catch (e: Exception) {
            logger.error("Failed to get infraction count for player $playerUuid, group $groupName", e)
            0
        }
    }

    suspend fun getPlayerBlockedPlayers(playerUuid: UUID): Set<UUID> = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuery(
                "SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?",
                playerUuid
            ) { rs ->
                UUID.fromString(rs.getString("blocked_uuid"))
            }.toSet()
        } catch (e: Exception) {
            logger.error("Failed to get blocked players for player $playerUuid", e)
            emptySet()
        }
    }

    suspend fun getCurrentLastSeenFromDatabase(uuid: UUID): Instant = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuerySingle(
                "SELECT last_seen FROM players WHERE uuid = ?",
                uuid
            ) { rs -> rs.getTimestamp("last_seen").toInstant() } ?: Instant.EPOCH
        } catch (e: Exception) {
            logger.debug("Failed to get last_seen for player $uuid, using epoch", e)
            Instant.EPOCH
        }
    }

    // Infraction queries
    suspend fun updateInfractionCount(playerUuid: UUID, groupName: String, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?",
                newCount, playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to update infraction count for player $playerUuid, group $groupName", e)
            false
        }
    }
    
    suspend fun updateInfractionCount(tx: DatabaseService.TransactionContext, playerUuid: UUID, groupName: String, newCount: Int): Boolean {
        return try {
            tx.executeUpdate(
                "UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?",
                newCount, playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to update infraction count for player $playerUuid, group $groupName in transaction", e)
            false
        }
    }

    suspend fun insertNewInfraction(playerUuid: UUID, groupName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)",
                playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert new infraction for player $playerUuid, group $groupName", e)
            false
        }
    }
    
    suspend fun insertNewInfraction(tx: DatabaseService.TransactionContext, playerUuid: UUID, groupName: String): Boolean {
        return try {
            tx.executeUpdate(
                "INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)",
                playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert new infraction for player $playerUuid, group $groupName in transaction", e)
            false
        }
    }

    /**
     * Adds an infraction inside a transaction and returns the new count.
     */
    suspend fun addInfractionTransactional(playerUuid: UUID, groupName: String): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeTransaction { tx ->
                val existingCount = tx.executeQuerySingle(
                    "SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                    playerUuid, groupName
                ) { rs -> rs.getInt("count") }

                if (existingCount != null) {
                    val updatedCount = existingCount + 1
                    updateInfractionCount(tx, playerUuid, groupName, updatedCount)
                    updatedCount
                } else {
                    insertNewInfraction(tx, playerUuid, groupName)
                    1
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to add infraction for player $playerUuid, group $groupName in transaction", e)
            0
        }
    }

    suspend fun deleteInfraction(playerUuid: UUID, groupName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = databaseService.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                playerUuid, groupName
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete infraction for player $playerUuid, group $groupName", e)
            false
        }
    }
    
    suspend fun deleteInfraction(tx: DatabaseService.TransactionContext, playerUuid: UUID, groupName: String): Boolean {
        return try {
            val affectedRows = tx.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ? AND group_name = ?",
                playerUuid, groupName
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete infraction for player $playerUuid, group $groupName in transaction", e)
            false
        }
    }

    suspend fun deleteAllInfractions(playerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = databaseService.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ?",
                playerUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete all infractions for player $playerUuid", e)
            false
        }
    }
    
    suspend fun deleteAllInfractions(tx: DatabaseService.TransactionContext, playerUuid: UUID): Boolean {
        return try {
            val affectedRows = tx.executeUpdate(
                "DELETE FROM player_infractions WHERE player_uuid = ?",
                playerUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete all infractions for player $playerUuid in transaction", e)
            false
        }
    }

    suspend fun persistInfractions(tx: DatabaseService.TransactionContext, playerUuid: UUID, infractions: Map<String, Int>) {
        // Delete existing infractions
        tx.executeUpdate(
            "DELETE FROM player_infractions WHERE player_uuid = ?",
            playerUuid
        )
        
        // Insert current infractions
        infractions.forEach { (groupName, count) ->
            tx.executeUpdate(
                """INSERT INTO player_infractions 
                (player_uuid, group_name, count, last_updated) 
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)""",
                playerUuid, groupName, count
            )
        }
    }

    // Block queries
    suspend fun checkBlockExists(blockerUuid: UUID, blockedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = databaseService.executeQuerySingle(
                "SELECT id FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?",
                blockerUuid, blockedUuid
            ) { rs -> rs.getLong("id") }
            existing != null
        } catch (e: Exception) {
            logger.error("Failed to check if block exists", e)
            false
        }
    }
    
    suspend fun checkBlockExists(tx: DatabaseService.TransactionContext, blockerUuid: UUID, blockedUuid: UUID): Boolean {
        return try {
            val existing = tx.executeQuerySingle(
                "SELECT id FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?",
                blockerUuid, blockedUuid
            ) { rs -> rs.getLong("id") }
            existing != null
        } catch (e: Exception) {
            logger.error("Failed to check if block exists in transaction", e)
            false
        }
    }

    /**
     * Attempts to create a block entry inside a single transaction.
     * Performs existence and limit checks using the provided transaction context.
     */
    suspend fun createBlockWithChecks(
        blockerUuid: UUID,
        blockedUuid: UUID,
        blockerUsername: String,
        maxBlocksPerPlayer: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseService.executeTransaction { tx ->
                val exists = checkBlockExists(tx, blockerUuid, blockedUuid)
                if (exists) return@executeTransaction false

                val currentBlocks = getBlockCount(tx, blockerUuid)
                if (currentBlocks >= maxBlocksPerPlayer) return@executeTransaction false

                insertBlock(tx, blockerUuid, blockedUuid, blockerUsername)
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to create block with transaction for $blockerUuid -> $blockedUuid", e)
            false
        }
    }

    suspend fun getBlockCount(blockerUuid: UUID): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuery(
                "SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?",
                blockerUuid
            ) { rs -> rs.getString("blocked_uuid") }.size
        } catch (e: Exception) {
            logger.error("Failed to get block count for player $blockerUuid", e)
            0
        }
    }
    
    suspend fun getBlockCount(tx: DatabaseService.TransactionContext, blockerUuid: UUID): Int {
        return try {
            tx.executeQuery(
                "SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?",
                blockerUuid
            ) { rs -> rs.getString("blocked_uuid") }.size
        } catch (e: Exception) {
            logger.error("Failed to get block count for player $blockerUuid in transaction", e)
            0
        }
    }

    suspend fun insertBlock(blockerUuid: UUID, blockedUuid: UUID, blockerUsername: String): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """INSERT INTO player_blocks 
                (blocker_uuid, blocked_uuid, blocked_by_username) 
                VALUES (?, ?, ?)""",
                blockerUuid, blockedUuid, blockerUsername
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert block", e)
            false
        }
    }
    
    suspend fun insertBlock(tx: DatabaseService.TransactionContext, blockerUuid: UUID, blockedUuid: UUID, blockerUsername: String): Boolean {
        return try {
            tx.executeUpdate(
                """INSERT INTO player_blocks 
                (blocker_uuid, blocked_uuid, blocked_by_username) 
                VALUES (?, ?, ?)""",
                blockerUuid, blockedUuid, blockerUsername
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert block in transaction", e)
            false
        }
    }

    suspend fun deleteBlock(blockerUuid: UUID, blockedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val affectedRows = databaseService.executeUpdate(
                "DELETE FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?",
                blockerUuid, blockedUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete block", e)
            false
        }
    }

    suspend fun persistBlockedPlayers(tx: DatabaseService.TransactionContext, playerUuid: UUID, blockedPlayers: Set<UUID>, username: String) {
        // Delete existing blocked players
        tx.executeUpdate(
            "DELETE FROM player_blocks WHERE blocker_uuid = ?",
            playerUuid
        )
        
        // Insert current blocked players
        blockedPlayers.forEach { blockedUuid ->
            tx.executeUpdate(
                """INSERT INTO player_blocks 
                (blocker_uuid, blocked_uuid, blocked_at, blocked_by_username) 
                VALUES (?, ?, CURRENT_TIMESTAMP, ?)""",
                playerUuid, blockedUuid, username
            )
        }
    }

    // Message bus queries
    suspend fun claimMessages(serverInstanceId: String, targetServerId: String, batchSize: Int): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """UPDATE message_bus 
                   SET status = 'CLAIMED', claimed_by = ?, claimed_at = CURRENT_TIMESTAMP 
                   WHERE target_server_id = ? AND status = 'PENDING' 
                   ORDER BY id ASC LIMIT ?""",
                serverInstanceId, targetServerId, batchSize
            )
        } catch (e: Exception) {
            logger.error("Failed to claim messages", e)
            0
        }
    }

    suspend fun getClaimedMessages(serverInstanceId: String): List<ClaimedMessage> = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuery(
                """SELECT id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload
                   FROM message_bus
                   WHERE claimed_by = ? AND status = 'CLAIMED'""",
                serverInstanceId
            ) { rs ->
                ClaimedMessage(
                    id = rs.getLong("id"),
                    type = rs.getString("type"),
                    senderUuid = UUID.fromString(rs.getString("sender_uuid")),
                    senderName = rs.getString("sender_username"),
                    recipientUuid = UUID.fromString(rs.getString("recipient_uuid")),
                    recipientName = rs.getString("recipient_username"),
                    payloadJson = rs.getString("payload")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get claimed messages", e)
            emptyList()
        }
    }

    suspend fun insertMessageBus(
        targetServerId: String,
        type: String,
        senderUuid: UUID,
        senderUsername: String,
        recipientUuid: UUID,
        recipientUsername: String,
        payload: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """INSERT INTO message_bus 
                   (target_server_id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload, status) 
                   VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')""",
                targetServerId, type, senderUuid, senderUsername, recipientUuid, recipientUsername, payload
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert message bus entry", e)
            false
        }
    }

    suspend fun updateMessageStatus(id: Long, status: String, error: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (error == null) {
                databaseService.executeUpdate(
                    "UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = NULL WHERE id = ?",
                    status, id
                )
            } else {
                databaseService.executeUpdate(
                    "UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = ? WHERE id = ?",
                    status, error, id
                )
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to update message status for message $id", e)
            false
        }
    }

    suspend fun reclaimStaleMessages(cutoff: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """UPDATE message_bus 
                   SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL 
                   WHERE status = 'CLAIMED' AND claimed_at < ?""",
                cutoff
            )
        } catch (e: Exception) {
            logger.error("Failed to reclaim stale messages", e)
            0
        }
    }

    suspend fun deleteOldMessages(cutoff: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "DELETE FROM message_bus WHERE created_at < ?",
                cutoff
            )
        } catch (e: Exception) {
            logger.error("Failed to delete old messages", e)
            0
        }
    }

    suspend fun getSenderPresence(
        senderUuid: UUID,
        cutoff: Instant
    ): String? = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuerySingle(
                """SELECT online_server_id FROM players
                   WHERE uuid = ?
                   AND online_server_id IS NOT NULL
                   AND online_last_heartbeat IS NOT NULL
                   AND online_last_heartbeat >= ?""",
                senderUuid, cutoff
            ) { rs -> rs.getString("online_server_id") }
        } catch (e: Exception) {
            logger.error("Failed to get sender presence for $senderUuid", e)
            null
        }
    }

    suspend fun getUsername(uuid: UUID): String? = withContext(Dispatchers.IO) {
        try {
            databaseService.executeQuerySingle(
                "SELECT username FROM players WHERE uuid = ?",
                uuid
            ) { rs -> rs.getString("username") }
        } catch (e: Exception) {
            logger.error("Failed to get username for $uuid", e)
            null
        }
    }

    // Maintenance queries
    suspend fun archiveOldInfractions(cutoffDate: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """INSERT INTO player_infractions_archive 
                (player_uuid, group_name, count, last_updated, created_at, archived_at)
                SELECT player_uuid, group_name, count, last_updated, created_at, CURRENT_TIMESTAMP
                FROM player_infractions 
                WHERE last_updated < ?""",
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to archive old infractions", e)
            0
        }
    }

    suspend fun archiveOldBlocks(cutoffDate: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                """INSERT INTO player_blocks_archive 
                (blocker_uuid, blocked_uuid, blocked_at, blocked_by_username, archived_at)
                SELECT blocker_uuid, blocked_uuid, blocked_at, blocked_by_username, CURRENT_TIMESTAMP
                FROM player_blocks 
                WHERE blocked_at < ?""",
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to archive old blocks", e)
            0
        }
    }

    suspend fun cleanupOldInfractions(cutoffDate: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "DELETE FROM player_infractions WHERE last_updated < ?",
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to cleanup old infractions", e)
            0
        }
    }

    suspend fun cleanupOldBlocks(cutoffDate: Instant): Int = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "DELETE FROM player_blocks WHERE blocked_at < ?",
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to cleanup old blocks", e)
            0
        }
    }

    suspend fun vacuumDatabase(): Boolean = withContext(Dispatchers.IO) {
        try {
            when (databaseService.databaseType) {
                DatabaseType.SQLITE -> {
                    databaseService.executeUpdate("VACUUM")
                    databaseService.executeUpdate("ANALYZE")
                }
                DatabaseType.MYSQL -> {
                    databaseService.executeUpdate("OPTIMIZE TABLE player_infractions")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_infractions_archive")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_blocks")
                    databaseService.executeUpdate("OPTIMIZE TABLE player_blocks_archive")
                    databaseService.executeUpdate("OPTIMIZE TABLE message_bus")
                    databaseService.executeUpdate("OPTIMIZE TABLE players")
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to vacuum database", e)
            false
        }
    }

    // Data classes for query results
    data class PlayerDataQueryResult(
        val uuid: UUID,
        val username: String,
        val firstSeen: Instant,
        val lastSeen: Instant,
        val chatDisabled: Boolean,
        val messagesDisabled: Boolean
    )

    data class ClaimedMessage(
        val id: Long,
        val type: String,
        val senderUuid: UUID,
        val senderName: String,
        val recipientUuid: UUID,
        val recipientName: String,
        val payloadJson: String
    )
}
