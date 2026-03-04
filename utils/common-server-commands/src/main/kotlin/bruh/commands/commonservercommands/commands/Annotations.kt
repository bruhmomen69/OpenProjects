package bruh.commands.commonservercommands.commands

/**
 * Annotation for parameters that should suggest game modes.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestGameMode

/**
 * Annotation for parameters that should suggest world names.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestWorld

/**
 * Annotation for parameters that should suggest online player names.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestOnlinePlayer

/**
 * Annotation for parameters that should suggest entity types.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestEntityType
