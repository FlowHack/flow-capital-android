package com.example.flowcapital.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.flowcapital.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

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
        val db = AppDatabase.getDatabase(applicationContext)
        val calendar = Calendar.getInstance()
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val messages = mutableListOf<String>()
        var hasAnyAction = false

        // Проверка нужно ли действие для ПСП (работает и в воскресенье!)
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

        // Воскресенье - проверяем что делать: если есть ПСП - работаем, если нет - пропускаем
        if (isSunday && !pspNeedsAction) {
            // Воскресенье, нет ПСП требующего действия - выходной для Н/РП
            return Result.success()
        }

        // Проверка Растущего Потока (ПН в воскресенье не работает!)
        val lastGrowingEntry = db.growingFlowDao().getLastEntry()
        if (lastGrowingEntry != null) {
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastGrowingEntry.date }
            val isPressedToday = lastGrowingEntry.isButtonPressed &&
                today == lastCal.get(Calendar.DAY_OF_YEAR) &&
                year == lastCal.get(Calendar.YEAR)

            // Воскресенье - пропускаем для РП/ПН
            if (!isPressedToday && !isSunday) {
                messages.add("Растущий Поток - нажмите кнопку")
                hasAnyAction = true
            }
        }

        // Проверка Потока Новичка (в воскресенье не работает!)
        val lastNoviceEntry = db.noviceFlowDao().getLastEntry()
        if (lastNoviceEntry != null) {
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastNoviceEntry.date }
            val isPressedToday = lastNoviceEntry.isButtonPressed &&
                today == lastCal.get(Calendar.DAY_OF_YEAR) &&
                year == lastCal.get(Calendar.YEAR)

            // Воскресенье - пропускаем для ПН
            if (!isPressedToday && !isSunday) {
                messages.add("Поток Новичка - нажмите кнопку")
                hasAnyAction = true
            }
        }

        // Отправляем уведомление только если есть что напомнить
        if (hasAnyAction) {
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

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText("• $text"))
            .build()

        manager.notify(1, notification)
    }
}
