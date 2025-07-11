package lol.mcplugs.minimessagechatplugin.paper.config

import org.slf4j.LoggerFactory
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.serialize.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class ConfigManager(private val dataFolder: Path) {
    private val logger = LoggerFactory.getLogger(ConfigManager::class.java)
    private val configFile = dataFolder.resolve("config.conf")
    private val loader = HoconConfigurationLoader.builder()
        .path(configFile)
        .emitComments(true)
        .prettyPrinting(true)
        .build()

    lateinit var config: Config
        private set

    fun loadConfig(): Boolean {
        return try {
            // Create data folder if it doesn't exist
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder)
            }

            val node: ConfigurationNode = if (Files.exists(configFile)) {
                loader.load()
            } else {
                // Create default config if it doesn't exist
                val defaultNode = loader.createNode()
                defaultNode.set(Config::class.java, Config())
                loader.save(defaultNode)
                logger.info("Created default configuration file")
                defaultNode
            }

            config = node.get(Config::class.java) ?: Config()
            logger.info("Configuration loaded successfully")
            true
        } catch (e: IOException) {
            logger.error("Failed to load configuration file", e)
            config = Config() // Use default config
            false
        } catch (e: SerializationException) {
            logger.error("Failed to deserialize configuration", e)
            config = Config() // Use default config
            false
        }
    }

    fun saveConfig(): Boolean {
        return try {
            val node = loader.createNode()
            node.set(Config::class.java, config)
            loader.save(node)
            logger.info("Configuration saved successfully")
            true
        } catch (e: IOException) {
            logger.error("Failed to save configuration file", e)
            false
        } catch (e: SerializationException) {
            logger.error("Failed to serialize configuration", e)
            false
        }
    }

    fun reloadConfig(): Boolean {
        return loadConfig()
    }

    fun updateConfig(newConfig: Config): Boolean {
        config = newConfig
        return saveConfig()
    }
}
