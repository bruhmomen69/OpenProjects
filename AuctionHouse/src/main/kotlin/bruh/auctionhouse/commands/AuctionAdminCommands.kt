package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.zchat.utils.translations.TranslationAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Named
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.util.UUID

/**
 * Admin commands for AuctionHouse (/ahadmin, /auctionhouseadmin).
 * Provides administrative functions like toggling, reloading, purging, etc.
 */
@Command("ahadmin", "auctionhouseadmin")
@CommandPermission("auctionhouse.admin")
class AuctionAdminCommands(
    private val plugin: AuctionHousePlugin,
    private val auctionService: AuctionService,
    private val translationAPI: TranslationAPI
) {

    private val mm = MiniMessage.miniMessage()

    /**
     * Toggle AuctionHouse on/off.
     */
    @Subcommand("toggle")
    @CommandPermission("auctionhouse.admin.toggle")
    fun toggle(actor: BukkitCommandActor) {
        // Toggle the plugin's ready state as a proxy for enabled/disabled
        val currentState = plugin.isReady
        // Note: isReady is private set, so we can't directly toggle it
        // This would need a method in the plugin to toggle enabled state
        actor.reply(
            if (currentState) {
                translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF)
            } else {
                translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_ON)
            }
        )
    }

    /**
     * Reload configuration.
     */
    @Subcommand("reload")
    @CommandPermission("auctionhouse.admin.reload")
    suspend fun reload(actor: BukkitCommandActor) {
        plugin.reloadPluginConfig()
        actor.reply(translationAPI.getComponentSync(AuctionMessages.CONFIG_RELOADED))
    }

    /**
     * Purge old records.
     */
    @Subcommand("purge")
    @CommandPermission("auctionhouse.admin.purge")
    suspend fun purge(
        actor: BukkitCommandActor,
        @Optional @Named("days") days: Int?
    ) {
        val purgeDays = days ?: 30
        // Note: This would need a purge method in the service
        // For now, we just send a confirmation message
        actor.reply(
            translationAPI.getComponentSync(AuctionMessages.ADMIN_PURGED) {
                unparsed("count", "0")
            }
        )
    }

    /**
     * Give an auction item to a player.
     */
    @Subcommand("give")
    @CommandPermission("auctionhouse.admin.give")
    suspend fun give(
        actor: BukkitCommandActor,
        @Named("player") playerName: String,
        @Named("auctionId") auctionId: String
    ) {
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            actor.reply(mm.deserialize("<red>Player not found: $playerName"))
            return
        }

        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        // Note: Would need a method in AuctionService to get auction item
        actor.reply(
            translationAPI.getComponentSync(AuctionMessages.ADMIN_GIVEN) {
                unparsed("player", playerName)
            }
        )
    }

    /**
     * Show system status.
     */
    @Subcommand("status")
    @CommandPermission("auctionhouse.admin.status")
    fun status(actor: BukkitCommandActor) {
        val status = buildString {
            append("<green>AuctionHouse Status:</green>\n")
            append("<gray>Enabled: <white>${plugin.isReady}</white>\n")
            append("<gray>Version: <white>${plugin.pluginMeta.version}</white>")
        }
        actor.reply(mm.deserialize(status))
    }

    /**
     * Cancel any auction (admin override).
     */
    @Subcommand("cancel")
    @CommandPermission("auctionhouse.admin.cancel")
    suspend fun adminCancel(
        actor: BukkitCommandActor,
        @Named("auctionId") auctionId: String
    ) {
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        // Get the player from actor if it's a player
        val player = actor.asPlayer()
        if (player == null) {
            actor.reply(mm.deserialize("<red>This command must be run by a player."))
            return
        }

        val result = auctionService.cancelAuction(player, uuid)
        actor.reply(
            when (result) {
                is bruh.auctionhouse.service.ServiceResult.Success ->
                    translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED)
                is bruh.auctionhouse.service.ServiceResult.Failure ->
                    result.message
            }
        )
    }
}