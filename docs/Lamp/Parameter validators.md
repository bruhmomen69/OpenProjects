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

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/parameter-validators.md)

1. [How-to](/lamp-docs/how-to)

# Parameter validators

This page explains how to create ParameterValidators that perform checks against parameters after they are supplied by the user.

The `ParameterValidator` interface is a part of the commands framework that allows you to perform custom validation on command parameters. This can be particularly useful when you want to enforce specific rules or constraints on command inputs, such as validating ranges, formats, or other criteria.

### [](#implementing-a-custom-parametervalidator) Implementing a Custom `ParameterValidator`

Let's create an example of a custom `ParameterValidator` that checks if a number is within a specified range. This validator will make use of a custom annotation called `@Range` to specify the minimum and maximum allowed values.

#### [](#step-1-define-the-range-annotation) Step 1: Define the `@Range` Annotation

First, we'll create a custom annotation `@Range` to specify the minimum and maximum values for our parameter:

Java

Kotlin

Copy

```
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Range {
    double min();
    double max();
}
```

Copy

```
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Range(val min: Double, val max: Double)
```

#### [](#step-2-implement-the-rangevalidator) Step 2: Implement the `RangeValidator`

Next, we implement the `RangeValidator` using the `ParameterValidator` interface. This validator checks the parameter value against the `@Range` annotation's constraints:

Java

Kotlin

Copy

```
public enum RangeValidator implements ParameterValidator<CommandActor, Number> {
    INSTANCE;

    @Override
    public void validate(@NotNull CommandActor actor, Number value, @NotNull ParameterNode<CommandActor, Number> parameter, @NotNull Lamp<CommandActor> lamp) {
        Range range = parameter.getAnnotation(Range.class);
        if (range == null) return;
        double d = value.doubleValue();
        if (d < range.min())
            throw new CommandErrorException("Specified value (" + d + ") is less than minimum " + range.min());
        if (d > range.max())
            throw new CommandErrorException("Specified value (" + d + ") is greater than maximum " + range.max());
    }
}
```

Copy

```
object RangeValidator : ParameterValidator<CommandActor, Number> {
    
    override fun validate(
        actor: CommandActor,
        value: Number,
        parameter: ParameterNode<CommandActor, Number>,
        lamp: Lamp<CommandActor>
    ) {
        val range = parameter.getAnnotation(Range::class.java) ?: return
        val d = value.toDouble()
        if (d < range.min) 
            throw CommandErrorException("Specified value ($d) is less than minimum ${range.min}")
        if (d > range.max)
             throw CommandErrorException("Specified value ($d) is greater than maximum ${range.max}")
    }
}
```

#### [](#step-3-register-the-validator) Step 3: Register the Validator

To use the `RangeValidator`, you need to register it with your `Lamp` instance. This is done through the `Lamp.Builder`:

Java

Kotlin

Copy

```
var lamp = Lamp.builder()
    .parameterValidator(Number.class, RangeValidator.INSTANCE)
    .build();
```

Copy

```
val lamp = Lamp.builder<CommandActor>()
    .parameterValidator(Number::class.java, RangeValidator)
    .build()
```

#### [](#step-4-using-the-range-annotation-in-commands) Step 4: Using the `@Range` Annotation in Commands

Now, you can use the `@Range` annotation in your command methods to specify the valid range for numeric parameters:

Java

Kotlin

Copy

```
@Command("age")
public void setAge(CommandActor actor, @Range(min = 13, max = 99) int age) {
    /* ... */
}
```

Copy

```
@Command("age")
fun setAge(actor: CommandActor, @Range(min = 13.0, max = 99.0) age: Int) {
    /* ... */
}
```

In this example, the `setAge` command requires the `age` parameter to be between 18 and 99. If the user provides a value outside this range, the `RangeValidator` will throw an exception, and Lamp will handle it appropriately.

[PreviousCommand permissions](/lamp-docs/how-to/command-permissions)[NextCommand conditions](/lamp-docs/how-to/command-conditions)

Last updated 1 year ago

Was this helpful?