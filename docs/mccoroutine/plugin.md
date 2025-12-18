# Suspending Plugin — Folia lifecycle

Use the Folia SuspendingJavaPlugin to get suspendable lifecycle hooks and proper startup blocking semantics.

```kotlin
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin

class MyPlugin : SuspendingJavaPlugin() {
    override suspend fun onEnableAsync() {
        // Runs on the Folia Global Region thread.
        // Context switches here block enable until control is returned.
        // Initialize services, warm caches, run migrations, etc.
    }

    override suspend fun onDisableAsync() {
        // Global Region thread; cleanup.
    }
}
```

Notes:
- onEnableAsync/onDisableAsync mirror the Bukkit variant but use Folia’s threading model.
- The enable sequence is only considered finished once your coroutine returns to the caller after suspensions — gives a clean startup order across plugins.

## Using a database from the plugin class

- Perform I/O on `Dispatchers.IO` or `plugin.asyncDispatcher`.
- Keep your in-memory state on `plugin.mainDispatcher`.
- Interact with entities/regions by switching dispatcher.

Example sketch:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerRepo {
    suspend fun load(id: java.util.UUID): PlayerData = withContext(Dispatchers.IO) {
        // query DB
        PlayerData(id)
    }

    suspend fun save(data: PlayerData) = withContext(Dispatchers.IO) {
        // update DB
    }
}

class MyPlugin : com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin() {
    private val repo = PlayerRepo()

    override suspend fun onEnableAsync() {
        // Global region thread
        // e.g. migrate schema
    }
}
```

For a larger end-to-end example, combine this with the patterns on the coroutine and tasks pages in this folder.
