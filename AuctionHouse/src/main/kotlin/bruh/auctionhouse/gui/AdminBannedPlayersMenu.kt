package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.PlayerBanRepository
import bruh.auctionhouse.model.PlayerBan
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Menu for managing banned players from the auction house.
 */
class AdminBannedPlayersMenu(
    private val menuAPI: MenuAPI,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player,
    private val playerBanRepository: PlayerBanRepository
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val bannedPlayers = runBlocking {
            playerBanRepository.getAllBans()
        }

        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.ADMIN_BANNED_PLAYERS)

            background = MenuUtils.backgroundItem()

            // Title
            item(4, VItem(XMaterial.WITHER_SKELETON_SKULL) {
                name = mm.deserialize("<red><bold>Banned Players")
                lore = mutableListOf(
                    mm.deserialize("<gray>Manage auction house bans"),
                    Component.empty(),
                    mm.deserialize("<gray>Current: <white>${bannedPlayers.size} players")
                )
                hideAllFlags()
            })

            // Ban player button
            item(20, VItem(XMaterial.LIME_WOOL) {
                name = mm.deserialize("<green>Ban Player")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to ban a player"),
                    Component.empty(),
                    mm.deserialize("<gray>Prevents auction house usage")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter player name to ban")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val playerName = result.value
                                val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
                                if (offlinePlayer.uniqueId == null) {
                                    player.sendMessage(mm.deserialize("<red>Player not found: $playerName"))
                                    return@runBlocking
                                }

                                val reasonResult = menuAPI.promptText(player, "Enter ban reason (optional)")
                                when (reasonResult) {
                                    is AnvilInputResult.Success -> {
                                        val ban = PlayerBan.create(
                                            playerUuid = offlinePlayer.uniqueId,
                                            playerName = playerName,
                                            banReason = reasonResult.value.ifEmpty { "No reason provided" },
                                            bannedBy = player.uniqueId,
                                            bannedByName = player.name
                                        )
                                        val success = playerBanRepository.addBan(ban)
                                        if (success) {
                                            player.sendMessage(mm.deserialize("<green>Banned $playerName from auction house"))

                                            // Cancel their auctions if configured
                                            if (config.restrictions.admin.onBanCancelAuctions) {
                                                player.sendMessage(mm.deserialize("<yellow>Their auctions will be cancelled"))
                                            }
                                        } else {
                                            player.sendMessage(mm.deserialize("<yellow>$playerName is already banned"))
                                        }
                                    }
                                    is AnvilInputResult.Cancelled -> {}
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    open()
                    ClickResult.CLOSE
                }
            })

            // Unban player button
            item(24, VItem(XMaterial.RED_WOOL) {
                name = mm.deserialize("<red>Unban Player")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to unban a player"),
                    Component.empty(),
                    mm.deserialize("<gray>Enter the player name")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter player name to unban")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val playerName = result.value
                                val existingBan = playerBanRepository.getByPlayerName(playerName)
                                if (existingBan != null) {
                                    playerBanRepository.removeBan(existingBan.playerUuid)
                                    player.sendMessage(mm.deserialize("<green>Unbanned $playerName from auction house"))
                                } else {
                                    player.sendMessage(mm.deserialize("<yellow>$playerName is not banned"))
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    open()
                    ClickResult.CLOSE
                }
            })

            // List banned players
            bannedPlayers.forEachIndexed { index, ban ->
                val slot = 10 + index
                if (slot < 54) {
                    item(slot, VItem(XMaterial.PLAYER_HEAD) {
                        name = mm.deserialize("<red>${ban.playerName}")
                        lore = mutableListOf(
                            mm.deserialize("<gray>Reason: <white>${ban.banReason}"),
                            mm.deserialize("<gray>Banned by: <white>${ban.bannedByName ?: "Unknown"}"),
                            mm.deserialize("<gray>At: <white>${java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(ban.bannedAt)}"),
                            Component.empty(),
                            mm.deserialize("<red>Click to unban")
                        )
                        hideAllFlags()

                        onClick { _, _ ->
                            runBlocking {
                                playerBanRepository.removeBan(ban.playerUuid)
                            }
                            player.sendMessage(mm.deserialize("<green>Unbanned ${ban.playerName} from auction house"))
                            open()
                            ClickResult.CLOSE
                        }
                    })
                }
            }

            // Back button
            item(49, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    AdminDashboardMenu(menuAPI, null, null, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            })

            // Close button
            item(53, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            })
        }

        menuAPI.open(menu, player)
    }
}
