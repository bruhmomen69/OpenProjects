# AuctionHouse

A GUI-based Auction House plugin with dual-mode auctions (Auction + BIN) and an Order system for bulk item requests.

## Features

- **Auction System**: Create auctions with bidding, Buy-It-Now (BIN), or both
- **Order System**: Request specific items in bulk quantities with smart fulfillment
  - Automatic inventory checking before fulfillment
  - Smart default quantity (uses your inventory amount for partial fills)
  - Streamlined UI (skips quantity selection for non-partial orders)
  - User-friendly error messages for insufficient items
- **GUI Layer**: Full interactive menu system using MenuAPI
  - Auction browser with pagination, filters, and sorting
  - Individual auction view with bid/BIN buttons
  - Interactive auction creation with Anvil inputs
  - Player auction management
  - Expired item retrieval
  - Order browser and fulfillment interface
- **Vault Integration**: Full economy support with configurable fees
- **Flexible Configuration**: Extensive customization for admins
- **Anti-Snipe Protection**: Automatic auction extension when bids are placed near end time
- **Expired Item Retrieval**: Players can retrieve expired items with partial retrieval support (overflow stays in expired items, never dropped)
- **Translation System**: Multi-language support using properties files

## Commands

- `/ah` - Open the auction house
- `/ah sell <price> [binPrice]` - Quick sell held item
- `/ah bid <auctionId> <amount>` - Place a bid
- `/ah buy <auctionId>` - Buy via BIN
- `/ah expired` - View expired items
- `/ah myauctions` - View your auctions
- `/order` - Browse orders
- `/order buy <material> <quantity> <price>` - Create buy order
- `/order sell <material> <quantity> <price>` - Create sell order
- `/order myorders` - View your orders
- `/ahadmin` - Admin commands
- `/ahadmin reload` - Reload configuration
- `/ahadmin purge` - Purge old records

## Configuration

See `config.conf` for all configuration options including:
- Auction settings (duration, fees, limits)
- Order settings (quantities, matching rules)
- Database configuration (SQLite, MySQL, PostgreSQL)
- GUI settings (menu sizes, sorting options)
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

- **Item Retrieval Safety Improvements**:
  - **Partial Retrieval Support**: The Expired Items Menu now supports partial retrieval. If your inventory is partially full, you'll receive what fits and the rest remains in expired items for later retrieval.
  - **No More Dropped Items**: Items from won auctions, BIN purchases, and order fills are now stored in the Expired Items system instead of being dropped on the ground when inventory is full.
  - **Buy Order Config Option**: New config `orders.buyOrdersAlwaysToExpiredItems` (default: false) - When enabled, items from fulfilled buy orders always go to the Expired Items menu instead of directly to inventory.

- **CRITICAL SAFETY**: Mock economy now requires BOTH environment variable `AUCTIONHOUSE_DEV_MODE=true` AND config `economy.useMockEconomy=true` to prevent accidental production usage
- Fixed potential NPE issues in item display name handling in AuctionService and OrderService
- Added safer null handling for buyNowPrice in buyNow operations
- Added `runPaper` plugin for test server support
- Fixed Configurate dependency to include Kotlin extensions
- Changed Lamp from `compileOnly` to `implementation` for proper shading
- Added `MockEconomyProvider` fallback for testing without economy plugins (now behind safety gate)
- Fixed `utils:configapi` to properly export Configurate dependencies
- Fixed partial claim splitting to keep remainder items linked to their consolidated group (no orphaned expired items)

### Mock Economy Safety Gate

The mock economy provider is **ONLY** available for development/testing. To enable it:

1. Set environment variable: `AUCTIONHOUSE_DEV_MODE=true`
2. Set in `config.conf`: `economy.useMockEconomy = true`

**Both must be set for mock economy to activate.** If Vault is not available and mock economy is not properly enabled, the plugin will fail to start with clear error messages. This prevents accidental use of mock economy (with unlimited fake money) in production environments.

- Vault (required) - Economy integration
- Paper 1.21+ (required) - Server platform
- PlaceholderAPI (optional) - Placeholder support for scoreboards and other plugins

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
- Quick access buttons for My Auctions, Create Auction, Orders

### AuctionDetailsMenu
Individual auction view (5 rows):
- Displays item with full details
- Place Bid button (with Anvil input)
- Buy Now button (instant purchase)
- Cancel button (for owner/admin)

### AuctionCreateMenu
Interactive auction creation (6 rows):
- Step 1: Select auction type (Auction/BIN/Both)
- Step 2: Set start price (Anvil input)
- Step 3: Set BIN price (optional)
- Step 4: Set duration in hours
- Step 5: Toggle anonymous (if enabled)
- Step 6: Confirm with fee preview

### MyAuctionsMenu
Player's auctions browser (6 rows, paginated):
- View all your auctions with status
- Cancel active auctions
- View sold/expired auction details

### ExpiredItemsMenu
Retrieve expired items (6 rows, paginated):
- View all unclaimed expired items
- Click to retrieve to inventory
- Handles full inventory with drops

### OrderBrowserMenu
Browse buy/sell orders (6 rows, paginated):
- Filter by order type (Buy/Sell)
- Sort by price, time, quantity
- View order details

### OrderFulfillMenu
Fulfill an order (5 rows):
- Display order details
- Quantity selector (if partial fills enabled)
- Earnings preview
- Confirm fulfillment

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

## Phase 1 Implementation Status

✅ Module structure created  
✅ Configuration system  
✅ Translation system  
✅ Economy provider  
✅ Main plugin class  

## Phase 2 Implementation Status

✅ Model enums (AuctionType, AuctionStatus, OrderType, OrderStatus, ExpiredItemType, TransactionType)  
✅ Model classes (Auction, Bid, Order, OrderFill, ExpiredItem, Transaction)  
✅ Filter/Sort classes (AuctionFilter, AuctionSort, OrderFilter, OrderSort)  
✅ Database schema with multi-dialect support (SQLite, MySQL, PostgreSQL)  
✅ AuctionRepository - CRUD + query operations  
✅ BidRepository - Bid history management  
✅ OrderRepository - CRUD + query operations  
✅ OrderFillRepository - Fill tracking  
✅ ExpiredItemRepository - Expired item storage  
✅ TransactionRepository - Economic transaction logging  

## Phase 3 Implementation Status

✅ ServiceResults - Sealed classes for operation results (ServiceResult, BidResult, PurchaseResult, CreateAuctionResult, CreateOrderResult, FulfillResult, PagedResult)  
✅ AuctionService - Business logic for auctions  
- createAuction() - Validate item/blacklist/prices, charge fee, create auction  
- placeBid() - Validate bid amount, refund previous bidder, charge new bidder, anti-snipe  
- buyNow() - Validate BIN, process purchase, refund bids, transfer item/money  
- cancelAuction() - Owner/admin check, refund bids, return item  
- getActiveAuctions() - With filters/sorting/pagination  
- getPlayerAuctions() - Get auctions by player  
- processExpiredAuctions() - Handle reserve price, winner logic, expired returns  
✅ OrderService - Business logic for orders  
- createBuyOrder() - Charge total cost + fee upfront  
- createSellOrder() - Take items from inventory, charge fee  
- fulfillOrder() - Item validation, fund transfer, status updates  
- cancelOrder() - Refund/return items based on order type  
- getActiveOrders() - With filters/sorting/pagination  
- getPlayerOrders() - Get orders by player  
- processExpiredOrders() - Handle expired order cleanup  

## Phase 4 Implementation Status

✅ GUI system (menus, components)  
✅ AuctionHouseMenu - Main auction browser with pagination, filters, sorting  
✅ AuctionDetailsMenu - Individual auction view with bid/BIN actions  
✅ AuctionCreateMenu - Interactive auction creation wizard  
✅ MyAuctionsMenu - Player's auctions management  
✅ ExpiredItemsMenu - Expired item retrieval interface  
✅ OrderBrowserMenu - Browse buy/sell orders  
✅ OrderFulfillMenu - Order fulfillment interface  
✅ MenuUtils - Shared GUI components  

## Phase 5 Implementation Status

✅ Commands registration via Lamp  
✅ AuctionHouseCommands - Main auction commands  
✅ OrderCommands - Order system commands  
✅ AuctionAdminCommands - Administrative commands  

## Phase 6 Implementation Status

✅ ExpirationService - Automatic auction/order expiration checking  
✅ PlaceholderAPIHook - PlaceholderAPI integration  
✅ Complete plugin lifecycle - onEnableAsync/onDisableAsync  
✅ Proper component initialization order  
✅ Service and repository access methods  

## Project Status

**All phases complete!** The AuctionHouse plugin is fully implemented with:
- Complete auction system (bidding, BIN, anti-snipe, anonymous auctions)
- Complete order system (buy/sell orders with partial fills)
- Full GUI interface using MenuAPI
- Comprehensive command system using Lamp
- Database support (SQLite, MySQL, PostgreSQL)
- Economy integration via Vault
- Translation system for multi-language support
- PlaceholderAPI integration for external plugins
- Automatic expiration handling