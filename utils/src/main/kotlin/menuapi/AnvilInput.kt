package bruh.zchat.utils.menuapi

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Result of an anvil input operation.
 */
sealed class AnvilInputResult<out T> {
    /**
     * The user submitted a value.
     */
    data class Success<T>(val value: T) : AnvilInputResult<T>()

    /**
     * The user cancelled/closed the input.
     */
    data object Cancelled : AnvilInputResult<Nothing>()

    /**
     * Get the value or null if cancelled.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Cancelled -> null
    }

    /**
     * Get the value or a default if cancelled.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Cancelled -> default
    }

    /**
     * Get the value or throw if cancelled.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Cancelled -> throw IllegalStateException("Input was cancelled")
    }

    /**
     * Map the success value.
     */
    inline fun <R> map(transform: (T) -> R): AnvilInputResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Cancelled -> Cancelled
    }

    /**
     * Check if this is a success.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Check if this was cancelled.
     */
    val isCancelled: Boolean get() = this is Cancelled
}

/**
 * Input validation result.
 */
sealed class InputValidation {
    /**
     * Input is valid.
     */
    data object Valid : InputValidation()

    /**
     * Input is invalid with a message.
     */
    data class Invalid(val message: String) : InputValidation()

    companion object {
        fun valid() = Valid
        fun invalid(message: String) = Invalid(message)
    }
}

/**
 * Builder for creating an anvil text input dialog.
 */
class AnvilInputBuilder(private val plugin: Plugin) {
    /** The title of the anvil GUI */
    var title: String = "Enter text"

    /** Adventure Component title (takes precedence over string title) */
    var titleComponent: Component? = null

    /** The initial text in the input field */
    var initialText: String = ""

    /** The item to display in the left slot */
    var itemLeft: ItemStack? = null

    /** The item to display in the right slot */
    var itemRight: ItemStack? = null

    /** Whether to prevent the player from closing the GUI */
    var preventClose: Boolean = false

    /** Validator function - return InputValidation.Valid or InputValidation.Invalid */
    var validator: ((String) -> InputValidation)? = null

    /** Text to show when validation fails (replaces input text) */
    var invalidText: String? = null

    /**
     * Set the title using an Adventure Component.
     */
    fun title(component: Component) {
        titleComponent = component
    }

    /**
     * Set the left item using a VItem.
     */
    fun itemLeft(vItem: VItem) {
        itemLeft = vItem.build()
    }

    /**
     * Set the right item using a VItem.
     */
    fun itemRight(vItem: VItem) {
        itemRight = vItem.build()
    }

    /**
     * Set a simple text validator.
     */
    fun validate(validator: (String) -> Boolean, errorMessage: String = "Invalid input") {
        this.validator = { text ->
            if (validator(text)) InputValidation.Valid
            else InputValidation.Invalid(errorMessage)
        }
    }

    /**
     * Require non-empty input.
     */
    fun requireNonEmpty(errorMessage: String = "Input cannot be empty") {
        validate({ it.isNotBlank() }, errorMessage)
    }

    /**
     * Require input to match a regex.
     */
    fun requireMatch(regex: Regex, errorMessage: String = "Invalid format") {
        validate({ regex.matches(it) }, errorMessage)
    }

    /**
     * Require minimum length.
     */
    fun requireMinLength(length: Int, errorMessage: String = "Must be at least $length characters") {
        validate({ it.length >= length }, errorMessage)
    }

    /**
     * Require maximum length.
     */
    fun requireMaxLength(length: Int, errorMessage: String = "Must be at most $length characters") {
        validate({ it.length <= length }, errorMessage)
    }

    /**
     * Open the anvil input for a player and return a CompletableFuture.
     */
    fun openAsync(player: Player): CompletableFuture<AnvilInputResult<String>> {
        val future = CompletableFuture<AnvilInputResult<String>>()

        val builder = AnvilGUI.Builder()
            .plugin(plugin)
            .text(initialText)

        // Set title
        titleComponent?.let { component ->
            builder.jsonTitle(GsonComponentSerializer.gson().serialize(component))
        } ?: builder.title(title)

        // Set items
        itemLeft?.let { builder.itemLeft(it) }
        itemRight?.let { builder.itemRight(it) }

        // Prevent close if requested
        if (preventClose) {
            builder.preventClose()
        }

        // Handle close
        builder.onClose { stateSnapshot ->
            if (!future.isDone) {
                future.complete(AnvilInputResult.Cancelled)
            }
        }

        // Handle click
        builder.onClick { slot, stateSnapshot ->
            if (slot != AnvilGUI.Slot.OUTPUT) {
                return@onClick emptyList()
            }

            val text = stateSnapshot.text

            // Validate if validator is set
            validator?.let { validatorFn ->
                when (val result = validatorFn(text)) {
                    is InputValidation.Valid -> {
                        future.complete(AnvilInputResult.Success(text))
                        listOf(AnvilGUI.ResponseAction.close())
                    }
                    is InputValidation.Invalid -> {
                        listOf(AnvilGUI.ResponseAction.replaceInputText(invalidText ?: result.message))
                    }
                }
            } ?: run {
                future.complete(AnvilInputResult.Success(text))
                listOf(AnvilGUI.ResponseAction.close())
            }
        }

        builder.open(player)
        return future
    }

    /**
     * Open the anvil input for a player and suspend until complete.
     */
    suspend fun open(player: Player): AnvilInputResult<String> {
        return suspendCoroutine { continuation ->
            openAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }
}

/**
 * Builder for creating an anvil number input dialog.
 */
class AnvilNumberInputBuilder(private val plugin: Plugin) {
    /** The title of the anvil GUI */
    var title: String = "Enter number"

    /** Adventure Component title (takes precedence over string title) */
    var titleComponent: Component? = null

    /** The initial value */
    var initialValue: Number? = null

    /** The item to display in the left slot */
    var itemLeft: ItemStack? = null

    /** The item to display in the right slot */
    var itemRight: ItemStack? = null

    /** Whether to prevent the player from closing the GUI */
    var preventClose: Boolean = false

    /** Minimum allowed value (inclusive) */
    var min: Number? = null

    /** Maximum allowed value (inclusive) */
    var max: Number? = null

    /** Whether to allow decimal numbers */
    var allowDecimals: Boolean = false

    /** Whether to allow negative numbers */
    var allowNegative: Boolean = true

    /** Error message for invalid number format */
    var invalidFormatMessage: String = "Please enter a valid number"

    /** Error message for out of range */
    var outOfRangeMessage: String? = null

    /**
     * Set the title using an Adventure Component.
     */
    fun title(component: Component) {
        titleComponent = component
    }

    /**
     * Set the left item using a VItem.
     */
    fun itemLeft(vItem: VItem) {
        itemLeft = vItem.build()
    }

    /**
     * Set the right item using a VItem.
     */
    fun itemRight(vItem: VItem) {
        itemRight = vItem.build()
    }

    /**
     * Set the allowed range.
     */
    fun range(min: Number, max: Number) {
        this.min = min
        this.max = max
    }

    /**
     * Open the anvil input for a player and return a CompletableFuture with a Double.
     */
    fun openDoubleAsync(player: Player): CompletableFuture<AnvilInputResult<Double>> {
        val future = CompletableFuture<AnvilInputResult<Double>>()

        val builder = AnvilGUI.Builder()
            .plugin(plugin)
            .text(initialValue?.toString() ?: "")

        // Set title
        titleComponent?.let { component ->
            builder.jsonTitle(GsonComponentSerializer.gson().serialize(component))
        } ?: builder.title(title)

        // Set items
        itemLeft?.let { builder.itemLeft(it) }
        itemRight?.let { builder.itemRight(it) }

        if (preventClose) {
            builder.preventClose()
        }

        builder.onClose { stateSnapshot ->
            if (!future.isDone) {
                future.complete(AnvilInputResult.Cancelled)
            }
        }

        builder.onClick { slot, stateSnapshot ->
            if (slot != AnvilGUI.Slot.OUTPUT) {
                return@onClick emptyList()
            }

            val text = stateSnapshot.text.trim()
            val number = text.toDoubleOrNull()

            when {
                number == null -> {
                    listOf(AnvilGUI.ResponseAction.replaceInputText(invalidFormatMessage))
                }
                !allowDecimals && number != number.toLong().toDouble() -> {
                    listOf(AnvilGUI.ResponseAction.replaceInputText("Whole numbers only"))
                }
                !allowNegative && number < 0 -> {
                    listOf(AnvilGUI.ResponseAction.replaceInputText("Positive numbers only"))
                }
                min != null && number < min!!.toDouble() -> {
                    val msg = outOfRangeMessage ?: "Minimum: ${min}"
                    listOf(AnvilGUI.ResponseAction.replaceInputText(msg))
                }
                max != null && number > max!!.toDouble() -> {
                    val msg = outOfRangeMessage ?: "Maximum: ${max}"
                    listOf(AnvilGUI.ResponseAction.replaceInputText(msg))
                }
                else -> {
                    future.complete(AnvilInputResult.Success(number))
                    listOf(AnvilGUI.ResponseAction.close())
                }
            }
        }

        builder.open(player)
        return future
    }

    /**
     * Open the anvil input for a player and return a CompletableFuture with an Int.
     */
    fun openIntAsync(player: Player): CompletableFuture<AnvilInputResult<Int>> {
        allowDecimals = false
        return openDoubleAsync(player).thenApply { result ->
            result.map { it.toInt() }
        }
    }

    /**
     * Open the anvil input for a player and return a CompletableFuture with a Long.
     */
    fun openLongAsync(player: Player): CompletableFuture<AnvilInputResult<Long>> {
        allowDecimals = false
        return openDoubleAsync(player).thenApply { result ->
            result.map { it.toLong() }
        }
    }

    /**
     * Open the anvil input for a player and suspend until complete (Double).
     */
    suspend fun openDouble(player: Player): AnvilInputResult<Double> {
        return suspendCoroutine { continuation ->
            openDoubleAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }

    /**
     * Open the anvil input for a player and suspend until complete (Int).
     */
    suspend fun openInt(player: Player): AnvilInputResult<Int> {
        return suspendCoroutine { continuation ->
            openIntAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }

    /**
     * Open the anvil input for a player and suspend until complete (Long).
     */
    suspend fun openLong(player: Player): AnvilInputResult<Long> {
        return suspendCoroutine { continuation ->
            openLongAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }
}

/**
 * Builder for a raw anvil GUI with full control.
 */
class AnvilBuilder(private val plugin: Plugin) {
    /** The title of the anvil GUI */
    var title: String = ""

    /** Adventure Component title */
    var titleComponent: Component? = null

    /** The initial text */
    var text: String = ""

    /** The item to display in the left slot */
    var itemLeft: ItemStack? = null

    /** The item to display in the right slot */
    var itemRight: ItemStack? = null

    /** Whether to prevent the player from closing the GUI */
    var preventClose: Boolean = false

    /** Called when the output slot is clicked */
    var onComplete: ((Player, String) -> List<AnvilGUI.ResponseAction>)? = null

    /** Called when the GUI is closed */
    var onClose: ((Player) -> Unit)? = null

    /**
     * Set the title using an Adventure Component.
     */
    fun title(component: Component) {
        titleComponent = component
    }

    /**
     * Set the left item using a VItem.
     */
    fun itemLeft(vItem: VItem) {
        itemLeft = vItem.build()
    }

    /**
     * Set the right item using a VItem.
     */
    fun itemRight(vItem: VItem) {
        itemRight = vItem.build()
    }

    /**
     * Open the anvil for a player.
     */
    fun open(player: Player): AnvilGUI {
        val builder = AnvilGUI.Builder()
            .plugin(plugin)
            .text(text)

        // Set title
        if (titleComponent != null) {
            builder.jsonTitle(GsonComponentSerializer.gson().serialize(titleComponent!!))
        } else if (title.isNotEmpty()) {
            builder.title(title)
        }

        // Set items
        itemLeft?.let { builder.itemLeft(it) }
        itemRight?.let { builder.itemRight(it) }

        if (preventClose) {
            builder.preventClose()
        }

        onClose?.let { handler ->
            builder.onClose { stateSnapshot ->
                handler(stateSnapshot.player)
            }
        }

        onComplete?.let { handler ->
            builder.onClick { slot, stateSnapshot ->
                if (slot != AnvilGUI.Slot.OUTPUT) {
                    return@onClick emptyList()
                }
                handler(stateSnapshot.player, stateSnapshot.text)
            }
        }

        return builder.open(player)
    }
}

/**
 * Extension functions for MenuAPI to create anvil inputs.
 */

/**
 * Create a text input anvil dialog.
 */
inline fun MenuAPI.textInput(builder: AnvilInputBuilder.() -> Unit): AnvilInputBuilder {
    return AnvilInputBuilder(plugin).apply(builder)
}

/**
 * Create a number input anvil dialog.
 */
inline fun MenuAPI.numberInput(builder: AnvilNumberInputBuilder.() -> Unit): AnvilNumberInputBuilder {
    return AnvilNumberInputBuilder(plugin).apply(builder)
}

/**
 * Create a raw anvil dialog with full control.
 */
inline fun MenuAPI.anvil(builder: AnvilBuilder.() -> Unit): AnvilBuilder {
    return AnvilBuilder(plugin).apply(builder)
}

/**
 * Quick text input - opens immediately and returns a CompletableFuture.
 */
fun MenuAPI.promptTextAsync(
    player: Player,
    title: String = "Enter text",
    initialText: String = "",
    validator: ((String) -> InputValidation)? = null
): CompletableFuture<AnvilInputResult<String>> {
    return AnvilInputBuilder(plugin).apply {
        this.title = title
        this.initialText = initialText
        this.validator = validator
    }.openAsync(player)
}

/**
 * Quick text input - opens immediately and suspends until complete.
 */
suspend fun MenuAPI.promptText(
    player: Player,
    title: String = "Enter text",
    initialText: String = "",
    validator: ((String) -> InputValidation)? = null
): AnvilInputResult<String> {
    return AnvilInputBuilder(plugin).apply {
        this.title = title
        this.initialText = initialText
        this.validator = validator
    }.open(player)
}

/**
 * Quick integer input - opens immediately and returns a CompletableFuture.
 */
fun MenuAPI.promptIntAsync(
    player: Player,
    title: String = "Enter number",
    initialValue: Int? = null,
    min: Int? = null,
    max: Int? = null
): CompletableFuture<AnvilInputResult<Int>> {
    return AnvilNumberInputBuilder(plugin).apply {
        this.title = title
        initialValue?.let { this.initialValue = it }
        min?.let { this.min = it }
        max?.let { this.max = it }
    }.openIntAsync(player)
}

/**
 * Quick integer input - opens immediately and suspends until complete.
 */
suspend fun MenuAPI.promptInt(
    player: Player,
    title: String = "Enter number",
    initialValue: Int? = null,
    min: Int? = null,
    max: Int? = null
): AnvilInputResult<Int> {
    return AnvilNumberInputBuilder(plugin).apply {
        this.title = title
        initialValue?.let { this.initialValue = it }
        min?.let { this.min = it }
        max?.let { this.max = it }
    }.openInt(player)
}

/**
 * Quick double input - opens immediately and returns a CompletableFuture.
 */
fun MenuAPI.promptDoubleAsync(
    player: Player,
    title: String = "Enter number",
    initialValue: Double? = null,
    min: Double? = null,
    max: Double? = null
): CompletableFuture<AnvilInputResult<Double>> {
    return AnvilNumberInputBuilder(plugin).apply {
        this.title = title
        this.allowDecimals = true
        initialValue?.let { this.initialValue = it }
        min?.let { this.min = it }
        max?.let { this.max = it }
    }.openDoubleAsync(player)
}

/**
 * Quick double input - opens immediately and suspends until complete.
 */
suspend fun MenuAPI.promptDouble(
    player: Player,
    title: String = "Enter number",
    initialValue: Double? = null,
    min: Double? = null,
    max: Double? = null
): AnvilInputResult<Double> {
    return AnvilNumberInputBuilder(plugin).apply {
        this.title = title
        this.allowDecimals = true
        initialValue?.let { this.initialValue = it }
        min?.let { this.min = it }
        max?.let { this.max = it }
    }.openDouble(player)
}
