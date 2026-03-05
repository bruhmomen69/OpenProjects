package bruh.auctionhouse.database

import bruh.auctionhouse.model.Notification
import bruh.auctionhouse.model.NotificationType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Repository for notification CRUD operations and queries.
 */
class NotificationRepository(private val database: Database) {

    /**
     * Creates a new notification.
     */
    suspend fun create(notification: Notification) = withContext(Dispatchers.IO) {
        database.execute(
            sql {
                mysql("INSERT INTO notifications (player_uuid, type, title, message, related_auction_id, related_order_id, created_at, is_read, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO notifications (player_uuid, type, title, message, related_auction_id, related_order_id, created_at, is_read, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            notification.playerUuid.toString(),
            notification.type.name,
            notification.title,
            notification.message,
            notification.relatedAuctionId?.toString(),
            notification.relatedOrderId?.toString(),
            notification.createdAt,
            notification.isRead,
            notification.expiresAt
        )
    }

    /**
     * Gets unread notifications for a player.
     */
    suspend fun getUnread(playerUuid: UUID, limit: Int = 50): List<Notification> = withContext(Dispatchers.IO) {
        database.query(
            sql("SELECT * FROM notifications WHERE player_uuid = ? AND is_read = FALSE AND (expires_at IS NULL OR expires_at > ?) ORDER BY created_at DESC LIMIT ?"),
            playerUuid.toString(),
            Instant.now(),
            limit
        ) { rs ->
            Notification(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                type = NotificationType.valueOf(rs.getString("type")),
                title = rs.getString("title"),
                message = rs.getString("message"),
                relatedAuctionId = rs.getString("related_auction_id")?.let { UUID.fromString(it) },
                relatedOrderId = rs.getString("related_order_id")?.let { UUID.fromString(it) },
                createdAt = rs.getTimestamp("created_at").toInstant(),
                isRead = rs.getBoolean("is_read"),
                expiresAt = rs.getTimestamp("expires_at")?.toInstant()
            )
        }
    }

    /**
     * Gets all notifications for a player with pagination.
     */
    suspend fun getPlayerNotifications(
        playerUuid: UUID,
        page: Int,
        pageSize: Int,
        type: NotificationType? = null
    ): List<Notification> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        
        var sqlQuery = "SELECT * FROM notifications WHERE player_uuid = ? AND (expires_at IS NULL OR expires_at > ?)"
        val params = mutableListOf<Any>(playerUuid.toString(), Instant.now())
        
        type?.let {
            sqlQuery += " AND type = ?"
            params.add(it.name)
        }
        
        sqlQuery += " ORDER BY created_at DESC LIMIT ? OFFSET ?"
        params.add(pageSize)
        params.add(offset)

        database.query(sql(sqlQuery), *params.toTypedArray()) { rs ->
            Notification(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                type = NotificationType.valueOf(rs.getString("type")),
                title = rs.getString("title"),
                message = rs.getString("message"),
                relatedAuctionId = rs.getString("related_auction_id")?.let { UUID.fromString(it) },
                relatedOrderId = rs.getString("related_order_id")?.let { UUID.fromString(it) },
                createdAt = rs.getTimestamp("created_at").toInstant(),
                isRead = rs.getBoolean("is_read"),
                expiresAt = rs.getTimestamp("expires_at")?.toInstant()
            )
        }
    }

    /**
     * Marks a notification as read.
     */
    suspend fun markAsRead(notificationId: Long) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE notifications SET is_read = TRUE WHERE id = ?"),
            notificationId
        )
    }

    /**
     * Marks all notifications as read for a player.
     */
    suspend fun markAllAsRead(playerUuid: UUID) = withContext(Dispatchers.IO) {
        database.execute(
            sql("UPDATE notifications SET is_read = TRUE WHERE player_uuid = ? AND is_read = FALSE"),
            playerUuid.toString()
        )
    }

    /**
     * Deletes old/expired notifications.
     */
    suspend fun deleteExpired() = withContext(Dispatchers.IO) {
        database.execute(
            sql("DELETE FROM notifications WHERE expires_at IS NOT NULL AND expires_at < ?"),
            Instant.now()
        )
    }

    /**
     * Deletes notifications older than specified days.
     */
    suspend fun deleteOlderThan(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(days.toLong()))
        database.execute(
            sql("DELETE FROM notifications WHERE created_at < ?"),
            cutoff
        )
    }

    /**
     * Counts unread notifications for a player.
     */
    suspend fun countUnread(playerUuid: UUID): Int = withContext(Dispatchers.IO) {
        database.querySingle(
            sql("SELECT COUNT(*) as count FROM notifications WHERE player_uuid = ? AND is_read = FALSE AND (expires_at IS NULL OR expires_at > ?)"),
            playerUuid.toString(),
            Instant.now()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }
}
