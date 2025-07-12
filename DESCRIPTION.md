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

## Commands (default aliases)
| Command                   | Description                     | Permission           |
|---------------------------|---------------------------------|----------------------|
| `/zealouschat`            | Root command & all sub-commands | `chatplugin.admin`   |
| `/msg <player> <message>` | Send a private message          | `chatplugin.message` |
| `/reply <message>`        | Reply to last PM                | `chatplugin.message` |

*See `/chatplugin help` in-game for full list.*

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