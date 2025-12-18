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

- [Creating variants of /teleport](#creating-variants-of-teleport)
- [/teleport &lt;x&gt; &lt;y&gt; &lt;z&gt;](#teleport-less-than-x-greater-than-less-than-y-greater-than-less-than-z-greater-than)
- [/teleport &lt;target&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;](#teleport-less-than-target-greater-than-less-than-x-greater-than-less-than-y-greater-than-less-than-z)
- [/teleport &lt;target&gt; here](#teleport-less-than-target-greater-than-here)
- [/teleport &lt;to&gt;](#teleport-less-than-to-greater-than)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/creating-variants-of-teleport.md)

1. [How-to](/lamp-docs/how-to)

# Creating variants of /teleport

This page will explain how we can use Lamp to create multiple variants of the /teleport command

The /greet command we built in the last section was relatively simple, and enough to showcase basic command creation in Lamp.

In this section, however, we will build more complicated commands that simulate real-life cases. Tons of fun awaits us!

## [](#creating-variants-of-teleport) Creating variants of /teleport

The /teleport command in Minecraft is a good example of a multi-functional command. In these examples, we will build the following:

- `/teleport <x> <y> <z>`
- `/teleport <target> <x> <y> <z>`
- `/teleport <target> here`
- `/teleport <to target>`

We will also define them with the `/tp` as a shorter form.

We will start with implementing the core functionality. To keep things simple for now, we will not add fancy features like `~` and `^` (relative coordinates and angles). We will introduce them in later sections, however.

Let's start by creating a separate class for containing these commands:

Java

Kotlin

Copy

```
public class TeleportCommands {

}
```

Copy

```
class TeleportCommands {

}
```

And register it to our `Lamp` instance:

Java

Kotlin

Copy

```
public final class TestPlugin extends JavaPlugin {

    @Override public void onEnable() {
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new TeleportCommands());
    }
}
```

Copy

```
class TestPlugin : JavaPlugin() {

    override fun onEnable() {
        val lamp = BukkitLamp.builder(this).build()
        lamp.register(TeleportCommands())
    }
}
```

### [](#teleport-less-than-x-greater-than-less-than-y-greater-than-less-than-z-greater-than) `/teleport <x> <y> <z>`

This command should be easy to create. Let's define our function:

Java

Kotlin

Copy

```
public class TeleportCommands {

    @Command({"teleport", "tp"})
    public void teleport(Player sender, double x, double y, double z) {
        Location location = new Location(sender.getWorld(), x, y, z);
        sender.teleport(location);
    }
}
```

Copy

```
class TeleportCommands {
    
    @Command("teleport", "tp")
    fun teleport(sender: Player, x: Double, y: Double, z: Double) {
        val location = Location(sender.world, x, y, z)
        sender.teleport(location)
    }
}
```

Let's break this down:

- `@Command({"teleport", "tp"})`: This command defines our command as /teleport and /tp.
  
  - Any aliases defined
- `Player sender`: This is the argument that represents the player executing the command.
  
  - Because this is the first parameter in the method, Lamp will implicitly infer it as the command sender
  - Because it is a `Player`, any non-player entity attempting to execute this command will receive a `You must be a player to use this command!`-like error. This makes it easy to restrict certain commands to player senders only.
- `double x, double y, double z`: These are the arguments that our command will receive. Lamp will automatically parse the user input and parse it into `double`s, or emit errors if the user inputs an invalid value.

Let's try our command:

![](https://foxhut.gitbook.io/lamp-docs/~gitbook/image?url=https%3A%2F%2Fi.gyazo.com%2Fde2e8ea2e3249df6dab97ee3d1415d33.gif&width=768&dpr=4&quality=100&sign=665e386c&sv=2)

Executing /teleport

That's one variant down. Let's create another.

### [](#teleport-less-than-target-greater-than-less-than-x-greater-than-less-than-y-greater-than-less-than-z) `/teleport <target> <x> <y> <z>`

Java

Kotlin

Copy

```
@Command({"teleport", "tp"})
public void teleport(Player sender, EntitySelector<LivingEntity> target, double x, double y, double z) {
    Location location = new Location(sender.getWorld(), x, y, z);
    for (LivingEntity entity : target)
        entity.teleport(location);
}
```

Copy

```
@Command("teleport", "tp")
fun teleport(sender: Player, target: EntitySelector<LivingEntity>, x: Double, y: Double, z: Double) {
    val location = Location(sender.world, x, y, z)
    for (entity in target) 
        entity.teleport(location)
}
```

Note that our commands with similar signatures can co-exist peacefully with no problems.

![](https://foxhut.gitbook.io/lamp-docs/~gitbook/image?url=https%3A%2F%2F3830094576-files.gitbook.io%2F~%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Fspaces%252F1XJxI1qfaXuj9Pvzw9i7%252Fuploads%252FVgYBkk32KT4iZICytqcp%252Fimage.png%3Falt%3Dmedia%26token%3D264ecbdf-9100-426b-a91b-45bcdec4fd82&width=300&dpr=4&quality=100&sign=9d19fd4c&sv=2)

We can have as many variants of /teleport as we want, as long as Lamp can actually *differentiate* between them.

When there are multiple candidates for commands, Lamp will try to find the best one. This method is not foolproof and may go wrong in rare cases of real confusion.

At the end of the page, we will go through the criteria Lamp uses to decide the best execution candidate.

### [](#teleport-less-than-target-greater-than-here) `/teleport <target> here`

Now, we will implement `/teleport <target> here`. This command is slightly different from the ones above as it involves an argument in the middle of the command.

We noticed that, in previous commands, arguments would always come at the end of the command, in the same order they are defined. However, we can declare the order in the command annotations as needed. And, as expected, if a parameter is not defined in the command path, it will be put at the end of the command.

> 💡 To define an argument in the middle of the command, simply declare it in the command annotation, enclosed with `<>`. For example

Java

Kotlin

Copy

```
@Command("teleport <target> here")
public void teleportHere(Player sender, EntitySelector<LivingEntity> target) {
    for (LivingEntity entity : target)
        entity.teleport(sender);
}
```

Copy

```
@Command("teleport <target> here")
fun teleportHere(sender: Player, target: EntitySelector<LivingEntity>) {
    for (entity in target)
        entity.teleport(sender)
}
```

When Lamp encounters a name enclosed by `<>`, it will automatically infer it as a parameter name and look for a parameter with that name.

> ⚠️ **Important note**: You need to [enable parameter names](/lamp-docs#optional-preserve-parameter-names) for this, or define an `@Named` annotation on the method's parameters. Lamp will throw an exception if it cannot find the parameter.

![](https://foxhut.gitbook.io/lamp-docs/~gitbook/image?url=https%3A%2F%2F3830094576-files.gitbook.io%2F~%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Fspaces%252F1XJxI1qfaXuj9Pvzw9i7%252Fuploads%252FOwJ1EaP3ibzugVL7gQzU%252Fimage.png%3Falt%3Dmedia%26token%3D21706941-af93-475b-9498-865ef9ff99da&width=768&dpr=4&quality=100&sign=b4208b81&sv=2)

Using `/teleport <target> here` command

Let's create the simple `/teleport <to>`

### [](#teleport-less-than-to-greater-than) /teleport &lt;to&gt;

This one is relatively simple too:

Java

Kotlin

Copy

```
@Command({"teleport", "tp"})
public void teleport(Player sender, Entity target) {
    sender.teleport(target);
}
```

Copy

```
@Command("teleport", "tp")
fun teleport(sender: Player, target: Entity) {
    sender.teleport(target)
}
```

That's it! We can see how Lamp can work with a single command that has multiple variants, and how we can define arguments that come in different places of the command.

In the next

[PreviousCommand line](/lamp-docs/platforms/command-line)[NextCustom parameter types](/lamp-docs/how-to/custom-parameter-types)

Last updated 1 year ago

Was this helpful?