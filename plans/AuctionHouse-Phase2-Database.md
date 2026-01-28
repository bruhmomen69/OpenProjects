# Phase 2: Database & Models - Detailed Implementation Plan

This phase creates the database schema, data models, and repository layer for auctions, orders, and transactions.

---

## Step 1: Create Model Classes

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/AuctionType.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

enum class AuctionType {
    AUCTION,    // Bidding only
    BIN,        // Buy It Now only
    BOTH        // Both bidding and BIN
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/AuctionStatus.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

enum class AuctionStatus {
    ACTIVE,     // Currently running
    SOLD,       // Sold via BIN or winning bid
    EXPIRED,    // Ended without sale
    CANCELLED   // Cancelled by seller
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/Auction.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

data class Auction(
    val id: UUID,
    val sellerUuid: UUID,
    val sellerName: String,
    val itemStack: ItemStack,
    val itemMaterial: String,
    val itemDisplayName: String?,
    
    val auctionType: AuctionType,
    val startPrice: Double,
    val buyNowPrice: Double?,
    val reservePrice: Double?,
    val minIncrement: Double,
    
    val status: AuctionStatus,
    val createdAt: Instant,
    val endsAt: Instant,
    val soldAt: Instant? = null,
    val soldToUuid: UUID? = null,
    val soldToName: String? = null,
    val finalPrice: Double? = null,
    
    val viewCount: Int = 0,
    val bidCount: Int = 0,
    val isAnonymous: Boolean = false
) {
    fun isActive(): Boolean = status == AuctionStatus.ACTIVE
    fun hasEnded(): Boolean = status != AuctionStatus.ACTIVE || Instant.now().isAfter(endsAt)
    fun canBid(): Boolean = isActive() && (auctionType == AuctionType.AUCTION || auctionType == AuctionType.BOTH)
    fun canBuyNow(): Boolean = isActive() && (auctionType == AuctionType.BIN || auctionType == AuctionType.BOTH) && buyNowPrice != null
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/Bid.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import java.time.Instant
import java.util.UUID

data class Bid(
    val id: Long = 0,
    val auctionId: UUID,
    val bidderUuid: UUID,
    val bidderName: String,
    val bidAmount: Double,
    val bidTime: Instant,
    val isOutbid: Boolean = false
)
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/OrderType.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

enum class OrderType {
    BUY_ORDER,   // Requesting to buy items
    SELL_ORDER   // Offering to sell items
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/OrderStatus.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

enum class OrderStatus {
    PENDING,    // Waiting to be filled
    PARTIAL,    // Partially filled
    FILLED,     // Completely filled
    EXPIRED,    // Expired without complete fill
    CANCELLED   // Cancelled by creator
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/Order.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID,
    val creatorUuid: UUID,
    val creatorName: String,
    val orderType: OrderType,
    
    val itemMaterial: Material,
    val itemDisplayName: String?,
    val itemLoreHash: String?,
    val itemNbtHash: String?,
    val itemStack: ItemStack?,  // For sell orders with specific items
    
    val quantityRequested: Int,
    val quantityFilled: Int = 0,
    val pricePerUnit: Double,
    val totalPrice: Double,
    
    val status: OrderStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val filledAt: Instant? = null,
    
    val allowPartial: Boolean = true,
    val minFillQuantity: Int? = null
) {
    fun isActive(): Boolean = status == OrderStatus.PENDING || status == OrderStatus.PARTIAL
    fun remainingQuantity(): Int = quantityRequested - quantityFilled
    fun totalValue(): Double = quantityRequested * pricePerUnit
    fun remainingValue(): Double = remainingQuantity() * pricePerUnit
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/OrderFill.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import java.time.Instant
import java.util.UUID

data class OrderFill(
    val id: Long = 0,
    val orderId: UUID,
    val fillerUuid: UUID,
    val fillerName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val totalPrice: Double,
    val filledAt: Instant
)
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/ExpiredItem.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

enum class ExpiredItemType {
    AUCTION_ITEM,
    ORDER_ITEM
}

data class ExpiredItem(
    val id: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val itemType: ExpiredItemType,
    val sourceId: UUID,
    val itemStack: ItemStack,
    val reason: String,
    val expiredAt: Instant,
    val claimed: Boolean = false,
    val claimedAt: Instant? = null
)
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/Transaction.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

import java.time.Instant
import java.util.UUID

enum class TransactionType {
    AUCTION_SALE,
    AUCTION_BID_RETURN,
    ORDER_FILL,
    ORDER_REFUND,
    LISTING_FEE,
    SALE_FEE,
    FILL_FEE
}

data class Transaction(
    val id: Long = 0,
    val transactionType: TransactionType,
    val fromUuid: UUID?,
    val fromName: String?,
    val toUuid: UUID?,
    val toName: String?,
    val amount: Double,
    val taxAmount: Double = 0.0,
    val itemMaterial: String?,
    val itemQuantity: Int?,
    val referenceId: UUID?,
    val timestamp: Instant,
    val serverId: String
)
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/model/ItemFilter.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.model

data class AuctionFilter(
    val searchQuery: String? = null,
    val material: String? = null,
    val auctionType: AuctionType? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sellerUuid: String? = null
)

enum class AuctionSort {
    ENDING_SOON,
    NEWEST,
    PRICE_LOW,
    PRICE_HIGH,
    MOST_BIDS
}

data class OrderFilter(
    val searchQuery: String? = null,
    val material: String? = null,
    val orderType: OrderType? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null
)

enum class OrderSort {
    NEWEST,
    PRICE_LOW,
    PRICE_HIGH,
    MOST_FILLED
}
```

---

## Step 2: Create Database Schema

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/AuctionHouseSchema.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.sql

object AuctionHouseSchema : DatabaseSchema("auctionhouse") {
    
    override val migrations = listOf(
        migration(1, "Initial schema") {
            
            // auctions table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(16) NOT NULL,
                        item_stack BLOB NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_display_name TEXT,
                        auction_type VARCHAR(20) NOT NULL,
                        start_price DECIMAL(19, 4) NOT NULL,
                        buy_now_price DECIMAL(19, 4),
                        reserve_price DECIMAL(19, 4),
                        min_increment DECIMAL(19, 4) NOT NULL DEFAULT 1.0,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ends_at TIMESTAMP NOT NULL,
                        sold_at TIMESTAMP,
                        sold_to_uuid VARCHAR(36),
                        sold_to_name VARCHAR(16),
                        final_price DECIMAL(19, 4),
                        view_count INT NOT NULL DEFAULT 0,
                        bid_count INT NOT NULL DEFAULT 0,
                        is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
                        INDEX idx_status (status),
                        INDEX idx_seller (seller_uuid, status),
                        INDEX idx_ends_at (ends_at, status),
                        INDEX idx_material (item_material, status)
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(16) NOT NULL,
                        item_stack BYTEA NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_display_name TEXT,
                        auction_type VARCHAR(20) NOT NULL,
                        start_price DECIMAL(19, 4) NOT NULL,
                        buy_now_price DECIMAL(19, 4),
                        reserve_price DECIMAL(19, 4),
                        min_increment DECIMAL(19, 4) NOT NULL DEFAULT 1.0,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ends_at TIMESTAMP NOT NULL,
                        sold_at TIMESTAMP,
                        sold_to_uuid VARCHAR(36),
                        sold_to_name VARCHAR(16),
                        final_price DECIMAL(19, 4),
                        view_count INT NOT NULL DEFAULT 0,
                        bid_count INT NOT NULL DEFAULT 0,
                        is_anonymous BOOLEAN NOT NULL DEFAULT FALSE
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS auctions (
                        id TEXT PRIMARY KEY,
                        seller_uuid TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_stack BLOB NOT NULL,
                        item_material TEXT NOT NULL,
                        item_display_name TEXT,
                        auction_type TEXT NOT NULL,
                        start_price REAL NOT NULL,
                        buy_now_price REAL,
                        reserve_price REAL,
                        min_increment REAL NOT NULL DEFAULT 1.0,
                        status TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ends_at TIMESTAMP NOT NULL,
                        sold_at TIMESTAMP,
                        sold_to_uuid TEXT,
                        sold_to_name TEXT,
                        final_price REAL,
                        view_count INTEGER NOT NULL DEFAULT 0,
                        bid_count INTEGER NOT NULL DEFAULT 0,
                        is_anonymous INTEGER NOT NULL DEFAULT 0
                    )
                """)
            })
            
            // auction_bids table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS auction_bids (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        auction_id VARCHAR(36) NOT NULL,
                        bidder_uuid VARCHAR(36) NOT NULL,
                        bidder_name VARCHAR(16) NOT NULL,
                        bid_amount DECIMAL(19, 4) NOT NULL,
                        bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        is_outbid BOOLEAN NOT NULL DEFAULT FALSE,
                        FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                        INDEX idx_auction (auction_id, bid_amount DESC),
                        INDEX idx_bidder (bidder_uuid)
                    )
                """)
                postgres("""
                    CREATE TABLE IF NOT EXISTS auction_bids (
                        id BIGSERIAL PRIMARY KEY,
                        auction_id VARCHAR(36) NOT NULL REFERENCES auctions(id) ON DELETE CASCADE,
                        bidder_uuid VARCHAR(36) NOT NULL,
                        bidder_name VARCHAR(16) NOT NULL,
                        bid_amount DECIMAL(19, 4) NOT NULL,
                        bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        is_outbid BOOLEAN NOT NULL DEFAULT FALSE
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS auction_bids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        auction_id TEXT NOT NULL REFERENCES auctions(id) ON DELETE CASCADE,
                        bidder_uuid TEXT NOT NULL,
                        bidder_name TEXT NOT NULL,
                        bid_amount REAL NOT NULL,
                        bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        is_outbid INTEGER NOT NULL DEFAULT 0
                    )
                """)
            })
            
            // orders table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id VARCHAR(36) PRIMARY KEY,
                        creator_uuid VARCHAR(36) NOT NULL,
                        creator_name VARCHAR(16) NOT NULL,
                        order_type VARCHAR(20) NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_display_name VARCHAR(255),
                        item_lore_hash VARCHAR(64),
                        item_nbt_hash VARCHAR(64),
                        item_stack BLOB,
                        quantity_requested INT NOT NULL,
                        quantity_filled INT NOT NULL DEFAULT 0,
                        price_per_unit DECIMAL(19, 4) NOT NULL,
                        total_price DECIMAL(19, 4) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        expires_at TIMESTAMP NOT NULL,
                        filled_at TIMESTAMP,
                        allow_partial BOOLEAN NOT NULL DEFAULT TRUE,
                        min_fill_quantity INT,
                        INDEX idx_status (status),
                        INDEX idx_creator (creator_uuid, status),
                        INDEX idx_item (item_material, status, order_type),
                        INDEX idx_expires (expires_at, status)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id TEXT PRIMARY KEY,
                        creator_uuid TEXT NOT NULL,
                        creator_name TEXT NOT NULL,
                        order_type TEXT NOT NULL,
                        item_material TEXT NOT NULL,
                        item_display_name TEXT,
                        item_lore_hash TEXT,
                        item_nbt_hash TEXT,
                        item_stack BLOB,
                        quantity_requested INTEGER NOT NULL,
                        quantity_filled INTEGER NOT NULL DEFAULT 0,
                        price_per_unit REAL NOT NULL,
                        total_price REAL NOT NULL,
                        status TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        expires_at TIMESTAMP NOT NULL,
                        filled_at TIMESTAMP,
                        allow_partial INTEGER NOT NULL DEFAULT 1,
                        min_fill_quantity INTEGER
                    )
                """)
            })
            
            // order_fills table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS order_fills (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        order_id VARCHAR(36) NOT NULL,
                        filler_uuid VARCHAR(36) NOT NULL,
                        filler_name VARCHAR(16) NOT NULL,
                        quantity INT NOT NULL,
                        price_per_unit DECIMAL(19, 4) NOT NULL,
                        total_price DECIMAL(19, 4) NOT NULL,
                        filled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS order_fills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                        filler_uuid TEXT NOT NULL,
                        filler_name TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        price_per_unit REAL NOT NULL,
                        total_price REAL NOT NULL,
                        filled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)
            })
            
            // expired_items table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS expired_items (
                        id VARCHAR(36) PRIMARY KEY,
                        owner_uuid VARCHAR(36) NOT NULL,
                        owner_name VARCHAR(16) NOT NULL,
                        item_type VARCHAR(20) NOT NULL,
                        source_id VARCHAR(36) NOT NULL,
                        item_stack BLOB NOT NULL,
                        reason VARCHAR(50) NOT NULL,
                        expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        claimed BOOLEAN NOT NULL DEFAULT FALSE,
                        claimed_at TIMESTAMP,
                        INDEX idx_owner (owner_uuid, claimed),
                        INDEX idx_expired (expired_at)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS expired_items (
                        id TEXT PRIMARY KEY,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        item_type TEXT NOT NULL,
                        source_id TEXT NOT NULL,
                        item_stack BLOB NOT NULL,
                        reason TEXT NOT NULL,
                        expired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        claimed INTEGER NOT NULL DEFAULT 0,
                        claimed_at TIMESTAMP
                    )
                """)
            })
            
            // transactions table
            execute(sql {
                mysql("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        transaction_type VARCHAR(30) NOT NULL,
                        from_uuid VARCHAR(36),
                        from_name VARCHAR(16),
                        to_uuid VARCHAR(36),
                        to_name VARCHAR(16),
                        amount DECIMAL(19, 4) NOT NULL,
                        tax_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
                        item_material VARCHAR(64),
                        item_quantity INT,
                        reference_id VARCHAR(36),
                        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        server_id VARCHAR(64),
                        INDEX idx_from (from_uuid, timestamp),
                        INDEX idx_to (to_uuid, timestamp),
                        INDEX idx_reference (reference_id)
                    )
                """)
                sqlite("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        transaction_type TEXT NOT NULL,
                        from_uuid TEXT,
                        from_name TEXT,
                        to_uuid TEXT,
                        to_name TEXT,
                        amount REAL NOT NULL,
                        tax_amount REAL NOT NULL DEFAULT 0,
                        item_material TEXT,
                        item_quantity INTEGER,
                        reference_id TEXT,
                        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        server_id TEXT
                    )
                """)
            })
        }
    )
}
```

---

## Step 3: Create Repository Classes

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/AuctionRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.*
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

class AuctionRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)
    
    suspend fun create(auction: Auction) {
        database.execute(
            sql {
                mysql("INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auctions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            auction.id.toString(),
            auction.sellerUuid.toString(),
            auction.sellerName,
            serializeItem(auction.itemStack),
            auction.itemMaterial,
            auction.itemDisplayName,
            auction.auctionType.name,
            auction.startPrice,
            auction.buyNowPrice,
            auction.reservePrice,
            auction.minIncrement,
            auction.status.name,
            auction.createdAt,
            auction.endsAt,
            auction.soldAt,
            auction.soldToUuid?.toString(),
            auction.soldToName,
            auction.finalPrice,
            auction.viewCount,
            auction.bidCount,
            auction.isAnonymous
        )
    }
    
    suspend fun getById(id: UUID): Auction? {
        return database.querySingle(
            sql {
                mysql("SELECT * FROM auctions WHERE id = ?")
                sqlite("SELECT * FROM auctions WHERE id = ?")
            },
            id.toString()
        ) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous")
            )
        }
    }
    
    suspend fun getActiveAuctions(filter: AuctionFilter, sort: AuctionSort, page: Int, pageSize: Int): List<Auction> {
        val offset = page * pageSize
        
        var sql = "SELECT * FROM auctions WHERE status = 'ACTIVE'"
        val params = mutableListOf<Any>()
        
        filter.searchQuery?.let {
            sql += " AND (item_display_name LIKE ? OR item_material LIKE ?)"
            params.add("%$it%")
            params.add("%$it%")
        }
        
        filter.material?.let {
            sql += " AND item_material = ?"
            params.add(it)
        }
        
        filter.auctionType?.let {
            sql += " AND auction_type = ?"
            params.add(it.name)
        }
        
        sql += when (sort) {
            AuctionSort.ENDING_SOON -> " ORDER BY ends_at ASC"
            AuctionSort.NEWEST -> " ORDER BY created_at DESC"
            AuctionSort.PRICE_LOW -> " ORDER BY start_price ASC"
            AuctionSort.PRICE_HIGH -> " ORDER BY start_price DESC"
            AuctionSort.MOST_BIDS -> " ORDER BY bid_count DESC"
        }
        
        sql += " LIMIT ? OFFSET ?"
        params.add(pageSize)
        params.add(offset)
        
        return database.query(sql(sql { mysql(sql); sqlite(sql) }), *params.toTypedArray()) { rs ->
            // Map result set to Auction objects
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous")
            )
        }
    }
    
    suspend fun updateStatus(id: UUID, status: AuctionStatus) {
        database.execute(
            sql("UPDATE auctions SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }
    
    suspend fun markAsSold(id: UUID, buyerUuid: UUID, buyerName: String, finalPrice: Double) {
        database.execute(
            sql {
                mysql("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
                sqlite("UPDATE auctions SET status = 'SOLD', sold_at = ?, sold_to_uuid = ?, sold_to_name = ?, final_price = ? WHERE id = ?")
            },
            Instant.now(),
            buyerUuid.toString(),
            buyerName,
            finalPrice,
            id.toString()
        )
    }
    
    suspend fun getPlayerAuctions(sellerUuid: UUID, status: AuctionStatus?): List<Auction> {
        val sql = if (status != null) {
            "SELECT * FROM auctions WHERE seller_uuid = ? AND status = ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM auctions WHERE seller_uuid = ? ORDER BY created_at DESC"
        }
        
        val params = if (status != null) {
            arrayOf(sellerUuid.toString(), status.name)
        } else {
            arrayOf(sellerUuid.toString())
        }
        
        return database.query(sql(sql { mysql(sql); sqlite(sql) }), *params) { rs ->
            // Map to Auction
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous")
            )
        }
    }
    
    suspend fun getExpiredAuctions(): List<Auction> {
        return database.query(
            sql("SELECT * FROM auctions WHERE status = 'ACTIVE' AND ends_at < ?"),
            Instant.now()
        ) { rs ->
            Auction(
                id = UUID.fromString(rs.getString("id")),
                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                sellerName = rs.getString("seller_name"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                itemMaterial = rs.getString("item_material"),
                itemDisplayName = rs.getString("item_display_name"),
                auctionType = AuctionType.valueOf(rs.getString("auction_type")),
                startPrice = rs.getDouble("start_price"),
                buyNowPrice = rs.getDouble("buy_now_price").takeIf { it > 0 },
                reservePrice = rs.getDouble("reserve_price").takeIf { it > 0 },
                minIncrement = rs.getDouble("min_increment"),
                status = AuctionStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                endsAt = rs.getTimestamp("ends_at").toInstant(),
                soldAt = rs.getTimestamp("sold_at")?.toInstant(),
                soldToUuid = rs.getString("sold_to_uuid")?.let { UUID.fromString(it) },
                soldToName = rs.getString("sold_to_name"),
                finalPrice = rs.getDouble("final_price").takeIf { it > 0 },
                viewCount = rs.getInt("view_count"),
                bidCount = rs.getInt("bid_count"),
                isAnonymous = rs.getBoolean("is_anonymous")
            )
        }
    }
    
    suspend fun incrementViewCount(id: UUID) {
        database.execute(
            sql("UPDATE auctions SET view_count = view_count + 1 WHERE id = ?"),
            id.toString()
        )
    }
    
    suspend fun incrementBidCount(id: UUID) {
        database.execute(
            sql("UPDATE auctions SET bid_count = bid_count + 1 WHERE id = ?"),
            id.toString()
        )
    }
    
    suspend fun countPlayerAuctions(sellerUuid: UUID, status: AuctionStatus): Int {
        return database.querySingle(
            sql("SELECT COUNT(*) as count FROM auctions WHERE seller_uuid = ? AND status = ?"),
            sellerUuid.toString(),
            status.name
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/BidRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.Bid
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import java.util.UUID

class BidRepository(private val database: Database) {
    
    suspend fun create(bid: Bid): Long {
        database.execute(
            sql {
                mysql("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO auction_bids (auction_id, bidder_uuid, bidder_name, bid_amount, bid_time, is_outbid) VALUES (?, ?, ?, ?, ?, ?)")
            },
            bid.auctionId.toString(),
            bid.bidderUuid.toString(),
            bid.bidderName,
            bid.bidAmount,
            bid.bidTime,
            bid.isOutbid
        )
        
        // Get the generated ID
        return database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    suspend fun getBidsForAuction(auctionId: UUID): List<Bid> {
        return database.query(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_amount DESC"),
            auctionId.toString()
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
    
    suspend fun getHighestBid(auctionId: UUID): Bid? {
        return database.querySingle(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? AND is_outbid = FALSE ORDER BY bid_amount DESC LIMIT 1"),
            auctionId.toString()
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
    
    suspend fun markAsOutbid(bidId: Long) {
        database.execute(
            sql("UPDATE auction_bids SET is_outbid = TRUE WHERE id = ?"),
            bidId
        )
    }
    
    suspend fun getBidHistory(auctionId: UUID, limit: Int): List<Bid> {
        return database.query(
            sql("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY bid_time DESC LIMIT ?"),
            auctionId.toString(),
            limit
        ) { rs ->
            Bid(
                id = rs.getLong("id"),
                auctionId = UUID.fromString(rs.getString("auction_id")),
                bidderUuid = UUID.fromString(rs.getString("bidder_uuid")),
                bidderName = rs.getString("bidder_name"),
                bidAmount = rs.getDouble("bid_amount"),
                bidTime = rs.getTimestamp("bid_time").toInstant(),
                isOutbid = rs.getBoolean("is_outbid")
            )
        }
    }
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/OrderRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.*
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

class OrderRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack?): ByteArray? = item?.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray?): ItemStack? = bytes?.let { ItemStack.deserializeBytes(it) }
    
    suspend fun create(order: Order) {
        database.execute(
            sql {
                mysql("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            order.id.toString(),
            order.creatorUuid.toString(),
            order.creatorName,
            order.orderType.name,
            order.itemMaterial.name,
            order.itemDisplayName,
            order.itemLoreHash,
            order.itemNbtHash,
            serializeItem(order.itemStack),
            order.quantityRequested,
            order.quantityFilled,
            order.pricePerUnit,
            order.totalPrice,
            order.status.name,
            order.createdAt,
            order.expiresAt,
            order.filledAt,
            order.allowPartial,
            order.minFillQuantity
        )
    }
    
    suspend fun getById(id: UUID): Order? {
        return database.querySingle(
            sql("SELECT * FROM orders WHERE id = ?"),
            id.toString()
        ) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
    }
    
    suspend fun updateFillStatus(id: UUID, quantityFilled: Int, status: OrderStatus) {
        val filledAt = if (status == OrderStatus.FILLED) Instant.now() else null
        
        database.execute(
            sql("UPDATE orders SET quantity_filled = ?, status = ?, filled_at = ? WHERE id = ?"),
            quantityFilled,
            status.name,
            filledAt,
            id.toString()
        )
    }
    
    suspend fun updateStatus(id: UUID, status: OrderStatus) {
        database.execute(
            sql("UPDATE orders SET status = ? WHERE id = ?"),
            status.name,
            id.toString()
        )
    }
    
    suspend fun getActiveOrders(filter: OrderFilter, sort: OrderSort, page: Int, pageSize: Int): List<Order> {
        val offset = page * pageSize
        
        var sql = "SELECT * FROM orders WHERE status IN ('PENDING', 'PARTIAL')"
        val params = mutableListOf<Any>()
        
        filter.searchQuery?.let {
            sql += " AND (item_display_name LIKE ? OR item_material LIKE ?)"
            params.add("%$it%")
            params.add("%$it%")
        }
        
        filter.material?.let {
            sql += " AND item_material = ?"
            params.add(it)
        }
        
        filter.orderType?.let {
            sql += " AND order_type = ?"
            params.add(it.name)
        }
        
        sql += when (sort) {
            OrderSort.NEWEST -> " ORDER BY created_at DESC"
            OrderSort.PRICE_LOW -> " ORDER BY price_per_unit ASC"
            OrderSort.PRICE_HIGH -> " ORDER BY price_per_unit DESC"
            OrderSort.MOST_FILLED -> " ORDER BY quantity_filled DESC"
        }
        
        sql += " LIMIT ? OFFSET ?"
        params.add(pageSize)
        params.add(offset)
        
        return database.query(sql(sql { mysql(sql); sqlite(sql) }), *params.toTypedArray()) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
    }
    
    suspend fun getPlayerOrders(creatorUuid: UUID, status: OrderStatus?): List<Order> {
        val sql = if (status != null) {
            "SELECT * FROM orders WHERE creator_uuid = ? AND status = ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM orders WHERE creator_uuid = ? ORDER BY created_at DESC"
        }
        
        val params = if (status != null) {
            arrayOf(creatorUuid.toString(), status.name)
        } else {
            arrayOf(creatorUuid.toString())
        }
        
        return database.query(sql(sql { mysql(sql); sqlite(sql) }), *params) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
    }
    
    suspend fun getExpiredOrders(): List<Order> {
        return database.query(
            sql("SELECT * FROM orders WHERE status IN ('PENDING', 'PARTIAL') AND expires_at < ?"),
            Instant.now()
        ) { rs ->
            Order(
                id = UUID.fromString(rs.getString("id")),
                creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                creatorName = rs.getString("creator_name"),
                orderType = OrderType.valueOf(rs.getString("order_type")),
                itemMaterial = Material.valueOf(rs.getString("item_material")),
                itemDisplayName = rs.getString("item_display_name"),
                itemLoreHash = rs.getString("item_lore_hash"),
                itemNbtHash = rs.getString("item_nbt_hash"),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                quantityRequested = rs.getInt("quantity_requested"),
                quantityFilled = rs.getInt("quantity_filled"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                status = OrderStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                filledAt = rs.getTimestamp("filled_at")?.toInstant(),
                allowPartial = rs.getBoolean("allow_partial"),
                minFillQuantity = rs.getInt("min_fill_quantity").takeIf { it > 0 }
            )
        }
    }
    
    suspend fun countPlayerOrders(creatorUuid: UUID, status: OrderStatus): Int {
        return database.querySingle(
            sql("SELECT COUNT(*) as count FROM orders WHERE creator_uuid = ? AND status = ?"),
            creatorUuid.toString(),
            status.name
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/OrderFillRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.OrderFill
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import java.util.UUID

class OrderFillRepository(private val database: Database) {
    
    suspend fun create(fill: OrderFill): Long {
        database.execute(
            sql {
                mysql("INSERT INTO order_fills (order_id, filler_uuid, filler_name, quantity, price_per_unit, total_price, filled_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO order_fills (order_id, filler_uuid, filler_name, quantity, price_per_unit, total_price, filled_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
            },
            fill.orderId.toString(),
            fill.fillerUuid.toString(),
            fill.fillerName,
            fill.quantity,
            fill.pricePerUnit,
            fill.totalPrice,
            fill.filledAt
        )
        
        return database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    suspend fun getFillsForOrder(orderId: UUID): List<OrderFill> {
        return database.query(
            sql("SELECT * FROM order_fills WHERE order_id = ? ORDER BY filled_at DESC"),
            orderId.toString()
        ) { rs ->
            OrderFill(
                id = rs.getLong("id"),
                orderId = UUID.fromString(rs.getString("order_id")),
                fillerUuid = UUID.fromString(rs.getString("filler_uuid")),
                fillerName = rs.getString("filler_name"),
                quantity = rs.getInt("quantity"),
                pricePerUnit = rs.getDouble("price_per_unit"),
                totalPrice = rs.getDouble("total_price"),
                filledAt = rs.getTimestamp("filled_at").toInstant()
            )
        }
    }
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/ExpiredItemRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.ExpiredItem
import bruh.zchat.auctionhouse.model.ExpiredItemType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

class ExpiredItemRepository(private val database: Database) {
    
    private fun serializeItem(item: ItemStack): ByteArray = item.serializeAsBytes()
    private fun deserializeItem(bytes: ByteArray): ItemStack = ItemStack.deserializeBytes(bytes)
    
    suspend fun create(expiredItem: ExpiredItem) {
        database.execute(
            sql {
                mysql("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO expired_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            expiredItem.id.toString(),
            expiredItem.ownerUuid.toString(),
            expiredItem.ownerName,
            expiredItem.itemType.name,
            expiredItem.sourceId.toString(),
            serializeItem(expiredItem.itemStack),
            expiredItem.reason,
            expiredItem.expiredAt,
            expiredItem.claimed,
            expiredItem.claimedAt
        )
    }
    
    suspend fun getById(id: UUID): ExpiredItem? {
        return database.querySingle(
            sql("SELECT * FROM expired_items WHERE id = ?"),
            id.toString()
        ) { rs ->
            ExpiredItem(
                id = UUID.fromString(rs.getString("id")),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = UUID.fromString(rs.getString("source_id")),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                claimed = rs.getBoolean("claimed"),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }
    
    suspend fun getPlayerExpiredItems(ownerUuid: UUID): List<ExpiredItem> {
        return database.query(
            sql("SELECT * FROM expired_items WHERE owner_uuid = ? AND claimed = FALSE ORDER BY expired_at DESC"),
            ownerUuid.toString()
        ) { rs ->
            ExpiredItem(
                id = UUID.fromString(rs.getString("id")),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                ownerName = rs.getString("owner_name"),
                itemType = ExpiredItemType.valueOf(rs.getString("item_type")),
                sourceId = UUID.fromString(rs.getString("source_id")),
                itemStack = deserializeItem(rs.getBytes("item_stack")),
                reason = rs.getString("reason"),
                expiredAt = rs.getTimestamp("expired_at").toInstant(),
                claimed = rs.getBoolean("claimed"),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }
    
    suspend fun markAsClaimed(id: UUID) {
        database.execute(
            sql("UPDATE expired_items SET claimed = TRUE, claimed_at = ? WHERE id = ?"),
            Instant.now(),
            id.toString()
        )
    }
    
    suspend fun deleteOldItems(days: Int): Int {
        return database.execute(
            sql("DELETE FROM expired_items WHERE expired_at < ?"),
            Instant.now().minusSeconds(days * 86400L)
        )
    }
    
    suspend fun countPlayerExpiredItems(ownerUuid: UUID): Int {
        return database.querySingle(
            sql("SELECT COUNT(*) as count FROM expired_items WHERE owner_uuid = ? AND claimed = FALSE"),
            ownerUuid.toString()
        ) { rs ->
            rs.getInt("count")
        } ?: 0
    }
}
```

### File: `AuctionHouse/src/main/kotlin/bruh/zchat/auctionhouse/database/TransactionRepository.kt` (Create)
```kotlin
package bruh.zchat.auctionhouse.database

import bruh.zchat.auctionhouse.model.Transaction
import bruh.zchat.auctionhouse.model.TransactionType
import bruh.zchat.utils.database.Database
import bruh.zchat.utils.database.sql
import java.util.UUID

class TransactionRepository(private val database: Database) {
    
    suspend fun create(transaction: Transaction): Long {
        database.execute(
            sql {
                mysql("INSERT INTO transactions (transaction_type, from_uuid, from_name, to_uuid, to_name, amount, tax_amount, item_material, item_quantity, reference_id, timestamp, server_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                sqlite("INSERT INTO transactions (transaction_type, from_uuid, from_name, to_uuid, to_name, amount, tax_amount, item_material, item_quantity, reference_id, timestamp, server_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            },
            transaction.transactionType.name,
            transaction.fromUuid?.toString(),
            transaction.fromName,
            transaction.toUuid?.toString(),
            transaction.toName,
            transaction.amount,
            transaction.taxAmount,
            transaction.itemMaterial,
            transaction.itemQuantity,
            transaction.referenceId?.toString(),
            transaction.timestamp,
            transaction.serverId
        )
        
        return database.querySingle(
            sql("SELECT last_insert_rowid() as id")
        ) { rs ->
            rs.getLong("id")
        } ?: 0L
    }
    
    suspend fun getByReferenceId(referenceId: UUID): List<Transaction> {
        return database.query(
            sql("SELECT * FROM transactions WHERE reference_id = ? ORDER BY timestamp DESC"),
            referenceId.toString()
        ) { rs ->
            Transaction(
                id = rs.getLong("id"),
                transactionType = TransactionType.valueOf(rs.getString("transaction_type")),
                fromUuid = rs.getString("from_uuid")?.let { UUID.fromString(it) },
                fromName = rs.getString("from_name"),
                toUuid = rs.getString("to_uuid")?.let { UUID.fromString(it) },
                toName = rs.getString("to_name"),
                amount = rs.getDouble("amount"),
                taxAmount = rs.getDouble("tax_amount"),
                itemMaterial = rs.getString("item_material"),
                itemQuantity = rs.getInt("item_quantity").takeIf { it > 0 },
                referenceId = rs.getString("reference_id")?.let { UUID.fromString(it) },
                timestamp = rs.getTimestamp("timestamp").toInstant(),
                serverId = rs.getString("server_id")
            )
        }
    }
    
    suspend fun getPlayerTransactions(playerUuid: UUID, limit: Int = 50): List<Transaction> {
        return database.query(
            sql("SELECT * FROM transactions WHERE from_uuid = ? OR to_uuid = ? ORDER BY timestamp DESC LIMIT ?"),
            playerUuid.toString(),
            playerUuid.toString(),
            limit
        ) { rs ->
            Transaction(
                id = rs.getLong("id"),
                transactionType = TransactionType.valueOf(rs.getString("transaction_type")),
                fromUuid = rs.getString("from_uuid")?.let { UUID.fromString(it) },
                fromName = rs.getString("from_name"),
                toUuid = rs.getString("to_uuid")?.let { UUID.fromString(it) },
                toName = rs.getString("to_name"),
                amount = rs.getDouble("amount"),
                taxAmount = rs.getDouble("tax_amount"),
                itemMaterial = rs.getString("item_material"),
                itemQuantity = rs.getInt("item_quantity").takeIf { it > 0 },
                referenceId = rs.getString("reference_id")?.let { UUID.fromString(it) },
                timestamp = rs.getTimestamp("timestamp").toInstant(),
                serverId = rs.getString("server_id")
            )
        }
    }
}
```

---

## Phase 2 Completion Checklist

After completing Phase 2, you should have:

- [ ] Model enums: `AuctionType`, `AuctionStatus`, `OrderType`, `OrderStatus`, `ExpiredItemType`, `TransactionType`
- [ ] Model classes: `Auction`, `Bid`, `Order`, `OrderFill`, `ExpiredItem`, `Transaction`
- [ ] Filter/Sort classes: `AuctionFilter`, `AuctionSort`, `OrderFilter`, `OrderSort`
- [ ] `AuctionHouseSchema.kt` with all migrations
- [ ] `AuctionRepository.kt` with CRUD operations
- [ ] `BidRepository.kt` for bid management
- [ ] `OrderRepository.kt` for order CRUD
- [ ] `OrderFillRepository.kt` for fill tracking
- [ ] `ExpiredItemRepository.kt` for expired item storage
- [ ] `TransactionRepository.kt` for audit logging

## Build Verification

```bash
./gradlew :AuctionHouse:build
```

The plugin should compile with all database classes ready for Phase 3 (Service Layer).
