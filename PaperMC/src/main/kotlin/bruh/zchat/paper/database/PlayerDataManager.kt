package bruh.zchat.paper.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlayerDataManager(private val databaseService: DatabaseService) {
    private val logger = LoggerFactory.getLogger(PlayerDataManager::class.java)
    private val onlinePlayers = ConcurrentHashMap<UUID, PlayerData>()
    
    data class PlayerData(
        val uuid: UUID,
        val username: String,
        val firstSeen: Instant,
        val lastSeen: Instant,
        val isOnline: Boolean,
        val infractions: Map<String, Int>,
        val blockedPlayers: Set<UUID>,
        val joinTimestamp: Instant = Instant.now(), // Added for server switching detection
        val onlineServerId: String? = null,
        val chatDisabled: Boolean = false,
        val messagesDisabled: Boolean = false,
        var isDirty: Boolean = false
    )

    data class ToggleState(
        val chatDisabled: Boolean,
        val messagesDisabled: Boolean
    )
    
    suspend fun onPlayerJoin(player: Player, serverInstanceId: String): PlayerData = withContext(Dispatchers.IO) {
        val now = Instant.now()
        
        try {
            // Check if player exists
            val existingPlayer = databaseService.executeQuerySingle(
                "SELECT uuid, username, first_seen, last_seen, chat_disabled, messages_disabled FROM players WHERE uuid = ?",
                player.uniqueId
            ) { rs -> 
                PlayerData(
                    uuid = UUID.fromString(rs.getString("uuid")),
                    username = rs.getString("username"),
                    firstSeen = rs.getTimestamp("first_seen").toInstant(),
                    lastSeen = rs.getTimestamp("last_seen").toInstant(),
                    isOnline = true,
                    infractions = emptyMap(),
                    blockedPlayers = emptySet(),
                    joinTimestamp = now, // Initialize with current join time for existing players
                    onlineServerId = serverInstanceId,
                    chatDisabled = rs.getBoolean("chat_disabled"),
                    messagesDisabled = rs.getBoolean("messages_disabled")
                )
            }

            if (existingPlayer == null) {
                val insertSql = when (databaseService.databaseType) {
                    DatabaseType.MYSQL -> """INSERT IGNORE INTO players
                    (uuid, username, first_seen, last_seen, is_online, online_server_id, online_last_heartbeat)
                    VALUES (?, ?, ?, ?, ?, ?, ?)"""
                    DatabaseType.SQLITE -> """INSERT OR IGNORE INTO players
                    (uuid, username, first_seen, last_seen, is_online, online_server_id, online_last_heartbeat)
                    VALUES (?, ?, ?, ?, ?, ?, ?)"""
                }

                databaseService.executeUpdate(
                    insertSql,
                    player.uniqueId, player.name, now, now, true, serverInstanceId, now
                )
            }
            
            // Update existing player - ensure is_online is set to TRUE and update server ID
            databaseService.executeUpdate(
                "UPDATE players SET username = ?, last_seen = ?, is_online = TRUE, online_server_id = ?, online_last_heartbeat = ? WHERE uuid = ?",
                player.name, now, serverInstanceId, now, player.uniqueId
            )
            
            // Load infractions
            val infractions = databaseService.executeQuery(
                "SELECT group_name, count FROM player_infractions WHERE player_uuid = ?",
                player.uniqueId
            ) { rs ->
                rs.getString("group_name") to rs.getInt("count")
            }.toMap()
            
            // Load blocked players
            val blockedPlayers = databaseService.executeQuery(
                "SELECT blocked_uuid FROM player_blocks WHERE blocker_uuid = ?",
                player.uniqueId
            ) { rs ->
                UUID.fromString(rs.getString("blocked_uuid"))
            }.toSet()
            
            val playerData = (existingPlayer ?: PlayerData(
                uuid = player.uniqueId,
                username = player.name,
                firstSeen = now,
                lastSeen = now,
                isOnline = true,
                infractions = emptyMap(),
                blockedPlayers = emptySet(),
                joinTimestamp = now,
                onlineServerId = serverInstanceId
            )).copy(
                username = player.name,
                lastSeen = now,
                isOnline = true,
                infractions = infractions,
                blockedPlayers = blockedPlayers,
                joinTimestamp = now,
                onlineServerId = serverInstanceId
            )
            
            onlinePlayers[player.uniqueId] = playerData
            
            logger.debug("Loaded player data for ${player.name}: ${infractions.size} infractions, ${blockedPlayers.size} blocked players")
            playerData
            
        } catch (e: Exception) {
            // Create new player if doesn't exist
            val insertSql = when (databaseService.databaseType) {
                DatabaseType.MYSQL -> """INSERT IGNORE INTO players
                (uuid, username, first_seen, last_seen, is_online, online_server_id, online_last_heartbeat)
                VALUES (?, ?, ?, ?, ?, ?, ?)"""
                DatabaseType.SQLITE -> """INSERT OR IGNORE INTO players
                (uuid, username, first_seen, last_seen, is_online, online_server_id, online_last_heartbeat)
                VALUES (?, ?, ?, ?, ?, ?, ?)"""
            }

            databaseService.executeUpdate(
                insertSql,
                player.uniqueId, player.name, now, now, true, serverInstanceId, now
            )
            
            val playerData = PlayerData(
                uuid = player.uniqueId,
                username = player.name,
                firstSeen = now,
                lastSeen = now,
                isOnline = true,
                infractions = emptyMap(),
                blockedPlayers = emptySet(),
                joinTimestamp = now,
                onlineServerId = serverInstanceId
            )
            
            onlinePlayers[player.uniqueId] = playerData
            
            logger.debug("Created new player data for ${player.name}")
            playerData
        }
    }

    suspend fun clearAllToggleStates() = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "UPDATE players SET chat_disabled = ?, messages_disabled = ?",
                false,
                false
            )

            val snapshot = onlinePlayers.toMap()
            snapshot.forEach { (uuid, data) ->
                onlinePlayers[uuid] = data.copy(chatDisabled = false, messagesDisabled = false)
            }
        } catch (e: Exception) {
            logger.error("Failed to clear all toggle states", e)
        }
    }

    fun setChatDisabledCached(uuid: UUID, disabled: Boolean) {
        val cached = onlinePlayers[uuid] ?: return
        onlinePlayers[uuid] = cached.copy(chatDisabled = disabled)
    }

    fun setMessagesDisabledCached(uuid: UUID, disabled: Boolean) {
        val cached = onlinePlayers[uuid] ?: return
        onlinePlayers[uuid] = cached.copy(messagesDisabled = disabled)
    }

    /**
     * Fast-path check for online players.
     */
    fun isChatDisabledOnline(uuid: UUID): Boolean = onlinePlayers[uuid]?.chatDisabled ?: false

    /**
     * Fast-path check for online players.
     */
    fun isMessagesDisabledOnline(uuid: UUID): Boolean = onlinePlayers[uuid]?.messagesDisabled ?: false

    suspend fun getToggleState(uuid: UUID): ToggleState? = withContext(Dispatchers.IO) {
        val cached = onlinePlayers[uuid]
        if (cached != null) {
            return@withContext ToggleState(cached.chatDisabled, cached.messagesDisabled)
        }

        try {
            return@withContext databaseService.executeQuerySingle(
                "SELECT chat_disabled, messages_disabled FROM players WHERE uuid = ?",
                uuid
            ) { rs ->
                ToggleState(
                    chatDisabled = rs.getBoolean("chat_disabled"),
                    messagesDisabled = rs.getBoolean("messages_disabled")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get toggle state for $uuid", e)
            null
        }
    }

    suspend fun setChatDisabled(uuid: UUID, disabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "UPDATE players SET chat_disabled = ? WHERE uuid = ?",
                disabled,
                uuid
            )

            val cached = onlinePlayers[uuid]
            if (cached != null) {
                onlinePlayers[uuid] = cached.copy(chatDisabled = disabled)
            }
        } catch (e: Exception) {
            logger.error("Failed to update chat_disabled for $uuid", e)
        }
    }

    suspend fun setMessagesDisabled(uuid: UUID, disabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            databaseService.executeUpdate(
                "UPDATE players SET messages_disabled = ? WHERE uuid = ?",
                disabled,
                uuid
            )

            val cached = onlinePlayers[uuid]
            if (cached != null) {
                onlinePlayers[uuid] = cached.copy(messagesDisabled = disabled)
            }
        } catch (e: Exception) {
            logger.error("Failed to update messages_disabled for $uuid", e)
        }
    }

    suspend fun getUsernameByUuid(uuid: UUID): String? = withContext(Dispatchers.IO) {
        val cached = onlinePlayers[uuid]
        if (cached != null) return@withContext cached.username

        try {
            return@withContext databaseService.executeQuerySingle(
                "SELECT username FROM players WHERE uuid = ?",
                uuid
            ) { rs -> rs.getString("username") }
        } catch (e: Exception) {
            logger.error("Failed to resolve username for uuid $uuid", e)
            null
        }
    }
    
    suspend fun onPlayerQuit(player: Player) = withContext(Dispatchers.IO) {
        try {
            val now = Instant.now()
            
            // Get player data from cache and persist if dirty before removal
            val playerData = onlinePlayers[player.uniqueId]
            if (playerData != null) {
                if (playerData.isDirty) {
                    persistPlayerData(playerData)
                    logger.debug("Persisted dirty data for ${player.name} on quit")
                }
                
                // Check for server switching scenario before updating is_online
                val currentLastSeen = getCurrentLastSeenFromDatabase(player.uniqueId)
                val joinTimestamp = playerData.joinTimestamp
                val timeSinceJoin = now.epochSecond - joinTimestamp.epochSecond
                val timeSinceLastSeen = if (currentLastSeen != Instant.EPOCH) {
                    now.epochSecond - currentLastSeen.epochSecond
                } else {
                    0L
                }
                
                // Don't set offline if this appears to be a server switch:
                // 1. Last seen in database is >= 10 seconds newer than our stored join time
                // 2. The last seen update was within the last 30 seconds  
                // 3. Player was online for less than 10 seconds (likely server switch)
                val isServerSwitch = timeSinceLastSeen >= 10 && timeSinceLastSeen <= 30 && timeSinceJoin < 10
                
                if (isServerSwitch) {
                    // Likely server switch - update last_seen but keep is_online = TRUE
                    databaseService.executeUpdate(
                        "UPDATE players SET last_seen = ? WHERE uuid = ?",
                        now, player.uniqueId
                    )
                    logger.debug("Skipping is_online update for ${player.name} - likely server switch detected (timeSinceJoin: ${timeSinceJoin}s, timeSinceLastSeen: ${timeSinceLastSeen}s)")
                } else {
                    // Normal quit - update database to set is_online = FALSE and update last_seen
                    // Also clear presence data
                    databaseService.executeUpdate(
                        "UPDATE players SET last_seen = ?, is_online = FALSE, online_server_id = NULL, online_last_heartbeat = NULL WHERE uuid = ?",
                        now, player.uniqueId
                    )
                }
                
                // Remove from online cache AFTER persisting and database update
                onlinePlayers.remove(player.uniqueId)
                
                logger.debug("Saved player data for ${player.name}: ${playerData.infractions.size} infractions, ${playerData.blockedPlayers.size} blocked players")
            } else {
                // Player data not in cache, still update database to ensure is_online = FALSE
                databaseService.executeUpdate(
                    "UPDATE players SET last_seen = ?, is_online = FALSE, online_server_id = NULL, online_last_heartbeat = NULL WHERE uuid = ?",
                    now, player.uniqueId
                )
                logger.debug("Updated database for ${player.name} (data not in cache)")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to save player data for ${player.name}", e)
        }
    }
    
    suspend fun updateHeartbeat(serverInstanceId: String) = withContext(Dispatchers.IO) {
        val onlineUuids = onlinePlayers.keys.toList()
        if (onlineUuids.isEmpty()) return@withContext
        
        try {
            // Batch update for all online players
            // Using a single query is more efficient than iterating
            val placeholders = onlineUuids.joinToString(",") { "?" }
            val sql = "UPDATE players SET online_last_heartbeat = CURRENT_TIMESTAMP WHERE online_server_id = ? AND uuid IN ($placeholders)"
            
            val params = mutableListOf<Any>(serverInstanceId)
            params.addAll(onlineUuids)
            
            databaseService.executeUpdate(sql, *params.toTypedArray())
        } catch (e: Exception) {
            logger.error("Failed to update heartbeats", e)
        }
    }
    
    // NEW: Get cross-server presence
    suspend fun getCrossServerPresence(uuid: UUID, heartbeatTimeoutSeconds: Int): String? = withContext(Dispatchers.IO) {
        // Check local cache first
        val cached = onlinePlayers[uuid]
        if (cached != null) return@withContext cached.onlineServerId
        
        val cutoff = Instant.now().minusSeconds(heartbeatTimeoutSeconds.toLong())
        
        // Query database
        try {
            return@withContext databaseService.executeQuerySingle(
                "SELECT online_server_id, online_last_heartbeat, is_online FROM players WHERE uuid = ?",
                uuid
            ) { rs ->
                val isOnline = rs.getBoolean("is_online")
                val serverId = rs.getString("online_server_id")
                val lastHeartbeat = rs.getTimestamp("online_last_heartbeat")?.toInstant()
                
                if (isOnline && serverId != null && lastHeartbeat != null && lastHeartbeat.isAfter(cutoff)) {
                    serverId
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get cross-server presence for $uuid", e)
            null
        }
    }
    
    suspend fun getUuidByUsername(username: String): UUID? = withContext(Dispatchers.IO) {
        // Check online players first (fast path)
        val online = onlinePlayers.values.find { it.username.equals(username, ignoreCase = true) }
        if (online != null) return@withContext online.uuid
        
        // Query database
        try {
            return@withContext databaseService.executeQuerySingle(
                "SELECT uuid FROM players WHERE username = ?",
                username
            ) { rs -> UUID.fromString(rs.getString("uuid")) }
        } catch (e: Exception) {
            logger.error("Failed to resolve UUID for username $username", e)
            null
        }
    }
    
    suspend fun getPlayerData(uuid: UUID): PlayerData? {
        return onlinePlayers[uuid]
    }
    
    fun getOnlinePlayerData(): Map<UUID, PlayerData> {
        return onlinePlayers.toMap()
    }
    
    suspend fun markInfractionDirty(playerUuid: UUID) {
        val playerData = onlinePlayers[playerUuid]
        if (playerData != null) {
            playerData.isDirty = true
        }
    }
    
    suspend fun markBlockDirty(playerUuid: UUID) {
        val playerData = onlinePlayers[playerUuid]
        if (playerData != null) {
            playerData.isDirty = true
        }
    }
    
    suspend fun updatePlayerInfractions(playerUuid: UUID, infractions: Map<String, Int>) = withContext(Dispatchers.IO) {
        val playerData = onlinePlayers[playerUuid]
        if (playerData != null) {
            onlinePlayers[playerUuid] = playerData.copy(infractions = infractions, isDirty = true)
        }
    }
    
    suspend fun updatePlayerBlockedPlayers(playerUuid: UUID, blockedPlayers: Set<UUID>) = withContext(Dispatchers.IO) {
        val playerData = onlinePlayers[playerUuid]
        if (playerData != null) {
            onlinePlayers[playerUuid] = playerData.copy(blockedPlayers = blockedPlayers, isDirty = true)
        }
    }
    
    // NEW: Persist individual player data
    private suspend fun persistPlayerData(playerData: PlayerData) {
        try {
            databaseService.executeTransaction { tx ->
                // Persist infractions
                persistInfractions(tx, playerData)
                // Persist blocked players
                persistBlockedPlayers(tx, playerData)
            }
            playerData.isDirty = false
        } catch (e: Exception) {
            logger.error("Failed to persist dirty data for player ${playerData.uuid}", e)
            throw e
        }
    }
    

    
    // Helper method to get current lastSeen from database for server switching detection
    private suspend fun getCurrentLastSeenFromDatabase(uuid: UUID): Instant {
        return try {
            databaseService.executeQuerySingle(
                "SELECT last_seen FROM players WHERE uuid = ?",
                uuid
            ) { rs -> rs.getTimestamp("last_seen").toInstant() } ?: Instant.EPOCH
        } catch (e: Exception) {
            logger.debug("Failed to get last_seen for player $uuid, using epoch", e)
            Instant.EPOCH
        }
    }
    
    // NEW: Helper methods for persisting specific data
    private suspend fun persistInfractions(tx: DatabaseService.TransactionContext, playerData: PlayerData) {
        // Delete existing infractions
        tx.executeUpdate(
            "DELETE FROM player_infractions WHERE player_uuid = ?",
            playerData.uuid
        )
        
        // Insert current infractions
        playerData.infractions.forEach { (groupName, count) ->
            tx.executeUpdate(
                """INSERT INTO player_infractions 
                (player_uuid, group_name, count, last_updated) 
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)""",
                playerData.uuid, groupName, count
            )
        }
    }
    
    private suspend fun persistBlockedPlayers(tx: DatabaseService.TransactionContext, playerData: PlayerData) {
        // Delete existing blocked players
        tx.executeUpdate(
            "DELETE FROM player_blocks WHERE blocker_uuid = ?",
            playerData.uuid
        )
        
        // Insert current blocked players
        playerData.blockedPlayers.forEach { blockedUuid ->
            tx.executeUpdate(
                """INSERT INTO player_blocks 
                (blocker_uuid, blocked_uuid, blocked_at, blocked_by_username) 
                VALUES (?, ?, CURRENT_TIMESTAMP, ?)""",
                playerData.uuid, blockedUuid, playerData.username
            )
        }
    }
}

