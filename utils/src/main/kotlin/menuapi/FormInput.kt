package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Annotation to mark a field as a form input field.
 * Fields without this annotation are skipped during form collection.
 *
 * @param index The order in which this field should be presented (lower = earlier)
 * @param label The display label for this field
 * @param description Optional description/hint text
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormField(
    val index: Int,
    val label: String,
    val description: String = ""
)

/**
 * Additional configuration for String input fields.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class StringInput(
    val placeholder: String = "",
    val minLength: Int = 0,
    val maxLength: Int = Int.MAX_VALUE,
    val regex: String = "",
    val regexError: String = "Invalid format"
)

/**
 * Additional configuration for numeric input fields (Int, Long, Float, Double).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NumberInput(
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE,
    val step: Double = 1.0
)

/**
 * Additional configuration for Boolean input fields.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class BooleanInput(
    val trueLabel: String = "Yes",
    val falseLabel: String = "No",
    val trueMaterial: String = "LIME_DYE",
    val falseMaterial: String = "GRAY_DYE"
)

/**
 * Additional configuration for Enum input fields.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnumInput(
    val material: String = "PAPER"
)

/**
 * Result of form data collection.
 */
sealed class FormResult<out T> {
    data class Success<T>(val data: T) : FormResult<T>()
    data object Cancelled : FormResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Cancelled -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Cancelled -> throw IllegalStateException("Form was cancelled")
    }

    val isSuccess: Boolean get() = this is Success
    val isCancelled: Boolean get() = this is Cancelled
}

/**
 * Internal representation of a form field for processing.
 */
internal data class FormFieldInfo(
    val index: Int,
    val label: String,
    val description: String,
    val property: KMutableProperty1<Any, Any?>,
    val type: KClass<*>,
    val stringConfig: StringInput?,
    val numberConfig: NumberInput?,
    val booleanConfig: BooleanInput?,
    val enumConfig: EnumInput?
)

/**
 * Form data collector - collects data from a player using GUIs and anvil inputs.
 */
class FormDataCollector<T : Any>(
    private val menuApi: MenuAPI,
    private val dataClass: T,
    private val title: String = "Form Input"
) {
    private val fields: List<FormFieldInfo>

    init {
        fields = extractFields(dataClass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFields(data: Any): List<FormFieldInfo> {
        val kClass = data::class
        return kClass.memberProperties
            .mapNotNull { prop ->
                val formField = prop.findAnnotation<FormField>() ?: return@mapNotNull null
                val mutableProp = prop as? KMutableProperty1<Any, Any?> ?: return@mapNotNull null
                mutableProp.isAccessible = true

                FormFieldInfo(
                    index = formField.index,
                    label = formField.label,
                    description = formField.description,
                    property = mutableProp,
                    type = prop.returnType.classifier as? KClass<*> ?: Any::class,
                    stringConfig = prop.findAnnotation(),
                    numberConfig = prop.findAnnotation(),
                    booleanConfig = prop.findAnnotation(),
                    enumConfig = prop.findAnnotation()
                )
            }
            .sortedBy { it.index }
    }

    /**
     * Collect form data asynchronously, returning a CompletableFuture.
     */
    fun collectAsync(player: Player): CompletableFuture<FormResult<T>> {
        val future = CompletableFuture<FormResult<T>>()

        if (fields.isEmpty()) {
            future.complete(FormResult.Success(dataClass))
            return future
        }

        collectFieldsSequentially(player, 0, future)
        return future
    }

    /**
     * Collect form data using a suspend function.
     */
    suspend fun collect(player: Player): FormResult<T> {
        return suspendCoroutine { continuation ->
            collectAsync(player).whenComplete { result, _ ->
                continuation.resume(result)
            }
        }
    }

    private fun collectFieldsSequentially(
        player: Player,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        if (fieldIndex >= fields.size) {
            future.complete(FormResult.Success(dataClass))
            return
        }

        val field = fields[fieldIndex]

        when {
            field.type == String::class -> collectStringField(player, field, fieldIndex, future)
            field.type == Int::class -> collectIntField(player, field, fieldIndex, future)
            field.type == Long::class -> collectLongField(player, field, fieldIndex, future)
            field.type == Float::class -> collectFloatField(player, field, fieldIndex, future)
            field.type == Double::class -> collectDoubleField(player, field, fieldIndex, future)
            field.type == Boolean::class -> collectBooleanField(player, field, fieldIndex, future)
            field.type.java.isEnum -> collectEnumField(player, field, fieldIndex, future)
            else -> {
                // Skip unsupported types
                collectFieldsSequentially(player, fieldIndex + 1, future)
            }
        }
    }

    private fun collectStringField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.stringConfig
        val currentValue = field.property.get(dataClass) as? String ?: ""

        val inputBuilder = AnvilInputBuilder(menuApi.plugin).apply {
            title = field.label
            initialText = config?.placeholder?.ifEmpty { currentValue } ?: currentValue

            if (config != null) {
                if (config.minLength > 0) {
                    requireMinLength(config.minLength)
                }
                if (config.maxLength < Int.MAX_VALUE) {
                    requireMaxLength(config.maxLength)
                }
                if (config.regex.isNotEmpty()) {
                    requireMatch(Regex(config.regex), config.regexError)
                }
            }
        }

        inputBuilder.openAsync(player).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    field.property.set(dataClass, result.value)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
                is AnvilInputResult.Cancelled -> {
                    future.complete(FormResult.Cancelled)
                }
            }
        }
    }

    private fun collectIntField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.numberConfig
        val currentValue = field.property.get(dataClass) as? Int

        val inputBuilder = AnvilNumberInputBuilder(menuApi.plugin).apply {
            title = field.label
            initialValue = currentValue
            allowDecimals = false
            if (config != null) {
                if (config.min > Double.MIN_VALUE) min = config.min.toInt()
                if (config.max < Double.MAX_VALUE) max = config.max.toInt()
            }
        }

        inputBuilder.openIntAsync(player).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    field.property.set(dataClass, result.value)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
                is AnvilInputResult.Cancelled -> {
                    future.complete(FormResult.Cancelled)
                }
            }
        }
    }

    private fun collectLongField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.numberConfig
        val currentValue = field.property.get(dataClass) as? Long

        val inputBuilder = AnvilNumberInputBuilder(menuApi.plugin).apply {
            title = field.label
            initialValue = currentValue
            allowDecimals = false
            if (config != null) {
                if (config.min > Double.MIN_VALUE) min = config.min.toLong()
                if (config.max < Double.MAX_VALUE) max = config.max.toLong()
            }
        }

        inputBuilder.openLongAsync(player).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    field.property.set(dataClass, result.value)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
                is AnvilInputResult.Cancelled -> {
                    future.complete(FormResult.Cancelled)
                }
            }
        }
    }

    private fun collectFloatField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.numberConfig
        val currentValue = field.property.get(dataClass) as? Float

        val inputBuilder = AnvilNumberInputBuilder(menuApi.plugin).apply {
            title = field.label
            initialValue = currentValue
            allowDecimals = true
            if (config != null) {
                if (config.min > Double.MIN_VALUE) min = config.min
                if (config.max < Double.MAX_VALUE) max = config.max
            }
        }

        inputBuilder.openDoubleAsync(player).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    field.property.set(dataClass, result.value.toFloat())
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
                is AnvilInputResult.Cancelled -> {
                    future.complete(FormResult.Cancelled)
                }
            }
        }
    }

    private fun collectDoubleField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.numberConfig
        val currentValue = field.property.get(dataClass) as? Double

        val inputBuilder = AnvilNumberInputBuilder(menuApi.plugin).apply {
            title = field.label
            initialValue = currentValue
            allowDecimals = true
            if (config != null) {
                if (config.min > Double.MIN_VALUE) min = config.min
                if (config.max < Double.MAX_VALUE) max = config.max
            }
        }

        inputBuilder.openDoubleAsync(player).thenAccept { result ->
            when (result) {
                is AnvilInputResult.Success -> {
                    field.property.set(dataClass, result.value)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
                is AnvilInputResult.Cancelled -> {
                    future.complete(FormResult.Cancelled)
                }
            }
        }
    }

    private fun collectBooleanField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val config = field.booleanConfig
        val currentValue = field.property.get(dataClass) as? Boolean ?: false

        val trueLabel = config?.trueLabel ?: "Yes"
        val falseLabel = config?.falseLabel ?: "No"
        val trueMaterial = try {
            XMaterial.valueOf(config?.trueMaterial ?: "LIME_DYE")
        } catch (e: Exception) {
            XMaterial.LIME_DYE
        }
        val falseMaterial = try {
            XMaterial.valueOf(config?.falseMaterial ?: "GRAY_DYE")
        } catch (e: Exception) {
            XMaterial.GRAY_DYE
        }

        // Track whether this field was actually answered. If the menu closes
        // without selecting a value, treat it as a cancellation. If we *do*
        // select a value, and then the menu closes because the next step
        // opens (another menu or anvil), we must NOT cancel the whole form.
        var fieldCompleted = false

        val menu = menuApi.dynamic {
            title = Component.text(field.label)

            item(trueMaterial) {
                name = Component.text(trueLabel)
                if (currentValue) glow()
                if (field.description.isNotEmpty()) {
                    lore(field.description)
                }
                onClickDeny { _, _ ->
                    fieldCompleted = true
                    field.property.set(dataClass, true)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
            }

            item(falseMaterial) {
                name = Component.text(falseLabel)
                if (!currentValue) glow()
                if (field.description.isNotEmpty()) {
                    lore(field.description)
                }
                onClickDeny { _, _ ->
                    fieldCompleted = true
                    field.property.set(dataClass, false)
                    collectFieldsSequentially(player, fieldIndex + 1, future)
                }
            }
        }

        menu.onClose = { _, _ ->
            // Only cancel if the user closed the menu *without* choosing a
            // value. If a value was chosen, the next step is already in
            // progress and the overall form should continue.
            if (!fieldCompleted && !future.isDone) {
                future.complete(FormResult.Cancelled)
            }
        }

        menuApi.open(menu, player)
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectEnumField(
        player: Player,
        field: FormFieldInfo,
        fieldIndex: Int,
        future: CompletableFuture<FormResult<T>>
    ) {
        val enumClass = field.type.java as Class<Enum<*>>
        val enumConstants = enumClass.enumConstants
        val currentValue = field.property.get(dataClass) as? Enum<*>

        val config = field.enumConfig
        val material = try {
            XMaterial.valueOf(config?.material ?: "PAPER")
        } catch (e: Exception) {
            XMaterial.PAPER
        }

        // Same logic as for booleans: only treat closing as cancellation if
        // the user never picked an enum value.
        var fieldCompleted = false

        val menu = menuApi.dynamic {
            title = Component.text(field.label)

            for (enumValue in enumConstants) {
                item(material) {
                    name = Component.text(enumValue.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() })
                    if (enumValue == currentValue) glow()
                    if (field.description.isNotEmpty()) {
                        lore(field.description)
                    }
                    onClickDeny { _, _ ->
                        fieldCompleted = true
                        field.property.set(dataClass, enumValue)
                        collectFieldsSequentially(player, fieldIndex + 1, future)
                    }
                }
            }
        }

        menu.onClose = { _, _ ->
            if (!fieldCompleted && !future.isDone) {
                future.complete(FormResult.Cancelled)
            }
        }

        menuApi.open(menu, player)
    }
}

/**
 * Extension function to collect form data from a data class.
 *
 * Usage:
 * ```kotlin
 * data class MyForm(
 *     @FormField(0, "Player Name")
 *     @StringInput(minLength = 3, maxLength = 16)
 *     var playerName: String = "",
 *
 *     @FormField(1, "Amount")
 *     @NumberInput(min = 1.0, max = 64.0)
 *     var amount: Int = 1,
 *
 *     @FormField(2, "Enabled")
 *     @BooleanInput(trueLabel = "On", falseLabel = "Off")
 *     var enabled: Boolean = false
 * )
 *
 * val result = menuApi.getFormData(MyForm(), player)
 * if (result.isSuccess) {
 *     val data = result.getOrThrow()
 *     // Use data.playerName, data.amount, data.enabled
 * }
 * ```
 */
suspend fun <T : Any> MenuAPI.getFormData(
    dataClass: T,
    player: Player,
    title: String = "Form Input"
): FormResult<T> {
    return FormDataCollector(this, dataClass, title).collect(player)
}

/**
 * Async version of getFormData.
 */
fun <T : Any> MenuAPI.getFormDataAsync(
    dataClass: T,
    player: Player,
    title: String = "Form Input"
): CompletableFuture<FormResult<T>> {
    return FormDataCollector(this, dataClass, title).collectAsync(player)
}
