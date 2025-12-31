package bruh.regionrestore.cmd

/**
 * Annotation to provide suggestions for instance ID parameters.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestInstanceId
