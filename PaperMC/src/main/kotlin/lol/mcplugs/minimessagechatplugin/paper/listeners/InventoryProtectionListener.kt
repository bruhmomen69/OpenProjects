package lol.mcplugs.minimessagechatplugin.paper.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Listener to protect read-only inventory views created by inventory placeholders
 */
class InventoryProtectionListener : Listener {
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()
        
        // Check if this is a read-only inventory view (contains player name and inventory type)
        if (isReadOnlyInventory(title)) {
            event.isCancelled = true
            event.whoClicked.sendMessage(
                Component.text("This is a read-only inventory view.")
                    .color(NamedTextColor.YELLOW)
            )
        }
    }
    
    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = event.view.title()
        
        // Check if this is a read-only inventory view
        if (isReadOnlyInventory(title)) {
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
        return titleText.contains("'s Inventory") || 
               titleText.contains("'s Ender Chest") || 
               titleText.contains("'s Armor") || 
               titleText.contains("'s Hand")
    }
}