package bruh.auctionhouse.util

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.inventory.ItemStack
import java.util.Base64

object OrderItemMatching {
    private val base64Encoder = Base64.getEncoder()

    fun serializeDisplayName(item: ItemStack, miniMessage: MiniMessage): String? {
        return item.itemMeta?.displayName()?.let(miniMessage::serialize)
    }

    fun computeStoredNbtHash(item: ItemStack): String {
        val normalized = item.clone().apply { amount = 1 }
        return base64Encoder.encodeToString(normalized.serializeAsBytes())
    }

    fun matchesStoredNbtHash(item: ItemStack, storedHash: String): Boolean {
        return storedHash == computeStoredNbtHash(item) || storedHash == computeLegacyNbtHash(item)
    }

    fun computeStoredLoreHash(item: ItemStack): String {
        val lore = item.itemMeta?.lore ?: return ""
        return lore.joinToString("|").hashCode().toString()
    }

    private fun computeLegacyNbtHash(item: ItemStack): String {
        val meta = item.itemMeta ?: return ""
        val sb = StringBuilder()

        meta.enchants.forEach { (enchant, level) ->
            sb.append(enchant.key.key).append(":").append(level).append(";")
        }

        if (meta.hasCustomModelData()) {
            sb.append("cmd:").append(meta.customModelData).append(";")
        }

        meta.itemFlags.forEach { flag ->
            sb.append("flag:").append(flag.name).append(";")
        }

        return sb.toString().hashCode().toString()
    }
}
