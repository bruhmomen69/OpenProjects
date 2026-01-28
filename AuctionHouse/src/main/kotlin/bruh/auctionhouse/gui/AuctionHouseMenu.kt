package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.Auction
import bruh.auctionhouse.model.AuctionFilter
import bruh.auctionhouse.model.AuctionSort
import bruh.auctionhouse.model.AuctionType
import bruh.auctionhouse.service.AuctionService
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * Main auction house browser menu with pagination, filters, and sorting.
 */
class AuctionHouseMenu(
    private val menuAPI: MenuAPI,
    private val auctionService: AuctionService,
    private val orderService: bruh.auctionhouse.service.OrderService,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val economy: EconomyProvider,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter = AuctionFilter()
    private var currentSort = AuctionSort.ENDING_SOON
    private var currentPage = 0

    fun open(page: Int = 0) {
        currentPage = page

        // Load auctions first
        val result = runBlocking {
            auctionService.getActiveAuctions(currentFilter, currentSort, page, 28)
        }

        val menu = menuAPI.paginated<Auction> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.MAIN_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = result.items

            itemRenderer = { auction, _ ->
                createAuctionItem(auction)
            }

            // Background
            background = MenuUtils.backgroundItem()

            // Navigation - these are handled automatically by PaginatedMenu
            // but we can customize the items
            previousPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            }
            nextPageItem = VItem(XMaterial.ARROW) {
                name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            }
            pageIndicatorRenderer = { current, total ->
                VItem(XMaterial.PAPER) {
                    name = Component.text("Page $current/$total")
                }
            }

            // Static control items
            staticItems[46] = createFilterButton()
            staticItems[47] = createSortButton()
            staticItems[48] = createSearchButton()
            staticItems[49] = createCreateOrderButton()
            staticItems[50] = createMyAuctionsButton()
            staticItems[51] = createCreateAuctionButton()
            staticItems[52] = createOrdersButton()
        }

        menuAPI.open(menu, player)
    }

    private fun createAuctionItem(auction: Auction): VItem {
        val material = XMaterial.matchXMaterial(auction.itemMaterial).orElse(XMaterial.STONE)

        val loreList = mutableListOf<Component>()

        // Seller
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_SELLER) {
            unparsed("seller", if (auction.isAnonymous) "Anonymous" else auction.sellerName)
        })

        // Price info based on auction type
        when (auction.auctionType) {
            AuctionType.AUCTION -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, economy))
                })
            }
            AuctionType.BIN -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                    unparsed("price", MenuUtils.formatPrice(auction.buyNowPrice ?: 0.0, plugin.economy))
                })
            }
            AuctionType.BOTH -> {
                loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BID) {
                    unparsed("price", MenuUtils.formatPrice(auction.startPrice, economy))
                })
                auction.buyNowPrice?.let { binPrice ->
                    loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIN) {
                        unparsed("price", MenuUtils.formatPrice(binPrice, economy))
                    })
                }
            }
        }

        // Bids
        if (auction.bidCount > 0) {
            loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_BIDS) {
                unparsed("count", auction.bidCount.toString())
            })
        }

        // Time left
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_TIME_LEFT) {
            unparsed("time", MenuUtils.formatTimeRemaining(auction.endsAt))
        })

        // Click instructions
        loreList.add(Component.empty())
        loreList.add(translationAPI.getComponentSync(GuiMessages.AUCTION_ITEM_CLICK_VIEW))

        return VItem(material) {
            name = auction.itemDisplayName?.let {
                mm.deserialize(it)
            } ?: Component.text(auction.itemMaterial.replace("_", " "))
            lore = loreList

            onClick { _, _ ->
                // Handle click - open auction details
                AuctionDetailsMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, economy, player, auction).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createFilterButton(): VItem {
        return VItem(XMaterial.HOPPER) {
            name = translationAPI.getComponentSync(GuiMessages.FILTER_ALL)
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through filter options
                currentFilter = when (currentFilter.auctionType) {
                    null -> currentFilter.copy(auctionType = AuctionType.AUCTION)
                    AuctionType.AUCTION -> currentFilter.copy(auctionType = AuctionType.BIN)
                    AuctionType.BIN -> currentFilter.copy(auctionType = AuctionType.BOTH)
                    AuctionType.BOTH -> currentFilter.copy(auctionType = null)
                }
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
            }
        }
    }

    private fun createSortButton(): VItem {
        return VItem(XMaterial.COMPASS) {
            name = translationAPI.getComponentSync(GuiMessages.SORT_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                // Cycle through sort options
                currentSort = when (currentSort) {
                    AuctionSort.ENDING_SOON -> AuctionSort.NEWEST
                    AuctionSort.NEWEST -> AuctionSort.PRICE_LOW
                    AuctionSort.PRICE_LOW -> AuctionSort.PRICE_HIGH
                    AuctionSort.PRICE_HIGH -> AuctionSort.MOST_BIDS
                    AuctionSort.MOST_BIDS -> AuctionSort.ENDING_SOON
                }
                // Refresh menu
                open(currentPage)
                ClickResult.ALLOW
            }
        }
    }

    private fun createSearchButton(): VItem {
        return VItem(XMaterial.OAK_SIGN) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_SEARCH)
            hideAllFlags()

            onClick { _, _ ->
                // TODO: Implement search functionality
                ClickResult.ALLOW
            }
        }
    }

    private fun createMyAuctionsButton(): VItem {
        return VItem(XMaterial.CHEST) {
            name = translationAPI.getComponentSync(GuiMessages.MY_AUCTIONS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                MyAuctionsMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createCreateAuctionButton(): VItem {
        return VItem(XMaterial.EMERALD) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_AUCTION)
            hideAllFlags()

            onClick { _, _ ->
                AuctionCreateMenu(menuAPI, auctionService, config, translationAPI, plugin, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createOrdersButton(): VItem {
        return VItem(XMaterial.BOOK) {
            name = translationAPI.getComponentSync(GuiMessages.ORDERS_TITLE)
            hideAllFlags()

            onClick { _, _ ->
                OrderBrowserMenu(menuAPI, auctionService, orderService, config, translationAPI, plugin, economy, player).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createCreateOrderButton(): VItem {
        return VItem(XMaterial.DIAMOND) {
            name = translationAPI.getComponentSync(GuiMessages.BUTTON_CREATE_ORDER)
            hideAllFlags()

            onClick { _, _ ->
                OrderCreateMenu(menuAPI, orderService, config, translationAPI, economy, plugin, player).open {
                    open(currentPage)
                }
                ClickResult.CLOSE
            }
        }
    }
}
