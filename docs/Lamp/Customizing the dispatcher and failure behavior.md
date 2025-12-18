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

- [DispatcherSettings](#dispatchersettings)
- [Example](#example)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/customizing-the-dispatcher-and-failure-behavior.md)

1. [How-to](/lamp-docs/how-to)

# Customizing the dispatcher and failure behavior

Lamp's strategy for finding the best command is not fail-proof, and, because it relies on user input, it may not always be able to find the error cause spot on (simply put, it may not exist, or there may be many!)

Therefore, Lamp allows you to tweak the resolution strategy, and change the failure behavior. This is done using the `DispatcherSettings` class, which you can customize using `Lamp.Builder#dispatcherSettings()`.

## [](#dispatchersettings) DispatcherSettings

The `DispatcherSettings` class allows you to customize the following:

1. The maximum number of commands it should try
2. The failure behavior that receives all the failed attempts (`Potential`s) and the user input. This is done using the `FailureHandler` interface.

### [](#example) Example

Java

Kotlin

Copy

```
var builder = ...;
builder.dispatcherSettings(settings -> {
    settings.maximumFailedAttempts(10).failureHandler((actor, failedAttempts, input) -> {
        ...
    });
});
```

Copy

```
val builder = ...
builder.dispatcherSettings { settings ->
    settings.maximumFailedAttempts(10).failureHandler { actor, failedAttempts, input ->
    
    }
}
```

[PreviousVisitors](/lamp-docs/how-to/visitors)

Last updated 1 year ago

Was this helpful?