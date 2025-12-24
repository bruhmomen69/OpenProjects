package bruh.zchat.paper.services

import bruh.zchat.paper.config.ChannelsConfig
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.MessagesConfig
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.channel.ChannelService
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.TimeUnit

class ChannelCommandService(
    private val channelService: ChannelService,
    private val messageFormattingService: MessageFormattingService,
    private val configManager: ConfigManager,
    private val messagesConfig: MessagesConfig,
    private val plugin: JavaPlugin
) {

    data class CommandExecutionResult(
        val handled: Boolean,
        val shouldCancelEvent: Boolean = true
    )
    // Caffeine caches for performance
    val commandCache: Cache<UUID, CachedCommands> = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(1000)
        .build()

    val playerCache: Cache<UUID, CachedPlayerData> = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(1000)
        .build()

    val channelCommandCache: Cache<String, CachedChannelCommandResult> = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .maximumSize(500)
        .build()

    data class CachedCommands(
        val commands: List<String>,
        val timestamp: Long,
        val permissionsHash: Int
    )

    data class CachedPlayerData(
        val onlinePlayers: List<String>,
        val timestamp: Long
    )

    data class CachedChannelCommandResult(
        val isChannelCommand: Boolean,
        val timestamp: Long,
    )

    fun parseCommandBuffer(buffer: String): List<String> {
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

    fun isChannelCommand(command: String): Boolean {
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

    fun generateCompletions(
        player: Player,
        parts: List<String>
    ): MutableList<AsyncTabCompleteEvent.Completion> {
        return when (parts.size) {
            1 -> getChannelCommandCompletions(player, parts[0])
            2 -> {
                val secondArg = parts[1]
                if (secondArg.isEmpty()) {
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
                    getPlayerNameCompletions(player, secondArg)
                } else {
                    ArrayList()
                }
            }
            else -> {
                if (parts.size > 2) {
                    getPlayerNameCompletions(player, parts.last())
                } else ArrayList()
            }
        }
    }

    fun getChannelCommandCompletions(
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
                    val tooltip = messageFormattingService.formatMessage(
                        messagesConfig.channels.tabChannelTooltipFormat,
                        player,
                        mapOf(
                            "channel_display_name" to def.displayName,
                            "channel_name_key" to def.nameKey
                        ),
                        processUrls = false,
                        processMentions = false
                    )
                    AsyncTabCompleteEvent.Completion.completion(cmd, tooltip)
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

    fun getPlayerNameCompletions(
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
                val tooltip = messageFormattingService.formatMessage(
                    messagesConfig.channels.tabPlayerTooltipFormat,
                    player,
                    mapOf(
                        "player_name" to onlinePlayer.name,
                        "world" to onlinePlayer.location.world.name,
                        "op" to if (onlinePlayer.isOp) " [OP]" else ""
                    ),
                    processUrls = false,
                    processMentions = false
                )
                AsyncTabCompleteEvent.Completion.completion(onlinePlayer.name, tooltip)
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

    fun updateChannelsCommandAlias() {
        val allCommands = configManager.channels.channels.flatMap { it.commands }
        Bukkit.getServer().commandAliases.put("channels", allCommands.toTypedArray())
    }

    fun executeChannelCommand(player: Player, commandAlias: String, message: String): CommandExecutionResult {
        if (!configManager.channels.settings.enabled) {
            return CommandExecutionResult(handled = false, shouldCancelEvent = false)
        }

        val definition = channelService.getDefinitions().firstOrNull { def -> def.commands.contains(commandAlias.lowercase()) }
            ?: return CommandExecutionResult(handled = false, shouldCancelEvent = false)

        // Check permission before resolving instance
        if (definition.requiredPermission.isNotBlank() && !player.hasPermission(definition.requiredPermission)) {
            player.sendMessage(
                messageFormattingService.getConfigMessage(
                    MessageKey.CHANNELS_NO_PERMISSION_CHANNEL,
                    player,
                    mapOf(
                        "channel_display_name" to definition.displayName
                    )
                )
            )
            return CommandExecutionResult(handled = true, shouldCancelEvent = true)
        }

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
            return CommandExecutionResult(handled = true, shouldCancelEvent = true)
        }

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
            return CommandExecutionResult(handled = true, shouldCancelEvent = true)
        }

        channelService.forceNextMessageToChannel(player, instance, channelOnly = true)
        if (definition.autoFocusOnMessage && channelService.isMember(player, instance)) {
            channelService.setActiveInstance(player, instance)
        }
        player.chat(message)
        return CommandExecutionResult(handled = true, shouldCancelEvent = true)
    }
}
