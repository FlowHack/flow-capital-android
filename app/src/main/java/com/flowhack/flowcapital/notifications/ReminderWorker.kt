package com.flowhack.flowcapital.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.flow.first

/**
 * Worker для отправки напоминаний о требуемых действиях.
 *
 * Логика работы:
 * - Проверяет все потоки на предмет невыполненных действий
 * - Воскресенье: выходной для ПН/РП, но рабочий для ПСП!
 * - Для РП/ПН: проверяет была ли нажата кнопка сегодня
 * - Для ПСП: проверяет не пора ли сделать взнос номинала (14 дней, без выходных)
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.d("ReminderWorker", "Запуск проверки напоминаний")

        val settingsManager = SettingsManager(applicationContext)

        // Если напоминание в режиме будильника (AlarmManager) — WorkManager не должен дублировать
        val timeTag = inputData.getString("timeTag")
        if (timeTag != null) {
            val alarmSet = settingsManager.alarmRemindersFlow.first()
            if (timeTag in alarmSet) {
                AppLogger.d("ReminderWorker", "Напоминание $timeTag в режиме будильника — пропуск")
                return Result.success()
            }
        }

        // Учитываем режим умных уведомлений при построении сообщений
        val smartNotifications = settingsManager.getSmartNotifications()
        val messages = ReminderMessageBuilder.buildReminderMessages(
            applicationContext, smartNotifications
        )

        // Отправляем уведомление только если есть что напомнить
        if (messages.isNotEmpty()) {
            sendNotification(messages)
        }

        return Result.success()
    }

    /**
     * Отправляет уведомление со списком требуемых действий.
     *
     * @param messages Список сообщений для каждого потока
     */
    private fun sendNotification(messages: List<String>) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "potok_reminders"

        val channel = NotificationChannel(channelId, "Напоминания Потока", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val title = "Требуется действие!"
        val text = messages.joinToString("\n• ")

        val launchIntent = Intent(applicationContext, com.flowhack.flowcapital.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("• $text"))
            .build()

        manager.notify(1, notification)
    }
}
