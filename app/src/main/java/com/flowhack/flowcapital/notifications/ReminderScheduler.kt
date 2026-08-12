package com.flowhack.flowcapital.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flowhack.flowcapital.MainActivity
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import java.util.Calendar


/**
 * Перепланировать все сохранённые напоминания при старте приложения.
 * Читает список напоминаний из DataStore и создаёт задачи AlarmManager.
 * Всегда планирует финальное напоминание в 23:00.
 */
suspend fun rescheduleSavedReminders(context: Context, settingsManager: SettingsManager) {
    AppLogger.d("ReminderScheduler", "Перепланирование всех напоминаний")
    val reminders = settingsManager.remindersFlow.first()
    val alarmSet = settingsManager.alarmRemindersFlow.first()
    AppLogger.d("ReminderScheduler", "Напоминаний: ${reminders.size}, будильников: ${alarmSet.size}")
    for (time in reminders) {
        val parts = time.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: continue
            val min = parts[1].toIntOrNull() ?: continue
            if (time in alarmSet) {
                scheduleAlarmReminder(context, hour, min, time)
            } else {
                scheduleDailyReminder(context, hour, min, time)
            }
        }
    }
    scheduleFinalReminder(context)
}

/**
 * Запланировать ежедневное напоминание через AlarmManager.
 *
 * Напоминание (в отличие от будильника) показывает обычное уведомление через
 * [ReminderReceiver] без полноэкранного будильника и без звука будильника.
 * AlarmManager обеспечивает надёжную доставку даже при убитом приложении.
 * При отсутствии SCHEDULE_EXACT_ALARM используется inexact fallback.
 */
fun scheduleDailyReminder(context: Context, hour: Int, min: Int, timeTag: String) {
    AppLogger.d("ReminderScheduler", "Планирование напоминания: $timeTag ($hour:$min)")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(ReminderReceiver.EXTRA_TIME_TAG, timeTag)
    }
    val operation = PendingIntent.getBroadcast(
        context,
        timeTag.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, min)
        set(Calendar.SECOND, 0)
    }
    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, operation)
        AppLogger.d("ReminderScheduler", "Напоминание $timeTag: нет SCHEDULE_EXACT_ALARM, fallback на inexact")
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, operation)
        AppLogger.d("ReminderScheduler", "Напоминание $timeTag: setExactAndAllowWhileIdle на ${target.timeInMillis}")
    }
}

/**
 * Запланировать точное срабатывание будильника через AlarmManager.
 * Использует setAlarmClock() — гарантирует срабатывание даже в Doze-режиме,
 * но требует SCHEDULE_EXACT_ALARM на Android 12+; при отсутствии разрешения используется inexact fallback.
 */
fun scheduleAlarmReminder(context: Context, hour: Int, min: Int, timeTag: String) {
    AppLogger.d("ReminderScheduler", "Планирование будильника: $timeTag ($hour:$min)")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_TIME_TAG, timeTag)
    }
    val operation = PendingIntent.getBroadcast(
        context,
        timeTag.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, min)
        set(Calendar.SECOND, 0)
    }
    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)

    val showIntent = PendingIntent.getActivity(
        context,
        timeTag.hashCode() + 5000,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, operation)
        AppLogger.d("ReminderScheduler", "Будильник $timeTag: нет SCHEDULE_EXACT_ALARM, fallback на inexact")
    } else {
        val alarmClockInfo = AlarmManager.AlarmClockInfo(target.timeInMillis, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operation)
        AppLogger.d("ReminderScheduler", "Будильник $timeTag: setAlarmClock на ${target.timeInMillis}")
    }
}

/**
 * Отменить напоминание (обычное уведомление) для указанного тайм-тега.
 */
fun cancelDailyReminder(context: Context, timeTag: String) {
    AppLogger.d("ReminderScheduler", "Отмена напоминания: $timeTag")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(ReminderReceiver.EXTRA_TIME_TAG, timeTag)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        timeTag.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

/**
 * Отменить будильник для указанного тайм-тега.
 */
fun cancelAlarmReminder(context: Context, timeTag: String) {
    AppLogger.d("ReminderScheduler", "Отмена будильника: $timeTag")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_TIME_TAG, timeTag)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        timeTag.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

/**
 * Финальное напоминание в 23:00 через AlarmManager.
 * При срабатывании показывает AlarmActivity с текстом "Есть потоки требующие внимания: …",
 * если хотя бы один поток требует действия.
 * Перепланируется на следующий день после срабатывания.
 */
fun scheduleFinalReminder(context: Context) {
    AppLogger.d("ReminderScheduler", "Планирование финального будильника 23:00")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_TIME_TAG, AlarmReceiver.FINAL_2300_TAG)
    }
    val operation = PendingIntent.getBroadcast(
        context,
        AlarmReceiver.FINAL_2300_TAG.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
    val showIntent = PendingIntent.getActivity(
        context,
        AlarmReceiver.FINAL_2300_TAG.hashCode() + 5000,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, operation)
        AppLogger.d("ReminderScheduler", "Финальный будильник 23:00: нет SCHEDULE_EXACT_ALARM, fallback на inexact")
    } else {
        val alarmClockInfo = AlarmManager.AlarmClockInfo(target.timeInMillis, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operation)
        AppLogger.d("ReminderScheduler", "Финальный будильник 23:00: setAlarmClock на ${target.timeInMillis}")
    }
}
