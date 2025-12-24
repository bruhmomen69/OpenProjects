package bruh.zchat.paper.listeners.channel

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.ChannelCommandService
import bruh.zchat.paper.services.channel.ChannelService
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.mojang.brigadier.suggestion.Suggestion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.plugin.java.JavaPlugin

class ChannelCommandListener(
    private val channelService: ChannelService,
    private val configManager: ConfigManager,
    private val channelCommandService: ChannelCommandService,
    private val plugin: JavaPlugin,
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChannelCommand(event: PlayerCommandPreprocessEvent) {
        val raw = event.message
        if (!raw.startsWith("/")) return

        val split = raw.substring(1).split(" ", limit = 2)
        if (split.isEmpty()) return
        val alias = split[0].lowercase()
        val message = if (split.size > 1) split[1] else ""

        val result = channelCommandService.executeChannelCommand(event.player, alias, message)

        if (result.handled && result.shouldCancelEvent) {
            event.isCancelled = true
        }
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