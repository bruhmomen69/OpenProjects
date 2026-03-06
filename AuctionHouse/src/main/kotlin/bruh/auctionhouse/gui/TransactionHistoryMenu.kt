package bruh.auctionhouse.gui

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Menu for viewing transaction history.
 */
class TransactionHistoryMenu(
    private val menuAPI: MenuAPI,
    private val transactionRepository: TransactionRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player
) {
    private val mm = MiniMessage.miniMessage()
    private var currentFilter: TransactionType? = null
    private var currentPage = 0
    private val pageSize = config.gui.transactionHistory.transactionsPerPage

    fun open(page: Int = 0) {
        currentPage = page

        if (!config.gui.transactionHistory.enabled) {
            player.sendMessage(mm.deserialize("<red>Transaction history is currently disabled."))
            return
        }

        val transactions = runBlocking {
            val offset = page * pageSize
            transactionRepository.getPlayerTransactions(
                player.uniqueId,
                currentFilter,
                null,
                null,
                offset,
                pageSize
            )
        }

        val menu = menuAPI.paginated<Transaction> {
            rows = 6
            title = translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_TITLE)

            contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

            dataSource = transactions

            itemRenderer = { transaction, _ ->
                createTransactionItem(transaction)
            }

            // Background
            background = MenuUtils.backgroundItem()

            // Navigation
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
            staticItems[48] = createBackButton()
            staticItems[50] = createDateRangeButton()
            staticItems[53] = MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            }
        }

        menuAPI.open(menu, player)
    }

    private fun createTransactionItem(transaction: Transaction): VItem {
        val material = when (transaction.transactionType) {
            TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL -> XMaterial.EMERALD
            TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL -> XMaterial.GOLD_INGOT
            TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN -> XMaterial.DIAMOND
            else -> XMaterial.PAPER
        }

        val color = when (transaction.transactionType) {
            TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL, TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN -> "<green>"
            TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL -> "<gold>"
            else -> "<gray>"
        }

        val amount = if (transaction.toUuid == player.uniqueId) {
            "<green>+${MenuUtils.formatPrice(transaction.amount, plugin.economy)}"
        } else {
            "<red>-${MenuUtils.formatPrice(transaction.amount, plugin.economy)}"
        }

        val loreList = mutableListOf<Component>()

        // Transaction type
        loreList.add(mm.deserialize("<gray>Type: ${transaction.transactionType.name.replace("_", " ")}"))

        // Amount
        loreList.add(mm.deserialize("$color$amount"))

        // Counterparty
        val counterparty = if (transaction.toUuid == player.uniqueId) {
            transaction.fromName ?: "Unknown"
        } else {
            transaction.toName ?: "Unknown"
        }
        loreList.add(mm.deserialize("<gray>Counterparty: <white>$counterparty"))

        // Item (if applicable)
        if (transaction.itemMaterial != null) {
            loreList.add(mm.deserialize("<gray>Item: <white>${transaction.itemMaterial}"))
        }

        // Date
        val date = transaction.timestamp.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        loreList.add(mm.deserialize("<gray>Date: <white>$date"))

        // Click instruction
        loreList.add(Component.empty())
        loreList.add(translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_DETAILS))

        return VItem(material) {
            name = mm.deserialize("$color${transaction.transactionType.name.replace("_", " ")}")
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                TransactionDetailsMenu(menuAPI, transactionRepository, config, translationAPI, plugin, player, transaction).open()
                ClickResult.CLOSE
            }
        }
    }

    private fun createFilterButton(): VItem {
        val filterInfo = when (currentFilter) {
            null -> Pair(XMaterial.COMPASS, translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_ALL))
            TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL -> Pair(XMaterial.EMERALD, translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_SALES))
            TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL, TransactionType.FEE_SALE, TransactionType.FILL_FEE -> Pair(XMaterial.GOLD_INGOT, translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_FEES))
            TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN, TransactionType.ORDER_REFUND -> Pair(XMaterial.DIAMOND, translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_REFUNDS))
            else -> Pair(XMaterial.PAPER, translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_ALL))
        }

        val (material, nameComponent) = filterInfo
        val nameText = mm.serialize(nameComponent)

        return VItem(material) {
            this.name = nameComponent
            lore(mm.deserialize("<gray>Current: <white>$nameText"))
            lore(Component.empty())
            lore(mm.deserialize("<gray>Click to cycle filters"))
            hideAllFlags()

            onClick { _, _ ->
                currentFilter = when (currentFilter) {
                    null -> TransactionType.AUCTION_SALE
                    TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL -> TransactionType.FEE_LISTING
                    TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL, TransactionType.FEE_SALE, TransactionType.FILL_FEE -> TransactionType.REFUND
                    TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN, TransactionType.ORDER_REFUND -> null
                    else -> null
                }
                open(0)
                ClickResult.ALLOW
            }
        }
    }

    private fun createDateRangeButton(): VItem {
        return VItem(XMaterial.CLOCK) {
            name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_DATE_RANGE) {
                unparsed("range", "All Time")
            }
            val loreList = mutableListOf<Component>()
            loreList.add(mm.deserialize("<gray>Click to filter by date"))
            loreList.add(mm.deserialize("<red>Not yet implemented"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                player.sendMessage(mm.deserialize("<yellow>Date range filtering coming soon!"))
                ClickResult.ALLOW
            }
        }
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(translationAPI).apply {
            onClick { _, _ ->
                player.performCommand("ah")
                ClickResult.CLOSE
            }
        }
    }
}

/**
 * Menu for viewing transaction details.
 */
class TransactionDetailsMenu(
    private val menuAPI: MenuAPI,
    private val transactionRepository: TransactionRepository,
    private val config: AuctionHouseConfig,
    private val translationAPI: TranslationAPI,
    private val plugin: AuctionHousePlugin,
    private val player: Player,
    private val transaction: Transaction
) {
    private val mm = MiniMessage.miniMessage()

    fun open() {
        val menu = menuAPI.simple {
            rows = 5
            title = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_TITLE)

            background = MenuUtils.backgroundItem()

            // Transaction icon
            item(13, VItem(XMaterial.PAPER) {
                name = mm.deserialize("<yellow>${transaction.transactionType.name.replace("_", " ")}")
                val loreList = mutableListOf<Component>()
                loreList.add(mm.deserialize("<gray>Full transaction details"))
                lore = loreList
                hideAllFlags()
            })

            // Transaction ID
            item(10, VItem(XMaterial.BOOK) {
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_ID) {
                    unparsed("id", transaction.id.toString())
                }
                hideAllFlags()
            })

            // Type
            item(11, VItem(XMaterial.PAPER) {
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_TYPE) {
                    unparsed("type", transaction.transactionType.name.replace("_", " "))
                }
                hideAllFlags()
            })

            // Date
            item(12, VItem(XMaterial.CLOCK) {
                val date = transaction.timestamp.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_DATE) {
                    unparsed("date", date)
                }
                hideAllFlags()
            })

            // Parties
            item(14, VItem(XMaterial.PLAYER_HEAD) {
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_PARTIES) {
                    unparsed("from", transaction.fromName ?: "System")
                    unparsed("to", transaction.toName ?: "System")
                }
                hideAllFlags()
            })

            // Amount
            item(15, VItem(XMaterial.EMERALD) {
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_AMOUNT) {
                    unparsed("amount", MenuUtils.formatPrice(transaction.amount, plugin.economy))
                }
                hideAllFlags()
            })

            // Fee
            item(16, VItem(XMaterial.GOLD_INGOT) {
                name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_FEE) {
                    unparsed("fee", MenuUtils.formatPrice(transaction.taxAmount, plugin.economy))
                }
                hideAllFlags()
            })

            // Item (if applicable)
            if (transaction.itemMaterial != null) {
                item(22, VItem(XMaterial.matchXMaterial(transaction.itemMaterial).orElse(XMaterial.STONE)) {
                    name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_ITEM) {
                        unparsed("item", transaction.itemMaterial)
                    }
                    val loreList = mutableListOf<Component>()
                    if (transaction.itemQuantity != null) {
                        loreList.add(mm.deserialize("<gray>Quantity: <white>${transaction.itemQuantity}"))
                    }
                    lore = loreList
                    hideAllFlags()
                })
            }

            // Reference (if applicable)
            if (transaction.referenceId != null) {
                item(24, VItem(XMaterial.COMPASS) {
                    name = translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_REFERENCE) {
                        unparsed("ref", transaction.referenceId.toString())
                    }
                    hideAllFlags()
                })
            }

            // Back button
            item(40, MenuUtils.backButton(translationAPI).apply {
                onClick { _, _ ->
                    TransactionHistoryMenu(menuAPI, transactionRepository, config, translationAPI, plugin, player).open()
                    ClickResult.CLOSE
                }
            })

            // Close button
            item(44, MenuUtils.closeButton(translationAPI).apply {
                onClick { _, _ -> ClickResult.CLOSE }
            })
        }

        menuAPI.open(menu, player)
    }
}