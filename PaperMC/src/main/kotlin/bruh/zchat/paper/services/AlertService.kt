package bruh.zchat.paper.services

import bruh.zchat.paper.PaperMC
import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.config.FilterGroup
import com.github.shynixn.mccoroutine.folia.asyncDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for managing swear filter violation alerts to staff members.
 * Integrates with MessageFormattingService for consistent message formatting.
 */
class AlertService(
    private val plugin: PaperMC,
    private val configManager: ConfigManager,
    private val messageFormattingService: MessageFormattingService
) {
    private val logger = LoggerFactory.getLogger(AlertService::class.java)
    
    // Track online staff with alerts enabled
    private val alertsEnabled = ConcurrentHashMap.newKeySet<UUID>()
    
    // Rate limiting maps
    private val lastAlertTime = ConcurrentHashMap<UUID, Long>()
    private val alertCountPerMinute = ConcurrentHashMap<UUID, AtomicInteger>()
    
    /**
     * Toggle alerts for a staff member
     */
    fun toggleAlerts(player: Player): Boolean {
        val config = configManager.config.swearFilter.alerts
        
        if (!config.enableAlerts) {
            player.sendMessage(messageFormattingService.getConfigMessage("alerts.system_disabled", player))
            return false
        }
        
        if (!player.hasPermission(config.alertPermission)) {
            player.sendMessage(messageFormattingService.getConfigMessage("alerts.no_permission", player))
            return false
        }
        
        val wasEnabled = alertsEnabled.contains(player.uniqueId)
        
        if (wasEnabled) {
            alertsEnabled.remove(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("alerts.disabled", player))
            logger.debug("${player.name} disabled swear filter alerts")
        } else {
            alertsEnabled.add(player.uniqueId)
            player.sendMessage(messageFormattingService.getConfigMessage("alerts.enabled", player))
            logger.debug("${player.name} enabled swear filter alerts")
        }
        
        return !wasEnabled
    }
    
    /**
     * Check if a player has alerts enabled
     */
    fun hasAlertsEnabled(player: Player): Boolean {
        return alertsEnabled.contains(player.uniqueId) && 
               player.hasPermission(configManager.config.swearFilter.alerts.alertPermission)
    }
    
    /**
     * Initialize alerts for a player (called when player joins)
     */
    fun initializeAlertsForPlayer(player: Player) {
        val config = configManager.config.swearFilter.alerts
        
        if (!config.enableAlerts) {
            return
        }
        
        if (!player.hasPermission(config.alertPermission)) {
            return
        }
        
        if (config.enableByDefault && !alertsEnabled.contains(player.uniqueId)) {
            alertsEnabled.add(player.uniqueId)
            logger.debug("Auto-enabled alerts for ${player.name} (has permission and enabled by default)")
            
            // Send auto-enabled message if configured and enabled
            if (config.showAutoEnabledMessage) {
                val autoEnabledMessage = configManager.config.messages.alerts.autoEnabled
                if (autoEnabledMessage.isNotEmpty()) {
                    player.sendMessage(messageFormattingService.formatMessageComponent(autoEnabledMessage, player))
                }
            }
        }
    }
    
    /**
     * Send a violation alert to all eligible staff members
     */
    suspend fun sendViolationAlert(
        player: Player, 
        originalMessage: String, 
        group: FilterGroup, 
        infractionCount: Int
    ) {
        val config = configManager.config.swearFilter.alerts
        
        if (!config.enableAlerts) {
            return
        }
        
        if (!shouldSendAlert(group, config)) {
            return
        }
        
        if (isRateLimited(player.uniqueId, config)) {
            return
        }
        
        // Check if we should only alert before punishment
        if (config.onlyBeforePunishment) {
            val punishments = group.punishments[infractionCount]
            if (punishments != null) {
                return // Don't send alert if punishment will be applied
            }
        }
        
        // Create additional placeholders specific to alerts
        val alertPlaceholders = mapOf(
            "group_name" to group.name,
            "group_type" to group.type,
            "infraction_count" to infractionCount.toString(),
            "original_message" to originalMessage,
            "filtered_message" to originalMessage // Can be enhanced later
        )
        
        // Use existing MessageFormattingService infrastructure
        val alertComponent = messageFormattingService.formatMessageComponent(
            format = config.alertMessage,
            player = player,
            stringPlaceholders = alertPlaceholders,
            processUrls = false, // Don't process URLs in alerts
            processMentions = false, // Don't process mentions in alerts
            allowColors = true,
            allowFormatting = true
        )
        
        // Send to all online staff with alerts enabled
        for (staff in Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(config.alertPermission) && hasAlertsEnabled(staff)) {
                staff.sendMessage(alertComponent)
            }
        }
        
        // Log to console if enabled
        logToConsole(player, originalMessage, group, config)
    }
    
    /**
     * Check if we should send an alert for this group and configuration
     */
    private fun shouldSendAlert(group: FilterGroup, config: bruh.zchat.paper.config.AlertConfig): Boolean {
        // Check if this group is in the alert groups list (if the list is not empty)
        if (config.alertGroups.isNotEmpty() && group.name !in config.alertGroups) {
            return false
        }
        
        // Check severity threshold (using distance as a proxy for severity for Levenshtein)
        if (group.type.lowercase() == "levenshtein" && group.distance > config.minimumSeverity) {
            return false
        }
        
        return true
    }
    
    /**
     * Check if alerts are rate limited for this player
     */
    private fun isRateLimited(player: UUID, config: bruh.zchat.paper.config.AlertConfig): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Global cooldown
        if (config.alertCooldownSeconds > 0) {
            val lastAlert = lastAlertTime[player] ?: 0
            if (currentTime - lastAlert < config.alertCooldownSeconds * 1000) {
                return true
            }
            lastAlertTime[player] = currentTime
        }
        
        // Per-minute rate limiting
        if (config.maxAlertsPerMinute > 0) {
            val count = alertCountPerMinute.computeIfAbsent(player) { AtomicInteger(0) }
            if (count.incrementAndGet() > config.maxAlertsPerMinute) {
                return true
            }
            
            // Reset counter after 1 minute
            plugin.launch(plugin.asyncDispatcher) {
                delay(60000) // 1 minute
                alertCountPerMinute.remove(player)
            }
        }
        
        return false
    }
    
    /**
     * Log alert to console if enabled
     */
    private fun logToConsole(
        player: Player, 
        message: String, 
        group: FilterGroup, 
        config: bruh.zchat.paper.config.AlertConfig
    ) {
        if (config.logToConsole) {
            val consolePlaceholders = mapOf(
                "player_name" to player.name,
                "group_name" to group.name,
                "original_message" to message,
                "time" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            )
            
            val consoleMessage = messageFormattingService.formatMessageComponent(
                format = config.consoleAlertMessage,
                player = player,
                stringPlaceholders = consolePlaceholders,
                processUrls = false,
                processMentions = false,
                allowColors = false, // Console doesn't need colors
                allowFormatting = false
            )
            
            val plainMessage = PlainTextComponentSerializer.plainText().serialize(consoleMessage)
            plugin.logger.info(plainMessage)
        }
    }
    
    /**
     * Force enable alerts for a player (admin command)
     */
    fun forceEnableAlerts(player: Player) {
        alertsEnabled.add(player.uniqueId)
        player.sendMessage(messageFormattingService.getConfigMessage("alerts.enabled", player))
        logger.info("Admin forced alerts enabled for ${player.name}")
    }
    
    /**
     * Force disable alerts for a player (admin command)
     */
    fun forceDisableAlerts(player: Player) {
        alertsEnabled.remove(player.uniqueId)
        player.sendMessage(messageFormattingService.getConfigMessage("alerts.disabled", player))
        logger.info("Admin forced alerts disabled for ${player.name}")
    }
    
    /**
     * Get all players with alerts enabled
     */
    fun getAlertsEnabledPlayers(): List<UUID> {
        return alertsEnabled.toList()
    }
    
    /**
     * Clear all alert states (admin command)
     */
    fun clearAllAlerts() {
        alertsEnabled.clear()
        lastAlertTime.clear()
        alertCountPerMinute.clear()
        logger.info("Cleared all alert states")
    }
    
    /**
     * Reload configuration and clear caches
     */
    fun reload() {
        // Clear rate limiting data
        lastAlertTime.clear()
        alertCountPerMinute.clear()
        
        logger.info("AlertService reloaded")
    }
}