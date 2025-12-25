package bruh.zchat.paper.listeners

import bruh.zchat.paper.utils.ChatInventoryHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent

/**
 * Listener to protect read-only inventory views created by inventory placeholders
 */
class InventoryProtectionListener : Listener {
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // Check if this is a read-only inventory view (contains player name and inventory type)
        if (event.inventory.holder is ChatInventoryHolder || event.clickedInventory?.holder is ChatInventoryHolder ||
            isReadOnlyInventory(event.view.title())) {
            event.isCancelled = true
            event.whoClicked.sendMessage(
                Component.text("This is a read-only inventory view.")
                    .color(NamedTextColor.YELLOW)
            )
        }
    }

    @EventHandler
    fun onInventoryPickup(event: InventoryPickupItemEvent) {
        val title = event.inventory.holder

        // Check if this is a read-only inventory view (contains player name and inventory type)
        if (title is ChatInventoryHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        // Check if this is a read-only inventory view (contains player name and inventory type)
        if (event.source.holder is ChatInventoryHolder || event.destination.holder is ChatInventoryHolder) {
            event.isCancelled = true
        }
    }


    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = event.view.title()
        
        // Check if this is a read-only inventory view
        if (isReadOnlyInventory(title) || event.inventory.holder is ChatInventoryHolder) {
            event.isCancelled = true
            event.whoClicked.sendMessage(
                Component.text("This is a read-only inventory view.")
                    .color(NamedTextColor.YELLOW)
            )
        }
    }
    
    /**
     * Check if an inventory title indicates it's a read-only view
     */
    private fun isReadOnlyInventory(title: Component): Boolean {
        val titleText = Component.text().append(title).build().toString()
        return titleText.contains("'s Inventory View") ||
               titleText.contains("'s Ender Chest View") ||
               titleText.contains("'s Armor View") ||
               titleText.contains("'s Hand View")
    }
}