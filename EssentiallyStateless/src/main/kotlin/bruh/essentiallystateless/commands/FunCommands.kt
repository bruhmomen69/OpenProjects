package bruh.essentiallystateless.commands

import bruh.essentiallystateless.EssentiallyStatelessPlugin
import bruh.essentiallystateless.translations.CommandMessages
import bruh.zchat.utils.translations.TranslationAPI
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.Registry
import org.bukkit.NamespacedKey
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Fun and miscellaneous commands.
 */
class FunCommands(
    private val plugin: EssentiallyStatelessPlugin,
    private val translations: TranslationAPI
) {
    private val miniMessage = MiniMessage.miniMessage()

    @Command("me", "action")
    @CommandPermission("essentiallystateless.me")
    suspend fun me(actor: BukkitCommandActor, action: String) {
        val format = plugin.config.meFormat
        val message = format
            .replace("<player>", actor.name())
            .replace("<action>", action)
        val component = miniMessage.deserialize(message)

        Bukkit.broadcast(component)
    }

    @Command("fireball")
    @CommandPermission("essentiallystateless.fireball")
    suspend fun fireball(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        withContext(plugin.regionDispatcher(player.location)) {
            val fireball = player.world.spawn(player.eyeLocation, Fireball::class.java)
            fireball.direction = player.location.direction
            fireball.shooter = player
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.FIREBALL_LAUNCHED))
    }

    @Command("book")
    @CommandPermission("essentiallystateless.book")
    suspend fun book(actor: BukkitCommandActor) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type != Material.WRITTEN_BOOK) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.BOOK_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val meta = item.itemMeta as BookMeta
            val newItem = org.bukkit.inventory.ItemStack(Material.WRITABLE_BOOK)
            val newMeta = newItem.itemMeta as org.bukkit.inventory.meta.BookMeta
            
            // Copy pages
            for (i in 1..meta.pageCount) {
                newMeta.addPage(meta.getPage(i))
            }
            
            newItem.itemMeta = newMeta
            player.inventory.setItemInMainHand(newItem)
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.BOOK_EDIT))
    }

    @Command("editsign")
    @CommandPermission("essentiallystateless.editsign")
    suspend fun editsign(
        actor: BukkitCommandActor,
        line: Int,
        @Optional text: String?
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player

        val targetBlock = player.getTargetBlockExact(10)
        if (targetBlock == null || targetBlock.state !is Sign) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.EDITSIGN_FAILED))
            return
        }

        val lineIndex = (line - 1).coerceIn(0, 3)
        val newText = text ?: ""

        withContext(plugin.regionDispatcher(targetBlock.location)) {
            val sign = targetBlock.state as Sign
            sign.getSide(Side.FRONT).line(lineIndex, miniMessage.deserialize(newText))
            sign.update()
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.EDITSIGN_SUCCESS))
    }

    @Command("potion")
    @CommandPermission("essentiallystateless.potion")
    suspend fun potion(
        actor: BukkitCommandActor,
        effect: String,
        @Optional @Default("30") duration: Int,
        @Optional @Default("0") amplifier: Int
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type != Material.POTION && 
            item.type != Material.SPLASH_POTION && 
            item.type != Material.LINGERING_POTION) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.POTION_FAILED))
            return
        }

        if (effect.equals("clear", ignoreCase = true)) {
            withContext(plugin.entityDispatcher(player)) {
                val meta = item.itemMeta as PotionMeta
                meta.clearCustomEffects()
                item.itemMeta = meta
            }
            actor.sender().sendMessage(translations.getComponent(CommandMessages.POTION_CLEARED))
            return
        }

        val key = NamespacedKey.minecraft(effect.lowercase())
        val effectType = Registry.POTION_EFFECT_TYPE.get(key)
        if (effectType == null) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.INVALID_NUMBER) {
                unparsed("value", effect)
            })
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val meta = item.itemMeta as PotionMeta
            meta.addCustomEffect(PotionEffect(effectType, duration * 20, amplifier), true)
            item.itemMeta = meta
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.POTION_ADDED) {
            unparsed("effect", effect)
        })
    }

    @Command("firework")
    @CommandPermission("essentiallystateless.firework")
    suspend fun firework(
        actor: BukkitCommandActor,
        @Optional power: Int?
    ) {
        if (actor.sender() !is Player) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }
        val player = actor.sender() as Player
        val item = player.inventory.itemInMainHand

        if (item.type != Material.FIREWORK_ROCKET) {
            actor.sender().sendMessage(translations.getComponent(CommandMessages.FIREWORK_FAILED))
            return
        }

        withContext(plugin.entityDispatcher(player)) {
            val meta = item.itemMeta as org.bukkit.inventory.meta.FireworkMeta
            if (power != null) {
                meta.power = power.coerceIn(0, 127)
            }
            item.itemMeta = meta
        }

        actor.sender().sendMessage(translations.getComponent(CommandMessages.FIREWORK_MODIFIED))
    }
}
