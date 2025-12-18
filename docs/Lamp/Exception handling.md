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

- [CommandExceptionHandler Interface](#commandexceptionhandler-interface)
- [Overview](#overview)
- [Example: Bukkit Platform](#example-bukkit-platform)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/exception-handling.md)

1. [How-to](/lamp-docs/how-to)

# Exception handling

This page explains how to use exception handlers, which allow you to catch and handle exceptions that occur during command execution, and modify the default behavior for each exception.

## [](#commandexceptionhandler-interface) CommandExceptionHandler Interface

The `CommandExceptionHandler` interface is a key component in Lamp that allows handling exceptions thrown during command execution. Each platform (e.g., Bukkit, Sponge, Velocity, JDA, etc.) has its own set of exceptions and a corresponding `DefaultExceptionHandler` extension tailored to handle those exceptions.

### [](#overview) Overview

#### [](#what-is-commandexceptionhandler) What is `CommandExceptionHandler`?

`CommandExceptionHandler` is a functional interface designed to handle any exceptions that may occur during command invocation. These exceptions can be standard Java exceptions or specific ones thrown by different components of the framework.

#### [](#default-exception-handlers) Default Exception Handlers

For each platform, there is a `DefaultExceptionHandler` extension that handles common exceptions relevant to that platform. Users should extend the respective platform's `DefaultExceptionHandler` to customize their exception handling.

For example:

- **Bukkit**: `BukkitExceptionHandler`
- **Sponge**: `SpongeExceptionHandler`
- **Velocity**: `VelocityExceptionHandler`
- **JDA**: `SlashJDAExceptionHandler`
- and so on

#### [](#how-to-use) How to Use

To create custom exception handling logic for your platform, follow these steps:

1. **Extend the DefaultExceptionHandler**: Extend the default exception handler class provided for your platform.
2. **Add Custom Exception Handling**: Use the `@HandleException` annotation to define methods that handle specific exceptions.
3. **Set the Exception Handler**: Register your custom exception handler with the Lamp builder.

### [](#example-bukkit-platform) Example: Bukkit Platform

Here’s an example of how to create a custom exception handler for the Bukkit platform.

#### [](#step-1-extend-bukkitexceptionhandler) Step 1: Extend `BukkitExceptionHandler`

Java

Kotlin

Copy

```
public class MyBukkitExceptionHandler extends BukkitExceptionHandler {

    @Override
    public void onInvalidPlayer(InvalidPlayerException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cInvalid player: &e" + e.input() + "&c."));
    }

    @HandleException
    public void onSomeCustomException(SomeCustomException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("..."));
    }
}
```

Copy

```
class MyBukkitExceptionHandler : BukkitExceptionHandler() {

    override fun onInvalidPlayer(e: InvalidPlayerException, actor: BukkitCommandActor) {
        actor.error(legacyColorize("&cInvalid player: &e${e.input()}&c."))
    }

    @HandleException
    fun onSomeCustomException(e: SomeCustomException, actor: BukkitCommandActor) {
        actor.error(legacyColorize("..."))
    }
}
```

In this example, `MyBukkitExceptionHandler` extends `BukkitExceptionHandler` and handles two exceptions: `InvalidPlayerException` and `NoPermissionException`.

#### [](#step-2-register-your-custom-handler) Step 2: Register Your Custom Handler

Java

Kotlin

Copy

```
var lamp = BukkitLamp.builder(this)
    .exceptionHandler(new MyBukkitExceptionHandler())
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    .exceptionHandler(MyBukkitExceptionHandler())
    .build()
```

The `exceptionHandler` method in the Lamp builder registers your custom exception handler with the framework.

[PreviousOrphan command](/lamp-docs/how-to/orphan-command)[NextHooks](/lamp-docs/how-to/hooks)

Last updated 1 year ago

Was this helpful?