package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Standard placeholder [VItem] factories for common menu states.
 */
object MenuPlaceholders {

    fun loading(text: Component = Component.text("Loading...", NamedTextColor.YELLOW)): VItem =
        VItem(XMaterial.CLOCK) {
            name = text
            enchantGlint = true
            hideAllFlags()
        }

    fun empty(text: Component = Component.text("Nothing to show", NamedTextColor.GRAY)): VItem =
        VItem(XMaterial.LIGHT_GRAY_STAINED_GLASS_PANE) {
            name = text
            hideAllFlags()
        }

    fun error(text: Component = Component.text("An error occurred", NamedTextColor.RED)): VItem =
        VItem(XMaterial.BARRIER) {
            name = text
            hideAllFlags()
        }

    fun processing(text: Component = Component.text("Processing...", NamedTextColor.GOLD)): VItem =
        VItem(XMaterial.CLOCK) {
            name = text
            enchantGlint = true
            hideAllFlags()
        }
}
