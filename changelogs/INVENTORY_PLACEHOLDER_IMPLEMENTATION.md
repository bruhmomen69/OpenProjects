# Inventory Placeholder System Implementation

## Overview

A new chat-specific placeholder system has been implemented to handle inventory-related placeholders in user chat messages. This system operates independently from the existing config-based placeholder system to ensure security while allowing players to share their inventories in chat.

**Key Feature**: The system preserves existing chat formatting (hover/click events) by processing inventory placeholders BEFORE chat formatting, ensuring the main chat format's hover and click events apply to the entire message while inventory placeholders remain independently clickable.

## New Placeholders

### 1. `{inv}` or `[inv]` - Player Inventory
- **Usage**: Type `{inv}` or `[inv]` in chat
- **Description**: Shows player's main inventory with click to view
- **Example**: `"Check out my loot: {inv}"`
- **Result**: `"Check out my loot: [Inventory: 15 items]"` (clickable)

### 2. `[ender]` - Ender Chest
- **Usage**: Type `[ender]` in chat
- **Description**: Shows player's ender chest with click to view
- **Example**: `"My ender chest storage: [ender]"`
- **Result**: `"My ender chest storage: [Ender Chest: 8 items]"` (clickable)

### 3. `[armor]` - Player Armor
- **Usage**: Type `[armor]` in chat
- **Description**: Shows player's equipped armor with click to view
- **Example**: `"My current gear: [armor]"`
- **Result**: `"My current gear: [Armor: 4 items]"` (clickable)

### 4. `[hand]` - Hand Items
- **Usage**: Type `[hand]` in chat
- **Description**: Shows items in player's main and off hand
- **Example**: `"Look what I'm holding: [hand]"`
- **Result**: `"Look what I'm holding: [Hand: 2 items]"` (clickable)

## How It Works

### 1. **Chat Message Processing**
- When a player sends a chat message, the `ChatInventoryPlaceholderService` scans for inventory placeholders in the RAW message
- Uses regex patterns to detect `{inv}`, `[inv]`, `[ender]`, `[armor]`, and `[hand]` placeholders
- Creates a **hybrid Component** with unparsed text segments and parsed inventory components
- This preserves the unparsed nature of user input while adding clickable inventory elements
- Chat formatting then applies hover/click events to the entire hybrid component

### 2. **Snapshot Creation**
- When a placeholder is detected, the plugin creates a snapshot of the relevant inventory
- Snapshots are saved to disk using Java object serialization in `plugins/MiniMessageChatPlugin/inventory_snapshots/`
- Each snapshot has a unique ID: `{playerUUID}_{inventoryType}_{timestamp}.dat`

### 3. **Clickable Display**
- Placeholders are replaced with clickable text showing item counts
- Hover shows a preview of the first few items
- Click executes `/chatplugin viewinventory {snapshotId}` command

### 4. **Read-Only Viewing**
- Clicking opens a Bukkit inventory that players cannot modify
- `InventoryProtectionListener` prevents any item manipulation
- Inventory title clearly indicates it's a read-only view

### 5. **Automatic Cleanup**
- Old snapshots (older than 1 hour) are automatically deleted
- Cleanup runs whenever a new snapshot is viewed

## Technical Architecture

### Core Components

1. **`ChatInventoryPlaceholderService`**
   - Main service handling placeholder detection and processing
   - Manages snapshot creation and retrieval
   - Handles inventory viewing and cleanup

2. **`InventoryViewCommand`**
   - Handles `/chatplugin viewinventory {snapshotId}` command
   - Validates permissions and opens inventory views

3. **`InventoryProtectionListener`**
   - Prevents modification of read-only inventory views
   - Detects inventory titles to identify protected inventories

4. **`ChatMessageListener` (Updated)**
   - Integrates inventory placeholder processing into chat flow
   - Processes placeholders after normal chat formatting

### Integration Points

- **Chat Processing**: Integrated into `ChatMessageListener.onAsyncChat()`
- **Command System**: Uses existing Lamp command framework
- **Permission System**: Uses existing permission structure
- **File Storage**: Uses plugin data folder for snapshots

## Security Features

### 1. **Separate Processing System**
- Uses a dedicated service independent from config-based placeholders
- No risk of injection into configuration formats
- Player input is sanitized through regex pattern matching

### 2. **Read-Only Inventories**
- All inventory views are completely read-only
- Players cannot take, move, or modify items
- Clear visual indication that inventories are snapshots

### 3. **Permission Control**
- `zchat.viewinventory` permission controls who can view snapshots
- Existing chat permissions control who can use placeholders in chat

### 4. **Automatic Cleanup**
- Snapshots are automatically deleted after 1 hour
- Prevents disk space accumulation
- No persistent storage of sensitive inventory data

## Usage Examples

### Basic Usage
```
Player: "Look at my diamonds! {inv}"
Result: "Look at my diamonds! [Inventory: 23 items]" (clickable)
```

### Multiple Placeholders
```
Player: "My gear: [armor] and my storage: [ender]"
Result: "My gear: [Armor: 4 items] and my storage: [Ender Chest: 12 items]" (both clickable)
```

### Mixed Content
```
Player: "Trading my [hand] for your rare items!"
Result: "Trading my [Hand: 1 items] for your rare items!" (clickable)
```

## Configuration

### Permissions
- `zchat.viewinventory` - View inventory snapshots (default: true)
- All existing chat permissions apply for using placeholders

### File Storage
- **Location**: `plugins/MiniMessageChatPlugin/inventory_snapshots/`
- **Format**: Serialized `.dat` files
- **Retention**: 1 hour automatic cleanup

## Compatibility

- **Minecraft Version**: 1.20+ (Paper API)
- **Dependencies**: None (uses built-in Java serialization)
- **Performance**: Minimal impact, efficient regex processing
- **Memory**: Low footprint with automatic cleanup

## Error Handling

- **Invalid Snapshots**: Graceful fallback with error message
- **Serialization Errors**: Logged and replaced with error placeholder
- **Permission Errors**: Standard permission denied messages
- **File System Errors**: Logged with fallback behavior

## Future Enhancements

Potential future improvements:
- Configurable snapshot retention time
- Additional inventory types (crafting, furnace, etc.)
- Snapshot sharing between players
- Integration with economy plugins for trading

## Testing

To test the functionality:

1. **Basic Test**: Type `{inv}` in chat and verify it becomes clickable
2. **Hover Test**: Hover over the placeholder to see item preview
3. **Click Test**: Click to open the read-only inventory view
4. **Permission Test**: Remove `zchat.viewinventory` and verify access is denied
5. **Multiple Types**: Test all placeholder types: `{inv}`, `[inv]`, `[ender]`, `[armor]`, `[hand]`

The implementation is complete and ready for production use!