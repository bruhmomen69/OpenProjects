package bruh.zchat.utils.translations

/**
 * Interface representing a translatable message key.
 * Implementations should be enums that define all translation keys for a specific category.
 */
interface MessageKey {
    /**
     * The translation key used to look up the message in translation files.
     * This key is combined with the prefix registered for the enum to form the full key.
     */
    val key: String

    /**
     * The default value (in English) for this message key.
     * Used when no translation is available for the current locale.
     */
    val default: String
}
