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

- [Creating a Quest type](#creating-a-quest-type)
- [Creating a QuestManager](#creating-a-questmanager)
- [Creating a QuestParameterType](#creating-a-questparametertype)
- [Adding tab completion to our Quest type](#adding-tab-completion-to-our-quest-type)
- [Registering our QuestParameterType to Lamp](#registering-our-questparametertype-to-lamp)
- [Creating our QuestCommands class](#creating-our-questcommands-class)
- [Using a ParameterType Factory](#using-a-parametertype-factory)

Was this helpful?

[Edit](https://github.com/Revxrsal/lamp-docs/blob/main/how-to/custom-parameter-types.md)

1. [How-to](/lamp-docs/how-to)

# Custom parameter types

This page will explain how you can create and resolve custom parameter types

One of the core features of Lamp is the ability to use custom parameter types for commands. This allows us to use types with specific meanings, restrict values to certain options, or provide customized tab completions.

We will illustrate this with a simple ***Quests*** plugin.

### [](#creating-a-quest-type) Creating a Quest type

Let's create a `Quest` class that contains all relevant data for a single `Quest`. Because this is slightly irrelevant to our end goal, we will use a relatively simple implementation.

Java

Kotlin

Copy

```
public record Quest(
        String id,
        String description
) {}
```

Copy

```
data class Quest(
    val id: String,
    val description: String
)
```

We will create a class that handles, stores, and retrieves all Quest objects. Let's call it `QuestManager`. It will contain basic functionality for creating, updating, querying and deleting quests

### [](#creating-a-questmanager) Creating a QuestManager

Java

Kotlin

Copy

```
public final class QuestManager {

    private final Map<String, Quest> quests = new HashMap<>();

    public boolean questExists(@NotNull String name) {
        return quests.containsKey(name);
    }

    public void add(@NotNull Quest quest) {
        if (questExists(quest.name()))
            throw new IllegalArgumentException("Quest with name '" + quest.name() + "' already exists!");
        quests.put(quest.name(), quest);
    }

    public Quest remove(@NotNull Quest quest) {
        return quests.remove(quest.name());
    }

    public void clearAllQuests() {
        quests.clear();
    }
    
    public Quest quest(@NotNull String name) {
        return quests.get(name);
    }
    
    public Map<String, Quest> quests() {
        return quests;
    }
}
```

Copy

```
class QuestManager {
    
    val quests = mutableMapOf<String, Quest>()

    fun questExists(name: String): Boolean {
        return quests.containsKey(name)
    }

    fun add(quest: Quest) {
        require(!questExists(quest.name)) { 
            "Quest with name '" + quest.name + "' already exists!" 
        }
        quests[quest.name] = quest
    }

    fun remove(quest: Quest): Quest? {
        return quests.remove(quest.name)
    }

    fun clearAllQuests() {
        quests.clear()
    }

    fun quest(name: String): Quest? {
        return quests[name]
    }
}
```

We will create a single instance of this QuestManager in our main class.

Java

Kotlin

Copy

```
public final class QuestsPlugin extends JavaPlugin {

+    private final QuestManager questManager = new QuestManager();

}
```

Copy

```
class QuestsPlugin : JavaPlugin() {
    
+    private val questManager = QuestManager()

}
```

Now, let's tell Lamp how to resolve a `Quest` parameter.

To create custom parameter types, we must implement the `ParameterType` interface. This interface describes how parameters are resolved and what suggestions they receive by default.

### [](#creating-a-questparametertype) Creating a QuestParameterType

Java

Kotlin

Copy

```
public final class QuestParameterType implements ParameterType<BukkitCommandActor, Quest> {

    @Override
    public Quest parse(@NotNull MutableStringStream input, @NotNull ExecutionContext<BukkitCommandActor> context) {
        /* Resolve a Quest here */
    }
}
```

Copy

```
class QuestParameterType : ParameterType<BukkitCommandActor, Quest> {
    
    override fun parse(input: MutableStringStream, context: ExecutionContext<BukkitCommandActor>): Quest {
        /* Resolve a Quest here */
    }
}
```

You may have noticed that `ParameterType` contains generics. In fact, it requires that you define two generics when you use it:

- `A`: A subclass of `CommandActor` that the ParameterType can work with. If we are creating a general ParameterType that works with *any* platform, we can have this as the `CommandActor` interface. If we, however, need a ParameterType that only works with Bukkit, for example, this would be `BukkitCommandActor` or any of its subclasses. *In other words, what is the most general CommandActor implementation we can work with?*
- `T`: The type of object we need to resolve. In this case, it is a `Quest` type. This is used by `#parse(...)` to dictate what types we are expected to return.

We would like to resolve our objects from our QuestManager. For this, let's create a constructor that receives a QuestManager:

Java

Kotlin

Copy

```
private final QuestManager questManager;

public QuestParameterType(QuestManager questManager) {
    this.questManager = questManager;
}
```

Copy

```
class QuestParameterType(private val questManager: QuestManager) /* ... */
```

Let's create a simple implementation of our `parse` function:

Java

Kotlin

Copy

```
@Override
public Quest parse(@NotNull MutableStringStream input, @NotNull ExecutionContext<BukkitCommandActor> context) {
    String name = input.readString();
    Quest quest = questManager.quest(name);
    if (quest == null)
        throw new CommandErrorException("No such quest: " + name);
    return quest;
}
```

Copy

```
override fun parse(input: MutableStringStream, context: ExecutionContext<BukkitCommandActor>): Quest {
    val name = input.readString()
    val quest = questManager.quest(name) 
        ?: throw CommandErrorException("No such quest: $name")
    return quest
}
```

Let's break down what we are doing here:

- `input.readString()`: This consumes a string from a MutableStringStream. You can think of MutableStringStream as a String that is tracked by a cursor that moves along that string. When we read something from it, we move the cursor forward and receive the value that the cursor passed over. `readString()` will consume an entire string token. This means that if the user gives a value enclosed by double-quotations, for example, `"Hello world"`, this will return `Hello world`. Otherwise, this will consume the next string until it finds a space. A `ParameterType` is free to consume as much of a MutableStringStream as it needs. The only requirement is that if it consumes a token, it must consume it *entirely*. It cannot consume part of a string, for example.
- `throw new CommandErrorException("No such quest: " + name)`: This will signal that the command execution failed, and tell the user that they supplied an invalid quest.

### [](#adding-tab-completion-to-our-quest-type) Adding tab completion to our Quest type

A nicety in ParameterType is that it allows us to define custom suggestions for our quest type. These can be supplied using the `defaultSuggestions` method:

Java

Kotlin

Copy

```
@Override public @NotNull SuggestionProvider<BukkitCommandActor> defaultSuggestions() {
    return (context) -> List.copyOf(questManager.quests().keySet());
}
```

Copy

```
override fun defaultSuggestions(): SuggestionProvider<BukkitCommandActor> {
    return SuggestionProvider { _ -> questManager.quests.keys }
}
```

### [](#registering-our-questparametertype-to-lamp) Registering our QuestParameterType to Lamp

Let's create our Lamp instance:

Java

Kotlin

Copy

```
@Override public void onEnable() {
    var lamp = BukkitLamp.builder(this)
        .parameterTypes(builder -> {
            builder.addParameterType(Quest.class, new QuestParameterType(questManager));
        })
        .build();
}
```

Copy

```
override fun onEnable() {
    val lamp = BukkitLamp.builder(this)
        .parameterTypes {
            it.addParameterType(Quest::class.java, QuestParameterType(questManager))
        }
        .build()
}
```

That's it! We can now use `Quest` in our commands to our heart's delight.

> 💡 You may have noticed that the `builder` provides `addXXX` and `addXXXLast`. Why the two variants?
> 
> When you use the `addXXXLast` variant, you are essentially giving your ParameterType less priority over others. When two ParameterTypes, one registered with `addXXX` while the other with `addXXXLast` conflict, the `addXXX` one will be used.
> 
> Using `addXXXLast` is very useful if you want to leave area for later overriding. Lamp uses it under the hood to provide all default ParameterTypes, which means you can override any of the default ones easily.

### [](#creating-our-questcommands-class) Creating our QuestCommands class

Let's create a simple QuestCommands class:

Java

Kotlin

Copy

```
public class QuestCommands {}
```

And register it:

Copy

```
lamp.register(new QuestCommands());
```

Copy

```
class QuestCommands
```

And register it:

Copy

```
lamp.register(QuestCommands());
```

We need to access this QuestManager from our command class. How are we going to do this?

We have multiple solutions:

- **Pass it to the constructor**: It is the traditional Java way of doing things. It involves no magic and no overhead. It, however, creates a tightly-coupled class that
- **Create a Lamp dependency**: This is the way Lamp encourages. It is a simple form of [dependency injection](https://stackoverflow.com/questions/130794/what-is-dependency-injection) that allows us to create loosely coupled code. In simpler terms, it says "*I want a QuestManager. I don't care where this QuestManager comes from. I just want it*"

We will go with the second way. It will keep our code clean and easy for future refactoring. Dependency injection also comes with many benefits, and this is not the place to discuss them. You can read up on the topic for more details.

To create a dependency, we must register it in our `Lamp` instance as follows:

Java

Kotlin

Copy

```
var lamp = BukkitLamp.builder(this)
    // ...
    .dependency(QuestManager.class, questManager)
    // ...
    .build();
```

Copy

```
val lamp = BukkitLamp.builder(this)
    // ...
    .dependency(QuestManager::class.java, questManager)
    // ...
    .build()
```

Now, to use it in our `QuestsCommand` class:

Java

Kotlin

Copy

```
public class QuestCommands {

    @Dependency
    private QuestManager questManager;

}
```

Copy

```
class QuestCommands {
 
    @Dependency
    private lateinit var questManager: QuestManager

}
```

That's it! We can now create our Quest commands easily. And we have a `QuestManager` at our disposal for all Quest-related operations.

Java

Kotlin

Copy

```
@Command("quest")
@CommandPermission("quests.command")
public class QuestCommands {

    @Dependency 
    private QuestManager questManager;

    @Subcommand("create")
    public void createQuest(BukkitCommandActor actor, String name, String description) {
        /*...*/
    }

    @Subcommand("delete")
    public void deleteQuest(BukkitCommandActor actor, Quest quest) {
        /*...*/
    }
    
    @Subcommand("start")
    public void startQuest(Player actor, Quest quest) {
        /*...*/
    }

    @Subcommand("clear")
    public void clearQuests(BukkitCommandActor actor) {
        /*...*/
    }
}
```

Copy

```
@Command("quest")
@CommandPermission("quests.command")
class QuestCommands {

    @Dependency
    private lateinit var questManager: QuestManager

    @Subcommand("create")
    fun createQuest(actor: BukkitCommandActor, name: String, description: String) {
        /*...*/
    }

    @Subcommand("delete")
    fun deleteQuest(actor: BukkitCommandActor, quest: Quest) {
        /*...*/
    }

    @Subcommand("start")
    fun startQuest(actor: Player, quest: Quest) {
        /*...*/
    }

    @Subcommand("clear")
    fun clearQuests(actor: BukkitCommandActor) {
        /*...*/
    }
}
```

### [](#using-a-parametertype-factory) Using a ParameterType Factory

`ParameterType.Factory` allows you to dynamically create `ParameterType` instances based on the type of parameter and annotations. This is particularly useful for complex parameter parsing scenarios. Below is an example demonstrating how to create a custom factory for handling enum types.

#### [](#example-enum-parameter-type-factory) Example: Enum Parameter Type Factory

This example shows how to implement a `ParameterType.Factory` that handles enum types. The factory converts a string input into an enum constant and provides suggestions based on enum names.

Java

Kotlin

Copy

```
public enum EnumParameterTypeFactory implements ParameterType.Factory<CommandActor> {
    INSTANCE;

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> ParameterType<CommandActor, T> create(@NotNull Type parameterType, @NotNull AnnotationList annotations, @NotNull Lamp<CommandActor> lamp) {
        Class<?> rawType = getRawType(parameterType);
        if (!rawType.isEnum())
            return null;
        Enum<?>[] enumConstants = (Enum<?>[]) rawType.getEnumConstants();
        Map<String, Enum<?>> byKeys = new HashMap<>();
        List<String> suggestions = new ArrayList<>();
        for (Enum<?> enumConstant : enumConstants) {
            String name = enumConstant.name().toLowerCase();
            byKeys.put(name, enumConstant);
            suggestions.add(name);
        }
        return new EnumParameterType(byKeys, suggestions);
    }

    private record EnumParameterType<E extends Enum<E>>(
            Map<String, E> byKeys,
            List<String> suggestions
    ) implements ParameterType<CommandActor, E> {

        @Override
        public E parse(@NotNull MutableStringStream input, @NotNull ExecutionContext<CommandActor> context) {
            String key = input.readUnquotedString();
            E value = byKeys.get(key.toLowerCase());
            if (value != null)
                return value;
            throw new EnumNotFoundException(key);
        }

        @Override
        public @NotNull SuggestionProvider<CommandActor> defaultSuggestions() {
            return SuggestionProvider.of(suggestions);
        }

        @Override
        public @NotNull PrioritySpec parsePriority() {
            return PrioritySpec.highest();
        }
    }
}
```

Copy

```
object EnumParameterTypeFactory : ParameterType.Factory<CommandActor> {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> create(parameterType: Type, annotations: AnnotationList, lamp: Lamp<CommandActor>): ParameterType<CommandActor, T>? {
        val rawType = getRawType(parameterType)
        if (!rawType.isEnum) return null
        val enumConstants = rawType.enumConstants as Array<Enum<*>>
        val byKeys = mutableMapOf<String, Enum<*>>()
        val suggestions = mutableListOf<String>()
        for (enumConstant in enumConstants) {
            val name = enumConstant.name.lowercase()
            byKeys[name] = enumConstant
            suggestions.add(name)
        }
        return EnumParameterType(byKeys, suggestions) as ParameterType<CommandActor, T>
    }

    private data class EnumParameterType<E : Enum<E>>(
        val byKeys: Map<String, E>,
        val suggestions: List<String>
    ) : ParameterType<CommandActor, E> {

        override fun parse(input: MutableStringStream, context: ExecutionContext<CommandActor>): E {
            val key = input.readUnquotedString()
            return byKeys[key.lowercase()] ?: throw EnumNotFoundException(key)
        }

        override fun defaultSuggestions(): SuggestionProvider<CommandActor> {
            return SuggestionProvider.of(suggestions)
        }

        override fun parsePriority(): PrioritySpec {
            return PrioritySpec.highest()
        }
    }
}
```

This example demonstrates how to create a `ParameterType.Factory` that can parse enums and provide suggestions based on the enum values.

Well done! In this tutorial, we have learned the following:

- How to create a custom parameter type
- How to provide default suggestions for a parameter type
- How to create and use dependencies

In the next tutorial, we will go through suggestions and auto-completion

[PreviousCreating variants of /teleport](/lamp-docs/how-to/creating-variants-of-teleport)[NextSuggestions and auto-completion](/lamp-docs/how-to/suggestions-and-auto-completion)

Last updated 1 year ago

Was this helpful?