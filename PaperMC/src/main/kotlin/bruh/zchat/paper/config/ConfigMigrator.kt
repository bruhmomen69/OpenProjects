package bruh.zchat.paper.config

import org.slf4j.LoggerFactory

class ConfigMigrator {
    private val logger = LoggerFactory.getLogger(ConfigMigrator::class.java)

    /**
     * Migrates a LegacyConfig instance into the new split configuration structure.
     * Returns a Triple containing the new Config, MessagesConfig, and StorageConfig.
     */
    fun migrate(legacy: LegacyConfig): Triple<Config, MessagesConfig, StorageConfig> {
        logger.info("Migrating legacy configuration to new split-file structure...")

        // 1. Migrate Main Config (Feature Toggles and structural settings)
        val config = Config(
            chatFormat = ChatFormatConfig(
                enableGroupFormats = legacy.chatFormat.enableGroupFormats,
                enableWorldFormats = legacy.chatFormat.enableWorldFormats,
                formatPriority = legacy.chatFormat.formatPriority,
                enableRankedFormats = legacy.chatFormat.enableRankedFormats,
                rankedFormatPriority = legacy.chatFormat.rankedFormatPriority,
                enableHoverMessages = legacy.chatFormat.enableHoverMessages,
                enableClickActions = legacy.chatFormat.enableClickActions,
                applyInteractiveToEntireMessage = legacy.chatFormat.applyInteractiveToEntireMessage
            ),
            placeholders = PlaceholderConfig(
                enableBuiltinPlaceholders = legacy.placeholders.enableBuiltinPlaceholders,
                customPlaceholders = legacy.placeholders.customPlaceholders,
                enablePlaceholderAPI = legacy.placeholders.enablePlaceholderAPI,
                placeholderAPITimeout = legacy.placeholders.placeholderAPITimeout
            ),
            permissions = PermissionConfig(
                usePermissionBasedFormats = legacy.permissions.usePermissionBasedFormats,
                formatPermissionPrefix = legacy.permissions.formatPermissionPrefix,
                colorPermission = legacy.permissions.colorPermission,
                formattingPermission = legacy.permissions.formattingPermission,
                urlPermission = legacy.permissions.urlPermission,
                mentionPermission = legacy.permissions.mentionPermission,
                inventoryPlaceholderPermission = legacy.permissions.inventoryPlaceholderPermission,
                swearFilterBypassPermission = legacy.permissions.swearFilterBypassPermission
            ),
            chat = ChatConfig(
                enableFormatting = legacy.chat.enableFormatting,
                enableColorCodes = legacy.chat.enableColorCodes,
                enableTextFormatting = legacy.chat.enableTextFormatting,
                enableUrls = legacy.chat.enableUrls,
                enableMentions = legacy.chat.enableMentions,
                enableCooldown = legacy.chat.enableCooldown,
                cooldownSeconds = legacy.chat.cooldownSeconds,
                enableLogging = legacy.chat.enableLogging,
                cacheFormats = legacy.chat.cacheFormats
            ),
            joinLeave = JoinLeaveConfig(
                enableJoin = legacy.joinLeave.enableJoin,
                enableJoinHover = legacy.joinLeave.enableJoinHover,
                enableLeave = legacy.joinLeave.enableLeave,
                enableLeaveHover = legacy.joinLeave.enableLeaveHover
            ),
            death = DeathConfig(
                enabled = legacy.death.enabled,
                disabled = legacy.death.disabled,
                enableHover = legacy.death.enableHover
            ),
            advancement = AdvancementConfig(
                enabled = legacy.advancement.enabled,
                enableHover = legacy.advancement.enableHover
            ),
            privateMessages = PrivateMessageConfig(
                enablePrivateMessages = legacy.privateMessages.enablePrivateMessages,
                enableMessageCooldown = legacy.privateMessages.enableMessageCooldown,
                messageCooldownSeconds = legacy.privateMessages.messageCooldownSeconds,
                enableMessageLogging = legacy.privateMessages.enableMessageLogging,
                allowFormattingInMessages = legacy.privateMessages.allowFormattingInMessages
            ),
            chatToggle = ChatToggleConfig(
                enableChatToggle = legacy.chatToggle.enableChatToggle,
                enableMessageToggle = legacy.chatToggle.enableMessageToggle,
                persistToggleState = legacy.chatToggle.persistToggleState,
                linkChatAndMessages = legacy.chatToggle.linkChatAndMessages
            ),
            socialSpy = SocialSpyConfig(
                enableSocialSpy = legacy.socialSpy.enableSocialSpy,
                enableCommandSpy = legacy.socialSpy.enableCommandSpy,
                ignoreModerators = legacy.socialSpy.ignoreModerators,
                logToConsole = legacy.socialSpy.logToConsole,
                persistSocialSpyState = legacy.socialSpy.persistSocialSpyState
            ),
            inventoryPlaceholders = InventoryPlaceholderConfig(
                enabled = legacy.inventoryPlaceholders.enabled,
                enableInventoryPlaceholder = legacy.inventoryPlaceholders.enableInventoryPlaceholder,
                enableEnderPlaceholder = legacy.inventoryPlaceholders.enableEnderPlaceholder,
                enableArmorPlaceholder = legacy.inventoryPlaceholders.enableArmorPlaceholder,
                enableHandPlaceholder = legacy.inventoryPlaceholders.enableHandPlaceholder,
                enablePositionPlaceholder = legacy.inventoryPlaceholders.enablePositionPlaceholder,
                enableHealthPlaceholder = legacy.inventoryPlaceholders.enableHealthPlaceholder,
                snapshotRetentionMinutes = legacy.inventoryPlaceholders.snapshotRetentionMinutes,
                maxPreviewItems = legacy.inventoryPlaceholders.maxPreviewItems
            ),
            blocks = BlockConfig(
                enableBlockSystem = legacy.blocks.enableBlockSystem,
                maxBlocksPerPlayer = legacy.blocks.maxBlocksPerPlayer,
                persistBlockLists = legacy.blocks.persistBlockLists,
                blockSelf = legacy.blocks.blockSelf,
                logBlocks = legacy.blocks.logBlocks
            ),
            swearFilter = SwearFilterConfig(
                enabled = legacy.swearFilter.enabled,
                filterGroups = legacy.swearFilter.filterGroups.map { g ->
                    FilterGroup(
                        name = g.name,
                        type = g.type,
                        distance = g.distance,
                        filters = g.filters,
                        punishments = g.punishments
                    )
                },
                alerts = AlertConfig(
                    enableAlerts = legacy.swearFilter.alerts.enableAlerts,
                    alertPermission = legacy.swearFilter.alerts.alertPermission,
                    enableByDefault = legacy.swearFilter.alerts.enableByDefault,
                    showAutoEnabledMessage = legacy.swearFilter.alerts.showAutoEnabledMessage,
                    logToConsole = legacy.swearFilter.alerts.logToConsole,
                    alertGroups = legacy.swearFilter.alerts.alertGroups,
                    minimumSeverity = legacy.swearFilter.alerts.minimumSeverity,
                    alertCooldownSeconds = legacy.swearFilter.alerts.alertCooldownSeconds,
                    onlyBeforePunishment = legacy.swearFilter.alerts.onlyBeforePunishment,
                    maxAlertsPerMinute = legacy.swearFilter.alerts.maxAlertsPerMinute
                )
            )
        )

        // 2. Migrate Messages Config (Extracted formats and strings)
        val messages = MessagesConfig(
            chatFormat = ChatFormatMessages(
                defaultFormat = legacy.chatFormat.defaultFormat,
                groupFormats = legacy.chatFormat.groupFormats,
                worldFormats = legacy.chatFormat.worldFormats,
                hoverMessages = legacy.chatFormat.hoverMessages,
                clickActions = legacy.chatFormat.clickActions
            ),
            joinLeave = JoinLeaveMessages(
                joinMessage = legacy.joinLeave.joinMessage,
                joinHoverMessage = legacy.joinLeave.joinHoverMessage,
                joinClickAction = legacy.joinLeave.joinClickAction,
                leaveMessage = legacy.joinLeave.leaveMessage,
                leaveHoverMessage = legacy.joinLeave.leaveHoverMessage,
                leaveClickAction = legacy.joinLeave.leaveClickAction
            ),
            death = DeathMessages(
                messages = legacy.death.messages,
                defaultMessage = legacy.death.defaultMessage,
                hoverMessage = legacy.death.hoverMessage,
                clickAction = legacy.death.clickAction
            ),
            advancement = AdvancementMessages(
                messages = legacy.advancement.messages,
                defaultMessage = legacy.advancement.defaultMessage,
                hoverMessage = legacy.advancement.hoverMessage,
                clickAction = legacy.advancement.clickAction
            ),
            privateMessages = PrivateMessageMessages(
                senderFormat = legacy.privateMessages.senderFormat,
                recipientFormat = legacy.privateMessages.recipientFormat,
                playerNotFound = legacy.privateMessages.playerNotFoundMessage,
                targetMessagesDisabled = legacy.privateMessages.messagesDisabledMessage,
                systemDisabled = legacy.messages.privateMessages.systemDisabled,
                cooldown = legacy.messages.privateMessages.cooldown,
                selfMessage = legacy.messages.privateMessages.selfMessage,
                noReplyTarget = legacy.messages.privateMessages.noReplyTarget,
                replyTargetOffline = legacy.messages.privateMessages.replyTargetOffline,
                deliveryFailed = legacy.messages.privateMessages.deliveryFailed
            ),
            chat = ChatMessages(
                disabledSelf = legacy.messages.chat.disabledSelf,
                formattingError = legacy.messages.chat.formattingError,
                cooldown = legacy.messages.chat.cooldown,
                systemDisabled = legacy.messages.chatToggle.systemDisabled,
                messageToggleDisabled = legacy.messages.chatToggle.messageToggleDisabled,
                chatEnabled = legacy.chatToggle.chatEnabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.chatToggle.chatEnabled,
                chatDisabled = legacy.chatToggle.chatDisabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.chatToggle.chatDisabled,
                messagesEnabled = legacy.chatToggle.messagesEnabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.chatToggle.messagesEnabled,
                messagesDisabled = legacy.chatToggle.messagesDisabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.chatToggle.messagesDisabled
            ),
            socialSpy = SocialSpyMessages(
                socialSpyFormat = legacy.socialSpy.socialSpyFormat,
                commandSpyFormat = legacy.socialSpy.commandSpyFormat,
                enabled = legacy.socialSpy.socialSpyEnabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.socialSpy.enabled,
                disabled = legacy.socialSpy.socialSpyDisabledMessage.takeIf { it.isNotEmpty() } ?: legacy.messages.socialSpy.disabled,
                systemDisabled = legacy.messages.socialSpy.systemDisabled,
                noPermission = legacy.messages.socialSpy.noPermission
            ),
            inventoryPlaceholders = InventoryPlaceholderMessages(
                inventoryDisplayFormat = legacy.inventoryPlaceholders.inventoryDisplayFormat,
                positionDisplayFormat = legacy.inventoryPlaceholders.positionDisplayFormat,
                healthDisplayFormat = legacy.inventoryPlaceholders.healthDisplayFormat,
                inventoryHoverFormat = legacy.inventoryPlaceholders.inventoryHoverFormat,
                positionHoverFormat = legacy.inventoryPlaceholders.positionHoverFormat,
                healthHoverFormat = legacy.inventoryPlaceholders.healthHoverFormat,
                emptyInventoryText = legacy.inventoryPlaceholders.emptyInventoryText,
                itemPreviewFormat = legacy.inventoryPlaceholders.itemPreviewFormat,
                moreItemsText = legacy.inventoryPlaceholders.moreItemsText,
                noPermission = legacy.messages.inventoryPlaceholders.noPermission,
                disabled = legacy.messages.inventoryPlaceholders.disabled,
                placeholderDisabled = legacy.messages.inventoryPlaceholders.placeholderDisabled,
                snapshotNotFound = legacy.messages.inventoryPlaceholders.snapshotNotFound,
                snapshotCreationFailed = legacy.messages.inventoryPlaceholders.snapshotCreationFailed,
                viewFailed = legacy.messages.inventoryPlaceholders.viewFailed,
                readOnlyInventory = legacy.messages.inventoryPlaceholders.readOnlyInventory,
                positionUnavailable = legacy.messages.inventoryPlaceholders.positionUnavailable,
                healthUnavailable = legacy.messages.inventoryPlaceholders.healthUnavailable
            ),
            blocks = BlockMessages(
                systemDisabled = legacy.messages.blocks.systemDisabled,
                blocked = legacy.messages.blocks.blocked,
                unblocked = legacy.messages.blocks.unblocked,
                alreadyBlocked = legacy.messages.blocks.alreadyBlocked,
                notBlocked = legacy.messages.blocks.notBlocked,
                blockListEmpty = legacy.messages.blocks.blockListEmpty,
                blockList = legacy.messages.blocks.blockList,
                targetBlockedYou = legacy.messages.blocks.targetBlockedYou,
                maxBlocksReached = legacy.messages.blocks.maxBlocksReached
            ),
            swearFilter = SwearFilterMessages(
                alertMessage = legacy.swearFilter.alerts.alertMessage,
                consoleAlertMessage = legacy.swearFilter.alerts.consoleAlertMessage,
                alertsDisabled = legacy.messages.alerts.systemDisabled,
                alertsNoPermission = legacy.messages.alerts.noPermission,
                alertsEnabled = legacy.messages.alerts.enabled,
                alertsDisabledPersonal = legacy.messages.alerts.disabled,
                alertsAutoEnabled = legacy.messages.alerts.autoEnabled
            ),
            commands = CommandMessages(
                playerOnly = legacy.messages.commands.playerOnly,
                noPermission = legacy.messages.commands.noPermission,
                reloadSuccess = legacy.messages.commands.reloadSuccess,
                reloadFailed = legacy.messages.commands.reloadFailed,
                playerNotFound = legacy.messages.commands.playerNotFound,
                featureEnabled = legacy.messages.commands.featureEnabled,
                featureDisabled = legacy.messages.commands.featureDisabled,
                updateFailed = legacy.messages.commands.updateFailed,
                formatUpdated = legacy.messages.commands.formatUpdated
            ),
            system = SystemMessages(
                error = legacy.messages.system.error,
                success = legacy.messages.system.success,
                dataCleared = legacy.messages.system.dataCleared,
                invalidUsage = legacy.messages.system.invalidUsage
            )
        )

        // 3. Migrate Storage Config (Database and Cross-Server)
        val storage = StorageConfig(
            database = DatabaseConfig(
                type = legacy.database.type,
                host = legacy.database.host,
                port = legacy.database.port,
                database = legacy.database.database,
                username = legacy.database.username,
                password = legacy.database.password,
                sqliteFile = legacy.database.sqliteFile,
                poolSize = legacy.database.poolSize,
                connectionTimeout = legacy.database.connectionTimeout,
                maxLifetime = legacy.database.maxLifetime,
                leakDetectionThreshold = legacy.database.leakDetectionThreshold,
                autoMigrate = legacy.database.autoMigrate,
                enableArchive = legacy.database.enableArchive,
                dataRetentionDays = legacy.database.dataRetentionDays,
                maintenanceTime = legacy.database.maintenanceTime,
                enablePerformanceMonitoring = legacy.database.enablePerformanceMonitoring
            ),
            crossServerMessaging = CrossServerMessagingConfig(
                enabled = legacy.crossServerMessaging.enabled,
                backend = legacy.crossServerMessaging.backend,
                redis = RedisCrossServerMessagingConfig(
                    uri = legacy.crossServerMessaging.redis.uri,
                    channelPrefix = legacy.crossServerMessaging.redis.channelPrefix,
                    clientName = legacy.crossServerMessaging.redis.clientName,
                    connectTimeoutMillis = legacy.crossServerMessaging.redis.connectTimeoutMillis
                ),
                pollIntervalMillis = legacy.crossServerMessaging.pollIntervalMillis,
                heartbeatIntervalSeconds = legacy.crossServerMessaging.heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds = legacy.crossServerMessaging.heartbeatTimeoutSeconds,
                claimTimeoutSeconds = legacy.crossServerMessaging.claimTimeoutSeconds,
                pollBatchSize = legacy.crossServerMessaging.pollBatchSize,
                messageRetentionDays = legacy.crossServerMessaging.messageRetentionDays
            )
        )

        logger.info("Migration successful!")
        return Triple(config, messages, storage)
    }
}
