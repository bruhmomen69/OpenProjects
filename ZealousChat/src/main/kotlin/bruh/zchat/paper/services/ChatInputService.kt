package bruh.zchat.paper.services

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for capturing chat input from players for use in GUI menus.
 * When a player is awaiting input, their next chat message is intercepted
 * and passed to a callback instead of being sent to chat.
 */
class ChatInputService(
    private val plugin: JavaPlugin
) : Closeable {
    
    private val pendingInputs = ConcurrentHashMap<UUID, PendingInput>()
    private val listener = ChatInputListener()
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()
    
    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }
    
    /**
     * Requests text input from a player.
     * The player's next chat message will be captured and passed to the callback.
     * 
     * @param player The player to request input from
     * @param callback Called with the input text, or null if cancelled
     * @param onCancel Optional callback when input is cancelled
     */
    fun requestInput(
        player: Player,
        callback: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        pendingInputs[player.uniqueId] = PendingInput(callback, onCancel)
    }
    
    /**
     * Cancels any pending input request for a player.
     */
    fun cancelInput(player: Player) {
        pendingInputs.remove(player.uniqueId)?.onCancel?.invoke()
    }
    
    /**
     * Checks if a player has a pending input request.
     */
    fun hasPendingInput(player: Player): Boolean {
        return pendingInputs.containsKey(player.uniqueId)
    }
    
    override fun close() {
        HandlerList.unregisterAll(listener)
        pendingInputs.clear()
    }
    
    private data class PendingInput(
        val callback: (String) -> Unit,
        val onCancel: (() -> Unit)?
    )
    
    private inner class ChatInputListener : Listener {
        
        @EventHandler(priority = EventPriority.LOWEST)
        fun onAsyncChat(event: AsyncChatEvent) {
            val pending = pendingInputs.remove(event.player.uniqueId) ?: return
            
            // Cancel the chat event so the message doesn't go to chat
            event.isCancelled = true
            
            val message = plainTextSerializer.serialize(event.message())
            
            // Check for cancel keyword
            if (message.equals("cancel", ignoreCase = true)) {
                pending.onCancel?.invoke()
                return
            }
            
            pending.callback(message)
        }
    }
}
