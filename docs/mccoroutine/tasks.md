# Delayed and repeating tasks (Folia)

Coroutines make delays and repeaters straightforward without blocking Folia region threads.

## Delay

If already in a suspend function, call `delay(...)`. If not, `plugin.launch { delay(...) }`.

```kotlin
suspend fun sayHelloOnce() {
    println("Will say hello in 2s")
    kotlinx.coroutines.delay(2000)
    println("hello")
}

fun sayHelloFromHandler() {
    plugin.launch {
        kotlinx.coroutines.delay(2000)
        println("hello")
    }
}
```

## Tick-accurate delays

Prefer `delay(1.ticks)` for work related to server ticks (Bukkit/Folia tick helper). It’s more accurate than raw milliseconds on the main/region threads.

```kotlin
import com.github.shynixn.mccoroutine.bukkit.ticks // tick extension is shared semantics

plugin.launch {
    kotlinx.coroutines.delay(20.ticks) // ~1s
}
```

## Repeating

Use loops with `delay(...)` inside the dispatcher you need.

```kotlin
plugin.launch {
    repeat(10) {
        kotlinx.coroutines.delay(2000)
        println("hello")
    }
}
```

## Mini-game style loop (sketch)

Keep game state on `plugin.mainDispatcher`. Hop to entity/region for world effects.

```kotlin
class MiniGame {
    private var started = false
    private val players = java.util.HashSet<org.bukkit.entity.Player>()

    fun join(p: org.bukkit.entity.Player) { if (!started) players.add(p) }

    suspend fun start() {
        if (started) return
        started = true

        for (i in 20 downTo 1) {
            players.forEach { it.sendMessage("Starting in ${i}s") }
            kotlinx.coroutines.delay(1000)
        }

        run()
    }

    private suspend fun run() {
        var remaining = 300
        while (started && remaining > 0) {
            players.forEach { it.sendMessage("Ends in ${remaining}s") }
            kotlinx.coroutines.delay(1000)
            remaining--
        }
        started = false
    }
}
```

In handlers:

```kotlin
@org.bukkit.event.EventHandler
fun onJoin(e: org.bukkit.event.player.PlayerJoinEvent) {
    plugin.launch { game.join(e.player) }
}
```
