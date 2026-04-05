package bruh.regionrestore.template

import com.github.luben.zstd.Zstd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.slf4j.Logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import bruh.regionrestore.nms.RegionTemplateVersion
import io.netty.buffer.Unpooled
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class TemplateRepository(
    private val dataFolder: Path,
    private val logger: Logger,
    private val nmsAdapter: PaperNmsAdapter
) {
    private val templatesFolder = dataFolder.resolve("templates")
    private val templatesIndexPath: Path = dataFolder.resolve("templates.conf")

    private val indexLoader: ConfigurationLoader<CommentedConfigurationNode> =
        HoconConfigurationLoader.builder()
            .path(templatesIndexPath)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

    @Volatile
    private var cache: TemplateCache? = null

    /**
    * In-memory cache of active version IDs (templateName -> activeVersionId)
    * This avoids repeated index.yml reads and prevents circular dependency with TemplateCache
    */
    private val activeVersionIds = ConcurrentHashMap<String, Int>()

    /**
     * Mutex to synchronize access to index file and related state
     */
    private val indexMutex = Mutex()

    /**
     * Set the cache for this repository. Called during plugin initialization.
     */
    fun setCache(cache: TemplateCache) {
        this.cache = cache
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val cbor = Cbor {
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class ChunkCoord(
        val x: Int,
        val z: Int
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class TemplateMetadata(
        val templateName: String,
        val versionId: Int,
        val createdAt: Long,
        val minecraftVersion: String,
        val templateName1: String,
        val description: String,
        val sourceWorld: String,
        val minChunkX: Int,
        val minChunkZ: Int,
        val sizeXChunks: Int,
        val sizeZChunks: Int,
        val chunkCoords: List<ChunkCoord> = emptyList(),
        val encodedVersion: Int = 1
    )

    @ConfigSerializable
    data class TemplateVersionInfo(
        val versionId: Int = 0,
        val createdAt: Long = 0,
        val minecraftVersion: String = "",
        val description: String = ""
    )

    @ConfigSerializable
    data class TemplateIndex(
        val templateName: String = "",
        val versions: List<TemplateVersionInfo> = emptyList(),
        val activeVersionId: Int = 0
    )

    @ConfigSerializable
    data class TemplatesIndexConfig(
        val templates: MutableMap<String, TemplateIndex> = mutableMapOf()
    )

    init {
        templatesFolder.createDirectories()
    }

    suspend fun load() {
        val indexConfig = loadTemplatesIndex()

        logger.info("Discovered ${indexConfig.templates.size} template(s):")
        indexConfig.templates.forEach { (name, index) ->
            logger.info("  - $name: ${index.versions.size} version(s), active=v${index.activeVersionId}")

            // Populate the active version ID cache
            activeVersionIds[name] = index.activeVersionId
        }
    }

    suspend fun saveTemplate(
        name: String,
        description: String,
        template: RegionTemplate,
        minecraftVersion: String
    ): RegionTemplateVersion = withContext(Dispatchers.IO) {
        val templateWithMetadata = template.copy(
            name = name,
            description = description
        )

        val templateFolder = templatesFolder.resolve(name)
        templateFolder.createDirectories()

        // Atomic operation: load index, modify, save, update cache
        val (newVersionId, now) = indexMutex.withLock {
            val indexConfig = loadTemplatesIndexUnlocked()
            val index = indexConfig.templates[name] ?: TemplateIndex(templateName = name)

            val versionId = (index.versions.maxOfOrNull { it.versionId } ?: 0) + 1
            val timestamp = System.currentTimeMillis()

            val versionInfo = TemplateVersionInfo(
                versionId = versionId,
                createdAt = timestamp,
                minecraftVersion = minecraftVersion,
                description = description
            )

            val newIndex = TemplateIndex(
                templateName = name,
                versions = index.versions + versionInfo,
                activeVersionId = versionId
            )

            indexConfig.templates[name] = newIndex
            saveTemplatesIndexUnlocked(indexConfig)

            // Update the in-memory cache
            activeVersionIds[name] = newIndex.activeVersionId

            Pair(versionId, timestamp)
        }

        val templateVersion = RegionTemplateVersion(
            templateName = name,
            versionId = newVersionId,
            createdAt = Instant.ofEpochMilli(now),
            minecraftVersion = minecraftVersion,
            data = templateWithMetadata
        )

        val versionFile = templateFolder.resolve("${name}_v$newVersionId.crd")
        saveTemplateData(versionFile, templateVersion)

        logger.info("Saved template $name version $newVersionId")

        templateVersion
    }

    suspend fun loadTemplateVersion(
        name: String,
        versionId: Int
    ): RegionTemplateVersion? {
        cache?.let { c ->
            c.get(name, versionId)?.let { return it }
        }

        return loadTemplateVersionFromDisk(name, versionId)
    }

    suspend fun loadTemplateVersionFromDisk(
        name: String,
        versionId: Int
    ): RegionTemplateVersion? = withContext(Dispatchers.IO) {
        val templateFolder = templatesFolder.resolve(name)

        val newNameFile = templateFolder.resolve("${name}_v$versionId.crd")
        val legacyNameFile = templateFolder.resolve("v$versionId.crd")

        val versionFile = when {
            newNameFile.exists() -> newNameFile
            legacyNameFile.exists() -> legacyNameFile
            else -> {
                logger.warn("Version file does not exist for $name v$versionId??")
                return@withContext null
            }
        }

        try {
            loadTemplateData(versionFile, name, versionId)
        } catch (e: Exception) {
            logger.error("Failed to load template $name v$versionId", e)
            null
        }
    }

    suspend fun loadActiveTemplateVersion(name: String): RegionTemplateVersion? {
        var activeVersionId = activeVersionIds[name]

        if (activeVersionId == null || activeVersionId == 0) {
            val versions = getTemplateVersions(name)

            if (versions == null) {
                logger.warn("Template $name not found in the index??")
                return null
            }

            activeVersionId = versions?.maxOfOrNull { it.versionId } ?: run {
                logger.warn("Template $name has no versions??")
                return null
            }
        }

        // Use loadTemplateVersion() which will use the TemplateCache if available
        return loadTemplateVersion(name, activeVersionId)
    }

    suspend fun setActiveVersion(name: String, versionId: Int): Boolean = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            val indexConfig = loadTemplatesIndexUnlocked()
            val index = indexConfig.templates[name] ?: return@withLock false

            if (index.versions.none { it.versionId == versionId }) {
                return@withLock false
            }

            val newIndex = index.copy(activeVersionId = versionId)
            indexConfig.templates[name] = newIndex
            saveTemplatesIndexUnlocked(indexConfig)

            activeVersionIds[name] = versionId

            logger.info("Set active version of template $name to $versionId")

            true
        }
    }

    suspend fun deleteTemplate(name: String): Boolean = withContext(Dispatchers.IO) {
        val templateFolder = templatesFolder.resolve(name)

        if (!templateFolder.exists()) {
            return@withContext false
        }

        val versions = indexMutex.withLock {
            val indexConfig = loadTemplatesIndexUnlocked()
            val index = indexConfig.templates[name]
            index?.versions
        }

        val cacheRef = cache
        if (versions != null && cacheRef != null) {
            for (version in versions) {
                cacheRef.remove(name, version.versionId)
            }
        }

        templateFolder.toFile().deleteRecursively()

        indexMutex.withLock {
            val indexConfig = loadTemplatesIndexUnlocked()
            indexConfig.templates.remove(name)
            saveTemplatesIndexUnlocked(indexConfig)

            activeVersionIds.remove(name)
        }

        logger.info("Deleted template $name")

        true
    }

    suspend fun listTemplates(): List<String> = withContext(Dispatchers.IO) {
        val indexConfig = loadTemplatesIndex()
        indexConfig.templates.keys.toList()
    }

    suspend fun getTemplateVersions(name: String): List<TemplateVersionInfo>? = withContext(Dispatchers.IO) {
        val indexConfig = loadTemplatesIndex()
        val index = indexConfig.templates[name] ?: run {
            logger.warn("Template $name not found in index??")
            return@withContext null
        }

        index.versions
    }

    private suspend fun loadTemplatesIndex(): TemplatesIndexConfig = indexMutex.withLock {
        loadTemplatesIndexUnlocked()
    }

    private fun loadTemplatesIndexUnlocked(): TemplatesIndexConfig {
        return try {
            val rootNode = indexLoader.load()
            val existing = rootNode.get<TemplatesIndexConfig>()

            if (existing != null && existing.templates.isNotEmpty()) {
                existing
            } else {
                // Attempt one-time migration from legacy per-template index.yml files
                val migrated = tryMigrateLegacyIndex()
                if (migrated != null) {
                    saveTemplatesIndexUnlocked(migrated)
                    migrated
                } else {
                    existing ?: TemplatesIndexConfig()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load templates index: ${e.message}", e)
            TemplatesIndexConfig()
        }
    }

    private suspend fun saveTemplatesIndex(index: TemplatesIndexConfig) = indexMutex.withLock {
        saveTemplatesIndexUnlocked(index)
    }

    private fun saveTemplatesIndexUnlocked(index: TemplatesIndexConfig) {
        try {
            val rootNode = indexLoader.load()
            rootNode.set(index)
            indexLoader.save(rootNode)
        } catch (e: Exception) {
            logger.error("Failed to save templates index: ${e.message}", e)
        }
    }

    private fun tryMigrateLegacyIndex(): TemplatesIndexConfig? {
        if (!templatesFolder.exists()) {
            return null
        }

        val templateDirs = templatesFolder.listDirectoryEntries()
            .filter { it.isDirectory() }

        if (templateDirs.isEmpty()) {
            return null
        }

        val templates = mutableMapOf<String, TemplateIndex>()

        for (templateFolder in templateDirs) {
            val name = templateFolder.fileName.toString()
            val indexFile = templateFolder.resolve("index.yml")

            if (!indexFile.exists()) {
                continue
            }

            try {
                val legacyIndex = loadLegacyIndex(indexFile, name)
                templates[name] = legacyIndex
            } catch (e: Exception) {
                logger.error("Failed to migrate legacy index for template $name: ${e.message}", e)
            }
        }

        if (templates.isEmpty()) {
            return null
        }

        logger.info("Migrated ${templates.size} legacy template index file(s) into templates.conf")
        return TemplatesIndexConfig(templates)
    }

    private fun loadLegacyIndex(indexFile: Path, templateName: String): TemplateIndex {
        val content = indexFile.readText()
        val lines = content.lines()

        val versionsList = mutableListOf<TemplateVersionInfo>()
        var activeVersionId = 0

        var currentVersionId = -1
        var currentCreatedAt = 0L
        var currentMinecraftVersion = ""
        var currentDescription = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("activeVersionId:") -> {
                    activeVersionId = trimmed.substringAfter(":RegionRestore:").trim().toIntOrNull() ?: 0
                }

                trimmed.startsWith("- versionId:") -> {
                    if (currentVersionId != -1) {
                        versionsList.add(
                            TemplateVersionInfo(
                                versionId = currentVersionId,
                                createdAt = currentCreatedAt,
                                minecraftVersion = currentMinecraftVersion,
                                description = currentDescription
                            )
                        )
                    }
                    currentVersionId = trimmed.substringAfter(":RegionRestore:").trim().toIntOrNull() ?: -1
                    currentCreatedAt = 0L
                    currentMinecraftVersion = ""
                    currentDescription = ""
                }

                trimmed.startsWith("createdAt:") -> {
                    currentCreatedAt = trimmed.substringAfter(":RegionRestore:").trim().toLongOrNull() ?: 0L
                }

                trimmed.startsWith("minecraftVersion:") -> {
                    currentMinecraftVersion = trimmed.substringAfter(":RegionRestore:").trim().removeSurrounding("\"")
                }

                trimmed.startsWith("description:") -> {
                    currentDescription = trimmed.substringAfter(":RegionRestore:").trim().removeSurrounding("\"")
                }
            }
        }

        if (currentVersionId != -1) {
            versionsList.add(
                TemplateVersionInfo(
                    versionId = currentVersionId,
                    createdAt = currentCreatedAt,
                    minecraftVersion = currentMinecraftVersion,
                    description = currentDescription
                )
            )
        }

        return TemplateIndex(
            templateName = templateName,
            versions = versionsList,
            activeVersionId = activeVersionId
        )
    }

    private suspend fun saveTemplateData(versionFile: Path, version: RegionTemplateVersion) =
        withContext(Dispatchers.IO) {
            val metadata = TemplateMetadata(
                templateName = version.templateName,
                versionId = version.versionId,
                createdAt = version.createdAt.toEpochMilli(),
                minecraftVersion = version.minecraftVersion,
                templateName1 = version.data.name,
                description = version.data.description,
                sourceWorld = version.data.sourceWorld.toString(),
                minChunkX = version.data.minChunkX,
                minChunkZ = version.data.minChunkZ,
                sizeXChunks = version.data.sizeXChunks,
                sizeZChunks = version.data.sizeZChunks,
                chunkCoords = version.data.chunkData.keys.map { (x, z) -> ChunkCoord(x, z) }
            )

            val metadataBytes = cbor.encodeToByteArray(metadata)

            val chunkDataBuffer = nmsAdapter.serializeChunkDataToByteBuf(version.data.chunkData)
            val chunkDataBytes = ByteArray(chunkDataBuffer.readableBytes())
            chunkDataBuffer.readBytes(chunkDataBytes)
            chunkDataBuffer.release()

            val combined = byteArrayOf(
                (metadataBytes.size shr 24).toByte(),
                (metadataBytes.size shr 16).toByte(),
                (metadataBytes.size shr 8).toByte(),
                metadataBytes.size.toByte(),
                *metadataBytes,
                *chunkDataBytes
            )

            val compressed = Zstd.compress(combined, 9)

            versionFile.writeBytes(compressed)
        }

    private suspend fun loadTemplateData(versionFile: Path, name: String, versionId: Int): RegionTemplateVersion =
        withContext(Dispatchers.IO) {
            val compressed = versionFile.readBytes()
            val originalSize =
                Zstd.getFrameContentSize(compressed).let { if (it <= 0) compressed.size.toLong() * 5 else it }
            val decompressed = Zstd.decompress(compressed, originalSize.toInt())

            val metadataLength = ((decompressed[0].toInt() and 0xFF) shl 24) or
                    ((decompressed[1].toInt() and 0xFF) shl 16) or
                    ((decompressed[2].toInt() and 0xFF) shl 8) or
                    (decompressed[3].toInt() and 0xFF)
            val metadataBytes = decompressed.copyOfRange(4, 4 + metadataLength)
            val chunkDataBytes = decompressed.copyOfRange(4 + metadataLength, decompressed.size)

            val metadata = cbor.decodeFromByteArray<TemplateMetadata>(metadataBytes)

            val chunkDataBuffer = Unpooled.wrappedBuffer(chunkDataBytes)

            val offheap = MemoryChecker.hasSufficientOffheapMemory(originalSize)
            if (!offheap) {
                logger.warn("Loading template $name v$versionId using heap memory. This can cause reduced server performance and increased GC pressure. Increase the memory overhead (or contact support) to improve performance.")
            }

            val chunkData = nmsAdapter.deserializeChunkDataFromByteBuf(chunkDataBuffer, offheap)

            val template = RegionTemplate(
                name = metadata.templateName1,
                description = metadata.description,
                createdAt = Instant.ofEpochMilli(metadata.createdAt),
                sourceWorld = UUID.fromString(metadata.sourceWorld),
                minChunkX = metadata.minChunkX,
                minChunkZ = metadata.minChunkZ,
                sizeXChunks = metadata.sizeXChunks,
                sizeZChunks = metadata.sizeZChunks,
                chunkData = chunkData
            )

            RegionTemplateVersion(
                templateName = metadata.templateName,
                versionId = metadata.versionId,
                createdAt = Instant.ofEpochMilli(metadata.createdAt),
                minecraftVersion = metadata.minecraftVersion,
                data = template
            )
        }
}
