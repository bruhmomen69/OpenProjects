package bruh.zchat.paper.utils

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

interface ChatInventoryHolder: InventoryHolder {

}

class ChatInventoryHolderImpl: ChatInventoryHolder {
    public lateinit var innerInventory: Inventory
    override fun getInventory(): Inventory {
        return innerInventory
    }
}