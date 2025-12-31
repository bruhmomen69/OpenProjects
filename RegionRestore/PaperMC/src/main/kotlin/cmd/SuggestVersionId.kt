package bruh.regionrestore.cmd

/**
 * Annotation to provide version ID suggestions for command parameters.
 * Suggests "active" plus all version IDs from existing templates.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestVersionId
