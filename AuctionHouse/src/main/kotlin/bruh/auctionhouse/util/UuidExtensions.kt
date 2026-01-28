package bruh.auctionhouse.util

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Extension functions for converting UUIDs to/from 128-bit integers and byte arrays.
 */

/** Mask for extracting lower 64 bits (2^64 - 1) */
private val MASK_64: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

/** Maximum value for a 128-bit unsigned integer (2^128 - 1) */
private val MAX_128: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

/**
 * Converts this UUID to a BigInteger representing its 128-bit unsigned value.
 * The MSB forms the upper 64 bits and the LSB forms the lower 64 bits.
 */
fun UUID.toBigInteger(): BigInteger {
    val msb = this.mostSignificantBits
    val lsb = this.leastSignificantBits

    // Convert signed longs to unsigned BigInteger values, then combine
    val msbUnsigned = BigInteger.valueOf(msb).and(MASK_64)
    val lsbUnsigned = BigInteger.valueOf(lsb).and(MASK_64)

    // Shift MSB left by 64 bits and OR with LSB to get 128-bit value
    return msbUnsigned.shiftLeft(64).or(lsbUnsigned)
}

/**
 * Converts this BigInteger to a UUID.
 * The BigInteger must be non-negative and fit within 128 bits (0 <= value <= 2^128 - 1).
 *
 * @throws IllegalArgumentException if the BigInteger is negative or exceeds 128 bits
 */
fun BigInteger.toUuid(): UUID {
    require(this.signum() >= 0) { "BigInteger must be non-negative" }
    require(this <= MAX_128) { "BigInteger exceeds 128-bit maximum value" }

    // Extract MSB (upper 64 bits) and LSB (lower 64 bits)
    val msb = this.shiftRight(64).toLong()
    val lsb = this.and(MASK_64).toLong()

    return UUID(msb, lsb)
}

/**
 * Converts this UUID to a 16-byte array in big-endian format.
 * Suitable for BLOB/BINARY(16) database storage.
 */
fun UUID.toBytes(): ByteArray {
    return ByteBuffer.allocate(16)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
        .array()
}

/**
 * Converts this 16-byte array to a UUID.
 * Assumes the byte array is in big-endian format.
 *
 * @throws IllegalArgumentException if the byte array is not exactly 16 bytes
 */
fun ByteArray.toUuid(): UUID {
    require(this.size == 16) { "Byte array must be exactly 16 bytes, got ${this.size}" }

    val buffer = ByteBuffer.wrap(this)
    return UUID(buffer.long, buffer.long)
}
