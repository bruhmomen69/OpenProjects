package bruh.regionrestore.utils

fun asLong(x: Int, z: Int): Long {
    return x.toLong() and 4294967295L or ((z.toLong() and 4294967295L) shl 32)
}