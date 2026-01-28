package bruh.auctionhouse.database

import bruh.zchat.utils.database.migration.DatabaseSchema
import bruh.zchat.utils.database.sql

/**
 * Database schema and migrations for the AuctionHouse plugin.
 * Contains all table definitions for auctions, orders, bids, and transactions.
 */
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
