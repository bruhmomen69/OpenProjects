# EssentiallyStateless

A comprehensive stateless essential commands plugin for PaperMC that provides over 70 essential server commands without storing any runtime-modified state outside the Minecraft server itself.

## What is EssentiallyStateless?

EssentiallyStateless is a complete rewrite of classic essential commands plugins, designed specifically for modern PaperMC servers. Unlike traditional plugins that store player data in external files, this plugin operates entirely within Minecraft's native systems - all state is stored in player data and world data.

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

### Build from Source
```bash
git clone <repository>
cd MiniMessageChatPlugin
./gradlew :EssentiallyStateless:build
# JAR will be in: EssentiallyStateless/build/libs/
```

## Configuration

### Configuration File
The plugin generates a comprehensive configuration file at `plugins/EssentiallyStateless/config.conf`:

```hocon
# Language for translations
language = "en"

# Speed settings
defaultFlySpeed = 0.1
maxFlySpeed = 1.0
defaultWalkSpeed = 0.2
maxWalkSpeed = 1.0

# Player settings
defaultHealAmount = 20.0
defaultFeedAmount = 20

# Limits
maxRemoveEntities = 1000
maxSpawnMobs = 100
nearRadius = 200

# Broadcast formats
broadcastFormat = "<red>[Broadcast]</red> <message>"
worldBroadcastFormat = "<yellow>[<world>]</yellow> <message>"
meFormat = "<gray>* <player> <action></gray>"
```

### Translation System
- **Multi-language support** with automatic file generation
- **Translation files** located in `plugins/EssentiallyStateless/translations/`
- **Customizable messages** for all commands
- **Placeholder support** with MiniMessage formatting

## Permissions

### Permission Structure
All commands use the permission format: `essentiallystateless.<command>`

### Permission Groups

#### Basic Users
```
essentiallystateless.gamemode
essentiallystateless.time
essentiallystateless.weather
essentiallystateless.heal
essentiallystateless.feed
essentiallystateless.fly
essentiallystateless.speed
essentiallystateless.god
essentiallystateless.ext
essentiallystateless.rest
essentiallystateless.exp
essentiallystateless.kill
essentiallystateless.burn
essentiallystateless.tp
essentiallystateless.top
essentiallystateless.bottom
essentiallystateless.jump
essentiallystateless.world
essentiallystateless.anvil
essentiallystateless.workbench
essentiallystateless.enderchest
essentiallystateless.disposal
essentiallystateless.clearinventory
essentiallystateless.item
essentiallystateless.more
essentiallystateless.repair
essentiallystateless.hat
essentiallystateless.skull
essentiallystateless.itemname
essentiallystateless.itemlore
essentiallystateless.condense
essentiallystateless.lightning
essentiallystateless.tree
essentiallystateless.bigtree
essentiallystateless.break
essentiallystateless.me
essentiallystateless.fireball
essentiallystateless.book
essentiallystateless.editsign
essentiallystateless.potion
essentiallystateless.firework
essentiallystateless.gc
essentiallystateless.list
essentiallystateless.whois
essentiallystateless.near
essentiallystateless.seen
essentiallystateless.ping
essentiallystateless.getpos
essentiallystateless.compass
essentiallystateless.depth
essentiallystateless.playtime
```

#### Staff Permissions
```
essentiallystateless.gamemode.others
essentiallystateless.heal.others
essentiallystateless.feed.others
essentiallystateless.fly.others
essentiallystateless.speed.others
essentiallystateless.god.others
essentiallystateless.enderchest.others
essentiallystateless.clearinventory.others
essentiallystateless.give
essentiallystateless.enchant
essentiallystateless.spawnmob
essentiallystateless.spawner
essentiallystateless.remove
essentiallystateless.kick
essentiallystateless.kickall
essentiallystateless.ban
essentiallystateless.unban
essentiallystateless.banip
essentiallystateless.unbanip
essentiallystateless.sudo
essentiallystateless.broadcast
essentiallystateless.broadcastworld
essentiallystateless.reload
```

#### Admin Permission
```
essentiallystateless.admin
```

### LuckPerms Setup Example
```bash
# Give admin access to all commands
/lp group admin permission set essentiallystateless.admin true

# Give basic commands to default players
/lp group default permission set essentiallystateless.gamemode true
/lp group default permission set essentiallystateless.heal true
/lp group default permission set essentiallystateless.fly true

# Give staff access to others commands
/lp group moderator permission set essentiallystateless.heal.others true
/lp group moderator permission set essentiallystateless.fly.others true
```

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
| Category | Commands | Permission |
|----------|----------|------------|
| GameMode | `/gamemode`, `/gmc`, `/gms`, `/gma`, `/gmsp` | `essentiallystateless.gamemode` |
| Time | `/time`, `/day`, `/night`, `/noon`, `/midnight` | `essentiallystateless.time` |
| Weather | `/weather`, `/sun`, `/rain`, `/storm`, `/thunder` | `essentiallystateless.weather` |
| Player | `/heal`, `/feed`, `/fly`, `/speed`, `/god` | `essentiallystateless.heal` etc. |
| Teleport | `/tp`, `/tphere`, `/tpall`, `/tppos`, `/top`, `/bottom`, `/jump` | `essentiallystateless.tp` etc. |
| Inventory | `/anvil`, `/workbench`, `/enderchest`, `/invsee`, `/clearinventory` | `essentiallystateless.anvil` etc. |
| Items | `/give`, `/item`, `/more`, `/repair`, `/enchant`, `/hat`, `/skull` | `essentiallystateless.give` etc. |
| World | `/lightning`, `/remove`, `/spawnmob`, `/tree`, `/break` | `essentiallystateless.lightning` etc. |
| Admin | `/kick`, `/ban`, `/broadcast`, `/sudo` | `essentiallystateless.kick` etc. |
| Info | `/gc`, `/list`, `/whois`, `/near`, `/seen`, `/ping` | `essentiallystateless.gc` etc. |
| Fun | `/me`, `/fireball`, `/book`, `/editsign` | `essentiallystateless.me` etc. |

## Support & Development

### Reporting Issues
- **GitHub Issues** - Report bugs and feature requests
- **Discord** - Community support
- **Wiki** - Detailed documentation and tutorials

### Contributing
- **Pull Requests** - Contributions welcome
- **Translations** - Help translate to other languages
- **Testing** - Report compatibility issues

---

**EssentiallyStateless** - Essential commands, reimagined for the modern era.
