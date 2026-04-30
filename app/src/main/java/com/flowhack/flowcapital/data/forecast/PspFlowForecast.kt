package com.flowhack.flowcapital.data.forecast

import timber.log.Timber
import java.util.Calendar

/**
 * Рассчитывает прогноз ПСП (Премиум Стартовый Поток).
 *
 * Логика по ТЗ:
 * - 20 периодов по 14 дней
 * - Коэффициенты из БД Настроек
 * - Считается, что взнос делается идеально день в день
 *
 * @param nominal Номинал (сумма взноса)
 * @param startDateMillis Дата старта первого периода (timestamp)
 * @param coefficients Карта коэффициентов по периодам (1-20)
 * @return Список записей прогноза (20 периодов)
 */
fun calculatePspForecast(
    nominal: Double,
    startDateMillis: Long,
    coefficients: Map<Int, Double>
): List<PspForecastResult> {
    Timber.d("Начало прогноза ПСП: nominal=%.2f", nominal)
    Timber.d("Старт: ${formatDate(startDateMillis)}")

    val results = mutableListOf<PspForecastResult>()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startDateMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    var totalAccrued = 0.0
    var currentNominal = nominal

    for (periodNum in 1..20) {
        val percent = coefficients[periodNum] ?: 0.0
        val accrual = currentNominal * (percent / 100.0)
        totalAccrued += accrual

        // Дата окончания периода = startDate + (periodNum * 14 дней)
        val periodEndDate = Calendar.getInstance().apply {
            timeInMillis = startDateMillis
            add(Calendar.DAY_OF_YEAR, (periodNum * 14))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        results.add(PspForecastResult(
            periodNumber = periodNum,
            startDate = calendar.timeInMillis,
            endDate = periodEndDate,
            nominal = currentNominal,
            percent = percent,
            accrualAmount = accrual,
            totalAccrued = totalAccrued,
            isCompleted = periodNum == 20
        ))

        Timber.v("ПСП Период $periodNum: nominal=%.2f, percent=%.2f, accrual=%.2f, total=%.2f",
            currentNominal, percent, accrual, totalAccrued)

        // Переход к следующему периоду (номинал остается тот же, но в реальности добавляется)
        // Для прогноза считаем что номинал тот же (или можно предположить реинвест)
        // По ТЗ "идеально день в день" - значит номинал тот же
        calendar.add(Calendar.DAY_OF_YEAR, 14)
    }

    Timber.d("Прогноз ПСП завершен: периодов=${results.size}, всего начислено=%.2f", totalAccrued)
    return results
}

/**
 * Результат прогноза одного периода Премиум Стартового Потока (ПСП).
 * Используется для отображения прогноза и экспорта в Excel.
 *
 * @property periodNumber Номер периода (1-20, согласно ТЗ)
 * @property startDate Дата начала периода (timestamp, начало 14-дневного цикла)
 * @property endDate Дата окончания периода (startDate + 14 дней)
 * @property nominal Номинал потока (сумма взноса за период)
 * @property percent Процент начисления для этого периода (из настроек коэффициентов)
 * @property accrualAmount Сумма начисления за период (nominal * percent / 100)
 * @property totalAccrued Общая сумма начислений за все периоды включая текущий
 * @property isCompleted true если это 20-й период (последний)
 */
data class PspForecastResult(
    val periodNumber: Int,
    val startDate: Long,
    val endDate: Long,
    val nominal: Double,
    val percent: Double,
    val accrualAmount: Double,
    val totalAccrued: Double,
    val isCompleted: Boolean
)

/**
 * Форматирует timestamp в читаемую строку даты (ДД.ММ.ГГГГ) для логирования.
 * Использует системный календарь для преобразования.
 *
 * @param millis Время в миллисекундах
 * @return Отформатированная строка даты
 */
private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
}