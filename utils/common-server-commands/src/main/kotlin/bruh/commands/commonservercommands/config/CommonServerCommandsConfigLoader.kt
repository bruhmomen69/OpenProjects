package bruh.commands.commonservercommands.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Handles loading and saving of common server commands configuration.
 */
class CommonServerCommandsConfigLoader(
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

    /**
     * Loads the configuration from disk, creating defaults if necessary.
     */
    suspend fun load(): CommonServerCommandsConfig = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = loader.load()

        var config: CommonServerCommandsConfig? = rootNode.get()
        if (config == null) config = CommonServerCommandsConfig()

        rootNode.set(config)
        loader.save(rootNode)

        config
    }

    /**
     * Reloads the configuration from disk.
     */
    suspend fun reload(): CommonServerCommandsConfig = load()
}
