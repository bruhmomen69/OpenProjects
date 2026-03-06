package bruh.auctionhouse.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a ban from using the auction house.
 *
 * @property playerUuid The UUID of the banned player
 * @property playerName The name of the banned player at the time of banning
 * @property banReason The reason for the ban
 * @property bannedAt When the ban was issued
 * @property bannedBy The UUID of the admin who issued the ban (optional)
 * @property bannedByName The name of the admin who issued the ban (optional)
 */
data class PlayerBan(
    val playerUuid: UUID,
    val playerName: String,
    val banReason: String,
    val bannedAt: Instant = Instant.now(),
    val bannedBy: UUID? = null,
    val bannedByName: String? = null
) {
    /**
     * Checks if this ban is still active.
     * Currently, all bans are permanent unless manually removed.
     */
    fun isActive(): Boolean = true

    /**
     * Creates a copy with the admin issuer information.
     */
    fun withIssuer(adminUuid: UUID, adminName: String): PlayerBan {
        return copy(bannedBy = adminUuid, bannedByName = adminName)
    }

    companion object {
        /**
         * Creates a new ban with the current timestamp.
         */
        fun create(
            playerUuid: UUID,
            playerName: String,
            banReason: String,
            bannedBy: UUID? = null,
            bannedByName: String? = null
        ): PlayerBan {
            return PlayerBan(
                playerUuid = playerUuid,
                playerName = playerName,
                banReason = banReason,
                bannedAt = Instant.now(),
                bannedBy = bannedBy,
                bannedByName = bannedByName
            )
        }
    }
}
