package bruh.zchat.paper.utils.streaming

import java.io.Serializable

data class ItemStackByteWrapper(val data: ByteArray): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemStackByteWrapper) return false

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}