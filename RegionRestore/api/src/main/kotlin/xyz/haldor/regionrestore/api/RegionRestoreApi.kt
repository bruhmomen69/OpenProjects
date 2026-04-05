package bruh.regionrestore.api

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.config.NotificationEventConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.nms.RegionTemplate
import bruh.regionrestore.nms.RegionTemplateVersion
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.notification.NotificationConfig
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.cloner.InstanceConfig
import bruh.regionrestore.cloner.InstanceType
import bruh.regionrestore.cloner.RegionInstance
import bruh.regionrestore.cloner.RegionPool
import java.util.UUID

/**
 * Entry point for interacting with the RegionRestore plugin from other plugins.
 *
 * All methods are safe to call from the main server thread unless otherwise documented.
 */
interface RegionRestoreApi {
    /** Template and repository operations. */
    val templates: TemplateApi

    /** Notification helpers. */
    val notifications: NotificationApi

    /** Low-level restore scheduler helpers. */
    val restore: RestoreSchedulerApi

    /** Mass cloner & instance management helpers. */
    val cloner: MassClonerApi

    /** Configuration access & reload. */
    val config: ConfigApi

    /** Access to the active NMS adapter implementation. */
    val nms: PaperNmsAdapter
}

/**
 * Static access to the RegionRestore API instance.
 *
 * The plugin initializes this during enable and clears it on disable.
 */
object RegionRestore {
    @Volatile
    private var apiInstance: RegionRestoreApi? = null

    fun setApi(api: RegionRestoreApi) {
        this.apiInstance = api
    }

    fun clearApi() {
        this.apiInstance = null
    }

    /** Get the API instance or null if the plugin is not enabled. */
    fun apiOrNull(): RegionRestoreApi? = apiInstance

    /** Get the API instance or throw if it is not available. */
    fun api(): RegionRestoreApi = apiInstance
        ?: error("RegionRestore API is not available (plugin not enabled?)")
}

/** Summary information about a saved template version. */
data class TemplateVersionSummary(
    val versionId: Int,
    val createdAt: Long,
    val minecraftVersion: String,
    val description: String
)

/** Template repository and creation helpers. */
interface TemplateApi {
    // Raw repository-like operations

    suspend fun listTemplates(): List<String>

    suspend fun getTemplateVersions(name: String): List<TemplateVersionSummary>?

    suspend fun saveTemplate(
        name: String,
        description: String,
        template: RegionTemplate,
        minecraftVersion: String = nms.minecraftVersion
    ): RegionTemplateVersion

    suspend fun loadTemplateVersion(name: String, versionId: Int): RegionTemplateVersion?

    suspend fun loadTemplateVersionFromDisk(name: String, versionId: Int): RegionTemplateVersion?

    suspend fun loadActiveTemplateVersion(name: String): RegionTemplateVersion?

    suspend fun setActiveVersion(name: String, versionId: Int): Boolean

    suspend fun deleteTemplate(name: String): Boolean

    /** Reload the templates index from disk, logging discovered templates. */
    suspend fun reloadIndex()

    val nms: PaperNmsAdapter

    // High-level helpers

    /**
     * Create and save a template using chunk coordinates.
     */
    suspend fun createTemplateFromChunks(
        name: String,
        description: String,
        world: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int
    ): RegionTemplateVersion

    /**
     * Create and save a template using block coordinates (converted to chunks).
     */
    suspend fun createTemplateFromBlocks(
        name: String,
        description: String,
        world: World,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int
    ): RegionTemplateVersion

    /**
     * Create and save a template using block coordinates and the default
     * description format from config (e.g. replacing <player> with the player name).
     */
    suspend fun createTemplateFromBlocksWithDefaultDescription(
        player: Player,
        name: String,
        world: World,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int
    ): RegionTemplateVersion
}

/** Notification helpers built on top of NotificationService. */
interface NotificationApi {
    val defaultNearbyRadiusBlocks: Int

    fun send(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World? = null,
        location: Location? = null,
        radiusBlocks: Int = defaultNearbyRadiusBlocks
    )

    fun sendForRegionAABB(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World? = null,
        minBlockX: Int = 0,
        maxBlockX: Int = 0,
        minBlockZ: Int = 0,
        maxBlockZ: Int = 0,
        radiusBlocks: Int = defaultNearbyRadiusBlocks
    )

    /** Build a NotificationConfig from a NotificationEventConfig and placeholders. */
    fun buildFromEventConfig(
        eventConfig: NotificationEventConfig,
        variables: Map<String, String> = emptyMap()
    ): NotificationConfig?
}

/** Restore scheduling helpers, wrapping SchedulerService. */
interface RestoreSchedulerApi {
    val defaultAnnounceTimes: List<Int>
    val defaultAudienceScope: AudienceScope

    // Raw access

    suspend fun scheduleRestore(
        job: RestoreJob,
        countdownSeconds: Int? = null,
        announcePoints: List<Int> = defaultAnnounceTimes,
        audienceScope: AudienceScope = defaultAudienceScope
    )

    fun scheduleRepeatingRestore(
        job: RestoreJob,
        intervalSeconds: Int,
        audienceScope: AudienceScope = defaultAudienceScope
    )

    fun cancelRepeatingRestore(jobId: UUID)

    fun cancelCountdown(jobId: UUID)

    fun cancelAll()

    // High-level helpers

    /**
     * Schedule a one-shot restore for an existing RegionTemplateVersion.
     * Returns the job id.
     */
    suspend fun scheduleTemplateRestoreOnce(
        templateVersion: RegionTemplateVersion,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        countdownSeconds: Int? = null,
        audienceScope: AudienceScope = defaultAudienceScope,
        announcePoints: List<Int> = defaultAnnounceTimes
    ): UUID

    /**
     * Resolve a template version by name/version and schedule a one-shot restore.
     * If versionId is null, the active version is used.
     */
    suspend fun scheduleTemplateRestoreOnce(
        templateName: String,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        versionId: Int? = null,
        countdownSeconds: Int? = null,
        audienceScope: AudienceScope = defaultAudienceScope,
        announcePoints: List<Int> = defaultAnnounceTimes
    ): UUID

    /**
     * Schedule a repeating restore for a template version.
     */
    suspend fun scheduleTemplateRepeatingRestore(
        templateName: String,
        world: World,
        originChunkX: Int,
        originChunkZ: Int,
        versionId: Int? = null,
        intervalSeconds: Int,
        audienceScope: AudienceScope = defaultAudienceScope
    ): UUID
}

/** Mass cloner helpers wrapping MassClonerService and PlacementAllocator. */
interface MassClonerApi {
    // Raw MassClonerService operations

    suspend fun saveState(): Result<Unit>

    suspend fun shutdown()

    fun getInstancesForPool(worldName: String, templateName: String): List<RegionInstance>

    fun listInstances(worldName: String? = null, instanceType: InstanceType? = null): List<RegionInstance>

    fun getInstance(instanceId: UUID): RegionInstance?

    suspend fun addManualInstance(instance: RegionInstance): Result<Unit>

    suspend fun removeInstance(instanceId: UUID): Result<Boolean>

    suspend fun triggerInstanceRestore(instance: RegionInstance)

    suspend fun triggerInstanceRestore(instanceId: UUID)

    suspend fun startInstanceTriggers(instance: RegionInstance)

    fun stopInstanceTriggers(instance: RegionInstance)

    suspend fun regeneratePools(worldNames: List<String> = emptyList()): Result<Pair<Int, Int>>

    // High-level helpers

    /**
     * Create, register and persist a new manual instance, optionally starting its triggers.
     */
    suspend fun createManualInstance(
        worldName: String,
        templateName: String,
        originChunkX: Int,
        originChunkZ: Int,
        sizeXChunks: Int,
        sizeZChunks: Int,
        versionId: Int? = null,
        config: InstanceConfig? = null,
        startTriggers: Boolean = true
    ): Result<RegionInstance>

    /**
     * Allocate positions for a pool without mutating internal state.
     *
     * This uses the same PlacementAllocator logic as MassClonerService
     * but does not register or persist the instances.
     */
    suspend fun allocateInstancesForPool(
        pool: RegionPool,
        existingInstances: List<RegionInstance>,
        template: RegionTemplate,
        neededCount: Int
    ): List<RegionInstance>
}

/** Configuration access and reload helpers. */
interface ConfigApi {
    /** Current in-memory configuration used by the plugin. */
    val current: RegionRestoreConfig

    /**
     * Reload config.conf from disk and update the plugin's configuration.
     */
    suspend fun reload(): RegionRestoreConfig
}
