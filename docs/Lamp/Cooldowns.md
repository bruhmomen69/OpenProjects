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

- [Simple, fixed cooldowns](#simple-fixed-cooldowns)
- [Cooldown handles](#cooldown-handles)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/cooldowns.md)

1. [How-to](/lamp-docs/how-to)

# Cooldowns

This page explains how to create cooldowns for commands

Cooldowns are important in many sensitive operations that cannot afford to be done many times in a short period of time. Therefore, Lamp provides a neat way to integrate cooldowns

### [](#simple-fixed-cooldowns) Simple, fixed cooldowns

The most straightforward way to declare cooldowns is using the `@Cooldown`annotation:

Java

Kotlin

Copy

```
@Command("foo")
@Cooldown(value = 3, unit = TimeUnit.SECONDS)
public void foo(CommandActor actor) {
    // ...
}
```

Copy

```
@Command("foo")
@Cooldown(value = 3, unit = TimeUnit.SECONDS)
fun foo(actor: CommandActor) {
    // ...
}
```

This will put the user on cooldown if the command successfully executes without any error (if the command body throws any exception, the user will not be put on cooldown).

This method is sufficient for simple use cases. However, more sophisticated use cases may need something a bit more granular.

### [](#cooldown-handles) Cooldown handles

Lamp provides a convenient way to control cooldowns, the `CooldownHandle`interface. This interface is passed as a parameter to the command method, and it provides finer control over the cooldown.

A cooldown handle provides the following functions:

- `isOnCooldown()`: Check if the user is currently on cooldown.
- `elapsedMillis()`: Get the elapsed time since the cooldown started in milliseconds.
- `cooldown()`: Puts the user on cooldown
- `requireNotOnCooldown()`: Checks if the user is on cooldown, and if they are, throws a `CooldownException`(which is handled by Lamp automatically)
- `removeCooldown()` : Removes the cooldown
- `remainingTime(TimeUnit)`: Gets the time remaining and converts it to the given unit

**Note**: Some of the above functions provide two overloads: `f(...)`and `f(..., long, TimeUnit)`. The first overload can **only** be used if you have `@Cooldown`on the handle parameter (or received a handle from `#withCooldown()`, otherwise you must provide the cooldown value in the second overload. Attempting to use the first variant will throw an exception.

#### [](#usage) Usage

It can be used in two ways:

1. Combined with `@Cooldown`
2. Without `@Cooldown`

#### [](#with-cooldown) With `@Cooldown`:

This is done by declaring the `CooldownHandle` parameter with `@Cooldown`on it:

Java

Kotlin

Copy

```
@Command("foo")
public void foo(
    CommandActor actor,
    @Cooldown(value = 3, unit = TimeUnit.SECONDS) CooldownHandle handle
) {
    // ...
}
```

Copy

```
@Command("foo")
fun foo(
    actor: CommandActor,
    @Cooldown(value = 3, unit = TimeUnit.SECONDS) handle: CooldownHandle
) {
    // ...
}
```

Note that this will **not affect the behavior of the command by itself**! To actually put the user on cooldown, you have to use the handle:

Java

Kotlin

Copy

```
@Command("foo")
public void foo(
    CommandActor actor,
    @Cooldown(value = 3, unit = TimeUnit.SECONDS) CooldownHandle handle
) {
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }
    
    // ...
    
    // put the user on cooldown:
    handle.cooldown();
}
```

Copy

```
@Command("foo")
fun foo(
    actor: CommandActor,
    @Cooldown(value = 3, unit = TimeUnit.SECONDS) handle: CooldownHandle
) {
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }

    // ...

    // put the user on cooldown:
    handle.cooldown()
}
```

A clear drawback of this method is that it requires the cooldown value to be known at compile-time. To counter this, it is possible to use `CooldownHandle`s without `@Cooldown`:

Java

Kotlin

Copy

```
@Command("foo")
public void foo(
    CommandActor actor,
    CooldownHandle handle
) {
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }

    // ...

    // put the user on cooldown:
    handle.cooldown(10, TimeUnit.SECONDS);
}
```

Copy

```
@Command("foo")
fun foo(
    actor: CommandActor,
    handle: CooldownHandle
) {
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }

    // ...

    // put the user on cooldown:
    handle.cooldown(10, TimeUnit.SECONDS)
}
```

Attempting to invoke `#cooldown()`(without parameters) will throw an exception.

To save you from repeating yourself a lot, you can also do `handle.withCooldown()`so that you can use `#cooldown()`and other methods without having to repeat the cooldown value:

Java

Kotlin

Copy

```
@Command("foo")
public void foo(
    CommandActor actor,
    CooldownHandle handle
) {
    handle = handle.withCooldown(10, TimeUnit.SECONDS);
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }

    // ...

    // put the user on cooldown:
    handle.cooldown();
}
```

Copy

```
@Command("foo")
fun foo(
    actor: CommandActor,
    handle: CooldownHandle
) {
    handle = handle.withCooldown(10, TimeUnit.SECONDS)
    if (handle.isOnCooldown()) {
        // user is on cooldown
    }

    // ...

    // put the user on cooldown:
    handle.cooldown()
}
```

This last method combines the benefits of all the above methods, albeit requires manual management. Pick the one that fits your use case!

[PreviousResponse handlers](/lamp-docs/how-to/response-handlers)[NextHelp commands](/lamp-docs/how-to/help-commands)

Last updated 9 months ago

Was this helpful?