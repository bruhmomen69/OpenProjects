# MiniMessage Chat Plugin Refactoring Summary

## Overview
Successfully extracted the complex placeholder, URL extraction, and MiniMessage tracking system from `ChatFormattingService` into a new reusable `MessageFormattingService`. This service can now be used across all components for consistent message formatting and placeholder resolution.

## Key Changes Made

### 1. Created New MessageFormattingService
- **File**: `PaperMC/src/main/kotlin/lol/mcplugs/minimessagechatplugin/paper/services/MessageFormattingService.kt`
- **Purpose**: Centralized service for all message formatting, placeholder resolution, and MiniMessage processing
- **Features**:
  - Built-in placeholder system (player_name, world, server_name, time, etc.)
  - Legacy placeholder conversion (`{placeholder}` → `<placeholder>`)
  - URL and mention processing
  - Permission-based formatting/color stripping
  - PlaceholderAPI integration
  - Configurable message retrieval from config

### 2. Enhanced Configuration System
- **File**: `PaperMC/src/main/kotlin/lol/mcplugs/minimessagechatplugin/paper/config/Config.kt`
- **Added**: Comprehensive `MessagesConfig` with nested message categories:
  - `CommandMessagesConfig` - All command-related messages
  - `PrivateMessageMessagesConfig` - Private messaging system messages
  - `ChatMessagesConfig` - Chat system messages
  - `ChatToggleMessagesConfig` - Chat toggle system messages
  - `SocialSpyMessagesConfig` - Social spy system messages
  - `SystemMessagesConfig` - General error and system messages

### 3. Refactored Existing Services

#### ChatFormattingService
- **Removed**: Duplicate placeholder, URL, and mention processing code
- **Now uses**: MessageFormattingService for all formatting operations
- **Retained**: Chat-specific logic (cooldowns, format selection, interactive elements)

#### PrivateMessageService
- **Removed**: Duplicate message processing and formatting code
- **Now uses**: MessageFormattingService for all message operations
- **Updated**: All hardcoded messages to use configurable messages

#### ChatListener
- **Updated**: To use configurable messages via MessageFormattingService
- **Improved**: Error handling with proper message formatting

### 4. Updated Command System
- **ChatPluginCommand**: Now uses configurable messages for responses
- **All commands**: Can now leverage the centralized message system

## Placeholder System Improvements

### Legacy Placeholder Conversion
The new system automatically converts legacy `{placeholder}` syntax to MiniMessage `<placeholder>` syntax:
- `{player_name}` → `<player_name>`
- `{message}` → `<message>`
- `{sender}` → `<sender>`
- `{recipient}` → `<recipient>`

### Built-in Placeholders
All services now have access to:
- **Player Info**: `<player_name>`, `<player_displayname>`, `<player_uuid>`
- **World Info**: `<world>`, `<world_displayname>`
- **Server Info**: `<server_name>`, `<server_version>`, `<server_motd>`
- **Online Players**: `<online_players>`, `<max_players>`
- **Time/Date**: `<time>`, `<date>`, `<datetime>`

## Configuration Examples

### Example Configurable Messages
```hocon
messages {
  commands {
    player_only = "<red>This command can only be used by players!</red>"
    reload_success = "<green>Configuration reloaded successfully!</green>"
    reload_failed = "<red>Failed to reload configuration. Check console for errors.</red>"
  }
  
  private_messages {
    system_disabled = "<red>Private messages are currently disabled.</red>"
    cooldown = "<red>You must wait <time> seconds before sending another message!</red>"
    player_not_found = "<red>Player '<player>' is not online!</red>"
  }
  
  chat {
    disabled_self = "<red>You have chat disabled! Use /chatplugin toggle chat to enable it.</red>"
    formatting_error = "<red>An error occurred while formatting your message.</red>"
  }
}
```

### Example Usage in Code
```kotlin
// Before (hardcoded)
player.sendMessage(miniMessage.deserialize("<red>Player not found!</red>"))

// After (configurable)
player.sendMessage(messageFormattingService.getConfigMessage(
    "commands.player_not_found", 
    player, 
    mapOf("player" to playerName)
))
```

## Benefits Achieved

### 1. Code Reusability
- Single source of truth for message formatting
- Eliminates duplicate placeholder processing code
- Consistent formatting across all components

### 2. Maintainability
- Centralized message configuration
- Easy to update messages without code changes
- Consistent placeholder syntax

### 3. Flexibility
- All messages are now configurable
- Support for both legacy and MiniMessage placeholder syntax
- Easy to extend with new placeholders

### 4. Performance
- Efficient placeholder caching
- Reduced code duplication
- Optimized TagResolver creation

## Current Status

✅ **Completed**:
- MessageFormattingService implementation
- Config system enhancement with MessagesConfig
- ChatFormattingService refactoring
- PrivateMessageService refactoring
- ChatToggleService refactoring
- SocialSpyService refactoring
- ChatListener updates
- Command system updates (ChatPluginCommand, PrivateMessageCommands)
- All services now use configurable messages
- Build verification (all tests pass)
- Complete migration from hardcoded messages

## Recommendations for Next Steps

### 1. ✅ Complete Message Migration - DONE
All services now use configurable messages:
- ✅ `ChatToggleService` - Updated
- ✅ `SocialSpyService` - Updated  
- ✅ All command classes - Updated

### 2. Enhanced Placeholder System
Consider adding:
- Custom placeholder registration API
- Dynamic placeholder resolution
- Conditional placeholder logic

### 3. Message Validation
Add validation for:
- MiniMessage syntax validation
- Placeholder existence checking
- Configuration completeness verification

### 4. Documentation Updates
- Update README.md with new configuration options
- Add examples for custom message configuration
- Document the new placeholder system

### 5. Testing
- Add unit tests for MessageFormattingService
- Integration tests for placeholder resolution
- Configuration validation tests

## Migration Guide for Developers

### Using the New MessageFormattingService

```kotlin
// Inject the service
class YourService(
    private val messageFormattingService: MessageFormattingService
) {
    
    // Format a message with placeholders
    fun sendFormattedMessage(player: Player, messageKey: String, placeholders: Map<String, String> = emptyMap()) {
        val message = messageFormattingService.getConfigMessage(messageKey, player, placeholders)
        player.sendMessage(message)
    }
    
    // Custom formatting
    fun formatCustomMessage(format: String, player: Player) {
        val message = messageFormattingService.formatMessage(
            format = format,
            player = player,
            processUrls = true,
            processMentions = true,
            allowColors = true,
            allowFormatting = true
        )
        player.sendMessage(message)
    }
}
```

### Adding New Configurable Messages

1. Add to appropriate config class in `Config.kt`
2. Update `getMessageByKey()` in `MessageFormattingService`
3. Use via `messageFormattingService.getConfigMessage()`

This refactoring significantly improves the codebase's maintainability, flexibility, and consistency while providing a solid foundation for future enhancements.