# RegionRestore

**High-Performance World Region Restoration for Paper/Folia Servers**

RegionRestore is a powerful, performance-focused plugin designed to restore parts of your world back to their original state at exceptional speeds. Whether you're managing PvP arenas, mini-game lobbies, or need reliable world state resets, RegionRestore delivers sub-10ms restoration for small regions while maintaining server stability even for massive operations.

---

## Why RegionRestore?

### Lightning-Fast Restores
Small regions (like duels arenas) restore in **under 10 milliseconds**, ensuring minimal disruption to gameplay.

### Chunk Streaming Technology
Large restores use intelligent **chunk-by-chunk streaming** that progressively loads and restores chunks rather than loading everything at once. This prevents overloading Minecraft's chunk system and maintains consistent TPS even during extensive restoration operations.

### Instant Light Restoration
Unlike other solutions that trigger expensive light recalculation, RegionRestore features **instant light restore** technology. This dramatically reduces CPU usage during restoration operations and eliminates the lag spikes commonly associated with lighting updates.

### Perfect for Arena Management
Ideal for:
- **PvP Duels Arenas** - Instant reset between matches
- **Mini-Game Lobbies** - Restore lobby state after each game
- **Event Arenas** - Reset builds, terrain, and structures
- **Practice Arenas** - Fast environment resets
- **Plot Worlds** - Mass plot regeneration

---

## Supported Versions

| Platform | Supported Versions |
|----------|-------------------|
| Paper | 1.21.4 - 1.21.11 |
| Folia | 1.21.4+ (Full support) |

### Minecraft Version Support
RegionRestore uses NMS adapters for maximum performance on specific versions:
- 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11

> **Note:** The plugin requires a matching NMS adapter for your server version. Updates are released shortly after new Minecraft versions.

---

## Dependencies

### Required
- **Paper API 1.21.4+** (or Folia)

### Optional (Soft Dependencies)
- **PlaceholderAPI** - Access RegionRestore data via `%regionrestore_*%` placeholders
- **MiniPlaceholders** - Use `<regionrestore_*>` placeholders in MiniMessage-formatted text

---

## Placeholders

<details>
<summary><strong>PlaceholderAPI Placeholders</strong></summary>

| Placeholder | Description |
|-------------|-------------|
| `%regionrestore_instance_count%` | Total number of instances |
| `%regionrestore_instance_count_pooled%` | Count of pooled instances |
| `%regionrestore_instance_count_manual%` | Count of manual instances |
| `%regionrestore_instance_count_<world>%` | Instance count for a specific world |
| `%regionrestore_template_count%` | Total number of templates |
| `%regionrestore_template_<name>_versions%` | Number of versions for a template |
| `%regionrestore_template_<name>_active_version%` | Active version ID for a template |
| `%regionrestore_pool_count%` | Total number of configured pools |
| `%regionrestore_pool_<world>_<template>_count%` | Instance count for a specific pool |
| `%regionrestore_pool_<world>_<template>_target%` | Target instance count for a pool |
| `%regionrestore_pool_<world>_<template>_occupied%` | Occupied instance count for a pool |
| `%regionrestore_instance_<uuid>_world%` | World name of an instance |
| `%regionrestore_instance_<uuid>_template%` | Template name of an instance |
| `%regionrestore_instance_<uuid>_occupancy%` | Occupancy count of an instance |
| `%regionrestore_instance_<uuid>_type%` | Instance type (pooled/manual) |
| `%regionrestore_instance_<uuid>_originx%` | Origin chunk X coordinate |
| `%regionrestore_instance_<uuid>_originz%` | Origin chunk Z coordinate |
| `%regionrestore_instance_<uuid>_sizex%` | Size in chunks (X) |
| `%regionrestore_instance_<uuid>_sizez%` | Size in chunks (Z) |
| `%regionrestore_instance_<uuid>_minblockx%` | Minimum block X coordinate |
| `%regionrestore_instance_<uuid>_maxblockx%` | Maximum block X coordinate |
| `%regionrestore_instance_<uuid>_minblockz%` | Minimum block Z coordinate |
| `%regionrestore_instance_<uuid>_maxblockz%` | Maximum block Z coordinate |

</details>

<details>
<summary><strong>MiniPlaceholders Placeholders</strong></summary>

| Placeholder | Description |
|-------------|-------------|
| `<regionrestore_instance_count>` | Total number of instances |
| `<regionrestore_instance_count_pooled>` | Count of pooled instances |
| `<regionrestore_instance_count_manual>` | Count of manual instances |
| `<regionrestore_template_count>` | Total number of templates |
| `<regionrestore_pool_count>` | Total number of pools |
| `<regionrestore_instance_count_world:'world_name'>` | Instance count for a world |
| `<regionrestore_pool_instance_count:'world':'template'>` | Instance count for a pool |
| `<regionrestore_pool_target:'world':'template'>` | Target count for a pool |
| `<regionrestore_pool_occupied:'world':'template'>` | Occupied count for a pool |
| `<regionrestore_template_versions:'template_name'>` | Version count for a template |
| `<regionrestore_template_active_version:'template_name'>` | Active version for a template |

</details>

---

## Commands

All commands use `/regionrestore` (or aliases `/rr`, `/arena`, `/arenas`).

<details>
<summary><strong>GUI Command</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr gui` | Open the interactive management GUI | `regionrestore.gui` |

The GUI provides visual management of templates, instances, pools, and timers with intuitive menu navigation.

</details>

<details>
<summary><strong>Template Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr template create <name> <minX> <minZ> <maxX> <maxZ> [world]` | Create a new template from world coordinates | `regionrestore.template.create` |
| `/rr template list` | List all saved templates | `regionrestore.template.list` |
| `/rr template info <name>` | View template details and versions | `regionrestore.template.info` |
| `/rr template setactive <name> <versionId>` | Set the active version for a template | `regionrestore.template.setactive` |
| `/rr template delete <name>` | Delete a template | `regionrestore.template.delete` |

**Versioning System:** Templates support multiple versions, allowing you to maintain different snapshots of the same region. The "active" version is used by default for restores.

</details>

<details>
<summary><strong>Restore Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr restore <template> [version/active] [scope]` | Restore template at its original position | `regionrestore.restore` |
| `/rr restore at <template> <world> <x> <z> [version]` | Restore template at specific coordinates | `regionrestore.restore` |
| `/rr restore in <template> <seconds> [version] [scope]` | Schedule restore with countdown | `regionrestore.restorein` |
| `/rr restore version <template> [version/active] [scope]` | Restore specific version | `regionrestore.restore` |

**Audience Scopes:**
- `NEARBY` - Notify players within configured radius
- `WORLD` - Notify all players in the world
- `SERVER` - Notify all online players
- `NONE` - Silent restore

</details>

<details>
<summary><strong>Instance Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr instance create <template> <chunkX> <chunkZ> [world] [version] [boot] [vacate] [interval]` | Create a tracked region instance | `regionrestore.instance.create` |
| `/rr instance list all` | List all instances | `regionrestore.instance.list` |
| `/rr instance list world <world> [type]` | List instances in a world | `regionrestore.instance.list` |
| `/rr instance info <instanceId>` | View instance details | `regionrestore.instance.info` |
| `/rr instance delete <instanceId>` | Delete an instance | `regionrestore.instance.delete` |
| `/rr instance restore <instanceId>` | Trigger restore for an instance | `regionrestore.instance.restore` |

**Instance Types:**
- **Pooled** - Auto-managed instances for mass cloning
- **Manual** - Individually created and managed instances

**Instance Options:**
- `boot` - Restore on server startup
- `vacate` - Restore when all players leave
- `interval` - Auto-restore interval in seconds

</details>

<details>
<summary><strong>Cloner (Pool) Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr cloner status [world]` | View pool status and configuration | `regionrestore.cloner.status` |
| `/rr cloner restore [world] [template]` | Trigger restore for pool instances | `regionrestore.cloner.restore` |
| `/rr cloner regen [world] [--force]` | Regenerate all pool instances | `regionrestore.cloner.regen` |

**Mass Cloner:** Automatically manages multiple copies (instances) of a template across configured worlds. Perfect for duels servers that need dozens or hundreds of identical arenas.

</details>

<details>
<summary><strong>Timer Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr timer set instance <instanceId> <intervalSeconds> [scope]` | Set repeating restore timer for instance | `regionrestore.timer.set` |
| `/rr timer set template <templateName> <intervalSeconds> [scope]` | Set timer for template at original position | `regionrestore.timer.set` |
| `/rr timer cancel id <instanceId>` | Cancel timer by instance ID | `regionrestore.timer.cancel` |
| `/rr timer cancel template-all <templateName>` | Cancel all timers for a template | `regionrestore.timer.cancel` |

</details>

<details>
<summary><strong>Selection Commands</strong></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/rr selection wand` | Get the selection wand item | `regionrestore.wand` |
| `/rr selection` | View current selection info | `regionrestore.selection` |
| `/rr selection clear` | Clear your selection | `regionrestore.selection` |
| `/rr selection create <name>` | Create template from current selection | `regionrestore.template.create` |

**Using the Selection Wand:**
- Right-click to set positions (alternates between pos1 and pos2)
- Left-click to view current selection info
- Supports cross-world selection detection

</details>

---

## Scheduling Features

RegionRestore provides a comprehensive scheduling system that allows you to automate restores based on various triggers and conditions.

<details>
<summary><strong>Countdown Scheduling (Delayed/Scheduled Restores)</strong></summary>

Schedule restores to happen after a countdown delay with automatic player notifications. This allows you to schedule restores for a future time and warn players before restoration occurs.

```
/rr restore in <template> <seconds> [version] [scope]
```

**Features:**
- **Scheduled Restore** - Delay restoration by specified seconds
- **Configurable Announce Points** - Notify players at specific intervals (default: 60, 30, 10, 5, 4, 3, 2, 1 seconds)
- **Audience Scope Control** - Choose who receives countdown notifications:
  - `NEARBY` - Players within configured radius
  - `WORLD` - All players in the world
  - `SERVER` - All online players
  - `NONE` - Silent countdown
- **Plurality-Aware Messages** - Automatic "second/seconds" grammar

**Example - Schedule arena restore in 30 seconds:**
```
/rr restore in my_arena 30 WORLD
```
Announces to all players: "Restoring region in 30 seconds..." counting down to "Restoring region in 1 second..."

**Tip:** Use this for scheduled maintenance, event endings, or to give players time to finish their current activity before the restore.

</details>

<details>
<summary><strong>Repeating/Interval Scheduling</strong></summary>

Set up automatic periodic restores for instances or templates.

```
/rr timer set instance <instanceId> <intervalSeconds> [scope]
/rr timer set template <templateName> <intervalSeconds> [scope]
```

**Features:**
- **Instance-Based** - Timer follows a specific region instance
- **Template-Based** - Creates a hidden instance at template's original location
- **Persistent** - Timers survive server restarts
- **Configurable Scope** - Per-timer audience notification settings

**Use Cases:**
- Event arenas that reset every 5 minutes
- Practice arenas that auto-reset every 60 seconds
- Lobby areas that refresh periodically

**Cancellation:**
```
/rr timer cancel id <instanceId>        # Cancel specific instance timer
/rr timer cancel template-all <name>    # Cancel all timers for a template
```

</details>

<details>
<summary><strong>Boot Restoration</strong></summary>

Automatically restore regions when the server starts.

**Configuration:**
```yaml
massCloner:
  worlds:
    - name: duels_world
      pools:
        - templateName: classic_arena
          restoreOnBoot: true
```

**Instance-Level:**
```
/rr instance create <template> <x> <z> [world] [version] true false [interval]
```
(5th parameter `true` enables boot restore)

**Use Cases:**
- Reset all arenas on server restart
- Ensure plots are clean after maintenance
- Restore event areas to known state

</details>

<details>
<summary><strong>Vacate Restoration</strong></summary>

Automatically restore a region when all players leave it.

**How It Works:**
- Tracks player occupancy in real-time
- Monitors chunk movements, teleports, world changes, joins, and quits
- Triggers restore after all players have left + configurable delay

**Configuration:**
```yaml
massCloner:
  worlds:
    - name: duels_world
      pools:
        - templateName: classic_arena
          restoreOnVacate: true
```

**Instance-Level:**
```
/rr instance create <template> <x> <z> [world] [version] [boot] true [interval]
```
(6th parameter `true` enables vacate restore)

**Configuration Options:**
```yaml
massCloner:
  vacateDelaySeconds: 5  # Delay after last player leaves before restoring
```

**Use Cases:**
- Duels/PvP arenas that auto-reset after matches
- Mini-game instances that clean up when finished
- Private arenas that refresh between uses

</details>

<details>
<summary><strong>Concurrent Restore Management</strong></summary>

Prevent server overload with intelligent concurrent restore limiting.

**Configuration:**
```yaml
restore:
  maxConcurrentRestores: 5  # Maximum simultaneous restores
```

**Behavior:**
- When limit is reached, new restores are skipped with configurable notification
- Prevents TPS drops from too many simultaneous chunk operations
- Restores automatically queue and execute when slots become available

**Notification:**
Players receive a message explaining why the restore was skipped:
> "Region restore skipped: maximum concurrent restores (5) reached"

</details>

<details>
<summary><strong>Scheduling Configuration Examples</strong></summary>

**Example 1: Duels Arena with Vacate**
```yaml
massCloner:
  vacateDelaySeconds: 3  # 3 second delay after last player leaves
  worlds:
    - name: duels
      pools:
        - templateName: 1v1_arena
          count: 20
          restoreOnVacate: true
          restoreOnBoot: false
```

**Example 2: Event Arena with Repeating Timer**
```
# Create instance at specific location
/rr instance create event_arena 100 100 events 0 false false

# Set 5-minute repeating timer with WORLD notifications
/rr timer set instance <instance-id> 300 WORLD
```

**Example 3: Lobby with Boot + Timer**
```yaml
massCloner:
  worlds:
    - name: lobby
      pools:
        - templateName: main_lobby
          count: 1
          restoreOnBoot: true
          restoreIntervalSeconds: 3600  # Restore every hour
```

**Example 4: Silent Background Restore**
```
# Restore with no notifications
/rr restore in silent_arena 10 NONE
```

</details>

---

## Permissions

<details>
<summary><strong>Complete Permission List</strong></summary>

| Permission | Description | Default |
|------------|-------------|---------|
| `regionrestore.*` | Grants all RegionRestore permissions | OP |
| `regionrestore.gui` | Open the management GUI | OP |
| `regionrestore.template.create` | Create templates | OP |
| `regionrestore.template.list` | List templates | OP |
| `regionrestore.template.info` | View template info | OP |
| `regionrestore.template.setactive` | Set active version | OP |
| `regionrestore.template.delete` | Delete templates | OP |
| `regionrestore.restore` | Restore templates | OP |
| `regionrestore.restorein` | Schedule timed restores | OP |
| `regionrestore.instance.create` | Create instances | OP |
| `regionrestore.instance.list` | List instances | OP |
| `regionrestore.instance.info` | View instance info | OP |
| `regionrestore.instance.delete` | Delete instances | OP |
| `regionrestore.instance.restore` | Trigger instance restore | OP |
| `regionrestore.cloner.status` | View cloner status | OP |
| `regionrestore.cloner.restore` | Trigger pool restores | OP |
| `regionrestore.cloner.regen` | Regenerate pools | OP |
| `regionrestore.timer.set` | Set restore timers | OP |
| `regionrestore.timer.cancel` | Cancel timers | OP |
| `regionrestore.wand` | Use selection wand | OP |
| `regionrestore.selection` | Manage selections | OP |

</details>

---

## Configuration

Key configuration options available in `config.yml`:

### Restore Settings
```yaml
restore:
  # How fast to load chunks (50% = low TPS impact, 1000% = realtime)
  taskChunkLoadThrottle: 300
  
  # Unload chunks after restoration (recommended for large areas)
  unload: true
  
  # Instant chunk unloading vs queued
  unloadInstant: false
  
  # Update lighting (disabled by default for instant light restore)
  updateLight: false
  
  # Stream large restores chunk-by-chunk
  streamingRestore: false
  
  # Fully async restore (experimental)
  asyncRestore: null  # null = auto-enable when compatible
```

### Template Settings
```yaml
templates:
  # Template cache settings for performance
  cacheTtlMinutes: 10
  cacheMaxSize: 50
  
  # Preload templates on boot
  preloadOnBoot: true
```

### Notification Settings
```yaml
notifications:
  # Who receives restore notifications
  defaultAudienceScope: NEARBY  # NEARBY, WORLD, SERVER, NONE
  
  # Radius for NEARBY scope
  nearbyRadiusBlocks: 50
  
  # Customize messages, titles, sounds for restore events
  countdownTick:
    message: "<yellow>Restoring region in <seconds> second<seconds,s>..."
  restoreStarted:
    message: "<yellow>Region restore started..."
  restoreCompleted:
    message: "<green>Region restore completed!"
    title: "<green>DONE!"
    sound: "block_anvil_use"
```

---

## Use Cases

### Duels Server
Create a pool of 50 identical PvP arenas that auto-restore when players leave:
```yaml
massCloner:
  worlds:
    - name: duels_world
      pools:
        - templateName: classic_arena
          count: 50
          separationChunks: 10
          restoreOnVacate: true
```

### Event Arena
Restore an arena on a timer with countdown announcements:
```
/rr timer set template event_arena 300 SERVER
```

### Practice Server
Instantly reset practice arenas between rounds:
```
/rr restore my_arena
```

### Plot World
Mass regenerate all plots on server restart:
```yaml
pools:
  - templateName: default_plot
    count: 100
    restoreOnBoot: true
```

---

## Performance Notes

- **Small Regions** (< 100 chunks): Sub-10ms restore times
- **Large Regions**: Uses chunk streaming to maintain TPS
- **Light Updates**: Disabled by default (instant light restore)
- **Async Operations**: Optional fully async restoration
- **Chunk Unloading**: Automatic cleanup prevents memory leaks

---

## Support

For support, feature requests, or bug reports, please use the GitHub issue tracker.

**Website:** https://github.com/bruhmomen69/RegionRestore

---

*RegionRestore - Restore your worlds at the speed of thought.*
