package com.flowhack.flowcapital.data.forecast

import com.flowhack.flowcapital.data.db.NoviceFlowEntity
import timber.log.Timber
import java.util.Calendar

/**
 * Рассчитывает прогноз ПН (Поток Новичка).
 *
 * Логика по ТЗ:
 * - Старт: записывается с процентом, в потоке, начислением, кошельком
 * - Если не воскресенье: в день старта СРАЗУ делается начисление (при isExistingFlow=false)
 * - Если isExistingFlow=true: START в текущий день, первое DAILY только со следующего дня
 * - Каждый день (кроме воскресений): кнопка нажимается
 * - Воскресенье: SUNDAY запись без начисления
 * - Прогноз останавливается при inFlow <= 0
 * - Процент фиксированный (не растет как в РП)
 * - Сложный процент: при compoundInterest=true, когда wallet >= reinvestAmount,
 *   происходит реинвест (кошелек переходит в поток) в тот же день
 *
 * @param inFlow Начальная сумма в потоке
 * @param dailyPercent Фиксированный ежедневный процент
 * @param wallet Начальный кошелек
 * @param startDateMillis Дата старта (timestamp)
 * @param targetDateMillis Дата окончания прогноза (timestamp)
 * @param isExistingFlow true если это расчет действующего потока
 * @param compoundInterest true чтобы включить сложный процент (реинвест при накоплении)
 * @param reinvestAmount Сумма в кошельке, при достижении которой происходит реинвест (по умолчанию 2000)
 * @param bonusPercent Процент бонуса за взнос (из настроек), используется при реинвесте
 * @return Список записей прогноза NoviceFlowEntity
 */
fun calculateNoviceFlowForecast(
    inFlow: Double,
    dailyPercent: Double,
    wallet: Double,
    startDateMillis: Long,
    targetDateMillis: Long,
    isExistingFlow: Boolean = false,
    compoundInterest: Boolean = false,
    reinvestAmount: Double = 2000.0,
    bonusPercent: Double = 50.0
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

    // Для нового потока: если не воскресенье - сразу делаем DAILY в день старта
    // Для действующего потока: DAILY начинается только со следующего дня
    // Если воскресенье - создаем SUNDAY запись
    if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
        // Воскресенье: создаем SUNDAY запись без начисления
        results.add(NoviceFlowEntity(
            date = calendar.timeInMillis,
            percent = dailyPercent,
            inFlowAmount = simInFlow,
            dailyAccrual = simAccrual,
            walletAmount = simWallet,
            isButtonPressed = false,
            actionType = "SUNDAY"
        ))
        Timber.v("SUNDAY ПН (start day): ${formatDate(calendar.timeInMillis)}")
    } else if (!isExistingFlow) {
        // Не воскресенье и новый поток: делаем DAILY
        val actualAccrual = minOf(simInFlow, simAccrual)
        val accrualForRecord = actualAccrual

        simInFlow -= actualAccrual
        if (simInFlow < 0) simInFlow = 0.0
        simWallet += actualAccrual

        // Пересчет для следующего дня
        if (simInFlow > 0) {
            simAccrual = simInFlow * (dailyPercent / 100.0)
        }

        step++

        results.add(NoviceFlowEntity(
            date = calendar.timeInMillis,
            percent = dailyPercent,
            inFlowAmount = simInFlow,
            dailyAccrual = accrualForRecord, // Начисление за текущий день
            walletAmount = simWallet,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        ))
        Timber.v("DAILY ПН (start day): step=%d, inFlow=%.2f, accrual=%.2f, wallet=%.2f",
            step, simInFlow, accrualForRecord, simWallet)
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
            // Нажатие кнопки (DAILY) - начисление за текущий день
            val actualAccrual = minOf(simInFlow, simAccrual)
            val accrualForRecord = actualAccrual

            simInFlow -= actualAccrual
            if (simInFlow < 0) simInFlow = 0.0
            simWallet += actualAccrual

            // Пересчет для следующего дня
            if (simInFlow > 0) {
                simAccrual = simInFlow * (dailyPercent / 100.0)
            }

            step++

            results.add(NoviceFlowEntity(
                date = calendar.timeInMillis,
                percent = dailyPercent,
                inFlowAmount = simInFlow,
                dailyAccrual = accrualForRecord, // Начисление за текущий день
                walletAmount = simWallet,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            ))
            Timber.v("DAILY ПН: step=%d, inFlow=%.2f, accrual=%.2f, wallet=%.2f",
                step, simInFlow, simAccrual, simWallet)

            // Сложный процент: проверяем, нужно ли делать реинвест
            if (compoundInterest && simWallet >= reinvestAmount) {
                val reinvestAmountActual = simWallet
                val withBonus = reinvestAmountActual + reinvestAmountActual * (bonusPercent / 100.0)
                simInFlow += withBonus
                simWallet = 0.0
                simAccrual = if (simInFlow > 0) simInFlow * (dailyPercent / 100.0) else 0.0

                step++

                results.add(NoviceFlowEntity(
                    date = calendar.timeInMillis, // Та же дата, что и для DAILY
                    percent = dailyPercent,
                    inFlowAmount = simInFlow,
                    dailyAccrual = simAccrual,
                    walletAmount = simWallet,
                    isButtonPressed = true,
                    actionType = "PN_REINVEST"
                ))
                Timber.v("REINVEST ПН (сложный процент): step=%d, reinvest=%.2f (с бонусом %.0f%% -> %.2f), newInFlow=%.2f, newAccrual=%.2f",
                    step, reinvestAmountActual, bonusPercent, withBonus, simInFlow, simAccrual)
            }
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