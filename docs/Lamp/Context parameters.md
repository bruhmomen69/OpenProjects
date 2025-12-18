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
- [ContextParameter Interface](#contextparameter-interface)
- [ContextParameter.Factory Interface](#contextparameter.factory-interface)
- [Example: PlayerWorldContextParameterFactory](#example-playerworldcontextparameterfactory)
- [Registering ContextParameter.Factory](#registering-contextparameter.factory)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/context-parameters.md)

1. [How-to](/lamp-docs/how-to)

# Context parameters

Context resolvers allow for dynamic resolution of context-specific values into command parameters, enabling commands to adapt based on the environment or actor state.

### [](#overview) Overview

`ContextParameter` is a functional interface that allows you to resolve parameters based on the context of the command execution. This can be useful for extracting complex types or values from the command context.

### [](#contextparameter-interface) ContextParameter Interface

The `ContextParameter` interface is used to define how a parameter should be resolved from the command context.

Copy

```
@FunctionalInterface
public interface ContextParameter<A extends CommandActor, T> {

    /**
     * Reads input from the given {@link MutableStringStream}, parses the object, or throws
     * exceptions if needed.
     *
     * @param parameter The parameter
     * @param context   The command execution context, as well as arguments that have been resolved
     * @return The parsed object. This should never be null.
     */
    T resolve(@NotNull CommandParameter parameter, @NotNull ExecutionContext<A> context);
}
```

### [](#contextparameter.factory-interface) ContextParameter.Factory Interface

The `ContextParameter.Factory` interface is used to create `ContextParameter` instances dynamically. It is helpful for creating custom parameter types.

Copy

```
@FunctionalInterface
public interface ContextParameter.Factory<A extends CommandActor> extends ParameterFactory {

    /**
     * Dynamically creates a {@link ContextParameter}
     *
     * @param <T>           The parameter type
     * @param parameterType The command parameter to create for
     * @param annotations   The parameter annotations
     * @param lamp          The Lamp instance (for referencing other parameter types)
     * @return The newly created {@link ContextParameter}, or {@code null} if this factory
     * cannot deal with it.
     */
    @Nullable
    <T> ContextParameter<A, T> create(@NotNull Type parameterType, @NotNull AnnotationList annotations, @NotNull Lamp<A> lamp);
}
```

### [](#example-playerworldcontextparameterfactory) Example: PlayerWorldContextParameterFactory

Here is an example of a `ContextParameter.Factory` that resolves a `World` from the `Player` context:

Java

Kotlin

Copy

```
public class PlayerWorldContextParameterFactory implements ContextParameter.Factory<BukkitCommandActor> {

    @Nullable
    @Override
    public <T> ContextParameter<BukkitCommandActor, T> create(@NotNull Type parameterType, @NotNull AnnotationList annotations, @NotNull Lamp<BukkitCommandActor> lamp) {
        if (parameterType != World.class) {
            return null;
        }
        PlayerWorld playerWorldAnnotation = annotations.get(PlayerWorld.class);
        if (playerWorldAnnotation == null) {
            return null;
        }
        return (parameter, context) -> context.actor().sender().requirePlayer().getWorld();
    }
}
```

Copy

```
object PlayerWorldContextParameterFactory : ContextParameter.Factory<BukkitCommandActor> {

    @Suppress("UNCHECKED_CAST")
    override fun <T> create(parameterType: Type, annotations: AnnotationList, lamp: Lamp<BukkitCommandActor>): ContextParameter<BukkitCommandActor, T>? {
        if (parameterType != World::class.java) {
            return null
        }
        val playerWorldAnnotation = annotations.get(PlayerWorld::class.java)
        if (playerWorldAnnotation == null) {
            return null
        }
        return ContextParameter { _, context -> context.actor().sender().requirePlayer().world } as ContextParameter<BukkitCommandActor, T>
    }
}
```

### [](#registering-contextparameter.factory) Registering ContextParameter.Factory

You can register a `ContextParameter.Factory` with the `Lamp` builder to handle custom parameter types.

Java

Kotlin

Copy

```
var lamp = BukkitLamp.builder(this)
    .parameterTypes(parameters -> {
        parameters.addContextParameterFactory(new PlayerWorldContextParameterFactory());
    })
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    .parameterTypes { parameters ->
        parameters.addContextParameterFactory(PlayerWorldContextParameterFactory())
    }
    .build()
```

[PreviousSuggestions and auto-completion](/lamp-docs/how-to/suggestions-and-auto-completion)[NextCommand permissions](/lamp-docs/how-to/command-permissions)

Last updated 1 year ago

Was this helpful?