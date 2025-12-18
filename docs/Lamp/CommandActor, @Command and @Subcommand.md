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

- [The CommandActor type](#the-commandactor-type)
- [The @Command and @Subcommand annotations](#the-command-and-subcommand-annotations)
- [@CommandPlaceholder](#commandplaceholder)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/introduction/commandactor-command-and-subcommand.md)

1. [Introduction](/lamp-docs/introduction)

# CommandActor, @Command and @Subcommand

This page will introduce you to the fundamentals of creating commands with Lamp

Building commands in Lamp is done **entirely** using annotations. Each command is represented as a **function** (method).

In this example, we will build a simple command, and then upgrade it gradually as we introduce more concepts.

## [](#the-commandactor-type) The CommandActor type

**CommandActor** is an interface that represents the sender of the command. It is platform-agnostic and provides generic functionality that is expected of any command sender.

**Every platform has its own subclass(es) of CommandActor:**

- Bukkit -&gt; BukkitCommandActor
- Bungee -&gt; BungeeCommandActor
- JDA -&gt; SlashCommandActor
- Sponge -&gt; SpongeCommandActor
- Velocity -&gt; VelocityCommandActor
- ...

In these examples, we will use the common `CommandActor` interface. You can use the platform subclass to access more platform-specific functionality.

> 💡 **For the curious**: Lamp does not enforce any specific CommandActor implementation, but provides a basic one for each platform. In further examples, we will build our own implementation of CommandActor and buff it with our own methods and functionality.

## [](#the-command-and-subcommand-annotations) The @Command and @Subcommand annotations

- **@Command** and **@Subcommand** are the two main annotations that we use to define commands.
  
  - @Command always defines the start of a new command. It is the main entry point to commands
  - @Subcommand defines a sub-command of a @Command. There cannot be a @Subcommand with no parent @Command.
- **Spaces in @Command and @Subcommand**
  
  - Spaces are respected in @Command and @Subcommand annotations. This means you can define complex commands easily and they would work as you expect
  - `@Command("foo bar")` -&gt; /foo bar
  - `@Command("foo bar") @Subcommand("buzz bam")` -&gt; /foo bar buzz bam
- **Combining @Command and @Subcommand**
  
  - `@Command` and `@Subcommand` can be defined on classes and inner classes to create a hierarchal tree of commands:
  - When any of @Command or @Subcommand defines multiple values, all possible combinations are taken
    
    - `@Command({"foo", "bar"}) @Subcommand({"joo", "kay"})` will generate:
      
      - /foo joo
      - /foo kay
      - /bar joo
      - /bar kay

Copy

```
@Command("game")
public class GameCommands {
    
    @Subcommand("test") // <--- /game test
    public void test(...) {}
    
    @Subcommand({"match", "arena"}) // <--- /game arena, /game match
    public static class Arena {
        
        @Subcommand("create") // <--- /game arena create, /game match create
        public void create(...) {}
        
    }
}
```

- **Argument names**
  
  - `@Command` and `@Subcommand` can define arguments that come in the middle of the command

Copy

```
    @Command("bank <player> add")
    public void addCoins(Player player, int coins) {
    }

    @Command("bank <player> reset")
    public void resetCoins(Player player) {
    }
```

- Any trailing arguments that are not defined inside the annotation will automatically be added to the end of the command.

## [](#commandplaceholder) @CommandPlaceholder

Besides `@Command` and `@Subcommand`, Lamp provides an auxiliary annotation `@CommandPlaceholder`. This has two main use cases:

1. For automatically inheriting the closest `@Command` and `@Subcommand` path of the declaring class.

Copy

```
@Command({"test", "foo bar", "buzz"})
public final class TestCommands {

    @CommandPlaceholder // <- equals @Command({"test", "foo bar", "buzz"})
    public void onTest() {
        // ...
    }
}
```

1. For [orphan commands](/lamp-docs/how-to/orphan-command), this will get replaced with the `@Command` of the path specified by the orphan command:

Copy

```
public final class TestCommands implements OrphanCommand {

    @CommandPlaceholder
    // ^^
    // will get replaced with a @Command("the path") at
    // runtime
    public void onTest() {

    }
}
```

[PreviousSetting up](/lamp-docs)[NextCreating your first command](/lamp-docs/introduction/creating-your-first-command)

Last updated 1 year ago

Was this helpful?