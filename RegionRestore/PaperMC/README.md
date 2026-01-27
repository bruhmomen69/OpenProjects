# RegionRestore PaperMC Module

Core implementation of the RegionRestore plugin for Paper/Folia servers.

## Features

- **Template Management**: Save and restore region snapshots with versioning
- **Mass Cloner**: Auto-allocate and manage multiple region instances from pools
- **Occupancy Tracking**: Track player presence in region instances
- **Restore Scheduling**: Timer-based and event-driven restore triggers
- **Notifications**: Configurable notifications for restore events

## Architecture

### Timer Package

The timer package (`bruh.regionrestore.timer`) handles all restore scheduling and execution:

#### [`SchedulerService`](RegionRestore/PaperMC/src/main/kotlin/timer/SchedulerService.kt:65)
Main entry point for scheduling and managing restore operations. Coordinates between various managers.

#### [`RestoreJob`](RegionRestore/PaperMC/src/main/kotlin/timer/SchedulerService.kt:29)
Data class representing a restore job with job state and region bounds.

#### Job Management ([`RestoreJobManager`](RegionRestore/PaperMC/src/main/kotlin/timer/job/RestoreJobManager.kt:23))
- Manages restore job scheduling with countdowns and repeating restores
- Tracks active restore count and enforces concurrent restore limits
- Handles cancellation of countdown and repeating jobs

#### Restore Execution ([`RestoreExecutor`](RegionRestore/PaperMC/src/main/kotlin/timer/restore/RestoreExecutor.kt:23))
- Executes restore operations using streaming (chunk-by-chunk) or legacy (bulk) mode
- Manages notification sending for restore events
- Coordinates with chunk managers for loading and locking

#### Chunk Management

[`ChunkTicketManager`](RegionRestore/PaperMC/src/main/kotlin/timer/chunk/ChunkTicketManager.kt:19)
- Manages chunk ticket references to prevent chunk unloading during restores
- Handles delayed unloading based on configuration
- Provides throttling for chunk loading operations

[`ChunkLockManager`](RegionRestore/PaperMC/src/main/kotlin/timer/chunk/ChunkLockManager.kt:16)
- Manages chunk locking for concurrent restore operations
- Uses mutex-based system with spin locking to prevent concurrent modifications
- Ensures locks are acquired for both the target chunk and its neighbors

[`ChunkPreloader`](RegionRestore/PaperMC/src/main/kotlin/timer/chunk/ChunkPreloader.kt:18)
- Handles preloading chunks for legacy restore mode
- Creates ticket handles for tracking loaded chunks
- Provides throttling for chunk load operations

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
