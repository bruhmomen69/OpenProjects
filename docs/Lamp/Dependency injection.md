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

- [Adding Dependencies](#adding-dependencies)
- [Injecting Dependencies](#injecting-dependencies)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/dependency-injection.md)

1. [How-to](/lamp-docs/how-to)

# Dependency injection

This page explains how to use dependency injection, which provides a way to inject dependencies (instances of objects) into command classes

Lamp supports a simple form of dependency injection, allowing you to manage dependencies with ease. This feature simplifies the process of injecting dependencies into your command classes.

### [](#adding-dependencies) Adding Dependencies

Dependencies are added to the `Lamp` instance using the `builder.dependency(...)` method. You can provide dependencies as either a supplier or an object.

Java

Kotlin

Copy

```
Lamp lamp = Lamp.builder()
    .dependency(questManager) // Adding a constant dependency
    .build();
```

Copy

```
val lamp = Lamp.builder()
    .dependency(questManager)
    .build()
```

### [](#injecting-dependencies) Injecting Dependencies

To inject dependencies into fields, use the `@Dependency` annotation. This annotation tells the framework to inject the specified dependency into the annotated field.

Java

Kotlin

Copy

```
@CommandPermission("quests.command")
public class QuestCommands {

    @Dependency
    private QuestManager questManager; // Dependency will be injected here

    @Command("quest create")
    public void createQuest(CommandSender sender, String name, String description) {
        // Use questManager to create a quest
    }
    
    ...
}
```

Copy

```
@CommandPermission("quests.command")
class QuestCommands {

    @Dependency
    private lateinit var questManager: QuestManager // Dependency will be injected here

    @Command("quest create")
    fun createQuest(sender: CommandSender, name: String, description: String) {
        // Use questManager to create a quest
    }
    
    ...
}
```

[PreviousHooks](/lamp-docs/how-to/hooks)[NextVisitors](/lamp-docs/how-to/visitors)

Last updated 1 year ago

Was this helpful?