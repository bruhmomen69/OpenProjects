package bruh.regionrestore.cmd

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import bruh.regionrestore.cloner.*
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.SchedulerService
import bruh.regionrestore.utils.sendMiniMessage
import bruh.zchat.utils.menuapi.MenuAPI
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import kotlin.math.floor

/**
 * Target for timer operations - either an instance ID or a template name.
 */
sealed class TimerTarget {
    data class ByInstanceId(val instanceId: java.util.UUID) : TimerTarget()
    data class ByTemplateName(val templateName: String) : TimerTarget()
}

@Command("regionrestore", "rr", "arena")
class RegionRestoreCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val schedulerService: SchedulerService,
    private val config: RegionRestoreConfig,
    private val massClonerService: MassClonerService,
    private val menuAPI: MenuAPI,
    private val plugin: JavaPlugin
) {
    @Subcommand("uitest")
    fun uiTest(actor: BukkitCommandActor) {
        val menu = menuAPI.dynamic {
            item(XMaterial.ALLIUM) {
                name = Component.text("Test Allium")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Allium")
                }
            }

            item(XMaterial.ROSE_BUSH) {
                name = Component.text("Test Rose")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Rose")
                }
            }

            item(XMaterial.AZALEA) {
                name = Component.text("Test Azalea")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Azalea")
                }
            }

            item(XMaterial.SUNFLOWER) {
                name = Component.text("Test Sunflower")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Sunflower")
                }
            }


            item(XMaterial.CACTUS) {
                name = Component.text("Test Cactus")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Cactus")
                }
            }

            item(XMaterial.ORANGE_TULIP) {
                name = Component.text("Test Tulip")
                lore = mutableListOf(Component.text("Lore line 1"), Component.text("Lore line 2"))
                onClickDeny { a, b ->
                    actor.requirePlayer().sendMiniMessage("You clicked Tulip")
                }
            }
        }

        menuAPI.open(menu, actor.requirePlayer())
    }

    @Subcommand("template create")
    @CommandPermission("regionrestore.template.create")
    suspend fun createTemplate(
        actor: CommandSender,
        name: String,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        world: String? = null
    ) {
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("This command can only be run by a player")
            return
        }

        val targetWorld = if (world != null) {
            Bukkit.getWorld(world)
        } else {
            player.world
        } ?: run {
            actor.sendMiniMessage("World not found")
            return
        }

        val minChunkX = floor(minX / 16.0).toInt()
        val maxChunkX = floor(maxX / 16.0).toInt()
        val minChunkZ = floor(minZ / 16.0).toInt()
        val maxChunkZ = floor(maxZ / 16.0).toInt()

        val template = nmsAdapter.serializeArea(targetWorld, minChunkX, minChunkZ, maxChunkX, maxChunkZ)

        val descriptionFormat = config.templates.defaultDescriptionFormat
        val description = descriptionFormat.replace("<player>", player.name)
        templateRepository.saveTemplate(name, description, template, nmsAdapter.minecraftVersion)

        actor.sendMiniMessage("<green>Template '$name' created successfully!")
    }

    @Subcommand("template list")
    @CommandPermission("regionrestore.template.list")
    suspend fun listTemplates(actor: CommandSender) {
        val templates = templateRepository.listTemplates()
        if (templates.isEmpty()) {
            actor.sendMiniMessage("<gray>No templates found")
            return
        }

        actor.sendMiniMessage("<green>Templates:")
        templates.forEach { templateName ->
            actor.sendMiniMessage("<white>- $templateName")
        }
    }

    @Subcommand("template info")
    @CommandPermission("regionrestore.template.info")
    suspend fun templateInfo(actor: CommandSender, @SuggestTemplateName name: String) {
        val versions = templateRepository.getTemplateVersions(name)
        if (versions == null || versions.isEmpty()) {
            actor.sendMiniMessage("<red>Template '$name' not found")
            return
        }

        val activeVersionId = templateRepository.loadActiveTemplateVersion(name)?.versionId ?: 0

        actor.sendMiniMessage("<green>Template: <white>$name")
        actor.sendMiniMessage("<gray>Versions: ${versions.size}")
        versions.forEach { version ->
            val activeMark = if (version.versionId == activeVersionId) " <green>(active)" else ""
            actor.sendMiniMessage(
                "<white>  v${version.versionId}:$activeMark <gray>${
                    java.time.Instant.ofEpochMilli(
                        version.createdAt
                    )
                } - ${version.description}"
            )
        }
    }

    @Subcommand("template setactive")
    @CommandPermission("regionrestore.template.setactive")
    suspend fun setActiveVersion(actor: CommandSender, @SuggestTemplateName name: String, @SuggestVersionNumber versionId: Int) {
        val success = templateRepository.setActiveVersion(name, versionId)
        if (success) {
            actor.sendMiniMessage("<green>Template '$name' active version set to v$versionId")
        } else {
            actor.sendMiniMessage("<red>Failed to set active version. Template or version not found.")
        }
    }

    @Subcommand("template delete")
    @CommandPermission("regionrestore.template.delete")
    suspend fun deleteTemplate(actor: CommandSender, @SuggestTemplateName name: String) {
        val deleted = templateRepository.deleteTemplate(name)
        if (deleted) {
            actor.sendMiniMessage("<green>Template '$name' deleted successfully!")
        } else {
            actor.sendMiniMessage("<red>Template '$name' not found")
        }
    }

    @Subcommand("restore version")
    @CommandPermission("regionrestore.restore")
    suspend fun restore(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        @SuggestVersionId @Optional @Default("active") versionOrActive: String = "active",
        @Optional scope: AudienceScope? = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("This command can only be run by a player")
            return
        }

        val targetWorld = player.world

        val scope = scope ?: config.notifications.defaultAudienceScope

        val templateVersion = if (versionOrActive.equals("active", true)) {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMiniMessage("<red>Invalid version ID: '$versionOrActive'")
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMiniMessage("<red>Template '$name' version '$versionOrActive' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template was created on Minecraft version ${templateVersion.minecraftVersion}, attemping restore on version ${nmsAdapter.minecraftVersion}. Please update the template to avoid restore errors.")
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), scope)

        actor.sendMiniMessage("<green>Restoring template '$name' (v${templateVersion.versionId})...")
    }

    @Subcommand("restore in")
    @CommandPermission("regionrestore.restorein")
    suspend fun restoreIn(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        seconds: Int,
        @SuggestVersionId versionOrActive: String = "active",
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("This command can only be run by a player")
            return
        }

        val targetWorld = player.world

        val templateVersion = if (versionOrActive == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMiniMessage("<red>Invalid version ID: '$versionOrActive'")
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMiniMessage("<red>Template '$name' version '$versionOrActive' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template was created on Minecraft version ${templateVersion.minecraftVersion}, restoring on version ${nmsAdapter.minecraftVersion}. Please update the template to avoid errors.")
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(
            job,
            countdownSeconds = seconds,
            announcePoints = config.restore.defaultAnnounceTimes,
            audienceScope = scope
        )

        actor.sendMiniMessage("<green>Scheduled restore of '$name' in $seconds seconds")
    }

    @Subcommand("restore template")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreTemplate(
        actor: CommandSender,
        @SuggestTemplateName name: String
    ) {
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("This command can only be run by a player")
            return
        }

        val targetWorld = player.world

        val templateVersion = templateRepository.loadActiveTemplateVersion(name)

        if (templateVersion == null) {
            actor.sendMiniMessage("<red>Template '$name' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template was created on Minecraft version ${templateVersion.minecraftVersion}, attemping restore on version ${nmsAdapter.minecraftVersion}. Please update the template to avoid errors.")
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMiniMessage("<green>Restoring template '$name' (v${templateVersion.versionId})...")
    }

    @Subcommand("restore at")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreAt(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        world: String,
        x: Int,
        z: Int,
        @SuggestVersionId @Optional versionOrActive: String? = null
    ) {
        val targetWorld = Bukkit.getWorld(world) ?: run {
            actor.sendMiniMessage("<red>World '$world' not found")
            return
        }

        val versionToUse = versionOrActive ?: "active"

        val templateVersion = if (versionToUse == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionToUse.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMiniMessage("<red>Invalid version ID: '$versionToUse'")
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMiniMessage("<red>Template '$name' version '$versionToUse' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template was created on Minecraft version ${templateVersion.minecraftVersion}, restoring on ${nmsAdapter.minecraftVersion}. Please update the template to avoid errors.")
        }

        val targetChunkX = floor(x / 16.0).toInt()
        val targetChunkZ = floor(z / 16.0).toInt()

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = targetChunkX,
            targetChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    targetChunkX,
                    targetChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMiniMessage("<green>Restoring template '$name' (v${templateVersion.versionId}) at chunk ($targetChunkX, $targetChunkZ)...")
    }

    @Subcommand("cloner status")
    @CommandPermission("regionrestore.cloner.status")
    suspend fun clonerStatus(actor: CommandSender, world: String? = null) {
        actor.sendMiniMessage("<green>Mass Cloner Status:")
        actor.sendMiniMessage("<gray>─────────────────────────────────")

        val worldsToShow = if (world != null) {
            val targetWorld = Bukkit.getWorld(world)
            if (targetWorld == null) {
                actor.sendMiniMessage("<red>World '$world' not found")
                return
            }
            listOf(targetWorld)
        } else {
            Bukkit.getWorlds().filter { w ->
                config.massCloner.worlds.any { it.name == w.name }
            }
        }

        for (targetWorld in worldsToShow) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == targetWorld.name }
            if (worldConfig == null) {
                actor.sendMiniMessage("<gray>${targetWorld.name}: <yellow>Not managed")
                continue
            }

            actor.sendMiniMessage("<white>${targetWorld.name}:")

            for (pool in worldConfig.pools) {
                val instances = massClonerService.getInstancesForPool(targetWorld.name, pool.templateName)
                val activeCount = instances.size
                val targetCount = pool.count
                val status = if (activeCount == targetCount) "<green>✓" else "<yellow>⚠"

                actor.sendMiniMessage("  $status <white>${pool.templateName}: $activeCount/$targetCount instances")
                actor.sendMiniMessage(
                    "     <gray>Version: ${pool.versionMode}" +
                            (if (pool.versionMode == VersionMode.PINNED) " #${pool.pinnedVersionId}" else " (active)")
                )
                actor.sendMiniMessage(
                    "     <gray>Separation: ${pool.separationChunks} chunks | " +
                            "Boot restore: ${pool.restoreOnBoot} | " +
                            "Vacate restore: ${pool.restoreOnVacate}"
                )

                if (pool.restoreIntervalSeconds != null) {
                    actor.sendMiniMessage("     <gray>Repeat: Every ${pool.restoreIntervalSeconds}s")
                }
            }
        }
    }

    @Subcommand("cloner restore")
    @CommandPermission("regionrestore.cloner.restore")
    suspend fun clonerRestore(actor: CommandSender, world: String?, template: String?) {
        val worldName = world ?: (actor as? Player)?.world?.name
        if (worldName == null) {
            actor.sendMiniMessage("<red>Please specify a world or run as a player")
            return
        }

        actor.sendMiniMessage("<green>Triggering manual restore for cloner instances...")

        val worldsToRestore = if (template != null) {
            // Restore specific template in specific world
            listOf(worldName to listOf(template))
        } else {
            // Restore all templates in world
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == worldName }
            if (worldConfig == null) {
                actor.sendMiniMessage("<red>World '$worldName' is not managed by Mass Cloner")
                return
            }
            listOf(worldName to worldConfig.pools.map { it.templateName })
        }

        var restoredCount = 0

        for ((w, templates) in worldsToRestore) {
            for (templateName in templates) {
                val instances = massClonerService.getInstancesForPool(w, templateName)
                for (instance in instances) {
                    massClonerService.triggerInstanceRestore(instance)
                    restoredCount++
                }
            }
        }

        actor.sendMiniMessage("<green>Triggered restore for $restoredCount instances")
    }

    @Subcommand("cloner regen")
    @CommandPermission("regionrestore.cloner.regen")
    suspend fun clonerRegen(
        actor: CommandSender,
        world: String? = null,
        force: Boolean = false
    ) {
        // Require --force flag to prevent accidental data loss
        if (!force) {
            actor.sendMiniMessage(
                "<red>This command will destroy and reallocate all pooled instances. " +
                        "Use --force to confirm: /regionrestore cloner regen [world] --force"
            )
            return
        }

        // Validate world if specified
        if (world != null) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == world }
            if (worldConfig == null) {
                actor.sendMiniMessage("<red>World '$world' is not configured in Mass Cloner")
                return
            }
        }

        // Call service to regenerate pools
        val worldsToRegen = if (world != null) listOf(world) else emptyList()
        val (removed, allocated) = massClonerService.regeneratePools(worldsToRegen)

        actor.sendMiniMessage("<green>Regeneration complete:")
        actor.sendMiniMessage("  Removed: $removed pooled instances")
        actor.sendMiniMessage("  Allocated: $allocated pooled instances")
        actor.sendMiniMessage("<yellow>Manual instances were preserved.")
    }

    @Subcommand("instance create")
    @CommandPermission("regionrestore.instance.create")
    suspend fun createInstance(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        chunkX: Int,
        chunkZ: Int,
        world: String? = null,
        versionId: Int? = null,
        restoreOnBoot: Boolean = false,
        restoreOnVacate: Boolean = false,
        restoreIntervalSeconds: Int? = null
    ) {
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("<red>This command must be run by a player")
            return
        }

        val targetWorld = if (world != null) {
            Bukkit.getWorld(world)
        } else {
            player.world
        } ?: run {
            actor.sendMiniMessage("<red>World not found")
            return
        }

        // Load template to get dimensions
        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: run {
            actor.sendMiniMessage("<red>Template '$templateName' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template version mismatch. Please update the template to avoid errors.")
        }

        // Create instance config
        val config = InstanceConfig(
            restoreOnBoot = restoreOnBoot,
            restoreOnVacate = restoreOnVacate,
            restoreIntervalSeconds = restoreIntervalSeconds,
            restoreAudienceScope = config.notifications.defaultAudienceScope,
            updateLight = null // allow fallback to pool/global updateLight at restore time
        )

        // Create instance
        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = versionId ?: templateVersion.versionId,
            originChunkX = chunkX,
            originChunkZ = chunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = config
        )

        // Add to registry
        massClonerService.addManualInstance(instance)

        // Start triggers
        if (config.restoreOnBoot || config.restoreIntervalSeconds != null) {
            massClonerService.startInstanceTriggers(instance)
        }

        actor.sendMiniMessage(
            "<green>Created manual instance '${instance.instanceId}' for template '$templateName' " +
                    "at chunk ($chunkX, $chunkZ)"
        )
    }

    @Subcommand("instance list all")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listAllInstances(actor: CommandSender) {
        val instances = massClonerService.listInstances(null, null)

        if (instances.isEmpty()) {
            actor.sendMiniMessage("<gray>No instances found")
            return
        }

        actor.sendMiniMessage("<green>Instances (${instances.size}):")
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMiniMessage(
                "  $typeMark ${instance.instanceId} - ${instance.templateName} " +
                        "at (${instance.originChunkX}, ${instance.originChunkZ}) in ${instance.worldName}"
            )
        }
    }

    @Subcommand("instance list world")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listInstancesByWorld(
        actor: CommandSender,
        world: String,
        @Optional type: String? = null
    ) {
        val instanceType = when (type?.uppercase()) {
            "POOLED" -> InstanceType.POOLED
            "MANUAL" -> InstanceType.MANUAL
            else -> null
        }

        val instances = massClonerService.listInstances(world, instanceType)

        if (instances.isEmpty()) {
            actor.sendMiniMessage("<gray>No instances found")
            return
        }

        actor.sendMiniMessage("<green>Instances (${instances.size}):")
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMiniMessage(
                "  $typeMark ${instance.instanceId} - ${instance.templateName} " +
                        "at (${instance.originChunkX}, ${instance.originChunkZ}) in ${instance.worldName}"
            )
        }
    }

    @Subcommand("instance info")
    @CommandPermission("regionrestore.instance.info")
    suspend fun instanceInfo(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMiniMessage("<red>Instance '$instanceId' not found")
            return
        }

        val config = instance.config
        actor.sendMiniMessage("<green>Instance: ${instance.instanceId}")
        actor.sendMiniMessage("  Template: ${instance.templateName}")
        actor.sendMiniMessage("  Location: ${instance.worldName} chunk (${instance.originChunkX}, ${instance.originChunkZ})")
        actor.sendMiniMessage("  Size: ${instance.sizeXChunks}x${instance.sizeZChunks} chunks")
        actor.sendMiniMessage("  Type: ${instance.instanceType}")
        actor.sendMiniMessage("  Config:")
        actor.sendMiniMessage("    Boot restore: ${config?.restoreOnBoot ?: false}")
        actor.sendMiniMessage("    Vacate restore: ${config?.restoreOnVacate ?: false}")
        actor.sendMiniMessage("    Repeat: ${config?.restoreIntervalSeconds ?: "none"}")
    }

    @Subcommand("instance delete")
    @CommandPermission("regionrestore.instance.delete")
    suspend fun deleteInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val deleted = massClonerService.removeInstance(id)

        if (deleted) {
            actor.sendMiniMessage("<green>Deleted instance '$instanceId'")
        } else {
            actor.sendMiniMessage("<red>Instance '$instanceId' not found")
        }
    }

    @Subcommand("instance restore")
    @CommandPermission("regionrestore.instance.restore")
    suspend fun restoreInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMiniMessage("<red>Instance '$instanceId' not found")
            return
        }

        massClonerService.triggerInstanceRestore(instance)
        actor.sendMiniMessage("<green>Triggered restore for instance '$instanceId'")
    }

    @Subcommand("timer set instance")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByInstanceId(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMiniMessage("<red>Instance '$instanceId' not found")
            return
        }

        // Update instance config with new interval
        val updatedConfig = InstanceConfig(
            restoreOnBoot = instance.config?.restoreOnBoot ?: false,
            restoreOnVacate = instance.config?.restoreOnVacate ?: false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null // preserve fallback to pool/global updateLight
        )

        // Create updated instance with new config
        val updatedInstance = instance.copy(
            config = updatedConfig
        )

        // Stop existing timers
        massClonerService.stopInstanceTriggers(instance)

        // Update the instance in the registry (remove old, add updated)
        massClonerService.removeInstance(instance.instanceId)
        massClonerService.addManualInstance(updatedInstance)

        // Start new timers
        massClonerService.startInstanceTriggers(updatedInstance)

        actor.sendMiniMessage("<green>Set repeating timer for instance '$instanceId' every $intervalSeconds seconds")
        actor.sendMiniMessage("<gray>Use '/regionrestore timer cancel id $instanceId' to stop")
    }

    @Subcommand("timer set template")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByTemplateName(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player
        val targetWorld = player?.world ?: run {
            actor.sendMiniMessage("<red>World not found")
            return
        }

        // Load template
        val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)
        if (templateVersion == null) {
            actor.sendMiniMessage("<red>Template '$templateName' not found")
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMiniMessage("<red>Template version mismatch. Please update the template to avoid errors.")
        }

        // Use template's original coordinates
        val targetChunkX = templateVersion.data.minChunkX
        val targetChunkZ = templateVersion.data.minChunkZ

        // Create hidden manual instance for this timer
        val instanceConfig = InstanceConfig(
            restoreOnBoot = false,
            restoreOnVacate = false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null // allow fallback to pool/global updateLight at restore time
        )

        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = templateVersion.versionId,
            originChunkX = targetChunkX,
            originChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = instanceConfig
        )

        massClonerService.addManualInstance(instance)
        massClonerService.startInstanceTriggers(instance)

        actor.sendMiniMessage(
            "<green>Set repeating timer for template '$templateName' at original location " +
                    "every $intervalSeconds seconds"
        )
        actor.sendMiniMessage("<gray>Instance ID: ${instance.instanceId} (use '/regionrestore timer cancel id ${instance.instanceId}' to stop)")
    }

    @Subcommand("timer cancel id")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerById(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMiniMessage("<red>Instance '$instanceId' not found")
            return
        }

        massClonerService.removeInstance(id)
        actor.sendMiniMessage("<green>Cancelled timer for instance '$instanceId'")
    }

    @Subcommand("timer cancel template-all")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerByTemplate(
        actor: CommandSender,
        @SuggestTemplateName templateName: String
    ) {
        // Cancel all timers for instances of this template in the current world
        val player = actor as? Player ?: run {
            actor.sendMiniMessage("This command can only be run by a player")
            return
        }

        val worldName = player.world.name
        val instances = massClonerService.listInstances(worldName, null)
        val templateInstances = instances.filter { it.templateName == templateName }

        if (templateInstances.isEmpty()) {
            actor.sendMiniMessage("<yellow>No instances found for template '$templateName' in this world")
            return
        }

        var cancelledCount = 0
        for (instance in templateInstances) {
            if (instance.config?.restoreIntervalSeconds != null) {
                massClonerService.removeInstance(instance.instanceId)
                cancelledCount++
            }
        }

        actor.sendMiniMessage("<green>Cancelled timers for $cancelledCount instance(s) of template '$templateName'")
    }
}
