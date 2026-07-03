package com.flowhack.flowcapital.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Перепланировать все сохранённые напоминания при старте приложения.
 * Читает список напоминаний из DataStore и создаёт WorkManager задачи.
 * Всегда планирует финальное напоминание в 23:00.
 */
suspend fun rescheduleSavedReminders(context: Context, settingsManager: SettingsManager) {
    val reminders = settingsManager.remindersFlow.first()
    for (time in reminders) {
        val parts = time.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: continue
            val min = parts[1].toIntOrNull() ?: continue
            scheduleDailyReminder(context, hour, min, time)
        }
    }
    scheduleFinalReminder(context)
}

/**
 * Запланировать ежедневное напоминание.
 */
fun scheduleDailyReminder(context: Context, hour: Int, min: Int, timeTag: String) {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, min)
        set(Calendar.SECOND, 0)
    }
    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
    val delay = target.timeInMillis - now.timeInMillis

    val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "potok_rem_$timeTag",
        ExistingPeriodicWorkPolicy.REPLACE,
        request
    )
}

/**
 * Финальное напоминание в 23:00.
 */
fun scheduleFinalReminder(context: Context) {
    val now = Calendar.getInstance()
    val target23 = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    if (target23.before(now)) target23.add(Calendar.DAY_OF_YEAR, 1)
    val request23 = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(target23.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "potok_final",
        ExistingPeriodicWorkPolicy.KEEP,
        request23
    )
}
