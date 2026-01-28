# EssentiallyStateless

A comprehensive stateless essential commands plugin for PaperMC that provides over 70 essential server commands without
storing any runtime-modified state outside the Minecraft server itself.

## What is EssentiallyStateless?

EssentiallyStateless is a complete rewrite of classic essential commands plugins, designed specifically for modern
PaperMC servers. Unlike traditional plugins that store player data in external files, this plugin operates entirely
within Minecraft's native systems - all state is stored in player data and world data.

## Key Features

### Stateless Design Philosophy

- **No external state storage** - All data stored within Minecraft's native systems
- **No configuration files for player data** - Uses player attributes and server state
- **Folia-compatible** - Built with MCCoroutine for optimal performance on Folia servers
- **Zero persistence** - Server restarts don't affect player states (uses Minecraft's built-in persistence)

### Comprehensive Command Suite

#### Game Mode Commands

- `/gamemode <mode> [player]` - Change game mode (survival, creative, adventure, spectator)
- `/gmc`, `/gms`, `/gma`, `/gmsp` - Quick game mode switches

#### Time & Weather

- `/time <value>` - Set world time (day, night, noon, midnight, or ticks)
- `/day`, `/night`, `/noon`, `/midnight` - Quick time presets
- `/weather <type>` - Change weather (clear, rain, storm)
- `/sun`, `/rain`, `/storm` - Weather shortcuts
- `/ptime <time>` - Set player client time
- `/pweather <weather>` - Set player client weather

#### Player Management

- `/heal [player]` - Restore health and food
- `/feed [player]` - Satisfy hunger
- `/fly [player]` - Toggle flight mode
- `/speed <value> [type] [player]` - Set walk/fly speed
- `/god [player]` - Toggle god mode (invulnerability)
- `/ext [player]` - Extinguish fire
- `/rest [player]` - Reset time since rest
- `/exp <action> [amount] [player]` - Manage experience
- `/kill [player]` - Kill player or self
- `/burn <player> [seconds]` - Set player on fire

#### Teleportation

- `/tp <target> [player]` - Teleport to player
- `/tphere <player>` - Teleport player to you
- `/tpall [target]` - Teleport all players to target
- `/tppos <x> <y> <z> [world] [player]` - Teleport to coordinates
- `/top` - Teleport to highest block
- `/bottom` - Teleport to lowest safe block
- `/jump` - Jump to target block
- `/world <world>` - Teleport to world spawn

#### Inventory & Items

- `/anvil` - Open virtual anvil
- `/workbench` - Open virtual crafting table
- `/grindstone` - Open virtual grindstone
- `/cartography` - Open virtual cartography table
- `/loom` - Open virtual loom
- `/smithing` - Open virtual smithing table
- `/stonecutter` - Open virtual stonecutter
- `/enderchest [player]` - Open ender chest (own or others')
- `/disposal` - Open disposal (trash) inventory
- `/invsee <player>` - View player's inventory
- `/clearinventory [player]` - Clear inventory
- `/give <player> <item> [amount]` - Give items to player
- `/item <item> [amount]` - Spawn items for yourself
- `/more [amount]` - Fill held item stack
- `/repair [all]` - Repair held item or all items
- `/enchant <enchantment> [level]` - Enchant held item
- `/hat` - Wear held item as helmet
- `/skull [player]` - Get player's head
- `/itemname [name]` - Rename held item
- `/itemlore [lore]` - Set item lore
- `/condense` - Condense items into blocks

#### World Manipulation

- `/lightning [player]` - Strike lightning
- `/remove [type] [radius]` - Remove entities
- `/spawnmob <type> [amount]` - Spawn mobs
- `/tree [type]` - Spawn tree at target location
- `/bigtree [type]` - Spawn large tree
- `/break` - Break target block
- `/spawner <entity>` - Change spawner type

#### Administration

- `/kick <player> [reason]` - Kick player
- `/kickall [reason]` - Kick all players
- `/ban <player> [reason]` - Ban player
- `/unban <player>` - Unban player
- `/banip <ip/player> [reason]` - Ban IP address
- `/unbanip <ip>` - Unban IP address
- `/sudo <player> <command>` - Force player to execute command
- `/broadcast <message>` - Broadcast server-wide message
- `/broadcastworld <world> <message>` - Broadcast to specific world

#### Information

- `/gc` - Server performance and memory info
- `/list` - List online players
- `/whois <player>` - Detailed player information
- `/near [radius]` - Find nearby players
- `/seen <player>` - Check when player was last seen
- `/ping [player]` - Check player ping
- `/getpos [player]` - Get player coordinates
- `/compass` - Show facing direction
- `/depth` - Show current depth
- `/playtime [player]` - Show player playtime

#### Fun Commands

- `/me <action>` - Perform emote action
- `/fireball` - Launch fireball
- `/book` - Edit written book
- `/editsign <line> [text]` - Edit sign text
- `/potion <effect> [duration] [amplifier]` - Modify held potion
- `/firework [power]` - Modify held firework

#### Plugin Management

- `/essentiallystateless reload` - Reload configuration
- `/essentiallystateless version` - Show plugin version

## Installation

### Requirements

- PaperMC 1.21+ or Folia
- Java 17+
- No additional dependencies required

### Quick Install

1. **Download** the plugin JAR from releases
2. **Place** the JAR in your server's `plugins/` folder
3. **Start** the server - configuration will be generated automatically
4. **Configure** by editing `plugins/EssentiallyStateless/config.conf`
5. **Reload** with `/essentiallystateless reload`

## Configuration

### Configuration File

The plugin generates a comprehensive configuration file at `plugins/EssentiallyStateless/config.conf`:

### Translation System

- **Multi-language support** with automatic file generation
- **Translation files** located in `plugins/EssentiallyStateless/translations/`
- **Customizable messages** for all commands
- **Placeholder support** with MiniMessage formatting

## Permissions

### Permission Structure

All commands use the permission format: `essentiallystateless.<command>`

### Default Permission Values

Permissions are configured with sensible defaults out of the box.

<details>
<summary><b>Available to All Players (default: true)</b></summary>

These commands work for everyone by default:

```
essentiallystateless.list          # List online players
essentiallystateless.ping          # Check ping
essentiallystateless.getpos        # Get coordinates
essentiallystateless.compass       # Show facing direction
essentiallystateless.depth         # Show depth
essentiallystateless.playtime      # Check playtime
essentiallystateless.seen          # Check when player was last online
essentiallystateless.near          # Find nearby players
essentiallystateless.me            # Emote actions
essentiallystateless.ptime         # Personal time
essentiallystateless.pweather      # Personal weather
essentiallystateless.ext           # Extinguish yourself
essentiallystateless.hat           # Wear items as hat
essentiallystateless.condense      # Condense items to blocks
essentiallystateless.enderchest    # Open your ender chest
essentiallystateless.disposal      # Open trash inventory
```
</details>

<details>
<summary><b>Game Mode Commands (default: op)</b></summary>

```
essentiallystateless.gamemode
essentiallystateless.gamemode.others
```
</details>

<details>
<summary><b>Player State Commands (default: op)</b></summary>

```
essentiallystateless.heal / heal.others
essentiallystateless.feed / feed.others
essentiallystateless.fly / fly.others
essentiallystateless.speed / speed.others
essentiallystateless.god / god.others
essentiallystateless.rest / rest.others
essentiallystateless.exp
essentiallystateless.kill
essentiallystateless.burn
```
</details>

<details>
<summary><b>Time & Weather Commands (default: op)</b></summary>

```
essentiallystateless.time
essentiallystateless.weather
```
</details>

<details>
<summary><b>Teleportation Commands (default: op)</b></summary>

```
essentiallystateless.tp
essentiallystateless.tphere
essentiallystateless.tpall
essentiallystateless.tppos
essentiallystateless.top
essentiallystateless.bottom
essentiallystateless.jump
essentiallystateless.world
```
</details>

<details>
<summary><b>Inventory Commands (default: op)</b></summary>

```
essentiallystateless.anvil
essentiallystateless.workbench
essentiallystateless.grindstone
essentiallystateless.cartography
essentiallystateless.loom
essentiallystateless.smithing
essentiallystateless.stonecutter
essentiallystateless.enderchest.others
essentiallystateless.invsee
essentiallystateless.clearinventory / clearinventory.others
```
</details>

<details>
<summary><b>Item Commands (default: op)</b></summary>

```
essentiallystateless.give
essentiallystateless.item
essentiallystateless.more
essentiallystateless.repair
essentiallystateless.enchant
essentiallystateless.skull
essentiallystateless.itemname
essentiallystateless.itemlore
```
</details>

<details>
<summary><b>World Manipulation Commands (default: op)</b></summary>

```
essentiallystateless.lightning
essentiallystateless.remove
essentiallystateless.spawnmob
essentiallystateless.tree
essentiallystateless.break
essentiallystateless.spawner
```
</details>

<details>
<summary><b>Administration Commands (default: op)</b></summary>

```
essentiallystateless.kick
essentiallystateless.kickall
essentiallystateless.ban / unban
essentiallystateless.banip / unbanip
essentiallystateless.sudo
essentiallystateless.broadcast
essentiallystateless.gc
essentiallystateless.whois
essentiallystateless.reload
```
</details>

<details>
<summary><b>Fun Commands (default: op)</b></summary>

```
essentiallystateless.fireball
essentiallystateless.book
essentiallystateless.editsign
essentiallystateless.potion
essentiallystateless.firework
```
</details>

<details>
<summary><b>Admin Permission (default: op)</b></summary>

```
essentiallystateless.admin    # Grants ALL permissions
```
</details>

<details>
<summary><b>LuckPerms Setup Examples</b></summary>

```bash
# Give admin access to all commands
/lp group admin permission set essentiallystateless.admin true

# Grant basic essentials to default players (already available by default)
# These are already default: true, so no setup needed for:
# - list, ping, getpos, compass, depth, playtime, seen, near, me
# - ptime, pweather, ext, hat, condense, enderchest, disposal

# Give moderator access to help players
/lp group moderator permission set essentiallystateless.heal true
/lp group moderator permission set essentiallystateless.heal.others true
/lp group moderator permission set essentiallystateless.tp true
/lp group moderator permission set essentiallystateless.kick true

# Give builder access to creative tools
/lp group builder permission set essentiallystateless.gamemode true
/lp group builder permission set essentiallystateless.time true
/lp group builder permission set essentiallystateless.speed true
/lp group builder permission set essentiallystateless.fly true
```
</details>

<details>
<summary><b>Customizing Defaults</b></summary>

To change default permissions, edit your permissions plugin configuration or use commands:

```bash
# Example: Allow all players to heal themselves
/lp group default permission set essentiallystateless.heal true

# Example: Remove disposal access from default players
/lp group default permission set essentiallystateless.disposal false
```

**Note:** The `.others` suffix permissions control whether a player can use the command on other players. For example:
- `essentiallystateless.heal` - Can heal yourself
- `essentiallystateless.heal.others` - Can heal other players (in addition to yourself)
</details>

## Usage Examples

### Basic Player Usage

```
/heal - Restore your health
/fly - Toggle flight mode
/speed 0.5 - Set walk speed to 50%
/tp PlayerName - Teleport to another player
/anvil - Open virtual anvil
/give PlayerName diamond 64 - Give 64 diamonds to player
```

### Staff Usage

```
/heal PlayerName - Heal another player
/gamemode creative PlayerName - Set player to creative mode
/time day - Set world time to day
/weather clear - Clear the weather
/tpall - Teleport all players to you
/broadcast Server maintenance in 5 minutes! - Broadcast message
```

### Advanced Usage

```
/tppos 100 64 -200 world_nether - Teleport to specific coordinates in nether
/enchant sharpness 5 - Add Sharpness V to held item
/skull Notch - Get Notch's player head
/tree oak - Spawn oak tree at target location
/whois PlayerName - View detailed player information
```

## Technical Details

### Architecture

- **Stateless Design** - No external data storage
- **MCCoroutine Integration** - Folia-compatible async operations
- **Lamp Command Framework** - Modern command handling with suggestions
- **Configurate HOCON** - Modern configuration format
- **TranslationAPI** - Multi-language support with caching
- **MenuAPI** - GUI support for future features

### Performance

- **Zero persistence** - No disk I/O for player data
- **Full coroutine support** - All commands are suspend functions for non-blocking execution
- **Proper thread dispatching** - Entity operations use entityDispatcher, world operations use regionDispatcher
- **No runBlocking** - Clean coroutine implementation without blocking the main thread
- **Efficient caching** - Translation and configuration caching
- **Minimal memory footprint** - Stateless design reduces memory usage

### Compatibility

- **PaperMC 1.21+** - Full Paper API compatibility
- **Folia** - Native Folia support with proper region dispatching
- **No dependencies** - Works standalone without additional plugins

## Command Reference

### Quick Reference Card

| Category  | Commands                                                            | Permission                            |
|-----------|---------------------------------------------------------------------|---------------------------------------|
| GameMode  | `/gamemode`, `/gmc`, `/gms`, `/gma`, `/gmsp`                        | `essentiallystateless.gamemode`       |
| Time      | `/time`, `/day`, `/night`, `/noon`, `/midnight`                     | `essentiallystateless.time`           |
| Weather   | `/weather`, `/sun`, `/rain`, `/storm`, `/thunder`                   | `essentiallystateless.weather`        |
| Player    | `/heal`, `/feed`, `/fly`, `/speed`, `/god`                          | `essentiallystateless.heal` etc.      |
| Teleport  | `/tp`, `/tphere`, `/tpall`, `/tppos`, `/top`, `/bottom`, `/jump`    | `essentiallystateless.tp` etc.        |
| Inventory | `/anvil`, `/workbench`, `/enderchest`, `/invsee`, `/clearinventory` | `essentiallystateless.anvil` etc.     |
| Items     | `/give`, `/item`, `/more`, `/repair`, `/enchant`, `/hat`, `/skull`  | `essentiallystateless.give` etc.      |
| World     | `/lightning`, `/remove`, `/spawnmob`, `/tree`, `/break`             | `essentiallystateless.lightning` etc. |
| Admin     | `/kick`, `/ban`, `/broadcast`, `/sudo`                              | `essentiallystateless.kick` etc.      |
| Info      | `/gc`, `/list`, `/whois`, `/near`, `/seen`, `/ping`                 | `essentiallystateless.gc` etc.        |
| Fun       | `/me`, `/fireball`, `/book`, `/editsign`                            | `essentiallystateless.me` etc.        |

## Support & Development

### Reporting Issues

- **GitHub Issues** - [Report bugs and feature requests](https://github.com/bruhmomen69/OpenProjects)
- **Discord** - [Community support](https://discord.com/invite/A6NG8DaAb7)

### Contributing

- **Pull Requests** - Contributions welcome
- **Translations** - Help translate to other languages
- **Testing** - Report compatibility issues

---

**EssentiallyStateless** - Essential commands, reimagined for the modern era.
