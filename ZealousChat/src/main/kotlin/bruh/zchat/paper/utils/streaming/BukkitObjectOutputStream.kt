package bruh.zchat.paper.utils.streaming

import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.inventory.ItemStack
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable

/**
 * This class is designed to be used in conjunction with the [ ] API. It translates objects to an internal
 * implementation for later deserialization using [ ].
 *
 *
 * Behavior of implementations extending this class is not guaranteed across
 * future versions.
 */
class BukkitObjectOutputStream : ObjectOutputStream {
    /**
     * Constructor provided to mirror super functionality.
     *
     * @throws IOException if an I/O error occurs while creating this stream
     * @throws SecurityException if a security manager exists and denies
     * enabling subclassing
     * @see ObjectOutputStream.ObjectOutputStream
     */
    protected constructor() : super() {
        super.enableReplaceObject(true)
    }

    /**
     * Object output stream decoration constructor.
     *
     * @param out the stream to wrap
     * @throws IOException if an I/O error occurs while writing stream header
     * @see ObjectOutputStream.ObjectOutputStream
     */
    constructor(out: OutputStream?) : super(out) {
        super.enableReplaceObject(true)
    }

    @Throws(IOException::class)
    override fun replaceObject(obj: Any): Any {
        var obj: Any? = obj

        if (obj !is Serializable ) {
            when (obj) {
                is ItemStack -> {
                    if (obj.type == Material.AIR) {
                        obj = ByteArray(1)
                        obj[0] = 255.toByte()
                        obj = ItemStackByteWrapper(obj)
                    } else {
                        obj = ItemStackByteWrapper(
                            obj.serializeAsBytes()
                        )
                    }
                }
                is ConfigurationSerializable -> obj = SectionWrapper(obj.serialize())
            }
        }

        return super.replaceObject(obj)
    }
}