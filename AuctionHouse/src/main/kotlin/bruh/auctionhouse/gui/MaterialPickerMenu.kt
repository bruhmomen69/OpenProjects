package bruh.auctionhouse.gui

import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptTextAsync
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component

/**
 * Menu for selecting a material when creating an order.
 * Features search, categories, and pagination.
 */
class MaterialPickerMenu(
    private val pctx: PlayerMenuContext,
    private val onSelect: (XMaterial) -> Menu
) : PaginatedMenu<XMaterial>() {

    private var searchQuery by menuState("")
    private var selectedCategory by menuState(MaterialCategory.ALL)

    private val allMaterials: List<XMaterial> by lazy {
        XMaterial.entries.filter { material ->
            try {
                val parsed = material.parseMaterial()
                parsed != null && parsed.isItem && !parsed.isLegacy
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.name }
    }

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.MATERIAL_PICKER_TITLE)
        background = MenuUtils.backgroundItem()

        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        itemRenderer = { material, _ ->
            createMaterialItem(material)
        }

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }
        pageIndicatorRenderer = { current, total ->
            VItem(XMaterial.PAPER) {
                name = Component.text("$current/$total")
            }
        }

        dataSource = getFilteredMaterials()
    }

    override fun populateItems() {
        items.clear()
        dataSource = getFilteredMaterials()

        items[49] = createSearchButton()
        items[48] = createCategoryButton()
        items[46] = MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.Close
            }
        }
        items[52] = MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.Close
            }
        }
    }

    private fun getFilteredMaterials(): List<XMaterial> {
        var result = allMaterials

        result = selectedCategory.filter(result)

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter { material ->
                material.name.lowercase().contains(query)
            }
        }

        return result
    }

    private fun createMaterialItem(material: XMaterial): VItem {
        return VItem(material) {
            name = Component.text(material.name.replace("_", " "))
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(onSelect(material))
            }
        }
    }

    private fun createSearchButton(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = if (searchQuery.isBlank()) {
                pctx.mm.deserialize("<yellow>Search...")
            } else {
                pctx.mm.deserialize("<yellow>Search: <white>$searchQuery")
            }
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to search for materials")
            )
            hideAllFlags()

            onClick { _, _ ->
                pctx.menuAPI.promptTextAsync(
                    pctx.player,
                    "Search materials",
                    searchQuery
                ).thenAccept { result ->
                    when (result) {
                        is AnvilInputResult.Success -> {
                            searchQuery = result.value
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                    pctx.plugin.server.scheduler.runTask(pctx.plugin, Runnable {
                        pctx.menuAPI.open(this@MaterialPickerMenu, pctx.player)
                    })
                }
                ClickResult.Deny
            }
        }
    }

    private fun createCategoryButton(): VItem {
        return VItem(selectedCategory.icon) {
            name = pctx.mm.deserialize("<yellow>Category: <white>${selectedCategory.displayName}")
            lore = mutableListOf(
                pctx.mm.deserialize("<gray>Click to cycle categories"),
                pctx.mm.deserialize("<gray>Current: ${selectedCategory.displayName}")
            )
            hideAllFlags()

            onClick { _, _ ->
                selectedCategory = MaterialCategory.entries[
                    (MaterialCategory.entries.indexOf(selectedCategory) + 1) % MaterialCategory.entries.size
                ]
                ClickResult.Deny
            }
        }
    }
}

enum class MaterialCategory(
    val icon: XMaterial,
    val displayName: String,
    val filter: (List<XMaterial>) -> List<XMaterial>
) {
    ALL(XMaterial.CHEST, "All", { it }),
    BUILDING(XMaterial.BRICKS, "Building", { materials ->
        materials.filter { material ->
            try {
                val parsed = material.parseMaterial()
                parsed != null && parsed.isBlock && !parsed.name.contains("ORE", ignoreCase = true)
            } catch (e: Exception) {
                false
            }
        }
    }),
    ORES(XMaterial.DIAMOND_ORE, "Ores", { materials ->
        materials.filter { material ->
            val name = material.name.uppercase()
            name.contains("ORE") || name.contains("INGOT") || name.contains("GEM")
        }
    }),
    REDSTONE(XMaterial.REDSTONE, "Redstone", { materials ->
        materials.filter { material ->
            val name = material.name.uppercase()
            name.contains("REDSTONE") || name.contains("PISTON") || name.contains("LAMP") ||
                name.contains("TORCH") || name.contains("DUST") || name.contains("COMPARATOR") ||
                name.contains("REPEATER")
        }
    }),
    FOOD(XMaterial.APPLE, "Food", { materials ->
        materials.filter { material ->
            try {
                val parsed = material.parseMaterial()
                parsed != null && parsed.isEdible
            } catch (e: Exception) {
                false
            }
        }
    }),
    TOOLS(XMaterial.DIAMOND_PICKAXE, "Tools", { materials ->
        materials.filter { material ->
            val name = material.name.uppercase()
            name.endsWith("PICKAXE") || name.endsWith("AXE") || name.endsWith("SHOVEL") ||
                name.endsWith("HOE") || name.endsWith("SWORD")
        }
    }),
    COMBAT(XMaterial.DIAMOND_SWORD, "Combat", { materials ->
        materials.filter { material ->
            val name = material.name.uppercase()
            name.endsWith("SWORD") || name.endsWith("HELMET") || name.endsWith("CHESTPLATE") ||
                name.endsWith("LEGGINGS") || name.endsWith("BOOTS") || name.contains("BOW") ||
                name.contains("ARROW") || name.contains("CROSSBOW") || name.contains("TRIDENT")
        }
    }),
    DECORATION(XMaterial.PAINTING, "Decoration", { materials ->
        materials.filter { material ->
            val name = material.name.uppercase()
            name.contains("FLOWER") || name.contains("BANNER") || name.contains("PAINTING") ||
                name.contains("ITEM_FRAME") || name.contains("ARMOR_STAND") || name.contains("HEAD") ||
                name.contains("SKULL")
        }
    })
}
