package bruh.auctionhouse.database

import bruh.auctionhouse.model.PlayerBan
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Repository for player ban CRUD operations and queries.
 * Manages persistent storage of auction house bans.
 */
class PlayerBanRepository(private val database: Database) {

    /**
     * Adds a ban for a player.
     *
     * @param ban The ban to add
     * @return true if the ban was added, false if a ban already exists
     */
    suspend fun addBan(ban: PlayerBan): Boolean = withContext(Dispatchers.IO) {
        // Check if already banned
        val existing = getByPlayerUuid(ban.playerUuid)
        if (existing != null) return@withContext false

        database.execute(
            sql {
                mysql("""
                    INSERT INTO player_bans (
                        player_uuid, player_name, ban_reason,
                        banned_at, banned_by, banned_by_name
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """)
                postgres("""
                    INSERT INTO player_bans (
                        player_uuid, player_name, ban_reason,
                        banned_at, banned_by, banned_by_name
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """)
                sqlite("""
                    INSERT INTO player_bans (
                        player_uuid, player_name, ban_reason,
                        banned_at, banned_by, banned_by_name
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """)
            },
            ban.playerUuid.toString(),
            ban.playerName,
            ban.banReason,
            ban.bannedAt,
            ban.bannedBy?.toString(),
            ban.bannedByName
        ) > 0
    }

    /**
     * Removes a ban for a player.
     *
     * @param playerUuid The UUID of the player to unban
     * @return true if the ban was removed, false if the player was not banned
     */
    suspend fun removeBan(playerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM player_bans WHERE player_uuid = ?"),
            playerUuid.toString()
        ) > 0
    }

    /**
     * Gets a ban by player UUID.
     *
     * @param playerUuid The UUID of the player
     * @return The ban record, or null if the player is not banned
     */
    suspend fun getByPlayerUuid(playerUuid: UUID): PlayerBan? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM player_bans WHERE player_uuid = ?"),
            playerUuid.toString()
        ) { rs ->
            PlayerBan(
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                playerName = rs.getString("player_name"),
                banReason = rs.getString("ban_reason"),
                bannedAt = rs.getTimestamp("banned_at").toInstant(),
                bannedBy = rs.getString("banned_by")?.let { UUID.fromString(it) },
                bannedByName = rs.getString("banned_by_name")
            )
        }
    }

    /**
     * Gets a ban by player name.
     *
     * @param playerName The name of the player
     * @return The ban record, or null if the player is not banned
     */
    suspend fun getByPlayerName(playerName: String): PlayerBan? = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT * FROM player_bans WHERE player_name = ?"),
            playerName
        ) { rs ->
            PlayerBan(
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                playerName = rs.getString("player_name"),
                banReason = rs.getString("ban_reason"),
                bannedAt = rs.getTimestamp("banned_at").toInstant(),
                bannedBy = rs.getString("banned_by")?.let { UUID.fromString(it) },
                bannedByName = rs.getString("banned_by_name")
            )
        }
    }

    /**
     * Gets all bans.
     *
     * @return List of all active bans
     */
    suspend fun getAllBans(): List<PlayerBan> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM player_bans ORDER BY banned_at DESC")
        ) { rs ->
            PlayerBan(
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                playerName = rs.getString("player_name"),
                banReason = rs.getString("ban_reason"),
                bannedAt = rs.getTimestamp("banned_at").toInstant(),
                bannedBy = rs.getString("banned_by")?.let { UUID.fromString(it) },
                bannedByName = rs.getString("banned_by_name")
            )
        }
    }

    /**
     * Checks if a player is banned.
     *
     * @param playerUuid The UUID of the player
     * @return true if the player is banned, false otherwise
     */
    suspend fun isBanned(playerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM player_bans WHERE player_uuid = ?"),
            playerUuid.toString()
        ) { rs ->
            rs.getInt("count") > 0
        } ?: false
    }

    /**
     * Counts the total number of bans.
     *
     * @return The number of active bans
     */
    suspend fun countBans(): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM player_bans")
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }

    /**
     * Updates the ban reason for a player.
     *
     * @param playerUuid The UUID of the player
     * @param newReason The new ban reason
     * @return true if the ban was updated, false if the player was not banned
     */
    suspend fun updateBanReason(playerUuid: UUID, newReason: String): Boolean = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE player_bans SET ban_reason = ? WHERE player_uuid = ?"),
            newReason,
            playerUuid.toString()
        ) > 0
    }
}
