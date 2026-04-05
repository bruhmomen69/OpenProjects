package bruh.regionrestore.notification

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import bruh.regionrestore.config.NotificationsConfig
import java.time.Duration
import bruh.regionrestore.config.NotificationEventConfig
import kotlin.math.sqrt

enum class AudienceScope {
    NEARBY,
    WORLD,
    SERVER
}

data class NotificationConfig(
    val message: Component,
    val title: Component? = null,
    val subtitle: Component? = null,
    val titleDurationTicks: Int = 20,
    val sound: String? = null,
    val soundVolume: Float = 1.0f,
    val soundPitch: Float = 1.0f
) {
    companion object {
        /**
         * Create a NotificationConfig from a NotificationEventConfig with variable substitution.
         *
         * @param eventConfig The event configuration from config file
         * @param variables Map of variable names to values for substitution (e.g., {"seconds" to "5"})
         * @return NotificationConfig with parsed MiniMessage and substituted variables
         */
        fun fromEventConfig(
            eventConfig: NotificationEventConfig,
            variables: Map<String, String> = emptyMap()
        ): NotificationConfig? {
            if (!eventConfig.enabled) return null

            val miniMessage = MiniMessage.miniMessage()

            // Substitute variables in message
            val message = eventConfig.message?.let { raw ->
                var substituted = raw
                variables.forEach { (key, value) ->
                    substituted = substituted.replace("<$key>", value)
                }
                miniMessage.deserialize(substituted)
            }

            // Substitute variables in title and subtitle
            val title = eventConfig.title?.let { raw ->
                var substituted = raw
                variables.forEach { (key, value) ->
                    substituted = substituted.replace("<$key>", value)
                }
                miniMessage.deserialize(substituted)
            }

            val subtitle = eventConfig.subtitle?.let { raw ->
                var substituted = raw
                variables.forEach { (key, value) ->
                    substituted = substituted.replace("<$key>", value)
                }
                miniMessage.deserialize(substituted)
            }

            return NotificationConfig(
                message = message ?: Component.empty(),
                title = title,
                subtitle = subtitle,
                titleDurationTicks = eventConfig.titleDurationTicks,
                sound = eventConfig.sound,
                soundVolume = eventConfig.soundVolume,
                soundPitch = eventConfig.soundPitch
            )
        }
    }
}

class NotificationService(
    private val plugin: SuspendingJavaPlugin,
    private val notificationsConfig: NotificationsConfig
) {

    fun sendNotification(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World? = null,
        location: Location? = null,
        radiusBlocks: Int = notificationsConfig.nearbyRadiusBlocks
    ) {
        val audience = resolveAudience(scope, world, location, radiusBlocks)

        sendToAudience(audience, config)
    }

    /**
     * Send a notification using AABB (axis-aligned bounding box) for NEARBY scope.
     * This is more accurate for large regions than center-point distance.
     *
     * @param scope The audience scope (NEARBY, WORLD, SERVER)
     * @param config The notification configuration
     * @param world The world to send notifications in (required for NEARBY and WORLD)
     * @param minBlockX Minimum block X coordinate of the region
     * @param maxBlockX Maximum block X coordinate of the region
     * @param minBlockZ Minimum block Z coordinate of the region
     * @param maxBlockZ Maximum block Z coordinate of the region
     * @param radiusBlocks Maximum distance from the bounding box for NEARBY scope
     */
    fun sendNotification(
        scope: AudienceScope,
        config: NotificationConfig,
        world: World? = null,
        minBlockX: Int = 0,
        maxBlockX: Int = 0,
        minBlockZ: Int = 0,
        maxBlockZ: Int = 0,
        radiusBlocks: Int = notificationsConfig.nearbyRadiusBlocks
    ) {
        val audience = resolveAudienceWithAABB(scope, world, minBlockX, maxBlockX, minBlockZ, maxBlockZ, radiusBlocks)

        sendToAudience(audience, config)
    }

    private fun resolveAudience(
        scope: AudienceScope,
        world: World? = null,
        location: Location? = null,
        radiusBlocks: Int = 50
    ): Audience {
        return when (scope) {
            AudienceScope.NEARBY -> {
                if (location == null || world == null) {
                    Audience.empty()
                } else {
                    Audience.audience(playersNearby(location, world, radiusBlocks))
                }
            }

            AudienceScope.WORLD -> {
                if (world == null) {
                    Audience.empty()
                } else {
                    Audience.audience(world.players)
                }
            }

            AudienceScope.SERVER -> {
                Audience.audience(Bukkit.getOnlinePlayers())
            }
        }
    }

    private fun resolveAudienceWithAABB(
        scope: AudienceScope,
        world: World? = null,
        minBlockX: Int = 0,
        maxBlockX: Int = 0,
        minBlockZ: Int = 0,
        maxBlockZ: Int = 0,
        radiusBlocks: Int = 50
    ): Audience {
        return when (scope) {
            AudienceScope.NEARBY -> {
                if (world == null) {
                    Audience.empty()
                } else {
                    Audience.audience(
                        playersNearbyAABB(
                            minBlockX,
                            maxBlockX,
                            minBlockZ,
                            maxBlockZ,
                            world,
                            radiusBlocks
                        )
                    )
                }
            }

            AudienceScope.WORLD -> {
                if (world == null) {
                    Audience.empty()
                } else {
                    Audience.audience(world.players)
                }
            }

            AudienceScope.SERVER -> {
                Audience.audience(Bukkit.getOnlinePlayers())
            }
        }
    }

    private fun playersNearby(location: Location, world: World, radiusBlocks: Int): List<Player> {
        return world.players.filter { player: Player ->
            val playerLoc = player.location
            if (playerLoc.world != world) return@filter false

            val dx = maxOf(location.x - playerLoc.x, 0.0, playerLoc.x - location.x)
            val dz = maxOf(location.z - playerLoc.z, 0.0, playerLoc.z - location.z)
            val dist = sqrt(dx * dx + dz * dz)

            dist <= radiusBlocks
        }
    }

    /**
     * Find players nearby using AABB (axis-aligned bounding box) distance calculation.
     * This is more accurate for large regions than center-point distance.
     *
     * @param minBlockX Minimum block X coordinate of the region
     * @param maxBlockX Maximum block X coordinate of the region
     * @param minBlockZ Minimum block Z coordinate of the region
     * @param maxBlockZ Maximum block Z coordinate of the region
     * @param world World to search in
     * @param radiusBlocks Maximum distance from the bounding box
     * @return List of players within the specified radius of the region
     */
    private fun playersNearbyAABB(
        minBlockX: Int,
        maxBlockX: Int,
        minBlockZ: Int,
        maxBlockZ: Int,
        world: World,
        radiusBlocks: Int
    ): List<Player> {
        return world.players.filter { player: Player ->
            val playerLoc = player.location
            if (playerLoc.world != world) return@filter false

            // Calculate horizontal distance from player to the AABB
            // dx = max(minX - playerX, 0, playerX - maxX)
            val dx = maxOf(minBlockX.toDouble() - playerLoc.x, 0.0, playerLoc.x - maxBlockX.toDouble())
            val dz = maxOf(minBlockZ.toDouble() - playerLoc.z, 0.0, playerLoc.z - maxBlockZ.toDouble())
            val dist = sqrt(dx * dx + dz * dz)

            dist <= radiusBlocks
        }
    }

    private fun sendToAudience(audience: Audience, config: NotificationConfig) {
        if (config.message != Component.empty()) {
            audience.sendMessage(config.message)
        }

        if (config.title != null || config.subtitle != null) {
            val title = Title.title(
                config.title ?: Component.empty(),
                config.subtitle ?: Component.empty(),
                Title.Times.times(
                    Duration.ofMillis(100),
                    Duration.ofMillis(config.titleDurationTicks * 50L),
                    Duration.ofMillis(500)
                )
            )
            audience.showTitle(title)
        }

        if (config.sound != null) {
            audience.playSound(
                Sound.sound()
                    .type(Key.key(config.sound))
                    .pitch(config.soundPitch)
                    .volume(config.soundVolume)
                    .build()
            )
        }
    }
}
