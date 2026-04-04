package bruh.auctionhouse.gui

import bruh.auctionhouse.model.PlayerBan
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.time.format.DateTimeFormatter

/**
 * Menu for managing banned players from the auction house.
 */
class AdminBannedPlayersMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    private var bannedPlayers by menuState<List<PlayerBan>>(emptyList())
    private var pendingBan: PlayerBan? = null
    private var pendingUnbanName: String? = null

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_BANNED_PLAYERS)
        background = MenuUtils.backgroundItem()

        asyncData<List<PlayerBan>> {
            load { pctx.playerBanRepository.getAllBans() }
            onLoaded { bans -> bannedPlayers = bans }
        }

        onOpen = { _, controls ->
            pendingBan?.let { ban ->
                pendingBan = null
                controls.runAsync(
                    action = { pctx.playerBanRepository.addBan(ban) },
                    onSuccess = { success ->
                        if (success) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BAN_SUCCESS) {
                                unparsed("player", ban.playerName)
                            })
                            if (pctx.config.restrictions.admin.onBanCancelAuctions) {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BAN_AUCTIONS_CANCELLED))
                            }
                        } else {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BAN_ALREADY_BANNED) {
                                unparsed("player", ban.playerName)
                            })
                        }
                        controls.runAsync(
                            action = { pctx.playerBanRepository.getAllBans() },
                            onSuccess = { bans -> bannedPlayers = bans }
                        )
                    }
                )
            }
            pendingUnbanName?.let { playerName ->
                pendingUnbanName = null
                controls.runAsync(
                    action = {
                        val existingBan = pctx.playerBanRepository.getByPlayerName(playerName)
                        if (existingBan != null) {
                            pctx.playerBanRepository.removeBan(existingBan.playerUuid)
                            true
                        } else {
                            false
                        }
                    },
                    onSuccess = { found ->
                        if (found) {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_UNBAN_SUCCESS) {
                                unparsed("player", playerName)
                            })
                        } else {
                            pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_UNBAN_NOT_BANNED) {
                                unparsed("player", playerName)
                            })
                        }
                        controls.runAsync(
                            action = { pctx.playerBanRepository.getAllBans() },
                            onSuccess = { bans -> bannedPlayers = bans }
                        )
                    }
                )
            }
        }
    }

    private fun reopenMenu() {
        pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
            pctx.menuAPI.open(this@AdminBannedPlayersMenu, pctx.player)
        })
    }

    override fun populateItems() {
        items.clear()

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        // Title
        item(4, VItem(XMaterial.WITHER_SKELETON_SKULL) {
            name = pctx.mm.deserialize("<red><bold>Banned Players")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Manage auction house bans"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Current: <white>${bannedPlayers.size} players")
            )
            hideAllFlags()
        })

        // Ban player button
        item(20, VItem(XMaterial.LIME_WOOL) {
            name = pctx.mm.deserialize("<green>Ban Player")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to ban a player"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Prevents auction house usage")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Enter player name to ban").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val playerName = result.value
                            val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
                            if (offlinePlayer.uniqueId == null) {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_PLAYER_NOT_FOUND) {
                                    unparsed("player", playerName)
                                })
                                reopenMenu()
                                return@thenAccept
                            }

                            pctx.menuAPI.promptTextAsync(pctx.player, "Enter ban reason (optional)").thenAccept { reasonResult ->
                                when (reasonResult) {
                                    is AnvilInputResult.Success -> {
                                        pendingBan = PlayerBan.create(
                                            playerUuid = offlinePlayer.uniqueId,
                                            playerName = playerName,
                                            banReason = reasonResult.value.ifEmpty { "No reason provided" },
                                            bannedBy = pctx.player.uniqueId,
                                            bannedByName = pctx.player.name
                                        )
                                    }
                                    is AnvilInputResult.Cancelled -> {}
                                }
                                reopenMenu()
                            }
                        }
                        is AnvilInputResult.Cancelled -> reopenMenu()
                    }
                }
                ClickResult.Deny
            }
        })

        // Unban player button
        item(24, VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>Unban Player")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to unban a player"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Enter the player name")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Enter player name to unban").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            pendingUnbanName = result.value
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    reopenMenu()
                }
                ClickResult.Deny
            }
        })

        // List banned players
        bannedPlayers.forEachIndexed { index, ban ->
            val slot = 10 + index
            if (slot < 54) {
                item(slot, VItem(XMaterial.PLAYER_HEAD) {
                    name = pctx.mm.deserialize("<red>${ban.playerName}")
                    lore = mutableListOf(
                        pctx.mm.deserialize("<gray>Reason: <white>${ban.banReason}"),
                        pctx.mm.deserialize("<gray>Banned by: <white>${ban.bannedByName ?: "Unknown"}"),
                        pctx.mm.deserialize("<gray>At: <white>${dateFormatter.format(ban.bannedAt)}"),
                        Component.empty(),
                        pctx.mm.deserialize("<red>Click to unban")
                    )
                    hideAllFlags()

                    onClick { _, controls ->
                        controls.runAsync(
                            action = { pctx.playerBanRepository.removeBan(ban.playerUuid) },
                            onSuccess = {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_UNBAN_SUCCESS) {
                                    unparsed("player", ban.playerName)
                                })
                                controls.runAsync(
                                    action = { pctx.playerBanRepository.getAllBans() },
                                    onSuccess = { bans -> bannedPlayers = bans }
                                )
                            }
                        )
                        ClickResult.Deny
                    }
                })
            }
        }

        // Back button
        item(49, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AdminDashboardMenu(pctx))
            }
        })

        // Close button
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }
}
