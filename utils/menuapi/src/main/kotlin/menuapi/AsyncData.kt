package bruh.zchat.utils.menuapi

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * State model for async menu-backed data.
 */
sealed interface AsyncMenuState<out T> {
    data object Idle : AsyncMenuState<Nothing>
    data object Loading : AsyncMenuState<Nothing>
    data class Ready<T>(val value: T, val loadedAt: Instant) : AsyncMenuState<T>
    data class Error(val cause: Throwable, val failedAt: Instant) : AsyncMenuState<Nothing>
}

/**
 * Data loading strategy for menus that need async-backed and potentially stale data.
 */
fun interface AsyncMenuDataSource<T> {
    fun load(player: Player): CompletableFuture<T>
}

/**
 * Freshness and load timing policy for async menu data.
 *
 * @param staleAfter Data older than this duration is considered stale. Null means never stale.
 * @param eagerLoadOnBind Load immediately when binding is created.
 */
data class AsyncDataPolicy(
    val staleAfter: Duration? = null,
    val eagerLoadOnBind: Boolean = true
) {
    fun isStale(loadedAt: Instant, now: Instant = Instant.now()): Boolean {
        val threshold = staleAfter ?: return false
        return loadedAt.plus(threshold).isBefore(now)
    }
}

/**
 * Runtime controller for async menu data loading.
 *
 * This controller guards against stale async results by:
 * - tracking request generations
 * - only applying data if the same menu holder is still open
 */
class AsyncMenuDataHandle<T> internal constructor(
    private val menuApi: MenuAPI,
    private val controls: MenuControls<*>,
    private val source: AsyncMenuDataSource<T>,
    private val policy: AsyncDataPolicy,
    private val onStateChange: (AsyncMenuState<T>, MenuControls<*>) -> Unit,
    private val onData: (T, MenuControls<*>) -> Unit
) {
    private val requestGeneration = AtomicLong(0)
    private val boundMenuGeneration = controls.generation

    @Volatile
    private var _state: AsyncMenuState<T> = AsyncMenuState.Idle

    val state: AsyncMenuState<T> get() = _state

    init {
        if (policy.eagerLoadOnBind) {
            refresh()
        }
    }

    /**
     * Refreshes async data immediately, regardless of stale policy.
     */
    fun refresh() {
        load(force = true)
    }

    /**
     * Reloads only if stale or not yet loaded, unless forced.
     */
    fun load(force: Boolean = false) {
        if (!force && !shouldLoadForStaleness()) return

        val generation = requestGeneration.incrementAndGet()
        updateState(AsyncMenuState.Loading)

        source.load(controls.player).whenComplete { value, throwable ->
            val applyResult = Runnable {
                // Ignore stale completions.
                if (generation != requestGeneration.get()) return@Runnable
                // Ignore if the player has moved to another menu.
                if (!isStillBoundToSameMenu()) return@Runnable

                if (throwable != null) {
                    val errorState = AsyncMenuState.Error(throwable, Instant.now())
                    updateState(errorState)
                    menuApi.plugin.slF4JLogger.warn(
                        "Async menu data load failed for ${controls.player.name}",
                        throwable
                    )
                    return@Runnable
                }

                val readyState = AsyncMenuState.Ready(value, Instant.now())
                updateState(readyState)
                onData(value, controls)
            }

            if (Bukkit.isPrimaryThread()) {
                applyResult.run()
            } else {
                Bukkit.getScheduler().runTask(menuApi.plugin, applyResult)
            }
        }
    }

    /**
     * Reloads only when stale according to policy.
     */
    fun refreshIfStale() {
        load(force = false)
    }

    private fun shouldLoadForStaleness(): Boolean {
        return when (val current = _state) {
            is AsyncMenuState.Idle -> true
            is AsyncMenuState.Loading -> false
            is AsyncMenuState.Error -> true
            is AsyncMenuState.Ready -> policy.isStale(current.loadedAt)
        }
    }

    private fun updateState(next: AsyncMenuState<T>) {
        _state = next
        onStateChange(next, controls)
    }

    private fun isStillBoundToSameMenu(): Boolean {
        if (controls.generation != boundMenuGeneration) return false
        return controls.isOpen()
    }
}

/**
 * Binds an async data source to currently-open menu controls.
 *
 * Typical usage:
 * - render a loading state in [onStateChange]
 * - mutate menu items in [onData]
 * - call `controls.refresh()` after mutation if needed
 */
fun <T> MenuAPI.bindAsyncData(
    controls: MenuControls<*>,
    source: AsyncMenuDataSource<T>,
    policy: AsyncDataPolicy = AsyncDataPolicy(),
    onStateChange: (AsyncMenuState<T>, MenuControls<*>) -> Unit = { _, _ -> },
    onData: (T, MenuControls<*>) -> Unit
): AsyncMenuDataHandle<T> {
    return AsyncMenuDataHandle(
        menuApi = this,
        controls = controls,
        source = source,
        policy = policy,
        onStateChange = onStateChange,
        onData = onData
    )
}
