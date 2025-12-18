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

- [The SuggestionProvider interface](#the-suggestionprovider-interface)
- [Static completions](#static-completions)
- [Type-specific completions](#type-specific-completions)
- [Annotation-specific completions](#annotation-specific-completions)
- [Suggestion provider factories](#suggestion-provider-factories)
- [Parameter-bound completions](#parameter-bound-completions)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/suggestions-and-auto-completion.md)

1. [How-to](/lamp-docs/how-to)

# Suggestions and auto-completion

This page explains how we can create static, dynamic and context-aware auto-completions

Auto-completions are a vital part of commands. They help users input valid values and save them a lot of typing.

Lamp provides a lot of helpful APIs for creating auto-completions. We will go through them in this page.

## [](#the-suggestionprovider-interface) The SuggestionProvider interface

This is the foundational building block that is responsible for handling suggestions and auto-completion. It is a basic functional interface that has access to the command actor and provided parameters (parsed) and returns a list of suggestions.

You will see `SuggestionProvider` in the following places:

- `SuggestionProviders`: An immutable registry that contains all registrations for `SuggestionProvider`s. It is maintained through `Lamp#suggestionProviders()` and can be constructed inside `Lamp.Builder#suggestionProviders()`.
- `SuggestionProvider.Factory`: An interface that can generate `SuggestionProvider`s dynamically for parameters. This factory can access the parameter type (and generics), its annotations, and other SuggestionProviders. It is a powerful interface as it can generate custom suggestions based on the type generics, a common interface, a specific data type (such as enums), or suggestions bound to a particular annotation.
- `ParameterType#defaultSuggestions()`: This is a method that can be overridden in subclasses of ParameterType. It allows parameter types to define a default `SuggestionProvider` that is used when no other suggestion provider is available.

Let's move on to the creation of custom suggestions

## [](#static-completions) Static completions

This is the simplest form of auto-completion: we have a static set of values that we would like to recommend to the user.

By static completions, we mean hard-coded, compile-time-defined constant completions. For that, we use the `@Suggest` annotation.

Java

Kotlin

Copy

```
@Command("coins give")
public void give(
    BukkitCommandActor actor,
    Player target,
    @Suggest({"100", "200", "300", "500", "1000"}) int coins
) {
    /* ... */
}
```

Copy

```
@Command("coins give")
fun give(
    actor: BukkitCommandActor,
    target: Player,
    @Suggest("100", "200", "300", "500", "1000") coins: Int
) {
    /* ... */
}
```

This will automatically supply the users with all the suggestions in `@Suggest`. A nicety of the `@Suggest` annotation is that suggestions are allowed to contain spaces. We can define suggestions that contain spaces easily and Lamp will handle the rest.

Java

Kotlin

Copy

```
@Command("teleport")
public void tp(
    BukkitCommandActor actor,
    Player target,
    @Suggest("~ ~ ~") Location location
) {
    /* ... */
}
```

Copy

```
@Command("teleport")
fun tp(
    actor: BukkitCommandActor,
    target: Player,
    @Suggest("~ ~ ~") location: Location
) {
    /* ... */
}
```

As you can see, there isn't much you can do with `@Suggest`. This is why we will need to introduce more complicated ways for creating suggestions.

## [](#type-specific-completions) Type-specific completions

It's common to have a certain set of completions bound to a particular Java class. Let's imagine we have a `World` parameter. We can create a `SuggestionProvider` that will retrieve the names of available worlds and give them back to us.

We can register this in our `Lamp.Builder`:

Java

Kotlin

Copy

```
var lamp = Lamp.builder()
    .suggestionProviders(providers -> {
        providers.addProvider(World.class, context -> {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .toList();
        });
    })
    .build();
```

Copy

```
val lamp = Lamp.builder<CommandActor>()
    .suggestionProviders { providers ->
        providers.addProvider(World::class.java) { _ ->
            Bukkit.getWorlds().map { it.name }
        }
    }
    .build()
```

Then, whenever we have a `World` parameter, we will automatically receive all World suggestions that are retrieved on demand.

Java

Kotlin

Copy

```
@Command("world")
public void gotoWorld(Player sender, World world) {
    /* ... */
}
```

Copy

```
@Command("world")
fun gotoWorld(sender: Player, world: World) {
    /* ... */
}
```

## [](#annotation-specific-completions) Annotation-specific completions

Lamp also provides a way to create auto-completions that are bound to certain annotations. This is a very flexible approach as it combines the benefits of dynamic parameters as well as annotations that allow you to tweak suggestions as needed.

Let's create an annotation named `@WithPermission("some.permission.node")`. This annotation will automatically give us all players that have a certain annotation node.

Let's define our annotation:

Java

Kotlin

Copy

```
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME) // <--- it's important!
public @interface WithPermission {
    
    String value();
    
}
```

Copy

```
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class WithPermission(val value: String)
```

> ⚠️ It's important to ensure that your annotation has **runtime retention**. This means that it should have `@Retention(RetentionPolicy.RUNTIME)` on it. Kotlin annotations have it by default.

Let's create our suggestion provider. We can register one with `SuggestionProviders.Builder#addProviderForAnnotation`, which constructs a `SuggestionProvider.Factory` under the hood.

Java

Kotlin

Copy

```
var lamp = Lamp.builder()
    .suggestionProviders(providers -> {
        providers.addProviderForAnnotation(WithPermission.class, withPermission -> {
            String permission = withPermission.value(); // <-- the value inside @WithPermission
            return context -> { // <-- here we return a SuggestionProvider
                return Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player -> player.hasPermission(permission))
                        .map(Player::getName)
                        .toList();
            };
        });
    })
    .build();
```

Copy

```
val lamp = Lamp.builder<CommandActor?>()
    .suggestionProviders { providers ->
        providers.addProviderForAnnotation(WithPermission::class.java) { withPermission: WithPermission ->
            val permission = withPermission.value
            SuggestionProvider { _ ->
                Bukkit.getOnlinePlayers().asSequence()
                    .filter { it.hasPermission(permission) }
                    .map { it.name }
                    .toList()
            }
        }
    }
    .build()
```

Now, we can use our `@WithPermission` annotation as needed.

Java

Kotlin

Copy

```
@Command("helpop")
public void helpOp(
    Player sender,
    @WithPermission("helpop.admin") Player op,
    String message
) {
    /* ... */
}
```

Copy

```
@Command("helpop")
fun helpOp(
    sender: Player,
    @WithPermission("helpop.admin") op: Player,
    message: String
) {
    /* ... */
}
```

> ⚠️ **Note**: While Lamp allows you to do everything in lambdas, it is actually discouraged as it can lead to terribly-looking code! For small lambdas, it's okay. But when things get big and messy, it's better to put them in a separate class.
> 
> Compare the above with the following, and decide for yourself:
> 
> Copy
> 
> ```
> public enum WithPermissionSuggestionFactory implements SuggestionProvider.Factory<CommandActor> {

    INSTANCE;

    @Override
    public @Nullable SuggestionProvider<CommandActor> create(
            @NotNull Type type,
            @NotNull AnnotationList annotations,
            @NotNull Lamp<CommandActor> lamp
    ) {
        WithPermission withPermission = annotations.get(WithPermission.class);
        if (withPermission == null)
            return null;
        String permission = withPermission.value();
        return context -> {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission(permission))
                    .map(Player::getName)
                    .toList();
        };
    }
}
> ```
> 
> Copy
> 
> ```
> var lamp = BukkitLamp.builder(this)
    .suggestionProviders(suggestions -> {
        suggestions.addProviderFactory(WithPermissionSuggestionFactory.INSTANCE);
    })
    .build();
> ```
> 
> A few extra lines, sure. But this will protect us from messy lambdas, and keep everything in its own separate place. Better organization and maintenance.

## [](#suggestion-provider-factories) Suggestion provider factories

So far, our suggestion providers have been very specific, either to a particular class or to a particular annotation. But, what if we want to generate `SuggestionProvider`s that work even beyond such scopes? This is where `SuggestionProvider.Factory` comes in. Here are some use-cases where it would come useful:

- Create annotation-bound SuggestionProviders that act differently depending on the parameter type or the annotations present on it
- Create SuggestionProviders for a certain class or its subclasses
- Create SuggestionProviders that respect generics (e.g. `List<String>` vs `List<Integer>`)
- Create SuggestionProviders made of other SuggestionProviders (e.g. `T[]` which uses the completions of `T`)
- Create SuggestionProviders that act on a certain type of classes, e.g. automatically generate suggestions for all `enum` types without having to explicitly register them.
- ...

Let's create a basic factory that will generate suggestions for all enum types. This will save us from creating individual SuggestionProviders.

Java

Kotlin

Copy

```
public enum EnumSuggestionProviderFactory implements SuggestionProvider.Factory<CommandActor> {
    
    INSTANCE;

    @Override
    public @Nullable SuggestionProvider<CommandActor> create(@NotNull Type type, @NotNull AnnotationList annotations, @NotNull Lamp<CommandActor> lamp) {
        return null;
    }
}
```

Copy

```
object EnumSuggestionProviderFactory : SuggestionProvider.Factory<CommandActor> {

    override fun create(
        type: Type,
        annotations: AnnotationList,
        lamp: Lamp<CommandActor>
    ): SuggestionProvider<CommandActor>? {
        return null
    }
}
```

> 💡 A SuggestionProvider.Factory should almost always work with well-defined constraints (in our case, enum types only). Because a SuggestionProvider.Factory has the potential to create suggestion providers for *anything*, we should be careful with what we return. **When a factory receives types/annotations that it does not care about (in our case, a non-enum type), it should return null!**

Let's check if our type is an enum, and if it is not, tell Lamp that we cannot deal with it:

Java

Kotlin

Copy

```
@Override public @Nullable SuggestionProvider<CommandActor> create(
    @NotNull Type type, 
    @NotNull AnnotationList annotations, 
    @NotNull Lamp<CommandActor> lamp
) {
    Class<?> rawType = Classes.getRawType(type);
    if (!rawType.isEnum())
        return null;
    return /* TODO() */
}
```

Copy

```
override fun create(
    type: Type,
    annotations: AnnotationList,
    lamp: Lamp<CommandActor>
): SuggestionProvider<CommandActor>? {
    val rawType = Classes.getRawType(type)
    if (!rawType.isEnum) 
        return null
    return TODO()
}
```

We made sure our factory only works on enums. Let's proceed to creating the suggestion provider.

1. We will cache the names of enums
2. We will create a static suggestion provider that returns them **in lowercase**.

Java

Kotlin

Copy

```
@Override public @Nullable SuggestionProvider<CommandActor> create(
    @NotNull Type type, 
    @NotNull AnnotationList annotations, 
    @NotNull Lamp<CommandActor> lamp
) {
    Class<?> rawType = Classes.getRawType(type);
    if (!rawType.isEnum())
        return null;
    Enum<?>[] enumValues = rawType.asSubclass(Enum.class).getEnumConstants();
    
    /* Create the list of suggestions */
    List<String> suggestions = new ArrayList<>();
    for (Enum<?> enumValue : enumValues) {
        /* Lower-case and add */
        suggestions.add(enumValue.name().toLowerCase());
    }
    
    /* Return a suggestion provider that always returns suggestions */
    return context -> suggestions;
}
```

Copy

```
override fun create(
    type: Type,
    annotations: AnnotationList,
    lamp: Lamp<CommandActor>
): SuggestionProvider<CommandActor>? {
    val rawType = Classes.getRawType(type)
    if (!rawType.isEnum) return null

    /* Map enums */
    val suggestions = rawType.enumConstants
        .map { (it as Enum<*>).name.lowercase() }

    /* Return a suggestion provider that always returns suggestions */
    return SuggestionProvider { _ -> suggestions }
}
```

That's it! We can simply register this to our `SuggestionProviders.Builder`, and automatically get suggestions for all enums generated. Effortless, efficient and easy!

Java

Kotlin

Copy

```
var lamp = Lamp.builder()
    .suggestionProviders(suggestions -> {
        suggestions.addProviderFactory(EnumSuggestionProviderFactory.INSTANCE);
    })
    .build()
```

Copy

```
val lamp = Lamp.builder()
    .suggestionProviders { suggestions ->
        suggestions.addProviderFactory(EnumSuggestionProviderFactory)
    }
    .build()
```

## [](#parameter-bound-completions) Parameter-bound completions

Finally, Lamp provides a utility annotation: `@SuggestWith` that allows you to specify either a SuggestionProvider or a SuggestionProvider.Factory for *individual* parameters. This is useful if we want a quick, dirty way of modifying suggestions for a particular parameter without creating individual annotations or wrapper types.

Let's imagine that we have this suggestion provider that automatically suggests all worlds that start with `world`.

Java

Kotlin

Copy

```
public final class DefaultWorlds implements SuggestionProvider<BukkitCommandActor> {
    
    @Override public @NotNull List<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.startsWith("world"))
                .toList();
    }
}
```

Copy

```
class DefaultWorlds : SuggestionProvider<BukkitCommandActor> {
    
    override fun getSuggestions(context: ExecutionContext<BukkitCommandActor>): List<String> {
        return Bukkit.getWorlds().stream()
            .map { obj: World -> obj.name }
            .filter { name: String -> name.startsWith("world") }
            .toList()
    }
}
```

Then, we can supply this class using `@SuggestWith`:

Java

Kotlin

Copy

```
@Command("world")
public void gotoWorld(
    Player sender,
    @SuggestWith(DefaultWorlds.class) World world
) {
    /* ... */
}
```

Copy

```
@Command("world")
fun gotoWorld(
    sender: Player, 
    @SuggestWith(DefaultWorlds::class) world: World
) {
    /* ... */
}
```

> ⚠️ Suggestion providers (or factories) supplied with `@SuggestWith` must provide a no-arg constructor, as Lamp will have to construct them reflectively.
> 
> In the future, Lamp will try different things (`instance`, `getInstance`, `INSTANCE`, singleton methods, etc.) for providing instances, but in the meantime, this is what you have to do.

[PreviousCustom parameter types](/lamp-docs/how-to/custom-parameter-types)[NextContext parameters](/lamp-docs/how-to/context-parameters)

Last updated 1 year ago

Was this helpful?