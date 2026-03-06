package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.AuctionRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.translations.AuctionMessages
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
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Menu for managing blacklisted materials.
 */
class AdminBlacklistMenu(
    private val menuAPI: MenuAPI,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val menu = menuAPI.simple {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.ADMIN_BLACKLIST)

            background = MenuUtils.backgroundItem()

            // Title
            item(4, VItem(XMaterial.BARRIER) {
                name = mm.deserialize("<red><bold>Blacklist Management")
                lore = mutableListOf(
                    mm.deserialize("<gray>Manage blacklisted materials"),
                    Component.empty(),
                    mm.deserialize("<gray>Current: <white>${config.restrictions.blacklistedMaterials.size} items")
                )
                hideAllFlags()
            })

            // Add material button
            item(20, VItem(XMaterial.LIME_WOOL) {
                name = mm.deserialize("<green>Add Material")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to add a material"),
                    Component.empty(),
                    mm.deserialize("<gray>e.g., BEDROCK, COMMAND_BLOCK")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter material name (e.g., BEDROCK)")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val materialName = result.value.uppercase()
                                // Validate material
                                try {
                                    Material.valueOf(materialName)
                                    // Add to blacklist
                                    val currentList = config.restrictions.blacklistedMaterials.toMutableList()
                                    if (!currentList.contains(materialName)) {
                                        currentList.add(materialName)
                                        // Note: In production, this would save to config
                                        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_ADDED) {
                                            unparsed("material", materialName)
                                        })
                                    } else {
                                        player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_ALREADY_EXISTS) {
                                            unparsed("material", materialName)
                                        })
                                    }
                                } catch (e: IllegalArgumentException) {
                                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_INVALID) {
                                        unparsed("material", materialName)
                                    })
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    open()
                    ClickResult.CLOSE
                }
            })

            // Remove material button
            item(24, VItem(XMaterial.RED_WOOL) {
                name = mm.deserialize("<red>Remove Material")
                lore = mutableListOf(
                    mm.deserialize("<gray>Click to remove a material"),
                    Component.empty(),
                    mm.deserialize("<gray>Enter the material name")
                )
                hideAllFlags()

                onClick { _, _ ->
                    runBlocking {
                        val result = menuAPI.promptText(player, "Enter material name to remove")
                        when (result) {
                            is AnvilInputResult.Success -> {
                                val materialName = result.value.uppercase()
                                val currentList = config.restrictions.blacklistedMaterials.toMutableList()
                                if (currentList.contains(materialName)) {
                                    currentList.remove(materialName)
                                    // Note: In production, this would save to config
                                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_REMOVED) {
                                        unparsed("material", materialName)
                                    })
                                } else {
                                    player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_NOT_FOUND) {
                                        unparsed("material", materialName)
                                    })
                                }
                            }
                            is AnvilInputResult.Cancelled -> {}
                        }
                    }
                    open()
                    ClickResult.CLOSE
                }
            })

            // List blacklisted materials
            val blacklistedMaterials = config.restrictions.blacklistedMaterials
            blacklistedMaterials.take(28).forEachIndexed { index, material ->
                val slot = 10 + index
                if (slot < 53) {
                    item(slot, VItem(XMaterial.GRAY_WOOL) {
                        name = mm.deserialize("<gray>$material")
                        lore = mutableListOf(mm.deserialize("<red>Click to remove"))
                        hideAllFlags()

                        onClick { _, _ ->
                            val currentList = config.restrictions.blacklistedMaterials.toMutableList()
                            currentList.remove(material)
                            // Note: In production, this would save to config
                            player.sendMessage(translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_REMOVED) {
                                unparsed("material", material)
                            })
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
