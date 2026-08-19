package com.flowhack.flowcapital.data.forecast

import com.flowhack.flowcapital.data.db.FastFlowDayEntity
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

/**
 * Чистые функции расчёта Быстрого Потока (БП) и Супер Быстрого Потока (СБП).
 *
 * Логика по ТЗ:
 * - БП длится 30 дней, СБП — 15 дней.
 * - Итоговая сумма = номинал * (1 + процент/100), где процент — итоговый прирост из таблицы.
 * - Ежедневное начисление = итоговая сумма / количество дней (равные части).
 * - Воскресенья — выходные (SUNDAY записи без начисления), растягивают календарь.
 * - Пропущенный день не теряется: начисление получается при следующем нажатии.
 * - Последний день корректируется, чтобы сумма всех дней точно сошлась с итоговой суммой.
 *
 * Все расчёты выполняются с округлением до 2 знаков (BigDecimal) для исключения
 * floating point ошибок (правило CONVENTIONS).
 */

/** Тип Быстрого Потока. */
const val FAST_FLOW_TYPE_BP = "BP"

/** Тип Супер Быстрого Потока. */
const val FAST_FLOW_TYPE_SBP = "SBP"

/**
 * Возвращает количество дней потока по типу.
 * @param type Тип потока ("BP" или "SBP")
 * @return 30 для БП, 15 для СБП
 */
fun getFastFlowDayCount(type: String): Int = if (type == FAST_FLOW_TYPE_BP) 30 else 15

/**
 * Округляет значение до 2 знаков после запятой (BigDecimal).
 * @param value Исходное значение
 * @return Округлённое значение
 */
fun round2(value: Double): Double =
    BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

/**
 * Определяет процент прироста по номиналу из таблицы коэффициентов.
 * Берётся максимальный порог, не превышающий номинал.
 * Если номинал ниже минимального порога — возвращается 0.0.
 *
 * @param nominal Номинал потока
 * @param coefficients Карта пороговых сумм к процентам (порог -> процент)
 * @return Процент прироста
 */
fun getFastFlowPercentForNominal(nominal: Double, coefficients: Map<Double, Double>): Double {
    if (nominal <= 0) return 0.0
    val best = coefficients.entries
        .filter { it.key <= nominal }
        .maxByOrNull { it.key }
    return best?.value ?: 0.0
}

/**
 * Рассчитывает ежедневное начисление.
 * dailyAccrual = номинал * (1 + процент/100) / количество дней.
 *
 * @param nominal Номинал потока
 * @param percent Итоговый процент прироста
 * @param type Тип потока ("BP" или "SBP")
 * @return Ежедневное начисление (округлено до 2 знаков)
 */
fun calculateFastFlowDailyAccrual(nominal: Double, percent: Double, type: String): Double {
    val dayCount = getFastFlowDayCount(type)
    if (dayCount <= 0) return 0.0
    val total = nominal * (1.0 + percent / 100.0)
    return round2(total / dayCount)
}

/**
 * Рассчитывает полный прогноз потока (БП/СБП) с учётом воскресений.
 *
 * Генерирует dayCount «рабочих» дней (нажатий кнопки), вставляя SUNDAY записи
 * на воскресенья. Последний рабочий день корректируется, чтобы сумма всех
 * начислений точно сошлась с итоговой суммой (номинал * (1 + процент/100)).
 *
 * @param nominal Номинал потока
 * @param percent Итоговый процент прироста
 * @param type Тип потока ("BP" или "SBP")
 * @param startDateMillis Дата старта (timestamp)
 * @return Список записей дней прогноза
 */
fun calculateFastFlowForecast(
    nominal: Double,
    percent: Double,
    type: String,
    startDateMillis: Long
): List<FastFlowDayEntity> {
    val dayCount = getFastFlowDayCount(type)
    val dailyAccrual = calculateFastFlowDailyAccrual(nominal, percent, type)
    val total = round2(nominal * (1.0 + percent / 100.0))

    Timber.d("Прогноз БП/СБП: type=$type, nominal=%.2f, percent=%.2f, daily=%.2f, total=%.2f",
        nominal, percent, dailyAccrual, total)

    val results = mutableListOf<FastFlowDayEntity>()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var dayNumber = 1
    var accruedSum = 0.0

    while (dayNumber <= dayCount) {
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (isSunday) {
            results.add(FastFlowDayEntity(
                flowId = 0,
                dayNumber = dayNumber,
                date = calendar.timeInMillis,
                accrualAmount = 0.0,
                isButtonPressed = false,
                actionType = "SUNDAY"
            ))
        } else {
            // Последний рабочий день — корректируем, чтобы сумма сошлась с итогом
            val isLastDay = dayNumber == dayCount
            val accrual = if (isLastDay) {
                round2(total - accruedSum)
            } else {
                dailyAccrual
            }
            accruedSum = round2(accruedSum + accrual)

            results.add(FastFlowDayEntity(
                flowId = 0,
                dayNumber = dayNumber,
                date = calendar.timeInMillis,
                accrualAmount = accrual,
                isButtonPressed = true,
                actionType = "DAILY"
            ))
            dayNumber++
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    Timber.d("Прогноз БП/СБП завершён: записей=${results.size}, сумма начислений=%.2f", accruedSum)
    return results
}

/**
 * Рассчитывает дату закрытия потока относительно текущей даты.
 * Закрытие = текущая дата + оставшиеся рабочие дни + воскресенья в диапазоне.
 *
 * @param todayMillis Текущая дата (timestamp)
 * @param currentDay Текущий день потока (1..dayCount)
 * @param type Тип потока ("BP" или "SBP")
 * @return Timestamp даты закрытия
 */
fun calculateFastFlowCloseDate(todayMillis: Long, currentDay: Int, type: String): Long {
    val dayCount = getFastFlowDayCount(type)
    val remaining = dayCount - currentDay
    if (remaining <= 0) return todayMillis

    val calendar = Calendar.getInstance().apply {
        timeInMillis = todayMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, 1)
    }

    var presses = 0
    while (presses < remaining) {
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            presses++
        }
        if (presses < remaining) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return calendar.timeInMillis
}

/**
 * Генерирует прошлые дни потока при создании с currentDay > 1.
 * Дни 1..currentDay-1 считаются нажатыми (DAILY), воскресенья — SUNDAY.
 *
 * @param startDateMillis Дата старта потока (timestamp)
 * @param currentDay Текущий день (1..dayCount)
 * @param type Тип потока ("BP" или "SBP")
 * @param dailyAccrual Ежедневное начисление
 * @return Список записей прошлых дней
 */
fun generateFastFlowPastDays(
    startDateMillis: Long,
    currentDay: Int,
    type: String,
    dailyAccrual: Double
): List<FastFlowDayEntity> {
    val days = mutableListOf<FastFlowDayEntity>()
    if (currentDay <= 1) return days

    val calendar = Calendar.getInstance().apply {
        timeInMillis = startDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var dayNumber = 1
    while (dayNumber < currentDay) {
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (isSunday) {
            days.add(FastFlowDayEntity(
                flowId = 0,
                dayNumber = dayNumber,
                date = calendar.timeInMillis,
                accrualAmount = 0.0,
                isButtonPressed = false,
                actionType = "SUNDAY"
            ))
        } else {
            val isFirstDay = dayNumber == 1
            days.add(FastFlowDayEntity(
                flowId = 0,
                dayNumber = dayNumber,
                date = calendar.timeInMillis,
                accrualAmount = dailyAccrual,
                isButtonPressed = true,
                actionType = if (isFirstDay) "START" else "DAILY"
            ))
            dayNumber++
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}
