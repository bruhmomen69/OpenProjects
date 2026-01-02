package bruh.regionrestore.api

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import bruh.regionrestore.cloner.*
import bruh.regionrestore.config.NotificationsConfig
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.config.RegionRestoreConfigLoader
import bruh.regionrestore.config.RestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import bruh.regionrestore.nms.RegionTemplateVersion
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.notification.NotificationService
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.SchedulerService
import java.util.*
import kotlin.math.floor

/**
 * Factory for creating a RegionRestoreApi that is backed by the PaperMC
 * implementation and the plugin's internal services.
 */
fun createRegionRestoreApi(
    config: RegionRestoreConfig,
    configLoader: RegionRestoreConfigLoader,
    nmsAdapter: PaperNmsAdapter,
    templateRepository: TemplateRepository,
    notificationService: NotificationService,
    schedulerService: SchedulerService,
    massClonerService: MassClonerService,
    plugin: JavaPlugin
): RegionRestoreApi {
    val notificationsConfig: NotificationsConfig = config.notifications
    val restoreConfig: RestoreConfig = config.restore

    val templateApi = PaperTemplateApi(
        nms = nmsAdapter,
        templateRepository = templateRepository,
        templatesConfig = config.templates
    )

    val notificationApi = PaperNotificationApi(
        notificationService = notificationService,
        notificationsConfig = notificationsConfig
    )

    val restoreApi = PaperRestoreSchedulerApi(
        schedulerService = schedulerService,
        templateRepository = templateRepository,
        notificationsConfig = notificationsConfig,
        restoreConfig = restoreConfig,
        nmsAdapter = nmsAdapter,
        plugin = plugin
    )

    val clonerApi = PaperMassClonerApi(
        massClonerService = massClonerService,
        allocator = PlacementAllocator(),
        templateRepository = templateRepository,
        nmsAdapter = nmsAdapter
    )

    val configApi = PaperConfigApi(
        initialConfig = config,
        configLoader = configLoader
    )

    return PaperRegionRestoreApi(
        templates = templateApi,
        notifications = notificationApi,
        restore = restoreApi,
        cloner = clonerApi,
        config = configApi,
        nms = nmsAdapter
    )
}

private class PaperRegionRestoreApi(
    override val templates: TemplateApi,
    override val notifications: NotificationApi,
    override val restore: RestoreSchedulerApi,
    override val cloner: MassClonerApi,
    override val config: ConfigApi,
    override val nms: PaperNmsAdapter
) : RegionRestoreApi

private class PaperTemplateApi(
    override val nms: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val templatesConfig: bruh.regionrestore.config.TemplatesConfig
) : TemplateApi {

    override suspend fun listTemplates(): List<String> =
        templateRepository.listTemplates()

    override suspend fun getTemplateVersions(name: String): List<TemplateVersionSummary>? {
        val versions = templateRepository.getTemplateVersions(name) ?: return null
        return versions.map {
            TemplateVersionSummary(
                versionId = it.versionId,
                createdAt = it.createdAt,
                minecraftVersion = it.minecraftVersion,
                description = it.description
            )
        }
    }

    override suspend fun saveTemplate(
        name: String,
        description: String,
        template: RegionTemplate,
        minecraftVersion: String
    ): RegionTemplateVersion {
        return templateRepository.saveTemplate(name, description, template, minecraftVersion)
    }

    override suspend fun loadTemplateVersion(name: String, versionId: Int): RegionTemplateVersion? =
        templateRepository.loadTemplateVersion(name, versionId)

    override suspend fun loadTemplateVersionFromDisk(name: String, versionId: Int): RegionTemplateVersion? =
        templateRepository.loadTemplateVersionFromDisk(name, versionId)

    override suspend fun loadActiveTemplateVersion(name: String): RegionTemplateVersion? =
        templateRepository.loadActiveTemplateVersion(name)

    override suspend fun setActiveVersion(name: String, versionId: Int): Boolean =
        templateRepository.setActiveVersion(name, versionId)

    override suspend fun deleteTemplate(name: String): Boolean =
        templateRepository.deleteTemplate(name)

    override suspend fun reloadIndex() {
        templateRepository.load()
    }

    override suspend fun saveIndex() {
        templateRepository.save()
    }

    override suspend fun createTemplateFromChunks(
        name: String,
        description: String,
        world: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int
    ): RegionTemplateVersion {
        val template = nms.serializeArea(world, minChunkX, minChunkZ, maxChunkX, maxChunkZ)
        return saveTemplate(name, description, template, nms.minecraftVersion)
    }

    override suspend fun createTemplateFromBlocks(
        name: String,
        description: String,
        world: World,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int
    ): RegionTemplateVersion {
        val minChunkX = floor(minBlockX / 16.0).toInt()
        val maxChunkX = floor(maxBlockX / 16.0).toInt()
        val minChunkZ = floor(minBlockZ / 16.0).toInt()
        val maxChunkZ = floor(maxBlockZ / 16.0).toInt()
        return createTemplateFromChunks(name, description, world, minChunkX, minChunkZ, maxChunkX, maxChunkZ)
    }

    override suspend fun createTemplateFromBlocksWithDefaultDescription(
        player: Player,
        name: String,
        world: World,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int
    ): RegionTemplateVersion {
        val descriptionFormat = templatesConfig.defaultDescriptionFormat
        val description = descriptionFormat.replace("<player>", player.name)
        return createTemplateFromBlocks(name, description, world, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
    }
}

private class PaperNotificationApi(
    private val notificationService: NotificationService,
    private val notificationsConfig: NotificationsConfig
) : NotificationApi {

    override val defaultNearbyRadiusBlocks: Int
        get() = notificationsConfig.nearbyRadiusBlocks

    override fun send(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World?,
        location: org.bukkit.Location?,
        radiusBlocks: Int
    ) {
        notificationService.sendNotification(scope, config, world, location, radiusBlocks)
    }

    override fun sendForRegionAABB(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World?,
        minBlockX: Int,
        maxBlockX: Int,
        minBlockZ: Int,
        maxBlockZ: Int,
        radiusBlocks: Int
    ) {
        notificationService.sendNotification(
            scope,
            config,
            world,
            minBlockX,
            maxBlockX,
            minBlockZ,
            maxBlockZ,
            radiusBlocks
        )
    }

    override fun buildFromEventConfig(
        eventConfig: bruh.regionrestore.config.NotificationEventConfig,
        variables: Map<String, String>
    ): NotificationConfig? {
        return NotificationConfig.fromEventConfig(eventConfig, variables)
    }
}

private class PaperRestoreSchedulerApi(
    private val schedulerService: SchedulerService,
    private val templateRepository: TemplateRepository,
    private val notificationsConfig: NotificationsConfig,
    private val restoreConfig: RestoreConfig,
    private val nmsAdapter: PaperNmsAdapter,
    private val plugin: JavaPlugin
) : RestoreSchedulerApi {

    override val defaultAnnounceTimes: List<Int>
        get() = restoreConfig.defaultAnnounceTimes

    override val defaultAudienceScope: AudienceScope
        get() = notificationsConfig.defaultAudienceScope

    override suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int?,
        announcePoints: List<Int>,
        audienceScope: AudienceScope
    ) {
        schedulerService.scheduleRestore(job, countdownSeconds, announcePoints, audienceScope)
    }

    override fun scheduleRepeatingRestore(
        job: RestoreJob,
        intervalSeconds: Int,
        audienceScope: AudienceScope
    ) {
        schedulerService.scheduleRepeatingRestore(job, intervalSeconds, audienceScope)
    }

    override fun cancelRepeatingRestore(jobId: UUID) {
        schedulerService.cancelRepeatingRestore(jobId)
    }

    override fun cancelCountdown(jobId: UUID) {
        schedulerService.cancelCountdown(jobId)
    }

    override fun cancelAll() {
        schedulerService.cancelAll()
    }

    override suspend fun scheduleTemplateRestoreOnce(
        templateVersion: RegionTemplateVersion,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        countdownSeconds: Int?,
        audienceScope: AudienceScope,
        announcePoints: List<Int>
    ): UUID {
        val jobId = UUID.randomUUID()
        val job = RestoreJob(
            id = jobId,
            world = world,
            targetChunkX = originChunkX,
            targetChunkZ = originChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = false
        )

        scheduleRestore(job, countdownSeconds, announcePoints, audienceScope)
        return jobId
    }

    override suspend fun scheduleTemplateRestoreOnce(
        templateName: String,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        versionId: Int?,
        countdownSeconds: Int?,
        audienceScope: AudienceScope,
        announcePoints: List<Int>
    ): UUID {
        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: error("Template '$templateName' not found")

        return scheduleTemplateRestoreOnce(
            templateVersion = templateVersion,
            world = world,
            originChunkX = originChunkX,
            originChunkZ = originChunkZ,
            countdownSeconds = countdownSeconds,
            audienceScope = audienceScope,
            announcePoints = announcePoints
        )
    }

    override suspend fun scheduleTemplateRepeatingRestore(
        templateName: String,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        versionId: Int?,
        intervalSeconds: Int,
        audienceScope: AudienceScope
    ): UUID {
        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: error("Template '$templateName' not found")

        val jobId = UUID.randomUUID()
        val job = RestoreJob(
            id = jobId,
            world = world,
            targetChunkX = originChunkX,
            targetChunkZ = originChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = false
        )

        schedulerService.scheduleRepeatingRestore(job, intervalSeconds, audienceScope)
        return jobId
    }
}

private class PaperMassClonerApi(
    private val massClonerService: MassClonerService,
    private val allocator: PlacementAllocator,
    private val templateRepository: TemplateRepository,
    private val nmsAdapter: PaperNmsAdapter
) : MassClonerApi {

    override suspend fun saveState() {
        massClonerService.saveState()
    }

    override suspend fun shutdown() {
        massClonerService.shutdown()
    }

    override fun getInstancesForPool(worldName: String, templateName: String): List<RegionInstance> =
        massClonerService.getInstancesForPool(worldName, templateName)

    override fun listInstances(worldName: String?, instanceType: InstanceType?): List<RegionInstance> {
        return massClonerService.listInstances(worldName, instanceType)
    }

    override fun getInstance(instanceId: UUID): RegionInstance? =
        massClonerService.getInstance(instanceId)

    override suspend fun addManualInstance(instance: RegionInstance) {
        massClonerService.addManualInstance(instance)
    }

    override suspend fun removeInstance(instanceId: UUID): Boolean =
        massClonerService.removeInstance(instanceId)

    override suspend fun triggerInstanceRestore(instance: RegionInstance) {
        massClonerService.triggerInstanceRestore(instance)
    }

    override suspend fun triggerInstanceRestore(instanceId: UUID) {
        val instance = getInstance(instanceId) ?: return
        triggerInstanceRestore(instance)
    }

    override suspend fun startInstanceTriggers(instance: RegionInstance) {
        massClonerService.startInstanceTriggers(instance)
    }

    override fun stopInstanceTriggers(instance: RegionInstance) {
        massClonerService.stopInstanceTriggers(instance)
    }

    override suspend fun regeneratePools(worldNames: List<String>): Pair<Int, Int> =
        massClonerService.regeneratePools(worldNames)

    override suspend fun createManualInstance(
        worldName: String,
        templateName: String,
        originChunkX: Int,
        originChunkZ: Int,
        sizeXChunks: Int,
        sizeZChunks: Int,
        versionId: Int?,
        config: InstanceConfig?,
        startTriggers: Boolean
    ): RegionInstance {
        val instance = RegionInstance.create(
            instanceId = UUID.randomUUID(),
            worldName = worldName,
            templateName = templateName,
            versionId = versionId,
            originChunkX = originChunkX,
            originChunkZ = originChunkZ,
            sizeXChunks = sizeXChunks,
            sizeZChunks = sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = config
        )

        massClonerService.addManualInstance(instance)

        if (startTriggers) {
            massClonerService.startInstanceTriggers(instance)
        }

        return instance
    }

    override suspend fun allocateInstancesForPool(
        pool: RegionPool,
        existingInstances: List<RegionInstance>,
        template: RegionTemplate,
        neededCount: Int
    ): List<RegionInstance> {
        // Delegate to the same placement logic used internally by MassClonerService.
        return allocator.allocateInstances(pool, existingInstances, template, neededCount)
    }
}

private class PaperConfigApi(
    initialConfig: RegionRestoreConfig,
    private val configLoader: RegionRestoreConfigLoader
) : ConfigApi {

    @Volatile
    private var currentConfig: RegionRestoreConfig = initialConfig

    override val current: RegionRestoreConfig
        get() = currentConfig

    override suspend fun reload(): RegionRestoreConfig {
        val newConfig = configLoader.load()
        currentConfig = newConfig
        return newConfig
    }
}
