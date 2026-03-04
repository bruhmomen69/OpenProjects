package bruh.commands.commonservercommands.commands

import bruh.commands.commonservercommands.CommandPlugin
import bruh.commands.commonservercommands.entityDispatcher
import bruh.commands.commonservercommands.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.Registry
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Commands for item manipulation.
 */
class ItemCommands(
    private val plugin: CommandPlugin,
    private val translations: TranslationAPI
) {
    private val miniMessage = MiniMessage.miniMessage()

    @Command("give")
    @CommandPermission("essentiallystateless.give")
    suspend fun give(
        actor: BukkitCommandActor,
        @SuggestOnlinePlayer targetName: String,
        material: String,
        @Optional @Default("1") amount: Int
    ) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_NOT_FOUND) {
                unparsed("player", targetName)
            })
            return
        }

        val mat = Material.matchMaterial(material)
        if (mat == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                unparsed("value", material)
            })
            return
        }

        val item = ItemStack(mat, amount.coerceIn(1, mat.maxStackSize * 36))

        withContext(plugin.entityDispatcher(target)) {
            target.inventory.addItem(item)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.GIVE_SUCCESS) {
            unparsed("amount", amount.toString())
            unparsed("item", mat.name.lowercase().replace("_", " "))
            unparsed("player", target.name)
        })
    }

    @Command("item", "i")
    @CommandPermission("essentiallystateless.item")
    suspend fun item(
        actor: BukkitCommandActor,
        material: String,
        @Optional @Default("1") amount: Int
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val mat = Material.matchMaterial(material)
        if (mat == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                unparsed("value", material)
            })
            return
        }

        val item = ItemStack(mat, amount.coerceIn(1, mat.maxStackSize * 36))

        withContext(plugin.entityDispatcher(player)) {
            player.inventory.addItem(item)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.ITEM_SPAWN) {
            unparsed("amount", amount.toString())
            unparsed("item", mat.name.lowercase().replace("_", " "))
        })
    }

    @Command("more")
    @CommandPermission("essentiallystateless.more")
    suspend fun more(actor: BukkitCommandActor, @Optional amount: Int?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.MORE_FAILED))
            return
        }

        val newAmount = amount ?: item.type.maxStackSize

        withContext(plugin.entityDispatcher(player)) {
            item.amount = newAmount
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.MORE_SUCCESS) {
            unparsed("amount", newAmount.toString())
        })
    }

    @Command("repair", "fix")
    @CommandPermission("essentiallystateless.repair")
    suspend fun repair(actor: BukkitCommandActor, @Optional all: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.entityDispatcher(player)) {
            if (all?.equals("all", ignoreCase = true) == true) {
                for (item in player.inventory.contents) {
                    if (item != null) {
                        repairItem(item)
                    }
                }
                player.sendMessage(translations.getComponent(CommandMessages.REPAIR_ALL_SUCCESS))
            } else {
                val item = player.inventory.itemInMainHand
                if (item.type == Material.AIR) {
                    player.sendMessage(translations.getComponent(CommandMessages.REPAIR_FAILED))
                    return@withContext
                }
                if (!repairItem(item)) {
                    player.sendMessage(translations.getComponent(CommandMessages.REPAIR_FAILED))
                    return@withContext
                }
                player.sendMessage(translations.getComponent(CommandMessages.REPAIR_SUCCESS))
            }
        }
    }

    private fun repairItem(item: ItemStack): Boolean {
        val meta = item.itemMeta
        if (meta is Damageable && meta.hasDamage()) {
            meta.damage = 0
            item.itemMeta = meta
            return true
        }
        return false
    }

    @Command("enchant")
    @CommandPermission("essentiallystateless.enchant")
    suspend fun enchant(
        actor: BukkitCommandActor,
        enchantmentName: String,
        @Optional @Default("1") level: Int
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ENCHANT_FAILED))
            return
        }

        val key = NamespacedKey.minecraft(enchantmentName.lowercase())
        val enchantment = Registry.ENCHANTMENT.get(key)
        if (enchantment == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ENCHANT_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            item.addUnsafeEnchantment(enchantment, level)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.ENCHANT_SUCCESS) {
            unparsed("enchantment", enchantmentName)
            unparsed("level", level.toString())
        })
    }

    @Command("hat", "head")
    @CommandPermission("essentiallystateless.hat")
    suspend fun hat(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.HAT_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val helmet = player.inventory.helmet
            player.inventory.helmet = item.clone().apply { amount = 1 }
            item.amount -= 1
            if (helmet != null && helmet.type != Material.AIR) {
                player.inventory.addItem(helmet)
            }
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.HAT_SUCCESS))
    }

    @Command("skull", "playerhead")
    @CommandPermission("essentiallystateless.skull")
    suspend fun skull(actor: BukkitCommandActor, @Optional @SuggestOnlinePlayer playerName: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val targetName = playerName ?: player.name

        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta
        
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
        meta.owningPlayer = offlinePlayer
        skull.itemMeta = meta

        withContext(plugin.entityDispatcher(player)) {
            player.inventory.addItem(skull)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.SKULL_SUCCESS) {
            unparsed("player", targetName)
        })
    }

    @Command("itemname", "iname", "rename")
    @CommandPermission("essentiallystateless.itemname")
    suspend fun itemname(actor: BukkitCommandActor, @Optional name: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ITEMNAME_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val meta = item.itemMeta ?: return@withContext
            if (name.isNullOrBlank()) {
                meta.displayName(null)
                item.itemMeta = meta
                player.sendMessage(translations.getComponent(CommandMessages.ITEMNAME_CLEARED))
            } else {
                meta.displayName(miniMessage.deserialize(name))
                item.itemMeta = meta
                player.sendMessage(translations.getComponent(CommandMessages.ITEMNAME_SUCCESS) {
                    unparsed("name", name)
                })
            }
        }
    }

    @Command("itemlore", "lore")
    @CommandPermission("essentiallystateless.itemlore")
    suspend fun itemlore(actor: BukkitCommandActor, @Optional lore: String?) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.ITEMLORE_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val meta = item.itemMeta ?: return@withContext
            if (lore.isNullOrBlank()) {
                meta.lore(null)
                item.itemMeta = meta
                player.sendMessage(translations.getComponent(CommandMessages.ITEMLORE_CLEARED))
            } else {
                val loreLines = lore.split("\\n", "|").map { miniMessage.deserialize(it) }
                meta.lore(loreLines)
                item.itemMeta = meta
                player.sendMessage(translations.getComponent(CommandMessages.ITEMLORE_SUCCESS))
            }
        }
    }

    @Command("condense", "compact")
    @CommandPermission("essentiallystateless.condense")
    suspend fun condense(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        // Mapping of condensable items
        val condenseMap = mapOf(
            Material.IRON_INGOT to Material.IRON_BLOCK,
            Material.GOLD_INGOT to Material.GOLD_BLOCK,
            Material.DIAMOND to Material.DIAMOND_BLOCK,
            Material.EMERALD to Material.EMERALD_BLOCK,
            Material.LAPIS_LAZULI to Material.LAPIS_BLOCK,
            Material.REDSTONE to Material.REDSTONE_BLOCK,
            Material.COAL to Material.COAL_BLOCK,
            Material.COPPER_INGOT to Material.COPPER_BLOCK,
            Material.RAW_IRON to Material.RAW_IRON_BLOCK,
            Material.RAW_GOLD to Material.RAW_GOLD_BLOCK,
            Material.RAW_COPPER to Material.RAW_COPPER_BLOCK,
            Material.NETHERITE_INGOT to Material.NETHERITE_BLOCK,
            Material.WHEAT to Material.HAY_BLOCK,
            Material.SLIME_BALL to Material.SLIME_BLOCK,
            Material.DRIED_KELP to Material.DRIED_KELP_BLOCK,
            Material.BONE_MEAL to Material.BONE_BLOCK,
            Material.SNOW_BLOCK to Material.SNOW_BLOCK,
            Material.CLAY_BALL to Material.CLAY,
            Material.GLOWSTONE_DUST to Material.GLOWSTONE,
            Material.AMETHYST_SHARD to Material.AMETHYST_BLOCK,
            Material.HONEYCOMB to Material.HONEYCOMB_BLOCK,
            Material.NETHER_WART to Material.NETHER_WART_BLOCK,
            Material.ICE to Material.PACKED_ICE,
            Material.PACKED_ICE to Material.BLUE_ICE
        )

        withContext(plugin.entityDispatcher(player)) {
            var totalBlocks = 0
            val inventory = player.inventory

            for ((material, block) in condenseMap) {
                val itemsNeeded = if (material == Material.GLOWSTONE_DUST || material == Material.CLAY_BALL) 4 else 9
                
                while (true) {
                    var totalCount = 0
                    val slots = mutableListOf<Int>()
                    
                    for (i in 0 until inventory.size) {
                        val item = inventory.getItem(i)
                        if (item?.type == material) {
                            totalCount += item.amount
                            slots.add(i)
                        }
                    }
                    
                    if (totalCount < itemsNeeded) break
                    
                    val blocksToCreate = totalCount / itemsNeeded
                    val remaining = totalCount % itemsNeeded
                    
                    // Clear all slots with this material
                    for (slot in slots) {
                        inventory.setItem(slot, null)
                    }
                    
                    // Add blocks
                    inventory.addItem(ItemStack(block, blocksToCreate))
                    totalBlocks += blocksToCreate
                    
                    // Add remaining items
                    if (remaining > 0) {
                        inventory.addItem(ItemStack(material, remaining))
                    }
                    
                    break
                }
            }

            if (totalBlocks > 0) {
                player.sendMessage(translations.getComponent(CommandMessages.CONDENSE_SUCCESS) {
                    unparsed("count", totalBlocks.toString())
                })
            } else {
                player.sendMessage(translations.getComponent(CommandMessages.CONDENSE_NONE))
            }
        }
    }
}
