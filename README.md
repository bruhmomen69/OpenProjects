# MiniMessage Chat Plugin
**A production-ready, highly configurable chat formatting plugin for PaperMC powered by Kyori Adventure's MiniMessage**

## 🎯 **Plugin Overview**
This plugin provides comprehensive chat formatting capabilities with rank-based formats, interactive elements, and extensive customization options. Built with modern Paper API and MiniMessage for maximum compatibility and performance.

## 🚀 **Key Features**

### 💬 **Advanced Chat Formatting**
- **Rank-Based Chat Formats** with priority system (owner → admin → moderator → helper → vip → premium → donor → member → default)
- **World-Specific Formats** for different worlds/gamemodes (overworld, nether, end, creative, survival)
- **Permission-Based Format Selection** with configurable priority levels
- **Individual Feature Toggles** for colors, formatting, URLs, mentions, and cooldowns

### 🎨 **Interactive Chat Elements**
- **Custom Hover Messages** showing player rank/info (admin-configurable only)
- **Custom Click Actions** (suggest commands, open URLs, etc.) (admin-configurable only)
- **Player Input Security** - players cannot inject custom hover/click elements
- **URL Auto-Linking** with clickable links and hover previews
- **Player Mentions** with click-to-message functionality (@username)

### ⚙️ **Comprehensive Configuration**
- **Individual Message Type Controls**:
  - ✅ Chat message formatting (toggleable)
  - ✅ Join/Leave messages (toggleable)
  - ✅ Death messages (toggleable)
  - ✅ Advancement messages (toggleable)
- **Built-in Placeholder System** with 15+ placeholders (player info, server info, time/date)
- **Inventory Placeholder System** with clickable inventory sharing ({inv}, [ender], [armor], [hand], [pos], [health])
- **Custom Placeholder Support** for server-specific values
- **PlaceholderAPI Integration Ready** for external plugin compatibility

### 🛡️ **Security & Permissions**
- **Granular Permission System** - every command requires specific permissions
- **Input Sanitization** using MiniMessage's TagResolver system
- **Chat Cooldowns** with bypass permissions for staff
- **Permission-Based Feature Access** (colors, formatting, URLs, mentions)

### 📨 **Private Messaging System**
- **Full private messaging** with `/msg`, `/message`, `/tell`, `/whisper`, `/w` commands
- **Reply system** with `/reply`, `/r` commands for quick responses
- **Configurable message formats** for sender and recipient with MiniMessage support
- **Message cooldowns** with bypass permissions for staff
- **Message logging** for moderation and audit purposes

### 🔄 **Chat Toggle System**
- **Independent toggles** for public chat and private messages
- **Player commands**: `/chatplugin toggle chat`, `/chatplugin toggle messages`
- **Persistent state** across server restarts and reconnections
- **Staff bypass permissions** for moderation purposes
- **Configurable linking** - optionally link chat and message toggles

### 👁️ **Social Spy & Moderation**
- **Social spy system** for moderators to monitor private messages
- **Command spy** (optional) to monitor player commands
- **Toggle command**: `/chatplugin toggle socialspy`
- **Ignore moderator messages** option for privacy
- **Console logging** for audit and compliance

### 🔌 **PlaceholderAPI Integration**
- **Full PlaceholderAPI support** with automatic detection
- **Bridge to MiniMessage** using TagResolver system
- **External plugin placeholders** (e.g., `%vault_rank%`, `%luckperms_prefix%`)
- **Graceful fallbacks** when PlaceholderAPI unavailable
- **Performance optimized** with timeout protection

### 🎮 **Player Commands**
- `/msg <player> <message>` - Send private message (aliases: `/message`, `/tell`, `/whisper`, `/w`)
- `/reply <message>` - Reply to last sender (alias: `/r`)
- `/chatplugin toggle chat` - Toggle public chat on/off
- `/chatplugin toggle messages` - Toggle private messages on/off
- `/chatplugin toggle socialspy` - Toggle social spy (moderators only)
- `/chatplugin status` - View current chat status
- `/chatplugin viewinventory <snapshotId>` - View shared inventory snapshots

### 📦 **Inventory Placeholder System**
Players can share their inventories, equipment, and status in chat using special placeholders:

- **`{inv}` or `[inv]`** - Share main inventory with click-to-view functionality
- **`[ender]`** - Share ender chest contents  
- **`[armor]`** - Share equipped armor and tools
- **`[hand]`** - Share items currently in hands
- **`[pos]`** - Share current position with world and biome info
- **`[health]`** - Share health, food, and active potion effects

**Example Usage:**
```
Player: "Check out my loot {inv} and meet me at [pos]!"
Result: "Check out my loot [Inventory: 23 items] and meet me at [Pos: 100, 64, -200]!"
```

All placeholders are clickable and show detailed hover information. Inventory placeholders open read-only views of the shared inventories. Each placeholder type can be individually enabled/disabled and requires the `chatplugin.inventory.placeholders` permission.

### 🎯 **Enhanced Chat Interactivity**
The plugin now supports applying hover and click events to entire chat messages while preserving individual inventory placeholder interactions:

- **Player Name Interaction** (default): Hover/click events apply only to player names
- **Entire Message Interaction** (optional): Hover/click events apply to the entire message
- **Smart Preservation**: Inventory placeholders maintain their own hover/click functionality regardless of the setting
- **Configurable**: Toggle via `chatFormat.applyInteractiveToEntireMessage` in config

**Example with entire message interaction enabled:**
```
Player: "Check my loot {inv} at [pos]!"
Result: Entire message shows admin hover/click, but [Inventory: 15 items] and [Pos: 100, 64, -200] retain their specific interactions
```

### 🛠️ **Admin Commands** (All Permission-Protected)
- `/chatplugin reload` - Reload configuration (`chatplugin.admin.reload`)
- `/chatplugin info` - View plugin status (`chatplugin.admin.info`)
- `/chatplugin test <message>` - Test formatting (`chatplugin.admin.test`)
- `/chatplugin format set default/group/world <format>` - Configure formats (`chatplugin.admin.format`)
- `/chatplugin format list` - List all formats (`chatplugin.admin.format`)
- `/chatplugin toggle colors/formatting/mentions/cooldown` - Toggle features (`chatplugin.admin.toggle`)
- `/chatplugin admin toggle chat/messages/all <player> <true/false>` - Force toggle player settings
- `/chatplugin admin socialspy <player> <true/false>` - Manage social spy access
- `/chatplugin admin stats` - View system statistics and active users
- `/chatplugin admin block <player> <target>` - Force a player to block another player
- `/chatplugin admin unblock <player> <target>` - Force a player to unblock another player
- `/chatplugin admin clearblocks <player>` - Clear all blocks for a player
- `/chatplugin admin clear <toggles/socialspy/cooldowns/blocks/all>` - Clear various data types

### 🔧 **Technical Excellence**
- **Modern Paper API** usage (AsyncChatEvent, Adventure Components)
- **Proper MiniMessage Integration** with TagResolver system
- **HOCON Configuration** with automatic generation and validation
- **Comprehensive Error Handling** and logging with SLF4J
- **Performance Optimized** with efficient placeholder processing

# Project State:
**✅ PRODUCTION READY** - Fully featured, tested, and building successfully.

## ✅ Completed Features:
- **Comprehensive Configuration System** with HOCON support
- **Rank-Based Chat Formats** with priority system (owner, admin, moderator, helper, vip, premium, donor, member, default)
- **World-Specific Chat Formats** for different worlds/gamemodes
- **Permission-Based Format Selection** with multiple priority levels
- **Interactive Chat Elements**:
  - Hover messages showing player rank/info
  - Click actions (suggest commands, open URLs, etc.)
- **Advanced MiniMessage Support**:
  - Full color support including gradients and hex colors
  - Text formatting (bold, italic, underline, strikethrough, obfuscated)
  - Click events and hover events
  - URL auto-linking with click actions
  - Player mentions with click-to-message
- **Chat Features**:
  - Configurable chat cooldowns with bypass permissions
  - Join/Leave message customization
  - Death message customization
  - Chat logging
  - Permission-based feature access
- **Private Messaging System** with full MiniMessage support:
  - `/msg`, `/message`, `/tell`, `/whisper`, `/w` - Send private messages
  - `/reply`, `/r` - Reply to last sender
  - Configurable sender/recipient message formats
  - Message cooldowns with bypass permissions
  - Message logging for moderation
- **Chat Toggle System** for player control:
  - `/chatplugin toggle chat` - Toggle public chat
  - `/chatplugin toggle messages` - Toggle private messages
  - Independent or linked toggle behavior (configurable)
  - Persistent state across server restarts
  - Staff bypass permissions
- **Social Spy & Moderation Tools**:
  - `/chatplugin toggle socialspy` - Monitor private messages
  - Optional command spy for monitoring player commands
  - Ignore moderator messages option
  - Console logging for audit purposes
- **PlaceholderAPI Integration**:
  - Automatic detection and optional loading
  - Bridge to MiniMessage TagResolver system
  - Support for all external plugin placeholders
  - Performance optimized with timeout protection
- **Comprehensive Command System** using Lamp framework:
  - `/zchat reload` - Reload configuration
  - `/zchat info` - View plugin information
  - `/zchat test <message>` - Test chat formatting
  - `/zchat status` - View current chat status
  - `/zchat format set default/group/world <format>` - Configure formats
  - `/zchat format list` - List all formats
  - `/zchat toggle colors/formatting/mentions/cooldown` - Toggle features
  - `/zchat admin toggle chat/messages/all <player> <true/false>` - Admin toggles
  - `/zchat admin socialspy <player> <true/false>` - Manage social spy
  - `/zchat admin stats` - View system statistics
  - `/zchat admin clear <type>` - Clear various data types
- **Built-in Placeholder System** with extensive placeholders:
  - Player info: `<player_name>`, `<player_displayname>`, `<player_uuid>`
  - World info: `<world>`, `<world_displayname>`
  - Server info: `<server_name>`, `<server_version>`, `<server_motd>`
  - Online players: `<online_players>`, `<max_players>`
  - Time/Date: `<time>`, `<date>`, `<datetime>`
  - Custom placeholders support
  - PlaceholderAPI integration with automatic detection and bridge to MiniMessage
- **Advanced Permission System** with granular controls
- **Error Handling & Logging** with SLF4J
- **Modern Paper API Usage** (AsyncChatEvent, Adventure Components)

## Permission Setup Examples

### LuckPerms Commands:
```bash
# Give admin format to a player
/lp user PlayerName permission set chatplugin.format.admin true

# Give VIP format to a group
/lp group vip permission set chatplugin.format.vip true

# Allow colors for all players
/lp group default permission set chatplugin.color true

# Bypass cooldown for staff
/lp group staff permission set chatplugin.bypass.cooldown true
```

### Group-based Permissions:
```bash
# Using traditional group permissions
/lp user PlayerName parent add admin  # Will use admin format if group.admin permission exists
```

## MiniMessage Format Examples

### Basic Formats:
```
<gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>
<red>[ADMIN]</red> <gold>{player_name}</gold> <gray>»</gray> <white>{message}</white>
```

### Advanced Formats with Gradients:
```
<gradient:red:gold>[OWNER]</gradient> <gradient:gold:yellow>{player_name}</gradient> <gray>»</gray> <white>{message}</white>
<gradient:blue:cyan>[VIP]</gradient> <rainbow>{player_name}</rainbow> <gray>»</gray> <white>{message}</white>
```

### Interactive Elements:
```
<hover:show_text:'<red>Administrator</red>\n<gray>Click to message</gray>'><click:suggest_command:'/msg {player_name} '><red>[ADMIN]</red> <gold>{player_name}</gold></click></hover> <gray>»</gray> <white>{message}</white>
```

# Build Instructions
- `./gradlew build` - Build all projects
- `./gradlew :PaperMC:shadowJar` - Build PaperMC plugin JAR
- `./gradlew :PaperMC:runServer` - Run test server with plugin

**ALWAYS BUILD FROM THE ROOT DIRECTORY OF THE REPOSITORY**

## Installation
1. Build the plugin: `./gradlew :PaperMC:shadowJar`
2. Copy `PaperMC/build/libs/PaperMC-1.0-SNAPSHOT-all.jar` to your server's `plugins/` folder
3. (Re)Start your server
4. Configure the plugin by editing `plugins/ZealousChat/config.conf`
5. Use `/zchat reload` to reload configuration changes

## Quick Start
1. Install the plugin
2. Give yourself admin permissions: `/lp user YourName permission set chatplugin.admin true`
3. Test the plugin: `/zchat test Hello World!`
4. Configure formats: `/zchat format set default <red>[<white>{player_name}</white>]</red> <gray>{message}</gray>`
5. Set up rank formats using permissions like `chatplugin.format.vip`
6. Try private messaging: `/msg PlayerName Hello there!`
7. Toggle features: `/zchat toggle chat` or `/zchat toggle socialspy`

## Permission Setup

### 🔑 **Essential Permissions**
```bash
# Basic user permissions (give to default group)
chatplugin.message          # Send/receive private messages
chatplugin.toggle.chat       # Toggle own public chat
chatplugin.toggle.messages   # Toggle own private messages
chatplugin.status           # View own chat status
chatplugin.color            # Use colors in chat
chatplugin.formatting       # Use text formatting
chatplugin.inventory.placeholders # Use inventory placeholders in chat
chatplugin.viewinventory    # View inventory snapshots

# Staff permissions
chatplugin.admin            # All admin commands
chatplugin.socialspy        # Monitor private messages
chatplugin.bypass.cooldown  # Bypass chat cooldowns
chatplugin.bypass.chattoggle # Always able to chat
```

### 🎨 **Rank Format Permissions**
```bash
# Format-specific permissions (one per rank)
chatplugin.format.owner     # Use owner chat format
chatplugin.format.admin     # Use admin chat format
chatplugin.format.moderator # Use moderator chat format
chatplugin.format.vip       # Use VIP chat format
chatplugin.format.premium   # Use premium chat format
```

### 🛠️ **Admin Permission Examples**
```bash
# LuckPerms examples for setting up permissions
/lp group admin permission set chatplugin.admin true
/lp group moderator permission set chatplugin.socialspy true
/lp group vip permission set chatplugin.format.vip true
/lp group default permission set chatplugin.message true
/lp group default permission set chatplugin.color true
```
