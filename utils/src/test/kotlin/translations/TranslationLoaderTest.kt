package bruh.zchat.utils.translations

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/**
 * Tests for TranslationLoader.
 */
class TranslationLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var translationsDir: Path
    private lateinit var loader: TranslationLoader

    @BeforeEach
    fun setUp() {
        translationsDir = tempDir.resolve("translations")
        loader = TranslationLoader(translationsDir)
    }

    @Test
    fun `initializeDirectory creates directory if not exists`() = runTest {
        assertFalse(Files.exists(translationsDir))

        loader.initializeDirectory()

        assertTrue(Files.exists(translationsDir))
        assertTrue(Files.isDirectory(translationsDir))
    }

    @Test
    fun `initializeDirectory handles existing directory`() = runTest {
        Files.createDirectories(translationsDir)

        loader.initializeDirectory()

        assertTrue(Files.exists(translationsDir))
    }

    @Test
    fun `generateAllLanguageFiles creates all language files`() = runTest {
        val keys = mapOf(
            "test.key1" to "Default 1",
            "test.key2" to "Default 2"
        )

        loader.generateAllLanguageFiles(keys)

        // Check English file has defaults
        val englishFile = translationsDir.resolve("en.properties")
        assertTrue(Files.exists(englishFile))
        val englishContent = Files.readString(englishFile)
        assertTrue(englishContent.contains("test.key1=Default 1"))
        assertTrue(englishContent.contains("test.key2=Default 2"))

        // Check other language files exist and are empty
        val frenchFile = translationsDir.resolve("fr.properties")
        assertTrue(Files.exists(frenchFile))
    }

    @Test
    fun `generateAllLanguageFiles preserves existing translations`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("fr.properties"),
            "test.key1=Valeur francaise"
        )

        val keys = mapOf(
            "test.key1" to "Default 1",
            "test.key2" to "Default 2"
        )

        loader.generateAllLanguageFiles(keys)

        val frenchContent = Files.readString(translationsDir.resolve("fr.properties"))
        assertTrue(frenchContent.contains("test.key1=Valeur francaise"))
    }

    @Test
    fun `generateAllLanguageFiles removes obsolete keys`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("en.properties"),
            "test.obsolete=Should be removed\ntest.key1=Value"
        )

        val keys = mapOf("test.key1" to "Default 1")

        loader.generateAllLanguageFiles(keys)

        val content = Files.readString(translationsDir.resolve("en.properties"))
        assertFalse(content.contains("obsolete"))
        assertTrue(content.contains("test.key1"))
    }

    @Test
    fun `loadTranslations loads properties from file`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("en.properties"),
            "test.key=Test Value"
        )

        val properties = loader.loadTranslations("en")

        assertEquals("Test Value", properties.getProperty("test.key"))
    }

    @Test
    fun `loadTranslations falls back to base language`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("en.properties"),
            "test.key=English Value"
        )

        // Request en_US but only en.properties exists
        val properties = loader.loadTranslations("en_US")

        assertEquals("English Value", properties.getProperty("test.key"))
    }

    @Test
    fun `loadTranslations returns empty properties for missing file`() = runTest {
        Files.createDirectories(translationsDir)

        val properties = loader.loadTranslations("nonexistent")

        assertTrue(properties.isEmpty)
    }

    @Test
    fun `getCachedTranslations returns cached properties`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("en.properties"),
            "test.key=Cached Value"
        )

        // Load to populate cache
        loader.loadTranslations("en")

        // Get from cache
        val cached = loader.getCachedTranslations("en")

        assertNotNull(cached)
        assertEquals("Cached Value", cached.getProperty("test.key"))
    }

    @Test
    fun `getCachedTranslations returns null for unloaded locale`() {
        val cached = loader.getCachedTranslations("never_loaded")
        assertNull(cached)
    }

    @Test
    fun `clearCache removes cached translations`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("en.properties"),
            "test.key=Value"
        )

        loader.loadTranslations("en")
        assertNotNull(loader.getCachedTranslations("en"))

        loader.clearCache()

        assertNull(loader.getCachedTranslations("en"))
    }

    @Test
    fun `getAvailableLocales returns all locale files`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(translationsDir.resolve("en.properties"), "")
        Files.writeString(translationsDir.resolve("fr.properties"), "")
        Files.writeString(translationsDir.resolve("de.properties"), "")

        val locales = loader.getAvailableLocales()

        assertTrue(locales.contains("en"))
        assertTrue(locales.contains("fr"))
        assertTrue(locales.contains("de"))
        assertEquals(3, locales.size)
    }

    @Test
    fun `getAvailableLocales returns empty list for missing directory`() = runTest {
        val locales = loader.getAvailableLocales()
        assertTrue(locales.isEmpty())
    }

    @Test
    fun `getAvailableLocales ignores non-properties files`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(translationsDir.resolve("en.properties"), "")
        Files.writeString(translationsDir.resolve("readme.txt"), "")

        val locales = loader.getAvailableLocales()

        assertEquals(1, locales.size)
        assertTrue(locales.contains("en"))
    }
}
