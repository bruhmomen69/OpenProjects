package bruh.regionrestore.template

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import bruh.regionrestore.nms.RegionTemplateVersion
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

/**
 * Reference to a specific template version.
 *
 * @property templateName Name of the template
 * @property versionId Specific version ID, or null to use active version
 */
data class TemplateRef(
    val templateName: String,
    val versionId: Int? = null
) {
    companion object {
        /**
         * Create a reference to a specific version.
         */
        fun specific(templateName: String, versionId: Int) = TemplateRef(templateName, versionId)

        /**
         * Create a reference to the active version.
         */
        fun active(templateName: String) = TemplateRef(templateName, null)
    }
}

/**
 * Cache statistics for monitoring and debugging.
 */
data class CacheStats(
    val pinnedSize: Int,
    val lruSize: Int,
    val lruMaxSize: Int,
    val pinnedEntries: List<String>,
    val lruEntries: List<String>
)

/**
 * Cached template with metadata.
 */
private data class CachedTemplate(
    val template: RegionTemplateVersion,
    var lastAccessedMillis: Long,
    var isPinned: Boolean
)

/**
 * High-performance template cache using MayakaApps Kache.
 *
 * Features:
 * - Two-tier caching: pinned (unbounded) and LRU (bounded with TTL)
 * - TTL-based eviction for non-pinned entries (default 10 minutes)
 * - Pin refcounting to prevent eviction of actively-used templates
 * - Async-only loading (never blocks main thread)
 * - Concurrent load deduplication via per-key mutexes
 *
 * Per PLAN.md §7.3:
 * - Pinned templates: Preloaded on boot for cloner/timers, never evicted while pinned
 * - Lazy templates: Loaded on first use, evicted after TTL expiry
 * - Thread-safe: All operations are async and thread-safe
 *
 * @property repository Underlying template repository for disk I/O
 * @property ttlMinutes Time-to-live for non-pinned entries (default 10)
 * @property lruMaxSize Maximum size of LRU cache (default 50)
 */
class TemplateCache(
    private val repository: TemplateRepository,
    private val ttlMinutes: Int = 10,
    private val lruMaxSize: Int = 50
) {
    private val logger = LoggerFactory.getLogger(TemplateCache::class.java)

    // LRU cache for non-pinned entries (evicted by size + TTL)
    private val lruCache = InMemoryKache<String, CachedTemplate>(maxSize = lruMaxSize.toLong()) {
        strategy = KacheStrategy.LRU
        expireAfterAccessDuration = ttlMinutes.minutes
        onEntryRemoved = { evicted, key, old, new ->
            if (pinnedCache.get(key) !== old) old.template.release()
        }
    }

    // Pinned entries (unbounded, never evicted while pinned)
    private val pinnedCache = ConcurrentHashMap<String, CachedTemplate>()

    // Pin refcounts (templateName -> versionId -> refcount)
    private val pinRefcounts = ConcurrentHashMap<Pair<String, Int>, AtomicInteger>()

    // Per-key mutexes for load deduplication
    private val loadMutexes = ConcurrentHashMap<String, Mutex>()

    // TTL in milliseconds
    private val ttlMillis = ttlMinutes.minutes.toLong(DurationUnit.MILLISECONDS)

    /**
     * Get a template version from cache or load from disk.
     *
     * @param name Template name
     * @param versionId Version ID
     * @return Cached template version, or null if not found
     */
    suspend fun get(name: String, versionId: Int): RegionTemplateVersion? = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(name, versionId)

        // Check pinned cache first
        val pinned = pinnedCache[cacheKey]
        if (pinned != null) {
            pinned.lastAccessedMillis = System.currentTimeMillis()
            logger.debug("Cache HIT (pinned): $cacheKey")
            return@withContext pinned.template
        }

        // Check LRU cache (with TTL validation) using getOrPut
        val now = System.currentTimeMillis()
        val cached = lruCache.get(cacheKey)
        if (cached != null) {
            val age = now - cached.lastAccessedMillis

            if (age <= ttlMillis && !cached.isPinned) {
                // Cache hit within TTL
                cached.lastAccessedMillis = now
                logger.debug("Cache HIT (LRU): $cacheKey (age=${age / 1000}s)")
                return@withContext cached.template
            } else {
                // Entry expired, just let it be - it will be replaced on next getOrPut
                logger.debug("Cache MISS (expired): $cacheKey (age=${age / 1000}s)")
            }
        } else {
            logger.debug("Cache MISS (not found): $cacheKey")
        }

        // Cache miss: load from disk with deduplication
        val mutex = loadMutexes.getOrPut(cacheKey) { Mutex() }
        try {
            mutex.withLock {
                // Double-check: another thread might have loaded it while we waited
                val doubleCheckPinned = pinnedCache[cacheKey]
                if (doubleCheckPinned != null) {
                    doubleCheckPinned.lastAccessedMillis = System.currentTimeMillis()
                    return@withContext doubleCheckPinned.template
                }

                val doubleCheckLru = lruCache.get(cacheKey)
                if (doubleCheckLru != null) {
                    val doubleCheckNow = System.currentTimeMillis()
                    if (doubleCheckNow - doubleCheckLru.lastAccessedMillis <= ttlMillis) {
                        doubleCheckLru.lastAccessedMillis = doubleCheckNow
                        return@withContext doubleCheckLru.template
                    }
                }

                // Load from repository using getOrPut to cache the result
                logger.debug("Loading from disk: $cacheKey")
                var loadedTemplate: RegionTemplateVersion? = null
                var wasNewEntry = false

                lruCache.getOrPut(cacheKey) {
                    val template = repository.loadTemplateVersionFromDisk(name, versionId)

                    if (template != null) {
                        val cached = CachedTemplate(
                            template = template,
                            lastAccessedMillis = System.currentTimeMillis(),
                            isPinned = false
                        )

                        loadedTemplate = template
                        wasNewEntry = true
                        logger.debug("Cached: $cacheKey")
                        cached
                    } else {
                        null
                    }
                }
                return@withContext loadedTemplate
            }
        } finally {
            if (!mutex.isLocked)
                loadMutexes.remove(cacheKey)
        }
    }

    /**
     * Get the active version of a template from cache or load from disk.
     *
     * @param name Template name
     * @return Active template version, or null if not found
     */
    suspend fun getActive(name: String): RegionTemplateVersion? = withContext(Dispatchers.IO) {
        // Delegate to repository which will use the cache efficiently
        repository.loadActiveTemplateVersion(name)
    }

    /**
     * Pin a template version to prevent eviction.
     *
     * Increments the pin refcount. Template will not be evicted until
     * refcount reaches zero via unpin().
     *
     * @param name Template name
     * @param versionId Version ID
     * @return Pinned template version, or null if not found
     */
    suspend fun pin(name: String, versionId: Int): RegionTemplateVersion? = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(name, versionId)
        logger.debug("Pinning: $cacheKey")

        // Get template (loads from disk if not cached)
        val template = get(name, versionId) ?: return@withContext null

        // Increment refcount
        val refKey = Pair(name, versionId)
        pinRefcounts.getOrPut(refKey) { AtomicInteger(0) }.incrementAndGet()

        // Move from LRU to pinned cache if present in LRU
        val lruEntry = lruCache.get(cacheKey)
        if (lruEntry != null) {
            lruEntry.isPinned = true
            pinnedCache[cacheKey] = lruEntry
            logger.debug("Moved from LRU to pinned: $cacheKey")
        }

        logger.debug("Pinned: $cacheKey (refcount=${pinRefcounts[refKey]?.get()})")
        return@withContext template
    }

    /**
     * Unpin a template version, allowing eviction when refcount reaches zero.
     *
     * @param name Template name
     * @param versionId Version ID
     */
    suspend fun unpin(name: String, versionId: Int) = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(name, versionId)
        val refKey = Pair(name, versionId)

        val refcount = pinRefcounts[refKey] ?: return@withContext
        val newCount = refcount.decrementAndGet()

        logger.debug("Unpinning: $cacheKey (refcount=$newCount)")

        if (newCount <= 0) {
            // Remove from pinned cache
            val pinned = pinnedCache.remove(cacheKey)
            pinRefcounts.remove(refKey)

            if (pinned != null) {
                // Move back to LRU cache
                pinned.isPinned = false
                pinned.lastAccessedMillis = System.currentTimeMillis()

                // Use getOrPut to re-insert into LRU cache
                lruCache.getOrPut(cacheKey) { pinned }
                logger.debug("Moved from pinned to LRU: $cacheKey")
            } else {
                // Template was not in pinned cache (edge case)
                // Remove from LRU cache if present and release ByteBufs
                val lruEntry = lruCache.get(cacheKey)
                if (lruEntry != null) {
                    lruCache.remove(cacheKey)
                }
            }
        }
    }

    /**
     * Pin the active version of a template.
     *
     * @param name Template name
     * @return Pinned active template version, or null if not found
     */
    suspend fun pinActive(name: String): RegionTemplateVersion? = withContext(Dispatchers.IO) {
        // First get the active template (this will use the cache)
        val activeTemplate = repository.loadActiveTemplateVersion(name) ?: return@withContext null

        // Then pin it using the specific version ID
        pin(name, activeTemplate.versionId)
    }

    /**
     * Preload multiple templates into cache and pin them.
     *
     * Used during plugin initialization to load templates referenced by
     * Mass Cloner pools and timers.
     *
     * @param templateRefs List of template references to preload
     * @return Number of successfully preloaded templates
     */
    suspend fun preload(templateRefs: List<TemplateRef>): Int = withContext(Dispatchers.IO) {
        logger.info("Preloading ${templateRefs.size} templates...")

        var successCount = 0
        var failureCount = 0

        for (ref in templateRefs) {
            try {
                val template = if (ref.versionId != null) {
                    pin(ref.templateName, ref.versionId)
                } else {
                    pinActive(ref.templateName)
                }

                if (template != null) {
                    successCount++
                    logger.debug("Preloaded: ${ref.templateName} v${ref.versionId ?: "active"}")
                } else {
                    failureCount++
                    logger.warn("Failed to preload: ${ref.templateName} v${ref.versionId ?: "active"} (not found)")
                }
            } catch (e: Exception) {
                failureCount++
                logger.error("Error preloading ${ref.templateName} v${ref.versionId ?: "active"}", e)
            }
        }

        logger.info("Preloading complete: $successCount succeeded, $failureCount failed")
        return@withContext successCount
    }

    /**
     * Clear all cache entries (both pinned and LRU).
     *
     * Called during plugin shutdown.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        logger.info("Clearing template cache...")

        val pinnedSize = pinnedCache.size

        // Release all ByteBufs in pinned templates to prevent memory leaks
        for (cached in pinnedCache.values) {
            cached.template.release()
        }

        pinnedCache.clear()
        lruCache.clear()
        pinRefcounts.clear()
        loadMutexes.clear()

        logger.info("Cleared cache: $pinnedSize pinned entries")
    }

    /**
     * Remove a specific template version from cache and release its ByteBufs.
     *
     * Called when a template is deleted from the repository.
     *
     * @param name Template name
     * @param versionId Version ID
     * @return true if the template was found and removed, false otherwise
     */
    suspend fun remove(name: String, versionId: Int): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(name, versionId)
        var removed = false

        // Check and remove from pinned cache
        val pinned = pinnedCache.remove(cacheKey)
        if (pinned != null) {
            pinned.template.release()
            pinRefcounts.remove(Pair(name, versionId))
            removed = true
            logger.debug("Removed from pinned cache: $cacheKey")
        }

        // Check and remove from LRU cache
        val lruEntry = lruCache.get(cacheKey)
        if (lruEntry != null) {
            lruCache.remove(cacheKey)
            removed = true
            logger.debug("Removed from LRU cache: $cacheKey")
        }

        removed
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @return Current cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            pinnedSize = pinnedCache.size,
            lruSize = lruCache.size.toInt(),
            lruMaxSize = lruMaxSize,
            pinnedEntries = pinnedCache.keys.toList(),
            lruEntries = emptyList() // Kache doesn't expose keys, so we return empty list
        )
    }

    /**
     * Create a cache key from template name and version ID.
     */
    private fun makeCacheKey(name: String, versionId: Int): String {
        return "$name:v$versionId"
    }
}
