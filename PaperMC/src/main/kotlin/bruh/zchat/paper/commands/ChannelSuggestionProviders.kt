package bruh.zchat.paper.commands

import bruh.zchat.paper.services.channel.ChannelService
import org.bukkit.entity.Player
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.node.ExecutionContext

object ChannelSuggestionProviders {

    private lateinit var channelService: ChannelService

    fun initialize(channelService: ChannelService) {
        this.channelService = channelService
    }

    class AvailableChannels : SuggestionProvider<BukkitCommandActor> {
        override fun getSuggestions(context: ExecutionContext<BukkitCommandActor>): List<String> {
            val actor = context.actor()
            val player = actor.asPlayer() ?: return emptyList()
            return channelService.getDefinitions()
                .filter { def ->
                    def.requiredPermission.isBlank() || player.hasPermission(def.requiredPermission)
                }
                .map { it.nameKey }
        }
    }

    class JoinedChannels : SuggestionProvider<BukkitCommandActor> {
        override fun getSuggestions(context: ExecutionContext<BukkitCommandActor>): List<String> {
            val actor = context.actor()
            val player = actor.asPlayer() ?: return emptyList()
            return channelService.getJoinedInstances(player)
                .map { it.nameKey }
        }
    }
}
