# Utils Module

Shared utilities for the monorepo plugins including configuration loading, menu systems, and translation support.

## Config API

Generic configuration loaders using Configurate with HOCON format.

### TypedConfigLoader

For `@ConfigSerializable` data classes:

```kotlin
val configLoader = TypedConfigLoader.create(
    configPath = dataFolder.resolve("config.conf"),
    defaultFactory = { MyConfig() },
    transform = { config ->
        // Transform runs on every load/reload
        config.copy(someField = config.someField.uppercase())
    }
)

// In a coroutine
val config = configLoader.load()
val reloaded = configLoader.reload()
```

### UntypedConfigLoader

For raw `CommentedConfigurationNode` access:

```kotlin
val loader = UntypedConfigLoader(
    configPath = dataFolder.resolve("data.conf"),
    defaultNodeFactory = { node ->
        node.node("version").set(1)
    },
    transform = { node ->
        // Runs on every load
    }
)

val node = loader.load()
```

## Menu API

### Standard Menus

DSL-based menu creation (see `menuapi/` package):

```kotlin
val menuApi = MenuAPI(plugin)

val menu = menuApi.simple {
    title = Component.text("My Menu")
    rows = 3
    
    item(13, XMaterial.DIAMOND) {
        name = Component.text("Click me!")
        onClickDeny { ctx, _ ->
            ctx.player.sendMessage("Clicked!")
        }
    }
}

menuApi.open(menu, player)
```


### MenuTree Navigation DSL

Build multi-level, back/close-aware GUI trees with actions and optional form inputs, then wait for a result. This is ideal for command-driven flows (see RegionRestore GUI).

```kotlin
// Suspend until the user completes an action or closes
val result = menuAPI.menuTree {
    title("Main Menu")

    // Root submenu with pagination
    submenu("templates", "Templates", XMaterial.BOOK) {
        paginated(28)

        // Static action at this level
        action(
            id = "create_template",
            title = "Create Template",
            material = XMaterial.ANVIL,
            description = listOf("Create a new template"),
            returnLevels = 1 // navigate back to parent after completion
        ) { player ->
            val name = menuAPI.promptText(player, "Enter name").getOrNull() ?: return@action null
            val minX = menuAPI.promptInt(player, "Min X").getOrNull() ?: return@action null
            // ... domain logic here (async-friendly)
            null
        }

        // Dynamic children (e.g., one submenu per template)
        dynamicItems { _ ->
            templateNames.map { tmplName ->
                submenuNode("tmpl_$tmplName", tmplName, XMaterial.PAPER) {
                    display("info", "Info", XMaterial.BOOK, listOf("Some lore"))
                    action("restore", "Restore", XMaterial.EMERALD) { p ->
                        restoreTemplate(p, tmplName)
                    }
                }
            }
        }
    }
}.open(player)

when (result) {
    is MenuTreeResult.ActionCompleted -> { /* actionId + optional data */ }
    is MenuTreeResult.Cancelled -> { /* user backed out */ }
    is MenuTreeResult.ClosedAtRoot -> { /* closed at root */ }
}
```

- **Nodes**: `submenu(...)`, `action(...)`, `actionWithForm(...)`, `nonClosingAction(...)`, `display(...)`.
- **Dynamic items**: `dynamicItems { player -> List<MenuNode> }` for per-player menus.
- **Return behavior**: `returnLevels > 0` returns up N levels after the action, otherwise closes. You can override with `postAction = ReturnAction.*`.
- **Helpers**: `submenuNode(...)`, `actionNode(...)`, `nonClosingActionNode(...)`, `displayNode(...)`, and `dynamicNodes(list) { ... }` to build nodes programmatically.
- **Async**: Use `.open(player)` (suspend) or `.openAsync(player)` (CompletableFuture<MenuTreeResult>).
- **Background & Nav**: Configure `background(...)` per submenu; built-in Back/Close are added to the bottom row.
- **Pagination note**: Paginated submenu rendering is available via `.paginated(...)`. Current implementation renders child icons; if you need clickable, paginated lists with per-item handlers, prefer composing a `PaginatedMenu` directly and wiring clicks there.

### SimpleMenu / DynamicMenu / ItemMenu / PaginatedMenu

All builders are created off `MenuAPI`, and opened via `menuAPI.open(menu, player)`.

- **SimpleMenu**: fixed slots.
```kotlin
val menu = menuAPI.simple {
    title = Component.text("Simple")
    rows = 3
    background = VItem.FILLER_GRAY
    item(13, XMaterial.DIAMOND) {
        name = Component.text("Click me")
        onClickDeny { ctx, _ -> ctx.player.sendMessage(Component.text("Hi")) }
    }
}
menuAPI.open(menu, player)
```

- **DynamicMenu**: auto-centers and evenly spaces items; just add items, it computes `rows` and slots.
```kotlin
val menu = menuAPI.dynamic {
    title = Component.text("Dynamic")
    background = VItem.FILLER_GRAY
    item(XMaterial.EMERALD) { name = Component.text("A") }
    item(XMaterial.REDSTONE) { name = Component.text("B") }
}
menuAPI.open(menu, player)
```

- **ItemMenu**: mix display items and drag-and-drop slots with validation and callbacks.
```kotlin
val menu = menuAPI.item {
    title = Component.text("Trade")
    rows = 6
    // Display-only button
    item(53, XMaterial.EMERALD_BLOCK) { name = Component.text("Confirm") }
    // Drag slot with default placeholder and validation
    dragSlot(20) {
        defaultItem = VItem(XMaterial.LIME_STAINED_GLASS_PANE) { name = Component.text("Place item here") }
        itemValidator = { stack -> stack.amount in 1..64 }
        onItemPlace = { p, stack, _ -> p.sendMessage(Component.text("Placed ${stack.type}")); true }
        onItemRemove = { p, stack, _ -> p.sendMessage(Component.text("Removed ${stack.type}")); true }
        onSlotChange = { p, current, _ -> /* called after change */ }
    }
}
menuAPI.open(menu, player)
// Later, read items: menu.getDragSlotItems(inventory)
```

- **PaginatedMenu<T>**: render collections across pages and wire navigation.
```kotlin
val menu = menuAPI.paginated<String> {
    title = Component.text("Players")
    rows = 6
    dataSource = Bukkit.getOnlinePlayers().map { it.name }
    contentSlots = (10..16) + (19..25) + (28..34) + (37..43)
    itemRenderer = { name, _ ->
        VItem(XMaterial.PLAYER_HEAD) {
            this.name = Component.text(name)
            onClickDeny { _, controls -> /* open details */ ClickResult.DENY }
        }
    }
    pageIndicatorRenderer = { cur, total -> VItem(XMaterial.PAPER) { name = Component.text("Page $cur/$total") } }
}
menuAPI.open(menu, player)
```

### Anvil Input (Text/Number prompts)

Use quick helpers for common prompts (used heavily by RegionRestore flows):

```kotlin
// Text
val text = menuAPI.promptText(player, title = "Enter name") { s ->
    if (s.isNotBlank()) InputValidation.Valid else InputValidation.Invalid("Required")
}

// Int / Double (min/max optional)
val count = menuAPI.promptInt(player, title = "Count", min = 1).getOrNull()
val price = menuAPI.promptDouble(player, title = "Price", min = 0.0).getOrNull()
```

For full control:

```kotlin
val builder = menuAPI.textInput {
    title = "Enter text"
    initialText = "default"
    validator = { t -> if (t.length <= 16) InputValidation.Valid else InputValidation.Invalid("Max 16") }
}
val result = builder.openAsync(player) // or builder.open(player) in suspend
```

### MenuAPI lifecycle and utilities

- **Init**: `val menuAPI = MenuAPI(plugin)` in onEnable; it auto-registers its listeners.
- **Open/Close**: `menuAPI.open(menu, player)`, `menuAPI.close(player)`, `menuAPI.closeAll(menu)`.
- **Updates**: `menuAPI.scheduleUpdate(periodTicks) { controls -> /* refresh or animate */ }`.
- **Configurable menus**: `menuAPI.configurable()` exposes the config-driven system documented below.
- **Shutdown**: call `menuAPI.close()` in onDisable to unregister listeners and close open menus.


### Configurable Menus

Config-driven menus with action enum pattern (see `menuapi/configurable/` package):

#### Setup

```kotlin
val menuApi = ConfigurableMenuAPI(plugin)

// Register menus on enable
menuApi.register(ShopMenu(menuApi))

// Close on disable
menuApi.close()
```

#### Defining a Menu

```kotlin
enum class ShopActions { BUY, SELL, CLOSE }

class ShopMenu(menuApi: ConfigurableMenuAPI) : ConfigurableMenu<ShopActions>(
    menuApi = menuApi,
    configName = "shop",  // Creates menus/shop.conf
    actionClass = ShopActions::class
) {
    override val actionHandlers = mapOf(
        ShopActions.BUY to { ctx, instance ->
            ctx.player.sendMessage("Buying!")
            ClickResult.DENY
        },
        ShopActions.SELL to { ctx, instance ->
            ctx.player.sendMessage("Selling!")
            ClickResult.DENY
        },
        ShopActions.CLOSE to { _, instance ->
            instance.close()
            ClickResult.CLOSE
        }
    )
}
```

#### Menu with Drag Slots

```kotlin
enum class TradeActions { CONFIRM, CANCEL }
enum class TradeSlots { MY_ITEM, THEIR_ITEM }

class TradeMenu(menuApi: ConfigurableMenuAPI) : ConfigurableItemMenu<TradeActions, TradeSlots>(
    menuApi = menuApi,
    configName = "trade",
    actionClass = TradeActions::class,
    slotClass = TradeSlots::class
) {
    override val actionHandlers = mapOf(
        TradeActions.CONFIRM to { ctx, instance ->
            val myItem = instance.getItem(TradeSlots.MY_ITEM)
            val theirItem = instance.getItem(TradeSlots.THEIR_ITEM)
            // Process trade...
            ClickResult.CLOSE
        },
        TradeActions.CANCEL to { _, instance ->
            instance.close()
            ClickResult.CLOSE
        }
    )

    // Optional: Add code-based validators on top of config validators
    override val additionalSlotValidators = mapOf(
        TradeSlots.MY_ITEM to { item -> item.amount <= 32 }
    )
}
```

#### Config File Format (HOCON)

`menus/shop.conf`:
```hocon
title = "<gold>Item Shop</gold>"
rows = 6

background {
    material = "BLACK_STAINED_GLASS_PANE"
    name = ""
    hide-tooltip = true
}

items {
    buy-button {
        slot = 11
        material = "EMERALD"
        name = "<green><bold>Buy Items</bold></green>"
        lore = [
            "<gray>Click to open buy menu",
            "<yellow>Stock: 100"
        ]
        enchant-glint = true
        action = "BUY"
    }
    
    sell-button {
        slot = 15
        material = "GOLD_INGOT"
        name = "<gold>Sell Items</gold>"
        action = "SELL"
    }
    
    info-display {
        slot = 4
        material = "BOOK"
        name = "<white>Shop Info</white>"
        lore = ["<gray>Welcome to the shop!"]
        # No action = display only
    }
    
    close-button {
        slot = 49
        material = "BARRIER"
        name = "<red>Close</red>"
        action = "CLOSE"
    }
}
```

`menus/trade.conf`:
```hocon
title = "<gold>Trade Menu</gold>"
rows = 6

items {
    confirm {
        slot = 53
        material = "EMERALD_BLOCK"
        name = "<green>Confirm Trade</green>"
        action = "CONFIRM"
    }
    cancel {
        slot = 45
        material = "BARRIER"
        name = "<red>Cancel</red>"
        action = "CANCEL"
    }
}

drag-slots {
    MY_ITEM {
        slot = 20
        default-item {
            material = "LIME_STAINED_GLASS_PANE"
            name = "<green>Place your item here</green>"
        }
        validator {
            allowed-materials = ["DIAMOND", "EMERALD", "GOLD_INGOT"]
            max-amount = 64
        }
    }
    THEIR_ITEM {
        slot = 24
        default-item {
            material = "RED_STAINED_GLASS_PANE"
            name = "<red>Their item appears here</red>"
        }
    }
}
```

#### Item Config Options

All VItem properties are configurable:

| Property | Type | Description |
|----------|------|-------------|
| `slot` | Int | Slot position (0-indexed) |
| `material` | String | XMaterial name |
| `name` | String | Display name (MiniMessage) |
| `lore` | List\<String\> | Lore lines (MiniMessage) |
| `amount` | Int | Stack size |
| `custom-model-data` | Int | Custom model data |
| `item-model` | String | Item model key (namespace:key) |
| `flags` | List\<String\> | ItemFlag names |
| `unbreakable` | Boolean | Unbreakable flag |
| `hide-tooltip` | Boolean | Hide tooltip entirely |
| `enchant-glint` | Boolean | Show glint without enchants |
| `is-glider` | Boolean | Glider component |
| `damage` | Int | Item damage |
| `max-stack-size` | Int | Max stack size override |
| `enchantments` | Map\<String, Int\> | Enchantments |
| `skull-owner` | String | Player head UUID |
| `skull-owner-name` | String | Player head name (legacy) |
| `action` | String | Action enum name (null = display only) |

#### Drag Slot Validator Options

| Property | Type | Description |
|----------|------|-------------|
| `allowed-materials` | List\<String\> | Only these materials accepted |
| `blocked-materials` | List\<String\> | These materials rejected |
| `min-amount` | Int | Minimum stack amount |
| `max-amount` | Int | Maximum stack amount |
| `require-name` | Boolean | Must have display name |
| `require-lore` | Boolean | Must have lore |

## Dependencies

The utils module includes:
- XSeries for cross-version material support
- AnvilGUI for anvil input menus
- Kyori Adventure for text components
- Configurate HOCON for configuration
