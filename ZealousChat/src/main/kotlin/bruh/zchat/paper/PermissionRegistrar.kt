package bruh.zchat.paper

import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.Plugin
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarFile

/**
 * Handles manual registration of permissions from plugin.yml
 * when the plugin is loaded via PaperLoader.
 */
class PermissionRegistrar(private val plugin: Plugin) {

    private val logger = plugin.slF4JLogger

    /**
     * Registers all permissions defined in plugin.yml
     * @return true if registration was successful, false otherwise
     */
    fun registerPermissions(): Boolean {
        return try {
            val pluginYml = loadPluginYml() ?: run {
                logger.warn("Failed to load plugin.yml from JAR file")
                return false
            }

            val permissions = parsePermissions(pluginYml)
            if (permissions.isEmpty()) {
                logger.warn("No permissions found in plugin.yml")
                return false
            }

            // Clear any previously registered permissions from this plugin
            clearExistingPermissions()

            // Register permissions in order (parents before children)
            val registeredCount = registerPermissionsInOrder(permissions)

            logger.info("Successfully registered $registeredCount permissions from plugin.yml")
            true
        } catch (e: Exception) {
            logger.warn("Failed to register permissions from plugin.yml: ${e.message}", e)
            false
        }
    }

    /**
     * Loads the plugin.yml file from the plugin's JAR
     */
    private fun loadPluginYml(): Map<String, Any>? {
        val jarFile = getPluginJarFile() ?: run {
            logger.warn("Could not locate plugin JAR file")
            return null
        }

        return try {
            JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry("plugin.yml")
                    ?: jar.getJarEntry("resources/plugin.yml")

                if (entry == null) {
                    logger.warn("plugin.yml not found in JAR file")
                    return null
                }

                jar.getInputStream(entry).use { inputStream ->
                    val yaml = Yaml()
                    @Suppress("UNCHECKED_CAST")
                    yaml.load(inputStream) as? Map<String, Any>
                }
            }
        } catch (e: Exception) {
            logger.warn("Error reading plugin.yml from JAR: ${e.message}", e)
            null
        }
    }

    /**
     * Gets the plugin's JAR file
     */
    private fun getPluginJarFile(): File? {
        return try {
            val url = plugin.javaClass.protectionDomain.codeSource.location
            File(url.toURI())
        } catch (e: Exception) {
            logger.debug("Could not get plugin JAR location: ${e.message}")
            null
        }
    }

    /**
     * Parses permissions section from plugin.yml
     */
    @Suppress("UNCHECKED_CAST")
    private fun parsePermissions(pluginYml: Map<String, Any>): Map<String, PermissionData> {
        val permissionsSection = pluginYml["permissions"] as? Map<*, *>
        val permissions = mutableMapOf<String, PermissionData>()

        if (permissionsSection == null) {
            logger.warn("No 'permissions' section found in plugin.yml")
            return permissions
        }

        for ((name, data) in permissionsSection) {
            val permName = name as? String ?: continue
            val permData = data as? Map<*, *> ?: continue

            val description = permData["description"] as? String ?: ""
            val defaultValue = permData["default"]
            val defaultStr = when (defaultValue) {
                is String -> defaultValue
                is Boolean -> defaultValue.toString()
                else -> null
            }
            val default = parsePermissionDefault(defaultStr)
            val childrenMap = permData["children"] as? Map<*, *>

            val children = childrenMap?.mapKeys { it.key as String }?.mapValues { entry ->
                when (val value = entry.value) {
                    is Boolean -> value
                    "true" -> true
                    "false" -> false
                    else -> false
                }
            } ?: emptyMap()

            permissions[permName] = PermissionData(permName, description, default, children)
        }

        return permissions
    }

    /**
     * Parses permission default value from string
     */
    private fun parsePermissionDefault(default: String?): PermissionDefault {
        return when (default?.lowercase()) {
            "true" -> PermissionDefault.TRUE
            "false" -> PermissionDefault.FALSE
            "not op" -> PermissionDefault.NOT_OP
            "op" -> PermissionDefault.OP
            else -> PermissionDefault.OP
        }
    }

    /**
     * Clears existing permissions registered by this plugin
     */
    private fun clearExistingPermissions() {
        val pluginManager = Bukkit.getPluginManager()
        val permissionsToRemove = pluginManager.permissions
            .filter { it.name.startsWith("zchat.") }
            .toTypedArray()

        for (permission in permissionsToRemove) {
            try {
                pluginManager.removePermission(permission)
            } catch (e: Exception) {
                logger.debug("Could not remove permission ${permission.name}: ${e.message}")
            }
        }
    }

    /**
     * Registers permissions in the correct order (parents before children)
     */
    private fun registerPermissionsInOrder(permissions: Map<String, PermissionData>): Int {
        val pluginManager = Bukkit.getPluginManager()
        var registeredCount = 0

        // Sort permissions so that parents come before children
        val sortedPermissions = topologicalSort(permissions)

        for (permData in sortedPermissions) {
            try {
                val permission = Permission(
                    permData.name,
                    permData.description,
                    permData.default
                )

                // Add children
                for ((childName, value) in permData.children) {
                    permission.children[childName] = value
                }

                pluginManager.addPermission(permission)
                registeredCount++
                logger.debug("Registered permission: ${permData.name}")
            } catch (e: Exception) {
                logger.warn("Failed to register permission ${permData.name}: ${e.message}")
            }
        }

        return registeredCount
    }

    /**
     * Sorts permissions topologically so that parents come before children
     */
    private fun topologicalSort(permissions: Map<String, PermissionData>): List<PermissionData> {
        val sorted = mutableListOf<PermissionData>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(name: String) {
            if (name in visited) return
            if (name in visiting) {
                logger.warn("Circular dependency detected in permissions: $name")
                return
            }

            val permData = permissions[name] ?: return

            visiting.add(name)

            // Visit children first
            for (childName in permData.children.keys) {
                if (childName in permissions) {
                    visit(childName)
                }
            }

            visiting.remove(name)
            visited.add(name)
            sorted.add(permData)
        }

        for (name in permissions.keys) {
            visit(name)
        }

        return sorted
    }

    /**
     * Data class representing a permission from plugin.yml
     */
    private data class PermissionData(
        val name: String,
        val description: String,
        val default: PermissionDefault,
        val children: Map<String, Boolean>
    )
}
