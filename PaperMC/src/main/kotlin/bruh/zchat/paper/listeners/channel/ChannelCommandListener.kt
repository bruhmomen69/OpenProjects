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
    private val channelCommandService: ChannelCommandService,
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
}