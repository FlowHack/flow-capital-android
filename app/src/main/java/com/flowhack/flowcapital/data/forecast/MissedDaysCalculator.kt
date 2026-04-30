package com.flowhack.flowcapital.data.forecast

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Результат проверки дня на необходимость создания записей.
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
 */
object MissedDaysCalculator {

    /**
     * Проверяет, какие действия нужно выполнить для конкретного дня (для РП).
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
            var shouldStopMissedCheck = needMissedCheck
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

            // Для пропусков: если START и НЕ воскресенье
            if (needMissedCheck && dayOfWeek != DayOfWeek.SUNDAY) {
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

        // Проверка текущего дня
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

        // Проверка на воскресенье (не первый день)
        if (needSundayCheck && dayOfWeek == DayOfWeek.SUNDAY) {
            if (!hasSundayRecord) {
                return DayCheckResult(shouldCreateSunday = true)
            } else {
                return DayCheckResult(shouldStopSundayCheck = true)
            }
        }

        // Проверка на пропуск дней
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
        // Логика идентична РП, но типы действий другие
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
