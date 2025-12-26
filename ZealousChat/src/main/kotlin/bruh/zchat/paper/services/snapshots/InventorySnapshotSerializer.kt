package bruh.zchat.paper.services.snapshots

import bruh.zchat.paper.services.ChatInventoryPlaceholderService
import bruh.zchat.paper.utils.streaming.BukkitObjectInputStream
import bruh.zchat.paper.utils.streaming.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Serializes and deserializes inventory snapshots to raw bytes.
 */
object InventorySnapshotSerializer {
    fun serialize(snapshot: ChatInventoryPlaceholderService.InventorySnapshot): ByteArray {
        val baos = ByteArrayOutputStream()
        BukkitObjectOutputStream(baos).use { oos ->
            oos.writeObject(snapshot)
        }
        return baos.toByteArray()
    }

    fun deserialize(bytes: ByteArray): ChatInventoryPlaceholderService.InventorySnapshot {
        ByteArrayInputStream(bytes).use { bais ->
            BukkitObjectInputStream(bais).use { ois ->
                @Suppress("UNCHECKED_CAST")
                return ois.readObject() as ChatInventoryPlaceholderService.InventorySnapshot
            }
        }
    }
}
