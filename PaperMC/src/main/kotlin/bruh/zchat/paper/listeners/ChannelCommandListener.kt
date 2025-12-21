package bruh.zchat.paper.listeners

import bruh.zchat.paper.services.ChannelService
import bruh.zchat.paper.services.MessageFormattingService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Handles per-channel join/send commands defined in ChannelsConfig.
 * - /alias            -> toggle join/leave channel
 * - /alias <message>  -> send this message to the channel only (one-shot)
 */
class ChannelCommandListener(
    private val channelService: ChannelService,
    private val messageFormattingService: MessageFormattingService
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChannelCommand(event: PlayerCommandPreprocessEvent) {
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
                messageFormattingService.formatMessage(
                    "<red>Unable to join channel ${definition.displayName}: identifier missing.</red>",
                    player,
                    processUrls = false,
                    processMentions = false
                )
            )
            event.isCancelled = true
            return
        }

        // No args -> toggle membership
        if (message.isBlank()) {
            val joinedBefore = channelService.isMember(player, instance)
            val success = if (joinedBefore) channelService.leaveChannel(player, instance) else channelService.joinChannel(player, definition, explicit = true)
            if (success) {
                val status = if (joinedBefore) "<red>left</red>" else "<green>joined</green>"
                player.sendMessage(
                    messageFormattingService.formatMessage(
                        "<gray>You $status channel <channel_name> [<channel_identifier>]</gray>",
                        player,
                        mapOf(
                            "channel_name" to definition.displayName,
                            "channel_identifier" to instance.identifier
                        ),
                        processUrls = false,
                        processMentions = false
                    )
                )
            } else {
                player.sendMessage(
                    messageFormattingService.formatMessage(
                        "<red>Could not ${if (joinedBefore) "leave" else "join"} channel ${definition.displayName}.</red>",
                        player,
                        processUrls = false,
                        processMentions = false
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
}
