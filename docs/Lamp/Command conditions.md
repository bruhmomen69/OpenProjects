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

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/command-conditions.md)

1. [How-to](/lamp-docs/how-to)

# Command conditions

This page explains how to use the CommandCondition interface to restrict command execution based on custom conditions

The `CommandCondition` interface allows you to define conditions that must be met for a command invocation to proceed. This is useful for performing checks based on specific annotations or other criteria to control command execution flow.

### [](#implementing-a-custom-commandcondition) Implementing a Custom `CommandCondition`

In this example, we will create a custom condition that checks if the command actor is an operator (op). We will use a custom annotation `@IsOpped` to mark commands that should only be executable by ops. If the condition is not met, the condition will throw an exception, preventing the command from executing.

#### [](#step-1-define-the-isopped-annotation) Step 1: Define the `@IsOpped` Annotation

The `@IsOpped` annotation will be used to mark commands that require the actor to be an operator:

Java

Kotlin

Copy

```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IsOpped {}
```

Copy

```
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IsOpped
```

#### [](#step-2-implement-the-isoppedcondition) Step 2: Implement the `IsOppedCondition`

Next, we create a `CommandCondition` implementation that checks if the actor is an operator by inspecting the `@IsOpped` annotation:

Java

Kotlin

Copy

```
public enum IsOppedCondition implements CommandCondition<BukkitCommandActor> {
    INSTANCE;

    @Override
    public void test(@NotNull ExecutionContext<BukkitCommandActor> context, @NotNull StringStream input) {
        boolean requiresOp = context.command().annotations().contains(IsOpped.class);
        if (requiresOp && !context.actor().sender().isOp()) {
            throw new CommandErrorException("You must be an operator to execute this command.");
        }
    }
}
```

Copy

```
object IsOppedCondition : CommandCondition<BukkitCommandActor> {

    override fun test(context: ExecutionContext<BukkitCommandActor>, input: StringStream) {
        val requiresOp = context.command().annotations().contains(IsOpped::class.java)
        if (requiresOp && !context.actor().sender().isOp()) {
            throw CommandErrorException("You must be an operator to execute this command.")
        }
    }
}
```

#### [](#step-3-register-the-condition) Step 3: Register the Condition

To use the `IsOppedCondition`, you need to register it with your `Lamp` instance, specifying that it should be used whenever the `@IsOpped` annotation is present:

Java

Kotlin

Copy

```
var lamp = BukkitLamp.builder(this)
    .condition(IsOppedCondition.INSTANCE)
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    .condition(IsOppedCondition)
    .build()
```

#### [](#step-4-applying-the-isopped-annotation-to-commands) Step 4: Applying the `@IsOpped` Annotation to Commands

Now, you can apply the `@IsOpped` annotation to any command method that should only be accessible by operators:

Java

Kotlin

Copy

```
@IsOpped
@Command("some admin command")
public void opOnlyCommand(Player player) {
    /* ... */
}
```

Copy

```
@IsOpped
@Command("some admin command")
fun opOnlyCommand(Player player) {
    /* ... */
}
```

In this example, the `opOnlyCommand` method is marked with the `@IsOpped` annotation, indicating that it requires the actor to be an operator. If a non-operator attempts to use this command, the `IsOppedCondition` will throw a `CommandErrorException`, and the command framework will handle the exception appropriately.

[PreviousParameter validators](/lamp-docs/how-to/parameter-validators)[NextResponse handlers](/lamp-docs/how-to/response-handlers)

Last updated 1 year ago

Was this helpful?