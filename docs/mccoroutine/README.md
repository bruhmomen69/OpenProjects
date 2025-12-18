# MCCoroutine for Folia — Practical Guide

This guide distills the official MCCoroutine docs with a focus on Paper/Folia. It explains how to structure your plugin, which dispatchers to use, and how to safely access Minecraft resources when there is no single "main thread".

- MCCoroutine home: https://shynixn.github.io/MCCoroutine/wiki/site/coroutine/
- Source docs referenced: coroutine.md, plugin.md, installation.md, tasks.md, faq.md

## Documentation set in this folder

- **installation.md** — Folia-focused installation and shading guidance
- **coroutine.md** — Starting coroutines and switching contexts on Folia
- **plugin.md** — SuspendingJavaPlugin lifecycle on Folia
- **tasks.md** — Delays, repeating tasks, tick helpers
- **faq.md** — Common questions and Folia-specific answers

## Why Folia is different

- Folia removes the global server main thread. Work happens on multiple region-/entity-specific threads.
- Accessing a Minecraft resource must happen on the thread that “owns” that resource (global region, a world region, or an entity).
- Therefore, you can no longer treat your plugin code as globally single-threaded. You need a strategy for your own plugin data, and explicit dispatching for Minecraft calls.

## MCCoroutine’s model for Folia

MCCoroutine provides a set of dispatchers and an opinionated pattern:

- plugin.mainDispatcher — Your plugin’s personal "main thread" for your own data and logic. Do not call Bukkit APIs here.
- plugin.globalRegionDispatcher — For global server state (e.g., gamerules). Folia global region thread.
- plugin.regionDispatcher(location) — For world/region-state at a specific Location.
- plugin.entityDispatcher(entity) — For entity-bound operations (Player, ArmorStand, etc.).
- plugin.asyncDispatcher — For general asynchronous work (I/O, CPU) outside Folia regions.

MCCoroutine falls back automatically to Bukkit schedulers if Folia schedulers are not present, so the same code can run on non-Folia servers with Bukkit-like semantics.

## Executors and Dispatchers (Folia)

- **mainDispatcher**
  - Thread: Your plugin’s personal main thread.
  - Use for: Your own data/state, ordering-sensitive logic. Do not call Bukkit APIs here.
  - Example:
    ```kotlin
    plugin.launch { // defaults to mainDispatcher
        val data = repo.load(id)
    }
    ```
- **globalRegionDispatcher**
  - Thread: Folia Global Region thread.
  - Use for: Global server operations (e.g., gamerules, global config touching server state).
  - Example:
    ```kotlin
    kotlinx.coroutines.withContext(plugin.globalRegionDispatcher) {
        // update global gamerules
    }
    ```
- **regionDispatcher(location)**
  - Thread: Region thread owning the given Location.
  - Use for: World/region-bound operations (read/write blocks, region state).
  - Example:
    ```kotlin
    kotlinx.coroutines.withContext(plugin.regionDispatcher(loc)) {
        val type = loc.block.type
    }
    ```
- **entityDispatcher(entity)**
  - Thread: The thread that currently owns the Entity.
  - Use for: Teleports, messaging, metadata, entity interaction.
  - Example:
    ```kotlin
    kotlinx.coroutines.withContext(plugin.entityDispatcher(player)) {
        player.sendMessage("Welcome!")
    }
    ```
- **asyncDispatcher**
  - Thread: General async pool.
  - Use for: DB, files, CPU. Prefer `Dispatchers.IO` for plain I/O to reduce coupling.
  - Example:
    ```kotlin
    kotlinx.coroutines.withContext(plugin.asyncDispatcher) {
        repository.save(data)
    }
    ```

Notes:
- Always hop to one of the Folia dispatchers before touching Minecraft state.
- On non-Folia servers, these dispatchers fall back to Bukkit semantics (e.g., region/entity dispatcher -> main thread).

### Other platform executors (quick ref)

- **Bukkit**: `minecraftDispatcher`, `asyncDispatcher`.
- **BungeeCord**: `bungeeCordDispatcher`.
- For non-Folia Bukkit, `plugin.launch {}` starts on the main thread.

## Quick start (Folia)

1) Add dependencies (see installation.md for details) and ensure Kotlin coroutines dependency is present.
2) Use SuspendingJavaPlugin from the Folia artifact to get suspendable lifecycle methods:

```kotlin
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin

class MyPlugin : SuspendingJavaPlugin() {
    override suspend fun onEnableAsync() {
        // Runs on Folia Global Region thread; context switches block startup until returned.
        // Initialize your repositories/services here (no Bukkit calls in your own data thread).
    }

    override suspend fun onDisableAsync() {
        // Global Region thread as well; clean up.
    }
}
```

3) Launch work on your plugin’s main dispatcher, then hop to the correct Minecraft dispatcher for side effects:

```kotlin
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext

fun doWorkFor(entity: org.bukkit.entity.Entity) {
    plugin.launch { // Equivalent to plugin.launch(plugin.mainDispatcher)
        val data = repository.load(entity.uniqueId) // your plugin’s “main thread”

        withContext(plugin.entityDispatcher(entity)) {
            // Safe to access entity (teleport, metadata, messaging, etc.)
            // On Bukkit (fallback), this is the main server thread
        }

        withContext(plugin.asyncDispatcher) {
            // I/O, CPU work, DB saves
        }
    }
}
```

## Migration notes (Bukkit -> Folia)

- Simply switching imports is not enough. Restructure so that:
  - All plugin-owned data access and logic happens on `plugin.mainDispatcher`.
  - All Minecraft interactions are executed on `globalRegionDispatcher`, `regionDispatcher(location)`, or `entityDispatcher(entity)` as appropriate.
- Avoid assuming a single thread for ordering; if ordering matters for your own state, keep that logic on `mainDispatcher`.
- Use `Dispatchers.IO` or `plugin.asyncDispatcher` for blocking I/O.

## Event handlers and commands

- You can still register listeners and command executors as usual. When handling an event, immediately switch to your plugin main dispatcher to compute, then hop to the correct Folia dispatcher to interact with the world/entity.

```kotlin
@org.bukkit.event.EventHandler
fun onJoin(e: org.bukkit.event.player.PlayerJoinEvent) {
    plugin.launch {
        val welcome = service.computeWelcome(e.player.uniqueId)
        withContext(plugin.entityDispatcher(e.player)) {
            e.player.sendMessage(welcome)
        }
    }
}
```

## Delays and repeating tasks

- Prefer `delay(1.ticks)` over hardcoded milliseconds for tick-accurate delays related to Minecraft ticks.
- Repeaters are simple `while`/`for` loops with `delay(...)` inside your current dispatcher context.

## Best practices and pitfalls

- Never call Bukkit APIs from `plugin.mainDispatcher`. It is your data/logic thread, not a Minecraft thread.
- Always hop to the correct dispatcher before touching Minecraft state.
- Avoid `runBlocking` except during plugin shutdown handling; prefer `launch` + `delay` and `withContext`.
- For performance, batch expensive I/O on `Dispatchers.IO` or `plugin.asyncDispatcher`.
- For unit tests, isolate logic that runs on `mainDispatcher` to avoid Folia dependencies.

## FAQ essentials

- Can I run the Folia artifact on a Bukkit server? Yes, MCCoroutine falls back to Bukkit schedulers when Folia is not available.
- Do suspendable listeners/commands add overhead? Registration uses reflection but runtime handling is as fast as ordinary handlers.
- Can I cancel events after a suspension? No; cancellation must happen before the first suspension point.

See the additional docs in this folder for installation, lifecycle patterns, coroutine context switching, delayed/repeating tasks, and Q&A tailored for Folia.
