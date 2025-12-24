# ZealousChat
*A modern, MiniMessage-powered chat formatter for Paper & Spigot servers*

<details>
<summary>Technical introduction</summary>

ZealousChat replaces Vanilla chat with a fully configurable system built on the Paper's high performance **AsyncChatEvent** and **MiniMessage** components.  It ships with rank/world specific formats, private messaging, inventory placeholders and a granular permission model – all while remaining light-weight and production ready.

---

## Features
- **MiniMessage everywhere** – gradients, hex colours, hover/click events
- **Rank, world & permission based formats** with priority system
- **Private messaging** (`/msg`, `/reply`) with social-spy & cooldowns
- **Cross-server private messaging** (optional, **MySQL required**) for networks running multiple servers
- **Chat / Message toggles** per-player (with staff bypass)
- **Inventory placeholders** `[inv]`, `[ender]`, `[armor]`, `[hand]`, `[pos]`, `[health]`
- **URL auto-linking** & **@mentions** (configurable)
- **Advanced swear filter** with regex and fuzzy matching, with a tiered punishment system.
- **Database-backed infraction tracking** with caching for performance
- **PlaceholderAPI bridge** (optional soft-depend)
- **Hot-reloadable** plugin configurations (`/zchat reload`)
- **Customizable Messages** for all messages used within the plugin.

</details>

ZealousChat is a high performance, 1.20.4+ chat formatting plugin that uses configurable MiniMessage-based chat formats to format chat messages. ZealousChat includes many extra features other plugins leave out, like the ability to send a read-only view of your inventory/hand/armor/enderchest in chat, formatted /msg and /reply commands, click/hover messages on messages, @mentions and more.
ZealousChat also has special features for server networks, such as cross-server private messaging, and cross server chat channels (for staffchat and similar).

ZealousChat implements all of its features using the Paper API, and is designed to be as efficient as possible.

[Showcase Images](https://modrinth.com/plugin/zealouschat/gallery)

## Commands

### Plugin Core
- `/zealouschat` (`/chatplugin`, `/zchat`) — Base command for ZealousChat
  - Permission: `zchat.admin`
  - This command is not an actual command, but rather the base for all sub-commands.

### Configuration Commands
- `/zealouschat reload` — Reload plugin configurations
  - Permission: `zchat.admin.reload`
- `/zealouschat info` — Display current plugin settings and enabled features
  - Permission: `zchat.admin.info`
- `/zealouschat test <message>` — Preview chat formatting
  - Permission: `zchat.admin.test`
  - Parameters:
    - `<message>`: Text to format and display

### Format Management
- `/zealouschat format set default <format>` — Set default chat format
  - Permission: `zchat.admin.format`
  - Parameters:
    - `<format>`: MiniMessage format string
- `/zealouschat format set group <group> <format>` — Define group-specific format
  - Permission: `zchat.admin.format`
  - Parameters:
    - `<group>`: Permission group name
    - `<format>`: MiniMessage format string
- `/zealouschat format set world <world> <format>` — Define world-specific format
  - Permission: `zchat.admin.format`
  - Parameters:
    - `<world>`: World name
    - `<format>`: MiniMessage format string
- `/zealouschat format list` — List all current chat formats
  - Permission: `zchat.admin.format`

### Feature Toggles
- `/zealouschat toggle colors` — Enable/disable color codes in chat
  - Permission: `zchat.admin.toggle`
- `/zealouschat toggle formatting` — Enable/disable text formatting
  - Permission: `zchat.admin.toggle`
- `/zealouschat toggle mentions` — Enable/disable player @mentions
  - Permission: `zchat.admin.toggle`
- `/zealouschat toggle cooldown` — Enable/disable chat cooldown
  - Permission: `zchat.admin.toggle`

### Private Messaging
- `/msg <player> <message>` — Send a private message (aliases: `/message`, `/tell`, `/whisper`, `/w`)
  - Permission: `zchat.message`
  - Parameters:
    - `<player>`: Recipient player name
    - `<message>`: Message text
- `/reply <message>` — Reply to the last private message sender (aliases: `/r`, `/respond`)
  - Permission: `zchat.message`
  - Parameters:
    - `<message>`: Reply text

#### Cross-server private messages

When you enable cross-server messaging in storage.conf (requires a MySQL or Redis database), ZealousChat can deliver private messages across multiple Paper servers on the same network.

- `/msg` will attempt cross-server delivery when the target is online on another server
- `/reply` also supports cross-server targets
- Recipients that have private messages toggled off will not receive cross-server messages

### Chat & Social Spy Toggles
- `/zealouschat toggle chat` — Toggle your public chat on/off
  - Permission: `zchat.toggle.chat`
- `/zealouschat toggle messages` — Toggle your private messages on/off
  - Permission: `zchat.toggle.messages`
- `/zealouschat toggle socialspy` — Enable/disable social spy for private messages
  - Permission: `zchat.socialspy`
- `/zealouschat alerts` — Toggle alerts on/off
  - Permission: `zchat.alerts.toggle`
- `/zealouschat toggle status` — View your current chat & private message status
  - Permission: `zchat.status`

### Channel Commands
- `/channel list` — List all available channels
  - Permission: `zchat.channel.use.list`
- `/channel join <channel>` — Join a channel
  - Permission: `zchat.channel.use.join`
  - Parameters:
    - `<channel>`: Channel name
- `/channel leave <channel>` — Leave a channel
  - Permission: `zchat.channel.use.leave`
  - Parameters:
    - `<channel>`: Channel name
- `/channel focus <channel>` — Set a channel as the active channel (where your messages go by default)
  - Permission: `zchat.channel.use.focus`
  - Parameters:
    - `<channel>`: Channel name
- `/channel who <channel>` — List members in a channel
  - Permission: `zchat.channel.use.who`
  - Parameters:
    - `<channel>`: Channel name

## Swear Filter System

The swear filter provides advanced profanity detection with configurable punishment tiers, database persistence, and bypass permissions for trusted staff.
*This system is currently experimental. There will be (mostly small) issues here. Please report any bugs you find.*

### Filter Types

**Regex Matching**
- Uses regular expressions for precise pattern matching
- Case-insensitive by default (e.g., `(?i)badword`)
- Supports complex patterns and word boundaries
- Automatically caches compiled patterns for performance

**Levenshtein Distance Matching**
- Fuzzy matching based on character distance between words
- Configurable distance threshold (default: 2-3 characters)
- Detects misspellings and variations of filtered words
- Splits messages into individual words for comparison

### Punishment System

- **Infraction Tracking**: Each filter group tracks infractions independently
- **Tiered Punishments**: Map infraction counts to command lists
- **Command Placeholders**: Use `{player}` for the offending player's name
- **Console Execution**: All punishment commands run as console
- **Database Persistence**: Infraction counts persist across server restarts

### Performance Features

- **Caching**: Online player infractions cached in memory for instant access
- **Async Processing**: Infraction handling runs asynchronously to prevent chat delays
- **Database Optimization**: Efficient queries with proper indexing
- **Regex Caching**: Compiled patterns cached to avoid recompilation

### Administrator Controls
- `/zealouschat admin clear <type>` — Clear various chat-related data
  - Permission: `zchat.admin.clear`
  - Parameters:
    - `<type>`: One of: `toggles` (resets all chat toggles), `socialspy` (resets all social spy states), `cooldowns` (clears message cooldowns), `blocks` (clears all block lists), `alerts` (clears all alert states), or `all` (clears all data)
- `/zealouschat admin toggle chat <player> <true|false>` — Force toggle public chat for a player
  - Permission: `zchat.admin.toggle.chat`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable chat
- `/zealouschat admin toggle messages <player> <true|false>` — Force toggle private messages for a player
  - Permission: `zchat.admin.toggle.messages`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable private messages
- `/zealouschat admin toggle all <player> <true|false>` — Force toggle both chat & private messages
  - Permission: `zchat.admin.toggle.all`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable both
- `/zealouschat admin socialspy <player> <true|false>` — Force toggle social spy for a player
  - Permission: `zchat.admin.socialspy`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable social spy
- `/zealouschat admin stats` — Display server-wide chat and private message toggle statistics
  - Permission: `zchat.admin.stats`
- `/zealouschat admin alerts <player> <true|false>` — Force toggle alerts for a player
  - Permission: `zchat.admin.alerts`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable alerts
- `/zealouschat admin block <player> <target>` — Force a player to block another player
  - Permission: `zchat.admin.block`
  - Parameters:
    - `<player>`: Player who will do the blocking
    - `<target>`: Player to be blocked
- `/zealouschat admin unblock <player> <target>` — Force a player to unblock another player
  - Permission: `zchat.admin.block`
  - Parameters:
    - `<player>`: Player who will do the unblocking
    - `<target>`: Player to be unblocked
- `/zealouschat admin clearblocks <player>` — Clear all blocks for a player
  - Permission: `zchat.admin.block`
  - Parameters:
    - `<player>`: Player whose block list will be cleared

## Quick Start
1. Drop the built jar into `plugins/` and restart.
2. Edit `plugins/ZealousChat/config.conf` – changes can be reloaded with `/zealouschat reload`.
3. (Optional) Configure database settings in `plugins/ZealousChat/database.conf` and messages in `plugins/ZealousChat/messages.conf`.
4. Grant players a format permission such as `zchat.format.vip` or allow colours with `zchat.color`.
5. Send `[inv]`, `[hand]`, `[ender]`, `[health]` and `[pos]` in chat messages to display inventory information.

## Placeholders
All Chat, PM and config strings support MiniMessage plus any PlaceholderAPI tags when the plugin is present.

## Supported Versions / Dependencies
- PaperMC / Spigot **1.20+** (may work on older versions, untested)
- Java 17+
- (Optional) PlaceholderAPI for external placeholders

## Permissions
<details>
<summary>Click to view permission nodes</summary>

| Node                                | Default | Description                         |
|-------------------------------------|---------|-------------------------------------|
| `zchat.*`                      | op      | Grant everything                    |
| `zchat.admin`                  | op      | Access root/admin commands          |
| `zchat.admin.reload`           | op      | Reload configuration                |
| `zchat.admin.info`             | op      | View plugin info                    |
| `zchat.admin.test`             | op      | Test formatting                     |
| `zchat.admin.format`           | op      | Manage chat formats                 |
| `zchat.admin.toggle`           | op      | Toggle plugin features              |
| `zchat.admin.block`            | op      | Manage player block lists           |
| `zchat.color`                  | true    | Use colour codes in chat            |
| `zchat.formatting`             | true    | Use text formatting codes           |
| `zchat.url`                    | true    | Send clickable URLs                 |
| `zchat.mention`                | true    | Use @mentions                       |
| `zchat.bypass.cooldown`        | op      | Bypass chat/message cooldowns       |
| `zchat.bypass.swearfilter`     | op      | Bypass swear filter restrictions    |
| `zchat.format.admin`           | op      | Use **Admin** chat format           |
| `zchat.format.moderator`       | false   | Use **Moderator** chat format       |
| `zchat.format.vip`             | false   | Use **VIP** chat format             |
| `zchat.message`                | true    | Send & receive private messages     |
| `zchat.channel.use.list`       | true    | Use the `/channel list` command     |
| `zchat.channel.use.join`       | true    | Use the `/channel join` command     |
| `zchat.channel.use.leave`      | true    | Use the `/channel leave` command    |
| `zchat.channel.use.focus`      | true    | Use the `/channel focus` command    |
| `zchat.channel.use.who`        | true    | Use the `/channel who` command      |
| `zchat.toggle`                 | true    | Parent of chat/message toggle perms |
| `zchat.toggle.chat`            | true    | Toggle public chat                  |
| `zchat.toggle.messages`        | true    | Toggle private messages             |
| `zchat.status`                 | true    | View own chat status                |
| `zchat.alerts.toggle`          | true    | Toggle alerts on/off               |
| `zchat.socialspy`              | op      | Monitor private messages            |
| `zchat.commandspy`             | op      | Monitor player commands             |
| `zchat.bypass.chattoggle`      | op      | Chat even when disabled             |
| `zchat.bypass.messagetoggle`   | op      | PM even when disabled               |
| `zchat.viewinventory`          | true    | View shared inventories             |
| `zchat.inventory.placeholders` | true    | Use inventory placeholders          |
| `zchat.admin.toggle.chat`      | op      | Force toggle chat for a player      |
| `zchat.admin.toggle.messages`  | op      | Force toggle messages for a player  |
| `zchat.admin.toggle.all`       | op      | Force toggle both chat & messages    |
| `zchat.admin.socialspy`        | op      | Force toggle social spy for player   |
| `zchat.admin.stats`            | op      | View server-wide statistics         |
| `zchat.admin.alerts`           | op      | Force toggle alerts for a player     |
| `zchat.admin.clear`            | op      | Clear various chat-related data     |

</details>

---

### Need help?
• Discord: [Join Discord](https://discord.gg/A6NG8DaAb7)