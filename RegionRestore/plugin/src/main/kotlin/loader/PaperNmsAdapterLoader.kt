package bruh.regionrestore.loader

import bruh.regionrestore.nms.PaperNmsAdapter
import org.bukkit.Bukkit

object PaperNmsAdapterLoader {
    private val versionAdapters = mapOf(
        "1.21.4" to "bruh.regionrestore.nms.v1_21_4.PaperNmsAdapter1_21_4",
        "1.21.5" to "bruh.regionrestore.nms.v1_21_5.PaperNmsAdapter1_21_5",
        "1.21.6" to "bruh.regionrestore.nms.v1_21_6.PaperNmsAdapter1_21_6",
        "1.21.7" to "bruh.regionrestore.nms.v1_21_7.PaperNmsAdapter1_21_7",
        "1.21.8" to "bruh.regionrestore.nms.v1_21_8.PaperNmsAdapter1_21_8",
        "1.21.9" to "bruh.regionrestore.nms.v1_21_9.PaperNmsAdapter1_21_9",
        "1.21.10" to "bruh.regionrestore.nms.v1_21_10.PaperNmsAdapter1_21_10",
        "1.21.11" to "bruh.regionrestore.nms.v1_21_11.PaperNmsAdapter1_21_11",
        "26.1" to "bruh.regionrestore.nms.v26_1.PaperNmsAdapter26_1",
        "26.1.1" to "bruh.regionrestore.nms.v26_1.PaperNmsAdapter26_1",
        "26.2" to "bruh.regionrestore.nms.v26_2.PaperNmsAdapter26_2"
    )

    fun load(): PaperNmsAdapter {
        val version = Bukkit.getMinecraftVersion()
        val adapterClassName = versionAdapters[version]
            ?: throw UnsupportedOperationException("RegionRestore does not support Minecraft version $version")

        val adapterClass = try {
            Class.forName(adapterClassName)
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("Failed to load adapter class $adapterClassName", e)
        }

        return try {
            adapterClass.getDeclaredConstructor().newInstance() as PaperNmsAdapter
        } catch (e: Exception) {
            throw IllegalStateException("Failed to instantiate adapter class $adapterClassName", e)
        }
    }
}
