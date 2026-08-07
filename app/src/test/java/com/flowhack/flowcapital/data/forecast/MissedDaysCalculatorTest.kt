package com.flowhack.flowcapital.data.forecast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Тесты для MissedDaysCalculator.
 * Проверяют чистую логику генерации пропущенных дней без зависимостей от БД.
 */
class MissedDaysCalculatorTest {

    // ==================== ТЕСТЫ ДЛЯ РП (GrowingFlow) ====================

    /**
     * Проверка: В воскресенье без SUNDAY записи нужно создать SUNDAY.
     */
    @Test
    fun growingFlow_sundayWithoutRecord_shouldCreateSunday() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать SUNDAY запись", result.shouldCreateSunday)
        assertFalse("Не должны останавливать проверку воскресений", result.shouldStopSundayCheck)
    }

    /**
     * Проверка: Если в воскресенье уже есть SUNDAY - остановить проверку воскресений.
     */
    @Test
    fun growingFlow_sundayWithExistingRecord_shouldStopSundayCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = true,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY", result.shouldCreateSunday)
        assertTrue("Должны остановить проверку воскресений", result.shouldStopSundayCheck)
    }

    /**
     * Проверка: В обычный день без DAILY и MISSED - нужно создать MISSED.
     */
    @Test
    fun growingFlow_weekdayWithoutDailyOrMissed_shouldCreateMissed() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать MISSED запись", result.shouldCreateMissed)
    }

    /**
     * Проверка: Если есть DAILY - остановить проверку пропусков.
     */
    @Test
    fun growingFlow_weekdayWithDaily_shouldStopMissedCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = true,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны остановить проверку пропусков", result.shouldStopMissedCheck)
    }

    /**
     * Проверка: День со START прерывает цикл (shouldBreak = true).
     */
    @Test
    fun growingFlow_dayWithStart_shouldBreak() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = true,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Цикл должен быть прерван", result.shouldBreak)
        assertTrue("Проверка воскресений должна остановиться", result.shouldStopSundayCheck)
    }

    /**
     * Проверка: День START + воскресенье - создаем SUNDAY и прерываем.
     */
    @Test
    fun growingFlow_startDaySundayWithoutSundayRecord_shouldCreateSundayAndBreak() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = true,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать SUNDAY", result.shouldCreateSunday)
        assertTrue("Цикл должен быть прерван", result.shouldBreak)
    }

    // ==================== ТЕСТЫ ДЛЯ ПН (NoviceFlow) ====================

    /**
     * Проверка: В воскресенье без SUNDAY записи нужно создать SUNDAY для ПН.
     */
    @Test
    fun noviceFlow_sundayWithoutRecord_shouldCreateSunday() {
        // Act
        val result = MissedDaysCalculator.checkDayForNoviceFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать SUNDAY запись", result.shouldCreateSunday)
    }

    /**
     * Проверка: В обычный день без PN_DAILY и MISSED - нужно создать MISSED для ПН.
     */
    @Test
    fun noviceFlow_weekdayWithoutDailyOrMissed_shouldCreateMissed() {
        // Act
        val result = MissedDaysCalculator.checkDayForNoviceFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.TUESDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать MISSED запись", result.shouldCreateMissed)
    }

    /**
     * Проверка: День со PN_START прерывает цикл для ПН.
     */
    @Test
    fun noviceFlow_dayWithStart_shouldBreak() {
        // Act
        val result = MissedDaysCalculator.checkDayForNoviceFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = true,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Цикл должен быть прерван", result.shouldBreak)
    }

    /**
     * Проверка: Если есть PN_DAILY - остановить проверку пропусков для ПН.
     */
    @Test
    fun noviceFlow_weekdayWithDaily_shouldStopMissedCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForNoviceFlow(
            hasSundayRecord = false,
            hasDailyRecord = true,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.THURSDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны остановить проверку пропусков", result.shouldStopMissedCheck)
    }

    /**
     * Проверка: isFirstIteration (текущий день) - если воскресенье и нет SUNDAY - создаем.
     */
    @Test
    fun growingFlow_firstIterationSunday_shouldCreateSunday() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = true,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertTrue("Должны создать SUNDAY в первой итерации", result.shouldCreateSunday)
    }

    // ==================== ДОПОЛНИТЕЛЬНЫЕ ВЕТКИ ====================

    /**
     * Проверка: Если есть MISSED, но нет DAILY - остановить проверку пропусков.
     */
    @Test
    fun growingFlow_weekdayWithMissedButNoDaily_shouldStopMissedCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = true,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать MISSED повторно", result.shouldCreateMissed)
        assertTrue("Должны остановить проверку пропусков", result.shouldStopMissedCheck)
    }

    /**
     * Проверка: День START с существующей DAILY - MISSED не создаётся.
     */
    @Test
    fun growingFlow_startDayWithDaily_shouldNotCreateMissed() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = true,
            hasMissedRecord = false,
            hasStartInDay = true,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать MISSED при наличии DAILY", result.shouldCreateMissed)
        assertTrue("Цикл должен быть прерван", result.shouldBreak)
    }

    /**
     * Проверка: День START с существующей SUNDAY - SUNDAY не создаётся, проверка останавливается.
     */
    @Test
    fun growingFlow_startDaySundayWithExistingSunday_shouldStopSundayCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = true,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = true,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY повторно", result.shouldCreateSunday)
        assertTrue("Должны остановить проверку воскресений", result.shouldStopSundayCheck)
        assertTrue("Цикл должен быть прерван", result.shouldBreak)
    }

    /**
     * Проверка: isFirstIteration в воскресенье с существующей SUNDAY - остановить проверку.
     */
    @Test
    fun growingFlow_firstIterationSundayWithExistingSunday_shouldStopSundayCheck() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = true,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = true,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY", result.shouldCreateSunday)
        assertTrue("Должны остановить проверку воскресений", result.shouldStopSundayCheck)
    }

    /**
     * Проверка: isFirstIteration в будний день - пустой результат (ничего не создаём).
     */
    @Test
    fun growingFlow_firstIterationWeekday_returnsEmptyResult() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = true,
            needSundayCheck = true,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY", result.shouldCreateSunday)
        assertFalse("Не должны создавать MISSED", result.shouldCreateMissed)
        assertFalse("Не должны останавливать проверку воскресений", result.shouldStopSundayCheck)
        assertFalse("Не должны останавливать проверку пропусков", result.shouldStopMissedCheck)
        assertFalse("Не должны прерывать цикл", result.shouldBreak)
    }

    /**
     * Проверка: Воскресенье при needMissedCheck=true - просто идём дальше (пустой результат).
     */
    @Test
    fun growingFlow_sundayWithNeedMissedCheck_returnsEmptyResult() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.SUNDAY,
            isFirstIteration = false,
            needSundayCheck = false,
            needMissedCheck = true
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY", result.shouldCreateSunday)
        assertFalse("Не должны создавать MISSED", result.shouldCreateMissed)
        assertFalse("Не должны останавливать проверку пропусков", result.shouldStopMissedCheck)
    }

    /**
     * Проверка: Будний день при needSundayCheck=false и needMissedCheck=false - пустой результат.
     */
    @Test
    fun growingFlow_weekdayWithNoChecks_returnsEmptyResult() {
        // Act
        val result = MissedDaysCalculator.checkDayForGrowingFlow(
            hasSundayRecord = false,
            hasDailyRecord = false,
            hasMissedRecord = false,
            hasStartInDay = false,
            dayOfWeek = DayOfWeek.MONDAY,
            isFirstIteration = false,
            needSundayCheck = false,
            needMissedCheck = false
        )

        // Assert
        assertFalse("Не должны создавать SUNDAY", result.shouldCreateSunday)
        assertFalse("Не должны создавать MISSED", result.shouldCreateMissed)
        assertFalse("Не должны останавливать проверку воскресений", result.shouldStopSundayCheck)
        assertFalse("Не должны останавливать проверку пропусков", result.shouldStopMissedCheck)
        assertFalse("Не должны прерывать цикл", result.shouldBreak)
    }
}
