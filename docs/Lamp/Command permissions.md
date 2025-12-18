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

- [Platform-Specific Command Permissions](#platform-specific-command-permissions)
- [Custom permission annotations](#custom-permission-annotations)
- [Example: Custom Command Permission in Bukkit](#example-custom-command-permission-in-bukkit)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/command-permissions.md)

1. [How-to](/lamp-docs/how-to)

# Command permissions

Command permissions allow you to restrict access to commands based on user permissions

The `CommandPermission` interface is used to define permissions required to execute a command. It is a functional interface that evaluates whether a command can be executed by a given `CommandActor`. This implementation may vary depending on the target platform.

#### [](#interface-methods) Interface Methods

- `boolean isExecutableBy(@NotNull A actor)`
  
  Determines whether the specified `actor` has permission to execute the command.

#### [](#factory-interface) Factory Interface

The `CommandPermission.Factory` interface allows you to create custom `CommandPermission` implementations based on annotations.

- `@Nullable CommandPermission<A> create(@NotNull AnnotationList annotations, @NotNull Lamp<A> lamp)`
  
  Creates a new `CommandPermission` based on the provided list of annotations and the `Lamp` instance. If the factory does not handle the given input, it may return `null`.

## [](#platform-specific-command-permissions) Platform-Specific Command Permissions

In addition to the generic `CommandPermission` interface, various platforms have their own specialized `@CommandPermission` annotations for handling permissions. This allows you to define and manage permissions specific to the platform you are working with.

## [](#custom-permission-annotations) Custom permission annotations

### [](#example-custom-command-permission-in-bukkit) Example: Custom Command Permission in Bukkit

#### [](#id-1.-create-a-custom-annotation) 1. Create a Custom Annotation

First, define a custom annotation to specify the required group:

Java

Kotlin

Copy

```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresGroup {
    String value();
}
```

Copy

```
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RequiresGroup(val value: String)
```

#### [](#id-2.-implement-a-custom-commandpermission.factory) 2. Implement a Custom CommandPermission.Factory

Next, create a `CommandPermission.Factory` that checks if the `CommandActor` has the required group:

Java

Kotlin

Copy

```
public class GroupPermissionFactory implements CommandPermission.Factory<BukkitCommandActor> {

    @Override
    @Nullable
    public CommandPermission<BukkitCommandActor> create(@NotNull AnnotationList annotations, @NotNull Lamp<BukkitCommandActor> lamp) {
        RequiresGroup requiresGroup = annotations.get(RequiresGroup.class);
        if (requiresGroup == null) return null;

        String requiredGroup = requiresGroup.value();

        return actor -> RankManager.getRank(actor.sender()).equals(requiredGroup);
    }
}
```

Copy

```
class GroupPermissionFactory : CommandPermission.Factory<BukkitCommandActor> {

    @Nullable
    override fun create(annotations: AnnotationList, lamp: Lamp<BukkitCommandActor>): CommandPermission<BukkitCommandActor>? {
        val requiresGroup = annotations.get(RequiresGroup::class.java) ?: return null
        val requiredGroup = requiresGroup.value

        return CommandPermission { actor ->
            RankManager.getRank(actor.sender) == requiredGroup
        }
    }
}
```

#### [](#id-3.-register-the-factory-with-lamp) 3. Register the Factory with Lamp

Register your custom `CommandPermission.Factory` with the `Lamp` instance:

Java

Kotlin

Copy

```
var lamp = BukkitLamp.builder(this)
    .permissionFactory(new GroupPermissionFactory())
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    .permissionFactory(GroupPermissionFactory())
    .build()
```

#### [](#id-4.-use-the-annotation-in-a-command) 4. Use the Annotation in a Command

Finally, use the `@RequiresGroup` annotation in your command method:

Java

Kotlin

Copy

```
public class MyCommands {

    @RequiresGroup("admin")
    @Command("admin command")
    public void adminCommand(BukkitCommandActor actor) {
        actor.reply("You are authorized to use this command!")
    }
}
```

Copy

```
class MyCommands {

    @RequiresGroup("admin")
    @Command("admin command")
    fun adminCommand(actor: BukkitCommandActor) {
        actor.reply("You are authorized to use this command!")
    }
}
```

In this setup:

- The `GroupPermissionFactory` checks if the sender’s rank matches the required group specified in the `@RequiresGroup` annotation.
- If the sender is authorized, they can execute the command; otherwise, they will be denied access.

[PreviousContext parameters](/lamp-docs/how-to/context-parameters)[NextParameter validators](/lamp-docs/how-to/parameter-validators)

Last updated 1 year ago

Was this helpful?