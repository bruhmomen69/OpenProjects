package bruh.zchat.utils.configapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Generic typed configuration loader using Configurate's object mapping.
 * Supports transforms between load/default and save/return steps.
 *
 * The transform function runs on EVERY load and reload, allowing dynamic
 * modifications to the config before it is saved and returned.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param configPath Path to the HOCON configuration file
 * @param configClass The KClass of the configuration type
 * @param defaultFactory Factory function to create default config instance
 * @param transform Optional transform applied after load, before save and return
 */
class TypedConfigLoader<T : Any>(
    private val configPath: Path,
    private val configClass: KClass<T>,
    private val defaultFactory: () -> T,
    private val transform: (T) -> T = { it }
) {
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
     * The transform is applied after loading and before saving.
     *
     * @return The loaded and transformed configuration
     */
    suspend fun load(): T = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = loader.load()

        var config: T? = rootNode.get(configClass.java)
        if (config == null) {
            config = defaultFactory()
        }

        // Apply transform on every load
        config = transform(config)

        rootNode.set(configClass.java, config)
        loader.save(rootNode)

        config
    }

    /**
     * Reloads the configuration from disk.
     * Equivalent to calling load() again.
     *
     * @return The reloaded and transformed configuration
     */
    suspend fun reload(): T = load()

    /**
     * Saves the given configuration to disk.
     * Note: The transform is NOT applied during explicit saves.
     *
     * @param config The configuration to save
     */
    suspend fun save(config: T) = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = loader.createNode()
        rootNode.set(configClass.java, config)
        loader.save(rootNode)
    }

    companion object {
        /**
         * Creates a TypedConfigLoader using reified type parameter.
         */
        inline fun <reified T : Any> create(
            configPath: Path,
            noinline defaultFactory: () -> T,
            noinline transform: (T) -> T = { it }
        ): TypedConfigLoader<T> = TypedConfigLoader(
            configPath = configPath,
            configClass = T::class,
            defaultFactory = defaultFactory,
            transform = transform
        )
    }
}
