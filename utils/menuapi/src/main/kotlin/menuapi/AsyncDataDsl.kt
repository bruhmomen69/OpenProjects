package bruh.zchat.utils.menuapi

import java.time.Duration

/**
 * Configuration for a single async data source on a menu.
 */
data class AsyncDataConfig<T>(
    val loader: suspend () -> T,
    val onLoaded: (T) -> Unit,
    val onError: ((Throwable) -> Unit)?,
    val staleAfter: Duration?
)

/**
 * DSL builder for declaring async data sources on menus.
 *
 * ```kotlin
 * asyncData<List<Auction>> {
 *     load { auctionService.getActiveAuctions() }
 *     onLoaded { auctions -> dataSource = auctions }
 *     onError { e -> player.sendMessage("Failed to load") }
 *     staleAfter(Duration.ofSeconds(30))
 * }
 * ```
 */
class AsyncDataDsl<T> {
    private var loader: (suspend () -> T)? = null
    private var onLoaded: ((T) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private var staleAfter: Duration? = null

    /** The suspending function that loads data. Runs off the main thread. */
    fun load(block: suspend () -> T) {
        loader = block
    }

    /** Called on the main thread when data arrives. Update menu state here. */
    fun onLoaded(block: (T) -> Unit) {
        onLoaded = block
    }

    /** Called on the main thread if loading fails. Optional — defaults to SLF4J warning. */
    fun onError(block: (Throwable) -> Unit) {
        onError = block
    }

    /** Mark data as stale after this duration (triggers reload on next refresh). */
    fun staleAfter(duration: Duration) {
        staleAfter = duration
    }

    internal fun build(): AsyncDataConfig<T> = AsyncDataConfig(
        loader = loader ?: error("asyncData requires a load {} block"),
        onLoaded = onLoaded ?: error("asyncData requires an onLoaded {} block"),
        onError = onError,
        staleAfter = staleAfter
    )
}
