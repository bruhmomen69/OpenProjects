# RegionRestore PaperMC Module

Core implementation of the RegionRestore plugin for Paper/Folia servers.

## Features

- **Template Management**: Save and restore region snapshots with versioning
- **Mass Cloner**: Auto-allocate and manage multiple region instances from pools
- **Occupancy Tracking**: Track player presence in region instances
- **Restore Scheduling**: Timer-based and event-driven restore triggers
- **Notifications**: Configurable notifications for restore events
- **Entity Killer**: Kill entities within a region before restore (whitelist/blacklist mode)
- **Selection Wand**: Tracked ItemAPI wand uses deny-by-default use handling (`onUseDeny`) for region corner selection

## Architecture

### Timer Package (`timer/`)

The restore scheduling system has been refactored into a clean, modular architecture:

- **[`RestoreJob`](src/main/kotlin/timer/RestoreJob.kt)**: Data class representing a restore operation
- **[`SchedulerService`](src/main/kotlin/timer/SchedulerService.kt)**: Main coordinator for scheduling restores (countdown, repeating, immediate)
- **[`ChunkTicketManager`](src/main/kotlin/timer/ChunkTicketManager.kt)**: Manages chunk loading and plugin chunk tickets
- **[`ChunkLockManager`](src/main/kotlin/timer/ChunkLockManager.kt)**: Handles chunk-level locking to prevent concurrent modifications
- **[`RestoreExecutionContext`](src/main/kotlin/timer/RestoreExecutionContext.kt)**: Shared context for all restore operations
- **[`StreamingRestoreExecutor`](src/main/kotlin/timer/StreamingRestoreExecutor.kt)**: Executes restores in streaming mode (lower memory, chunks restored as they load)
- **[`LegacyRestoreExecutor`](src/main/kotlin/timer/LegacyRestoreExecutor.kt)**: Executes restores in legacy mode (preload all chunks, then restore)

This separation allows each component to have a single responsibility and makes the codebase more maintainable and testable.

## Placeholder Integration

RegionRestore provides placeholder support through both PlaceholderAPI and MiniPlaceholders.
The hooks are loaded automatically if the respective plugins are installed.

### PlaceholderAPI Placeholders

Format: `%regionrestore_<placeholder>%`

| Placeholder | Description |
|-------------|-------------|
| `instance_count` | Total number of instances |
| `instance_count_pooled` | Count of pooled instances |
| `instance_count_manual` | Count of manual instances |
| `instance_count_<world>` | Instance count for a specific world |
| `template_count` | Total number of templates |
| `template_<name>_versions` | Number of versions for a template |
| `template_<name>_active_version` | Active version ID for a template |
| `pool_count` | Total number of configured pools |
| `pool_<world>_<template>_count` | Instance count for a specific pool |
| `pool_<world>_<template>_target` | Target instance count for a pool |
| `pool_<world>_<template>_occupied` | Occupied instance count for a pool |
| `instance_<uuid>_world` | World name of an instance |
| `instance_<uuid>_template` | Template name of an instance |
| `instance_<uuid>_occupancy` | Occupancy count of an instance |
| `instance_<uuid>_type` | Instance type (pooled/manual) |
| `instance_<uuid>_originx` | Origin chunk X coordinate |
| `instance_<uuid>_originz` | Origin chunk Z coordinate |
| `instance_<uuid>_sizex` | Size in chunks (X) |
| `instance_<uuid>_sizez` | Size in chunks (Z) |
| `instance_<uuid>_minblockx` | Minimum block X coordinate |
| `instance_<uuid>_maxblockx` | Maximum block X coordinate |
| `instance_<uuid>_minblockz` | Minimum block Z coordinate |
| `instance_<uuid>_maxblockz` | Maximum block Z coordinate |

### MiniPlaceholders Placeholders

Format: `<regionrestore_<placeholder>>`

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

## Dependencies

- Paper API 1.21.4+
- MCCoroutine (Folia support)
- Configurate (YAML/HOCON config)
- Lamp (command framework)
- Kyori Adventure (MiniMessage)

### Optional Dependencies

- **PlaceholderAPI**: For `%regionrestore_*%` placeholders
- **MiniPlaceholders**: For `<regionrestore_*>` placeholders in MiniMessage

## Entity Killer

The Entity Killer feature allows you to kill entities within a region before a template is restored. This is useful for clearing mobs, items, or other entities that shouldn't persist across restores.

### Configuration

```yaml
entityKiller:
  enabled: false  # Enable entity killing (default: false)
  whitelistMode: false  # If true, only kill listed entity types; if false, kill all except listed types
  entityTypes:
    - PLAYER  # In blacklist mode (default), these entities are NOT killed
```

### Modes

| Mode | `whitelistMode` | Behavior |
|------|-----------------|----------|
| Blacklist | `false` (default) | All entities EXCEPT those listed in `entityTypes` are killed |
| Whitelist | `true` | Only entities listed in `entityTypes` are killed |

### Default Configuration

By default, entity killing is **disabled**. When enabled with the default blacklist mode:

- Players are protected (not killed) - they will be teleported out via the vacancy system
- All other entity types (mobs, items, etc.) will be killed

### Entity Type Names

Use standard Minecraft entity type names in the configuration:

| Category | Entity Types |
|----------|-------------|
| Hostile | ZOMBIE, SKELETON, CREEPER, SPIDER, etc. |
| Passive | COW, PIG, CHICKEN, SHEEP, HORSE, etc. |
| Projectiles | ARROW, FIREBALL, SPECTRAL_ARROW, TRIDENT |
| Items | DROPPED_ITEM |
| Vehicles | MINECART, BOAT |
| Players | PLAYER |

### Example Configurations

**Clear all mobs except players:**
```yaml
entityKiller:
  enabled: true
  whitelistMode: false
  entityTypes:
    - PLAYER
```

**Only clear dropped items:**
```yaml
entityKiller:
  enabled: true
  whitelistMode: true
  entityTypes:
    - DROPPED_ITEM
```

**Clear all hostile mobs:**
```yaml
entityKiller:
  enabled: true
  whitelistMode: true
  entityTypes:
    - ZOMBIE
    - SKELETON
    - CREEPER
    - SPIDER
    - PHANTOM
```
