package bruh.zchat.utils.translations

import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.*

/**
 * Test MessageKey enum for GUI messages.
 */
enum class TestGuiMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    WELCOME("welcome", "Welcome to the server!"),
    GOODBYE("goodbye", "Goodbye, see you soon!"),
    PLAYER_GREETING("player_greeting", "Hello, <player>!"),
    COLORED_MESSAGE("colored", "<green>This is green text</green>")
}

/**
 * Test MessageKey enum for command messages.
 */
enum class TestCommandMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    HELP("help", "Use /help for commands"),
    ERROR("error", "<red>Error: <message></red>"),
    SUCCESS("success", "<green>Success!</green>"),
    // Intentionally same key as GUI to test prefix isolation
    WELCOME("welcome", "Welcome! Type /help to get started")
}

/**
 * Comprehensive tests for the TranslationAPI.
 */
class TranslationAPITest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var translationsDir: Path
    private lateinit var api: TranslationAPI

    @BeforeEach
    fun setUp() {
        translationsDir = tempDir.resolve("translations")
        api = TranslationAPI(translationsDir)
    }

    @AfterEach
    fun tearDown() = runTest {
        api.clearCache()
    }

    // ==================== Registration Tests ====================

    @Test
    fun `register enum with valid prefix`() {
        api.register("gui", TestGuiMessages::class)
        assertTrue(api.getRegisteredPrefixes().contains("gui"))
    }

    @Test
    fun `register multiple enums with different prefixes`() {
        api.register("gui", TestGuiMessages::class)
        api.register("commands", TestCommandMessages::class)

        assertEquals(setOf("gui", "commands"), api.getRegisteredPrefixes())
    }

    @Test
    fun `registering same prefix twice throws exception`() {
        api.register("gui", TestGuiMessages::class)

        assertFailsWith<IllegalArgumentException> {
            api.register("gui", TestCommandMessages::class)
        }
    }

    @Test
    fun `registering same enum twice throws exception`() {
        api.register("gui", TestGuiMessages::class)

        assertFailsWith<IllegalArgumentException> {
            api.register("gui2", TestGuiMessages::class)
        }
    }

    @Test
    fun `registering with blank prefix throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            api.register("", TestGuiMessages::class)
        }

        assertFailsWith<IllegalArgumentException> {
            api.register("   ", TestGuiMessages::class)
        }
    }

    // ==================== Loading Tests ====================

    @Test
    fun `load creates translations directory`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        assertTrue(Files.exists(translationsDir))
    }

    @Test
    fun `load generates all ISO-639 language files`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        // Check that English file exists
        assertTrue(Files.exists(translationsDir.resolve("en.properties")))

        // Check that some common language files exist
        assertTrue(Files.exists(translationsDir.resolve("fr.properties")))
        assertTrue(Files.exists(translationsDir.resolve("de.properties")))
        assertTrue(Files.exists(translationsDir.resolve("es.properties")))
        assertTrue(Files.exists(translationsDir.resolve("ja.properties")))
    }

    @Test
    fun `load populates English file with defaults`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val englishFile = translationsDir.resolve("en.properties")
        val properties = Properties()
        Files.newInputStream(englishFile).use { properties.load(it) }

        assertEquals("Welcome to the server!", properties.getProperty("gui.welcome"))
        assertEquals("Goodbye, see you soon!", properties.getProperty("gui.goodbye"))
    }

    @Test
    fun `load creates empty non-English files`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val frenchFile = translationsDir.resolve("fr.properties")
        val content = Files.readString(frenchFile)

        // French file should have keys but empty values
        assertFalse(content.contains("Welcome to the server!"))
    }

    @Test
    fun `load preserves existing translations`() = runTest {
        // Pre-create translations directory and French file with translations
        Files.createDirectories(translationsDir)
        val frenchFile = translationsDir.resolve("fr.properties")
        val initialProps = Properties()
        initialProps.setProperty("gui.welcome", "Bienvenue sur le serveur!")
        Files.newOutputStream(frenchFile).use { initialProps.store(it, null) }

        api.register("gui", TestGuiMessages::class)
        api.load()

        // French translation should be preserved
        val properties = Properties()
        Files.newInputStream(frenchFile).use { properties.load(it) }
        assertEquals("Bienvenue sur le serveur!", properties.getProperty("gui.welcome"))
    }

    @Test
    fun `load removes obsolete keys`() = runTest {
        // Pre-create French file with obsolete key
        Files.createDirectories(translationsDir)
        val frenchFile = translationsDir.resolve("fr.properties")
        val initialProps = Properties()
        initialProps.setProperty("gui.obsolete_key", "This should be removed")
        initialProps.setProperty("gui.welcome", "Bienvenue!")
        Files.newOutputStream(frenchFile).use { initialProps.store(it, null) }

        api.register("gui", TestGuiMessages::class)
        api.load()

        val properties = Properties()
        Files.newInputStream(frenchFile).use { properties.load(it) }
        assertNull(properties.getProperty("gui.obsolete_key"))
        assertEquals("Bienvenue!", properties.getProperty("gui.welcome"))
    }

    @Test
    fun `isInitialized returns false before load`() {
        api.register("gui", TestGuiMessages::class)
        assertFalse(api.isInitialized())
    }

    @Test
    fun `isInitialized returns true after load`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()
        assertTrue(api.isInitialized())
    }

    // ==================== getString Tests ====================

    @Test
    fun `getString returns default for English`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        assertEquals("Welcome to the server!", api.getString(TestGuiMessages.WELCOME))
    }

    @Test
    fun `getString returns translation for non-English locale`() = runTest {
        // Pre-create French translations
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("fr.properties"),
            "gui.welcome=Bienvenue sur le serveur!"
        )

        api.register("gui", TestGuiMessages::class)
        api.load()
        api.switchLanguage("fr")

        assertEquals("Bienvenue sur le serveur!", api.getString(TestGuiMessages.WELCOME))
    }

    @Test
    fun `getString falls back to English when translation missing`() = runTest {
        // Create empty French file
        Files.createDirectories(translationsDir)
        Files.writeString(translationsDir.resolve("fr.properties"), "")

        api.register("gui", TestGuiMessages::class)
        api.load()
        api.switchLanguage("fr")

        // Should fall back to English default
        assertEquals("Welcome to the server!", api.getString(TestGuiMessages.WELCOME))
    }

    @Test
    fun `getString handles duplicate keys across enums with different prefixes`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.register("commands", TestCommandMessages::class)
        api.load()

        // Both enums have "welcome" key but different prefixes
        assertEquals("Welcome to the server!", api.getString(TestGuiMessages.WELCOME))
        assertEquals("Welcome! Type /help to get started", api.getString(TestCommandMessages.WELCOME))
    }

    // ==================== getComponent Tests ====================

    @Test
    fun `getComponent returns parsed MiniMessage component`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val component = api.getComponent(TestGuiMessages.WELCOME)
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Welcome to the server!", plainText)
    }

    @Test
    fun `getComponent with placeholders substitutes values`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val component = api.getComponent(TestGuiMessages.PLAYER_GREETING) {
            unparsed("player", "TestPlayer")
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Hello, TestPlayer!", plainText)
    }

    @Test
    fun `getComponent with component placeholder`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val playerComponent = Component.text("TestPlayer")
        val component = api.getComponent(TestGuiMessages.PLAYER_GREETING) {
            placeholder("player", playerComponent)
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Hello, TestPlayer!", plainText)
    }

    @Test
    fun `getComponent with string placeholder parses MiniMessage`() = runTest {
        api.register("commands", TestCommandMessages::class)
        api.load()

        val component = api.getComponent(TestCommandMessages.ERROR) {
            placeholder("message", "<bold>Critical failure</bold>")
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Error: Critical failure", plainText)
    }

    @Test
    fun `getComponentSync works without coroutines`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val component = api.getComponentSync(TestGuiMessages.WELCOME)
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Welcome to the server!", plainText)
    }

    @Test
    fun `getComponentSync with placeholders`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val component = api.getComponentSync(TestGuiMessages.PLAYER_GREETING) {
            unparsed("player", "SyncPlayer")
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)

        assertEquals("Hello, SyncPlayer!", plainText)
    }

    // ==================== Language Switching Tests ====================

    @Test
    fun `switchLanguage changes current locale`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        assertEquals("en", api.getCurrentLocale())

        api.switchLanguage("fr")
        assertEquals("fr", api.getCurrentLocale())
    }

    @Test
    fun `switchLanguage with region code`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        api.switchLanguage("fr_FR")
        assertEquals("fr_FR", api.getCurrentLocale())
    }

    @Test
    fun `switchLanguage with invalid locale throws exception`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        assertFailsWith<IllegalArgumentException> {
            api.switchLanguage("invalid")
        }

        assertFailsWith<IllegalArgumentException> {
            api.switchLanguage("xx_XX_XX")
        }
    }

    @Test
    fun `switchLanguage loads translations for new locale`() = runTest {
        // Pre-create German translations
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("de.properties"),
            "gui.welcome=Willkommen auf dem Server!"
        )

        api.register("gui", TestGuiMessages::class)
        api.load()

        api.switchLanguage("de")
        assertEquals("Willkommen auf dem Server!", api.getString(TestGuiMessages.WELCOME))
    }

    // ==================== Cache Tests ====================

    @Test
    fun `component cache improves performance`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        // First call parses the component
        val component1 = api.getComponent(TestGuiMessages.WELCOME)

        // Second call should return cached component (faster)
        val component2 = api.getComponent(TestGuiMessages.WELCOME)

        // Components should be equal
        assertEquals(
            PlainTextComponentSerializer.plainText().serialize(component1),
            PlainTextComponentSerializer.plainText().serialize(component2)
        )
    }

    @Test
    fun `clearCache clears component cache`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        api.getComponent(TestGuiMessages.WELCOME)
        api.clearCache()

        // Should still work after cache clear
        val component = api.getComponent(TestGuiMessages.WELCOME)
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)
        assertEquals("Welcome to the server!", plainText)
    }

    @Test
    fun `reload clears cache and reloads translations`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        // Modify French translation file
        Files.writeString(
            translationsDir.resolve("fr.properties"),
            "gui.welcome=Bienvenue modifie!"
        )

        api.switchLanguage("fr")
        api.reload()

        assertEquals("Bienvenue modifie!", api.getString(TestGuiMessages.WELCOME))
    }

    // ==================== Available Locales Tests ====================

    @Test
    fun `getAvailableLocales returns all generated locales`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        val locales = api.getAvailableLocales()

        assertTrue(locales.contains("en"))
        assertTrue(locales.contains("fr"))
        assertTrue(locales.contains("de"))
        assertTrue(locales.size >= ISO639Languages.ALL_LANGUAGES.size)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `getString for unregistered enum throws exception`() = runTest {
        api.register("gui", TestGuiMessages::class)
        api.load()

        assertFailsWith<IllegalArgumentException> {
            api.getString(TestCommandMessages.HELP)
        }
    }

    @Test
    fun `empty translation value falls back to English`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("fr.properties"),
            "gui.welcome="  // Empty value
        )

        api.register("gui", TestGuiMessages::class)
        api.load()
        api.switchLanguage("fr")

        // Empty value should fall back to English
        assertEquals("Welcome to the server!", api.getString(TestGuiMessages.WELCOME))
    }

    @Test
    fun `whitespace only translation falls back to English`() = runTest {
        Files.createDirectories(translationsDir)
        Files.writeString(
            translationsDir.resolve("fr.properties"),
            "gui.welcome=   "  // Whitespace only
        )

        api.register("gui", TestGuiMessages::class)
        api.load()
        api.switchLanguage("fr")

        // Whitespace should fall back to English
        assertEquals("Welcome to the server!", api.getString(TestGuiMessages.WELCOME))
    }
}
