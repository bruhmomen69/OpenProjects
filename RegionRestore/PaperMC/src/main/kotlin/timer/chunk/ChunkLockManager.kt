package bruh.regionrestore.timer.chunk

import bruh.regionrestore.utils.asLong
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.math.roundToInt

/**
 * Manages chunk locks for concurrent restore operations.
 * Uses a mutex-based system with spin locking to prevent concurrent modifications to the same chunk or neighboring chunks.
 */
class ChunkLockManager(private val plugin: SuspendingJavaPlugin) {
    private val cbcLocks = ConcurrentHashMap<Long, ChunkLock>()

    data class ChunkLock(
        val x: Int,
        val z: Int,
        val lock: Mutex = Mutex(),
        val lockAccessCnt: AtomicInt = AtomicInt(0),
        var objectAccessCnt: Int = 0
    )

    /**
     * Acquires a lock for the specified chunk.
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     * @return The ChunkLock for the specified chunk
     */
    fun accessChunkLock(x: Int, z: Int): ChunkLock {
        return cbcLocks.compute(asLong(x, z)) { key, value ->
            val finalValue = value ?: ChunkLock(x, z)
            finalValue.objectAccessCnt++
            finalValue
        }!!
    }

    /**
     * Releases a previously acquired chunk lock.
     * Thread safety: Relies on CHM#lock to lock based on key so multiple concurrent accesses are safe.
     */
    fun releaseChunkLock(lock: ChunkLock) {
        cbcLocks.compute(asLong(lock.x, lock.z)) { key, value ->
            if (--lock.objectAccessCnt == 0) null else value
        }
    }

    /**
     * Acquires locks for a chunk and all its neighbors to prevent concurrent modifications.
     * Uses a spin-lock mechanism to wait for all required locks to become available.
     *
     * @param targetX The target chunk X coordinate
     * @param targetZ The target chunk Z coordinate
     * @param worldId The world UUID for generating neighbor keys
     * @return A LockedChunks object containing the acquired locks
     */
    suspend fun acquireChunkAndNeighborLocks(
        targetX: Int,
        targetZ: Int,
        worldId: UUID
    ): LockedChunks {
        val localLock = accessChunkLock(targetX, targetZ)

        val neighborKeys = listOf(
            ChunkTicketManager.ChunkKey(worldId, targetX, targetZ - 1),
            ChunkTicketManager.ChunkKey(worldId, targetX, targetZ + 1),
            ChunkTicketManager.ChunkKey(worldId, targetX - 1, targetZ),
            ChunkTicketManager.ChunkKey(worldId, targetX + 1, targetZ),
            ChunkTicketManager.ChunkKey(worldId, targetX - 1, targetZ - 1),
            ChunkTicketManager.ChunkKey(worldId, targetX - 1, targetZ + 1),
            ChunkTicketManager.ChunkKey(worldId, targetX + 1, targetZ - 1),
            ChunkTicketManager.ChunkKey(worldId, targetX + 1, targetZ + 1)
        )

        val neighborMutexes = neighborKeys.map { key -> accessChunkLock(key.x, key.z) }

        // Lock this and neighbours before doing work
        var locked = false
        val spinId = (Math.random() * 1000).roundToInt()
        
        while (!locked) {
            run {
                localLock.lock.lock()
                for ((x, z, mutex, refCnt) in neighborMutexes) {
                    refCnt.incrementAndFetch()
                    if (mutex.isLocked) {
                        // Unlock local lock
                        localLock.lock.unlock()
                        // Await remote lock availability.
                        plugin.slF4JLogger.debug("Task $spinId: Spinning on remote lock for chunk $x, $z")
                        mutex.lock()
                        mutex.unlock()
                        plugin.slF4JLogger.debug("Task $spinId: Spun remote lock for chunk $x, $z")
                        // Delay for additional time if someone else is checking to avoid re-checking while they re-check, resulting in a loop.
                        val extraDelay = refCnt.decrementAndFetch().let { cnt ->
                            if (cnt > 0) {
                                cnt
                            } else {
                                0
                            }
                        }
                        // Delay by random amount to avoid lock contention
                        delay((Math.random() * 3).roundToLong() + extraDelay)
                        // Delay for additional again, same reasons, this time is to just deal with randoms being random
                        val newDelay = refCnt.load()
                        if (newDelay > 0) {
                            delay(newDelay.toLong())
                        }
                        return@run
                    }
                    // Otherwise, lock is not locked, new lock cannot be locked as our local lock is locked, continue.
                    refCnt.decrementAndFetch() // Unload reference count for this op
                }

                locked = true
            }
        }
        plugin.slF4JLogger.debug("Locked chunk $targetX, $targetZ")

        return LockedChunks(localLock, neighborMutexes, locked)
    }

    /**
     * Represents a set of locked chunks that can be unlocked.
     */
    data class LockedChunks(
        val localLock: ChunkLock,
        val neighborMutexes: List<ChunkLock>,
        private var locked: Boolean
    ) {
        var isLocked: Boolean
            get() = locked
            private set

        /**
         * Unlocks all acquired chunk locks.
         */
        fun unlock(lockManager: ChunkLockManager) {
            if (locked) {
                localLock.lock.unlock()
                locked = false
            }
            
            // Release lock references
            neighborMutexes.forEach { lockManager.releaseChunkLock(it) }
            lockManager.releaseChunkLock(localLock)
        }

        /**
         * Unlocks the local chunk lock while keeping neighbor locks.
         * Used for unlocking before an await operation.
         */
        fun unlockLocal() {
            if (locked) {
                localLock.lock.unlock()
                locked = false
            }
        }
    }
}
