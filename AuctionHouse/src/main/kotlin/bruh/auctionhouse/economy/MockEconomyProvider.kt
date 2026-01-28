package bruh.auctionhouse.economy

import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock economy provider for testing purposes.
 * Stores balances in memory and provides basic economy functionality.
 */
class MockEconomyProvider : EconomyProvider {
    
    private val balances = ConcurrentHashMap<String, BigDecimal>()
    
    override val name: String = "MockEconomy"
    
    override val isAvailable: Boolean = true
    
    override fun format(amount: BigDecimal): String {
        return "$$amount"
    }
    
    override fun getBalance(player: OfflinePlayer): BigDecimal {
        return balances.getOrDefault(player.uniqueId.toString(), BigDecimal.ZERO)
    }
    
    override fun has(player: OfflinePlayer, amount: BigDecimal): Boolean {
        return getBalance(player) >= amount
    }
    
    override fun withdraw(player: OfflinePlayer, amount: BigDecimal): Boolean {
        val current = getBalance(player)
        if (current < amount) return false
        balances[player.uniqueId.toString()] = current - amount
        return true
    }
    
    override fun deposit(player: OfflinePlayer, amount: BigDecimal): Boolean {
        val current = getBalance(player)
        balances[player.uniqueId.toString()] = current + amount
        return true
    }
}