# SchedulerService Refactoring Summary

## Overview

The `SchedulerService` has been refactored to split a monolithic 648-line class into multiple focused managers and services. This improves code organization, maintainability, and testability.

## New File Structure

```
RegionRestore/PaperMC/src/main/kotlin/timer/
├── SchedulerService.kt          # Main entry point (refactored, ~170 lines)
├── chunk/
│   ├── ChunkTicketManager.kt    # Chunk ticket management (~150 lines)
│   ├── ChunkLockManager.kt      # Chunk locking logic (~150 lines)
│   └── ChunkPreloader.kt        # Chunk preloading (~120 lines)
├── restore/
│   └── RestoreExecutor.kt       # Restore execution logic (~280 lines)
└── job/
    └── RestoreJobManager.kt     # Job scheduling management (~170 lines)
```

## Component Responsibilities

### SchedulerService (Refactored)
**Purpose**: Main entry point for restore scheduling and coordination

**Key Responsibilities**:
- Instantiates and manages all sub-managers
- Provides public API for scheduleRestore(), scheduleRepeatingRestore(), etc.
- Coordinates between managers for restore execution
- Handles job lifecycle and error recovery

### RestoreJobManager (New)
**Purpose**: Manages restore job scheduling, countdowns, and repeating restores

**Key Responsibilities**:
- Manages countdown jobs with configurable announce points
- Handles repeating restore jobs with interval scheduling
- Tracks active restore count and enforces concurrent restore limits
- Provides notification helpers for skipped restores
- Handles job cancellation (cancelCountdown, cancelRepeatingRestore, cancelAll)

### RestoreExecutor (New)
**Purpose**: Handles the actual execution of restore operations

**Key Responsibilities**:
- Chooses between streaming (chunk-by-chunk) and legacy (bulk) restore modes
- Executes streaming restore with chunk-by-chunk processing
- Executes legacy restore with preload-restore-unload workflow
- Creates notification configs for restore events
- Sends notifications for started/completed/failed restores

**Key Methods**:
- `executeRestore()` - Main entry point with callbacks for events
- `executeStreamingRestore()` - Chunk-by-chunk restore for large regions
- `executeLegacyRestore()` - Bulk restore for smaller regions

### ChunkTicketManager (New)
**Purpose**: Manages chunk ticket references to prevent chunk unloading during restores

**Key Responsibilities**:
- Tracks chunk ticket reference counts
- Adds/removes plugin chunk tickets
- Handles delayed unloading based on configuration
- Provides throttling for chunk loading operations
- Creates ticket handles for tracking loaded chunks

### ChunkLockManager (New)
**Purpose**: Manages chunk locking for concurrent restore operations

**Key Responsibilities**:
- Provides mutex-based locking for chunks
- Implements spin-lock mechanism for acquiring neighbor locks
- Tracks lock access counts and manages lock lifecycle
- Provides `LockedChunks` object for safe lock management

**Key Methods**:
- `accessChunkLock()` - Acquires a chunk lock
- `releaseChunkLock()` - Releases a chunk lock
- `acquireChunkAndNeighborLocks()` - Acquires locks for chunk and all neighbors

### ChunkPreloader (New)
**Purpose**: Handles preloading chunks for legacy restore mode

**Key Responsibilities**:
- Preloads all chunks in a region
- Creates ticket handles for tracking loaded chunks
- Provides throttling for chunk load operations
- Waits for all chunks to complete loading

## Behavior Preservation

All original functionality has been preserved:

1. **Restore Scheduling** - Countdowns and repeating restores work exactly as before
2. **Streaming Restore** - Chunk-by-chunk restore logic unchanged, just moved to RestoreExecutor
3. **Legacy Restore** - Preload-restore-unload workflow unchanged
4. **Chunk Ticket Management** - Same reference counting and delayed unloading
5. **Chunk Locking** - Same spin-lock mechanism for concurrent access prevention
6. **Notification System** - All notifications sent at the same times
7. **Concurrent Restore Limits** - Same max concurrent restores enforcement
8. **Cancellation** - All cancellation methods work identically

## Benefits of Refactoring

1. **Separation of Concerns**: Each manager has a single, well-defined responsibility
2. **Improved Testability**: Managers can be tested independently
3. **Better Maintainability**: Changes to one area don't require understanding the entire service
4. **Reduced Complexity**: The original 648-line file is now split into focused components
5. **Code Reuse**: Chunk managers can be used in other contexts if needed
6. **Clearer Architecture**: Dependencies and data flow are more explicit

## Migration Notes

The public API of `SchedulerService` remains unchanged. All existing code using `SchedulerService` continues to work without modification:

```kotlin
// All existing calls continue to work
schedulerService.scheduleRestore(job, countdownSeconds, announcePoints, audienceScope)
schedulerService.scheduleRepeatingRestore(job, intervalSeconds, audienceScope)
schedulerService.cancelCountdown(jobId)
schedulerService.cancelRepeatingRestore(jobId)
schedulerService.cancelAll()
```

Internal implementations have changed but the external behavior is identical.
