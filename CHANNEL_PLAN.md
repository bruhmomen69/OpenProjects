## 1. Big-picture design

We will introduce a channel system that:

- **Reads from `ChannelsConfig`** and models:
  - Channel definitions (name, display name, commands, formats, cross-server flag, identifier creator, etc.).
  - Channel *instances* (definition + identifier).
- **Manages membership and active channel state** via a `ChannelService`.
- **Integrates with chat**:
  - Per-channel join/send commands via `PlayerCommandPreprocessEvent` (non-Lamp).
  - General channel commands via Lamp.
  - `AsyncChatEvent`:
    - Early: use `viewers()` to enforce visibility rules.
    - Late: per-audience formatting, compatible with `cacheFormats` on/off.
- **Adds cross-server channel chat** on top of the existing Redis/SQL message bus.
- **Implements channel social spy** for staff to monitor channel messages.
- **Follows mccoroutine/Folia threading rules**:
  - No new suspensions or `runBlocking` in `AsyncChatEvent`.
  - Heavy work offloaded to async/IO dispatchers.

Your explicit decisions:

- **When `allMessagesToChannel = true` but `channelOnly = false`**:
  - Messages are **dual-sent**:
    - They behave as *global* chat locally (visible to everyone who would normally see chat).
    - They are *also* treated as channel messages for:
      - Channel formatting.
      - Channel logs / social spy.
      - Cross-server channel forwarding (other servers only deliver them to channel members).
- **Console visibility**:
  - Console and other non-player audiences **always see all messages**, including purely channelled ones.
- **Channel social spy**:
  - Design a system so staff can effectively spy on all channels, including cross-server channel messages.

---

## 2. Channel identifiers – detailed design

Channel identifiers are central: they define *instances* of a channel and the routing key for cross-server.

### 2.1 Concepts and types

- **`ChannelDefinition`** (from `ChannelConfig`)
  - Fields:
    - `name: String` — logical channel name from config.
    - `displayName: String`
    - `commands: List<String>`
    - `allMessagesToChannel: Boolean`
    - `requiredPermission: String`
    - `identifierCreator: String` (raw PlaceholderAPI string; may be empty).
    - `requireIdentifierToJoin: Boolean`
    - `crossServerBridge: Boolean`
    - `identifierRefreshTicks: Int`
    - `chatFormat: String`
    - `groupFormats: List<ChannelChatFormatInstanceConfig>`
  - `name` is treated as **definition key**, not instance key.

- **`ChannelInstanceKey`**
  - Value object representing a specific instance:
    - `nameKey: String` — normalized channel name.
    - `identifier: String` — normalized, non-blank instance identifier.
  - Equality and hashing are based on both fields.
  - It can expose a convenience property:
    - `val globalKey: String get() = "$nameKey|$identifier"`

This `ChannelInstanceKey` is what we use:

- As keys in `membersByInstance`.
- For locating instance members on each server.
- As the primary identity for cross-server routing.

### 2.2 Normalizing channel names

All servers must agree on what `"staff"` or `"perworld"` means.

- **Normalization algorithm for `nameKey`**:
  - `nameKey = name.trim().lowercase(Locale.ROOT)`
  - Optionally:
    - Replace internal whitespace runs with `_`.
    - Strip characters outside `[a-z0-9_:-]` if you want extra safety.
  - The *original* `name` and `displayName` are preserved for UI; `nameKey` is for identity.

### 2.3 Identifiers when `identifierCreator` is empty

For channels **without** `identifierCreator`:

- These are “singleton” channels; there is only one instance.
- We still need a well-defined `identifier`:

  - Proposal:
    - `identifier = "default"` (constant).
    - So the single instance key is `ChannelInstanceKey(nameKey, "default")`.
  - This is:
    - Simple.
    - Stable across servers.
    - Gives us a clean cross-server key: `"staff|default"`.

This covers common named channels like `staff`, `globalstaff`, `support`, etc.

### 2.4 Identifiers when `identifierCreator` is non-empty

For channels that support **multiple instances per definition**, e.g. per-world chat:

- `identifierCreator` is a PlaceholderAPI string, e.g. `%player_world%`.

- **Evaluation rules**
  - Run PlaceholderAPI through your existing `PlaceholderAPIService` (same mechanics as elsewhere):
    - Do **not** eval in `AsyncChatEvent` handlers; instead:
      - Evaluate when:
        - A player **joins** the server and auto-joins channels.
        - A player runs a channel join command.
        - An identifier refresh tick fires.
  - Threading:
    - Evaluate identifiers synchronously from event handlers that are already on a Minecraft thread (join, command).
    - For refresh tasks, use `plugin.entityDispatcher(player)` so all entity access is safe on Folia and Bukkit.

- **Post-processing of the resolved identifier**
  - Let `rawIdentifier = placeholderAPIService.parse(..., identifierCreator)`.
  - Then normalize:

    ```text
    normalized = rawIdentifier.trim()
    if (normalized.isEmpty()) {
        if (requireIdentifierToJoin) {
            // Cannot join; no instance.
        } else {
            normalized = "default"
        }
    }
    // Optionally: lowercase + sanitize similar to nameKey.
    ```

  - Result must be **non-blank**.

- **Critical constraints**
  - The same `nameKey` + `identifier` combination on **different servers** must represent the same logical channel instance.
  - `identifierCreator` must not include server-instance-specific placeholders (e.g., random IDs), only context like world/town/guild that’s consistent between servers.

### 2.5 Instance keys & derived IDs

- `ChannelInstanceKey(nameKey, identifier)` is the primary identity in memory.

- **Cross-server identity**

  - We define:
    - `channelName` in envelopes: `nameKey` (normalized config name).
    - `channelIdentifier` in envelopes: `identifier` (normalized).
  - Optionally also:
    - `displayName` string for convenience, not for identity.

- **Internal combined key**
  - For maps and for Redis keys, we can use `globalKey`:

    ```kotlin
    val channelInstanceGlobalKey = "$nameKey|$identifier"
    ```

  - This string:
    - Is stable.
    - Can be used as part of Redis channels, logs, debugging output, etc.

### 2.6 Identifier refresh behavior

For each `ChannelDefinition` with:

- `identifierCreator` not empty.
- `identifierRefreshTicks > 0`.

We:

- Use `ScheduledTaskService` to schedule a repeating task on **each online player** who is currently in that channel definition.

  - Threading:
    - The scheduled task will run on `plugin.entityDispatcher(player)` (Folia-style) or main thread on Bukkit.
    - In that context:
      - Re-evaluate `identifierCreator` with PlaceholderAPI.
      - Normalize to `newIdentifier`.
      - Compare with the player’s current identifier for that definition (stored in `PlayerChannelState`).

- If identifier changed:
  - Compute `oldKey` = `ChannelInstanceKey(nameKey, oldIdentifier)`.
  - Compute `newKey` = `ChannelInstanceKey(nameKey, newIdentifier)`.
  - Update:
    - Remove from `membersByInstance[oldKey]`, add to `membersByInstance[newKey]`.
    - In `PlayerChannelState`, replace `oldKey` with `newKey` in `joinedInstances`.
    - If `activeInstance == oldKey`, set it to `newKey`.
  - If `requireIdentifierToJoin` and `newIdentifier` is effectively invalid (blank before fallback), we:
    - Remove the player from that channel entirely.
    - Optionally send them a configured message (“Left per-world channel because the identifier is no longer valid”).

This ensures players move between channel instances when world/town/guild context changes, without commands.

---

## 3. `ChannelService` – data & API

### 3.1 State

New service in Paper:

- `definitionsByName: Map<String, ChannelDefinition>` keyed by `nameKey`.
- `membersByInstance: ConcurrentHashMap<ChannelInstanceKey, MutableSet<UUID>>`.
- `playerStateByUuid: ConcurrentHashMap<UUID, PlayerChannelState>` where:

  ```kotlin
  data class PlayerChannelState(
      val joinedInstances: MutableSet<ChannelInstanceKey>,
      var activeInstance: ChannelInstanceKey?, // "talk channel"
      // Optionally track resolved identifiers per definition if needed.
  )
  ```

### 3.2 Key APIs

Non-suspending, in-memory methods:

- Definition access:
  - `getDefinitions(): Collection<ChannelDefinition>`
  - `getDefinitionByName(name: String): ChannelDefinition?`  
    (normalizes `name` to `nameKey`).
- Membership & targeting:
  - `joinChannel(player: Player, nameOrDefinition, explicit: Boolean): JoinResult`
  - `leaveChannel(player: Player, nameOrInstance): LeaveResult`
  - `getJoinedInstances(player: Player): Set<ChannelInstanceKey>`
  - `getActiveInstance(player: Player): ChannelInstanceKey?`
  - `setActiveInstance(player: Player, instance: ChannelInstanceKey?)`
  - `getViewersForInstance(instance: ChannelInstanceKey): Collection<Player>`
- Outgoing chat routing:
  - `getInstanceForOutgoingChat(player: Player): ChannelInstanceKey?`
    - Reflects:
      - A temporary “next message goes to channel X only” flag set by `/sc message` for that event.
      - Else `activeInstance` if present and `allMessagesToChannel = true`.
      - Else `null` (global-only).

### 3.3 Auto-join on login

Hook in `PlayerJoinQuitListener`:

- On join:
  - If `channels.enabled` and `autoJoin.enabled`:
    - Iterate `ChannelsConfig.channels` in configured order.
    - For each definition:
      - Check `requiredPermission`.
      - Resolve identifier (see section 2.4).
      - If allowed, join that instance (using `joinChannel`).
    - If `autoJoin.multiple == false`: stop after first success.
  - Choose `activeInstance`:
    - Prefer first joined channel with `allMessagesToChannel = true`.
    - Otherwise first joined channel.
    - Otherwise `null`.

- On quit:
  - Remove player from all `membersByInstance` sets and from `playerStateByUuid`.

---

## 4. Per-channel join + send commands (non-Lamp)

We implement per-channel commands using `PlayerCommandPreprocessEvent`.

### 4.1 Command behavior

For each `ChannelDefinition` and each `command` in `commands`:

- Commands: `/worldchat`, `/sc`, etc.

- Parsing logic:
  - If player runs `/sc` with **no args**:
    - Toggle join/leave for that channel instance:
      - Use `ChannelService.resolveInstanceForPlayer`:
        - Evaluate identifier if needed.
        - Only allow join if `requiredPermission` and `requireIdentifierToJoin` satisfied.
      - If already in that instance: leave it (and adjust `activeInstance` if necessary).
      - If not: join and **optionally** set as `activeInstance` when `allMessagesToChannel = true`.
  - If player runs `/sc hello world`:
    - Treat as “send to this channel only for this message”:
      - Determine instance as above.
      - Mark a *one-shot* flag in `ChannelService` so that `getInstanceForOutgoingChat` returns this instance for the next `AsyncChatEvent`, in “channel-only” mode (see below).
      - Cancel the command and inject the message into the normal chat pipeline by:
        - Either calling `player.chat()` with the stripped message (safest is to reuse the existing chat event pipeline).
        - Or constructing and firing your own `AsyncChatEvent` equivalent if necessary.

### 4.2 Channel-only vs dual behavior

We distinguish:

- **Explicit send commands (`/sc message`)**:
  - Always treated as **channel-only**:
    - For that event, `ChannelService` marks:
      - `forcedChannelInstance = instance`.
      - `forcedChannelOnly = true`.
    - Early and late chat handlers respect that; see section 5.

- **All-messages-to-channel auto-mode (`allMessagesToChannel = true`)**
  - If `channelOnly = true`:
    - Plain chat goes to channel-only (no global).
  - If `channelOnly = false`:
    - Plain chat is **dual**:
      - Seen by everyone globally.
      - Also considered a channel message for formatting, logs and cross-server.

No Lamp usage here; all dynamic from config.

---

## 5. AsyncChatEvent integration

Two handlers already exist: `onAsyncChatEarly` and `onAsyncChatLate`.

### 5.1 Early: viewer filtering with `event.viewers()`

We enhance `onAsyncChatEarly(event: AsyncChatEvent)`:

1. **Pre-checks**: keep your existing logic:
   - [chatToggleService.canSendChat](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChatToggleService.kt:94:4-108:5).
   - Swear filter.
2. **Determine channel routing for this message**:
   - Ask `ChannelService` for a **routing descriptor**, e.g.:

     ```kotlin
     data class ChannelRouting(
         val instance: ChannelInstanceKey?, // null = global
         val channelOnly: Boolean          // true = only channel members (for this event)
     )
     ```

   - Implementation in `ChannelService`:
     - If a forced message from `/sc message` is pending:
       - Use its `instance`.
       - `channelOnly = true`.
       - Clear that one-shot flag.
     - Else, if `allMessagesToChannel = true` and player has an `activeInstance`:
       - If `channels.channelOnly == true`:
         - `instance = activeInstance`, `channelOnly = true`.
       - If `channels.channelOnly == false`:
         - `instance = activeInstance`, `channelOnly = false` (dual).
     - Else:
       - `instance = null`, `channelOnly = false`.

3. **Filter viewers**:
   - If `instance == null`:
     - Do **not** filter for channels; viewers remain as set by Paper+other plugins.
   - If `instance != null` and `channelOnly == true`:
     - Restrict to channel members **and** keep all non-player audiences:

       ```kotlin
       val routing = ... // from above
       if (routing.instance != null && routing.channelOnly) {
           val instance = routing.instance
           event.viewers().removeIf { audience ->
               when (audience) {
                   is org.bukkit.entity.Player -> {
                       !channelService.isMember(audience, instance)
                   }
                   else -> false // console/other => ALWAYS keep
               }
           }
       }
       ```

   - If `instance != null` and `channelOnly == false` (dual mode):
     - **Do not remove any viewers** because global portion must be preserved.
     - The channel is still noted for formatting (and for cross-server send later).

This honors:

- Channel-only messages (pure channels, plus console).
- Dual messages (global plus channel semantics).
- “Console always sees everything” (we never remove non-Player audiences).

Threading: no new suspensions or `runBlocking`.

### 5.2 Late: per-audience formatting with `cacheFormats` handling

We update `onAsyncChatLate(event: AsyncChatEvent)`.

#### Common responsibilities

- If `configManager.config.chat.enableFormatting` is false: return.
- Apply cooldown according to `cacheFormats`:
  - When `cacheFormats = false`: your current behavior (apply per-message).
  - When `cacheFormats = true`: keep cooldown logic where it is now (one hit per message).

#### Channel-aware formatting service

Introduce a `ChannelFormattingService` (or extend [ChatFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChatFormattingService.kt:14:0-341:1)) with:

```kotlin
fun formatChannelMessage(
    sender: Player,
    viewer: Audience,
    baseMessageComponent: Component, // processed inventory placeholders, etc.
    routing: ChannelRouting           // instance + channelOnly flag
): Component
```

Internally:

1. Determine if `routing.instance` is null.
   - If **null**:
     - Use **existing** global formatting pipeline (via [ChatFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChatFormattingService.kt:14:0-341:1)).
   - If not null:
     - Lookup `ChannelDefinition` for `routing.instance.nameKey`.
     - Determine format:
       - Check `definition.groupFormats` for a matching `requiredPermission` on **sender**.
       - Else fallback to `definition.chatFormat`.
     - Build placeholder maps:
       - `"message"` -> `baseMessageComponent`.
       - `"channel_name"` -> raw `definition.name` or `displayName`.
       - `"channel_identifier"` -> `routing.instance.identifier`.
     - Use [MessageFormattingService.formatMessageComponent](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:78:4-100:5) to parse MiniMessage with these placeholders.
     - Optionally wrap with hover/click like normal formats.

#### Case A: `cacheFormats = true`

We compute expensive pieces once per event:

- In handler:

  ```kotlin
  val player = event.player
  val plainMessage = plainTextSerializer.serialize(event.message())
  val messageComponent = chatInventoryPlaceholderService.processRawMessage(player, plainMessage)
  val routing = channelService.getRoutingForMessage(player) // same logic as early, minus channelOnly effect
  ```

- Set a renderer:

  ```kotlin
  event.renderer { source, sourceDisplayName, ignoredMessage, viewer ->
      channelFormattingService.formatChannelMessage(
          sender = source,
          viewer = viewer,
          baseMessageComponent = messageComponent,
          routing = routing
      )
  }
  ```

Even with caching, per-viewer differences (e.g. viewer-based PAPI) can come from [MessageFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:22:0-356:1) if you choose to use the viewer as `player` there; but at minimum we reuse `messageComponent`.

#### Case B: `cacheFormats = false`

We recompute `messageComponent` per render, as today:

```kotlin
event.renderer { source, sourceDisplayName, ignoredMessage, viewer ->
    val plain = plainTextSerializer.serialize(event.message())
    val baseComponent = chatInventoryPlaceholderService.processRawMessage(source, plain)
    val routing = channelService.getRoutingForMessage(source)
    channelFormattingService.formatChannelMessage(
        sender = source,
        viewer = viewer,
        baseMessageComponent = baseComponent,
        routing = routing
    )
}
```

Console as viewer:

- If `viewer` is not a `Player`:
  - Treat `viewer` as `null` in any viewer-dependent logic, but still format based on `sender` + `messageComponent`.
  - Console always receives the message (per early filtering behavior).

---

## 6. Cross-server channel messaging

We extend `CrossServerMessageBusService` to handle channel chat.

### 6.1 Envelope changes

- **`MessageType`**:
  - Add `CHANNEL_CHAT`.

- **`RedisEnvelope`**:
  - Add fields:

    ```kotlin
    val channelName: String? = null    // nameKey
    val channelIdentifier: String? = null
    ```

  - Existing JSON config uses `ignoreUnknownKeys = true`, so adding these is safe.

- **Channel identity in messages**
  - Use:
    - `channelName = instance.nameKey`.
    - `channelIdentifier = instance.identifier`.

### 6.2 Sending channel messages

New API:

```kotlin
suspend fun sendChannelMessage(
    senderUuid: UUID,
    senderName: String,
    instance: ChannelInstanceKey,
    processedMessage: String,
    originalMessage: String?
): Boolean
```

- Behavior:

  - Check `configManager.storage.crossServerMessaging.enabled`.
  - For Redis backend:
    - Build `RedisEnvelope` with:
      - `type = MessageType.CHANNEL_CHAT`
      - `payload.processedMessage = processedMessage`
      - `payload.originalMessage = originalMessage`
      - `channelName = instance.nameKey`
      - `channelIdentifier = instance.identifier`
    - PUBLISH to a single bus channel (same as PM bus, or a channel-specific one; simplest is existing `<prefix>:server:<serverId>` and broadcasting to all servers).
  - For SQL backend:
    - Insert into the `message_bus` table with type `CHANNEL_CHAT` and payload fields accordingly.
    - Existing polling/reclaim logic then picks it up on other servers.

- Importantly:
  - We don’t rely on presence for channels; we just broadcast and each server filters by membership.

### 6.3 Receiving channel messages

In `handleRedisMessage` / `processMessage`:

- If `type == CHANNEL_CHAT`:
  - Recreate instance key:

    ```kotlin
    val instance = ChannelInstanceKey(
        nameKey = envelope.channelName!!,
        identifier = envelope.channelIdentifier!!
    )
    ```

  - Ask `ChannelService.getViewersForInstance(instance)`:
    - Collection of local `Player` objects who are currently in that instance.
  - For each viewer:
    - Format the message using `ChannelFormattingService`:
      - Use `processedMessage` as the raw string and re-run [MessageFormattingService.processMessageContent](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:156:4-174:5) *if needed* for the viewer, or:
      - Treat `processedMessage` as already authority-checked and just insert into channel format as `<message>`.
  - Additionally:
    - Invoke channel social spy (see next section).

Threading:

- Cross-server handlers already use `plugin.launch(Dispatchers.IO)` and hop to entity dispatchers as needed. We’ll follow the existing pattern.

### 6.4 Where we call `sendChannelMessage`

From our chat pipeline:

- In `onAsyncChatLate`:
  - After we have:
    - `routing.instance`.
    - The string form of the processed message.
  - If:
    - `routing.instance != null`
    - And its `ChannelDefinition.crossServerBridge == true`
  - Then:

    ```kotlin
    plugin.launch(asyncDispatcher) {
        crossServerMessageBusService.sendChannelMessage(
            senderUuid = player.uniqueId,
            senderName = player.name,
            instance = routing.instance,
            processedMessage = processedMessageString,
            originalMessage = plainMessage
        )
    }
    ```

  - For **channel-only messages**, remote servers will deliver only to channel members.
  - For **dual messages**, local server shows them globally; remote servers deliver to channel members only.

From explicit commands:

- `/sc message`:
  - Same as above, applied to the forced channel instance and message text.

---

## 7. Social spy for channels

We extend [SocialSpyService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/SocialSpyService.kt:14:0-302:1) to support channel messages.

### 7.1 Config additions

In `config.conf` social spy section (and `MessagesConfig`):

- Add:
  - `enableChannelSpy: Boolean`
  - Optional `ignoreModeratorsForChannelSpy: Boolean` (default true; reuse existing flag or add new).
- In messages:
  - `socialSpy.channelSpyFormat: String` e.g.:

    ```hocon
    "<dark_gray>[CH-SPY]</dark_gray> <gray>[<channel_name>/<channel_identifier>] <sender>:</gray> <message>"
    ```

### 7.2 Local channel spy

Add methods to [SocialSpyService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/SocialSpyService.kt:14:0-302:1):

```kotlin
fun broadcastChannelMessage(
    sender: Player,
    instance: ChannelInstanceKey,
    rawMessage: String
)
```

- Steps:
  - If `!config.socialSpy.enableChannelSpy` or `socialSpyEnabled` empty: return.
  - If `ignoreModeratorsForChannelSpy` and `sender.hasPermission("zchat.socialspy")`: optionally skip (mirroring PM spy behavior).
  - Build spy component via [MessageFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:22:0-356:1):

    ```kotlin
    val spyMessage = messageFormattingService.formatMessage(
        format = configManager.messages.socialSpy.channelSpyFormat,
        player = sender,
        additionalPlaceholders = mapOf(
            "sender" to sender.name,
            "channel_name" to channelDisplayName,
            "channel_identifier" to instance.identifier,
            "message" to rawMessage
        ),
        processUrls = false,
        processMentions = false,
        allowColors = true,
        allowFormatting = true
    )
    ```

  - For each `spyPlayerUUID` in `socialSpyEnabled`:
    - `val spy = Bukkit.getPlayer(spyPlayerUUID)`
    - If online and `spy.uniqueId != sender.uniqueId`, send `spyMessage`.

- Optional:
  - Log to console if `config.socialSpy.logToConsole` is true.

### 7.3 Remote (cross-server) channel spy

Similarly:

```kotlin
fun broadcastRemoteChannelMessage(
    senderName: String,
    instance: ChannelInstanceKey,
    rawMessage: String
)
```

- Used when `CrossServerMessageBusService` receives a `CHANNEL_CHAT` payload.

- Steps:
  - Similar to local spy, but choose a `player` context for placeholder resolution:
    - Either pick the first viewer, or fall back to null and only use string placeholders.

### 7.4 Hooking into pipeline

- In `onAsyncChatLate`:
  - After local delivery, if the message is considered channelled (explicit or allMessagesToChannel):
    - Call `socialSpyService.broadcastChannelMessage(...)` with the original (or processed) message string.
- In `CrossServerMessageBusService` when handling `CHANNEL_CHAT`:
  - After delivering to channel viewers:
    - Call `broadcastRemoteChannelMessage(...)`.

This provides staff with a complete view of all channel messages, local and cross-server.

---

## 8. General channel commands (Lamp)

We implement `ChannelCommands` for management:

- `/channel list`
  - Shows all channel definitions with:
    - Display name.
    - Whether player has permission.
    - Joined/active flags.
- `/channel join <name>`
- `/channel leave <name>`
- `/channel focus <name>`
  - Sets `activeInstance`.
- `/channel who [name]`
  - Shows members of a particular instance (using identifiers):
    - For channels with identifiers, might show multiple instance groups, e.g. per world.

Implementation:

- Class `ChannelCommands` using Lamp’s `@Command`, `@Subcommand`.
- Inject `ChannelService`, [MessageFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:22:0-356:1), [ConfigManager](./PaperMC/src/main/kotlin/bruh/zchat/paper/config/ConfigManager.kt:10:0-144:1).
- Commands run synchronously; they only talk to in-memory `ChannelService`.
- Messages formatted via [MessageFormattingService](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:22:0-356:1) (MiniMessage).

---

## 9. Files to add / modify (no implementation yet)

**New:**

- `PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChannelService.kt`
- `PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChannelFormattingService.kt`
- `PaperMC/src/main/kotlin/bruh/zchat/paper/listeners/ChannelCommandListener.kt`
- `PaperMC/src/main/kotlin/bruh/zchat/paper/commands/ChannelCommands.kt`

**Modified:**

- `PaperMC/src/main/kotlin/bruh/zchat/paper/config/ChannelsConfig.kt`
  - Make sure we document identifier semantics and channel placeholders.
- `Config` / [ConfigManager](./PaperMC/src/main/kotlin/bruh/zchat/paper/config/ConfigManager.kt:10:0-144:1):
  - Ensure `channels: ChannelsConfig` is wired.
- `PaperMC/src/main/kotlin/bruh/zchat/paper/listeners/ChatMessageListener.kt`
  - Implement `viewers()` filtering in `onAsyncChatEarly` per routing rules.
  - Implement `ChannelFormattingService` integration in `onAsyncChatLate` for `cacheFormats` on/off.
- [PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChatFormattingService.kt](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/ChatFormattingService.kt:0:0-0:0) / [MessageFormattingService.kt](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/MessageFormattingService.kt:0:0-0:0)
  - Provide hooks for channel formatting (either via a new service or by composition).
- `PaperMC/src/main/kotlin/bruh/zchat/paper/services/CrossServerMessageBusService.kt`
  - Add `CHANNEL_CHAT`, `channelName`, `channelIdentifier`, `sendChannelMessage`, and receiver side handling.
- [PaperMC/src/main/kotlin/bruh/zchat/paper/services/SocialSpyService.kt](./PaperMC/src/main/kotlin/bruh/zchat/paper/services/SocialSpyService.kt:0:0-0:0)
  - Add channel spy methods and config integration.
- [PaperMC/src/main/kotlin/bruh/zchat/paper/PaperMC.kt](./PaperMC/src/main/kotlin/bruh/zchat/paper/PaperMC.kt:0:0-0:0)
  - Wire `ChannelService`, `ChannelFormattingService`, `ChannelCommandListener`, and `ChannelCommands`.
- `ScheduledTaskService`
  - Add per-player identifier refresh tasks based on `identifierRefreshTicks`.