# MiniMessage Chat Plugin
**A production-ready, highly configurable chat formatting plugin for PaperMC powered by Kyori Adventure's MiniMessage**

## 🎯 **Plugin Overview**
This plugin provides comprehensive chat formatting capabilities with rank-based formats, interactive elements, and extensive customization options. Built with modern Paper API and MiniMessage for maximum compatibility and performance.

## 🚀 **Key Features**

### 💬 **Advanced Chat Formatting**
- **Rank-Based Chat Formats** with priority system (owner → admin → moderator → helper → vip → premium → donor → member → default)
- **World-Specific Formats** for different worlds/gamemodes (overworld, nether, end, creative, survival)
- **Permission-Based Format Selection** with configurable priority levels
- **Individual Feature Toggles** for colors, formatting, URLs, mentions, and cooldowns

### 🎨 **Interactive Chat Elements**
- **Custom Hover Messages** showing player rank/info (admin-configurable only)
- **Custom Click Actions** (suggest commands, open URLs, etc.) (admin-configurable only)
- **Player Input Security** - players cannot inject custom hover/click elements
- **URL Auto-Linking** with clickable links and hover previews
- **Player Mentions** with click-to-message functionality (@username)

### ⚙️ **Comprehensive Configuration**
- **Individual Message Type Controls**:
  - ✅ Chat message formatting (toggleable)
  - ✅ Join/Leave messages (toggleable)
  - ✅ Death messages (toggleable)
  - ✅ Advancement messages (toggleable)
- **Built-in Placeholder System** with 15+ placeholders (player info, server info, time/date)
- **Custom Placeholder Support** for server-specific values
- **PlaceholderAPI Integration Ready** for external plugin compatibility

### 🛡️ **Security & Permissions**
- **Granular Permission System** - every command requires specific permissions
- **Input Sanitization** using MiniMessage's TagResolver system
- **Chat Cooldowns** with bypass permissions for staff
- **Permission-Based Feature Access** (colors, formatting, URLs, mentions)

### 📨 **Private Messaging System**
- **Full private messaging** with `/msg`, `/message`, `/tell`, `/whisper`, `/w` commands
- **Reply system** with `/reply`, `/r` commands for quick responses
- **Configurable message formats** for sender and recipient with MiniMessage support
- **Message cooldowns** with bypass permissions for staff
- **Message logging** for moderation and audit purposes

### 🔄 **Chat Toggle System**
- **Independent toggles** for public chat and private messages
- **Player commands**: `/chatplugin toggle chat`, `/chatplugin toggle messages`
- **Persistent state** across server restarts and reconnections
- **Staff bypass permissions** for moderation purposes
- **Configurable linking** - optionally link chat and message toggles

### 👁️ **Social Spy & Moderation**
- **Social spy system** for moderators to monitor private messages
- **Command spy** (optional) to monitor player commands
- **Toggle command**: `/chatplugin toggle socialspy`
- **Ignore moderator messages** option for privacy
- **Console logging** for audit and compliance

### 🔌 **PlaceholderAPI Integration**
- **Full PlaceholderAPI support** with automatic detection
- **Bridge to MiniMessage** using TagResolver system
- **External plugin placeholders** (e.g., `%vault_rank%`, `%luckperms_prefix%`)
- **Graceful fallbacks** when PlaceholderAPI unavailable
- **Performance optimized** with timeout protection

### 🎮 **Player Commands**
- `/msg <player> <message>` - Send private message (aliases: `/message`, `/tell`, `/whisper`, `/w`)
- `/reply <message>` - Reply to last sender (alias: `/r`)
- `/chatplugin toggle chat` - Toggle public chat on/off
- `/chatplugin toggle messages` - Toggle private messages on/off
- `/chatplugin toggle socialspy` - Toggle social spy (moderators only)
- `/chatplugin status` - View current chat status

### 🛠️ **Admin Commands** (All Permission-Protected)
- `/chatplugin reload` - Reload configuration (`chatplugin.admin.reload`)
- `/chatplugin info` - View plugin status (`chatplugin.admin.info`)
- `/chatplugin test <message>` - Test formatting (`chatplugin.admin.test`)
- `/chatplugin format set default/group/world <format>` - Configure formats (`chatplugin.admin.format`)
- `/chatplugin format list` - List all formats (`chatplugin.admin.format`)
- `/chatplugin toggle colors/formatting/mentions/cooldown` - Toggle features (`chatplugin.admin.toggle`)
- `/chatplugin admin toggle chat/messages/all <player> <true/false>` - Force toggle player settings
- `/chatplugin admin socialspy <player> <true/false>` - Manage social spy access
- `/chatplugin admin stats` - View system statistics and active users
- `/chatplugin admin clear <toggles/socialspy/cooldowns/all>` - Clear various data types

### 🔧 **Technical Excellence**
- **Modern Paper API** usage (AsyncChatEvent, Adventure Components)
- **Proper MiniMessage Integration** with TagResolver system
- **HOCON Configuration** with automatic generation and validation
- **Comprehensive Error Handling** and logging with SLF4J
- **Performance Optimized** with efficient placeholder processing

# Project State:
**✅ PRODUCTION READY** - Fully featured, tested, and building successfully.

## ✅ Completed Features:
- **Comprehensive Configuration System** with HOCON support
- **Rank-Based Chat Formats** with priority system (owner, admin, moderator, helper, vip, premium, donor, member, default)
- **World-Specific Chat Formats** for different worlds/gamemodes
- **Permission-Based Format Selection** with multiple priority levels
- **Interactive Chat Elements**:
  - Hover messages showing player rank/info
  - Click actions (suggest commands, open URLs, etc.)
- **Advanced MiniMessage Support**:
  - Full color support including gradients and hex colors
  - Text formatting (bold, italic, underline, strikethrough, obfuscated)
  - Click events and hover events
  - URL auto-linking with click actions
  - Player mentions with click-to-message
- **Chat Features**:
  - Configurable chat cooldowns with bypass permissions
  - Join/Leave message customization
  - Death message customization
  - Chat logging
  - Permission-based feature access
- **Private Messaging System** with full MiniMessage support:
  - `/msg`, `/message`, `/tell`, `/whisper`, `/w` - Send private messages
  - `/reply`, `/r` - Reply to last sender
  - Configurable sender/recipient message formats
  - Message cooldowns with bypass permissions
  - Message logging for moderation
- **Chat Toggle System** for player control:
  - `/chatplugin toggle chat` - Toggle public chat
  - `/chatplugin toggle messages` - Toggle private messages
  - Independent or linked toggle behavior (configurable)
  - Persistent state across server restarts
  - Staff bypass permissions
- **Social Spy & Moderation Tools**:
  - `/chatplugin toggle socialspy` - Monitor private messages
  - Optional command spy for monitoring player commands
  - Ignore moderator messages option
  - Console logging for audit purposes
- **PlaceholderAPI Integration**:
  - Automatic detection and optional loading
  - Bridge to MiniMessage TagResolver system
  - Support for all external plugin placeholders
  - Performance optimized with timeout protection
- **Comprehensive Command System** using Lamp framework:
  - `/chatplugin reload` - Reload configuration
  - `/chatplugin info` - View plugin information
  - `/chatplugin test <message>` - Test chat formatting
  - `/chatplugin status` - View current chat status
  - `/chatplugin format set default/group/world <format>` - Configure formats
  - `/chatplugin format list` - List all formats
  - `/chatplugin toggle colors/formatting/mentions/cooldown` - Toggle features
  - `/chatplugin admin toggle chat/messages/all <player> <true/false>` - Admin toggles
  - `/chatplugin admin socialspy <player> <true/false>` - Manage social spy
  - `/chatplugin admin stats` - View system statistics
  - `/chatplugin admin clear <type>` - Clear various data types
- **Built-in Placeholder System** with extensive placeholders:
  - Player info: `{player_name}`, `{player_displayname}`, `{player_uuid}`
  - World info: `{world}`, `{world_displayname}`
  - Server info: `{server_name}`, `{server_version}`, `{server_motd}`
  - Online players: `{online_players}`, `{max_players}`
  - Time/Date: `{time}`, `{date}`, `{datetime}`
  - Custom placeholders support
  - PlaceholderAPI integration with automatic detection and bridge to MiniMessage
- **Advanced Permission System** with granular controls
- **Error Handling & Logging** with SLF4J
- **Modern Paper API Usage** (AsyncChatEvent, Adventure Components)

# Configuration Examples

## Default Configuration (config.conf)
```hocon
chatFormat {
    defaultFormat = "<gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
    
    groupFormats {
        owner = "<gradient:red:gold>[OWNER]</gradient> <gradient:gold:yellow>{player_name}</gradient> <gray>»</gray> <white>{message}</white>"
        admin = "<red>[ADMIN]</red> <gold>{player_name}</gold> <gray>»</gray> <white>{message}</white>"
        moderator = "<blue>[MOD]</blue> <yellow>{player_name}</yellow> <gray>»</gray> <white>{message}</white>"
        helper = "<green>[HELPER]</green> <lime>{player_name}</lime> <gray>»</gray> <white>{message}</white>"
        vip = "<green>[VIP]</green> <aqua>{player_name}</aqua> <gray>»</gray> <white>{message}</white>"
        premium = "<gold>[PREMIUM]</gold> <yellow>{player_name}</yellow> <gray>»</gray> <white>{message}</white>"
        donor = "<light_purple>[DONOR]</light_purple> <pink>{player_name}</pink> <gray>»</gray> <white>{message}</white>"
        member = "<gray>[MEMBER]</gray> <white>{player_name}</white> <gray>»</gray> <gray>{message}</gray>"
    }
    
    worldFormats {
        world = "<green>[Overworld]</green> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
        world_nether = "<red>[Nether]</red> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
        world_the_end = "<dark_purple>[The End]</dark_purple> <gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>"
    }
    
    enableGroupFormats = true
    enableWorldFormats = false
    formatPriority = ["permission", "world", "group", "default"]
    enableRankedFormats = true
    rankedFormatPriority = ["owner", "admin", "moderator", "helper", "vip", "premium", "donor", "member", "default"]
    
    enableHoverMessages = true
    hoverMessages {
        admin = "<red>Administrator</red>\n<gray>Click to message</gray>"
        moderator = "<blue>Moderator</blue>\n<gray>Click to message</gray>"
        vip = "<green>VIP Member</green>\n<gray>Click to message</gray>"
        default = "<gray>Player</gray>\n<gray>Click to message</gray>"
    }
    
    enableClickActions = true
    clickActions {
        default = "suggest_command:/msg {player_name} "
    }
}

features {
    enableColorCodes = true
    enableFormatting = true
    enableUrls = true
    enableMentions = true
    enableChatCooldown = false
    chatCooldownSeconds = 3
    enableJoinLeaveMessages = true
    joinMessage = "<green>+ <yellow>{player_name}</yellow> joined the server</green>"
    leaveMessage = "<red>- <yellow>{player_name}</yellow> left the server</red>"
    enableDeathMessages = true
    enableChatLogging = true
}

placeholders {
    enableBuiltinPlaceholders = true
    customPlaceholders {
        server_name = "My Server"
        website = "example.com"
    }
    enablePlaceholderAPI = true
}

permissions {
    usePermissionBasedFormats = true
    formatPermissionPrefix = "chatplugin.format."
    colorPermission = "chatplugin.color"
    formattingPermission = "chatplugin.formatting"
    urlPermission = "chatplugin.url"
    mentionPermission = "chatplugin.mention"
}
```

## Permission Setup Examples

### LuckPerms Commands:
```bash
# Give admin format to a player
/lp user PlayerName permission set chatplugin.format.admin true

# Give VIP format to a group
/lp group vip permission set chatplugin.format.vip true

# Allow colors for all players
/lp group default permission set chatplugin.color true

# Bypass cooldown for staff
/lp group staff permission set chatplugin.bypass.cooldown true
```

### Group-based Permissions:
```bash
# Using traditional group permissions
/lp user PlayerName parent add admin  # Will use admin format if group.admin permission exists
```

## MiniMessage Format Examples

### Basic Formats:
```
<gray>[<white>{player_name}</white>]</gray> <gray>{message}</gray>
<red>[ADMIN]</red> <gold>{player_name}</gold> <gray>»</gray> <white>{message}</white>
```

### Advanced Formats with Gradients:
```
<gradient:red:gold>[OWNER]</gradient> <gradient:gold:yellow>{player_name}</gradient> <gray>»</gray> <white>{message}</white>
<gradient:blue:cyan>[VIP]</gradient> <rainbow>{player_name}</rainbow> <gray>»</gray> <white>{message}</white>
```

### Interactive Elements:
```
<hover:show_text:'<red>Administrator</red>\n<gray>Click to message</gray>'><click:suggest_command:'/msg {player_name} '><red>[ADMIN]</red> <gold>{player_name}</gold></click></hover> <gray>»</gray> <white>{message}</white>
```

# Build Instructions
- `./gradlew build` - Build all projects
- `./gradlew :PaperMC:shadowJar` - Build PaperMC plugin JAR
- `./gradlew :PaperMC:runServer` - Run test server with plugin

## Installation
1. Build the plugin: `./gradlew :PaperMC:shadowJar`
2. Copy `PaperMC/build/libs/PaperMC-1.0-SNAPSHOT-all.jar` to your server's `plugins/` folder
3. Start your server
4. Configure the plugin by editing `plugins/MiniMessageChatPlugin/config.conf`
5. Use `/chatplugin reload` to reload configuration changes

## Quick Start
1. Install the plugin
2. Give yourself admin permissions: `/lp user YourName permission set chatplugin.admin true`
3. Test the plugin: `/chatplugin test Hello World!`
4. Configure formats: `/chatplugin format set default <red>[<white>{player_name}</white>]</red> <gray>{message}</gray>`
5. Set up rank formats using permissions like `chatplugin.format.vip`
6. Try private messaging: `/msg PlayerName Hello there!`
7. Toggle features: `/chatplugin toggle chat` or `/chatplugin toggle socialspy`

## Permission Setup

### 🔑 **Essential Permissions**
```bash
# Basic user permissions (give to default group)
chatplugin.message          # Send/receive private messages
chatplugin.toggle.chat       # Toggle own public chat
chatplugin.toggle.messages   # Toggle own private messages
chatplugin.status           # View own chat status
chatplugin.color            # Use colors in chat
chatplugin.formatting       # Use text formatting

# Staff permissions
chatplugin.admin            # All admin commands
chatplugin.socialspy        # Monitor private messages
chatplugin.bypass.cooldown  # Bypass chat cooldowns
chatplugin.bypass.chattoggle # Always able to chat
```

### 🎨 **Rank Format Permissions**
```bash
# Format-specific permissions (one per rank)
chatplugin.format.owner     # Use owner chat format
chatplugin.format.admin     # Use admin chat format
chatplugin.format.moderator # Use moderator chat format
chatplugin.format.vip       # Use VIP chat format
chatplugin.format.premium   # Use premium chat format
```

### 🛠️ **Admin Permission Examples**
```bash
# LuckPerms examples for setting up permissions
/lp group admin permission set chatplugin.admin true
/lp group moderator permission set chatplugin.socialspy true
/lp group vip permission set chatplugin.format.vip true
/lp group default permission set chatplugin.message true
/lp group default permission set chatplugin.color true
```

# Code Structure
- Common code is in the `utils` project
- Common application code is in the `app` project
- Platform specific code is in the `PaperMC` and `Sponge` projects.
- New features must be in `PaperMC`, but can be added to `Sponge` if easily possible.

# Working
Always document project state in the project state section at the top of the README.

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
}```

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
}```


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
}```

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