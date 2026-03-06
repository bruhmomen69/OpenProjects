package bruh.auctionhouse.service

import bruh.auctionhouse.AuctionHousePlugin
import bruh.auctionhouse.config.AuctionHouseConfig
import bruh.auctionhouse.config.FeeConfig
import bruh.auctionhouse.database.ExpiredItemRepository
import bruh.auctionhouse.database.OrderFillRepository
import bruh.auctionhouse.database.OrderRepository
import bruh.auctionhouse.database.TransactionRepository
import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.model.ExpiredItem
import bruh.auctionhouse.model.ExpiredItemType
import bruh.auctionhouse.model.Order
import bruh.auctionhouse.model.OrderFill
import bruh.auctionhouse.model.OrderFilter
import bruh.auctionhouse.model.OrderSort
import bruh.auctionhouse.model.OrderStatus
import bruh.auctionhouse.model.OrderType
import bruh.auctionhouse.model.Transaction
import bruh.auctionhouse.model.TransactionType
import bruh.auctionhouse.translations.OrderMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Service layer for order business logic.
 * Handles creation, fulfillment, and management of buy/sell orders.
 */
class OrderService(
    private val plugin: AuctionHousePlugin,
    private val config: AuctionHouseConfig,
    private val orderRepository: OrderRepository,
    private val orderFillRepository: OrderFillRepository,
    private val expiredItemRepository: ExpiredItemRepository,
    private val expiredItemManager: ExpiredItemManager,
    private val transactionRepository: TransactionRepository,
    private val economy: EconomyProvider,
    private val translationAPI: TranslationAPI,
    private val serverId: String
) {
    private val mm = MiniMessage.miniMessage()
    private val logger = plugin.slF4JLogger

    /**
     * Validates if an item matches an order's requirements based on config settings.
     */
    private fun itemMatchesOrder(item: ItemStack, order: Order): ItemMatchResult {
        // Check material type
        if (item.type != order.itemMaterial) {
            return ItemMatchResult.MaterialMismatch
        }

        // Check display name if order has display name filter
        if (order.itemDisplayName != null) {
            val itemDisplayName = item.itemMeta?.displayName()?.let { mm.serialize(it) }
            if (order.itemDisplayName != itemDisplayName) {
                return ItemMatchResult.NameMismatch
            }
        }

        // Check NBT if order has NBT hash (user specified exact NBT matching)
        if (order.itemNbtHash != null) {
            val itemNbtHash = computeItemNbtHash(item)
            if (order.itemNbtHash != itemNbtHash) {
                return ItemMatchResult.NbtMismatch
            }
        }

        // Check lore if order has lore hash (user specified exact lore matching)
        if (order.itemLoreHash != null) {
            val itemLoreHash = computeItemLoreHash(item)
            if (order.itemLoreHash != itemLoreHash) {
                return ItemMatchResult.LoreMismatch
            }
        }

        return ItemMatchResult.Match
    }

    /**
     * Computes a hash of an item's NBT data for comparison.
     */
    private fun computeItemNbtHash(item: ItemStack): String {
        // Simple hash based on item meta properties
        val meta = item.itemMeta ?: return ""
        val sb = StringBuilder()
        
        // Add enchantments
        meta.enchants.forEach { (enchant, level) ->
            sb.append(enchant.key.key).append(":").append(level).append(";")
        }
        
        // Add custom model data
        if (meta.hasCustomModelData()) {
            sb.append("cmd:").append(meta.customModelData).append(";")
        }
        
        // Add item flags
        meta.itemFlags.forEach { flag ->
            sb.append("flag:").append(flag.name).append(";")
        }
        
        return sb.toString().hashCode().toString()
    }

    /**
     * Computes a hash of an item's lore for comparison.
     */
    private fun computeItemLoreHash(item: ItemStack): String {
        val meta = item.itemMeta ?: return ""
        val lore = meta.lore ?: return ""
        return lore.joinToString("|").hashCode().toString()
    }

    /**
     * Creates a new buy order.
     *
     * @param creator The player creating the order
     * @param material The material being requested
     * @param displayName Optional display name filter for matching items
     * @param quantity The quantity requested
     * @param pricePerUnit The price per unit
     * @param allowPartial Whether partial fills are allowed
     * @param minFillQuantity Minimum quantity for partial fills
     * @param duration How long the order will be active
     * @param requireExactNbt Whether to require exact NBT match
     * @param requireExactLore Whether to require exact lore match
     * @return The result of the creation attempt
     */
    suspend fun createBuyOrder(
        creator: Player,
        material: Material,
        displayName: String?,
        quantity: Int,
        pricePerUnit: Double,
        allowPartial: Boolean,
        minFillQuantity: Int?,
        duration: Duration,
        requireExactNbt: Boolean = false,
        requireExactLore: Boolean = false
    ): CreateOrderResult = withContext(Dispatchers.IO) {
        if (!config.orders.enabled) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED)
            )
        }

        // Validate quantity
        if (quantity < config.orders.minQuantity || quantity > config.orders.maxQuantity) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_QUANTITY) {
                    unparsed("min", config.orders.minQuantity.toString())
                    unparsed("max", config.orders.maxQuantity.toString())
                }
            )
        }

        // Validate price
        if (pricePerUnit < config.orders.minPricePerUnit || pricePerUnit > config.orders.maxPricePerUnit) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_PRICE) {
                    unparsed("min", economy.format(BigDecimal.valueOf(config.orders.minPricePerUnit)))
                    unparsed("max", economy.format(BigDecimal.valueOf(config.orders.maxPricePerUnit)))
                }
            )
        }

        // Check concurrent orders
        val activeCount = orderRepository.countPlayerOrders(creator.uniqueId, OrderStatus.PENDING)
        if (activeCount >= config.orders.maxConcurrentOrders) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_MAX_REACHED) {
                    unparsed("max", config.orders.maxConcurrentOrders.toString())
                }
            )
        }

        // Calculate total cost
        val totalCost = quantity * pricePerUnit
        val listingFee = calculateFee(totalCost, config.orders.listingFee)
        val totalRequired = totalCost + listingFee

        // Check balance
        if (!economy.has(creator, BigDecimal.valueOf(totalRequired))) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponent(OrderMessages.ORDER_INSUFFICIENT_FUNDS) {
                    unparsed("amount", economy.format(BigDecimal.valueOf(totalRequired)))
                }
            )
        }

        // Charge fees
        economy.withdraw(creator, BigDecimal.valueOf(totalRequired))

        // For buy orders with NBT/lore matching, we need a sample item to compute hashes
        // The user should hold a sample item if they want exact matching
        var itemLoreHash: String? = null
        var itemNbtHash: String? = null
        var itemStack: ItemStack? = null
        
        if (requireExactNbt || requireExactLore) {
            // Get sample item from player's hand for hash computation
            val sampleItem = creator.inventory.itemInMainHand
            if (sampleItem.type == material && !sampleItem.type.isAir) {
                if (requireExactNbt) {
                    itemNbtHash = computeItemNbtHash(sampleItem)
                }
                if (requireExactLore) {
                    itemLoreHash = computeItemLoreHash(sampleItem)
                }
                // Store a copy of the item for reference
                itemStack = sampleItem.clone()
                itemStack.amount = 1
            }
        }

        // Create order
        val order = Order(
            id = UUID.randomUUID(),
            creatorUuid = creator.uniqueId,
            creatorName = creator.name,
            orderType = OrderType.BUY_ORDER,
            itemMaterial = material,
            itemDisplayName = displayName,
            itemLoreHash = itemLoreHash,
            itemNbtHash = itemNbtHash,
            itemStack = itemStack,
            quantityRequested = quantity,
            quantityFilled = 0,
            pricePerUnit = pricePerUnit,
            totalPrice = totalCost,
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(duration),
            allowPartial = allowPartial,
            minFillQuantity = minFillQuantity
        )

        orderRepository.create(order)

        CreateOrderResult(
            true, order, listingFee,
            translationAPI.getComponentSync(OrderMessages.ORDER_CREATED)
        )
    }

    /**
     * Creates a new sell order.
     *
     * @param creator The player creating the order
     * @param item The item being sold
     * @param pricePerUnit The price per unit
     * @param duration How long the order will be active
     * @return The result of the creation attempt
     */
    suspend fun createSellOrder(
        creator: Player,
        item: ItemStack,
        pricePerUnit: Double,
        duration: Duration
    ): CreateOrderResult = withContext(Dispatchers.IO) {
        if (!config.orders.enabled) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_SYSTEM_DISABLED)
            )
        }

        val quantity = item.amount

        // Validate price
        if (pricePerUnit < config.orders.minPricePerUnit || pricePerUnit > config.orders.maxPricePerUnit) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_PRICE) {
                    unparsed("min", economy.format(BigDecimal.valueOf(config.orders.minPricePerUnit)))
                    unparsed("max", economy.format(BigDecimal.valueOf(config.orders.maxPricePerUnit)))
                }
            )
        }

        // Check concurrent orders
        val activeCount = orderRepository.countPlayerOrders(creator.uniqueId, OrderStatus.PENDING)
        if (activeCount >= config.orders.maxConcurrentOrders) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_MAX_REACHED) {
                    unparsed("max", config.orders.maxConcurrentOrders.toString())
                }
            )
        }

        val listingFee = calculateFee(quantity * pricePerUnit, config.orders.listingFee)

        // Charge listing fee
        if (listingFee > 0 && !economy.has(creator, BigDecimal.valueOf(listingFee))) {
            return@withContext CreateOrderResult(
                false, null, 0.0,
                translationAPI.getComponent(OrderMessages.ORDER_INSUFFICIENT_FUNDS_LISTING) {
                    unparsed("amount", economy.format(BigDecimal.valueOf(listingFee)))
                }
            )
        }

        if (listingFee > 0) {
            economy.withdraw(creator, BigDecimal.valueOf(listingFee))
        }

        // Remove items from inventory
        withContext(plugin.entityDispatcher(creator)) {
            creator.inventory.removeItem(item)
        }

        // Create order
        val order = Order(
            id = UUID.randomUUID(),
            creatorUuid = creator.uniqueId,
            creatorName = creator.name,
            orderType = OrderType.SELL_ORDER,
            itemMaterial = item.type,
            itemDisplayName = run {
                val meta = item.itemMeta
                if (meta != null && meta.hasDisplayName()) {
                    meta.displayName()?.let { mm.serialize(it) }
                } else {
                    null
                }
            },
            itemLoreHash = null,
            itemNbtHash = null,
            itemStack = item.clone(),
            quantityRequested = quantity,
            quantityFilled = 0,
            pricePerUnit = pricePerUnit,
            totalPrice = quantity * pricePerUnit,
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(duration),
            allowPartial = false,
            minFillQuantity = null
        )

        orderRepository.create(order)

        CreateOrderResult(
            true, order, listingFee,
            translationAPI.getComponentSync(OrderMessages.ORDER_CREATED)
        )
    }

    /**
     * Fulfills an order by providing items (for buy orders) or buying items (for sell orders).
     *
     * @param filler The player fulfilling the order
     * @param orderId The ID of the order
     * @param items The items being provided
     * @return The result of the fulfillment attempt
     */
    suspend fun fulfillOrder(
        filler: Player,
        orderId: UUID,
        items: List<ItemStack>
    ): FulfillResult = withContext(Dispatchers.IO) {
        val order = orderRepository.getById(orderId)
            ?: return@withContext FulfillResult(
                false, 0, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND)
            )

        if (!order.isActive()) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                translationAPI.getComponentSync(OrderMessages.ORDER_ALREADY_FILLED)
            )
        }

        if (order.creatorUuid == filler.uniqueId) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                translationAPI.getComponent(OrderMessages.ORDER_CANNOT_OWN_ORDER)
            )
        }

        // Validate items match order requirements (NBT, lore, name based on config)
        for (item in items) {
            val matchResult = itemMatchesOrder(item, order)
            if (matchResult != ItemMatchResult.Match) {
                val reason = when (matchResult) {
                    ItemMatchResult.Match -> ""
                    ItemMatchResult.MaterialMismatch -> "Material does not match"
                    ItemMatchResult.NameMismatch -> "Item name does not match order requirements"
                    ItemMatchResult.NbtMismatch -> "Item NBT data does not match order requirements"
                    ItemMatchResult.LoreMismatch -> "Item lore does not match order requirements"
                }
                return@withContext FulfillResult(
                    false, 0, 0.0,
                    translationAPI.getComponent(OrderMessages.ORDER_ITEM_MISMATCH) {
                        unparsed("reason", reason)
                    }
                )
            }
        }

        // Validate items match order requirements
        val totalQuantity = items.sumOf { it.amount }
        val remaining = order.remainingQuantity()

        if (totalQuantity > remaining) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                translationAPI.getComponent(OrderMessages.ORDER_TOO_MANY_ITEMS) {
                    unparsed("max", remaining.toString())
                }
            )
        }

        if (!order.allowPartial && totalQuantity < remaining) {
            return@withContext FulfillResult(
                false, 0, 0.0,
                translationAPI.getComponent(OrderMessages.ORDER_REQUIRES_FULL_QUANTITY) {
                    unparsed("quantity", remaining.toString())
                }
            )
        }

        order.minFillQuantity?.let { min ->
            if (totalQuantity < min) {
                return@withContext FulfillResult(
                    false, 0, 0.0,
                    translationAPI.getComponentSync(OrderMessages.ORDER_MIN_FILL_NOT_MET) {
                        unparsed("min", min.toString())
                    }
                )
            }
        }

        // Calculate earnings
        val fillPrice = totalQuantity * order.pricePerUnit
        val fillFee = calculateFee(fillPrice, config.orders.fillFee)
        val earnings = fillPrice - fillFee

        // For buy orders: creator pays filler
        // For sell orders: filler pays creator
        when (order.orderType) {
            OrderType.BUY_ORDER -> {
                // Check creator still has funds
                if (!economy.has(
                        plugin.server.getOfflinePlayer(order.creatorUuid),
                        BigDecimal.valueOf(fillPrice)
                    )
                ) {
                    return@withContext FulfillResult(
                        false, 0, 0.0,
                        translationAPI.getComponent(OrderMessages.ORDER_CREATOR_NO_FUNDS)
                    )
                }

                // Transfer funds
                val creator = plugin.server.getOfflinePlayer(order.creatorUuid)
                economy.withdraw(creator, BigDecimal.valueOf(fillPrice))
                economy.deposit(filler, BigDecimal.valueOf(earnings))

                // Give items to creator (or store in expired items)
                plugin.server.getPlayer(order.creatorUuid)?.let { creatorPlayer ->
                    if (config.orders.buyOrdersAlwaysToExpiredItems) {
                        // Config option: always store in expired items
                        expiredItemManager.storeExpiredItems(
                            ownerUuid = order.creatorUuid,
                            ownerName = order.creatorName,
                            itemType = ExpiredItemType.ORDER_ITEM,
                            sourceId = orderId,
                            items = items,
                            reason = "ORDER_FILL"
                        )
                        creatorPlayer.sendMessage(
                            translationAPI.getComponentSync(OrderMessages.ORDER_FILLED_NOTIFICATION)
                        )
                    } else {
                        // Try to give items, store overflow in expired items
                        giveItemsOrStoreExpired(
                            creatorPlayer,
                            order.creatorUuid,
                            order.creatorName,
                            items,
                            orderId,
                            ExpiredItemType.ORDER_ITEM,
                            "ORDER_FILL"
                        )
                    }
                } ?: run {
                    // Creator offline - store items as expired
                    expiredItemManager.storeExpiredItems(
                        ownerUuid = order.creatorUuid,
                        ownerName = order.creatorName,
                        itemType = ExpiredItemType.ORDER_ITEM,
                        sourceId = orderId,
                        items = items,
                        reason = "ORDER_FILL"
                    )
                }

                // Remove items from filler AFTER successfully giving to creator
                // This prevents item loss if any operation fails
                withContext(plugin.entityDispatcher(filler)) {
                    items.forEach { item ->
                        filler.inventory.removeItemAnySlot(item)
                    }
                }
            }

            OrderType.SELL_ORDER -> {
                // Filler buys items from creator
                if (!economy.has(filler, BigDecimal.valueOf(fillPrice))) {
                    return@withContext FulfillResult(
                        false, 0, 0.0,
                        translationAPI.getComponent(OrderMessages.ORDER_FULFILL_NO_MONEY)
                    )
                }

                val creator = plugin.server.getOfflinePlayer(order.creatorUuid)
                economy.withdraw(filler, BigDecimal.valueOf(fillPrice))
                economy.deposit(creator, BigDecimal.valueOf(earnings))

                // Give items to filler (store overflow in expired items instead of dropping)
                giveItemsOrStoreExpired(
                    filler,
                    filler.uniqueId,
                    filler.name,
                    items,
                    orderId,
                    ExpiredItemType.ORDER_ITEM,
                    "ORDER_FILL"
                )
                // Note: Items are already removed from filler via giveItemsOrStoreExpired
                // since filler is receiving items, not providing them for sell orders
            }
        }

        // Create fill record
        val fill = OrderFill(
            orderId = orderId,
            fillerUuid = filler.uniqueId,
            fillerName = filler.name,
            quantity = totalQuantity,
            pricePerUnit = order.pricePerUnit,
            totalPrice = fillPrice,
            filledAt = Instant.now()
        )
        orderFillRepository.create(fill)

        // Update order status
        val newFilledQuantity = order.quantityFilled + totalQuantity
        val newStatus = when {
            newFilledQuantity >= order.quantityRequested -> OrderStatus.FILLED
            else -> OrderStatus.PARTIAL
        }
        orderRepository.updateFillStatus(orderId, newFilledQuantity, newStatus)

        // Log transaction
        transactionRepository.create(
            Transaction(
                transactionType = TransactionType.ORDER_FILL,
                fromUuid = if (order.orderType == OrderType.BUY_ORDER) order.creatorUuid else filler.uniqueId,
                fromName = if (order.orderType == OrderType.BUY_ORDER) order.creatorName else filler.name,
                toUuid = if (order.orderType == OrderType.BUY_ORDER) filler.uniqueId else order.creatorUuid,
                toName = if (order.orderType == OrderType.BUY_ORDER) filler.name else order.creatorName,
                amount = earnings,
                taxAmount = fillFee,
                itemMaterial = order.itemMaterial.name,
                itemQuantity = totalQuantity,
                referenceId = orderId,
                timestamp = Instant.now(),
                serverId = serverId
            )
        )

        // Notify order creator
        plugin.server.getPlayer(order.creatorUuid)?.let { creator ->
            if (newStatus == OrderStatus.FILLED) {
                creator.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_FILLED))
            } else {
                creator.sendMessage(
                    translationAPI.getComponentSync(OrderMessages.ORDER_PARTIAL_FILL) {
                        unparsed("filled", newFilledQuantity.toString())
                        unparsed("total", order.quantityRequested.toString())
                    }
                )
            }
        }

        FulfillResult(
            true, totalQuantity, earnings,
            translationAPI.getComponentSync(OrderMessages.ORDER_FULFILLED) {
                unparsed("amount", economy.format(BigDecimal.valueOf(earnings)))
            }
        )
    }

    /**
     * Cancels an order.
     *
     * @param player The player attempting to cancel (must be owner or admin)
     * @param orderId The ID of the order to cancel
     * @return The result of the cancellation attempt
     */
    suspend fun cancelOrder(player: Player, orderId: UUID): ServiceResult<Order> = withContext(Dispatchers.IO) {
        val order = orderRepository.getById(orderId)
            ?: return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND)
            )

        if (order.creatorUuid != player.uniqueId && !player.hasPermission("auctionhouse.admin.cancel")) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_NOT_OWNER)
            )
        }

        if (!order.isActive()) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_ALREADY_FILLED)
            )
        }

        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED)

        // Return items or refund based on order type
        when (order.orderType) {
            OrderType.SELL_ORDER -> {
                // Return items to seller
                order.itemStack?.let { itemStack ->
                    expiredItemManager.storeExpiredItem(
                        ownerUuid = order.creatorUuid,
                        ownerName = order.creatorName,
                        itemType = ExpiredItemType.ORDER_ITEM,
                        sourceId = orderId,
                        item = itemStack,
                        reason = "CANCELLED"
                    )
                }
            }

            OrderType.BUY_ORDER -> {
                // Refund remaining money
                val remainingValue = order.remainingValue()
                if (remainingValue > 0) {
                    val creator = plugin.server.getOfflinePlayer(order.creatorUuid)
                    economy.deposit(creator, BigDecimal.valueOf(remainingValue))
                    transactionRepository.create(
                        Transaction(
                            transactionType = TransactionType.ORDER_REFUND,
                            fromUuid = null,
                            fromName = null,
                            toUuid = order.creatorUuid,
                            toName = order.creatorName,
                            amount = remainingValue,
                            taxAmount = 0.0,
                            itemMaterial = null,
                            itemQuantity = null,
                            referenceId = orderId,
                            timestamp = Instant.now(),
                            serverId = serverId
                        )
                    )
                }
            }
        }

        ServiceResult.Success(order)
    }

    /**
     * Gets active orders with filtering, sorting, and pagination.
     *
     * @param filter Filter criteria
     * @param sort Sort order
     * @param page Page number (0-indexed)
     * @param pageSize Number of items per page
     * @return Paged result of orders
     */
    suspend fun getActiveOrders(
        filter: OrderFilter,
        sort: OrderSort,
        page: Int,
        pageSize: Int
    ): PagedResult<Order> = withContext(Dispatchers.IO) {
        val orders = orderRepository.getActiveOrders(filter, sort, page, pageSize)
        // Get accurate total count
        val total = orderRepository.countActiveOrders(filter)
        val totalPages = (total + pageSize - 1) / pageSize

        PagedResult(orders, page, totalPages.coerceAtLeast(1), total)
    }

    /**
     * Gets orders for a specific player.
     *
     * @param playerId The player's UUID
     * @param status Optional status filter
     * @return List of orders
     */
    suspend fun getPlayerOrders(playerId: UUID, status: OrderStatus?): List<Order> =
        orderRepository.getPlayerOrders(playerId, status)

    suspend fun getOrder(orderId: UUID): Order? =
        orderRepository.getById(orderId)

    suspend fun findOrderByShortId(shortId: String): Order? =
        orderRepository.findByShortId(shortId)

    suspend fun findBestBuyOrderForMaterial(material: Material): Order? =
        orderRepository.findBestBuyOrderForMaterial(material)

    suspend fun editOrderPrice(player: Player, orderId: UUID, newPricePerUnit: Double): ServiceResult<Order> = withContext(Dispatchers.IO) {
        val order = orderRepository.getById(orderId)
            ?: return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_NOT_FOUND)
            )

        if (order.creatorUuid != player.uniqueId) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_NOT_OWNER)
            )
        }

        if (!order.isActive()) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_ALREADY_FILLED)
            )
        }

        if (order.quantityFilled > 0) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_CANNOT_EDIT_PARTIAL)
            )
        }

        if (newPricePerUnit < config.orders.minPricePerUnit || newPricePerUnit > config.orders.maxPricePerUnit) {
            return@withContext ServiceResult.Failure(
                translationAPI.getComponentSync(OrderMessages.ORDER_INVALID_PRICE) {
                    unparsed("min", economy.format(BigDecimal.valueOf(config.orders.minPricePerUnit)))
                    unparsed("max", economy.format(BigDecimal.valueOf(config.orders.maxPricePerUnit)))
                }
            )
        }

        val oldPricePerUnit = order.pricePerUnit
        val oldTotalPrice = order.totalPrice
        val newTotalPrice = order.quantityRequested * newPricePerUnit

        when (order.orderType) {
            OrderType.BUY_ORDER -> {
                val priceDifference = newTotalPrice - oldTotalPrice
                if (priceDifference > 0) {
                    if (!economy.has(player, BigDecimal.valueOf(priceDifference))) {
                        return@withContext ServiceResult.Failure(
                            translationAPI.getComponentSync(OrderMessages.ORDER_INSUFFICIENT_FUNDS) {
                                unparsed("amount", economy.format(BigDecimal.valueOf(priceDifference)))
                            }
                        )
                    }
                    economy.withdraw(player, BigDecimal.valueOf(priceDifference))
                } else if (priceDifference < 0) {
                    economy.deposit(player, BigDecimal.valueOf(-priceDifference))
                }
            }
            OrderType.SELL_ORDER -> {}
        }

        orderRepository.updatePrice(orderId, newPricePerUnit, newTotalPrice)

        val updatedOrder = order.copy(
            pricePerUnit = newPricePerUnit,
            totalPrice = newTotalPrice
        )

        ServiceResult.Success(updatedOrder)
    }

    suspend fun processExpiredOrders() = withContext(Dispatchers.IO) {
        val expiredOrders = orderRepository.getExpiredOrders()

        for (order in expiredOrders) {
            try {
                processExpiredOrder(order)
            } catch (e: Exception) {
                logger.error("Error processing expired order ${order.id}", e)
            }
        }
    }

    private suspend fun processExpiredOrder(order: Order) {
        orderRepository.updateStatus(order.id, OrderStatus.EXPIRED)

        // Return items or refund
        when (order.orderType) {
            OrderType.SELL_ORDER -> {
                order.itemStack?.let { itemStack ->
                    expiredItemManager.storeExpiredItem(
                        ownerUuid = order.creatorUuid,
                        ownerName = order.creatorName,
                        itemType = ExpiredItemType.ORDER_ITEM,
                        sourceId = order.id,
                        item = itemStack,
                        reason = "EXPIRED"
                    )
                }
            }

            OrderType.BUY_ORDER -> {
                val remainingValue = order.remainingValue()
                if (remainingValue > 0) {
                    val creator = plugin.server.getOfflinePlayer(order.creatorUuid)
                    economy.deposit(creator, BigDecimal.valueOf(remainingValue))
                    transactionRepository.create(
                        Transaction(
                            transactionType = TransactionType.ORDER_REFUND,
                            fromUuid = null,
                            fromName = null,
                            toUuid = order.creatorUuid,
                            toName = order.creatorName,
                            amount = remainingValue,
                            taxAmount = 0.0,
                            itemMaterial = null,
                            itemQuantity = null,
                            referenceId = order.id,
                            timestamp = Instant.now(),
                            serverId = serverId
                        )
                    )
                }
            }
        }

        // Notify creator
        plugin.server.getPlayer(order.creatorUuid)?.let { creator ->
            creator.sendMessage(translationAPI.getComponentSync(OrderMessages.ORDER_EXPIRED))
        }
    }

    /**
     * Calculates a fee based on the amount and fee configuration.
     *
     * @param amount The base amount
     * @param feeConfig The fee configuration
     * @return The calculated fee
     */
    private fun calculateFee(amount: Double, feeConfig: FeeConfig): Double {
        val fee = when (feeConfig.type.uppercase()) {
            "PERCENTAGE" -> amount * (feeConfig.amount / 100)
            "FLAT" -> feeConfig.amount
            else -> 0.0
        }
        return fee.coerceIn(feeConfig.minFee, feeConfig.maxFee)
    }

    /**
     * Attempts to give items to a player. If their inventory is full or partially full,
     * stores the excess in the expired items system instead of dropping on the ground.
     *
     * @param player The player to give the items to
     * @param ownerUuid The UUID of the item owner (for expired item storage)
     * @param ownerName The name of the item owner (for expired item storage)
     * @param items The list of items to give
     * @param sourceId The source order ID
     * @param itemType The type of expired item
     * @param reason The reason for storage if needed
     */
    private suspend fun giveItemsOrStoreExpired(
        player: org.bukkit.entity.Player,
        ownerUuid: UUID,
        ownerName: String,
        items: List<ItemStack>,
        sourceId: UUID,
        itemType: ExpiredItemType,
        reason: String
    ) = withContext(plugin.entityDispatcher(player)) {
        var totalStored = 0

        // Collect all overflow items
        val overflowItems = mutableListOf<ItemStack>()
        items.forEach { item ->
            val remaining = player.inventory.addItem(item.clone())

            if (remaining.isNotEmpty()) {
                // Collect overflow items
                overflowItems.addAll(remaining.values)
                totalStored += remaining.values.sumOf { it.amount }
            }
        }

        // Store all overflow items at once
        if (overflowItems.isNotEmpty()) {
            expiredItemManager.storeExpiredItems(
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                itemType = itemType,
                sourceId = sourceId,
                items = overflowItems,
                reason = "$reason (INVENTORY_FULL)"
            )
        }

        if (totalStored > 0) {
            player.sendMessage(
                translationAPI.getComponentSync(OrderMessages.ORDER_INVENTORY_FULL) {
                    unparsed("count", totalStored.toString())
                }
            )
        }
    }
}

/**
 * Result of item matching validation.
 */
sealed class ItemMatchResult {
    object Match : ItemMatchResult()
    object MaterialMismatch : ItemMatchResult()
    object NameMismatch : ItemMatchResult()
    object NbtMismatch : ItemMatchResult()
    object LoreMismatch : ItemMatchResult()
}
