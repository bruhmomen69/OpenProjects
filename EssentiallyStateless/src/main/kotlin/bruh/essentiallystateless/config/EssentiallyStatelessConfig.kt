package bruh.essentiallystateless.config

import bruh.commands.commonservercommands.config.CommonServerCommandsConfig
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Main configuration for EssentiallyStateless plugin.
 */
@ConfigSerializable
data class EssentiallyStatelessConfig(
    @Comment("Language for translations. Configure translations in the 'translations' folder.")
    val language: String = "en",
    
    @Comment("Default fly speed (0.0 to 1.0, vanilla default is 0.1)")
    val defaultFlySpeed: Float = 0.1f,
    
    @Comment("Default walk speed (0.0 to 1.0, vanilla default is 0.2)")
    val defaultWalkSpeed: Float = 0.2f,
    
    @Comment("Maximum fly speed allowed (0.0 to 1.0)")
    val maxFlySpeed: Float = 1.0f,
    
    @Comment("Maximum walk speed allowed (0.0 to 1.0)")
    val maxWalkSpeed: Float = 1.0f,
    
    @Comment("Default heal amount (20 = full health)")
    val defaultHealAmount: Double = 20.0,
    
    @Comment("Default feed amount (20 = full hunger)")
    val defaultFeedAmount: Int = 20,
    
    @Comment("Maximum number of entities that can be removed at once with /remove")
    val maxRemoveEntities: Int = 1000,
    
    @Comment("Maximum number of mobs that can be spawned at once with /spawnmob")
    val maxSpawnMobs: Int = 100,
    
    @Comment("Radius in blocks for /near command")
    val nearRadius: Int = 200,
    
    @Comment("Broadcast format using MiniMessage. Use <message> for the broadcast content.")
    val broadcastFormat: String = "<red>[Broadcast]</red> <message>",
    
    @Comment("World broadcast format using MiniMessage. Use <world> and <message> placeholders.")
    val worldBroadcastFormat: String = "<yellow>[<world>]</yellow> <message>",
    
    @Comment("Me action format using MiniMessage. Use <player> and <action> placeholders.")
    val meFormat: String = "<gray>* <player> <action></gray>"
)

fun EssentiallyStatelessConfig.toCommonServerCommandsConfig(): CommonServerCommandsConfig {
    return CommonServerCommandsConfig(
        language = language,
        defaultFlySpeed = defaultFlySpeed,
        defaultWalkSpeed = defaultWalkSpeed,
        maxFlySpeed = maxFlySpeed,
        maxWalkSpeed = maxWalkSpeed,
        defaultHealAmount = defaultHealAmount,
        defaultFeedAmount = defaultFeedAmount,
        maxRemoveEntities = maxRemoveEntities,
        maxSpawnMobs = maxSpawnMobs,
        nearRadius = nearRadius,
        broadcastFormat = broadcastFormat,
        worldBroadcastFormat = worldBroadcastFormat,
        meFormat = meFormat
    )
}
