package bruh.zchat.paper.services

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
    private val databaseMaintenanceService: DatabaseMaintenanceService,
    private val playerDataManager: PlayerDataManager
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