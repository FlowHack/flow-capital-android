package com.flowhack.flowcapital.notifications

import android.content.Context
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.logging.AppLogger
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Класс для построения сообщений напоминаний о требуемых действиях в потоках.
 * Используется как в AlarmReceiver, так и в ReminderWorker.
 */
object ReminderMessageBuilder {

    /**
     * Проверить все потоки и собрать список сообщений о требуемых действиях.
     * Логика:
     * - ПСП работает даже в воскресенье
     * - РП/ПН не работают в воскресенье
     * - Для РП/ПН проверяется, была ли нажата кнопка сегодня
     * - Для ПСП проверяется, не пора ли сделать взнос номинала
     *
     * @return Список строк с описанием требуемых действий, или пустой список если действий нет
     */
    suspend fun buildReminderMessages(context: Context): List<String> {
        AppLogger.d("ReminderMessageBuilder", "Проверка потоков на требуемые действия")
        val db = AppDatabase.getDatabase(context)
        val calendar = Calendar.getInstance()
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val messages = mutableListOf<String>()

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
                    }
                }
            }
        }

        // Воскресенье без ПСП — выходной для РП/ПН
        if (isSunday && !pspNeedsAction) {
            AppLogger.d("ReminderMessageBuilder", "Воскресенье, действий нет")
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
            }
        }

        if (messages.isEmpty()) {
            AppLogger.d("ReminderMessageBuilder", "Все действия выполнены")
            return emptyList()
        }

        AppLogger.d("ReminderMessageBuilder", "Требуются действия: ${messages.size}")
        return messages
    }
}
