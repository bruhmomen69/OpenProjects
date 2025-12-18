# FAQ (Folia + MCCoroutine)

## How does MCCoroutine work under the hood?

It wraps each platform’s scheduler. In Folia, dispatchers delegate to the proper Folia scheduler (global, region, entity). On non-Folia, the Folia artifact falls back to Bukkit schedulers transparently.

## Does it add memory or thread overhead?

MCCoroutine itself does not create threads. Kotlin Coroutines may use additional thread pools (e.g., for IO). Memory impact is minimal and depends on your coroutines usage.

## Are suspendable listeners/commands slower?

Registration uses reflection and is slightly slower, but runtime performance is comparable to normal handlers.

## How do I cancel running jobs?

Use your plugin scope:

```kotlin
plugin.scope.coroutineContext.cancelChildren()
```

## Can I cancel an event after suspension?

No. Cancellation must happen before the first suspension. Once you suspend, the event outcome is already decided by the server.

```kotlin
@org.bukkit.event.EventHandler
suspend fun onInteract(e: org.bukkit.event.player.PlayerInteractEvent) {
    // Cancel before any suspension
    e.isCancelled = true
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // read file/db
        kotlinx.coroutines.delay(50)
    }
}
```

## Can I run the Folia variant on Bukkit servers?

Yes. MCCoroutine detects the absence of Folia schedulers and falls back to Bukkit dispatchers.

## What about `runBlocking`?

Avoid it except for shutdown paths that need to complete synchronously. Prefer `launch`, `withContext`, and `delay`.
