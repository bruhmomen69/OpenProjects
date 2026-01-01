package bruh.zchat.utils.menuapi

import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * A confirmation menu with Yes/No options.
 */
class ConfirmationMenu : Menu {
    override var title: Component = Component.text("Are you sure?")
    override var background: VItem? = VItem.FILLER_GRAY

    var confirmItem: VItem = VItem(XMaterial.LIME_WOOL) {
        name = Component.text("Confirm")
    }

    var cancelItem: VItem = VItem(XMaterial.RED_WOOL) {
        name = Component.text("Cancel")
    }

    var infoItem: VItem? = null

    var onConfirm: ((Player) -> Unit)? = null
    var onCancel: ((Player) -> Unit)? = null

    var confirmSlot: Int = 11
    var cancelSlot: Int = 15
    var infoSlot: Int = 13

    val rows: Int = 3

    companion object {
        inline operator fun invoke(builder: ConfirmationMenu.() -> Unit): ConfirmationMenu {
            return ConfirmationMenu().apply(builder)
        }
    }
}
