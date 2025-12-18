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
- [Example](#example)
- [Summary](#summary)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/orphan-command.md)

1. [How-to](/lamp-docs/how-to)

# Orphan command

This page explains how to use orphan commands, which are commands defined without the @Command annotation and whose path will be determined dynamically at runtime.

Due to the nature of annotations, all commands and subcommands in a typical command framework must have known paths at compile-time. This requirement makes it impossible to create commands whose paths are dynamically determined, such as those sourced from configuration files or user input. The `OrphanCommand` API solves this limitation by allowing commands to have paths that are resolved at runtime.

### [](#overview) Overview

#### [](#what-is-an-orphancommand) What is an `OrphanCommand`?

An `OrphanCommand` is a command whose parent or path is not known at compile-time. Implementing the `OrphanCommand` interface signals to the framework (Lamp) that the command's path will be dynamically determined and should be resolved at runtime.

#### [](#how-to-register-an-orphancommand) How to Register an `OrphanCommand`

To register an `OrphanCommand`, the command must be wrapped using the `Orphans` utility. This utility allows specifying the command path dynamically and is similar to using the `@Command` annotation but without the compile-time constraints.

#### [](#example-usage) Example Usage

1. **Implement the** `OrphanCommand` **Interface**: Create a class that implements `OrphanCommand` and define your command methods within it.
2. **Register the Orphan Command**: Use the `Orphans.path()` method to set the path for the orphan command dynamically.

### [](#example) Example

#### [](#step-1-implementing-an-orphancommand) Step 1: Implementing an `OrphanCommand`

Java

Kotlin

Copy

```
public class Foo implements OrphanCommand {

    @CommandPlaceholder 
    // ^^^
    // will get replaced with @Command("the path here")
    // at runtime
    public void onCommand(CommandActor actor) {
        // ...
    }

    @Subcommand("bar")
    public void bar(CommandActor actor) {
        actor.reply("Hello!");
    }
}
```

Copy

```
class Foo : OrphanCommand {
    
    @CommandPlaceholder 
    // ^^^
    // will get replaced with @Command("the path here")
    // at runtime
    fun onCommand(actor: CommandActor) {
        // ...
    }
    
    @Subcommand("bar")
    fun bar(actor: CommandActor) {
        actor.reply("Hello!")
    }
}
```

In this example, `Foo` is an orphan command class with a subcommand `bar`.

#### [](#step-2-registering-the-orphan-command) Step 2: Registering the Orphan Command

Java

Kotlin

Copy

```
var lamp = ...; // Initialize your Lamp instance

// Dynamically set the command path using Orphans.path()
lamp.register(Orphans.path(args[0]).handler(new Foo()));
```

Copy

```
val lamp = ... // Initialize your Lamp instance

// Dynamically set the command path using Orphans.path()
lamp.register(Orphans.path(args[0]).handler(Foo()))
```

Assuming `args[0]` is `"buzz"`, the above code will register a command with the path `"buzz"`.

**Example Command Execution**

Copy

```
> buzz bar
Hello!
```

#### [](#orphans.path-behavior) `Orphans.path()` Behavior

- `Orphans.path("foo", "bar")` is equivalent to `@Command("foo", "bar")`
- `Orphans.path("foo bar", "buzz boom")` is equivalent to `@Command("foo bar", "buzz boom")`

However, `Orphans.path()` can also accept strings that are not known at compile-time, such as values loaded from configuration files or provided by user input.

### [](#summary) Summary

The `OrphanCommand` interface and `Orphans` utility enable dynamic command path registration, allowing for greater flexibility and configurability in command definitions. This feature is especially useful when command paths need to be determined at runtime, bypassing the compile-time limitations of traditional annotations.

[PreviousAnnotation replacers](/lamp-docs/how-to/annotation-replacers)[NextException handling](/lamp-docs/how-to/exception-handling)

Last updated 1 year ago

Was this helpful?