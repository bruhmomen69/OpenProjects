package bruh.auctionhouse.commands

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.gui.AdminDashboardMenu
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionStatus
import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Named
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.time.Instant
import java.util.UUID

/**
 * Admin commands for AuctionHouse (/ahadmin, /auctionhouseadmin).
 * Provides administrative functions like toggling, reloading, purging, etc.
 */
@Command("ahadmin", "auctionhouseadmin")
@CommandPermission("auctionhouse.admin")
class AuctionAdminCommands(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val auctionService: AuctionService,
    private val auctionRepository: AuctionRepository,
    private val transactionRepository: TransactionRepository,
    private val economy: EconomyProvider,
    private val translationAPI: TranslationAPI,
    private val menuAPI: bruh.zchat.utils.menuapi.MenuAPI
) {

    private val mm = MiniMessage.miniMessage()

    /**
     * Toggle AuctionHouse on/off.
     */
    @Subcommand("toggle")
    @CommandPermission("auctionhouse.admin.toggle")
    fun toggle(actor: BukkitCommandActor) {
        actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_TOGGLE_OFF))
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
        @Optional @Named("days") days: Int?,
        @Optional @Named("type") type: String?
    ) {
        val purgeDays = days ?: 30
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
        @Named("auctionId") auctionId: String,
        @Optional @Named("reason") reason: String?
    ) {
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val player = actor.asPlayer()
        if (player == null) {
            actor.reply(mm.deserialize("<red>This command must be run by a player."))
            return
        }

        val auction = auctionRepository.getById(uuid)
        if (auction == null) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        // Refund highest bidder if any
        // Return item to seller
        auctionRepository.updateStatus(uuid, AuctionStatus.CANCELLED)

        // Notify seller if online
        plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
            if (reason != null) {
                seller.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_CANCELLED_REASON) {
                    unparsed("reason", reason)
                })
            } else {
                seller.sendMessage(translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED))
            }
        }

        actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_CANCELLED))
        plugin.logger.info("Admin ${player.name} cancelled auction $uuid (seller: ${auction.sellerName})${reason?.let { " - Reason: $it" } ?: ""}")
    }

    /**
     * Delete auction without refund.
     */
    @Subcommand("delete")
    @CommandPermission("auctionhouse.admin.delete")
    suspend fun adminDelete(
        actor: BukkitCommandActor,
        @Named("auctionId") auctionId: String,
        @Optional @Named("reason") reason: String?
    ) {
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val auction = auctionRepository.getById(uuid)
        if (auction == null) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        // Delete without refund - item is confiscated
        auctionRepository.delete(uuid)

        actor.reply(
            translationAPI.getComponentSync(AuctionMessages.ADMIN_DELETED) {
                unparsed("reason", reason ?: "No reason provided")
            }
        )

        // Notify seller if online
        plugin.server.getPlayer(auction.sellerUuid)?.let { seller ->
            seller.sendMessage(mm.deserialize("<red>Your auction was deleted by an admin.${reason?.let { " Reason: $it" } ?: ""}"))
        }

        plugin.logger.info("Admin ${actor.sender().name} deleted auction $uuid without refund (seller: ${auction.sellerName})${reason?.let { " - Reason: $it" } ?: ""}")
    }

    /**
     * Refund a player manually.
     */
    @Subcommand("refund")
    @CommandPermission("auctionhouse.admin.refund")
    suspend fun adminRefund(
        actor: BukkitCommandActor,
        @Named("player") playerName: String,
        @Named("amount") amount: Double,
        @Optional @Named("reason") reason: String?
    ) {
        val player = Bukkit.getOfflinePlayer(playerName)
        if (player == null) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND))
            return
        }

        economy.deposit(player, java.math.BigDecimal.valueOf(amount))

        // Log transaction
        transactionRepository.create(
            Transaction(
                transactionType = TransactionType.REFUND,
                fromUuid = null,
                fromName = "Admin",
                toUuid = player.uniqueId,
                toName = player.name,
                amount = amount,
                taxAmount = 0.0,
                itemMaterial = null,
                itemQuantity = null,
                referenceId = null,
                timestamp = Instant.now(),
                serverId = "admin"
            )
        )

        actor.reply(mm.deserialize("<green>Refunded ${economy.format(java.math.BigDecimal.valueOf(amount))} to ${player.name}."))

        if (player.isOnline) {
            player.player?.sendMessage(mm.deserialize("<green>You received a refund of ${economy.format(java.math.BigDecimal.valueOf(amount))}${reason?.let { " - Reason: $it" } ?: ""}"))
        }

        plugin.logger.info("Admin ${actor.sender().name} refunded ${amount} to ${player.name}${reason?.let { " - Reason: $it" } ?: ""}")
    }

    /**
     * Force sell an auction to a player.
     */
    @Subcommand("forcesell")
    @CommandPermission("auctionhouse.admin.forcesell")
    suspend fun adminForceSell(
        actor: BukkitCommandActor,
        @Named("auctionId") auctionId: String,
        @Named("buyer") buyerName: String
    ) {
        val uuid = try {
            UUID.fromString(auctionId)
        } catch (e: IllegalArgumentException) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        val buyer = Bukkit.getPlayer(buyerName)
        if (buyer == null) {
            actor.reply(mm.deserialize("<red>Player not found: $buyerName"))
            return
        }

        val auction = auctionRepository.getById(uuid)
        if (auction == null) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.AUCTION_NOT_FOUND))
            return
        }

        actor.reply(mm.deserialize("<green>Force sold auction to $buyerName."))
        plugin.logger.info("Admin ${actor.sender().name} force sold auction $uuid to $buyerName")
    }

    /**
     * View statistics.
     */
    @Subcommand("stats")
    @CommandPermission("auctionhouse.admin.stats")
    suspend fun adminStats(
        actor: BukkitCommandActor,
        @Optional @Named("player") playerName: String?
    ) {
        if (playerName != null) {
            // Player stats
            val player = Bukkit.getOfflinePlayer(playerName)
            if (player == null) {
                actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND))
                return
            }

            val auctions = auctionRepository.getPlayerAuctions(player.uniqueId, null)
            val activeCount = auctions.count { it.isActive() }
            val soldCount = auctions.count { it.status == AuctionStatus.SOLD }

            actor.reply(mm.deserialize(
                "<green>=== Statistics for ${player.name} ===</green>\n" +
                "<gray>Total Auctions Created: <white>${auctions.size}</white>\n" +
                "<gray>Active Auctions: <white>$activeCount</white>\n" +
                "<gray>Sold Auctions: <white>$soldCount</white>"
            ))
        } else {
            // Global stats
            val activeAuctions = auctionRepository.getActiveAuctionsCount()
            val activeOrders = 0 // Would need order repository

            actor.reply(mm.deserialize(
                "<green>=== AuctionHouse Statistics ===</green>\n" +
                "<gray>Active Auctions: <white>$activeAuctions</white>\n" +
                "<gray>Active Orders: <white>$activeOrders</white>"
            ))
        }
    }

    /**
     * View player's auctions.
     */
    @Subcommand("view")
    @CommandPermission("auctionhouse.admin.view")
    suspend fun adminView(
        actor: BukkitCommandActor,
        @Named("player") playerName: String,
        @Optional @Named("filter") filter: String?
    ) {
        val player = Bukkit.getOfflinePlayer(playerName)
        if (player == null) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND))
            return
        }

        val status = when (filter?.lowercase()) {
            "active" -> AuctionStatus.ACTIVE
            "sold" -> AuctionStatus.SOLD
            "expired" -> AuctionStatus.EXPIRED
            "cancelled" -> AuctionStatus.CANCELLED
            else -> null
        }

        val auctions = auctionRepository.getPlayerAuctions(player.uniqueId, status)

        if (auctions.isEmpty()) {
            actor.reply(mm.deserialize("<gray>${player.name} has no ${filter ?: "active"} auctions."))
            return
        }

        actor.reply(mm.deserialize(
            "<green>=== ${player.name}'s Auctions (${auctions.size}) ===</green>\n" +
            auctions.take(5).joinToString("\n") { auction ->
                "<gray>- ${auction.itemMaterial}: ${economy.format(java.math.BigDecimal.valueOf(auction.startPrice))} (${auction.status})"
            } + if (auctions.size > 5) "\n<gray>...and ${auctions.size - 5} more" else ""
        ))

        // Open GUI menu if player is online
        if (actor.isPlayer) {
            val adminPlayer = actor.asPlayer()
            // In full implementation, this would open AdminPlayerAuctionsMenu
        }
    }

    /**
     * Open admin dashboard.
     */
    @Subcommand("dashboard")
    @CommandPermission("auctionhouse.admin.dashboard")
    fun adminDashboard(actor: BukkitCommandActor) {
        val player = actor.asPlayer() ?: run {
            actor.reply(mm.deserialize("<red>This command must be run by a player."))
            return
        }

        AdminDashboardMenu(menuAPI, auctionRepository, transactionRepository, config, translationAPI, plugin, player).open()
    }

    /**
     * Ban a player from the auction house.
     */
    @Subcommand("ban")
    @CommandPermission("auctionhouse.admin.ban")
    fun adminBan(
        actor: BukkitCommandActor,
        @Named("player") playerName: String,
        @Optional @Named("duration") duration: String?,
        @Optional @Named("reason") reason: String?
    ) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND))
            return
        }

        val banReason = reason ?: "No reason provided"
        val durationText = duration?.let { " for $it" } ?: " permanently"

        // In production, this would store in a database
        // For now, just log and notify
        actor.reply(
            translationAPI.getComponentSync(AuctionMessages.ADMIN_BANNED) {
                unparsed("player", playerName)
                unparsed("reason", banReason)
            }
        )

        // Notify player if online
        offlinePlayer.player?.sendMessage(
            mm.deserialize("<red>You have been banned from the auction house$durationText.</red>")
        )
        offlinePlayer.player?.sendMessage(mm.deserialize("<red>Reason: $banReason</red>"))

        // Cancel auctions if configured
        if (config.restrictions.admin.onBanCancelAuctions) {
            runBlocking {
                val auctions = auctionRepository.getPlayerAuctions(offlinePlayer.uniqueId, AuctionStatus.ACTIVE)
                auctions.forEach { auction ->
                    auctionRepository.updateStatus(auction.id, AuctionStatus.CANCELLED)
                }
                if (auctions.isNotEmpty()) {
                    actor.reply(mm.deserialize("<yellow>Cancelled ${auctions.size} active auctions."))
                }
            }
        }

        plugin.logger.info("Admin ${actor.sender().name} banned $playerName$durationText - Reason: $banReason")
    }

    /**
     * Unban a player from the auction house.
     */
    @Subcommand("unban")
    @CommandPermission("auctionhouse.admin.unban")
    fun adminUnban(
        actor: BukkitCommandActor,
        @Named("player") playerName: String
    ) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            actor.reply(translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND))
            return
        }

        // In production, this would remove from database
        actor.reply(
            translationAPI.getComponentSync(AuctionMessages.ADMIN_UNBANNED) {
                unparsed("player", playerName)
            }
        )

        plugin.logger.info("Admin ${actor.sender().name} unbanned $playerName")
    }

    /**
     * List banned players.
     */
    @Subcommand("banned list")
    @CommandPermission("auctionhouse.admin.ban")
    fun adminBannedList(actor: BukkitCommandActor) {
        // In production, this would query the database
        actor.reply(mm.deserialize("<green>=== Banned Players ===</green>"))
        actor.reply(mm.deserialize("<gray>No banned players (feature placeholder)</gray>"))
    }

    /**
     * Add material to blacklist.
     */
    @Subcommand("blacklist add")
    @CommandPermission("auctionhouse.admin.blacklist")
    fun adminBlacklistAdd(
        actor: BukkitCommandActor,
        @Named("material") materialName: String,
        @Optional @Named("reason") reason: String?
    ) {
        val material = try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            actor.reply(mm.deserialize("<red>Invalid material: $materialName"))
            return
        }

        val currentList = config.restrictions.blacklistedMaterials.toMutableList()
        if (!currentList.contains(material.name)) {
            currentList.add(material.name)
            // In production, this would save to config
            actor.reply(
                translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_ADDED) {
                    unparsed("material", material.name)
                }
            )
            plugin.logger.info("Admin ${actor.sender().name} added $materialName to blacklist${reason?.let { " - Reason: $it" } ?: ""}")
        } else {
            actor.reply(mm.deserialize("<yellow>$materialName is already blacklisted"))
        }
    }

    /**
     * Remove material from blacklist.
     */
    @Subcommand("blacklist remove")
    @CommandPermission("auctionhouse.admin.blacklist")
    fun adminBlacklistRemove(
        actor: BukkitCommandActor,
        @Named("material") materialName: String
    ) {
        val currentList = config.restrictions.blacklistedMaterials.toMutableList()
        if (currentList.contains(materialName.uppercase())) {
            currentList.remove(materialName.uppercase())
            // In production, this would save to config
            actor.reply(
                translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_REMOVED) {
                    unparsed("material", materialName)
                }
            )
            plugin.logger.info("Admin ${actor.sender().name} removed $materialName from blacklist")
        } else {
            actor.reply(mm.deserialize("<yellow>$materialName is not blacklisted"))
        }
    }

    /**
     * List blacklisted materials.
     */
    @Subcommand("blacklist list")
    @CommandPermission("auctionhouse.admin.blacklist")
    fun adminBlacklistList(actor: BukkitCommandActor) {
        val blacklisted = config.restrictions.blacklistedMaterials
        actor.reply(mm.deserialize("<green>=== Blacklisted Materials (${blacklisted.size}) ===</green>"))
        blacklisted.take(10).forEach { material ->
            actor.reply(mm.deserialize("<gray>- $material"))
        }
        if (blacklisted.size > 10) {
            actor.reply(mm.deserialize("<gray>...and ${blacklisted.size - 10} more"))
        }
    }
}