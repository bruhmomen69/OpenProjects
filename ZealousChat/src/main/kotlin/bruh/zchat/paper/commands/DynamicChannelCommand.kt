package bruh.zchat.paper.commands

import bruh.zchat.paper.services.ChannelCommandService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DynamicChannelCommand(
    name: String,
    private val channelCommandService: ChannelCommandService
) : Command(name) {

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false

        val message = args.joinToString(" ")
        val result = channelCommandService.executeChannelCommand(sender, name, message)

        return result.handled
    }

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()

        val parts = listOf(name) + args
        val completions = channelCommandService.generateCompletions(sender, parts)
        return completions.map { it.suggestion() }
    }
}
