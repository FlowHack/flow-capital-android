package com.example.flowcapital.data.forecast

import com.example.flowcapital.data.db.GrowingFlowEntity
import timber.log.Timber
import java.util.Calendar

/**
 * Рассчитывает прогноз Растущего потока (РП).
 *
 * Логика по ТЗ:
 * - Старт: записывается с процентом, в потоке, начислением, кошельком
 * - Если не воскресенье: в день старта СРАЗУ делается начисление (кнопка нажата)
 * - Каждый день (кроме воскресений): кнопка нажимается, процент растет на dailyAddition
 * - Воскресенье: SUNDAY запись без начисления
 * - Прогноз останавливается при inFlow <= 0
 *
 * @param inFlow Начальная сумма в потоке с бонусом
 * @param percent Стартовый процент
 * @param wallet Начальный кошелек
 * @param startDateMillis Дата старта (timestamp)
 * @param targetDateMillis Дата окончания прогноза (timestamp)
 * @param dailyAddition Ежедневный добавочный процент
 * @param isExistingFlow true если это расчет действующего потока (START в текущий день, DAILY только со следующего)
 * @return Список записей GrowingFlowEntity для совместимости с БД
 */
fun calculateFlowForecast(
    inFlow: Double,
    percent: Double,
    wallet: Double,
    startDateMillis: Long,
    targetDateMillis: Long,
    dailyAddition: Double,
    isExistingFlow: Boolean = false
): List<GrowingFlowEntity> {
    Timber.d("Начало прогноза: inFlow=%.2f, percent=%.3f, wallet=%.2f", inFlow, percent, wallet)
    Timber.d("Период: ${formatDate(startDateMillis)} - ${formatDate(targetDateMillis)}")

    val results = mutableListOf<GrowingFlowEntity>()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = targetDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var simInFlow = inFlow
    var simPercent = percent
    var simWallet = wallet
    var simAccrual = if (inFlow > 0 && percent > 0) inFlow * (percent / 100.0) else 0.0
    var step = 1

    // Запись START (всегда)
    results.add(GrowingFlowEntity(
        date = calendar.timeInMillis,
        percent = simPercent,
        inFlowAmount = simInFlow,
        dailyAccrual = simAccrual,
        walletAmount = simWallet,
        isButtonPressed = true,
        actionType = "START"
    ))
    Timber.v("START: step=%d, percent=%.3f, inFlow=%.2f, accrual=%.3f, wallet=%.2f",
        step, simPercent, simInFlow, simAccrual, simWallet)

    // Для нового потока: если не воскресенье - сразу делаем DAILY в день старта
    // Для действующего потока: DAILY начинается только со следующего дня
    if (!isExistingFlow && calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        val actualAccrual = minOf(simInFlow, simAccrual)
        simInFlow -= actualAccrual
        if (simInFlow < 0) simInFlow = 0.0
        simWallet += actualAccrual

        if (simInFlow > 0) {
            simPercent += dailyAddition
            simAccrual = simInFlow * (simPercent / 100.0)
        }

        step++

        results.add(GrowingFlowEntity(
            date = calendar.timeInMillis,
            percent = simPercent,
            inFlowAmount = simInFlow,
            dailyAccrual = simAccrual,
            walletAmount = simWallet,
            isButtonPressed = true,
            actionType = "DAILY"
        ))
        Timber.v("DAILY (start day): step=%d, percent=%.3f, inFlow=%.2f, accrual=%.3f, wallet=%.2f",
            step, simPercent, simInFlow, simAccrual, simWallet)
    }

    calendar.add(Calendar.DAY_OF_YEAR, 1)

    // Основной цикл
    while (calendar.timeInMillis <= endCal.timeInMillis && simInFlow > 0) {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isSunday = dayOfWeek == Calendar.SUNDAY

        if (isSunday) {
            results.add(GrowingFlowEntity(
                date = calendar.timeInMillis,
                percent = simPercent,
                inFlowAmount = simInFlow,
                dailyAccrual = simAccrual,
                walletAmount = simWallet,
                isButtonPressed = false,
                actionType = "SUNDAY"
            ))
            Timber.v("SUNDAY: ${formatDate(calendar.timeInMillis)}, step=$step")
        } else {
            val actualAccrual = minOf(simInFlow, simAccrual)
            simInFlow -= actualAccrual
            if (simInFlow < 0) simInFlow = 0.0
            simWallet += actualAccrual

            if (simInFlow > 0) {
                simPercent += dailyAddition
                simAccrual = simInFlow * (simPercent / 100.0)
            }

            step++

            results.add(GrowingFlowEntity(
                date = calendar.timeInMillis,
                percent = simPercent,
                inFlowAmount = simInFlow,
                dailyAccrual = simAccrual,
                walletAmount = simWallet,
                isButtonPressed = true,
                actionType = "DAILY"
            ))
            Timber.v("DAILY: step=%d, percent=%.3f, inFlow=%.2f, accrual=%.3f, wallet=%.2f",
                step, simPercent, simInFlow, simAccrual, simWallet)
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    Timber.d("Прогноз завершен: записей=${results.size}, последний step=$step")
    return results
}

/**
 * Форматирует дату для логов.
 */
private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
}