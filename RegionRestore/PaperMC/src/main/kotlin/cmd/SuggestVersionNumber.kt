package bruh.regionrestore.cmd

/**
 * Annotation to provide numeric version ID suggestions for command parameters.
 * Unlike SuggestVersionId, this does not include the special "active" keyword.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestVersionNumber
