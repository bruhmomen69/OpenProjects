package bruh.zchat.paper.utils.streaming

import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.inventory.ItemStack
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream

/**
 * This class is designed to be used in conjunction with the [ ] API. It translates objects back to their
 * original implementation after being serialized by [ ].
 *
 *
 * Behavior of implementations extending this class is not guaranteed across
 * future versions.
 */
class BukkitObjectInputStream : ObjectInputStream {
    /**
     * Constructor provided to mirror super functionality.
     *
     * @throws IOException if an I/O error occurs while creating this stream
     * @throws SecurityException if a security manager exists and denies
     * enabling subclassing
     * @see ObjectInputStream.ObjectInputStream
     */
    protected constructor() : super() {
        super.enableResolveObject(true)
    }

    /**
     * Object input stream decoration constructor.
     *
     * @param in the input stream to wrap
     * @throws IOException if an I/O error occurs while reading stream header
     * @see ObjectInputStream.ObjectInputStream
     */
    constructor(`in`: InputStream?) : super(`in`) {
        super.enableResolveObject(true)
    }

    @Throws(IOException::class)
    override fun resolveObject(obj: Any): Any {
        var obj: Any? = obj

        when (obj) {
            is ItemStackByteWrapper -> {
                obj = if (obj.data[0] == 255.toByte()) ItemStack.of(Material.AIR)
                else ItemStack.deserializeBytes(obj.data)
            }
            is SectionWrapper -> obj = ConfigurationSerialization.deserializeObject(obj.map)
        }

        return super.resolveObject(obj)
    }
}