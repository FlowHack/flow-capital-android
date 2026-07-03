package com.flowhack.flowcapital.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flowhack.flowcapital.AlarmActivity
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * BroadcastReceiver для обработки срабатывания будильников (AlarmManager).
 *
 * При получении сигнала:
 * 1. Проверяет все потоки через buildReminderMessages()
 * 2. Если действий нет — тихий выход (для FINAL_2300_TAG всё равно перепланирует на завтра)
 * 3. Формирует текст и показывает AlarmActivity через FullScreenIntent (Android 10+)
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timeTag = intent.getStringExtra(EXTRA_TIME_TAG) ?: run {
            AppLogger.e("AlarmReceiver", "Получен Intent без timeTag")
            return
        }

        AppLogger.d("AlarmReceiver", "Сработал будильник для timeTag=$timeTag")

        val messages = runBlocking(Dispatchers.IO) {
            buildReminderMessages(context)
        }

        val isFinal = timeTag == FINAL_2300_TAG

        // FINAL_2300_TAG всегда перепланируется на завтра, даже если действий нет
        if (isFinal) {
            AppLogger.d("AlarmReceiver", "Финальный будильник 23:00 — перепланирование на завтра")
            scheduleFinalReminder(context)
        } else {
            // Обычный будильник — перепланировать на следующий день
            val parts = timeTag.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull()
                val min = parts[1].toIntOrNull()
                if (hour != null && min != null) {
                    AppLogger.d("AlarmReceiver", "Перепланирование будильника $timeTag на завтра")
                    scheduleAlarmReminder(context, hour, min, timeTag)
                }
            }
        }

        if (messages.isEmpty()) {
            AppLogger.d("AlarmReceiver", "Действия не требуются — тихий выход")
            return
        }

        val alarmText = if (isFinal) {
            "Есть потоки требующие внимания:\n• " + messages.joinToString("\n• ")
        } else {
            "• " + messages.joinToString("\n• ")
        }

        AppLogger.d("AlarmReceiver", "Показ AlarmActivity: $alarmText")

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_ALARM_TEXT, alarmText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val channelId = "potok_alarm"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Будильник", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getActivity(
                context, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (isFinal) "Внимание! Действие требуется" else "Будильник")
                .setContentText(messages.joinToString(", "))
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            manager.notify(ALARM_NOTIFICATION_ID, notification)
        } else {
            context.startActivity(alarmIntent)
        }
    }

    companion object {
        const val EXTRA_TIME_TAG = "timeTag"
        const val FINAL_2300_TAG = "final_2300"
        private const val ALARM_NOTIFICATION_ID = 1001
    }
}

/**
 * Проверить все потоки и собрать список сообщений о требуемых действиях.
 * Логика идентична ReminderWorker.doWork().
 *
 * @return Список строк с описанием требуемых действий, или пустой список если действий нет
 */
private suspend fun buildReminderMessages(context: Context): List<String> {
    AppLogger.d("buildReminderMessages", "Проверка потоков на требуемые действия")
    val db = AppDatabase.getDatabase(context)
    val calendar = Calendar.getInstance()
    val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    val messages = mutableListOf<String>()
    var hasAnyAction = false

    // Проверка ПСП — работает даже в воскресенье
    var pspNeedsAction = false
    val allPspFlows = db.premiumStartFlowDao().getAllFlows().first()
    for (flow in allPspFlows) {
        if (flow.isActive) {
            val currentPeriod = db.premiumStartPeriodDao().getCurrentPeriod(flow.id)
            if (currentPeriod != null && !currentPeriod.isContributionMade) {
                val now = Calendar.getInstance().timeInMillis
                if (now >= currentPeriod.endDate) {
                    pspNeedsAction = true
                    messages.add("ПСП - взнос номинала")
                    hasAnyAction = true
                }
            }
        }
    }

    // Воскресенье без ПСП — выходной для РП/ПН
    if (isSunday && !pspNeedsAction) {
        AppLogger.d("buildReminderMessages", "Воскресенье, действий нет")
        return emptyList()
    }

    // Проверка Растущего Потока
    val lastGrowingEntry = db.growingFlowDao().getLastEntry()
    if (lastGrowingEntry != null) {
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastGrowingEntry.date }
        val isPressedToday = lastGrowingEntry.isButtonPressed &&
            today == lastCal.get(Calendar.DAY_OF_YEAR) &&
            year == lastCal.get(Calendar.YEAR)
        if (!isPressedToday && !isSunday) {
                    messages.add("РП - нажмите кнопку")
            hasAnyAction = true
        }
    }

    // Проверка Потока Новичка
    val lastNoviceEntry = db.noviceFlowDao().getLastEntry()
    if (lastNoviceEntry != null) {
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastNoviceEntry.date }
        val isPressedToday = lastNoviceEntry.isButtonPressed &&
            today == lastCal.get(Calendar.DAY_OF_YEAR) &&
            year == lastCal.get(Calendar.YEAR)
        if (!isPressedToday && !isSunday) {
                    messages.add("ПН - нажмите кнопку")
            hasAnyAction = true
        }
    }

    if (!hasAnyAction) {
        AppLogger.d("buildReminderMessages", "Все действия выполнены")
        return emptyList()
    }

    AppLogger.d("buildReminderMessages", "Требуются действия: ${messages.size}")
    return messages
}
