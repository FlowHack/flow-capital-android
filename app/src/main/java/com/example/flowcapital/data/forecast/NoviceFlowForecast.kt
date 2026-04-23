package com.example.flowcapital.data.forecast

import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.NoviceFlowEntity
import timber.log.Timber
import java.util.Calendar

/**
 * Рассчитывает прогноз ПН (Поток Новичка).
 *
 * Логика по ТЗ:
 * - Старт: записывается с процентом, в потоке, начислением, кошельком
 * - Если не воскресенье: в день старта СРАЗУ делается начисление (кнопка нажата)
 * - Каждый день (кроме воскресений): кнопка нажимается
 * - Воскресенье: SUNDAY запись без начисления
 * - Прогноз останавливается при inFlow <= 0
 * - Процент фиксированный (не растет как в РП)
 *
 * @param inFlow Начальная сумма в потоке с бонусом
 * @param dailyPercent Фиксированный ежедневный процент
 * @param wallet Начальный кошелек
 * @param startDateMillis Дата старта (timestamp)
 * @param targetDateMillis Дата окончания прогноза (timestamp)
 * @return Список записей прогноза NoviceFlowEntity
 */
fun calculateNoviceFlowForecast(
    inFlow: Double,
    dailyPercent: Double,
    wallet: Double,
    startDateMillis: Long,
    targetDateMillis: Long
): List<NoviceFlowEntity> {
    Timber.d("Начало прогноза ПН: inFlow=%.2f, percent=%.2f, wallet=%.2f", inFlow, dailyPercent, wallet)
    Timber.d("Период: ${formatDate(startDateMillis)} - ${formatDate(targetDateMillis)}")

    val results = mutableListOf<NoviceFlowEntity>()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = targetDateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var simInFlow = inFlow
    var simWallet = wallet
    var simAccrual = if (inFlow > 0 && dailyPercent > 0) inFlow * (dailyPercent / 100.0) else 0.0
    var step = 1

    // Запись START (всегда)
    results.add(NoviceFlowEntity(
        date = calendar.timeInMillis,
        percent = dailyPercent,
        inFlowAmount = simInFlow,
        dailyAccrual = simAccrual,
        walletAmount = simWallet,
        isButtonPressed = true,
        actionType = "PN_START"
    ))
    Timber.v("START ПН: step=%d, percent=%.2f, inFlow=%.2f, accrual=%.2f, wallet=%.2f",
        step, dailyPercent, simInFlow, simAccrual, simWallet)

    // Если не воскресенье - сразу делаем DAILY в день старта
    if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        val actualAccrual = minOf(simInFlow, simAccrual)
        simInFlow -= actualAccrual
        if (simInFlow < 0) simInFlow = 0.0
        simWallet += actualAccrual
        if (simInFlow > 0) {
            simAccrual = simInFlow * (dailyPercent / 100.0)
        }

        step++

        results.add(NoviceFlowEntity(
            date = calendar.timeInMillis,
            percent = dailyPercent,
            inFlowAmount = simInFlow,
            dailyAccrual = simAccrual,
            walletAmount = simWallet,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        ))
        Timber.v("DAILY ПН (start day): step=%d, inFlow=%.2f, accrual=%.2f, wallet=%.2f",
            step, simInFlow, simAccrual, simWallet)
    }

    calendar.add(Calendar.DAY_OF_YEAR, 1)

    // Основной цикл
    while (calendar.timeInMillis <= endCal.timeInMillis && simInFlow > 0) {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isSunday = dayOfWeek == Calendar.SUNDAY

        if (isSunday) {
            results.add(NoviceFlowEntity(
                date = calendar.timeInMillis,
                percent = dailyPercent,
                inFlowAmount = simInFlow,
                dailyAccrual = simAccrual,
                walletAmount = simWallet,
                isButtonPressed = false,
                actionType = "SUNDAY"
            ))
            Timber.v("SUNDAY ПН: ${formatDate(calendar.timeInMillis)}, step=$step")
        } else {
            val actualAccrual = minOf(simInFlow, simAccrual)
            simInFlow -= actualAccrual
            if (simInFlow < 0) simInFlow = 0.0
            simWallet += actualAccrual
            if (simInFlow > 0) {
                simAccrual = simInFlow * (dailyPercent / 100.0)
            }

            step++

            results.add(NoviceFlowEntity(
                date = calendar.timeInMillis,
                percent = dailyPercent,
                inFlowAmount = simInFlow,
                dailyAccrual = simAccrual,
                walletAmount = simWallet,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            ))
            Timber.v("DAILY ПН: step=%d, inFlow=%.2f, accrual=%.2f, wallet=%.2f",
                step, simInFlow, simAccrual, simWallet)
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    Timber.d("Прогноз ПН завершен: записей=${results.size}, последний step=$step")
    return results
}

/**
 * Форматирует дату для логов.
 */
private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
}