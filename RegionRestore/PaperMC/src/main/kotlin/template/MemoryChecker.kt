package bruh.regionrestore.template

import oshi.SystemInfo
import oshi.hardware.GlobalMemory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

object MemoryChecker {

    private val logger: Logger = LoggerFactory.getLogger(MemoryChecker::class.java)

    private val systemInfo = AtomicReference<SystemInfo?>(null)
    private val globalMemory = AtomicReference<GlobalMemory?>(null)

    private fun ensureInitialized() {
        if (systemInfo.get() == null) {
            try {
                val info = SystemInfo()
                systemInfo.set(info)
                globalMemory.set(info.hardware.memory)
            } catch (e: Throwable) {
                logger.warn("OSHI not available, memory checks disabled: ${e.message}")
                systemInfo.set(null)
                globalMemory.set(null)
            }
        }
    }

    private const val SAFETY_MARGIN_BYTES = 256L * 1024 * 1024

    fun hasSufficientOffheapMemory(requiredBytes: Long): Boolean {
        ensureInitialized()
        val mem = globalMemory.get() ?: return true

        val available = mem.available
        val needed = requiredBytes + SAFETY_MARGIN_BYTES

        if (available < needed) {
            logger.debug(
                "Insufficient off-heap memory for template load. " +
                        "Available: ${formatBytes(available)}, Required: ${formatBytes(needed)} " +
                        "(including 256 MB safety margin). " +
                        "Falling back to on-heap allocation."
            )
            return false
        }

        return true
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(mb)
    }
}
