# Common Server Commands Module

A comprehensive, reusable library of essential Minecraft server commands for PaperMC plugins. This module provides over 70 essential server commands that can be easily integrated into any PaperMC plugin.

## Features

- **Reusable Command Library**: Extract all commands from a specific plugin and use them in any other plugin
- **Stateless Design**: All commands operate without storing external state; changes are made directly to Minecraft entities
- **Translation Support**: Built-in support for multi-language command messages
- **Configuration Support**: Optional configuration system for command behavior tuning
- **MCCoroutine Compatible**: Full support for Folia-compatible async operations
- **Lamp Framework Integration**: Uses the Lamp command framework for robust command handling

## Commands Included

The module provides the following command groups:

- **Game Mode Commands**: `/gamemode`, `/gmc`, `/gms`, `/gma`, `/gmsp`
- **Time & Weather**: `/time`, `/day`, `/night`, `/weather`, `/sun`, `/rain`, `/storm`, `/ptime`, `/pweather`
- **Player Management**: `/heal`, `/feed`, `/fly`, `/speed`, `/god`, `/ext`, `/rest`, `/exp`, `/kill`, `/burn`
- **Teleportation**: `/tp`, `/tphere`, `/tpall`, `/tppos`, `/top`, `/bottom`, `/jump`, `/world`
- **Inventory**: `/anvil`, `/workbench`, `/grindstone`, `/cartography`, `/loom`, `/smithing`, `/stonecutter`, `/enderchest`, `/invsee`, `/clearinventory`
- **Items**: `/give`, `/item`, `/more`, `/repair`, `/enchant`, `/hat`, `/skull`, `/itemname`, `/itemlore`, `/condense`
- **World**: `/lightning`, `/remove`, `/spawnmob`, `/tree`, `/bigtree`, `/break`, `/spawner`
- **Admin**: `/kick`, `/ban`, `/unban`, `/sudo`, `/broadcast`
- **Info**: `/gc`, `/list`, `/whois`, `/near`, `/seen`, `/ping`, `/getpos`, `/compass`, `/depth`, `/playtime`
- **Fun**: `/me`, `/fireball`, `/book`, `/editsign`, `/potion`, `/firework`

## Integration Guide

### 1. Implement CommandPlugin Interface

Your plugin must implement the `CommandPlugin` interface:

```kotlin
import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.config.CommonServerCommandsConfig
import bruh.commands.commonservercommands.config.CommonServerCommandsConfigLoader
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin

class MyPlugin : SuspendingJavaPlugin(), CommandPlugin {
    lateinit var configLoader: CommonServerCommandsConfigLoader
    override lateinit var config: CommonServerCommandsConfig
    override lateinit var translations: TranslationAPI
    override var menuAPI: MenuAPI? = null
    
    override fun getUnderlyingPlugin(): SuspendingJavaPlugin = this
    
    override suspend fun onEnableAsync() {
        // Load configuration
        configLoader = CommonServerCommandsConfigLoader(dataFolder.toPath(), slF4JLogger)
        config = configLoader.load()
        
        // Initialize translations
        translations = translationApi()
        translations.register("commands", CommandMessages::class)
        translations.switchLanguage(config.language)
        translations.load()
        
        // Initialize MenuAPI if needed
        menuAPI = MenuAPI(this)
        
        // Register commands
        setupCommands()
    }
    
    private fun setupCommands() {
        val lamp = BukkitLamp.builder(this)
            .suggestionProviders { providers ->
                // Setup suggestion providers for annotations
                providers.addProviderForAnnotation(SuggestGameMode::class.java) { _ ->
                    SuggestionProvider { _ ->
                        GameMode.entries.map { it.name.lowercase() }
                    }
                }
                // ... add other providers as needed
            }
            .build()
        
        // Create command factory and register all commands
        val factory = CommonServerCommandsFactory(this)
        factory.createAllCommands().forEach { lamp.register(it) }
    }
}
```

### 2. Configuration

The module uses Configurate for configuration management. Configuration is stored in `config.conf` with the following options:

```hocon
language = "en"

defaultFlySpeed = 0.1
defaultWalkSpeed = 0.2
maxFlySpeed = 1.0
maxWalkSpeed = 1.0
defaultHealAmount = 20.0
defaultFeedAmount = 20
maxRemoveEntities = 1000
maxSpawnMobs = 100
nearRadius = 200

broadcastFormat = "<red>[Broadcast]</red> <message>"
worldBroadcastFormat = "<yellow>[<world>]</yellow> <message>"
meFormat = "<gray>* <player> <action></gray>"
```

### 3. Translations

Translations are managed through the `CommandMessages` enum class. Each command message has a key and a default English translation. To support additional languages, create translation files in your plugin's translations folder.

## Architecture

### Key Classes

- **CommandPlugin**: Interface that plugins must implement to use the commands
- **CommonServerCommandsFactory**: Factory for creating command instances
- **CommonServerCommandsConfig**: Configuration data class
- **CommonServerCommandsConfigLoader**: Handles loading and saving configuration
- **CommandMessages**: Enum of all command message keys and defaults

### Command Classes

- `GameModeCommands`: Game mode changing commands
- `TimeWeatherCommands`: Time and weather management
- `PlayerCommands`: Player state management (heal, feed, fly, etc.)
- `TeleportCommands`: Teleportation commands
- `InventoryCommands`: Virtual container and inventory management
- `ItemCommands`: Item manipulation commands
- `WorldCommands`: World manipulation commands
- `AdminCommands`: Administrative commands
- `InfoCommands`: Server and player information commands
- `FunCommands`: Entertainment commands

### Annotations

The module provides custom annotations for Lamp suggestion providers:

- `@SuggestGameMode`: Suggests available game modes
- `@SuggestWorld`: Suggests available worlds
- `@SuggestOnlinePlayer`: Suggests currently online players
- `@SuggestEntityType`: Suggests spawnable entity types

## Dependencies

This module requires:

- PaperAPI 1.21+
- MCCoroutine (for Folia compatibility)
- Lamp command framework
- Kyori Adventure (for text formatting)
- Configurate (for configuration management)
- Slf4j (for logging)

## Permissions

All commands use the permission format: `essentiallystateless.<command>`

Common permission hierarchy:
- `essentiallystateless.admin` - Grants all permissions
- `essentiallystateless.<command>` - Base permission for a command
- `essentiallystateless.<command>.others` - Permission to use command on other players

Refer to the EssentiallyStateless plugin documentation for the complete permission reference.

## Example Usage

See the [EssentiallyStateless plugin](../../EssentiallyStateless) for a complete implementation example.
