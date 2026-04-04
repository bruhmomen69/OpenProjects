package bruh.regionrestore.timer

import bruh.regionrestore.utils.asLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.update

/**
 * Manages chunk-level locking to prevent concurrent modifications
 * to the same chunk or its neighbors during restore operations.
 * Thread-safe via ConcurrentHashMap.
 */
@OptIn(ExperimentalAtomicApi::class)
class ChunkLockManager {
    private val cbcLocks = ConcurrentHashMap<Long, ChunkLock>()
    private val spinCounter = AtomicInt(0)

    data class ChunkLock(
        val x: Int,
        val z: Int,
        val lock: Mutex = Mutex(),
        val lockAccessCnt: AtomicInt = AtomicInt(0),
        var objectAccessCnt: Int = 0
    )

    /**
     * Get or create a lock for a chunk.
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     */
    fun accessChunkLock(x: Int, z: Int): ChunkLock {
        return cbcLocks.compute(asLong(x, z)) { key, value ->
            val finalValue = value ?: ChunkLock(x, z)
            finalValue.objectAccessCnt++
            finalValue
        }!!
    }

    /**
     * Release a lock reference. When reference count reaches 0, the lock is removed.
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     */
    fun releaseChunkLock(lock: ChunkLock) {
        cbcLocks.compute(asLong(lock.x, lock.z)) { key, value ->
            if (--lock.objectAccessCnt == 0) null else value
        }
    }

    /**
     * Acquire the local lock and check neighbor locks.
     * Returns true if all locks were acquired successfully.
     * Implements a spin-lock mechanism with deterministic delays to avoid contention.
     *
     * @param localLock The primary lock to acquire
     * @param neighbourLocks List of neighbor locks to check
     * @param pluginLogger Optional logger for debug output
     * @return true if the locks were acquired
     */
    suspend fun acquireLocksWithRetry(
        localLock: ChunkLock,
        neighbourLocks: List<ChunkLock>,
        logger: (String) -> Unit = {}
    ): Boolean {
        val spinId = spinCounter.update { it: Int -> if (it == Int.MAX_VALUE) 0 else it + 1 }
        var locked = false

        while (!locked) {
            val shouldRetry = run {
                localLock.lock.lock()
                for ((x, z, mutex, refCnt) in neighbourLocks) {
                    refCnt.incrementAndFetch()
                    if (mutex.isLocked) {
                        // Unlock local lock
                        localLock.lock.unlock()
                        // Await remote lock availability.
                        logger("Task $spinId: Spinning on remote lock for chunk $x, $z")
                        mutex.lock()
                        mutex.unlock()
                        logger("Task $spinId: Spun remote lock for chunk $x, $z")

                        // Delay for additional time if someone else is checking to avoid re-checking while they re-check, resulting in a loop.
                        val extraDelay = refCnt.decrementAndFetch().let { cnt ->
                            if (cnt > 0) cnt else 0
                        }
                        // Delay to avoid lock contention
                        delay(extraDelay.toLong())

                        // Delay again for remaining references to drain
                        val newDelay = refCnt.load()
                        if (newDelay > 0) {
                            delay(newDelay.toLong())
                        }
                        return@run true // Retry
                    }
                    // Otherwise, lock is not locked, new lock cannot be locked as our local lock is locked, continue.
                    refCnt.decrementAndFetch() // Unload reference count for this op
                }

                locked = true
                return@run false // Done, don't retry
            }

            if (!shouldRetry) break
        }

        return locked
    }
}
