package bruh.zchat.paper.database

import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import bruh.zchat.utils.database.getInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlayerDataManager(
    private val database: Database,
    private val dbPlayerQueries: DBPlayerQueries
) {
    private val logger = LoggerFactory.getLogger(PlayerDataManager::class.java)
    private val onlinePlayers = ConcurrentHashMap<UUID, PlayerData>()
    
    data class PlayerData(
        val uuid: UUID,
        val username: String,
        val firstSeen: Instant,
        val lastSeen: Instant,
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
            val existingPlayerData = dbPlayerQueries.getPlayerData(player.uniqueId)
            val existingPlayer = existingPlayerData?.let { data ->
                PlayerData(
                    uuid = data.uuid,
                    username = data.username,
                    firstSeen = data.firstSeen,
                    lastSeen = data.lastSeen,
                    infractions = emptyMap(),
                    blockedPlayers = emptySet(),
                    joinTimestamp = now, // Initialize with current join time for existing players
                    onlineServerId = serverInstanceId,
                    chatDisabled = data.chatDisabled,
                    messagesDisabled = data.messagesDisabled
                )
            }

            if (existingPlayerData == null) {
                dbPlayerQueries.insertOrUpdatePlayer(player.uniqueId, player.name, now, serverInstanceId)
            } else {
                dbPlayerQueries.insertOrUpdatePlayer(player.uniqueId, player.name, now, serverInstanceId)
            }
            
            // Load infractions
            val infractions = dbPlayerQueries.getPlayerInfractions(player.uniqueId)
            
            // Load blocked players
            val blockedPlayers = dbPlayerQueries.getPlayerBlockedPlayers(player.uniqueId)
            
            val playerData = (existingPlayer ?: PlayerData(
                uuid = player.uniqueId,
                username = player.name,
                firstSeen = now,
                lastSeen = now,
                infractions = emptyMap(),
                blockedPlayers = emptySet(),
                joinTimestamp = now,
                onlineServerId = serverInstanceId
            )).copy(
                username = player.name,
                lastSeen = now,
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
            dbPlayerQueries.insertOrUpdatePlayer(player.uniqueId, player.name, now, serverInstanceId)
            
            val playerData = PlayerData(
                uuid = player.uniqueId,
                username = player.name,
                firstSeen = now,
                lastSeen = now,
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
            database.execute(
                sql("UPDATE players SET chat_disabled = ?, messages_disabled = ?"),
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
            return@withContext database.querySingle(
                sql("SELECT chat_disabled, messages_disabled FROM players WHERE uuid = ?"),
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
            database.execute(
                sql("UPDATE players SET chat_disabled = ? WHERE uuid = ?"),
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
            database.execute(
                sql("UPDATE players SET messages_disabled = ? WHERE uuid = ?"),
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

        dbPlayerQueries.getUsername(uuid)
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
                
                // Check for server switching scenario before updating last_seen
                val currentLastSeen = dbPlayerQueries.getCurrentLastSeenFromDatabase(player.uniqueId)
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
                    // Likely server switch - update last_seen but keep presence data
                    database.execute(
                        sql("UPDATE players SET last_seen = ? WHERE uuid = ?"),
                        now, player.uniqueId
                    )
                    logger.debug("Skipping presence update for ${player.name} - likely server switch detected (timeSinceJoin: ${timeSinceJoin}s, timeSinceLastSeen: ${timeSinceLastSeen}s)")
                } else {
                    // Normal quit - update last_seen and clear presence data
                    database.execute(
                        sql("UPDATE players SET last_seen = ?, online_server_id = NULL, online_last_heartbeat = NULL WHERE uuid = ?"),
                        now, player.uniqueId
                    )
                }
                
                // Remove from online cache AFTER persisting and database update
                onlinePlayers.remove(player.uniqueId)
                
                logger.debug("Saved player data for ${player.name}: ${playerData.infractions.size} infractions, ${playerData.blockedPlayers.size} blocked players")
            } else {
                // Player data not in cache, still update database to ensure presence data is cleared
                database.execute(
                    sql("UPDATE players SET last_seen = ?, online_server_id = NULL, online_last_heartbeat = NULL WHERE uuid = ?"),
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
            val sqlString = "UPDATE players SET online_last_heartbeat = CURRENT_TIMESTAMP WHERE online_server_id = ? AND uuid IN ($placeholders)"
            
            val params = mutableListOf<Any>(serverInstanceId)
            params.addAll(onlineUuids)
            
            database.execute(sql(sqlString), *params.toTypedArray())
            logger.debug("Updated heartbeats for ${onlineUuids.size} online players on server $serverInstanceId")
        } catch (e: Exception) {
            logger.error("Failed to update heartbeats for server $serverInstanceId", e)
        }
    }
    
    // NEW: Get cross-server presence
    suspend fun getCrossServerPresence(uuid: UUID, heartbeatTimeoutSeconds: Int): String? = withContext(Dispatchers.IO) {
        // Check local cache first
        val cached = onlinePlayers[uuid]
        if (cached != null) {
            logger.warn("Found local cross-server presence for $uuid: ${cached.onlineServerId}. This should not happen.")
            return@withContext cached.onlineServerId
        }
        
        val cutoff = Instant.now().minusSeconds(heartbeatTimeoutSeconds.toLong())
        
        // Query database
        try {
            return@withContext database.querySingle(
                sql("SELECT online_server_id, online_last_heartbeat FROM players WHERE uuid = ?"),
                uuid
            ) { rs ->
                val serverId = rs.getString("online_server_id")
                val lastHeartbeat = rs.getInstant("online_last_heartbeat")
                
                if (serverId != null && lastHeartbeat != null && lastHeartbeat.isAfter(cutoff)) {
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
            val matches = database.query(
                sql("SELECT uuid, username, last_seen FROM players WHERE LOWER(username) = LOWER(?) ORDER BY last_seen DESC LIMIT 6"),
                username
            ) { rs ->
                val lastSeen = rs.getInstant("last_seen") ?: Instant.EPOCH
                Triple(UUID.fromString(rs.getString("uuid")), rs.getString("username"), lastSeen)
            }

            if (matches.isEmpty()) return@withContext null

            if (matches.size > 1) {
                val preview = matches.joinToString(", ") { (uuid, name, lastSeen) -> "$uuid(name=$name,lastSeen=$lastSeen)" }
                logger.debug(
                    "Multiple UUIDs found for username {} (showing up to {} matches, newest first): {}",
                    username,
                    matches.size,
                    preview
                )
            }

            return@withContext matches.first().first
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
            database.transaction {
                // Persist infractions
                dbPlayerQueries.persistInfractions(this, playerData.uuid, playerData.infractions)
                // Persist blocked players
                dbPlayerQueries.persistBlockedPlayers(this, playerData.uuid, playerData.blockedPlayers, playerData.username)
            }
            playerData.isDirty = false
        } catch (e: Exception) {
            logger.error("Failed to persist dirty data for player ${playerData.uuid}", e)
            throw e
        }
    }
}

