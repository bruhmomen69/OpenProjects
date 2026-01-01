package bruh.zchat.paper.database

import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.DatabaseDialect
import bruh.zchat.utils.database.TransactionScope
import bruh.zchat.utils.database.getInstant
import bruh.zchat.utils.database.getUUIDOrThrow
import bruh.zchat.utils.database.sql
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Database queries for player data, infractions, blocks, and cross-server messaging.
 */
class DBPlayerQueries(private val database: Database) {
    private val logger = LoggerFactory.getLogger(DBPlayerQueries::class.java)
    
    val dialect: DatabaseDialect get() = database.dialect

    // ==================== Player Data Queries ====================
    
    /**
     * Gets player data from the database.
     */
    suspend fun getPlayerData(uuid: UUID): PlayerDataQueryResult? {
        return try {
            database.querySingle(
                sql("SELECT uuid, username, first_seen, last_seen, chat_disabled, messages_disabled FROM players WHERE uuid = ?"),
                uuid
            ) { rs ->
                PlayerDataQueryResult(
                    uuid = rs.getUUIDOrThrow("uuid"),
                    username = rs.getString("username"),
                    firstSeen = rs.getInstant("first_seen") ?: Instant.EPOCH,
                    lastSeen = rs.getInstant("last_seen") ?: Instant.EPOCH,
                    chatDisabled = rs.getBoolean("chat_disabled"),
                    messagesDisabled = rs.getBoolean("messages_disabled")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get player data for $uuid", e)
            null
        }
    }

    /**
     * Inserts a new player or updates an existing player's data.
     */
    suspend fun insertOrUpdatePlayer(
        uuid: UUID,
        username: String,
        now: Instant,
        serverInstanceId: String
    ): Boolean {
        return try {
            database.execute(
                sql {
                    mysql("""
                        INSERT IGNORE INTO players
                        (uuid, username, first_seen, last_seen, online_server_id, online_last_heartbeat)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """)
                    postgres("""
                        INSERT INTO players
                        (uuid, username, first_seen, last_seen, online_server_id, online_last_heartbeat)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (uuid) DO NOTHING
                    """)
                    sqlite("""
                        INSERT OR IGNORE INTO players
                        (uuid, username, first_seen, last_seen, online_server_id, online_last_heartbeat)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """)
                },
                uuid, username, now, now, serverInstanceId, now
            )

            database.execute(
                sql("UPDATE players SET username = ?, last_seen = ?, online_server_id = ?, online_last_heartbeat = ? WHERE uuid = ?"),
                username, now, serverInstanceId, now, uuid
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert/update player $uuid", e)
            false
        }
    }

    /**
     * Gets all infractions for a player.
     */
    suspend fun getPlayerInfractions(playerUuid: UUID): Map<String, Int> {
        return try {
            database.query(
                sql("SELECT group_name, count FROM player_infractions WHERE player_uuid = ? ORDER BY group_name"),
                playerUuid
            ) { rs ->
                rs.getString("group_name") to rs.getInt("count")
            }.toMap()
        } catch (e: Exception) {
            logger.error("Failed to get infractions for player $playerUuid", e)
            emptyMap()
        }
    }

    /**
     * Gets the infraction count for a player in a specific group.
     */
    suspend fun getPlayerInfractionCount(playerUuid: UUID, groupName: String): Int {
        return try {
            database.querySingle(
                sql("SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?"),
                playerUuid, groupName
            ) { rs -> rs.getInt("count") } ?: 0
        } catch (e: Exception) {
            logger.error("Failed to get infraction count for player $playerUuid, group $groupName", e)
            0
        }
    }

    /**
     * Gets all blocked players for a player.
     */
    suspend fun getPlayerBlockedPlayers(playerUuid: UUID): Set<UUID> {
        return try {
            database.query(
                sql("SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?"),
                playerUuid
            ) { rs ->
                UUID.fromString(rs.getString("blocked_uuid"))
            }.toSet()
        } catch (e: Exception) {
            logger.error("Failed to get blocked players for player $playerUuid", e)
            emptySet()
        }
    }

    /**
     * Gets the last seen timestamp for a player from the database.
     */
    suspend fun getCurrentLastSeenFromDatabase(uuid: UUID): Instant {
        return try {
            database.querySingle(
                sql("SELECT last_seen FROM players WHERE uuid = ?"),
                uuid
            ) { rs -> rs.getInstant("last_seen") } ?: Instant.EPOCH
        } catch (e: Exception) {
            logger.debug("Failed to get last_seen for player $uuid, using epoch", e)
            Instant.EPOCH
        }
    }

    // ==================== Infraction Queries ====================

    /**
     * Updates the infraction count for a player in a specific group.
     */
    suspend fun updateInfractionCount(playerUuid: UUID, groupName: String, newCount: Int): Boolean {
        return try {
            database.execute(
                sql("UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?"),
                newCount, playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to update infraction count for player $playerUuid, group $groupName", e)
            false
        }
    }

    /**
     * Updates infraction count within a transaction.
     */
    suspend fun updateInfractionCount(tx: TransactionScope, playerUuid: UUID, groupName: String, newCount: Int): Boolean {
        return try {
            tx.execute(
                sql("UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?"),
                newCount, playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to update infraction count for player $playerUuid, group $groupName in transaction", e)
            false
        }
    }

    /**
     * Inserts a new infraction record.
     */
    suspend fun insertNewInfraction(playerUuid: UUID, groupName: String): Boolean {
        return try {
            database.execute(
                sql("INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)"),
                playerUuid, groupName
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert new infraction for player $playerUuid, group $groupName", e)
            false
        }
    }

    /**
     * Inserts a new infraction within a transaction.
     */
    suspend fun insertNewInfraction(tx: TransactionScope, playerUuid: UUID, groupName: String): Boolean {
        return try {
            tx.execute(
                sql("INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)"),
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
    suspend fun addInfractionTransactional(playerUuid: UUID, groupName: String): Int {
        return try {
            database.transaction {
                val existingCount = querySingle(
                    sql("SELECT count FROM player_infractions WHERE player_uuid = ? AND group_name = ?"),
                    playerUuid, groupName
                ) { rs -> rs.getInt("count") }

                if (existingCount != null) {
                    val updatedCount = existingCount + 1
                    execute(
                        sql("UPDATE player_infractions SET count = ?, last_updated = CURRENT_TIMESTAMP WHERE player_uuid = ? AND group_name = ?"),
                        updatedCount, playerUuid, groupName
                    )
                    updatedCount
                } else {
                    execute(
                        sql("INSERT INTO player_infractions (player_uuid, group_name, count) VALUES (?, ?, 1)"),
                        playerUuid, groupName
                    )
                    1
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to add infraction for player $playerUuid, group $groupName in transaction", e)
            0
        }
    }

    /**
     * Deletes an infraction record.
     */
    suspend fun deleteInfraction(playerUuid: UUID, groupName: String): Boolean {
        return try {
            val affectedRows = database.execute(
                sql("DELETE FROM player_infractions WHERE player_uuid = ? AND group_name = ?"),
                playerUuid, groupName
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete infraction for player $playerUuid, group $groupName", e)
            false
        }
    }

    /**
     * Deletes an infraction within a transaction.
     */
    suspend fun deleteInfraction(tx: TransactionScope, playerUuid: UUID, groupName: String): Boolean {
        return try {
            val affectedRows = tx.execute(
                sql("DELETE FROM player_infractions WHERE player_uuid = ? AND group_name = ?"),
                playerUuid, groupName
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete infraction for player $playerUuid, group $groupName in transaction", e)
            false
        }
    }

    /**
     * Deletes all infractions for a player.
     */
    suspend fun deleteAllInfractions(playerUuid: UUID): Boolean {
        return try {
            val affectedRows = database.execute(
                sql("DELETE FROM player_infractions WHERE player_uuid = ?"),
                playerUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete all infractions for player $playerUuid", e)
            false
        }
    }

    /**
     * Deletes all infractions within a transaction.
     */
    suspend fun deleteAllInfractions(tx: TransactionScope, playerUuid: UUID): Boolean {
        return try {
            val affectedRows = tx.execute(
                sql("DELETE FROM player_infractions WHERE player_uuid = ?"),
                playerUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete all infractions for player $playerUuid in transaction", e)
            false
        }
    }

    /**
     * Persists infractions within a transaction.
     */
    suspend fun persistInfractions(tx: TransactionScope, playerUuid: UUID, infractions: Map<String, Int>) {
        tx.execute(
            sql("DELETE FROM player_infractions WHERE player_uuid = ?"),
            playerUuid
        )
        
        infractions.forEach { (groupName, count) ->
            tx.execute(
                sql("INSERT INTO player_infractions (player_uuid, group_name, count, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP)"),
                playerUuid, groupName, count
            )
        }
    }

    // ==================== Block Queries ====================

    /**
     * Checks if a block exists.
     */
    suspend fun checkBlockExists(blockerUuid: UUID, blockedUuid: UUID): Boolean {
        return try {
            val existing = database.querySingle(
                sql("SELECT id FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?"),
                blockerUuid, blockedUuid
            ) { rs -> rs.getLong("id") }
            existing != null
        } catch (e: Exception) {
            logger.error("Failed to check if block exists", e)
            false
        }
    }

    /**
     * Checks if a block exists within a transaction.
     */
    suspend fun checkBlockExists(tx: TransactionScope, blockerUuid: UUID, blockedUuid: UUID): Boolean {
        return try {
            val existing = tx.querySingle(
                sql("SELECT id FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?"),
                blockerUuid, blockedUuid
            ) { rs -> rs.getLong("id") }
            existing != null
        } catch (e: Exception) {
            logger.error("Failed to check if block exists in transaction", e)
            false
        }
    }

    /**
     * Creates a block with existence and limit checks in a transaction.
     */
    suspend fun createBlockWithChecks(
        blockerUuid: UUID,
        blockedUuid: UUID,
        blockerUsername: String,
        maxBlocksPerPlayer: Int
    ): Boolean {
        return try {
            database.transaction {
                val exists = querySingle(
                    sql("SELECT id FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?"),
                    blockerUuid, blockedUuid
                ) { true } != null
                if (exists) return@transaction false

                val currentBlocks = query(
                    sql("SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?"),
                    blockerUuid
                ) { it }.size
                if (currentBlocks >= maxBlocksPerPlayer) return@transaction false

                execute(
                    sql("INSERT INTO player_blocks (blocker_uuid, blocked_uuid, blocked_by_username) VALUES (?, ?, ?)"),
                    blockerUuid, blockedUuid, blockerUsername
                )
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to create block with transaction for $blockerUuid -> $blockedUuid", e)
            false
        }
    }

    /**
     * Gets the block count for a player.
     */
    suspend fun getBlockCount(blockerUuid: UUID): Int {
        return try {
            database.query(
                sql("SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?"),
                blockerUuid
            ) { rs -> rs.getString("blocked_uuid") }.size
        } catch (e: Exception) {
            logger.error("Failed to get block count for player $blockerUuid", e)
            0
        }
    }

    /**
     * Gets the block count within a transaction.
     */
    suspend fun getBlockCount(tx: TransactionScope, blockerUuid: UUID): Int {
        return try {
            tx.query(
                sql("SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?"),
                blockerUuid
            ) { rs -> rs.getString("blocked_uuid") }.size
        } catch (e: Exception) {
            logger.error("Failed to get block count for player $blockerUuid in transaction", e)
            0
        }
    }

    /**
     * Inserts a block record.
     */
    suspend fun insertBlock(blockerUuid: UUID, blockedUuid: UUID, blockerUsername: String): Boolean {
        return try {
            database.execute(
                sql("INSERT INTO player_blocks (blocker_uuid, blocked_uuid, blocked_by_username) VALUES (?, ?, ?)"),
                blockerUuid, blockedUuid, blockerUsername
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert block", e)
            false
        }
    }

    /**
     * Inserts a block within a transaction.
     */
    suspend fun insertBlock(tx: TransactionScope, blockerUuid: UUID, blockedUuid: UUID, blockerUsername: String): Boolean {
        return try {
            tx.execute(
                sql("INSERT INTO player_blocks (blocker_uuid, blocked_uuid, blocked_by_username) VALUES (?, ?, ?)"),
                blockerUuid, blockedUuid, blockerUsername
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert block in transaction", e)
            false
        }
    }

    /**
     * Deletes a block record.
     */
    suspend fun deleteBlock(blockerUuid: UUID, blockedUuid: UUID): Boolean {
        return try {
            val affectedRows = database.execute(
                sql("DELETE FROM player_blocks WHERE blocker_uuid = ? AND blocked_uuid = ?"),
                blockerUuid, blockedUuid
            )
            affectedRows > 0
        } catch (e: Exception) {
            logger.error("Failed to delete block", e)
            false
        }
    }

    /**
     * Persists blocked players within a transaction.
     */
    suspend fun persistBlockedPlayers(tx: TransactionScope, playerUuid: UUID, blockedPlayers: Set<UUID>, username: String) {
        tx.execute(
            sql("DELETE FROM player_blocks WHERE blocker_uuid = ?"),
            playerUuid
        )
        
        blockedPlayers.forEach { blockedUuid ->
            tx.execute(
                sql("INSERT INTO player_blocks (blocker_uuid, blocked_uuid, blocked_at, blocked_by_username) VALUES (?, ?, CURRENT_TIMESTAMP, ?)"),
                playerUuid, blockedUuid, username
            )
        }
    }

    // ==================== Message Bus Queries ====================

    /**
     * Claims pending messages for processing.
     */
    suspend fun claimMessages(serverInstanceId: String, targetServerId: String, batchSize: Int): Int {
        return try {
            database.execute(
                sql {
                    mysql("""
                        UPDATE message_bus 
                        SET status = 'CLAIMED', claimed_by = ?, claimed_at = CURRENT_TIMESTAMP 
                        WHERE target_server_id = ? AND status = 'PENDING' 
                        ORDER BY id ASC LIMIT ?
                    """)
                    postgres("""
                        UPDATE message_bus 
                        SET status = 'CLAIMED', claimed_by = ?, claimed_at = CURRENT_TIMESTAMP 
                        WHERE id IN (
                            SELECT id FROM message_bus 
                            WHERE target_server_id = ? AND status = 'PENDING' 
                            ORDER BY id ASC LIMIT ?
                        )
                    """)
                    sqlite("""
                        UPDATE message_bus 
                        SET status = 'CLAIMED', claimed_by = ?, claimed_at = CURRENT_TIMESTAMP 
                        WHERE target_server_id = ? AND status = 'PENDING' 
                        ORDER BY id ASC LIMIT ?
                    """)
                },
                serverInstanceId, targetServerId, batchSize
            )
        } catch (e: Exception) {
            logger.error("Failed to claim messages", e)
            0
        }
    }

    /**
     * Gets all claimed messages for a server instance.
     */
    suspend fun getClaimedMessages(serverInstanceId: String): List<ClaimedMessage> {
        return try {
            database.query(
                sql("""
                    SELECT id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload
                    FROM message_bus
                    WHERE claimed_by = ? AND status = 'CLAIMED'
                """),
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

    /**
     * Inserts a message into the message bus.
     */
    suspend fun insertMessageBus(
        targetServerId: String,
        type: String,
        senderUuid: UUID,
        senderUsername: String,
        recipientUuid: UUID,
        recipientUsername: String,
        payload: String
    ): Boolean {
        return try {
            database.execute(
                sql("""
                    INSERT INTO message_bus 
                    (target_server_id, type, sender_uuid, sender_username, recipient_uuid, recipient_username, payload, status) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """),
                targetServerId, type, senderUuid, senderUsername, recipientUuid, recipientUsername, payload
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to insert message bus entry", e)
            false
        }
    }

    /**
     * Updates the status of a message.
     */
    suspend fun updateMessageStatus(id: Long, status: String, error: String? = null): Boolean {
        return try {
            if (error == null) {
                database.execute(
                    sql("UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = NULL WHERE id = ?"),
                    status, id
                )
            } else {
                database.execute(
                    sql("UPDATE message_bus SET status = ?, delivered_at = CURRENT_TIMESTAMP, error = ? WHERE id = ?"),
                    status, error, id
                )
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to update message status for message $id", e)
            false
        }
    }

    /**
     * Reclaims stale messages that were claimed but not processed.
     */
    suspend fun reclaimStaleMessages(cutoff: Instant): Int {
        return try {
            database.execute(
                sql("""
                    UPDATE message_bus 
                    SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL 
                    WHERE status = 'CLAIMED' AND claimed_at < ?
                """),
                cutoff
            )
        } catch (e: Exception) {
            logger.error("Failed to reclaim stale messages", e)
            0
        }
    }

    /**
     * Deletes old messages from the message bus.
     */
    suspend fun deleteOldMessages(cutoff: Instant): Int {
        return try {
            database.execute(
                sql("DELETE FROM message_bus WHERE created_at < ?"),
                cutoff
            )
        } catch (e: Exception) {
            logger.error("Failed to delete old messages", e)
            0
        }
    }

    /**
     * Gets the server presence for a sender.
     */
    suspend fun getSenderPresence(senderUuid: UUID, cutoff: Instant): String? {
        return try {
            database.querySingle(
                sql("""
                    SELECT online_server_id FROM players
                    WHERE uuid = ?
                    AND online_server_id IS NOT NULL
                    AND online_last_heartbeat IS NOT NULL
                    AND online_last_heartbeat >= ?
                """),
                senderUuid, cutoff
            ) { rs -> rs.getString("online_server_id") }
        } catch (e: Exception) {
            logger.error("Failed to get sender presence for $senderUuid", e)
            null
        }
    }

    /**
     * Gets a player's username by UUID.
     */
    suspend fun getUsername(uuid: UUID): String? {
        return try {
            database.querySingle(
                sql("SELECT username FROM players WHERE uuid = ?"),
                uuid
            ) { rs -> rs.getString("username") }
        } catch (e: Exception) {
            logger.error("Failed to get username for $uuid", e)
            null
        }
    }

    // ==================== Maintenance Queries ====================

    /**
     * Archives old infractions.
     */
    suspend fun archiveOldInfractions(cutoffDate: Instant): Int {
        return try {
            database.execute(
                sql("""
                    INSERT INTO player_infractions_archive 
                    (player_uuid, group_name, count, last_updated, created_at, archived_at)
                    SELECT player_uuid, group_name, count, last_updated, created_at, CURRENT_TIMESTAMP
                    FROM player_infractions 
                    WHERE last_updated < ?
                """),
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to archive old infractions", e)
            0
        }
    }

    /**
     * Cleans up old infractions.
     */
    suspend fun cleanupOldInfractions(cutoffDate: Instant): Int {
        return try {
            database.execute(
                sql("DELETE FROM player_infractions WHERE last_updated < ?"),
                cutoffDate
            )
        } catch (e: Exception) {
            logger.error("Failed to cleanup old infractions", e)
            0
        }
    }

    /**
     * Optimizes the database.
     */
    suspend fun vacuumDatabase(): Boolean {
        return try {
            when (database.dialect) {
                DatabaseDialect.SQLITE -> {
                    database.execute(sql("VACUUM"))
                    database.execute(sql("ANALYZE"))
                }
                DatabaseDialect.MYSQL -> {
                    database.execute(sql("OPTIMIZE TABLE player_infractions"))
                    database.execute(sql("OPTIMIZE TABLE player_infractions_archive"))
                    database.execute(sql("OPTIMIZE TABLE player_blocks"))
                    database.execute(sql("OPTIMIZE TABLE message_bus"))
                    database.execute(sql("OPTIMIZE TABLE players"))
                }
                DatabaseDialect.POSTGRES -> {
                    database.execute(sql("VACUUM ANALYZE player_infractions"))
                    database.execute(sql("VACUUM ANALYZE player_infractions_archive"))
                    database.execute(sql("VACUUM ANALYZE player_blocks"))
                    database.execute(sql("VACUUM ANALYZE message_bus"))
                    database.execute(sql("VACUUM ANALYZE players"))
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to vacuum database", e)
            false
        }
    }

    // ==================== Data Classes ====================

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
