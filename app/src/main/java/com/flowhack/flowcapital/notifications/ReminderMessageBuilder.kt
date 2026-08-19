package com.flowhack.flowcapital.notifications

import android.content.Context
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.FastFlowEntity
import com.flowhack.flowcapital.data.forecast.FAST_FLOW_TYPE_BP
import com.flowhack.flowcapital.data.forecast.buildFastFlowTitle
import com.flowhack.flowcapital.data.forecast.getFastFlowDayCount
import com.flowhack.flowcapital.data.logging.AppLogger
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Класс для построения сообщений напоминаний о требуемых действиях в потоках.
 * Используется в AlarmReceiver и ReminderReceiver.
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
     *   сообщение «Вчера в это время вы выполнили действия по РП потоку»
     *   или «Вчера в это время вы выполнили действия по ПН потоку»
     * - Для БП/СБП в момент активации формируется сообщение «Вчера в это время Вы
     *   выполнили действие по СБП «СБП 18.08.2026»»; если несколько потоков нажаты
     *   в пределах ±1 минуты, события объединяются в одно сообщение с перечнем типов
     *   («Вчера в это время Вы выполнили действие по СБП, БП»)
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

        // Проверка Быстрого Потока (БП/СБП) — ежедневная кнопка, воскресенье выходной.
        // События «Вчера в это время…» группируются по времени последнего нажатия
        // (в пределах ±1 минуты), чтобы несколько потоков не спамили сообщениями.
        val allFastFlows = db.fastFlowDao().getAllFlows().first()
        val fastActivations = mutableListOf<Pair<FastFlowEntity, Long>>()
        val fastPressMessages = mutableListOf<String>()

        for (flow in allFastFlows) {
            if (!flow.isActive) continue
            val dayCount = getFastFlowDayCount(flow.type)
            if (flow.currentDay > dayCount) continue
            val flowLabel = if (flow.type == FAST_FLOW_TYPE_BP) "БП" else "СБП"

            val lastDay = db.fastFlowDayDao().getLastPressEntry(flow.id)
            val isPressedToday = lastDay != null && lastDay.isButtonPressed &&
                today == Calendar.getInstance().apply { timeInMillis = lastDay.date }.get(Calendar.DAY_OF_YEAR) &&
                year == Calendar.getInstance().apply { timeInMillis = lastDay.date }.get(Calendar.YEAR)

            if (smartNotifications) {
                val now = currentTimeMillis
                val isReadyForPress = lastDay == null || now >= lastDay.date + DAY_MILLIS
                if (isReadyForPress && !isPressedToday && !isSunday) {
                    if (lastDay != null && now < lastDay.date + DAY_MILLIS + ACTIVATION_WINDOW_MILLIS) {
                        // Кнопка снова активна и нажатие было «вчера в это время»
                        fastActivations.add(flow to lastDay.date)
                    } else {
                        fastPressMessages.add("$flowLabel - нажмите кнопку")
                    }
                }
            } else {
                if (!isPressedToday && !isSunday) {
                    fastPressMessages.add("$flowLabel - нажмите кнопку")
                }
            }
            AppLogger.d("ReminderMessageBuilder",
                "$flowLabel поток id=${flow.id}: текущий день=${flow.currentDay}, нажато сегодня=$isPressedToday")
        }

        // Группировка активаций по времени нажатия (±1 минута)
        val sortedActivations = fastActivations.sortedBy { it.second }
        val timeClusters = ReminderGrouping.withPressTimes(sortedActivations.map { it.second })
        timeClusters.forEach { clusterTimes ->
            val clusterEvents = sortedActivations.filter { it.second in clusterTimes }
            if (clusterEvents.isEmpty()) return@forEach
            if (clusterEvents.size == 1) {
                // Один поток — указываем его название: «СБП «СБП 18.08.2026»»
                val (flow, _) = clusterEvents.first()
                val prefix = if (flow.type == FAST_FLOW_TYPE_BP) "БП" else "СБП"
                val title = buildFastFlowTitle(flow, allFastFlows)
                messages.add("Вчера в это время Вы выполнили действие по $prefix «$title»")
            } else {
                // Несколько потоков — перечисляем типы: «по СБП, БП»
                val types = clusterEvents
                    .map { (flow, _) -> if (flow.type == FAST_FLOW_TYPE_BP) "БП" else "СБП" }
                    .distinct()
                    .joinToString(", ")
                messages.add("Вчера в это время Вы выполнили действие по $types")
            }
            AppLogger.d("ReminderMessageBuilder",
                "Группа активаций «вчера в это время»: потоков=${clusterEvents.size}, " +
                    "времена=${clusterTimes.joinToString()}")
        }
        messages.addAll(fastPressMessages)

        if (messages.isEmpty()) {
            AppLogger.d("ReminderMessageBuilder", "Все действия выполнены")
            return emptyList()
        }

        AppLogger.d("ReminderMessageBuilder", "Требуются действия: ${messages.size}")
        return messages
    }
}
