# ZealousChat
*A modern, MiniMessage-powered chat formatter for Paper & Spigot servers*

ZealousChat replaces Vanilla chat with a fully–configurable system built on the Paper's high performance **AsyncChatEvent** and **MiniMessage** components.  It ships with rank/world specific formats, private messaging, inventory placeholders and a granular permission model – all while remaining light-weight and production ready.

---

## Features
- **MiniMessage everywhere** – gradients, hex colours, hover/click events
- **Rank, world & permission based formats** with priority system
- **Private messaging** (`/msg`, `/reply`) with social-spy & cooldowns
- **Chat / Message toggles** per-player (with staff bypass)
- **Inventory placeholders** `[inv]`, `[ender]`, `[armor]`, `[hand]`, `[pos]`, `[health]`
- **URL auto-linking** & **@mentions** (configurable)
- **Command framework** powered by Lamp with live `/zchat test` preview
- **PlaceholderAPI bridge** (optional soft-depend)
- **Hot-reloadable** HOCON configuration (`/zchat reload`)
- **Customizable Messages** for all messages used within the plugin.

## Commands

### Plugin Core
- `/zealouschat` (`/chatplugin`, `/chatformat`, `/zchat`) — Base command for ZealousChat
  - Permission: `chatplugin.admin`
  - This command is not an actual command, but rather the base for all sub-commands.

### Configuration Commands
- `/zealouschat reload` — Reload plugin configuration
  - Permission: `chatplugin.admin.reload`
- `/zealouschat info` — Display current plugin settings and feature flags
  - Permission: `chatplugin.admin.info`
- `/zealouschat test <message>` — Preview chat formatting
  - Permission: `chatplugin.admin.test`
  - Parameters:
    - `<message>`: Text to format and display

### Format Management
- `/zealouschat format set default <format>` — Set default chat format
  - Permission: `chatplugin.admin.format`
  - Parameters:
    - `<format>`: MiniMessage format string
- `/zealouschat format set group <group> <format>` — Define group-specific format
  - Permission: `chatplugin.admin.format`
  - Parameters:
    - `<group>`: Permission group name
    - `<format>`: MiniMessage format string
- `/zealouschat format set world <world> <format>` — Define world-specific format
  - Permission: `chatplugin.admin.format`
  - Parameters:
    - `<world>`: World name
    - `<format>`: MiniMessage format string
- `/zealouschat format list` — List all current chat formats
  - Permission: `chatplugin.admin.format`

### Feature Toggles
- `/zealouschat toggle colors` — Enable/disable color codes in chat
  - Permission: `chatplugin.admin.toggle`
- `/zealouschat toggle formatting` — Enable/disable text formatting
  - Permission: `chatplugin.admin.toggle`
- `/zealouschat toggle mentions` — Enable/disable player @mentions
  - Permission: `chatplugin.admin.toggle`
- `/zealouschat toggle cooldown` — Enable/disable chat cooldown
  - Permission: `chatplugin.admin.toggle`

### Private Messaging
- `/msg <player> <message>` — Send a private message (aliases: `/message`, `/tell`, `/whisper`, `/w`)
  - Permission: `chatplugin.message`
  - Parameters:
    - `<player>`: Recipient player name
    - `<message>`: Message text
- `/reply <message>` — Reply to the last private message sender (aliases: `/r`, `/respond`)
  - Permission: `chatplugin.message`
  - Parameters:
    - `<message>`: Reply text

### Chat & Social Spy Toggles
- `/zealouschat toggle chat` — Toggle your public chat on/off
  - Permission: `chatplugin.toggle.chat`
- `/zealouschat toggle messages` — Toggle your private messages on/off
  - Permission: `chatplugin.toggle.messages`
- `/zealouschat toggle socialspy` — Enable/disable social spy for private messages
  - Permission: `chatplugin.socialspy`
- `/zealouschat toggle status` — View your current chat & private message status
  - Permission: `chatplugin.status`

### Administrator Controls
- `/zealouschat admin toggle chat <player> <true|false>` — Force toggle public chat for a player
  - Permission: `chatplugin.admin`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable chat
- `/zealouschat admin toggle messages <player> <true|false>` — Force toggle private messages for a player
  - Permission: `chatplugin.admin`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable private messages
- `/zealouschat admin toggle all <player> <true|false>` — Force toggle both chat & private messages
  - Permission: `chatplugin.admin`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable both
- `/zealouschat admin socialspy <player> <true|false>` — Force toggle social spy for a player
  - Permission: `chatplugin.admin`
  - Parameters:
    - `<player>`: Target player name
    - `<true|false>`: Enable or disable social spy
- `/zealouschat admin stats` — Display server-wide chat and private message toggle statistics
  - Permission: `chatplugin.admin`

## Quick Start
1. Drop the built jar into `plugins/` and restart.
2. Edit `plugins/ZealousChat/config.conf` – changes can be reloaded with `/zealouschat reload`.
3. Grant players a format permission such as `chatplugin.format.vip` or allow colours with `chatplugin.color`.

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
| `chatplugin.*`                      | op      | Grant everything                    |
| `chatplugin.admin`                  | op      | Access root/admin commands          |
| `chatplugin.admin.reload`           | op      | Reload configuration                |
| `chatplugin.admin.info`             | op      | View plugin info                    |
| `chatplugin.admin.test`             | op      | Test formatting                     |
| `chatplugin.admin.format`           | op      | Manage chat formats                 |
| `chatplugin.admin.toggle`           | op      | Toggle plugin features              |
| `chatplugin.color`                  | true    | Use colour codes in chat            |
| `chatplugin.formatting`             | true    | Use text formatting codes           |
| `chatplugin.url`                    | true    | Send clickable URLs                 |
| `chatplugin.mention`                | true    | Use @mentions                       |
| `chatplugin.bypass.cooldown`        | op      | Bypass chat/message cooldowns       |
| `chatplugin.format.admin`           | op      | Use **Admin** chat format           |
| `chatplugin.format.moderator`       | false   | Use **Moderator** chat format       |
| `chatplugin.format.vip`             | false   | Use **VIP** chat format             |
| `chatplugin.message`                | true    | Send & receive private messages     |
| `chatplugin.toggle`                 | true    | Parent of chat/message toggle perms |
| `chatplugin.toggle.chat`            | true    | Toggle public chat                  |
| `chatplugin.toggle.messages`        | true    | Toggle private messages             |
| `chatplugin.status`                 | true    | View own chat status                |
| `chatplugin.socialspy`              | op      | Monitor private messages            |
| `chatplugin.commandspy`             | op      | Monitor player commands             |
| `chatplugin.bypass.chattoggle`      | op      | Chat even when disabled             |
| `chatplugin.bypass.messagetoggle`   | op      | PM even when disabled               |
| `chatplugin.viewinventory`          | true    | View shared inventories             |
| `chatplugin.inventory.placeholders` | true    | Use inventory placeholders          |

</details>

---

### Need help?
• Issues & suggestions: [GitHub Issues](https://github.com/your-org/ZealousChat)
• Discord: *coming soon*