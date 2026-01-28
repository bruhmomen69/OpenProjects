package bruh.auctionhouse.economy

import org.bukkit.OfflinePlayer
import java.math.BigDecimal

/**
 * Interface for economy providers.
 * Abstracts economy operations to allow for different implementations.
 */
interface EconomyProvider {
    
    /**
     * Gets the name of the economy provider.
     */
    val name: String
    
    /**
     * Checks if the economy provider is available and ready to use.
     */
    val isAvailable: Boolean
    
    /**
     * Formats an amount into a human-readable string with currency symbol.
     * 
     * @param amount The amount to format
     * @return Formatted string (e.g., "$1,234.56")
     */
    fun format(amount: BigDecimal): String
    
    /**
     * Gets the balance of a player.
     * 
     * @param player The player to check
     * @return The player's balance
     */
    fun getBalance(player: OfflinePlayer): BigDecimal
    
    /**
     * Checks if a player has at least the specified amount.
     * 
     * @param player The player to check
     * @param amount The amount required
     * @return True if the player has sufficient funds
     */
    fun has(player: OfflinePlayer, amount: BigDecimal): Boolean
    
    /**
     * Withdraws money from a player's account.
     * 
     * @param player The player to withdraw from
     * @param amount The amount to withdraw
     * @return True if the withdrawal was successful
     */
    fun withdraw(player: OfflinePlayer, amount: BigDecimal): Boolean
    
    /**
     * Deposits money into a player's account.
     * 
     * @param player The player to deposit to
     * @param amount The amount to deposit
     * @return True if the deposit was successful
     */
    fun deposit(player: OfflinePlayer, amount: BigDecimal): Boolean
    
    /**
     * Transfers money from one player to another.
     * 
     * @param from The player to withdraw from
     * @param to The player to deposit to
     * @param amount The amount to transfer
     * @return True if the transfer was successful
     */
    fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: BigDecimal): Boolean {
        if (!withdraw(from, amount)) {
            return false
        }
        if (!deposit(to, amount)) {
            // Refund the withdrawal if deposit fails
            deposit(from, amount)
            return false
        }
        return true
    }
}