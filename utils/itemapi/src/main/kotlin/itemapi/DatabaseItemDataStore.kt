package bruh.zchat.utils.itemapi

import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.getInstant
import bruh.zchat.utils.database.getUUID
import bruh.zchat.utils.database.sql
import java.time.Instant
import java.util.UUID

/**
 * Database-backed implementation of ItemDataStore.
 * Uses the Database API for persistence of TrackedItemInstance data.
 *
 * @param database The Database instance to use for queries
 */
class DatabaseItemDataStore(
    private val database: Database
) : ItemDataStore {

    override suspend fun load(instanceId: UUID): TrackedItemInstance? {
        // Load the base item data
        val baseData = database.querySingle(
            sql("SELECT instance_id, item_id, owner_uuid, created_at, last_interacted_at FROM tracked_items WHERE instance_id = ?"),
            instanceId.toString()
        ) { rs ->
            Triple(
                rs.getString("item_id"),
                rs.getUUID("owner_uuid"),
                Pair(
                    rs.getInstant("created_at") ?: Instant.now(),
                    rs.getInstant("last_interacted_at") ?: Instant.now()
                )
            )
        } ?: return null

        // Load metadata
        val metadata = database.query(
            sql("SELECT meta_key, meta_value FROM tracked_item_metadata WHERE instance_id = ?"),
            instanceId.toString()
        ) { rs ->
            rs.getString("meta_key") to rs.getString("meta_value")
        }.toMap().toMutableMap()

        return TrackedItemInstance(
            instanceId = instanceId,
            itemId = baseData.first,
            ownerUuid = baseData.second,
            createdAt = baseData.third.first,
            lastInteractedAt = baseData.third.second,
            metadata = metadata,
            isDirty = false
        )
    }

    override suspend fun save(instance: TrackedItemInstance) {
        database.transaction {
            // Upsert the base item data
            execute(
                sql {
                    mysql("""
                        INSERT INTO tracked_items (instance_id, item_id, owner_uuid, created_at, last_interacted_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            item_id = VALUES(item_id),
                            owner_uuid = VALUES(owner_uuid),
                            last_interacted_at = VALUES(last_interacted_at)
                    """)
                    sqlite("""
                        INSERT OR REPLACE INTO tracked_items (instance_id, item_id, owner_uuid, created_at, last_interacted_at)
                        VALUES (?, ?, ?, ?, ?)
                    """)
                    postgres("""
                        INSERT INTO tracked_items (instance_id, item_id, owner_uuid, created_at, last_interacted_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (instance_id) DO UPDATE SET
                            item_id = EXCLUDED.item_id,
                            owner_uuid = EXCLUDED.owner_uuid,
                            last_interacted_at = EXCLUDED.last_interacted_at
                    """)
                },
                instance.instanceId.toString(),
                instance.itemId,
                instance.ownerUuid?.toString(),
                instance.createdAt,
                instance.lastInteractedAt
            )

            // Delete existing metadata and re-insert
            execute(
                sql("DELETE FROM tracked_item_metadata WHERE instance_id = ?"),
                instance.instanceId.toString()
            )

            // Insert new metadata
            if (instance.metadata.isNotEmpty()) {
                val paramSets = instance.metadata.map { (key, value) ->
                    arrayOf(instance.instanceId.toString(), key, value)
                }
                executeBatch(
                    sql("INSERT INTO tracked_item_metadata (instance_id, meta_key, meta_value) VALUES (?, ?, ?)"),
                    paramSets
                )
            }
        }

        instance.isDirty = false
    }

    override suspend fun delete(instanceId: UUID): Boolean {
        // Metadata is deleted via CASCADE
        val deleted = database.execute(
            sql("DELETE FROM tracked_items WHERE instance_id = ?"),
            instanceId.toString()
        )
        return deleted > 0
    }

    override suspend fun deleteByOwner(ownerUuid: UUID): Int {
        // Metadata is deleted via CASCADE
        return database.execute(
            sql("DELETE FROM tracked_items WHERE owner_uuid = ?"),
            ownerUuid.toString()
        )
    }

    override suspend fun findByOwner(ownerUuid: UUID): List<TrackedItemInstance> {
        val instanceIds = database.query(
            sql("SELECT instance_id FROM tracked_items WHERE owner_uuid = ?"),
            ownerUuid.toString()
        ) { rs ->
            UUID.fromString(rs.getString("instance_id"))
        }

        return instanceIds.mapNotNull { load(it) }
    }

    override suspend fun findByItemId(itemId: String): List<TrackedItemInstance> {
        val instanceIds = database.query(
            sql("SELECT instance_id FROM tracked_items WHERE item_id = ?"),
            itemId
        ) { rs ->
            UUID.fromString(rs.getString("instance_id"))
        }

        return instanceIds.mapNotNull { load(it) }
    }

    override suspend fun updateLastInteracted(instanceId: UUID, timestamp: Instant) {
        database.execute(
            sql("UPDATE tracked_items SET last_interacted_at = ? WHERE instance_id = ?"),
            timestamp,
            instanceId.toString()
        )
    }

    override suspend fun count(): Long {
        return database.querySingle(
            sql("SELECT COUNT(*) as cnt FROM tracked_items")
        ) { rs ->
            rs.getLong("cnt")
        } ?: 0L
    }

    override suspend fun countByItemId(itemId: String): Long {
        return database.querySingle(
            sql("SELECT COUNT(*) as cnt FROM tracked_items WHERE item_id = ?"),
            itemId
        ) { rs ->
            rs.getLong("cnt")
        } ?: 0L
    }

    override fun close() {
        // Database lifecycle is managed externally
    }
}
