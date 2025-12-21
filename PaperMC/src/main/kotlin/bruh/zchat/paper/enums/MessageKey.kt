package bruh.zchat.paper.enums

import bruh.zchat.paper.config.MessagesConfig
import java.util.function.Function

enum class MessageKey(
    val key: String, 
    val category: String,
    private val messageResolver: Function<MessagesConfig, String>
) {
    // Command messages
    COMMANDS_PLAYER_ONLY("commands.player_only", "commands", Function { it.commands.playerOnly }),
    COMMANDS_NO_PERMISSION("commands.no_permission", "commands", Function { it.commands.noPermission }),
    COMMANDS_RELOAD_SUCCESS("commands.reload_success", "commands", Function { it.commands.reloadSuccess }),
    COMMANDS_RELOAD_FAILED("commands.reload_failed", "commands", Function { it.commands.reloadFailed }),
    COMMANDS_PLAYER_NOT_FOUND("commands.player_not_found", "commands", Function { it.commands.playerNotFound }),
    COMMANDS_FEATURE_ENABLED("commands.feature_enabled", "commands", Function { it.commands.featureEnabled }),
    COMMANDS_FEATURE_DISABLED("commands.feature_disabled", "commands", Function { it.commands.featureDisabled }),
    COMMANDS_UPDATE_FAILED("commands.update_failed", "commands", Function { it.commands.updateFailed }),
    COMMANDS_FORMAT_UPDATED("commands.format_updated", "commands", Function { it.commands.formatUpdated }),
    
    // Private message messages
    PRIVATE_MESSAGES_SYSTEM_DISABLED("private_messages.system_disabled", "private_messages", Function { it.privateMessages.systemDisabled }),
    PRIVATE_MESSAGES_COOLDOWN("private_messages.cooldown", "private_messages", Function { it.privateMessages.cooldown }),
    PRIVATE_MESSAGES_PLAYER_NOT_FOUND("private_messages.player_not_found", "private_messages", Function { it.privateMessages.playerNotFound }),
    PRIVATE_MESSAGES_SELF_MESSAGE("private_messages.self_message", "private_messages", Function { it.privateMessages.selfMessage }),
    PRIVATE_MESSAGES_TARGET_MESSAGES_DISABLED("private_messages.target_messages_disabled", "private_messages", Function { it.privateMessages.targetMessagesDisabled }),
    PRIVATE_MESSAGES_NO_REPLY_TARGET("private_messages.no_reply_target", "private_messages", Function { it.privateMessages.noReplyTarget }),
    PRIVATE_MESSAGES_REPLY_TARGET_OFFLINE("private_messages.reply_target_offline", "private_messages", Function { it.privateMessages.replyTargetOffline }),
    PRIVATE_MESSAGES_DELIVERY_FAILED("private_messages.delivery_failed", "private_messages", Function { it.privateMessages.deliveryFailed }),
    
    // Chat messages
    CHAT_DISABLED_SELF("chat.disabled_self", "chat", Function { it.chat.disabledSelf }),
    CHAT_FORMATTING_ERROR("chat.formatting_error", "chat", Function { it.chat.formattingError }),
    CHAT_COOLDOWN("chat.cooldown", "chat", Function { it.chat.cooldown }),
    
    // Chat toggle messages
    CHAT_TOGGLE_SYSTEM_DISABLED("chat_toggle.system_disabled", "chat_toggle", Function { it.chat.systemDisabled }),
    CHAT_TOGGLE_MESSAGE_TOGGLE_DISABLED("chat_toggle.message_toggle_disabled", "chat_toggle", Function { it.chat.messageToggleDisabled }),
    CHAT_TOGGLE_CHAT_ENABLED("chat_toggle.chat_enabled", "chat_toggle", Function { it.chat.chatEnabled }),
    CHAT_TOGGLE_CHAT_DISABLED("chat_toggle.chat_disabled", "chat_toggle", Function { it.chat.chatDisabled }),
    CHAT_TOGGLE_MESSAGES_ENABLED("chat_toggle.messages_enabled", "chat_toggle", Function { it.chat.messagesEnabled }),
    CHAT_TOGGLE_MESSAGES_DISABLED("chat_toggle.messages_disabled", "chat_toggle", Function { it.chat.messagesDisabled }),
    
    // Social spy messages
    SOCIAL_SPY_SYSTEM_DISABLED("social_spy.system_disabled", "social_spy", Function { it.socialSpy.systemDisabled }),
    SOCIAL_SPY_NO_PERMISSION("social_spy.no_permission", "social_spy", Function { it.socialSpy.noPermission }),
    SOCIAL_SPY_ENABLED("social_spy.enabled", "social_spy", Function { it.socialSpy.enabled }),
    SOCIAL_SPY_DISABLED("social_spy.disabled", "social_spy", Function { it.socialSpy.disabled }),
    
    // Block system messages
    BLOCKS_SYSTEM_DISABLED("blocks.system_disabled", "blocks", Function { it.blocks.systemDisabled }),
    BLOCKS_BLOCKED("blocks.blocked", "blocks", Function { it.blocks.blocked }),
    BLOCKS_UNBLOCKED("blocks.unblocked", "blocks", Function { it.blocks.unblocked }),
    BLOCKS_ALREADY_BLOCKED("blocks.already_blocked", "blocks", Function { it.blocks.alreadyBlocked }),
    BLOCKS_NOT_BLOCKED("blocks.not_blocked", "blocks", Function { it.blocks.notBlocked }),
    BLOCKS_BLOCK_LIST_EMPTY("blocks.block_list_empty", "blocks", Function { it.blocks.blockListEmpty }),
    BLOCKS_BLOCK_LIST("blocks.block_list", "blocks", Function { it.blocks.blockList }),
    BLOCKS_TARGET_BLOCKED_YOU("blocks.target_blocked_you", "blocks", Function { it.blocks.targetBlockedYou }),
    BLOCKS_MAX_BLOCKS_REACHED("blocks.max_blocks_reached", "blocks", Function { it.blocks.maxBlocksReached }),
    
    // Alert messages
    ALERTS_SYSTEM_DISABLED("alerts.system_disabled", "alerts", Function { it.swearFilter.alertsDisabled }),
    ALERTS_NO_PERMISSION("alerts.no_permission", "alerts", Function { it.swearFilter.alertsNoPermission }),
    ALERTS_ENABLED("alerts.enabled", "alerts", Function { it.swearFilter.alertsEnabled }),
    ALERTS_DISABLED("alerts.disabled", "alerts", Function { it.swearFilter.alertsDisabledPersonal }),
    ALERTS_AUTO_ENABLED("alerts.auto_enabled", "alerts", Function { it.swearFilter.alertsAutoEnabled }),
    
    // Swear filter messages
    SWEAR_FILTER_BLOCKED_MESSAGE("swearFilter.blockedMessage", "swear_filter", Function { it.swearFilter.blockedMessage }),
    
    // Channel messages
    CHANNELS_SYSTEM_DISABLED("channels.system_disabled", "channels", Function { it.channels.systemDisabled }),
    CHANNELS_CHANNEL_NOT_FOUND("channels.channel_not_found", "channels", Function { it.channels.channelNotFound }),
    CHANNELS_NO_PERMISSION("channels.no_permission", "channels", Function { it.channels.noPermission }),
    CHANNELS_NO_PERMISSION_CHANNEL("channels.no_permission_channel", "channels", Function { it.channels.noPermissionChannel }),
    CHANNELS_IDENTIFIER_MISSING("channels.identifier_missing", "channels", Function { it.channels.identifierMissing }),
    CHANNELS_CHANNEL_JOINED("channels.channel_joined", "channels", Function { it.channels.channelJoined }),
    CHANNELS_CHANNEL_JOIN_FAILED("channels.channel_join_failed", "channels", Function { it.channels.channelJoinFailed }),
    CHANNELS_CHANNEL_LEFT("channels.channel_left", "channels", Function { it.channels.channelLeft }),
    CHANNELS_CHANNEL_LEAVE_FAILED("channels.channel_leave_failed", "channels", Function { it.channels.channelLeaveFailed }),
    CHANNELS_NOT_IN_CHANNEL("channels.not_in_channel", "channels", Function { it.channels.notInChannel }),
    CHANNELS_ACTIVE_CHANNEL_SET("channels.active_channel_set", "channels", Function { it.channels.activeChannelSet }),
    CHANNELS_NO_ACTIVE_INSTANCES("channels.no_active_instances", "channels", Function { it.channels.noActiveInstances }),
    CHANNELS_MEMBERS_LIST_HEADER("channels.members_list_header", "channels", Function { it.channels.membersListHeader }),
    CHANNELS_MEMBERS_LIST_INSTANCE("channels.members_list_instance", "channels", Function { it.channels.membersListInstance }),
    CHANNELS_NO_MEMBERS("channels.no_members", "channels", Function { it.channels.noMembers }),
    CHANNELS_LIST_HEADER("channels.list_header", "channels", Function { it.channels.listHeader }),
    CHANNELS_LIST_FORMAT("channels.list_format", "channels", Function { it.channels.listFormat }),
    CHANNELS_FOCUS_NOT_JOINED("channels.focus_not_joined", "channels", Function { it.channels.focusNotJoined }),
    CHANNELS_CHANNEL_ONLY_ENABLED("channels.channel_only_enabled", "channels", Function { it.channels.channelOnlyEnabled }),
    CHANNELS_CHANNEL_ONLY_DISABLED("channels.channel_only_disabled", "channels", Function { it.channels.channelOnlyDisabled }),
    CHANNELS_CHANNEL_ONLY_NO_CHANNEL("channels.channel_only_no_channel", "channels", Function { it.channels.channelOnlyNoChannel }),
    
    // System messages
    SYSTEM_ERROR("system.error", "system", Function { it.system.error }),
    SYSTEM_SUCCESS("system.success", "system", Function { it.system.success }),
    SYSTEM_DATA_CLEARED("system.data_cleared", "system", Function { it.system.dataCleared }),
    SYSTEM_INVALID_USAGE("system.invalid_usage", "system", Function { it.system.invalidUsage });
    
    /**
     * Resolve the message text from the MessagesConfig using the lambda resolver
     */
    fun resolveMessage(messages: MessagesConfig): String {
        return messageResolver.apply(messages)
    }
    
    companion object {
        fun fromKey(key: String): MessageKey? {
            return values().find { it.key == key }
        }
        
        /**
         * Get all message keys in a specific category
         */
        fun getByCategory(category: String): List<MessageKey> {
            return values().filter { it.category == category }
        }
    }
}