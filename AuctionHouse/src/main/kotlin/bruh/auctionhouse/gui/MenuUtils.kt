package bruh.auctionhouse.gui

import bruh.auctionhouse.economy.EconomyProvider
import bruh.auctionhouse.translations.GuiMessages
import bruh.zchat.utils.menuapi.VItem
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.time.Duration
import java.time.Instant

/**
 * Utility functions and items for creating menus.
 */
object MenuUtils {
    private val mm = MiniMessage.miniMessage()

    /**
     * Creates a background filler item.
     */
    fun backgroundItem(): VItem {
        return VItem(XMaterial.GRAY_STAINED_GLASS_PANE) {
            name = Component.empty()
            hideAllFlags()
        }
    }

    /**
     * Creates a navigation button to go to the previous page.
     */
    fun navigationPrevious(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.ARROW) {
            name = translationAPI.getComponentSync(GuiMessages.PREVIOUS_PAGE)
            hideAllFlags()
        }
    }

    /**
     * Creates a navigation button to go to the next page.
     */
    fun navigationNext(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.ARROW) {
            name = translationAPI.getComponentSync(GuiMessages.NEXT_PAGE)
            hideAllFlags()
        }
    }

    /**
     * Creates a close menu button.
     */
    fun closeButton(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.BARRIER) {
            name = translationAPI.getComponentSync(GuiMessages.CLOSE)
            hideAllFlags()
        }
    }

    /**
     * Creates a back button to return to the parent menu.
     */
    fun backButton(translationAPI: TranslationAPI): VItem {
        return VItem(XMaterial.OAK_DOOR) {
            name = translationAPI.getComponentSync(GuiMessages.BACK)
            hideAllFlags()
        }
    }

    /**
     * Formats time remaining until the given end time.
     * Returns formats like "Xd Xh", "Xh Xm", "Xm Xs", or "Xs"
     */
    fun formatTimeRemaining(endTime: Instant): String {
        val duration = Duration.between(Instant.now(), endTime)

        return when {
            duration.isNegative -> "Ended"
            duration.toDays() > 0 -> "${duration.toDays()}d ${duration.toHoursPart()}h"
            duration.toHours() > 0 -> "${duration.toHours()}h ${duration.toMinutesPart()}m"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ${duration.toSecondsPart()}s"
            else -> "${duration.toSeconds()}s"
        }
    }

    /**
     * Formats a price with the currency symbol.
     */
    fun formatPrice(price: Double, economy: EconomyProvider): String {
        return economy.format(java.math.BigDecimal.valueOf(price))
    }

    /**
     * Checks if a price exceeds the expensive threshold.
     */
    fun isExpensiveAction(price: Double, threshold: Double): Boolean {
        return price >= threshold
    }
}
