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

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/help-commands.md)

1. [How-to](/lamp-docs/how-to)

# Help commands

This page explains how to create help commands in Lamp

### [](#including-help-interfaces-in-command-methods) Including Help Interfaces in Command Methods

1. `Help.RelatedCommands`
   
   - **Purpose**: Use this interface to include all child and sibling commands. This is ideal for providing a complete help menu that encompasses all relevant commands at the same level or below.
   - **Usage**: Add `Help.RelatedCommands` as a parameter in your command method. This will provide the combined list of both child and sibling commands.

Java

Kotlin

Copy

```
private static final int ENTRIES_PER_PAGE = 7;

@Command("myplugin help")
public void sendHelpMenu(
	   BukkitCommandActor actor,
	   @Range(min = 1) @Default("1") int page,
	   Help.RelatedCommands<BukkitCommandActor> commands
) {
   var list = commands.paginate(page, ENTRIES_PER_PAGE);
   for (var command : list) {
      actor.reply("- " + command.usage());
   }
}
```

Copy

```
private const val ENTRIES_PER_PAGE = 7

@Command("myplugin help")
fun sendHelpMenu(
   actor: BukkitCommandActor,
   @Range(min = 1) @Optional page: Int = 1,
   commands: Help.RelatedCommands<BukkitCommandActor>
) {
   val list = commands.paginate(page, ENTRIES_PER_PAGE)
   for (command in list) {
	   actor.reply("- ${command.usage()}")
   }
}
```

1. `Help.ChildrenCommands`
   
   - **Purpose**: Use this interface to include all child commands associated with a specific command. This is useful for displaying commands that fall under a parent command, creating a hierarchical help menu.
   - **Usage**: Add `Help.ChildrenCommands` as a parameter in your command method. This will automatically provide the list of child commands for the current command.

Java

Kotlin

Copy

```
private static final int ENTRIES_PER_PAGE = 7;

@Command("myplugin help")
public void sendHelpMenu(
	   BukkitCommandActor actor,
	   @Range(min = 1) @Default("1") int page,
	   Help.ChildrenCommands<BukkitCommandActor> commands
) {
   var list = commands.paginate(page, ENTRIES_PER_PAGE);
   for (var command : list) {
      actor.reply("- " + command.usage());
   }
}
```

Copy

```
private const val ENTRIES_PER_PAGE = 7

@Command("myplugin help")
fun sendHelpMenu(
   actor: BukkitCommandActor,
   @Range(min = 1) @Optional page: Int = 1,
   commands: Help.ChildrenCommands<BukkitCommandActor>
) {
   val list = commands.paginate(page, ENTRIES_PER_PAGE)
   for (command in list) {
	   actor.reply("- ${command.usage()}")
   }
}
```

1. `Help.SiblingCommands`
   
   - **Purpose**: Use this interface to include all sibling commands that are on the same level as the current command. This is useful for showing related commands that are at the same command hierarchy level.
   - **Usage**: Add `Help.SiblingCommands` as a parameter in your command method. This will automatically provide the list of sibling commands for the current command.

Java

Kotlin

Copy

```
private static final int ENTRIES_PER_PAGE = 7;

@Command("myplugin help")
public void sendHelpMenu(
	   BukkitCommandActor actor,
	   @Range(min = 1) @Default("1") int page,
	   Help.SiblingCommands<BukkitCommandActor> commands
) {
   var list = commands.paginate(page, ENTRIES_PER_PAGE);
   for (var command : list) {
      actor.reply("- " + command.usage());
   }
}
```

Copy

```
private const val ENTRIES_PER_PAGE = 7

@Command("myplugin help")
fun sendHelpMenu(
   actor: BukkitCommandActor,
   @Range(min = 1) @Optional page: Int = 1,
   commands: Help.SiblingCommands<BukkitCommandActor>
) {
   val list = commands.paginate(page, ENTRIES_PER_PAGE)
   for (command in list) {
	   actor.reply("- ${command.usage()}")
   }
}
```

[PreviousCooldowns](/lamp-docs/how-to/cooldowns)[NextAnnotation replacers](/lamp-docs/how-to/annotation-replacers)

Last updated 1 year ago

Was this helpful?