package bruh.zchat.utils.translations

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for ISO639Languages utility object.
 */
class ISO639LanguagesTest {

    @Test
    fun `ALL_LANGUAGES contains common languages`() {
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("en"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("fr"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("de"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("es"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("ja"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("zh"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("ar"))
        assertTrue(ISO639Languages.ALL_LANGUAGES.containsKey("ru"))
    }

    @Test
    fun `ALL_LANGUAGES has correct English names`() {
        assertEquals("English", ISO639Languages.ALL_LANGUAGES["en"])
        assertEquals("French", ISO639Languages.ALL_LANGUAGES["fr"])
        assertEquals("German", ISO639Languages.ALL_LANGUAGES["de"])
        assertEquals("Japanese", ISO639Languages.ALL_LANGUAGES["ja"])
    }

    @Test
    fun `COMMON_LOCALES contains common locale combinations`() {
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("en_US"))
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("en_GB"))
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("fr_FR"))
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("de_DE"))
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("zh_CN"))
        assertTrue(ISO639Languages.COMMON_LOCALES.containsKey("ja_JP"))
    }

    @Test
    fun `getBaseLanguage extracts language from locale`() {
        assertEquals("en", ISO639Languages.getBaseLanguage("en"))
        assertEquals("en", ISO639Languages.getBaseLanguage("en_US"))
        assertEquals("en", ISO639Languages.getBaseLanguage("en_GB"))
        assertEquals("fr", ISO639Languages.getBaseLanguage("fr_FR"))
        assertEquals("zh", ISO639Languages.getBaseLanguage("zh_CN"))
    }

    @Test
    fun `getBaseLanguage handles uppercase input`() {
        assertEquals("en", ISO639Languages.getBaseLanguage("EN"))
        assertEquals("en", ISO639Languages.getBaseLanguage("EN_US"))
    }

    @Test
    fun `isValidLocale accepts valid ISO 639-1 codes`() {
        assertTrue(ISO639Languages.isValidLocale("en"))
        assertTrue(ISO639Languages.isValidLocale("fr"))
        assertTrue(ISO639Languages.isValidLocale("de"))
        assertTrue(ISO639Languages.isValidLocale("ja"))
    }

    @Test
    fun `isValidLocale accepts valid locale with region`() {
        assertTrue(ISO639Languages.isValidLocale("en_US"))
        assertTrue(ISO639Languages.isValidLocale("en_GB"))
        assertTrue(ISO639Languages.isValidLocale("fr_FR"))
        assertTrue(ISO639Languages.isValidLocale("zh_CN"))
    }

    @Test
    fun `isValidLocale rejects invalid language codes`() {
        assertFalse(ISO639Languages.isValidLocale("xx"))
        assertFalse(ISO639Languages.isValidLocale("invalid"))
        assertFalse(ISO639Languages.isValidLocale(""))
    }

    @Test
    fun `isValidLocale rejects malformed locales`() {
        assertFalse(ISO639Languages.isValidLocale("en_US_extra"))
        assertFalse(ISO639Languages.isValidLocale("en_"))
        assertFalse(ISO639Languages.isValidLocale("_US"))
        assertFalse(ISO639Languages.isValidLocale("en_123"))
    }

    @Test
    fun `ALL_LANGUAGES size is reasonable`() {
        // ISO 639-1 has about 180+ languages
        assertTrue(ISO639Languages.ALL_LANGUAGES.size >= 180)
    }
}
