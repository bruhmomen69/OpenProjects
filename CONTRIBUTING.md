
# Code Structure
- You are in a monorepo with core utils, and the `ZealousChat` and `RegionRestore` plugins.
- The `ZealousChat` plugin is a chat plugin that uses MiniMessage for chat formatting.
- The `RegionRestore` plugin is a region restore plugin that uses MiniMessage for chat formatting.
- The `EssentiallyStateless` plugin is a essentials plugin that does not store user state.
- The `AuctionHouse` plugin is an Auctions and Orders plugin.
- RegionRestore uses a multi module structure, with `api`, `PaperMC` and `plugin` (packaging) modules, along with NMS modules.

# Working
Always update the README.md file inside the module you are working on with the current state of the project. If your module does not have a readme, then a parent project probably will, so use that instead.

# Tech Stack
- Use Kotlin for all code.
- Use Configurate (https://github.com/SpongePowered/Configurate) for configuration.
- Use Lamp (https://github.com/Revxrsal/Lamp) for commands.
- Use Slf4j for logging.

# Overall Common Kyori Adventure dependencies (you do not probably need all of these, and should usually change the scope of these)
- implementation("net.kyori:adventure-api:4.23.0")
- implementation("net.kyori:adventure-text-serializer-legacy:4.23.0")
- implementation("net.kyori:adventure-text-serializer-gson:4.23.0")
- implementation("net.kyori:adventure-text-minimessage:4.23.0")

# Lamp Dependency Info:

<dependencies>
  <!-- Required for all platforms -->
  <dependency>
      <groupId>io.github.revxrsal</groupId>
      <artifactId>lamp.common</artifactId> 
      <version>[VERSION]</version>
  </dependency>

  <!-- Add your specific platform module here -->
  <dependency>
      <groupId>io.github.revxrsal</groupId>
      <artifactId>lamp.[PLATFORM]</artifactId>
      <version>[VERSION]</version>
  </dependency>  
</dependencies>
Latest version: `4.0.0-rc.12`

Where [PLATFORM] is any of the following:

bukkit: Contains integrations for the Bukkit platform.

bungee: Contains integrations for the BungeeCord API

brigadier: Contains integrations for Mojang's Brigadier API

sponge: Contains integrations for the Sponge platform (version 8+)

velocity: Contains integrations for the VelocityPowered API

minestom: Contains integrations for the Minestom platform

fabric: Contains integrations for the Fabric modding API

jda: Contains integrations for the Java Discord API

cli: A minimal implementation of the Lamp APIs for command-line applications

The dependencies are available on Maven Central.

### Lamp Examples:

```java
public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Lamp<BukkitCommandActor> lamp = BukkitLamp.builder(this).build();
        lamp.register(new MyCommand());
    }

    public class MyCommand {

        @Command("hello")
        @CommandPermission("myplugin.hello")
        public void hello(BukkitCommandActor actor) {
            // Command logic here
        }
    }
}
```

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0")
public class MyPlugin {

    @Listener
    private void onConstructPlugin(ConstructPluginEvent event) {
        Lamp<SpongeCommandActor> lamp = SpongeLamp.builder(this).build();
        lamp.register(new MyCommand());
    }

    public class MyCommand {

        @Command("hello")
        @CommandPermission("myplugin.hello")
        public void hello(SpongeCommandActor actor) {
            // Command logic here
        }
    }
}
```


```java

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

# MiniMessage Placeholder Syntax
Dynamic Replacements
MiniMessage has some included TagResolver s which can replace tags dynamically when parsing those. Those resolvers can replace a tag with dynamic input such as a string or a formatted number.

Placeholders
Placeholders replace the tag with a specific text. Those are the most basic replacements:

Insert a component
You can simply insert a component for the tag with the component placeholder.

MiniMessage.miniMessage().deserialize("<gray>Hello <name> :)", Placeholder.component("name", Component.text("TEST", NamedTextColor.RED)));
This will insert the red text component “TEST” for the tag name.

Insert some unparsed text
Sometimes it’s better to not parse dynamic text such as user inputs. For those things MiniMessage provides the unparsed placeholder. With this method you can sanitize user input without escaping the tags directly.

MiniMessage.miniMessage().deserialize("<gray>Hello <name>", Placeholder.unparsed("name", "<red>TEST :)"));
This will insert the text without parsing. The result will be a gray text with Hello <red>TEST :).

Insert and parse text
When you want to insert a text and allow MiniMessage to parse the tags you can use the parsed placeholder. The parsed placeholder will insert the replacement before parsing the string. The tags in the placeholder can affect the parsed result after the placeholder.

MiniMessage.miniMessage().deserialize("<gray>Hello <name> :)", Placeholder.parsed("name", "<red>TEST"));
// returns Component.text("Hello ", NamedTextColor.GRAY).append(Component.text("TEST :)", NamedTextColor.RED));
This will insert and parse the text.

Insert a style
When you want to create your own styling tag you can use the styling placeholder.

MiniMessage.miniMessage().deserialize("<my-style>Hello :)</my-style> How are you?",
Placeholder.styling("my-style", ClickEvent.suggestCommand("/say hello"), NamedTextColor.RED, TextDecoration.BOLD));
// will apply a click even, a red text color and bold decoration to the text
This will insert the style with a click event and a red text. Styling placeholders can be used for any style, e.g. colors, text decoration and events.

Create your own styling tags:

Placeholder.styling("fancy", TextColor.color(150, 200, 150)); // will replace the color between "<fancy>" and "</fancy>"
Placeholder.styling("myhover", HoverEvent.showText(Component.text("test"))); // will display your custom text as hover
Placeholder.styling("mycmd", ClickEvent.runCommand("/mycmd is cool")); // will create a clickable text which will run your specified command.
Tip

Styling placeholders can be used to sanitize input from players in click events. Instead of using a parsed placeholder the string can be used directly.

Formatters
Not everything is a text, sometimes its useful to display a number or a date. For that you can use the provided Formatters from MiniMessage

Insert a number
You can insert a Number by using the number formatter in MiniMessage.

To specify the locale and format of the number the formatter accepts optionally tag arguments. You can specify the locale and the number format. It’s possible to pass both as arguments to the tag but you have provide the locale first.

MiniMessage.miniMessage().deserialize("<gray>Hello my number <no>!", Formatter.number("no", 250.25d));
MiniMessage.miniMessage().deserialize("<gray>Hello my number <no:'#.00'>!", Formatter.number("no", 250.25d));
MiniMessage.miniMessage().deserialize("<gray>Hello my number <no:'de-DE':'#.00'>!", Formatter.number("no", 250.25d));
MiniMessage.miniMessage().deserialize("<gray>Hello my number <no:'de-DE'>!", Formatter.number("no", 250.25d));
All those examples are valid and will insert the number as the tag.

Refer to Locale and DecimalFormat for valid locale tags and usable patterns.

Tip

You can change the style such as the color by a more complex pattern:

MiniMessage.miniMessage().deserialize("<gray>Your current balance is <no:'en-EN':'<green>#.00;<red>-#.00'>.", Formatter.number("no", 250.25d));
This will display the balance in red for negative numbers, otherwise the number will be green.

Insert a date
To insert an instance of an TemporalAccessor such as a LocalDateTime you can use the date formatter.

The tag resolver requires a tag argument for the format. Refer to DateTimeFormatter for a usable patterns.

MiniMessage.miniMessage().deserialize("<gray>Current date is: <date:'yyyy-MM-dd HH:mm:ss'>!", Formatter.date("date", LocalDateTime.now(ZoneId.systemDefault()));
This will display the current date with the specified format. E.g. as 2022-05-27 11:30:25.

Insert a choice
To insert a number and format some text based on the number you can use the choice formatter.

This will accept a ChoiceFormat pattern.

MiniMessage.miniMessage().deserialize("<gray>I met <choice:'0#no developer|1#one developer|1<many developers'>!", Formatter.choice("choice", 5));
This will format your input based on the provided ChoiceFormat. In this case it will be I met many developers!

Complex placeholders
You can simply create your own placeholders. Take a look at the Formatter and Placeholder class from MiniMessage for examples.

Examples
Create a custom tag which makes its contents clickable:

TagResolver.resolver("click-by-version", (args, context) -> {
final String version = args.popOr("version expected").value();
return Tag.styling(ClickEvent.openUrl("https://jd.advntr.dev/api/ " + version + "/"));
});
// creates a tag to get javadocs of adventure by the version: <click-by-version:'4.14.0'>
You can create your own complex placeholders with multiple arguments and their own logic.

# Run test server

To run the test server, use the following command:
```
timeout 30s ./gradlew :PaperMC:runServer || echo "Server test completed (timeout is expected)"
```

The server will automatically shut down after 30 seconds, which is expected behavior for testing.

To run tests:
```
./gradlew :PaperMC:test
```

