package bruh.regionrestore.cmd

/**
 * Annotation to provide template name suggestions for command parameters.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestTemplateName
