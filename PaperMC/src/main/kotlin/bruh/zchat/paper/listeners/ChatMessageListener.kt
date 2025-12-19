package bruh.zchat.paper.listeners

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.services.*
import bruh.zchat.paper.swearfilter.SwearFilterService
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

class ChatMessageListener(
    private val configManager: ConfigManager,
    private val chatFormattingService: ChatFormattingService,
    private val chatToggleService: ChatToggleService,
    private val messageFormattingService: MessageFormattingService,
    private val chatInventoryPlaceholderService: ChatInventoryPlaceholderService,
    private val swearFilterService: SwearFilterService
) : Listener {

    private val logger = LoggerFactory.getLogger(ChatMessageListener::class.java)
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onAsyncChatEarly(event: AsyncChatEvent) {
        // Check if player can send chat messages
        if (!chatToggleService.canSendChat(event.player)) {
            event.isCancelled = true
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.disabled_self", event.player))
            return
        }

        val plainMessage = plainTextSerializer.serialize(event.message())
        if (swearFilterService.checkMessage(event.player, plainMessage)) {
            event.isCancelled = true
            return
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

            if (configManager.config.chat.cacheFormats) {
                val plainMessage = plainTextSerializer.serialize(event.message())
                // Process inventory placeholders in the raw message first
                val messageComponent = chatInventoryPlaceholderService.processRawMessage(player, plainMessage)

                // Then apply normal chat formatting with the processed component
                val formattedMessage = chatFormattingService.formatMessageWithComponent(player, messageComponent)

                // Apply a renderer that just returns the pre-formatted message
                event.renderer { source, sourceDisplayName, message, viewer ->
                    formattedMessage
                }
            } else {
                event.renderer { source, sourceDisplayName, message, viewer ->
                    val plainMessage = plainTextSerializer.serialize(event.message())
                    // Process inventory placeholders in the raw message first
                    val messageComponent = chatInventoryPlaceholderService.processRawMessage(source, plainMessage)

                    // Then apply normal chat formatting with the processed component
                    val formattedMessage =
                        chatFormattingService.formatMessageWithComponent(source, messageComponent, false)
                    formattedMessage
                }
            }
        } catch (e: ChatCooldownException) {
            event.player.sendMessage(
                messageFormattingService.getConfigMessage(
                    "chat.cooldown", event.player,
                    mapOf("time" to e.message!!)
                )
            )
            event.isCancelled = true
        } catch (e: Exception) {
            logger.error("Error formatting chat message for player ${event.player.name}", e)
            event.player.sendMessage(messageFormattingService.getConfigMessage("chat.formatting_error", event.player))
            event.isCancelled = true
        }
    }
}
