package bruh.auctionhouse.gui

import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.Material

/**
 * Menu for managing blacklisted materials.
 */
class AdminBlacklistMenu(
    private val pctx: PlayerMenuContext
) : SimpleMenu() {

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.ADMIN_BLACKLIST)
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        // Title
        item(4, VItem(XMaterial.BARRIER) {
            name = pctx.mm.deserialize("<red><bold>Blacklist Management")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Manage blacklisted materials"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Current: <white>${pctx.config.restrictions.blacklistedMaterials.size} items")
            )
            hideAllFlags()
        })

        // Add material button
        item(20, VItem(XMaterial.LIME_WOOL) {
            name = pctx.mm.deserialize("<green>Add Material")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to add a material"),
                Component.empty(),
                pctx.mm.deserialize("<gray>e.g., BEDROCK, COMMAND_BLOCK")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Enter material name (e.g., BEDROCK)").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val materialName = result.value.uppercase()
                            try {
                                Material.valueOf(materialName)
                                val currentList = pctx.config.restrictions.blacklistedMaterials.toMutableList()
                                if (!currentList.contains(materialName)) {
                                    currentList.add(materialName)
                                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_ADDED) {
                                        unparsed("material", materialName)
                                    })
                                } else {
                                    pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_ALREADY_EXISTS) {
                                        unparsed("material", materialName)
                                    })
                                }
                            } catch (e: IllegalArgumentException) {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_INVALID) {
                                    unparsed("material", materialName)
                                })
                            }
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@AdminBlacklistMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        })

        // Remove material button
        item(24, VItem(XMaterial.RED_WOOL) {
            name = pctx.mm.deserialize("<red>Remove Material")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to remove a material"),
                Component.empty(),
                pctx.mm.deserialize("<gray>Enter the material name")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(pctx.player, "Enter material name to remove").thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            val materialName = result.value.uppercase()
                            val currentList = pctx.config.restrictions.blacklistedMaterials.toMutableList()
                            if (currentList.contains(materialName)) {
                                currentList.remove(materialName)
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_REMOVED) {
                                    unparsed("material", materialName)
                                })
                            } else {
                                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_NOT_FOUND) {
                                    unparsed("material", materialName)
                                })
                            }
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@AdminBlacklistMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        })

        // List blacklisted materials
        val blacklistedMaterials = pctx.config.restrictions.blacklistedMaterials
        blacklistedMaterials.take(28).forEachIndexed { index, material ->
            val slot = 10 + index
            if (slot < 53) {
                item(slot, VItem(XMaterial.GRAY_WOOL) {
                    name = pctx.mm.deserialize("<gray>$material")
                    lore = mutableListOf(pctx.mm.deserialize("<red>Click to remove"))
                    hideAllFlags()

                    onClick { _, _ ->
                        val currentList = pctx.config.restrictions.blacklistedMaterials.toMutableList()
                        currentList.remove(material)
                        pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.ADMIN_BLACKLIST_REMOVED) {
                            unparsed("material", material)
                        })
                        ClickResult.Refresh
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
