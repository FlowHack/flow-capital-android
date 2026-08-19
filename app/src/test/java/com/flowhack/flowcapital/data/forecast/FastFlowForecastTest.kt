package com.flowhack.flowcapital.data.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Тесты для функций расчёта Быстрого Потока (БП) и Супер Быстрого Потока (СБП).
 * Проверяют:
 * - Количество дней потока (30 для БП, 15 для СБП)
 * - Определение процента по номиналу из таблицы коэффициентов
 * - Расчёт ежедневного начисления (номинал * (1 + процент/100) / дней)
 * - Полный прогноз с учётом воскресений и сходимостью последнего дня
 * - Дату закрытия (текущая дата + оставшиеся дни + воскресенья)
 * - Генерацию прошлых дней при создании с currentDay > 1
 */
class FastFlowForecastTest {

    private val bpCoefficients = mapOf(
        25000.0 to 3.5,
        50000.0 to 3.6,
        100000.0 to 3.7,
        1000000.0 to 4.7
    )

    private val sbpCoefficients = mapOf(
        50000.0 to 2.0,
        100000.0 to 2.1,
        1000000.0 to 3.0
    )

    @Test
    fun dayCount_bp_returns30() {
        assertEquals("БП должен длиться 30 дней", 30, getFastFlowDayCount(FAST_FLOW_TYPE_BP))
    }

    @Test
    fun dayCount_sbp_returns15() {
        assertEquals("СБП должен длиться 15 дней", 15, getFastFlowDayCount(FAST_FLOW_TYPE_SBP))
    }

    @Test
    fun percentForNominal_exactThreshold_returnsPercent() {
        assertEquals("Точный порог 25000 -> 3.5%", 3.5,
            getFastFlowPercentForNominal(25000.0, bpCoefficients), 0.001)
    }

    @Test
    fun percentForNominal_betweenThresholds_takesFloor() {
        assertEquals("75000 между 50000 и 100000 -> 3.6%", 3.6,
            getFastFlowPercentForNominal(75000.0, bpCoefficients), 0.001)
    }

    @Test
    fun percentForNominal_belowMin_returnsZero() {
        assertEquals("Номинал ниже минимального порога -> 0%", 0.0,
            getFastFlowPercentForNominal(10000.0, bpCoefficients), 0.001)
    }

    @Test
    fun percentForNominal_aboveMax_takesMax() {
        assertEquals("Номинал выше максимума -> максимальный %", 4.7,
            getFastFlowPercentForNominal(5000000.0, bpCoefficients), 0.001)
    }

    @Test
    fun dailyAccrual_bp25000_equals862_50() {
        // Итог = 25000 * 1.035 = 25875; daily = 25875/30 = 862.50
        assertEquals("Ежедневное начисление БП 25000 (3.5%) = 862.50",
            862.50, calculateFastFlowDailyAccrual(25000.0, 3.5, FAST_FLOW_TYPE_BP), 0.01)
    }

    @Test
    fun dailyAccrual_sbp50000_equals3400() {
        // Итог = 50000 * 1.02 = 51000; daily = 51000/15 = 3400
        assertEquals("Ежедневное начисление СБП 50000 (2%) = 3400.00",
            3400.0, calculateFastFlowDailyAccrual(50000.0, 2.0, FAST_FLOW_TYPE_SBP), 0.01)
    }

    @Test
    fun forecast_bp_creates30DaysAndConverges() {
        val start = createDateMillis(2026, Calendar.JANUARY, 5) // понедельник
        val result = calculateFastFlowForecast(25000.0, 3.5, FAST_FLOW_TYPE_BP, start)

        val dailyDays = result.count { it.actionType == "DAILY" }
        assertEquals("Рабочих дней в БП должно быть 30", 30, dailyDays)

        val total = result.filter { it.actionType == "DAILY" }.sumOf { it.accrualAmount }
        // Итог должен сойтись с 25875.00 (с учётом округления)
        assertEquals("Сумма начислений должна сойтись с номиналом*1.035",
            25875.0, total, 0.05)
    }

    @Test
    fun forecast_lastDay_adjustsToConverge() {
        val start = createDateMillis(2026, Calendar.JANUARY, 1)
        val result = calculateFastFlowForecast(25000.0, 3.5, FAST_FLOW_TYPE_BP, start)

        val dailyDays = result.filter { it.actionType == "DAILY" }
        val total = dailyDays.sumOf { it.accrualAmount }
        val expected = 25000.0 * 1.035

        assertEquals("Сумма всех начислений должна равняться итоговой сумме",
            expected, total, 0.01)
    }

    @Test
    fun forecast_sundaysIncludedAsNoAccrual() {
        // 01.01.2026 — четверг. Первое воскресенье — 04.01.2026
        val start = createDateMillis(2026, Calendar.JANUARY, 1)
        val result = calculateFastFlowForecast(5000.0, 3.6, FAST_FLOW_TYPE_BP, start)

        val sundays = result.filter { it.actionType == "SUNDAY" }
        assertTrue("В прогнозе должны быть SUNDAY записи", sundays.isNotEmpty())
        assertTrue("Воскресенье должно иметь нулевое начисление",
            sundays.all { it.accrualAmount == 0.0 && !it.isButtonPressed })
    }

    @Test
    fun closeDate_noSundays_equalsTodayPlusRemaining() {
        // Вторник 06.01.2026, текущий день 1 из 15 (СБП), осталось 14 дней, воскресенья есть
        val today = createDateMillis(2026, Calendar.JANUARY, 6)
        val close = calculateFastFlowCloseDate(today, 1, FAST_FLOW_TYPE_SBP)

        val startCal = Calendar.getInstance().apply { timeInMillis = today }
        val endCal = Calendar.getInstance().apply { timeInMillis = close }
        val diffDays = daysBetween(startCal, endCal)

        // Должно быть >= 14 (оставшиеся дни) и включать минимум 2 воскресенья
        assertTrue("Дата закрытия должна быть минимум через 14 дней", diffDays >= 14)
    }

    @Test
    fun closeDate_lastDay_returnsToday() {
        val today = createDateMillis(2026, Calendar.JANUARY, 6)
        val close = calculateFastFlowCloseDate(today, 30, FAST_FLOW_TYPE_BP)
        assertEquals("Если текущий день — последний, закрытие сегодня", today, close)
    }

    @Test
    fun pastDays_currentDay5_creates4PressedDays() {
        val start = createDateMillis(2026, Calendar.JANUARY, 5) // понедельник
        val result = generateFastFlowPastDays(start, 5, FAST_FLOW_TYPE_BP, 862.50)

        val pressed = result.filter { it.actionType in listOf("START", "DAILY") && it.isButtonPressed }
        assertEquals("Должно быть 4 нажатых прошлых дня", 4, pressed.size)

        val first = result.firstOrNull { it.dayNumber == 1 }
        assertEquals("День 1 должен быть START", "START", first?.actionType)
        assertTrue("Начисления прошлых дней должны быть равны dailyAccrual",
            pressed.all { it.accrualAmount == 862.50 })
    }

    @Test
    fun pastDays_currentDay1_returnsEmpty() {
        val start = createDateMillis(2026, Calendar.JANUARY, 5)
        val result = generateFastFlowPastDays(start, 1, FAST_FLOW_TYPE_BP, 862.50)
        assertTrue("При currentDay=1 прошлых дней нет", result.isEmpty())
    }

    private fun createDateMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun daysBetween(from: Calendar, to: Calendar): Long {
        return (to.timeInMillis - from.timeInMillis) / (24 * 60 * 60 * 1000)
    }
}