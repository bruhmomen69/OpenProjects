package bruh.zchat.paper.listeners.channel

import bruh.zchat.paper.config.ChannelsConfig
import bruh.zchat.paper.config.MessagesConfig
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.ChannelService
import bruh.zchat.paper.services.MessageFormattingService
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.mojang.brigadier.suggestion.Suggestion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Handles per-channel join/send commands defined in ChannelsConfig.
 * - /alias            -> toggle join/leave channel
 * - /alias <message>  -> send this message to the channel only (one-shot)
 *
 * Now includes async tab completion with caching and rich tooltips.
 */
class ChannelCommandListener(
    private val channelService: ChannelService,
    private val messageFormattingService: MessageFormattingService,
    private val channelsConfig: ChannelsConfig,
    private val messagesConfig: MessagesConfig,
    private val plugin: JavaPlugin,
) : Listener {

    // Caffeine caches for performance
    private val commandCache: Cache<UUID, CachedCommands> = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(1000)
        .build()

    private val playerCache: Cache<UUID, CachedPlayerData> = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(1000)
        .build()

    private val channelCommandCache: Cache<String, CachedChannelCommandResult> = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .maximumSize(500)
        .build()

    private data class CachedCommands(
        val commands: List<String>,
        val timestamp: Long,
        val permissionsHash: Int
    )

    private data class CachedPlayerData(
        val onlinePlayers: List<String>,
        val timestamp: Long
    )

    private data class CachedChannelCommandResult(
        val isChannelCommand: Boolean,
        val timestamp: Long,
    )

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChannelCommand(event: PlayerCommandPreprocessEvent) {
        if (!channelsConfig.enabled) return

        val raw = event.message
        if (!raw.startsWith("/")) return

        val split = raw.substring(1).split(" ", limit = 2)
        if (split.isEmpty()) return
        val alias = split[0].lowercase()
        val message = if (split.size > 1) split[1] else ""

        val definition = channelService.getDefinitions().firstOrNull { def -> def.commands.contains(alias) } ?: return
        val player = event.player

        val instance = channelService.resolveInstanceForPlayer(player, definition)
        if (instance == null) {
            player.sendMessage(
                messageFormattingService.getConfigMessage(
                    MessageKey.CHANNELS_IDENTIFIER_MISSING,
                    player,
                    mapOf(
                        "channel_display_name" to definition.displayName
                    )
                )
            )
            event.isCancelled = true
            return
        }

        // No args -> toggle membership
        if (message.isBlank()) {
            val joinedBefore = channelService.isMember(player, instance)
            val success =
                if (joinedBefore) channelService.leaveChannel(player, instance) else channelService.joinChannel(
                    player,
                    definition,
                    explicit = true
                )
            if (success) {
                val messageKey =
                    if (joinedBefore) MessageKey.CHANNELS_CHANNEL_LEFT_TOGGLE else MessageKey.CHANNELS_CHANNEL_JOINED_TOGGLE
                val channelDisplayName = messageFormattingService.formatMessage(
                    definition.displayName,
                    player,
                    processUrls = false,
                    processMentions = false
                )
                player.sendMessage(
                    messageFormattingService.formatMessageComponent(
                        messageFormattingService.getMessageByKey(messagesConfig, messageKey)!!,
                        player,
                        mapOf(
                            "channel_name" to channelDisplayName,
                            "channel_display_name" to channelDisplayName,
                            "channel_identifier" to Component.text(instance.identifier)
                        ),
                        processUrls = false,
                        processMentions = false
                    )
                )
            } else {
                val messageKey =
                    if (joinedBefore) MessageKey.CHANNELS_CHANNEL_TOGGLE_LEAVE_FAILED else MessageKey.CHANNELS_CHANNEL_TOGGLE_JOIN_FAILED
                player.sendMessage(
                    messageFormattingService.getConfigMessage(
                        messageKey,
                        player,
                        mapOf("channel_display_name" to definition.displayName)
                    )
                )
            }
            event.isCancelled = true
            return
        }

        // Message provided -> force one-shot channel-only route
        channelService.forceNextMessageToChannel(player, instance, channelOnly = true)
        event.isCancelled = true
        player.chat(message)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onCommandList(event: PlayerCommandSendEvent) {
        if (!channelsConfig.enabled) return

        channelService
            .getDefinitions()
            .filter { it.requiredPermission.isBlank() || event.player.hasPermission(it.requiredPermission) }
            .forEach { definition ->
                definition.commands.forEach { cmdStr ->
                    event.commands.add(cmdStr)
                }
            }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onCommandList(event: AsyncPlayerSendSuggestionsEvent) {
        if (!channelsConfig.enabled) return
        
        val sender = event.player

        val buffer = event.buffer
        if (!buffer.startsWith("/")) {
            return
        }

        // Parse command buffer safely
        val parts = parseCommandBuffer(buffer)
        if (parts.isEmpty()) return

        val command = parts[0].lowercase()

        // Check if this is a channel command we handle
        if (!isChannelCommand(command)) return

        // Generate appropriate completions
        val completions = generateCompletions(sender, parts).map {
            Suggestion(event.suggestions.range, it.suggestion())
        }
        event.suggestions.list.addAll(completions)
    }

    private fun parseCommandBuffer(buffer: String): List<String> {
        return try {
            val parts = buffer.substring(1).split(" ")
            // Preserve trailing empty string to detect trailing space (user ready for next argument)
            if (parts.lastOrNull()?.isEmpty() == true) {
                parts.dropLast(1).filter { it.isNotEmpty() } + ""
            } else {
                parts.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            plugin.slF4JLogger.warn("Failed to parse command buffer: ", e)
            emptyList()
        }
    }

    private fun isChannelCommand(command: String): Boolean {
        // Check cache first
        val cached = channelCommandCache.getIfPresent(command)

        if (cached != null) {
            return cached.isChannelCommand
        }

        // Compute fresh result
        val result = channelService.getDefinitions()
            .any { def ->
                def.commands.any {
                    it.startsWith(command)
                }
            }

        // Update cache
        channelCommandCache.put(
            command, CachedChannelCommandResult(
                isChannelCommand = result,
                timestamp = System.currentTimeMillis(),
            )
        )

        return result
    }

    private fun generateCompletions(
        player: Player,
        parts: List<String>
    ): MutableList<AsyncTabCompleteEvent.Completion> {
        return when (parts.size) {
            1 -> {
                // User is typing the command name itself
                if (channelsConfig.forceMainThreadForTabCompletion) {
                    runBlocking {
                        withContext(plugin.entityDispatcher(player)) {
                            getChannelCommandCompletions(player, parts[0])
                        }
                    }
                } else {
                    getChannelCommandCompletions(player, parts[0])
                }
            }

            2 -> {
                val secondArg = parts[1]
                if (secondArg.isEmpty()) {
                    // Empty argument - show available options
                    mutableListOf(
                        AsyncTabCompleteEvent.Completion.completion(
                            "<message>",
                            messageFormattingService.getConfigMessage(MessageKey.CHANNELS_TAB_MESSAGE_TOOLTIP, player)
                        ),
                        AsyncTabCompleteEvent.Completion.completion(
                            "toggle",
                            messageFormattingService.getConfigMessage(MessageKey.CHANNELS_TAB_TOGGLE_TOOLTIP, player)
                        )
                    )
                } else if (secondArg.startsWith("<") || secondArg.length > 2) {
                    // Likely typing a message - suggest player names
                    getPlayerNameCompletions(player, secondArg)
                } else {
                    ArrayList()
                }
            }

            else -> {
                // More arguments - continue suggesting player names for message content
                if (parts.size > 2) {
                    getPlayerNameCompletions(player, parts.last())
                } else ArrayList()
            }
        }
    }

    private fun getChannelCommandCompletions(
        player: Player,
        partial: String
    ): MutableList<AsyncTabCompleteEvent.Completion> {
        val playerId = player.uniqueId

        // Check cache first
        val cached = commandCache.getIfPresent(playerId)
        val permissionsHash = channelService.getDefinitions().hashCode()

        if (cached != null && cached.timestamp > System.currentTimeMillis() - 30000 && cached.permissionsHash == permissionsHash) {
            return cached.commands
                .filter { it.startsWith(partial) }
                .map { cmd ->
                    AsyncTabCompleteEvent.Completion.completion(
                        cmd,
                        messageFormattingService.getConfigMessage(MessageKey.CHANNELS_TAB_CHANNEL_TOOLTIP, player)
                    )
                }
                .toMutableList()
        }

        // Compute fresh results
        val completions = channelService.getDefinitions()
            .filter { def ->
                val hasPermission = def.requiredPermission.isBlank() || player.hasPermission(def.requiredPermission)
                val canCreateInstance = channelService.resolveInstanceForPlayer(player, def) != null
                hasPermission && canCreateInstance
            }
            .flatMap { def ->
                def.commands.map { cmd ->
                    AsyncTabCompleteEvent.Completion.completion(
                        cmd, Component.text()
                            .append(Component.text(def.displayName, NamedTextColor.GREEN))
                            .append(Component.text(" - ${def.nameKey}", NamedTextColor.GRAY))
                            .build()
                    )
                }
            }
            .filter { completion -> completion.suggestion().startsWith(partial) }
            .sortedBy { it.suggestion() }

        // Update cache
        commandCache.put(
            playerId, CachedCommands(
                commands = completions.map { it.suggestion() },
                timestamp = System.currentTimeMillis(),
                permissionsHash = permissionsHash
            )
        )

        return completions.toMutableList()
    }

    private fun getPlayerNameCompletions(
        player: Player,
        partial: String
    ): MutableList<AsyncTabCompleteEvent.Completion> {
        val playerId = player.uniqueId

        // Check cache first
        val cached = playerCache.getIfPresent(playerId)

        if (cached != null && cached.timestamp > System.currentTimeMillis() - 10000) {
            return cached.onlinePlayers
                .filter { it.lowercase().startsWith(partial.lowercase()) }
                .map { name ->
                    AsyncTabCompleteEvent.Completion.completion(
                        name,
                        messageFormattingService.getConfigMessage(MessageKey.CHANNELS_TAB_PLAYER_TOOLTIP, player)
                    )
                }
                .toMutableList()
        }

        // Compute fresh results
        val completions = player.server.onlinePlayers
            .filter { other -> player.canSee(other) }
            .map { onlinePlayer ->
                AsyncTabCompleteEvent.Completion.completion(
                    onlinePlayer.name, Component.text()
                        .append(Component.text(onlinePlayer.name, NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(onlinePlayer.location.world.name, NamedTextColor.AQUA))
                        .append(
                            if (onlinePlayer.isOp) Component.text(
                                " [OP]",
                                NamedTextColor.RED
                            ) else Component.empty()
                        )
                        .build()
                )
            }
            .filter { completion -> completion.suggestion().lowercase().startsWith(partial.lowercase()) }
            .sortedBy { it.suggestion() }

        // Update cache
        playerCache.put(
            playerId, CachedPlayerData(
                onlinePlayers = completions.map { it.suggestion() },
                timestamp = System.currentTimeMillis()
            )
        )

        return completions.toMutableList()
    }
}