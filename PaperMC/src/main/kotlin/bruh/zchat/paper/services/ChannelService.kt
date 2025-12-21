package bruh.zchat.paper.services

import bruh.zchat.paper.config.ChannelConfig
import bruh.zchat.paper.config.ChannelsConfig
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.ChannelChatFormatInstanceConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Channel definitions built from configuration.
 */
data class ChannelDefinition(
    val name: String,
    val nameKey: String,
    val displayName: String,
    val commands: List<String>,
    val allMessagesToChannel: Boolean,
    val requiredPermission: String,
    val identifierCreator: String,
    val requireIdentifierToJoin: Boolean,
    val crossServerBridge: Boolean,
    val identifierRefreshTicks: Int,
    val chatFormat: String,
    val groupFormats: List<ChannelChatFormatInstanceConfig>
)

/**
 * Unique identifier for a channel instance (definition + identifier).
 */
data class ChannelInstanceKey(val nameKey: String, val identifier: String) {
    val globalKey: String get() = "$nameKey|$identifier"
}

/**
 * Routing info for a chat event.
 */
data class ChannelRouting(
    val instance: ChannelInstanceKey?,
    val channelOnly: Boolean
)

data class PlayerChannelState(
    val joinedInstances: MutableSet<ChannelInstanceKey> = mutableSetOf(),
    var activeInstance: ChannelInstanceKey? = null,
    val resolvedIdentifiers: MutableMap<String, String> = mutableMapOf() // nameKey -> identifier
)

class ChannelService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val placeholderAPIService: PlaceholderAPIService
) {
    private val definitionsByName = ConcurrentHashMap<String, ChannelDefinition>()
    private val membersByInstance = ConcurrentHashMap<ChannelInstanceKey, MutableSet<UUID>>()
    private val playerStateByUuid = ConcurrentHashMap<UUID, PlayerChannelState>()

    init {
        rebuildDefinitions()
    }

    fun rebuildDefinitions() {
        definitionsByName.clear()
        val channelsConfig: ChannelsConfig = configManager.config.channels
        channelsConfig.channels.forEach { cfg ->
            val def = buildDefinition(cfg)
            definitionsByName[def.nameKey] = def
        }
    }

    fun getDefinitions(): Collection<ChannelDefinition> = definitionsByName.values

    fun getDefinitionByName(name: String): ChannelDefinition? = definitionsByName[normalizeName(name)]

    private fun buildDefinition(config: ChannelConfig): ChannelDefinition {
        val nameKey = normalizeName(config.name)
        return ChannelDefinition(
            name = config.name,
            nameKey = nameKey,
            displayName = config.displayName,
            commands = config.commands.map { it.lowercase(Locale.ROOT) },
            allMessagesToChannel = config.allMessagesToChannel,
            requiredPermission = config.requiredPermission,
            identifierCreator = config.identifierCreator,
            requireIdentifierToJoin = config.requireIdentifierToJoin,
            crossServerBridge = config.crossServerBridge,
            identifierRefreshTicks = config.identifierRefreshTicks,
            chatFormat = config.chatFormat,
            groupFormats = config.groupFormats
        )
    }

    fun normalizeName(name: String): String {
        return name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")
    }

    private fun normalizeIdentifier(identifier: String): String {
        var id = identifier.trim()
        if (id.isEmpty()) {
            id = "default"
        }
        return id
    }

    private fun resolveIdentifier(player: Player, definition: ChannelDefinition): String? {
        if (definition.identifierCreator.isBlank()) {
            return "default"
        }
        val raw = placeholderAPIService.parsePlaceholders(player, definition.identifierCreator)
        val normalized = normalizeIdentifier(raw)
        if (normalized.isBlank() && definition.requireIdentifierToJoin) {
            return null
        }
        return normalized.ifBlank { if (definition.requireIdentifierToJoin) null else "default" }
    }

    fun joinChannel(player: Player, definition: ChannelDefinition, explicit: Boolean = true): Boolean {
        if (definition.requiredPermission.isNotBlank() && !player.hasPermission(definition.requiredPermission)) {
            return false
        }
        val identifier = resolveIdentifier(player, definition) ?: return false
        val instance = ChannelInstanceKey(definition.nameKey, identifier)
        val state = playerStateByUuid.computeIfAbsent(player.uniqueId) { PlayerChannelState() }
        if (state.joinedInstances.contains(instance)) {
            return true
        }
        state.joinedInstances.add(instance)
        state.resolvedIdentifiers[definition.nameKey] = identifier
        membersByInstance.computeIfAbsent(instance) { ConcurrentHashMap.newKeySet() }.add(player.uniqueId)
        if (state.activeInstance == null || (explicit && definition.allMessagesToChannel)) {
            state.activeInstance = instance
        }
        return true
    }

    fun leaveChannel(player: Player, instance: ChannelInstanceKey): Boolean {
        val state = playerStateByUuid[player.uniqueId] ?: return false
        val removed = state.joinedInstances.remove(instance)
        membersByInstance[instance]?.remove(player.uniqueId)
        if (state.activeInstance == instance) {
            state.activeInstance = state.joinedInstances.firstOrNull()
        }
        return removed
    }

    fun getJoinedInstances(player: Player): Set<ChannelInstanceKey> {
        return playerStateByUuid[player.uniqueId]?.joinedInstances?.toSet() ?: emptySet()
    }

    fun setActiveInstance(player: Player, instance: ChannelInstanceKey?) {
        val state = playerStateByUuid.computeIfAbsent(player.uniqueId) { PlayerChannelState() }
        state.activeInstance = instance
    }

    fun getActiveInstance(player: Player): ChannelInstanceKey? {
        return playerStateByUuid[player.uniqueId]?.activeInstance
    }

    fun getViewersForInstance(instance: ChannelInstanceKey): Collection<Player> {
        val members = membersByInstance[instance] ?: return emptyList()
        return members.mapNotNull { uuid -> Bukkit.getPlayer(uuid) }.filter { it.isOnline }
    }

    fun isMember(player: Player, instance: ChannelInstanceKey): Boolean {
        return membersByInstance[instance]?.contains(player.uniqueId) == true
    }

    /**
     * Used by chat handlers to determine routing for the next message.
     * If a forced routing is present, it is consumed here.
     */
    fun peekRoutingForMessage(player: Player, channelOnlyToggle: Boolean): ChannelRouting {
        val state = playerStateByUuid[player.uniqueId] ?: return ChannelRouting(null, false)
        val forced = forcedRouting[player.uniqueId]
        if (forced != null) return forced

        val active = state.activeInstance ?: return ChannelRouting(null, false)
        val def = definitionsByName[active.nameKey] ?: return ChannelRouting(null, false)
        return if (def.allMessagesToChannel) {
            ChannelRouting(active, channelOnlyToggle)
        } else {
            ChannelRouting(null, false)
        }
    }

    fun consumeRoutingForMessage(player: Player, channelOnlyToggle: Boolean): ChannelRouting {
        val state = playerStateByUuid[player.uniqueId] ?: return ChannelRouting(null, false)
        val forced = forcedRouting.remove(player.uniqueId)
        if (forced != null) return forced

        val active = state.activeInstance ?: return ChannelRouting(null, false)
        val def = definitionsByName[active.nameKey] ?: return ChannelRouting(null, false)
        return if (def.allMessagesToChannel) {
            ChannelRouting(active, channelOnlyToggle)
        } else {
            ChannelRouting(null, false)
        }
    }

    private val forcedRouting = ConcurrentHashMap<UUID, ChannelRouting>()

    fun forceNextMessageToChannel(player: Player, instance: ChannelInstanceKey, channelOnly: Boolean) {
        forcedRouting[player.uniqueId] = ChannelRouting(instance, channelOnly)
    }

    fun handlePlayerQuit(player: Player) {
        val state = playerStateByUuid.remove(player.uniqueId) ?: return
        state.joinedInstances.forEach { inst ->
            membersByInstance[inst]?.remove(player.uniqueId)
        }
        forcedRouting.remove(player.uniqueId)
    }

    /**
     * Auto join channels for a player on login based on configuration order.
     */
    fun handlePlayerJoin(player: Player) {
        val channelsConfig = configManager.config.channels
        if (!channelsConfig.enabled) return

        val state = playerStateByUuid.computeIfAbsent(player.uniqueId) { PlayerChannelState() }
        var firstJoined: ChannelInstanceKey? = null
        var firstAllMessages: ChannelInstanceKey? = null

        for (cfg in channelsConfig.channels) {
            val def = definitionsByName[normalizeName(cfg.name)] ?: continue
            val instance = resolveInstanceForPlayer(player, def) ?: continue
            if (def.requiredPermission.isNotBlank() && !player.hasPermission(def.requiredPermission)) {
                continue
            }
            val joined = joinChannel(player, def, explicit = false)
            if (joined) {
                if (firstJoined == null) firstJoined = instance
                if (def.allMessagesToChannel && firstAllMessages == null) {
                    firstAllMessages = instance
                }
                if (!channelsConfig.autoJoin.multiple) {
                    break
                }
            }
        }

        if (state.activeInstance == null) {
            state.activeInstance = firstAllMessages ?: firstJoined
        }
    }

    fun getInstancesForDefinition(nameKey: String): List<ChannelInstanceKey> {
        return membersByInstance.keys.filter { it.nameKey == nameKey }
    }

    /**
     * Refresh identifiers for players in the specified definition. Intended to be called from a scheduler.
     */
    fun refreshIdentifiersForDefinition(definition: ChannelDefinition) {
        val nameKey = definition.nameKey
        val onlinePlayers = Bukkit.getOnlinePlayers()
        for (player in onlinePlayers) {
            val state = playerStateByUuid[player.uniqueId] ?: continue
            val currentId = state.resolvedIdentifiers[nameKey] ?: continue
            val newId = resolveIdentifier(player, definition) ?: continue
            if (newId == currentId) continue
            val oldInstance = ChannelInstanceKey(nameKey, currentId)
            val newInstance = ChannelInstanceKey(nameKey, newId)

            // Update membership
            state.joinedInstances.remove(oldInstance)
            membersByInstance[oldInstance]?.remove(player.uniqueId)

            state.joinedInstances.add(newInstance)
            membersByInstance.computeIfAbsent(newInstance) { ConcurrentHashMap.newKeySet() }.add(player.uniqueId)
            state.resolvedIdentifiers[nameKey] = newId

            if (state.activeInstance == oldInstance) {
                state.activeInstance = newInstance
            }
        }
    }

    /**
     * Compute an instance for a given player/definition without joining.
     */
    fun resolveInstanceForPlayer(player: Player, definition: ChannelDefinition): ChannelInstanceKey? {
        val identifier = resolveIdentifier(player, definition) ?: return null
        return ChannelInstanceKey(definition.nameKey, identifier)
    }
}
