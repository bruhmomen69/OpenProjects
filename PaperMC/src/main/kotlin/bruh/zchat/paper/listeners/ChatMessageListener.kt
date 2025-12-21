package bruh.zchat.paper.listeners

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.enums.MessageKey
import bruh.zchat.paper.services.*
import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.services.CrossServerMessageBusService
import bruh.zchat.paper.swearfilter.SwearFilterService
import com.github.shynixn.mccoroutine.folia.launch
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers

class ChatMessageListener(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val channelFormattingService: ChannelFormattingService,
    private val channelService: ChannelService,
    private val chatToggleService: ChatToggleService,
    private val messageFormattingService: MessageFormattingService,
    private val chatInventoryPlaceholderService: ChatInventoryPlaceholderService,
    private val swearFilterService: SwearFilterService,
    private val socialSpyService: SocialSpyService,
    private val crossServerMessageBusService: CrossServerMessageBusService,
    private val plugin: PaperMC
) : Listener {

    private val logger = LoggerFactory.getLogger(ChatMessageListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onAsyncChatEarly(event: AsyncChatEvent) {
        // Check if player can send chat messages
        if (!chatToggleService.canSendChat(event.player)) {
            event.isCancelled = true
            event.player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_DISABLED_SELF, event.player))
            return
        }

        val plainMessage = plainTextSerializer.serialize(event.message())
        if (swearFilterService.checkMessage(event.player, plainMessage)) {
            event.isCancelled = true
            return
        }

        val channelsConfig = configManager.config.channels
        val routing = if (channelsConfig.enabled) {
            channelService.peekRoutingForMessage(event.player, channelsConfig.channelOnly)
        } else {
            ChannelRouting(null, false)
        }

        event.viewers().removeIf { audience ->
            when (audience) {
                is org.bukkit.entity.Player -> {
                    if (routing.instance != null && routing.channelOnly) {
                        !channelService.isMember(audience, routing.instance)
                    } else {
                        false
                    }
                }
                else -> false // console/other => ALWAYS keep
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChatLate(event: AsyncChatEvent) {
        // Check if chat formatting is enabled
        if (!configManager.config.chat.enableFormatting) {
            return // Let vanilla handle the chat
        }

        try {
            val player = event.player

            // Apply cooldowns
            if (!configManager.config.chat.cacheFormats) {
                chatFormattingService.applyCooldown(player)
            }

            // Log the chat message if enabled
            if (configManager.config.chat.enableLogging) {
                val message = plainTextSerializer.serialize(event.message())
                logger.info("[CHAT] ${player.name}: $message")
            }

            val channelsConfig = configManager.config.channels
            val routing = if (channelsConfig.enabled) {
                channelService.consumeRoutingForMessage(player, channelsConfig.channelOnly)
            } else {
                ChannelRouting(null, false)
            }

            val definition = routing.instance?.let { channelService.getDefinitionByName(it.nameKey) }
            val plainMessage = plainTextSerializer.serialize(event.message())
            val processedStringMessage = messageFormattingService.processMessageContent(player, plainMessage)

            if (configManager.config.chat.cacheFormats) {
                // Process inventory placeholders in the raw message first
                val messageComponent = chatInventoryPlaceholderService.processRawMessage(player, plainMessage)

                val formattedMessage = if (routing.instance != null && definition != null) {
                    channelFormattingService.formatChannelMessage(
                        sender = player,
                        viewer = Audience.empty(),
                        baseMessageComponent = messageComponent,
                        routing = routing
                    )
                } else {
                    chatFormattingService.formatMessageWithComponent(player, messageComponent)
                }

                // Apply a renderer that just returns the pre-formatted message
                event.renderer { source, sourceDisplayName, message, viewer ->
                    formattedMessage
                }

                // Social spy for channel
                if (routing.instance != null) {
                    socialSpyService.broadcastChannelMessage(player, definition?.displayName ?: routing.instance.nameKey, routing.instance.identifier, processedStringMessage)
                }

                // Cross-server channel send
                if (routing.instance != null && definition?.crossServerBridge == true) {
                    plugin.launch(Dispatchers.IO) {
                        crossServerMessageBusService.sendChannelMessage(
                            senderUuid = player.uniqueId,
                            senderName = player.name,
                            instance = routing.instance,
                            processedMessage = processedStringMessage,
                            originalMessage = plainMessage
                        )
                    }
                }
            } else {
                event.renderer { source, sourceDisplayName, message, viewer ->
                    val plainMessage = plainTextSerializer.serialize(event.message())
                    // Process inventory placeholders in the raw message first
                    val messageComponent = chatInventoryPlaceholderService.processRawMessage(source, plainMessage)

                    val formattedMessage =
                        if (routing.instance != null && definition != null) {
                            channelFormattingService.formatChannelMessage(
                                sender = source,
                                viewer = viewer,
                                baseMessageComponent = messageComponent,
                                routing = routing
                            )
                        } else {
                            chatFormattingService.formatMessageWithComponent(source, messageComponent, false)
                        }
                    formattedMessage
                }

                if (routing.instance != null) {
                    socialSpyService.broadcastChannelMessage(player, definition?.displayName ?: routing.instance.nameKey, routing.instance.identifier, processedStringMessage)
                }

                if (routing.instance != null && definition?.crossServerBridge == true) {
                    plugin.launch(Dispatchers.IO) {
                        crossServerMessageBusService.sendChannelMessage(
                            senderUuid = player.uniqueId,
                            senderName = player.name,
                            instance = routing.instance,
                            processedMessage = processedStringMessage,
                            originalMessage = plainMessage
                        )
                    }
                }
            }
        } catch (e: ChatCooldownException) {
            event.player.sendMessage(
                messageFormattingService.getConfigMessage(
                    MessageKey.CHAT_COOLDOWN, event.player,
                    mapOf("time" to e.message!!)
                )
            )
            event.isCancelled = true
        } catch (e: Exception) {
            logger.error("Error formatting chat message for player ${event.player.name}", e)
            event.player.sendMessage(messageFormattingService.getConfigMessage(MessageKey.CHAT_FORMATTING_ERROR, event.player))
            event.isCancelled = true
        }
    }
}
