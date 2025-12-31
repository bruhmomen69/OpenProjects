package bruh.regionrestore.cloner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Path

/**
 * Handles loading and saving MassClonerService state using Configurate.
 */
class ClonerStateLoader(
    private val dataFolder: Path,
    private val logger: Logger
) {
    private val statePath: Path = dataFolder.resolve("cloner-state.conf")

    private val loader: ConfigurationLoader<CommentedConfigurationNode> =
        HoconConfigurationLoader.builder()
            .path(statePath)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

    /**
     * Load state from HOCON file.
     */
    suspend fun load(): ClonerState = withContext(Dispatchers.IO) {
        // Load HOCON format
        if (!statePath.toFile().exists()) {
            logger.info("No existing state file found, starting with empty state")
            return@withContext ClonerState.empty()
        }

        try {
            val rootNode = loader.load()
            val state = rootNode.get<ClonerState>()

            if (state == null) {
                logger.warn("State file is empty or invalid, creating new state")
                return@withContext ClonerState.empty()
            }

            logger.info("Loaded state with ${state.instances.size} world(s)")
            state.instances.forEach { (world, instances) ->
                logger.info("  - $world: ${instances.size} instance(s)")
            }

            state
        } catch (e: Exception) {
            logger.error("Failed to load state file: ${e.message}", e)
            logger.error("Starting with empty state. Backup corrupted file to: ${statePath.fileName}.corrupted")
            statePath.toFile().copyTo(
                dataFolder.resolve("cloner-state.conf.corrupted").toFile(),
                overwrite = true
            )
            ClonerState.empty()
        }
    }

    /**
     * Save state to HOCON file.
     */
    suspend fun save(state: ClonerState) = withContext(Dispatchers.IO) {
        try {
            val rootNode = loader.load()
            rootNode.set(state)
            loader.save(rootNode)

            val totalInstances = state.instances.values.sumOf { it.size }
            logger.debug("Saved state: ${state.instances.size} world(s), $totalInstances instance(s)")
        } catch (e: Exception) {
            logger.error("Failed to save state: ${e.message}", e)
        }
    }
}
