package bruh.auctionhouse.gui

import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.AnvilInputResult
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.Menu
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.menuapi.promptText
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Menu for selecting a material when creating an order.
 * Features search, categories, and pagination.
 */
class MaterialPickerMenu(
    private val menuAPI: MenuAPI,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val onSelect: (XMaterial) -> Menu
) : bruh.zchat.utils.menuapi.PaginatedMenu<XMaterial>() {
    private val mm = MiniMessage.miniMessage()
    private var currentPage = 0
    private var searchQuery = ""
    private var selectedCategory = MaterialCategory.ALL
    private val pageSize = 28

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

    fun createMenu(player: Player, page: Int = 0): Menu {
        this.player = player
        currentPage = page
        return buildMenu()
    }

    private fun buildMenu(): Menu {
        val filteredMaterials = getFilteredMaterials()
        val totalPages = (filteredMaterials.size + pageSize - 1) / pageSize
        val pageContent = filteredMaterials.drop(currentPage * pageSize).take(pageSize)

        return this.apply {
            items.clear()
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MATERIAL_PICKER_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = pageContent

            itemRenderer = { material, _ ->
                createMaterialItem(material)
            }

            background = MenuUtils.backgroundItem()

            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }
            pageIndicatorRenderer = { current, total ->
                VItem(XMaterial.PAPER) {
                    name = Component.text("$current/$total")
                }
            }

            items[49] = createSearchButton()
            items[48] = createCategoryButton()
            items[46] = MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.Close
                }
            }
            items[52] = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ ->
                    ClickResult.Close
                }
            }
        }
    }

    private lateinit var player: Player

    private fun getFilteredMaterials(): List<XMaterial> {
        var result = allMaterials

        // Apply category filter
        result = selectedCategory.filter(result)

        // Apply search filter
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
                mm.deserialize("<yellow>Search...")
            } else {
                mm.deserialize("<yellow>Search: <white>$searchQuery")
            }
            lore = mutableListOf(
                mm.deserialize("<gray>Click to search for materials")
            )
            hideAllFlags()

            onClick { _, _ ->
                runBlocking {
                    val result = menuAPI.promptText(
                        player,
                        "Search materials",
                        searchQuery
                    )
                    when (result) {
                        is AnvilInputResult.Success -> {
                            searchQuery = result.value
                            currentPage = 0
                        }
                        is AnvilInputResult.Cancelled -> {}
                    }
                }
                ClickResult.SwitchMenu(createMenu(player, currentPage))
            }
        }
    }

    private fun createCategoryButton(): VItem {
        return VItem(selectedCategory.icon) {
            name = mm.deserialize("<yellow>Category: <white>${selectedCategory.displayName}")
            lore = mutableListOf(
                mm.deserialize("<gray>Click to cycle categories"),
                mm.deserialize("<gray>Current: ${selectedCategory.displayName}")
            )
            hideAllFlags()

            onClick { _, _ ->
                selectedCategory = MaterialCategory.entries[
                    (MaterialCategory.entries.indexOf(selectedCategory) + 1) % MaterialCategory.entries.size
                ]
                currentPage = 0
                ClickResult.SwitchMenu(createMenu(player, currentPage))
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
