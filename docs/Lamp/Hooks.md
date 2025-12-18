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

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/hooks.md)

1. [How-to](/lamp-docs/how-to)

# Hooks

This page explains how to use hooks, which allow for injecting custom logic before or after command registration, unregistration, or execution.

In Lamp, hooks provide a powerful way to interact with the command lifecycle, allowing you to execute custom logic at various points before and after command operations. You can use hooks to perform actions or modify behavior related to command registration, unregistration, and execution.

#### [](#available-hooks) Available Hooks

There are three main types of hooks you can use:

1. **Command Registered Hook**: Invoked when a command is about to be registered.
   
   - **Interface**: `CommandRegisteredHook<A extends CommandActor>`
   - **Method**: `void onRegistered(@NotNull ExecutableCommand<A> command, @NotNull CancelHandle cancelHandle)`
2. **Command Unregistered Hook**: Invoked when a command is about to be unregistered.
   
   - **Interface**: `CommandUnregisteredHook<A extends CommandActor>`
   - **Method**: `void onUnregistered(@NotNull ExecutableCommand<A> command, @NotNull CancelHandle cancelHandle)`
3. **Command Executed Hook**: Invoked when a command is about to be executed.
   
   - **Interface**: `CommandExecutedHook<A extends CommandActor>`
   - **Method**: `void onExecuted(@NotNull ExecutableCommand<A> command, @NotNull ExecutionContext<A> context, @NotNull CancelHandle cancelHandle)`

#### [](#cancel-handle) Cancel Handle

Each hook method may receive a `CancelHandle` parameter which can be used to cancel the command operation if needed:

- **Interface**: `CancelHandle`
- **Methods**:
  
  - `boolean wasCancelled()`
  - `void cancel()`

#### [](#configuring-hooks) Configuring Hooks

Hooks are managed through an immutable registry. You can configure hooks using `Hooks.Builder`, which is available in `Lamp.Builder`. For example:

Copy

```
var lamp = Lamp.builder()
    .hooks(hooks -> {
        hooks.onCommandExecuted(new MyCommandExecutedHook());
        // Add other hooks as needed
    })
    .build();
```

#### [](#example-command-executed-hook) Example: Command Executed Hook

Here's an example of a `CommandExecutedHook` that prints a message to the console when a command is executed:

Java

Kotlin

Copy

```
public class MyCommandExecutedHook implements CommandExecutedHook<CommandActor> {

    @Override
    public void onExecuted(@NotNull ExecutableCommand<CommandActor> command, @NotNull ExecutionContext<CommandActor> context, @NotNull CancelHandle cancelHandle) {
        System.out.println("Command executed: " + command.path());
    }
}
```

Copy

```
class MyCommandExecutedHook : CommandExecutedHook<CommandActor> {

    override fun onExecuted(command: ExecutableCommand<CommandActor>, context: ExecutionContext<CommandActor>, cancelHandle: CancelHandle) {
        println("Command executed: ${command.path()}")
    }
}
```

This hook will output a message to the console every time a command is executed, allowing you to track or log command executions easily.

[PreviousException handling](/lamp-docs/how-to/exception-handling)[NextDependency injection](/lamp-docs/how-to/dependency-injection)

Last updated 1 year ago

Was this helpful?