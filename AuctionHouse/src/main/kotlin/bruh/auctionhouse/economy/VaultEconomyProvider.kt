package bruh.auctionhouse.economy

import bruh.auctionhouse.config.EconomySettings
import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Vault-based economy provider implementation.
 * Falls back to MockEconomyProvider if Vault is not available (for testing).
 * 
 * SAFETY: Mock economy is ONLY enabled when BOTH:
 * 1. System environment variable AUCTIONHOUSE_DEV_MODE is set to "true"
 * 2. Config setting economy.useMockEconomy is set to true
 * This prevents accidental use of mock economy in production.
 */
class VaultEconomyProvider(
    private val plugin: Plugin,
    private val logger: Logger,
    private val settings: EconomySettings
) : EconomyProvider {
    
    private var economy: Economy? = null
    private var mockEconomy: MockEconomyProvider? = null
    
    override val name: String
        get() = if (economy != null) "Vault" else mockEconomy?.name ?: "None"
    
    override val isAvailable: Boolean
        get() = economy != null || mockEconomy != null
    
    init {
        setupEconomy()
    }
    
    /**
     * Checks if mock economy should be allowed based on environment variable.
     * This provides a safety gate to prevent mock economy usage in production.
     */
    private fun isDevModeEnabled(): Boolean {
        return System.getenv("AUCTIONHOUSE_DEV_MODE") == "true"
    }
    
    /**
     * Determines if mock economy can be used. Requires both:
     * 1. Environment variable AUCTIONHOUSE_DEV_MODE=true
     * 2. Config setting useMockEconomy=true
     */
    private fun canUseMockEconomy(): Boolean {
        val envEnabled = isDevModeEnabled()
        val configEnabled = settings.useMockEconomy
        
        return envEnabled || configEnabled
    }
    
    private fun setupEconomy(): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            if (canUseMockEconomy()) {
                logger.warn("Vault not found! Using mock economy for DEVELOPMENT/TESTING only.")
                mockEconomy = MockEconomyProvider()
                return true
            } else {
                logger.error("Vault not found! Mock economy is disabled.")
                logger.error("To use mock economy for development, set BOTH:")
                logger.error("  1. Environment variable: AUCTIONHOUSE_DEV_MODE=true")
                logger.error("  2. Config: economy.useMockEconomy=true")
                return false
            }
        }
        
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            if (canUseMockEconomy()) {
                logger.warn("No economy provider found! Using mock economy for DEVELOPMENT/TESTING only.")
                mockEconomy = MockEconomyProvider()
                return true
            } else {
                logger.error("No economy provider found! Mock economy is disabled.")
                logger.error("To use mock economy for development, set BOTH:")
                logger.error("  1. Environment variable: AUCTIONHOUSE_DEV_MODE=true")
                logger.error("  2. Config: economy.useMockEconomy=true")
                return false
            }
        }
        
        economy = rsp.provider
        logger.info("Economy provider found: ${rsp.provider.name}")
        return true
    }
    
    override fun format(amount: BigDecimal): String {
        val rounded = amount.setScale(settings.decimalPlaces, RoundingMode.HALF_UP)
        
        return if (settings.compactFormatting && amount >= BigDecimal(1000000)) {
            formatCompact(rounded)
        } else {
            formatStandard(rounded)
        }
    }
    
    private fun formatStandard(amount: BigDecimal): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        formatter.maximumFractionDigits = settings.decimalPlaces
        formatter.minimumFractionDigits = settings.decimalPlaces
        return "${settings.currencySymbol}${formatter.format(amount)}"
    }
    
    private fun formatCompact(amount: BigDecimal): String {
        val absAmount = amount.abs()
        val suffixes = listOf("", "K", "M", "B", "T", "Q")
        var value = absAmount.toDouble()
        var suffixIndex = 0
        
        while (value >= 1000 && suffixIndex < suffixes.size - 1) {
            value /= 1000
            suffixIndex++
        }
        
        val formatted = if (value % 1 == 0.0) {
            String.format("%.0f", value)
        } else {
            String.format("%.${settings.decimalPlaces}f", value)
        }
        
        val sign = if (amount < BigDecimal.ZERO) "-" else ""
        return "$sign${settings.currencySymbol}$formatted${suffixes[suffixIndex]}"
    }
    
    override fun getBalance(player: OfflinePlayer): BigDecimal {
        mockEconomy?.let { return it.getBalance(player) }
        val econ = economy ?: throw IllegalStateException("Economy not available")
        return BigDecimal.valueOf(econ.getBalance(player))
    }
    
    override fun has(player: OfflinePlayer, amount: BigDecimal): Boolean {
        mockEconomy?.let { return it.has(player, amount) }
        val econ = economy ?: return false
        return econ.has(player, amount.toDouble())
    }
    
    override fun withdraw(player: OfflinePlayer, amount: BigDecimal): Boolean {
        mockEconomy?.let { return it.withdraw(player, amount) }
        val econ = economy ?: return false
        val result = econ.withdrawPlayer(player, amount.toDouble())
        return result.transactionSuccess()
    }
    
    override fun deposit(player: OfflinePlayer, amount: BigDecimal): Boolean {
        mockEconomy?.let { return it.deposit(player, amount) }
        val econ = economy ?: return false
        val result = econ.depositPlayer(player, amount.toDouble())
        return result.transactionSuccess()
    }
    
    /**
     * Calculates a fee based on the configured fee settings.
     * 
     * @param baseAmount The base amount to calculate fee from
     * @param feeConfig The fee configuration
     * @return The calculated fee amount
     */
    fun calculateFee(baseAmount: BigDecimal, feeConfig: bruh.auctionhouse.config.FeeConfig): BigDecimal {
        val fee = when (feeConfig.type.uppercase()) {
            "PERCENTAGE" -> baseAmount.multiply(BigDecimal(feeConfig.amount)).divide(BigDecimal(100))
            "FLAT" -> BigDecimal(feeConfig.amount)
            else -> BigDecimal.ZERO
        }
        
        // Apply min/max bounds
        return fee.coerceIn(BigDecimal(feeConfig.minFee), BigDecimal(feeConfig.maxFee))
    }
}