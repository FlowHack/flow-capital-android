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

    /** Окно активности кнопки: 24 часа после последнего клика */
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Окно (в мс) вокруг момента активации кнопки, в котором показывается сообщение «Вчера в это время…». */
    private const val ACTIVATION_WINDOW_MILLIS = 60 * 60 * 1000L

    /**
     * Проверить все потоки и собрать список сообщений о требуемых действиях.
     * Логика:
     * - ПСП работает даже в воскресенье
     * - РП/ПН не работают в воскресенье
     * - Для РП/ПН проверяется, была ли нажата кнопка сегодня
     * - Для ПСП проверяется, не пора ли сделать взнос номинала
     * - При smartNotifications=true для РП/ПН напоминание срабатывает только через 24 часа
     *   после последнего клика по кнопке, а в момент активации кнопки показывается
     *   сообщение «Вчера в это время вы выполнили действия по РП/ПН потоку»
     *
     * @param smartNotifications Включён ли режим умных уведомлений
     * @param currentTimeMillis Текущее время (для тестируемости). По умолчанию System.currentTimeMillis()
     * @return Список строк с описанием требуемых действий, или пустой список если действий нет
     */
    suspend fun buildReminderMessages(
        context: Context,
        smartNotifications: Boolean = false,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<String> {
        AppLogger.d("ReminderMessageBuilder", "Проверка потоков на требуемые действия (smart=$smartNotifications)")
        val db = AppDatabase.getDatabase(context)
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
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
                    val now = currentTimeMillis
                    // Нормализуем endDate к началу дня: кнопка ПСП активна с начала дня окончания периода
                    val periodEndDayStart = Calendar.getInstance().apply {
                        timeInMillis = currentPeriod.endDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (now >= periodEndDayStart) {
                        pspNeedsAction = true
                        messages.add("ПСП - взнос номинала")
                        AppLogger.d("ReminderMessageBuilder",
                            "ПСП поток id=${flow.id}: период=${currentPeriod.periodNumber}, " +
                                "endDate=${currentPeriod.endDate}, взнос не сделан, дата наступила")
                    } else {
                        AppLogger.d("ReminderMessageBuilder",
                            "ПСП поток id=${flow.id}: период=${currentPeriod.periodNumber}, " +
                                "endDate=${currentPeriod.endDate}, дата взноса ещё не наступила — пропуск")
                    }
                } else {
                    AppLogger.d("ReminderMessageBuilder",
                        "ПСП поток id=${flow.id}: период=${currentPeriod?.periodNumber}, " +
                            "взнос сделан или период отсутствует — пропуск")
                }
            } else {
                AppLogger.d("ReminderMessageBuilder", "ПСП поток id=${flow.id}: неактивен — пропуск")
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
            if (smartNotifications) {
                // Умный режим: напоминаем, только когда кнопка снова активна и ещё не нажата сегодня
                val lastPress = db.growingFlowDao().getLastPressEntry()
                val now = currentTimeMillis
                val isReadyForPress = lastPress == null || now >= lastPress.date + DAY_MILLIS
                if (isReadyForPress && !isPressedToday && !isSunday) {
                    // В момент активации кнопки показываем сообщение «Вчера в это время…»
                    if (lastPress != null && now < lastPress.date + DAY_MILLIS + ACTIVATION_WINDOW_MILLIS) {
                        messages.add("Вчера в это время вы выполнили действия по РП потоку")
                    } else {
                        messages.add("РП - нажмите кнопку")
                    }
                }
            } else {
                if (!isPressedToday && !isSunday) {
                    messages.add("РП - нажмите кнопку")
                }
            }
        }

        // Проверка Потока Новичка
        val lastNoviceEntry = db.noviceFlowDao().getLastEntry()
        if (lastNoviceEntry != null) {
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastNoviceEntry.date }
            val isPressedToday = lastNoviceEntry.isButtonPressed &&
                today == lastCal.get(Calendar.DAY_OF_YEAR) &&
                year == lastCal.get(Calendar.YEAR)
            if (smartNotifications) {
                // Умный режим: напоминаем, только когда кнопка снова активна и ещё не нажата сегодня
                val lastPress = db.noviceFlowDao().getLastPressEntry()
                val now = currentTimeMillis
                val isReadyForPress = lastPress == null || now >= lastPress.date + DAY_MILLIS
                if (isReadyForPress && !isPressedToday && !isSunday) {
                    // В момент активации кнопки показываем сообщение «Вчера в это время…»
                    if (lastPress != null && now < lastPress.date + DAY_MILLIS + ACTIVATION_WINDOW_MILLIS) {
                        messages.add("Вчера в это время вы выполнили действия по ПН потоку")
                    } else {
                        messages.add("ПН - нажмите кнопку")
                    }
                }
            } else {
                if (!isPressedToday && !isSunday) {
                    messages.add("ПН - нажмите кнопку")
                }
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
