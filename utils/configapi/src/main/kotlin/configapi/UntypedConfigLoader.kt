package bruh.zchat.utils.configapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Untyped configuration loader for raw CommentedConfigurationNode access.
 * Useful for configurations that don't map to a specific class structure.
 *
 * The transform function runs on EVERY load and reload, allowing dynamic
 * modifications to the node tree before it is saved and returned.
 *
 * @param configPath Path to the HOCON configuration file
 * @param defaultNodeFactory Optional factory to populate default values on a new node
 * @param transform Optional transform applied after load, before save and return
 */
class UntypedConfigLoader(
    private val configPath: Path,
    private val defaultNodeFactory: (CommentedConfigurationNode) -> Unit = {},
    private val transform: (CommentedConfigurationNode) -> Unit = {}
) {
    private val loader: ConfigurationLoader<CommentedConfigurationNode> =
        HoconConfigurationLoader.builder()
            .path(configPath)
            .build()

    /**
     * Loads the configuration from disk, creating defaults if necessary.
     * The transform is applied after loading and before saving.
     *
     * @return The loaded and transformed configuration node
     */
    suspend fun load(): CommentedConfigurationNode = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }

        val rootNode = if (Files.exists(configPath)) {
            loader.load()
        } else {
            loader.createNode().also { defaultNodeFactory(it) }
        }

        // Apply transform on every load
        transform(rootNode)

        loader.save(rootNode)

        rootNode
    }

    /**
     * Reloads the configuration from disk.
     * Equivalent to calling load() again.
     *
     * @return The reloaded and transformed configuration node
     */
    suspend fun reload(): CommentedConfigurationNode = load()

    /**
     * Saves the given configuration node to disk.
     * Note: The transform is NOT applied during explicit saves.
     *
     * @param node The configuration node to save
     */
    suspend fun save(node: CommentedConfigurationNode) = withContext(Dispatchers.IO) {
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.parent)
        }
        loader.save(node)
    }

    /**
     * Creates a new empty node that can be populated and saved.
     */
    fun createNode(): CommentedConfigurationNode = loader.createNode()
}
