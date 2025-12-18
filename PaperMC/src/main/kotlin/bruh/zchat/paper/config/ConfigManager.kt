package bruh.zchat.paper.config

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
    private val messagesFile = dataFolder.resolve("messages.conf")
    private val storageFile = dataFolder.resolve("storage.conf")

    private fun createLoader(path: Path) = HoconConfigurationLoader.builder()
        .path(path)
        .emitComments(true)
        .prettyPrinting(true)
        .build()

    private val configLoader = createLoader(configFile)
    private val messagesLoader = createLoader(messagesFile)
    private val storageLoader = createLoader(storageFile)

    lateinit var config: Config
        private set
    lateinit var messages: MessagesConfig
        private set
    lateinit var storage: StorageConfig
        private set

    fun loadConfig(): Boolean {
        return try {
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder)
            }

            // Check for migration
            if (Files.exists(configFile) && !Files.exists(messagesFile)) {
                handleMigration()
            } else {
                loadAll()
            }
            
            logger.info("All configurations loaded successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to load configurations", e)
            loadDefaults()
            false
        }
    }

    private fun handleMigration() {
        logger.info("New configuration files missing. Attempting migration from legacy config.conf...")
        try {
            val legacyLoader = HoconConfigurationLoader.builder().path(configFile).build()
            val legacyNode = legacyLoader.load()
            val legacyConfig = legacyNode.get(LegacyConfig::class.java) ?: LegacyConfig()

            val migrator = ConfigMigrator()
            val (newConfig, newMessages, newStorage) = migrator.migrate(legacyConfig)

            this.config = newConfig
            this.messages = newMessages
            this.storage = newStorage

            // Save the new split configs
            saveAll()
            logger.info("Migration completed successfully. Config split into config.conf, messages.conf, and storage.conf")
        } catch (e: Exception) {
            logger.error("Migration failed! Loading defaults.", e)
            loadDefaults()
        }
    }

    private fun loadAll() {
        config = loadFile(configFile, configLoader, Config::class.java)
        messages = loadFile(messagesFile, messagesLoader, MessagesConfig::class.java)
        storage = loadFile(storageFile, storageLoader, StorageConfig::class.java)
    }

    private fun <T : Any> loadFile(path: Path, loader: HoconConfigurationLoader, clazz: Class<T>): T {
        return if (Files.exists(path)) {
            val node = loader.load()
            node.get(clazz) ?: clazz.getDeclaredConstructor().newInstance()
        } else {
            val instance = clazz.getDeclaredConstructor().newInstance()
            val node = loader.createNode()
            node.set(clazz, instance)
            loader.save(node)
            instance
        }
    }

    private fun loadDefaults() {
        config = Config()
        messages = MessagesConfig()
        storage = StorageConfig()
    }

    fun saveConfig(): Boolean {
        return saveAll()
    }

    private fun saveAll(): Boolean {
        return try {
            saveFile(configLoader, Config::class.java, config)
            saveFile(messagesLoader, MessagesConfig::class.java, messages)
            saveFile(storageLoader, StorageConfig::class.java, storage)
            true
        } catch (e: Exception) {
            logger.error("Failed to save configurations", e)
            false
        }
    }

    private fun <T> saveFile(loader: HoconConfigurationLoader, clazz: Class<T>, instance: T) {
        val node = loader.createNode()
        node.set(clazz, instance)
        loader.save(node)
    }

    fun reloadConfig(): Boolean {
        return loadConfig()
    }

    fun updateConfig(newConfig: Config): Boolean {
        config = newConfig
        return saveAll()
    }

    fun updateMessages(newMessages: MessagesConfig): Boolean {
        messages = newMessages
        return saveAll()
    }

    fun updateStorage(newStorage: StorageConfig): Boolean {
        storage = newStorage
        return saveAll()
    }
}
