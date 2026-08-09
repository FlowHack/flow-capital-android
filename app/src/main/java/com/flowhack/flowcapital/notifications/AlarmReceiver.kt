package com.flowhack.flowcapital.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.flowhack.flowcapital.AlarmActivity
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmReceiver:alarm"
        )
        wakeLock.acquire(10_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _onReceive(context, intent)
            } catch (e: Exception) {
                AppLogger.e("AlarmReceiver", "Ошибка обработки будильника", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private suspend fun _onReceive(context: Context, intent: Intent) {
        val timeTag = intent.getStringExtra(EXTRA_TIME_TAG) ?: run {
            AppLogger.e("AlarmReceiver", "Получен Intent без timeTag")
            return
        }

        AppLogger.d("AlarmReceiver", "Сработал будильник для timeTag=$timeTag")

        // Перепланирование будильника в начале, до тяжёлых запросов
        val isFinal = timeTag == FINAL_2300_TAG
        if (isFinal) {
            AppLogger.d("AlarmReceiver", "Финальный будильник 23:00 — перепланирование на завтра")
            scheduleFinalReminder(context)
        } else {
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

        val settingsManager = SettingsManager(context)
        val smartNotifications = settingsManager.getSmartNotifications()
        val messages = ReminderMessageBuilder.buildReminderMessages(context, smartNotifications)

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
        const val ALARM_NOTIFICATION_ID = 1001
    }
}


