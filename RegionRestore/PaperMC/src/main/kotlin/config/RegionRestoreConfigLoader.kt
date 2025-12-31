package bruh.regionrestore.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import bruh.regionrestore.nms.PaperNmsAdapter
import java.nio.file.Files
import java.nio.file.Path

class RegionRestoreConfigLoader(
    private val dataFolder: Path,
    private val logger: Logger
) {

    private val configPath: Path = dataFolder.resolve("config.conf")

    private val loader: ConfigurationLoader<CommentedConfigurationNode> =
        HoconConfigurationLoader.builder()
            .path(configPath)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

    suspend fun load(nmsAdapter: PaperNmsAdapter? = null): RegionRestoreConfig = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = loader.load()

        var config: RegionRestoreConfig? = rootNode.get()
        if (config == null) config = RegionRestoreConfig()

        if (nmsAdapter != null && nmsAdapter.supportsAsync && config.restore.asyncRestore == null) {
            config = config.copy(
                restore = config.restore.copy(
                    asyncRestore = true
                )
            )
        }

        rootNode.set(config)
        loader.save(rootNode)

        config!!
    }
}
