package bruh.zchat.paper.listeners.channel

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.MessagesConfig
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.ChannelCommandService
import bruh.zchat.paper.services.MessageFormattingService
import bruh.zchat.paper.services.channel.ChannelService
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.mojang.brigadier.suggestion.Suggestion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.plugin.java.JavaPlugin

class ChannelCommandListener(
    private val channelService: ChannelService,
    private val messageFormattingService: MessageFormattingService,
    private val configManager: ConfigManager,
    private val messagesConfig: MessagesConfig,
    private val channelCommandService: ChannelCommandService,
    private val plugin: JavaPlugin,
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChannelCommand(event: PlayerCommandPreprocessEvent) {
        if (!configManager.channels.settings.enabled) return

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
        // Set as active channel if autoFocusOnMessage is enabled
        if (definition.autoFocusOnMessage) {
            channelService.setActiveInstance(player, instance)
        }
        event.isCancelled = true
        player.chat(message)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onCommandList(event: PlayerCommandSendEvent) {
        if (!configManager.channels.settings.enabled) return

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
        if (!configManager.channels.settings.enabled) return

        val sender = event.player

        val buffer = event.buffer
        if (!buffer.startsWith("/")) {
            return
        }

        // Parse command buffer safely
        val parts = channelCommandService.parseCommandBuffer(buffer)
        if (parts.isEmpty()) return

        val command = parts[0].lowercase()

        // Check if this is a channel command we handle
        if (!channelCommandService.isChannelCommand(command)) return

        // Generate appropriate completions
        val completions = generateCompletions(sender, parts).map {
            Suggestion(event.suggestions.range, it.suggestion())
        }
        event.suggestions.list.addAll(completions)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncTabComplete(event: AsyncTabCompleteEvent) {
        if (!configManager.channels.settings.enabled) return

        val sender = event.sender
        if (sender !is Player) return

        val buffer = event.buffer
        if (!event.isCommand || !buffer.startsWith("/")) return

        // Parse command buffer safely
        val parts = channelCommandService.parseCommandBuffer(buffer)
        if (parts.isEmpty()) return

        val command = parts[0].lowercase()

        // Check if this is a channel command we handle
        if (!channelCommandService.isChannelCommand(command)) return

        // Generate appropriate completions
        val completions = generateCompletions(sender, parts)
        completions.addAll(event.completions())

        // Set rich completions with tooltips
        event.completions(completions)
        if (completions.isNotEmpty()) {
            event.isHandled = true
        }
    }

    private fun generateCompletions(
        player: Player,
        parts: List<String>
    ): MutableList<AsyncTabCompleteEvent.Completion> {
        return if (configManager.channels.settings.forceMainThreadForTabCompletion && parts.size == 1) {
            runBlocking {
                withContext(plugin.entityDispatcher(player)) {
                    channelCommandService.generateCompletions(player, parts)
                }
            }
        } else {
            channelCommandService.generateCompletions(player, parts)
        }
    }
}