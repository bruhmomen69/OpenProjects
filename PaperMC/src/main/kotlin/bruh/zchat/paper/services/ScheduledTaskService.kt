package bruh.zchat.paper.services

import bruh.zchat.paper.config.ConfigManager
import bruh.zchat.paper.database.DatabaseMaintenanceService
import bruh.zchat.paper.database.PlayerDataManager
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

class ScheduledTaskService(
    private val plugin: Plugin,
    private val configManager: ConfigManager,
    private val databaseMaintenanceService: DatabaseMaintenanceService,
    private val playerDataManager: PlayerDataManager,
    private val crossServerMessageBusService: CrossServerMessageBusService
) {
    private val logger = LoggerFactory.getLogger(ScheduledTaskService::class.java)
    private val scheduledTasks = mutableMapOf<String, Int>()

    fun scheduleMaintenanceTasks() {
        // Schedule daily data retention (default: 2 AM)
        scheduleDailyTask("data-retention", 2, 0) {
            plugin.launch(Dispatchers.IO) {
                try {
                    val result = databaseMaintenanceService.performDataRetention()
                    if (result.success) {
                        logger.info("Daily data retention: ${result.message}")
                    } else {
                        logger.error("Daily data retention failed: ${result.message}")
                    }
                } catch (e: Exception) {
                    logger.error("Daily data retention task failed", e)
                }
            }
        }
    }
    
    fun scheduleCrossServerTasks(serverInstanceId: String) {
        val config = configManager.storage.crossServerMessaging
        if (!config.enabled) return
        
        // 1. Heartbeat task (every X seconds)
        val heartbeatTicks = config.heartbeatIntervalSeconds * 20L
        val heartbeatTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            plugin.launch(Dispatchers.IO) {
                playerDataManager.updateHeartbeat(serverInstanceId)
            }
        }, heartbeatTicks, heartbeatTicks).taskId
        scheduledTasks["heartbeat"] = heartbeatTaskId
        
        // 2. Message Bus Polling (every X ms) - Fixed 250ms per plan, but using config value as base
        // Note: Bukkit scheduler runs in ticks (50ms). 250ms = 5 ticks.
        // For sub-tick precision or independent timing, we could use a separate thread/timer, 
        // but async task is fine for now. 250ms is achievable with 5 ticks.
        val pollTicks = (config.pollIntervalMillis / 50).coerceAtLeast(1)
        val pollTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            plugin.launch(Dispatchers.IO) {
                crossServerMessageBusService.pollMessages()
            }
        }, pollTicks, pollTicks).taskId
        scheduledTasks["message-poll"] = pollTaskId
        
        // 3. Reclaim stale messages (every minute or so)
        val reclaimTicks = 60 * 20L
        val reclaimTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            plugin.launch(Dispatchers.IO) {
                crossServerMessageBusService.reclaimStaleMessages()
            }
        }, reclaimTicks, reclaimTicks).taskId
        scheduledTasks["message-reclaim"] = reclaimTaskId
        
        logger.info("Scheduled cross-server tasks (Instance ID: $serverInstanceId)")
    }

    private fun scheduleDailyTask(name: String, hour: Int, minute: Int, task: Runnable) {
        val initialDelay = calculateInitialDelay(hour, minute)
        val period = 24 * 60 * 60 * 20L // 24 hours in ticks

        val taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelay, period).taskId
        scheduledTasks[name] = taskId
        logger.info("Scheduled daily task '$name' at ${hour}:${minute}")
    }

    private fun scheduleHourlyTask(name: String, task: Runnable) {
        val taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, task,
            20L * 60 * 60L, // 1 hour initial delay
            20L * 60 * 60L // 1 hour period
        ).taskId
        scheduledTasks[name] = taskId
        logger.info("Scheduled hourly task '$name'")
    }

    fun cancelAllTasks() {
        scheduledTasks.values.forEach { taskId ->
            Bukkit.getScheduler().cancelTask(taskId)
        }
        scheduledTasks.clear()
        logger.info("Cancelled all scheduled tasks")
    }

    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(hour, minute)
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }
        val duration = Duration.between(now, target)
        return duration.seconds * 20L // Convert to ticks
    }
}