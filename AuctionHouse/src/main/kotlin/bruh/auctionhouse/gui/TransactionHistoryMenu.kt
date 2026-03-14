package bruh.auctionhouse.gui

import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
import bruh.auctionhouse.translations.AuctionMessages
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.ClickResult
import bruh.zchat.utils.menuapi.PaginatedMenu
import bruh.zchat.utils.menuapi.SimpleMenu
import bruh.zchat.utils.menuapi.VItem
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Menu for viewing transaction history.
 */
class TransactionHistoryMenu(
    private val pctx: PlayerMenuContext
) : PaginatedMenu<Transaction>() {

    private var currentFilter by menuState<TransactionType?>(null)

    init {
        rows = 6
        title = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_TITLE)
        background = MenuUtils.backgroundItem()

        contentSlots = (10..16) + (19..25) + (28..34) + (37..43)

        loadingPlaceholder = MenuUtils.loadingAuctionItem()
        emptyPlaceholder = MenuUtils.emptyAuctionsItem()

        itemRenderer = { transaction, _ ->
            createTransactionItem(transaction)
        }

        previousPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
        }
        nextPageItem = VItem(XMaterial.ARROW) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
        }
        pageIndicatorRenderer = { current, total ->
            VItem(XMaterial.PAPER) {
                name = Component.text("Page $current/$total")
            }
        }

        asyncData<List<Transaction>> {
            load {
                if (!pctx.config.gui.transactionHistory.enabled) return@load emptyList()
                pctx.transactionRepository.getPlayerTransactions(
                    pctx.player.uniqueId,
                    currentFilter,
                    null,
                    null,
                    0,
                    Int.MAX_VALUE
                )
            }
            onLoaded { transactions -> dataSource = transactions }
        }
    }

    override fun populateItems() {
        items.clear()

        item(46, createFilterButton())
        item(48, createBackButton())
        item(50, createDateRangeButton())
        item(53, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
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

        val amount = if (transaction.toUuid == pctx.player.uniqueId) {
            "<green>+${MenuUtils.formatPrice(transaction.amount, pctx.economy)}"
        } else {
            "<red>-${MenuUtils.formatPrice(transaction.amount, pctx.economy)}"
        }

        val loreList = mutableListOf<Component>()

        loreList.add(pctx.mm.deserialize("<gray>Type: ${transaction.transactionType.name.replace("_", " ")}"))
        loreList.add(pctx.mm.deserialize("$color$amount"))

        val counterparty = if (transaction.toUuid == pctx.player.uniqueId) {
            transaction.fromName ?: "Unknown"
        } else {
            transaction.toName ?: "Unknown"
        }
        loreList.add(pctx.mm.deserialize("<gray>Counterparty: <white>$counterparty"))

        if (transaction.itemMaterial != null) {
            loreList.add(pctx.mm.deserialize("<gray>Item: <white>${transaction.itemMaterial}"))
        }

        val date = transaction.timestamp.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        loreList.add(pctx.mm.deserialize("<gray>Date: <white>$date"))

        loreList.add(Component.empty())
        loreList.add(pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_DETAILS))

        return VItem(material) {
            name = pctx.mm.deserialize("$color${transaction.transactionType.name.replace("_", " ")}")
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                ClickResult.SwitchMenu(TransactionDetailsMenu(pctx, transaction))
            }
        }
    }

    private fun createFilterButton(): VItem {
        val filterInfo = when (currentFilter) {
            null -> Pair(XMaterial.COMPASS, pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_ALL))
            TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL -> Pair(XMaterial.EMERALD, pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_SALES))
            TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL, TransactionType.FEE_SALE, TransactionType.FILL_FEE -> Pair(XMaterial.GOLD_INGOT, pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_FEES))
            TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN, TransactionType.ORDER_REFUND -> Pair(XMaterial.DIAMOND, pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_REFUNDS))
            else -> Pair(XMaterial.PAPER, pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_FILTER_ALL))
        }

        val (material, nameComponent) = filterInfo
        val nameText = pctx.mm.serialize(nameComponent)

        return VItem(material) {
            this.name = nameComponent
            lore(pctx.mm.deserialize("<gray>Current: <white>$nameText"))
            lore(Component.empty())
            lore(pctx.mm.deserialize("<gray>Click to cycle filters"))
            hideAllFlags()

            onClick { _, controls ->
                currentFilter = when (currentFilter) {
                    null -> TransactionType.AUCTION_SALE
                    TransactionType.AUCTION_SALE, TransactionType.ORDER_FILL -> TransactionType.FEE_LISTING
                    TransactionType.FEE_LISTING, TransactionType.FEE_ORDER_FILL, TransactionType.FEE_SALE, TransactionType.FILL_FEE -> TransactionType.REFUND
                    TransactionType.REFUND, TransactionType.AUCTION_BID_RETURN, TransactionType.ORDER_REFUND -> null
                    else -> null
                }
                controls.reloadData()
                ClickResult.Deny
            }
        }
    }

    private fun createDateRangeButton(): VItem {
        return VItem(XMaterial.CLOCK) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_HISTORY_DATE_RANGE) {
                unparsed("range", "All Time")
            }
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Click to filter by date"))
            loreList.add(pctx.mm.deserialize("<red>Not yet implemented"))
            lore = loreList
            hideAllFlags()

            onClick { _, _ ->
                pctx.player.sendMessage(pctx.translationAPI.getComponentSync(AuctionMessages.DATE_RANGE_FILTERING_SOON))
                ClickResult.Deny
            }
        }
    }

    private fun createBackButton(): VItem {
        return MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(AuctionHouseMenu(pctx))
            }
        }
    }
}

/**
 * Menu for viewing transaction details.
 */
class TransactionDetailsMenu(
    private val pctx: PlayerMenuContext,
    private val transaction: Transaction
) : SimpleMenu() {

    init {
        rows = 5
        title = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_TITLE)
        background = MenuUtils.backgroundItem()
    }

    override fun populateItems() {
        items.clear()

        // Transaction icon
        item(13, VItem(XMaterial.PAPER) {
            name = pctx.mm.deserialize("<yellow>${transaction.transactionType.name.replace("_", " ")}")
            val loreList = mutableListOf<Component>()
            loreList.add(pctx.mm.deserialize("<gray>Full transaction details"))
            lore = loreList
            hideAllFlags()
        })

        // Transaction ID
        item(10, VItem(XMaterial.BOOK) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_ID) {
                unparsed("id", transaction.id.toString())
            }
            hideAllFlags()
        })

        // Type
        item(11, VItem(XMaterial.PAPER) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_TYPE) {
                unparsed("type", transaction.transactionType.name.replace("_", " "))
            }
            hideAllFlags()
        })

        // Date
        item(12, VItem(XMaterial.CLOCK) {
            val date = transaction.timestamp.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_DATE) {
                unparsed("date", date)
            }
            hideAllFlags()
        })

        // Parties
        item(14, VItem(XMaterial.PLAYER_HEAD) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_PARTIES) {
                unparsed("from", transaction.fromName ?: "System")
                unparsed("to", transaction.toName ?: "System")
            }
            hideAllFlags()
        })

        // Amount
        item(15, VItem(XMaterial.EMERALD) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_AMOUNT) {
                unparsed("amount", MenuUtils.formatPrice(transaction.amount, pctx.economy))
            }
            hideAllFlags()
        })

        // Fee
        item(16, VItem(XMaterial.GOLD_INGOT) {
            name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_FEE) {
                unparsed("fee", MenuUtils.formatPrice(transaction.taxAmount, pctx.economy))
            }
            hideAllFlags()
        })

        // Item (if applicable)
        if (transaction.itemMaterial != null) {
            item(22, VItem(XMaterial.matchXMaterial(transaction.itemMaterial).orElse(XMaterial.STONE)) {
                name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_ITEM) {
                    unparsed("item", transaction.itemMaterial)
                }
                val loreList = mutableListOf<Component>()
                if (transaction.itemQuantity != null) {
                    loreList.add(pctx.mm.deserialize("<gray>Quantity: <white>${transaction.itemQuantity}"))
                }
                lore = loreList
                hideAllFlags()
            })
        }

        // Reference (if applicable)
        if (transaction.referenceId != null) {
            item(24, VItem(XMaterial.COMPASS) {
                name = pctx.translationAPI.getComponentSync(GuiMessages.TRANSACTION_DETAILS_REFERENCE) {
                    unparsed("ref", transaction.referenceId.toString())
                }
                hideAllFlags()
            })
        }

        // Back button
        item(40, MenuUtils.backButton(pctx.translationAPI).apply {
            onClick { _, _ ->
                ClickResult.SwitchMenu(TransactionHistoryMenu(pctx))
            }
        })

        // Close button
        item(44, MenuUtils.closeButton(pctx.translationAPI).apply {
            onClick { _, _ -> ClickResult.Close }
        })
    }
}
