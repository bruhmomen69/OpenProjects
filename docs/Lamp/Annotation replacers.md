[🛖  
\
Fox Hut](/lamp-docs)

`CtrlK`

- Introduction
  
  - [Setting up](/lamp-docs)
  - [CommandActor, @Command and @Subcommand](/lamp-docs/introduction/commandactor-command-and-subcommand)
  - [Creating your first command](/lamp-docs/introduction/creating-your-first-command)
  - [Improving our greet command](/lamp-docs/introduction/improving-our-greet-command)
- Platforms
  
  - [Bukkit / Spigot / Paper](/lamp-docs/platforms/bukkit-spigot-paper)
  - [BungeeCord](/lamp-docs/platforms/bungeecord)
  - [Velocity](/lamp-docs/platforms/velocity)
  - [Sponge](/lamp-docs/platforms/sponge)
  - [Fabric](/lamp-docs/platforms/fabric)
  - [Brigadier](/lamp-docs/platforms/brigadier)
  - [Minestom](/lamp-docs/platforms/minestom)
  - [JDA](/lamp-docs/platforms/jda)
  - [Command line](/lamp-docs/platforms/command-line)
- How-to
  
  - [Creating variants of /teleport](/lamp-docs/how-to/creating-variants-of-teleport)
  - [Custom parameter types](/lamp-docs/how-to/custom-parameter-types)
  - [Suggestions and auto-completion](/lamp-docs/how-to/suggestions-and-auto-completion)
  - [Context parameters](/lamp-docs/how-to/context-parameters)
  - [Command permissions](/lamp-docs/how-to/command-permissions)
  - [Parameter validators](/lamp-docs/how-to/parameter-validators)
  - [Command conditions](/lamp-docs/how-to/command-conditions)
  - [Response handlers](/lamp-docs/how-to/response-handlers)
  - [Cooldowns](/lamp-docs/how-to/cooldowns)
  - [Help commands](/lamp-docs/how-to/help-commands)
  - [Annotation replacers](/lamp-docs/how-to/annotation-replacers)
  - [Orphan command](/lamp-docs/how-to/orphan-command)
  - [Exception handling](/lamp-docs/how-to/exception-handling)
  - [Hooks](/lamp-docs/how-to/hooks)
  - [Dependency injection](/lamp-docs/how-to/dependency-injection)
  - [Visitors](/lamp-docs/how-to/visitors)
  - [Customizing the dispatcher and failure behavior](/lamp-docs/how-to/customizing-the-dispatcher-and-failure-behavior)

[Powered by GitBook](https://www.gitbook.com/?utm_source=content&utm_medium=trademark&utm_campaign=1XJxI1qfaXuj9Pvzw9i7)

On this page

- [Overview](#overview)
- [Example: Creating and Using @PluginCommand](#example-creating-and-using-plugincommand)
- [Summary](#summary)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/annotation-replacers.md)

1. [How-to](/lamp-docs/how-to)

# Annotation replacers

This page explains how to use the AnnotationReplacer interface to dynamically replace custom annotations.

The `AnnotationReplacer` interface allows you to create dynamic annotations that can be replaced by other annotations at runtime. This feature is powerful for creating flexible and configurable annotations that are not restricted by static, compile-time constraints.

### [](#overview) Overview

#### [](#interface-definition) Interface Definition

Copy

```
@FunctionalInterface
public interface AnnotationReplacer<T extends Annotation> {

    /**
     * Returns a collection of annotations that will substitute the given annotation,
     * and be accessible in {@link AnnotationList#get(Class)}.
     *
     * @param element    The element (method, parameter, class, etc.)
     * @param annotation The annotation to replace.
     * @return The list of replacing annotations. The collection may be null or empty.
     */
    @Nullable Collection<Annotation> replaceAnnotation(@NotNull AnnotatedElement element, @NotNull T annotation);

}
```

#### [](#how-it-works) How It Works

1. **Define Your Custom Annotation**: Create an annotation that will be dynamically replaced.
2. **Implement the** `AnnotationReplacer` **Interface**: Define how your custom annotation will be replaced with others.
3. **Register the Replacer**: Use the builder to register your `AnnotationReplacer` with the framework.
4. **Apply the Custom Annotation**: Use the custom annotation in your code, and it will be replaced dynamically by the specified annotations.

### [](#example-creating-and-using-plugincommand) Example: Creating and Using `@PluginCommand`

In this example, we will create a custom `@PluginCommand` annotation that contains `path`, `description`, and `usage` attributes. We will then use an `AnnotationReplacer` to replace `@PluginCommand` with `@Command`, `@Description`, and `@Usage` annotations respectively.

#### [](#step-1-define-the-plugincommand-annotation) Step 1: Define the `@PluginCommand` Annotation

Java

Kotlin

Copy

```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginCommand {
    String path();
    String description();
    String usage();
}
```

Copy

```
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class PluginCommand(
    val path: String,
    val description: String,
    val usage: String
)
```

#### [](#step-2-implement-the-annotationreplacer-for-plugincommand) Step 2: Implement the `AnnotationReplacer` for `@PluginCommand`

Java

Kotlin

Copy

```
public class PluginCommandReplacer implements AnnotationReplacer<PluginCommand> {

    @Override
    public Collection<Annotation> replaceAnnotation(AnnotatedElement element, PluginCommand annotation) {
        // Create the replacing annotations dynamically
        Annotation commandAnnotation = Annotations.create(Command.class, "value", annotation.path());
        Annotation descriptionAnnotation = Annotations.create(Description.class, "value", annotation.description());
        Annotation usageAnnotation = Annotations.create(Usage.class, "value", annotation.usage());

        return Arrays.asList(commandAnnotation, descriptionAnnotation, usageAnnotation);
    }
}
```

Copy

```
class PluginCommandReplacer : AnnotationReplacer<PluginCommand> {

    override fun replaceAnnotation(element: AnnotatedElement, annotation: PluginCommand): Collection<Annotation> {
        // Create the replacing annotations dynamically
        val commandAnnotation = Annotations.create(Command::class.java, "value", annotation.path)
        val descriptionAnnotation = Annotations.create(Description::class.java, "value", annotation.description)
        val usageAnnotation = Annotations.create(Usage::class.java, "value", annotation.usage)

        return listOf(commandAnnotation, descriptionAnnotation, usageAnnotation)
    }
}
```

#### [](#step-3-register-the-replacer) Step 3: Register the Replacer

Java

Kotlin

Copy

```
BukkitLamp.builder(this)
    .annotationReplacer(PluginCommand.class, new PluginCommandReplacer())
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    .annotationReplacer(PluginCommand::class.java, PluginCommandReplacer())
    .build()
```

#### [](#step-4-using-the-plugincommand-annotation) Step 4: Using the `@PluginCommand` Annotation

Java

Kotlin

Copy

```
@PluginCommand(
    path = "example", 
    description = "An example command", 
    usage = "/example <arg>"
)
public void exampleCommand() {
    // Command implementation
}
```

Copy

```
@PluginCommand(
    path = "example", 
    description = "An example command", 
    usage = "/example <arg>"
)
fun exampleCommand() {
    // Command implementation
}
```

In this example:

- `@PluginCommand` is defined with `path`, `description`, and `usage` attributes.
- The `PluginCommandReplacer` dynamically replaces `@PluginCommand` with `@Command`, `@Description`, and `@Usage` annotations.
- The command method `exampleCommand` is annotated with `@PluginCommand`, which will be replaced at runtime.

### [](#summary) Summary

The `AnnotationReplacer` interface provides a flexible way to dynamically replace annotations, allowing for powerful and configurable annotation setups. By creating and registering custom annotation replacers, you can enhance your command framework's capabilities and adapt annotations to your needs.

[PreviousHelp commands](/lamp-docs/how-to/help-commands)[NextOrphan command](/lamp-docs/how-to/orphan-command)

Last updated 1 year ago

Was this helpful?