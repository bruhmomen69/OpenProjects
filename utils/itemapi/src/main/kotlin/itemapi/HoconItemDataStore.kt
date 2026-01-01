package bruh.zchat.utils.itemapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * HOCON file-backed implementation of ItemDataStore.
 * Stores each TrackedItemInstance in a separate .conf file under the items directory.
 * Uses Dispatchers.IO for all file operations and per-file mutexes to prevent race conditions.
 *
 * @param itemsDirectory The directory where item files will be stored (e.g., pluginDataFolder/items)
 */
class HoconItemDataStore(
    private val itemsDirectory: Path
) : ItemDataStore {

    /**
     * Tracks a per-instance mutex with a simple reference count so that
     * we can safely remove entries from [fileMutexes] when no operations
     * are using them. This prevents unbounded growth of the map while
     * still guaranteeing that all concurrent operations on the same
     * instance share a single mutex.
     */
    private data class MutexEntry(
        val mutex: Mutex,
        @Volatile
        var refCount: Int
    )

    private val fileMutexes = ConcurrentHashMap<UUID, MutexEntry>()

    init {
        // Ensure the items directory exists
        if (Files.notExists(itemsDirectory)) {
            Files.createDirectories(itemsDirectory)
        }
    }

    private fun acquireMutexEntry(instanceId: UUID): MutexEntry {
        return fileMutexes.compute(instanceId) { _, existing ->
            val entry = existing ?: MutexEntry(Mutex(), 0)
            entry.refCount++
            entry
        }!!
    }

    private fun releaseMutexEntry(instanceId: UUID, entry: MutexEntry) {
        fileMutexes.compute(instanceId) { _, current ->
            if (current == null) {
                null
            } else {
                // Decrement the reference count for this entry. If it reaches 0,
                // remove the entry so that UUIDs do not accumulate forever.
                current.refCount--
                if (current.refCount <= 0) null else current
            }
        }
    }

    private suspend fun <T> withInstanceLock(instanceId: UUID, block: suspend () -> T): T {
        val entry = acquireMutexEntry(instanceId)
        try {
            return entry.mutex.withLock { block() }
        } finally {
            releaseMutexEntry(instanceId, entry)
        }
    }

    private fun getFilePath(instanceId: UUID): Path {
        return itemsDirectory.resolve("$instanceId.conf")
    }

    private fun createLoader(path: Path): HoconConfigurationLoader {
        return HoconConfigurationLoader.builder()
            .path(path)
            .build()
    }

    override suspend fun load(instanceId: UUID): TrackedItemInstance? = withContext(Dispatchers.IO) {
        withInstanceLock(instanceId) {
            val filePath = getFilePath(instanceId)
            if (Files.notExists(filePath)) {
                null
            } else {
                val loader = createLoader(filePath)
                val rootNode = loader.load()
                parseInstance(instanceId, rootNode)
            }
        }
    }

    private fun parseInstance(instanceId: UUID, node: CommentedConfigurationNode): TrackedItemInstance? {
        val itemId = node.node("item-id").string ?: return null
        val ownerUuidStr = node.node("owner-uuid").string
        val ownerUuid = ownerUuidStr?.let {
            try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
        }
        val createdAt = node.node("created-at").long.let { Instant.ofEpochMilli(it) }
        val lastInteractedAt = node.node("last-interacted-at").long.let { Instant.ofEpochMilli(it) }

        val metadata = mutableMapOf<String, String>()
        val metadataNode = node.node("metadata")
        if (!metadataNode.virtual()) {
            metadataNode.childrenMap().forEach { (key, valueNode) ->
                val keyStr = key.toString()
                val valueStr = valueNode.string
                if (valueStr != null) {
                    metadata[keyStr] = valueStr
                }
            }
        }

        return TrackedItemInstance(
            instanceId = instanceId,
            itemId = itemId,
            ownerUuid = ownerUuid,
            createdAt = createdAt,
            lastInteractedAt = lastInteractedAt,
            metadata = metadata,
            isDirty = false
        )
    }

    override suspend fun save(instance: TrackedItemInstance): Unit = withContext(Dispatchers.IO) {
        withInstanceLock(instance.instanceId) {
            val filePath = getFilePath(instance.instanceId)

            if (Files.notExists(filePath.parent)) {
                Files.createDirectories(filePath.parent)
            }

            val loader = createLoader(filePath)
            val rootNode = loader.createNode()

            rootNode.node("item-id").set(instance.itemId)
            rootNode.node("owner-uuid").set(instance.ownerUuid?.toString())
            rootNode.node("created-at").set(instance.createdAt.toEpochMilli())
            rootNode.node("last-interacted-at").set(instance.lastInteractedAt.toEpochMilli())

            val metadataNode = rootNode.node("metadata")
            instance.metadata.forEach { (key, value) ->
                metadataNode.node(key).set(value)
            }

            loader.save(rootNode)
        }
        instance.isDirty = false
    }

    override suspend fun delete(instanceId: UUID): Boolean = withContext(Dispatchers.IO) {
        withInstanceLock(instanceId) {
            val filePath = getFilePath(instanceId)
            if (Files.exists(filePath)) {
                Files.delete(filePath)
                true
            } else {
                false
            }
        }
    }

    override suspend fun deleteByOwner(ownerUuid: UUID): Int = withContext(Dispatchers.IO) {
        val instances = findByOwner(ownerUuid)
        var count = 0
        for (instance in instances) {
            if (delete(instance.instanceId)) {
                count++
            }
        }
        count
    }

    override suspend fun findByOwner(ownerUuid: UUID): List<TrackedItemInstance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TrackedItemInstance>()

        if (Files.notExists(itemsDirectory)) {
            return@withContext results
        }

        // Collect file paths first to avoid calling suspend functions inside forEach
        val filePaths = Files.list(itemsDirectory).use { stream ->
            stream.filter { it.toString().endsWith(".conf") }.toList()
        }

        for (filePath in filePaths) {
            val instanceId = extractInstanceId(filePath) ?: continue
            val instance = load(instanceId)
            if (instance != null && instance.ownerUuid == ownerUuid) {
                results.add(instance)
            }
        }

        results
    }

    override suspend fun findByItemId(itemId: String): List<TrackedItemInstance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TrackedItemInstance>()

        if (Files.notExists(itemsDirectory)) {
            return@withContext results
        }

        // Collect file paths first to avoid calling suspend functions inside forEach
        val filePaths = Files.list(itemsDirectory).use { stream ->
            stream.filter { it.toString().endsWith(".conf") }.toList()
        }

        for (filePath in filePaths) {
            val instanceId = extractInstanceId(filePath) ?: continue
            val instance = load(instanceId)
            if (instance != null && instance.itemId == itemId) {
                results.add(instance)
            }
        }

        results
    }

    override suspend fun updateLastInteracted(instanceId: UUID, timestamp: Instant): Unit = withContext(Dispatchers.IO) {
        withInstanceLock(instanceId) {
            val filePath = getFilePath(instanceId)
            if (Files.exists(filePath)) {
                val loader = createLoader(filePath)
                val rootNode = loader.load()

                rootNode.node("last-interacted-at").set(timestamp.toEpochMilli())

                loader.save(rootNode)
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        if (Files.notExists(itemsDirectory)) {
            return@withContext 0L
        }

        Files.list(itemsDirectory).use { stream ->
            stream.filter { it.toString().endsWith(".conf") }.count()
        }
    }

    override suspend fun countByItemId(itemId: String): Long = withContext(Dispatchers.IO) {
        findByItemId(itemId).size.toLong()
    }

    private fun extractInstanceId(filePath: Path): UUID? {
        val fileName = filePath.fileName.toString()
        if (!fileName.endsWith(".conf")) return null
        val uuidStr = fileName.removeSuffix(".conf")
        return try {
            UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun close() {
        fileMutexes.clear()
    }
}
