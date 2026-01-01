package bruh.regionrestore.hooks

import io.github.miniplaceholders.api.Expansion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import bruh.regionrestore.cloner.InstanceType
import bruh.regionrestore.cloner.MassClonerService
import bruh.regionrestore.template.TemplateRepository
import kotlinx.coroutines.runBlocking

/**
 * MiniPlaceholders expansion for RegionRestore.
 * Provides MiniMessage-compatible placeholders for pools, instances, templates, and restore information.
 *
 * Placeholder format: <regionrestore_<placeholder>>
 *
 * Available placeholders:
 * - <regionrestore_instance_count> - Total number of instances
 * - <regionrestore_instance_count_pooled> - Count of pooled instances
 * - <regionrestore_instance_count_manual> - Count of manual instances
 * - <regionrestore_template_count> - Total number of templates
 * - <regionrestore_pool_count> - Total number of pools
 */
object MiniPlaceholdersHook {

    /**
     * Creates and registers the MiniPlaceholders expansion.
     */
    fun register(
        massClonerService: MassClonerService,
        templateRepository: TemplateRepository
    ) {
        val expansion = Expansion.builder("regionrestore")
            // Total instance count
            .globalPlaceholder("instance_count") { _, _ ->
                Tag.selfClosingInserting(
                    Component.text(massClonerService.listInstances().size)
                )
            }
            // Pooled instance count
            .globalPlaceholder("instance_count_pooled") { _, _ ->
                Tag.selfClosingInserting(
                    Component.text(massClonerService.listInstances(instanceType = InstanceType.POOLED).size)
                )
            }
            // Manual instance count
            .globalPlaceholder("instance_count_manual") { _, _ ->
                Tag.selfClosingInserting(
                    Component.text(massClonerService.listInstances(instanceType = InstanceType.MANUAL).size)
                )
            }
            // Template count
            .globalPlaceholder("template_count") { _, _ ->
                Tag.selfClosingInserting(
                    Component.text(runBlocking { templateRepository.listTemplates().size })
                )
            }
            // Pool count
            .globalPlaceholder("pool_count") { _, _ ->
                Tag.selfClosingInserting(
                    Component.text(massClonerService.getPoolCount())
                )
            }
            // Instance count by world (takes world name as argument)
            .globalPlaceholder("instance_count_world") { queue, _ ->
                val worldName = queue.popOr("World name required").value()
                Tag.selfClosingInserting(
                    Component.text(massClonerService.listInstances(worldName = worldName).size)
                )
            }
            // Pool instance count (takes world and template as arguments)
            .globalPlaceholder("pool_instance_count") { queue, _ ->
                val worldName = queue.popOr("World name required").value()
                val templateName = queue.popOr("Template name required").value()
                Tag.selfClosingInserting(
                    Component.text(massClonerService.getInstancesForPool(worldName, templateName).size)
                )
            }
            // Pool target count (takes world and template as arguments)
            .globalPlaceholder("pool_target") { queue, _ ->
                val worldName = queue.popOr("World name required").value()
                val templateName = queue.popOr("Template name required").value()
                Tag.selfClosingInserting(
                    Component.text(massClonerService.getPoolTarget(worldName, templateName) ?: 0)
                )
            }
            // Pool occupied count (takes world and template as arguments)
            .globalPlaceholder("pool_occupied") { queue, _ ->
                val worldName = queue.popOr("World name required").value()
                val templateName = queue.popOr("Template name required").value()
                Tag.selfClosingInserting(
                    Component.text(
                        massClonerService.getInstancesForPool(worldName, templateName)
                            .count { it.occupancyCount > 0 }
                    )
                )
            }
            // Template versions (takes template name as argument)
            .globalPlaceholder("template_versions") { queue, _ ->
                val templateName = queue.popOr("Template name required").value()
                Tag.selfClosingInserting(
                    Component.text(
                        runBlocking { templateRepository.getTemplateVersions(templateName)?.size ?: 0 }
                    )
                )
            }
            // Template active version (takes template name as argument)
            .globalPlaceholder("template_active_version") { queue, _ ->
                val templateName = queue.popOr("Template name required").value()
                Tag.selfClosingInserting(
                    Component.text(
                        runBlocking {
                            templateRepository.loadActiveTemplateVersion(templateName)?.versionId?.toString() ?: "none"
                        }
                    )
                )
            }
            .build()

        expansion.register()
    }
}
