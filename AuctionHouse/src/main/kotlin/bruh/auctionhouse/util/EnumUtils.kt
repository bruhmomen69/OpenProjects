package bruh.auctionhouse.util

import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("EnumUtils")

/**
 * Safely converts a string to an enum value, returning a Result.
 * @param name The string to convert
 * @return Result.success with the enum value if found, Result.failure with IllegalArgumentException if not
 */
inline fun <reified T : Enum<T>> safeValueOfResult(name: String): Result<T> {
    return try {
        Result.success(enumValueOf<T>(name))
    } catch (e: IllegalArgumentException) {
        logger.warn("Unknown enum value '{}' for {}", name, T::class.simpleName)
        Result.failure(e)
    }
}

/**
 * Safely converts a string to an enum value, returning the default if conversion fails.
 * Logs a warning when falling back to default.
 * @param name The string to convert
 * @param default The default value to return if conversion fails
 * @return The enum value if found, otherwise the default
 */
inline fun <reified T : Enum<T>> safeValueOf(name: String, default: T): T {
    return try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        logger.warn("Unknown enum value '{}' for {}, using default '{}'", name, T::class.simpleName, default)
        default
    }
}

/**
 * Safely converts a string to an enum value, returning null if conversion fails.
 * Logs a warning when value is unknown.
 * @param name The string to convert
 * @return The enum value if found, otherwise null
 */
inline fun <reified T : Enum<T>> safeValueOfOrNull(name: String): T? {
    return try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        logger.warn("Unknown enum value '{}' for {}", name, T::class.simpleName)
        null
    }
}

/**
 * Safely converts a string to an enum value with a custom fallback function.
 * @param name The string to convert
 * @param fallback Function to call if conversion fails, receives the exception
 * @return The enum value if found, otherwise the result of the fallback function
 */
inline fun <reified T : Enum<T>> safeValueOfOrElse(name: String, fallback: (IllegalArgumentException) -> T): T {
    return try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        fallback(e)
    }
}
