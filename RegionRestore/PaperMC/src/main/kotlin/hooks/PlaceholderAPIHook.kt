package bruh.regionrestore.hooks

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import bruh.regionrestore.cloner.InstanceType
import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.template.TemplateRepository
import kotlinx.coroutines.runBlocking

/**
 * PlaceholderAPI expansion for RegionRestore.
 * Provides placeholders for pools, instances, templates, and restore information.
 *
 * Placeholder format: %regionrestore_<placeholder>%
 *
 * Available placeholders:
 * - pool_count - Total number of configured pools
 * - pool_<world>_<template>_count - Instance count for a specific pool
 * - pool_<world>_<template>_target - Target instance count for a pool
 * - instance_count - Total number of instances
 * - instance_count_<world> - Instance count for a specific world
 * - instance_count_pooled - Count of pooled instances
 * - instance_count_manual - Count of manual instances
 * - template_count - Total number of templates
 * - template_<name>_versions - Number of versions for a template
 * - template_<name>_active_version - Active version ID for a template
 */
class PlaceholderAPIHook(
    private val plugin: JavaPlugin,
    private val massClonerService: MassClonerService,
    private val templateRepository: TemplateRepository
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "regionrestore"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return when {
            // Total instance count
            params.equals("instance_count", ignoreCase = true) -> {
                massClonerService.listInstances().size.toString()
            }

            // Instance count by world
            params.startsWith("instance_count_", ignoreCase = true) -> {
                val suffix = params.removePrefix("instance_count_")
                when {
                    suffix.equals("pooled", ignoreCase = true) -> {
                        massClonerService.listInstances(instanceType = InstanceType.POOLED).size.toString()
                    }

                    suffix.equals("manual", ignoreCase = true) -> {
                        massClonerService.listInstances(instanceType = InstanceType.MANUAL).size.toString()
                    }

                    else -> {
                        // World name
                        massClonerService.listInstances(worldName = suffix).size.toString()
                    }
                }
            }

            // Template count
            params.equals("template_count", ignoreCase = true) -> {
                runBlocking {
                    templateRepository.listTemplates().size.toString()
                }
            }

            // Template-specific placeholders
            params.startsWith("template_", ignoreCase = true) -> {
                handleTemplatePlaceholder(params.removePrefix("template_"))
            }

            // Pool-specific placeholders
            params.startsWith("pool_", ignoreCase = true) -> {
                handlePoolPlaceholder(params.removePrefix("pool_"))
            }

            // Instance details by UUID
            params.startsWith("instance_", ignoreCase = true) -> {
                handleInstancePlaceholder(params.removePrefix("instance_"))
            }

            else -> null
        }
    }

    private fun handleTemplatePlaceholder(params: String): String? {
        // Format: <name>_versions or <name>_active_version
        val parts = params.split("_")
        if (parts.size < 2) return null

        // Find the suffix (versions or active_version)
        return when {
            params.endsWith("_versions", ignoreCase = true) -> {
                val templateName = params.removeSuffix("_versions")
                runBlocking {
                    templateRepository.getTemplateVersions(templateName)?.size?.toString() ?: "0"
                }
            }

            params.endsWith("_active_version", ignoreCase = true) -> {
                val templateName = params.removeSuffix("_active_version")
                runBlocking {
                    templateRepository.loadActiveTemplateVersion(templateName)?.versionId?.toString() ?: "none"
                }
            }

            else -> null
        }
    }

    private fun handlePoolPlaceholder(params: String): String? {
        // Format: <world>_<template>_count or <world>_<template>_target
        // Also: count (total pool count)
        if (params.equals("count", ignoreCase = true)) {
            return massClonerService.getPoolCount().toString()
        }

        // Parse world_template_suffix format
        val lastUnderscoreIndex = params.lastIndexOf('_')
        if (lastUnderscoreIndex == -1) return null

        val suffix = params.substring(lastUnderscoreIndex + 1)
        val worldAndTemplate = params.substring(0, lastUnderscoreIndex)

        // Find the separator between world and template
        val separatorIndex = worldAndTemplate.indexOf('_')
        if (separatorIndex == -1) return null

        val worldName = worldAndTemplate.substring(0, separatorIndex)
        val templateName = worldAndTemplate.substring(separatorIndex + 1)

        return when (suffix.lowercase()) {
            "count" -> {
                massClonerService.getInstancesForPool(worldName, templateName).size.toString()
            }

            "target" -> {
                massClonerService.getPoolTarget(worldName, templateName)?.toString() ?: "0"
            }

            "occupied" -> {
                massClonerService.getInstancesForPool(worldName, templateName)
                    .count { it.occupancyCount > 0 }.toString()
            }

            else -> null
        }
    }

    private fun handleInstancePlaceholder(params: String): String? {
        // Skip instance_count which is handled above
        if (params.startsWith("count", ignoreCase = true)) return null

        // Format: <uuid>_<property>
        val lastUnderscoreIndex = params.lastIndexOf('_')
        if (lastUnderscoreIndex == -1) return null

        val uuidStr = params.substring(0, lastUnderscoreIndex)
        val property = params.substring(lastUnderscoreIndex + 1)

        val uuid = try {
            java.util.UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val instance = massClonerService.getInstance(uuid) ?: return null

        return when (property.lowercase()) {
            "world" -> instance.worldName
            "template" -> instance.templateName
            "occupancy" -> instance.occupancyCount.toString()
            "type" -> instance.instanceType.name.lowercase()
            "originx" -> instance.originChunkX.toString()
            "originz" -> instance.originChunkZ.toString()
            "sizex" -> instance.sizeXChunks.toString()
            "sizez" -> instance.sizeZChunks.toString()
            "minblockx" -> instance.minBlockX.toString()
            "maxblockx" -> instance.maxBlockX.toString()
            "minblockz" -> instance.minBlockZ.toString()
            "maxblockz" -> instance.maxBlockZ.toString()
            else -> null
        }
    }
}
