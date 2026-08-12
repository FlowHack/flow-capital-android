package com.flowhack.flowcapital.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.flowhack.flowcapital.MainActivity
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver для тихих напоминаний (обычные уведомления, не будильники).
 *
 * Планируется через AlarmManager (надёжная доставка даже при убитом приложении),
 * но в отличие от [AlarmReceiver] показывает обычное уведомление без полноэкранного
 * будильника и без звука будильника.
 *
 * При получении сигнала:
 * 1. Перепланирует напоминание на следующий день
 * 2. Проверяет все потоки через buildReminderMessages()
 * 3. Если действий нет — тихий выход
 * 4. Иначе показывает обычное уведомление
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ReminderReceiver:reminder"
        )
        wakeLock.acquire(10_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _onReceive(context, intent)
            } catch (e: Exception) {
                AppLogger.e("ReminderReceiver", "Ошибка обработки напоминания", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private suspend fun _onReceive(context: Context, intent: Intent) {
        val timeTag = intent.getStringExtra(EXTRA_TIME_TAG) ?: run {
            AppLogger.e("ReminderReceiver", "Получен Intent без timeTag")
            return
        }

        AppLogger.d("ReminderReceiver", "Сработало напоминание для timeTag=$timeTag")

        // Перепланирование напоминания на следующий день
        val parts = timeTag.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull()
            val min = parts[1].toIntOrNull()
            if (hour != null && min != null) {
                AppLogger.d("ReminderReceiver", "Перепланирование напоминания $timeTag на завтра")
                scheduleDailyReminder(context, hour, min, timeTag)
            }
        }

        val settingsManager = SettingsManager(context)
        val smartNotifications = settingsManager.getSmartNotifications()
        val messages = ReminderMessageBuilder.buildReminderMessages(context, smartNotifications)

        if (messages.isEmpty()) {
            AppLogger.d("ReminderReceiver", "Действия не требуются — тихий выход")
            return
        }

        AppLogger.d("ReminderReceiver", "Показ уведомления: ${messages.joinToString(", ")}")

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "potok_reminders"
        val channel = NotificationChannel(channelId, "Напоминания Потока", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, timeTag.hashCode(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Требуется действие!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("• ${messages.joinToString("\n• ")}"))
            .build()

        manager.notify(timeTag.hashCode(), notification)
    }

    companion object {
        const val EXTRA_TIME_TAG = "timeTag"
    }
}
