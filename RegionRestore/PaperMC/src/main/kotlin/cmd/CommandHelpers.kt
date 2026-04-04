package bruh.regionrestore.cmd

import bruh.regionrestore.cloner.RegionInstance
import bruh.regionrestore.translations.CommandMessages
import bruh.regionrestore.translations.GuiMessages
import bruh.zchat.utils.menuapi.ConfirmationMenu
import bruh.zchat.utils.menuapi.MenuAPI
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CommandHelpers {
    fun tGui(translations: TranslationAPI, key: GuiMessages): String = translations.getString(key)

    suspend fun confirmInstanceDeletion(
        player: Player,
        instance: RegionInstance,
        translations: TranslationAPI,
        menuAPI: MenuAPI,
        plugin: JavaPlugin
    ): Boolean {
        return suspendCoroutine { continuation ->
            var completed = false
            val menu = ConfirmationMenu {
                title = Component.text(tGui(translations, GuiMessages.CONFIRM_DELETE_TITLE))
                infoItem = VItem(XMaterial.PAPER) {
                    name = Component.text(tGui(translations, GuiMessages.CONFIRM_DELETE_INFO))
                    loreStrings(
                        listOf(
                            tGui(translations, GuiMessages.CONFIRM_INSTANCE_LINE).replace("<id>", instance.instanceId.toString()),
                            tGui(translations, GuiMessages.CONFIRM_TEMPLATE_LINE).replace("<name>", instance.templateName),
                            tGui(translations, GuiMessages.CONFIRM_WORLD_LINE).replace("<world>", instance.worldName),
                            tGui(translations, GuiMessages.CONFIRM_WARNING)
                        )
                    )
                }
                onConfirm = {
                    if (!completed) {
                        completed = true
                        continuation.resume(true)
                    }
                }
                onCancel = {
                    if (!completed) {
                        completed = true
                        continuation.resume(false)
                    }
                }
            }

            plugin.launch(plugin.entityDispatcher(player)) {
                menuAPI.open(menu, player)
            }
        }
    }
}
