# Coroutines on Folia — dispatchers and context switches

This page condenses the official "Kotlin Coroutines and Minecraft Plugins" guide with Folia-first guidance.

## Starting a coroutine

Use `plugin.launch {}` from the Folia artifact (`com.github.shynixn.mccoroutine.folia.launch`). It enters your plugin’s main dispatcher by default.

```kotlin
import com.github.shynixn.mccoroutine.folia.launch

fun anyEntryPoint(entity: org.bukkit.entity.Entity) {
    plugin.launch { // same as plugin.launch(plugin.mainDispatcher)
        // Your plugin data/logic here (do not call Bukkit APIs).
        val value = service.compute(entity.uniqueId)

        // Switch to the correct Folia thread when touching Minecraft state
        kotlinx.coroutines.withContext(plugin.entityDispatcher(entity)) {
            entity.sendMessage("Computed: $value")
        }
    }
}
```

Important for Folia:
- There is no global main thread. Access Minecraft through the correct dispatcher.
- MCCoroutine auto-falls-back to Bukkit schedulers if Folia is unavailable.

## Switching coroutine context (Folia)

Available dispatchers:

- `plugin.mainDispatcher` — Your plugin’s personal main thread. Own data and ordering live here. No Bukkit calls.
- `plugin.globalRegionDispatcher` — Global-region operations (gamerules, global state).
- `plugin.regionDispatcher(location)` — Region-specific world operations for a given Location.
- `plugin.entityDispatcher(entity)` — Entity-specific operations for a given Entity/Player.
- `plugin.asyncDispatcher` — Off-thread I/O or CPU work.

Example:

```kotlin
import kotlinx.coroutines.withContext

fun inspect(location: org.bukkit.Location) {
    plugin.launch {
        val blockType = withContext(plugin.regionDispatcher(location)) {
            location.block.type
        }

        cache.add(blockType)

        withContext(plugin.asyncDispatcher) {
            repository.save(blockType)
        }
    }
}
```

## Execution order with launch + delay

`launch` does not suspend the caller; inside the launched coroutine you can `delay(...)` to suspend logically without blocking threads.

```kotlin
plugin.launch {
    println("start")
    kotlinx.coroutines.delay(1000)
    println("continued after 1s on the same dispatcher")
}
println("caller continues immediately")
```

## Events and commands pattern

Enter via `plugin.launch {}` in handlers, compute on `mainDispatcher`, then hop to `entityDispatcher`/`regionDispatcher` for world interaction.

```kotlin
@org.bukkit.event.EventHandler
fun onInteract(e: org.bukkit.event.player.PlayerInteractEvent) {
    plugin.launch {
        val allowed = acl.check(e.player.uniqueId)
        if (!allowed) return@launch

        // Do world mutation on the correct dispatcher
        kotlinx.coroutines.withContext(plugin.entityDispatcher(e.player)) {
            e.player.sendMessage("OK")
        }
    }
}
```

## Tips

- Prefer `Dispatchers.IO` or `plugin.asyncDispatcher` for DB/files.
- Keep business logic on `mainDispatcher` to maintain ordering without explicit locks.
- Never call Bukkit from `mainDispatcher`.
