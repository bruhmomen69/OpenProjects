package bruh.regionrestore.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import bruh.regionrestore.cloner.VersionMode
import bruh.regionrestore.notification.AudienceScope

@ConfigSerializable
data class RegionRestoreConfig(
    @Comment("Language for translations. Configure translations in the `translations` folder.")
    val language: String = "en",
    val templates: TemplatesConfig = TemplatesConfig(),
    val notifications: NotificationsConfig = NotificationsConfig(),
    val restore: RestoreConfig = RestoreConfig(),
    val massCloner: MassClonerConfig = MassClonerConfig(),
    val entityKiller: EntityKillerConfig = EntityKillerConfig()
)

@ConfigSerializable
data class TemplatesConfig(
    val defaultDescriptionFormat: String = "Created by <player>",
    val saveSourceWorldName: Boolean = true,
    val cacheTtlMinutes: Int = 10,
    val cacheMaxSize: Int = 50,
    val preloadOnBoot: Boolean = true
)

/**
 * Configuration for a single notification event.
 * Supports MiniMessage formatting and variable substitution.
 */
@ConfigSerializable
data class NotificationEventConfig(
    val enabled: Boolean = true,
    val message: String? = null,  // MiniMessage format
    val title: String? = null,
    val subtitle: String? = null,
    val titleDurationTicks: Int = 20,
    val sound: String? = null,
    val soundVolume: Float = 1.0f,
    val soundPitch: Float = 1.0f
)

@ConfigSerializable
data class NotificationsConfig(
    val nearbyRadiusBlocks: Int = 50,
    val defaultAudienceScope: AudienceScope = AudienceScope.NEARBY,
    val countdownTick: NotificationEventConfig = NotificationEventConfig(
        message = "<yellow>Restoring region in <seconds> second<seconds,s>..."
    ),
    val restoreStarted: NotificationEventConfig = NotificationEventConfig(
        message = "<yellow>Region restore started..."
    ),
    val restoreCompleted: NotificationEventConfig = NotificationEventConfig(
        message = "<green>Region restore completed!",
        title = "<green>DONE!",
        sound = "block_anvil_use"
    ),
    val restoreFailed: NotificationEventConfig = NotificationEventConfig(
        message = "<red>Region restore failed: <error>",
        sound = "entity_villager_no"
    ),
    val restoreSkipped: NotificationEventConfig = NotificationEventConfig(
        message = "<yellow>Region restore skipped: <reason>"
    )
)

@ConfigSerializable
data class RestoreConfig(
    val defaultAnnounceTimes: List<Int> = listOf(60, 30, 10, 5, 4, 3, 2, 1),
    val maxConcurrentRestores: Int = 5,
    @Comment("In %, how fast to load chunks. 50% for low TPS impact, 1000% for chunk loading at realtime speed.")
    val taskChunkLoadThrottle: Int = 300,
    @Comment("Unload chunks that were not loaded before after restoration is complete. When restoring large areas (roughly 50k chunks), without unloading, all the chunks can stay loaded for a while, causing server lag. This fixes this issue.")
    val unload: Boolean = true,
    @Comment("Instant unload chunks? If not, then we use paper's unload queue which helps reduce per-tick lag.")
    val unloadInstant: Boolean = false,
    @Comment("Relight chunks after restoring chunks? Can cause some delay before chunk update packets while light updates. This can also cause a lot of async cpu usage, and most servers do not need it.")
    val updateLight: Boolean = false,
    @Comment("Do the restore fully async? May cause server instability. `null` here means enable when compatible.")
    val asyncRestore: Boolean? = null,
    @Comment("Stream large restores as chunks are loaded? Makes the restore use more time on CPU, but it will not load all chunks at once, which provides a performance improvement for large sections.")
    val streamingRestore: Boolean = false,
    @Comment("Log restore timing information to console. Shows active time and total wall clock time for restores.")
    val logTimer: Boolean = false
)

@ConfigSerializable
data class MassClonerConfig(
    val allowOverwrite: Boolean = false,
    val vacateDelaySeconds: Int = 5,
    @Comment("Maximum Z chunk distance to search when allocating new instance positions. Prevents infinite loops in saturated worlds.")
    val allocatorMaxSearchZChunks: Int = 500_000,
    val worlds: List<MassClonerWorldConfig> = emptyList()
)

/**
 * Configuration for entity killing before restore.
 * By default, entities are blacklisted (not killed) with PLAYER already blacklisted.
 * When enabled, all entities EXCEPT blacklisted types will be killed.
 * When using whitelist mode, only listed entity types will be killed.
 */
@ConfigSerializable
data class EntityKillerConfig(
    @Comment("Enable entity killing before restore. Default: false (disabled)")
    val enabled: Boolean = false,
    @Comment("If true, the entityTypes list acts as a whitelist (only these entities are killed).\nIf false, the list acts as a blacklist (all entities except these are killed). Default: false (blacklist)")
    val whitelistMode: Boolean = false,
    @Comment("List of entity type names to whitelist or blacklist.\nIn blacklist mode (default), these entities will NOT be killed.\nIn whitelist mode, ONLY these entities will be killed.")
    val entityTypes: List<String> = listOf("PLAYER")
)

@ConfigSerializable
data class MassClonerWorldConfig(
    val name: String,
    val pools: List<MassClonerPoolConfig> = emptyList()
)

@ConfigSerializable
data class MassClonerPoolConfig(
    val templateName: String,
    val versionMode: VersionMode = VersionMode.ACTIVE,
    val pinnedVersionId: Int? = null,
    val count: Int = 1,
    val separationChunks: Int = 1,
    val restoreOnBoot: Boolean = false,
    val restoreOnVacate: Boolean = false,
    val restoreIntervalSeconds: Int? = null,
    val restoreAudienceScope: AudienceScope = AudienceScope.WORLD,
    @Comment("Override global restore.updateLight for this pool. If null, the global setting is used.")
    val updateLight: Boolean? = null
)
