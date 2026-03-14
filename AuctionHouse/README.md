# AuctionHouse

A GUI-based Auction House plugin with dual-mode auctions (Auction + BIN) and an Order system for bulk item requests.

## Features

- **Auction System**: Create auctions with bidding, Buy-It-Now (BIN), or both
- **Order System**: Request specific items in bulk quantities with smart fulfillment
  - Automatic inventory checking before fulfillment
  - Smart default quantity (uses your inventory amount for partial fills)
  - Streamlined UI (skips quantity selection for non-partial orders)
  - User-friendly error messages for insufficient items
  - **NEW**: Edit prices on active orders
  - **NEW**: Quick sell to highest buy order
- **GUI Layer**: Full interactive menu system using MenuAPI
  - Auction browser with pagination, filters, and sorting
  - Individual auction view with bid/BIN buttons
  - Interactive auction creation with Anvil inputs
  - Player auction management
  - Expired item retrieval with "Claim All" button
  - Order browser and fulfillment interface
  - **NEW**: Order watchlist with right-click to watch/unwatch
- **Vault Integration**: Full economy support with configurable fees
- **Flexible Configuration**: Extensive customization for admins
- **Anti-Snipe Protection**: Automatic auction extension when bids are placed near end time
- **Expired Item Retrieval**: Players can retrieve expired items with partial retrieval support (overflow stays in expired items, never dropped)
- **Translation System**: Multi-language support using properties files
- **Short IDs**: Easy-to-use 8-character IDs for auctions and orders

## Commands

- `/ah` - Open the auction house
- `/ah sell <price> [binPrice]` - Quick sell held item
- `/ah bid <auctionId> <amount>` - Place a bid (accepts short ID)
- `/ah buy <auctionId>` - Buy via BIN (accepts short ID)
- `/ah cancel <auctionId>` - Cancel your auction (accepts short ID)
- `/ah expired` - View expired items
- `/ah myauctions` - View your auctions
- `/order` - Browse orders
- `/order buy <material> <quantity> <price>` - Create buy order
- `/order sell <pricePerUnit> [quantity]` - Create sell order from held item
- `/order cancel <orderId>` - Cancel your order (accepts short ID)
- `/order myorders` - View and manage your orders
- `/ahadmin` - Admin commands
- `/ahadmin reload` - Reload configuration
- `/ahadmin purge` - Purge old records
- `/ahadmin cancel <auctionId> [reason]` - Cancel any auction
- `/ahadmin refund <player> <amount> [reason]` - Refund a player

## Configuration

See `config.conf` for all configuration options including:
- Auction settings (duration, fees, limits)
- Order settings (quantities, matching rules)
- Database configuration (SQLite, MySQL, PostgreSQL)
- GUI settings (menu sizes, sorting options, confirmation threshold)
- Economy settings (currency, formatting)
- Restrictions (blacklisted items, worlds)
- Notification settings (sounds, alerts)

## Dependencies

- **Required**: Vault (for economy integration)
- **Optional**: EssentialsX or any economy plugin that provides Vault economy
- **Optional**: PlaceholderAPI for placeholders

## Development

### Building

```bash
./gradlew :AuctionHouse:build
```

### Running Test Server

```bash
./gradlew :AuctionHouse:runServer
```

The test server will:
- Start Paper 1.21.11
- Download Vault and EssentialsX automatically
- Load the AuctionHouse plugin

### Recent Changes

#### Version 2.0 - Major Update

**Bug Fixes:**
- **Admin Cancel Now Properly Refunds**: The `/ahadmin cancel` command now correctly refunds the highest bidder and returns items to the seller via the expired items system
- **Bid Withdrawal Promotion**: When the winning bid is withdrawn, the second-highest bidder is now promoted and notified that they are the new highest bidder
- **Order Fulfillment Pre-Validation**: The order fulfillment menu now correctly counts items that match NBT and lore requirements, not just material type
- **Sell Order Quantity**: Sell orders now correctly use the quantity selected in the creation menu, not the entire held stack
- **Bulk Listing Item Recovery**: Items for failed bulk auctions are no longer removed from inventory; only successfully created auctions consume items

**New Features:**
- **My Orders Menu**: View and manage your own orders with `/order myorders` - cancel orders, view status, edit prices
- **Order Price Editing**: Edit the price per unit on your active orders (if no partial fills have occurred)
- **Order Browser Search**: Search orders by material name, filter by price range
- **Quick Sell**: Sell items directly to the highest buy order with one click from the main menu
- **Short IDs**: Auctions and orders now display short IDs (first 8 chars) in GUIs - use these in commands instead of full UUIDs
- **Claim All Button**: Claim all expired items at once from the expired items menu
- **Order Fulfillment Confirmation**: High-value order fulfillments now require confirmation (configurable threshold)
- **Order Watchlist**: Add orders to your watchlist to track price changes - right-click orders to watch/unwatch
- **Own Order Indicator**: Your own orders are now highlighted with a glow effect and show management options instead of fulfillment

**Improvements:**
- **Menu Transition Safety**: AuctionHouse GUI click handlers now use `ClickResult.SwitchMenu` menu transitions instead of direct `open()` + `ClickResult.Close`, preventing close/open race issues and preserving back-navigation flows.
- **Search Feature**: The search button in `/ah` menu is functional! Click it to search for auctions by item name or material type
- **GUI Security Fix**: Fixed search button in `/ah` menu allowing players to move items out of the GUI
- **Item Retrieval Safety**:
  - **Partial Retrieval Support**: Expired Items Menu supports partial retrieval - overflow remains for later retrieval
  - **No More Dropped Items**: Items from won auctions, BIN purchases, and order fills go to Expired Items when inventory is full
  - **Buy Order Config**: `orders.buyOrdersAlwaysToExpiredItems` - items always go to expired items
- **Mock Economy Safety**: Requires BOTH `AUCTIONHOUSE_DEV_MODE=true` env var AND `economy.useMockEconomy=true` config

### Mock Economy Safety Gate

The mock economy provider is **ONLY** available for development/testing. To enable it:

1. Set environment variable: `AUCTIONHOUSE_DEV_MODE=true`
2. Set in `config.conf`: `economy.useMockEconomy = true`

**Both must be set for mock economy to activate.** If Vault is not available and mock economy is not properly enabled, the plugin will fail to start with clear error messages.

## PlaceholderAPI Placeholders

When PlaceholderAPI is installed, the following placeholders are available:

| Placeholder | Description |
|-------------|-------------|
| `%auctionhouse_active_auctions%` | Player's active auction count |
| `%auctionhouse_active_orders%` | Player's pending orders count |
| `%auctionhouse_total_auctions%` | Global active auctions count |
| `%auctionhouse_expired_items%` | Player's unclaimed expired items count |

## GUI Menus

The plugin uses the MenuAPI utility for all GUI interactions:

### AuctionHouseMenu
Main auction browser (6 rows, paginated):
- Browse all active auctions
- Filter by: All, Auction Only, BIN Only, Both
- Sort by: Ending Soon, Newest, Price Low/High, Most Bids
- Quick access buttons for My Auctions, Create Auction, Orders, Quick Sell
- Short ID displayed in auction item lore

### AuctionDetailsMenu
Individual auction view (5 rows):
- Displays item with full details and short ID
- Place Bid button (with Anvil input)
- Buy Now button (instant purchase)
- Cancel button (for owner/admin)
- Extend auction button (for owner)
- Edit prices button (for owner, if no bids)
- Watchlist toggle button

### MyAuctionsMenu
Player's auctions browser (6 rows, paginated):
- View all your auctions with status
- Cancel active auctions
- View sold/expired auction details
- Short ID displayed

### MyOrdersMenu
Player's orders browser (6 rows, paginated):
- View all your orders with status
- Cancel active orders
- View filled/expired order details
- Short ID displayed

### ExpiredItemsMenu
Retrieve expired items (6 rows, paginated):
- View all unclaimed expired items
- Click to retrieve to inventory
- **Claim All** button to retrieve everything
- Handles full inventory with overflow storage

### OrderBrowserMenu
Browse buy/sell orders (6 rows, paginated):
- Filter by order type (Buy/Sell)
- Sort by price, time, quantity
- Search by material name and price range
- Right-click to watch/unwatch orders
- Own orders highlighted with glow effect
- Short ID displayed

### OrderFulfillMenu
Fulfill an order (5 rows):
- Display order details
- Quantity selector (if partial fills enabled)
- Earnings preview
- Confirmation for high-value transactions

### QuickSellMenu
Quick sell to best buy order (4 rows):
- Shows your item and best matching buy order
- Preview of earnings
- Confirmation for high-value transactions

## Database Support

- SQLite (default) - File-based, no setup required
- MySQL - For larger servers
- PostgreSQL - Alternative SQL option

## Permissions

- `auctionhouse.use` - Use basic auction house commands
- `auctionhouse.create` - Create auctions
- `auctionhouse.bid` - Place bids
- `auctionhouse.buy` - Use BIN purchases
- `auctionhouse.admin` - Admin commands
- `auctionhouse.order.create` - Create orders
- `auctionhouse.order.fill` - Fill orders

## Project Status

**Complete!** The AuctionHouse plugin is fully implemented with:
- Complete auction system (bidding, BIN, anti-snipe, anonymous auctions)
- Complete order system (buy/sell orders with partial fills)
- Full GUI interface using MenuAPI
- Comprehensive command system using Lamp
- Database support (SQLite, MySQL, PostgreSQL)
- Economy integration via Vault
- Translation system for multi-language support
- PlaceholderAPI integration for external plugins
- Automatic expiration handling
- Short IDs for easy command usage
- Watchlist for auctions and orders
