package com.flowhack.flowcapital.data.forecast

import java.time.DayOfWeek

/**
 * Результат проверки конкретного дня на необходимость создания записей пропусков или воскресений.
 *
 * @property shouldCreateSunday Нужно ли создать запись SUNDAY за этот день
 * @property shouldCreateMissed Нужно ли создать запись MISSED за этот день
 * @property shouldStopSundayCheck Остановить ли проверку воскресений для последующих дней (найдена запись или START)
 * @property shouldStopMissedCheck Остановить ли проверку пропусков для последующих дней (найдена DAILY/MISSED/START)
 * @property shouldBreak Прервать ли цикл обхода дней полностью (найден START)
 */
data class DayCheckResult(
    val shouldCreateSunday: Boolean = false,
    val shouldCreateMissed: Boolean = false,
    val shouldStopSundayCheck: Boolean = false,
    val shouldStopMissedCheck: Boolean = false,
    val shouldBreak: Boolean = false
)

/**
 * Калькулятор для определения необходимости создания записей пропущенных дней.
 * Содержит чистую логику (без зависимостей от БД или Android), пригодную для тестирования.
 *
 * Алгоритм работы (согласно ТЗ):
 * 1. Воскресенья проверяются с ТЕКУЩЕГО дня (today)
 * 2. Пропуски дней проверяются со ВЧЕРАШНЕГО дня (D-1) — текущий день НЕ проверяется
 * 3. Если в день есть START — создаем SUNDAY (если воскресенье) или MISSED (если нет DAILY),
 *    и прерываем цикл (break).
 * 4. Для обычных дней: если воскресенье и нет SUNDAY — создаем SUNDAY.
 * 5. Для пропусков: если нет DAILY и MISSED — создаем MISSED.
 */
object MissedDaysCalculator {

    /**
     * Проверяет, какие действия нужно выполнить для конкретного дня (для РП).
     *
     * @param hasSundayRecord Есть ли в этот день запись SUNDAY
     * @param hasDailyRecord Есть ли в этот день запись DAILY
     * @param hasMissedRecord Есть ли в этот день запись MISSED
     * @param hasStartInDay Есть ли в этот день запись START
     * @param dayOfWeek День недели (java.time.DayOfWeek)
     * @param isFirstIteration true, если это текущий день (today) — только воскресенья, без пропусков
     * @param needSundayCheck Флаг, указывающий, что еще нужно проверять воскресенья
     * @param needMissedCheck Флаг, указывающий, что еще нужно проверять пропуски
     * @return [DayCheckResult] с решениями по текущему дню
     */
    fun checkDayForGrowingFlow(
        hasSundayRecord: Boolean,
        hasDailyRecord: Boolean,
        hasMissedRecord: Boolean,
        hasStartInDay: Boolean,
        dayOfWeek: DayOfWeek,
        isFirstIteration: Boolean,
        needSundayCheck: Boolean,
        needMissedCheck: Boolean
    ): DayCheckResult {
        // Если это день со START
        if (hasStartInDay) {
            var shouldStopSundayCheck = needSundayCheck
            var shouldCreateSunday = false
            var shouldCreateMissed = false

            // Для воскресений
            if (needSundayCheck && dayOfWeek == DayOfWeek.SUNDAY) {
                if (!hasSundayRecord) {
                    shouldCreateSunday = true
                } else {
                    shouldStopSundayCheck = true
                }
            }

            // Останавливаем проверку воскресений при любом START
            if (needSundayCheck) {
                shouldStopSundayCheck = true
            }

            // Для пропусков: если START и НЕ воскресенье и это НЕ текущий день (today)
            if (needMissedCheck && dayOfWeek != DayOfWeek.SUNDAY && !isFirstIteration) {
                if (!hasDailyRecord && !hasMissedRecord) {
                    shouldCreateMissed = true
                }
            }

            return DayCheckResult(
                shouldCreateSunday = shouldCreateSunday,
                shouldCreateMissed = shouldCreateMissed,
                shouldStopSundayCheck = shouldStopSundayCheck,
                shouldStopMissedCheck = true, // После START всегда останавливаем
                shouldBreak = true
            )
        }

        // Проверка текущего дня (today) — только воскресенья, пропуски НЕ проверяем
        if (isFirstIteration) {
            if (needSundayCheck && dayOfWeek == DayOfWeek.SUNDAY) {
                if (!hasSundayRecord) {
                    return DayCheckResult(shouldCreateSunday = true)
                } else {
                    return DayCheckResult(shouldStopSundayCheck = true)
                }
            }
            return DayCheckResult()
        }

        // Для не-current дней (D-1 и ранее): воскресенья + пропуски
        if (needSundayCheck && dayOfWeek == DayOfWeek.SUNDAY) {
            if (!hasSundayRecord) {
                return DayCheckResult(shouldCreateSunday = true)
            } else {
                return DayCheckResult(shouldStopSundayCheck = true)
            }
        }

        // Проверка на пропуск дней (только для не-current)
        if (needMissedCheck) {
            if (dayOfWeek == DayOfWeek.SUNDAY) {
                return DayCheckResult() // Просто идем дальше
            }

            if (!hasDailyRecord && !hasMissedRecord) {
                return DayCheckResult(shouldCreateMissed = true)
            } else if (!hasDailyRecord && hasMissedRecord) {
                return DayCheckResult(shouldStopMissedCheck = true)
            } else if (hasDailyRecord) {
                return DayCheckResult(shouldStopMissedCheck = true)
            }
        }

        return DayCheckResult()
    }

    /**
     * Проверяет, какие действия нужно выполнить для конкретного дня (для ПН).
     * Логика идентична РП, но используются другие типы действий (PN_START, PN_DAILY и т.д.).
     *
     * @param hasSundayRecord Есть ли в этот день запись SUNDAY
     * @param hasDailyRecord Есть ли в этот день запись PN_DAILY
     * @param hasMissedRecord Есть ли в этот день запись MISSED
     * @param hasStartInDay Есть ли в этот день запись PN_START
     * @param dayOfWeek День недели
     * @param isFirstIteration true, если это текущий день
     * @param needSundayCheck Флаг проверки воскресений
     * @param needMissedCheck Флаг проверки пропусков
     * @return [DayCheckResult] с решениями по текущему дню
     */
    fun checkDayForNoviceFlow(
        hasSundayRecord: Boolean,
        hasDailyRecord: Boolean,
        hasMissedRecord: Boolean,
        hasStartInDay: Boolean,
        dayOfWeek: DayOfWeek,
        isFirstIteration: Boolean,
        needSundayCheck: Boolean,
        needMissedCheck: Boolean
    ): DayCheckResult {
        return checkDayForGrowingFlow(
            hasSundayRecord = hasSundayRecord,
            hasDailyRecord = hasDailyRecord,
            hasMissedRecord = hasMissedRecord,
            hasStartInDay = hasStartInDay,
            dayOfWeek = dayOfWeek,
            isFirstIteration = isFirstIteration,
            needSundayCheck = needSundayCheck,
            needMissedCheck = needMissedCheck
        )
    }
}
